# 生产部署指南

[文档索引](README.md) · [项目首页](../README.md) · [数据迁移](migration.md) · [发布与运维](operations.md)

本文与仓库部署模板于 2026-10-20 核对，使用 Nginx + Spring Boot + MySQL 单机部署。2 核 4 GB 可作为起步配置，不是容量承诺；当前计划约 40 人使用，需要按目标服务器与实际请求组合压测。应用包含进程内缓存、任务状态和锁，仅按单后端实例运行，不依赖 Redis；外部 OJ 同步/校验排队不能单靠升级 CPU 消除。

仓库提供以下部署模板：

- [环境变量模板](../deploy/whut-acm.env.example)：复制到 `/etc/whut-acm/whut-acm.env`，填写实际数据库凭据、管理员凭据与域名。
- [systemd 服务](../deploy/systemd/whut-acm.service)：管理后端服务。
- [Nginx 配置](../deploy/nginx/whut-acm.conf.example)：静态资源、API 代理与 HTTPS。
- [临时 HTTP/IP 配置](../deploy/nginx/whut-acm-http.conf.example)：先通过公网 IPv4 访问，之后再替换为 HTTPS 配置。

生产环境必须使用 `prod` profile 和独立数据库账号。数据库及超级管理员密码至少 16 位，默认只允许明确的 HTTPS 来源，不能沿用开发凭据。临时 HTTP/IP 必须显式设置 `ALLOW_INSECURE_HTTP=true` 并填写准确的 IPv4 来源，不能使用 `*`；这不是加密措施，密码和令牌仍会明文传输。真实环境变量文件和备份不要提交到 Git。

现有服务器目录约定：

```text
/srv/whut-training-team                源码目录（迁移仓库无需更名）
/opt/whut-acm/training-backend.jar      后端运行包
/var/www/whut-acm                      前端静态产物
/etc/whut-acm/whut-acm.env              生产环境变量
/usr/local/sbin/deploy-whut-acm         已安装的一键发布脚本
```

## 阅读顺序

- 新机首次部署：按下方环境、数据库、环境变量和构建步骤执行，再选择 HTTPS 或临时 HTTP/IP。
- 迁移已有数据：先准备新机，按 [迁移指南](migration.md) 完成停写、备份和恢复，再启动新机应用。
- 暂不绑定域名：阅读 [临时 HTTP/IP 部署](#http-ip)；后续再切换到 [域名 HTTPS](#https)。
- 本文基于 Ubuntu 24.04，不要求 Docker。

下面命令在 **Linux 服务器的 root SSH 终端**运行，普通用户先执行 <code>sudo -i</code>，不要直接粘贴进本地 PowerShell。示例中的 <code>OLD_SERVER_IP</code>、<code>NEW_SERVER_IP</code>、<code>acm.example.com</code> 和密码执行前必须替换。

使用 Java 21、Node.js 22（至少 22.12）、MySQL 8.0。迁移时优先保持与旧机相同 MySQL 版本并恢复演练；开发 Compose 是 8.4，不要直接当成生产 8.0 的替代配置。先运行旧机当前发布版本，迁移稳定后再升级。

如果此次迁移要启用新增的临时 HTTP/IP 模式，目标代码必须包含 `ALLOW_INSECURE_HTTP` 功能；旧版本仅添加环境变量不会生效。先在演练库验证目标版本，再进行最终停写迁移，不要把不支持 HTTP 的旧版本与新配置混用。

<a id="install"></a>

## 1. 安装环境

~~~bash
apt update
apt install -y openjdk-21-jdk maven mysql-server nginx git curl xz-utils rsync openssl certbot python3-certbot-nginx
systemctl enable --now mysql nginx
~~~

Node.js 使用官方 Node 22 二进制，并验证 SHA256；整个括号块一起执行，任一步失败会停止：

~~~bash
(
  set -euo pipefail
  case "$(uname -m)" in
    x86_64) node_arch=x64 ;;
    aarch64) node_arch=arm64 ;;
    *) echo "Unsupported architecture"; exit 1 ;;
  esac
  node_stage="$(mktemp -d)"
  cd "$node_stage"
  curl -fsSLO https://nodejs.org/dist/latest-v22.x/SHASUMS256.txt
  node_suffix="-linux-$node_arch.tar.xz"
  node_archive="$(awk -v suffix="$node_suffix" \
    'substr($2, length($2)-length(suffix)+1) == suffix { print $2; exit }' SHASUMS256.txt)"
  test -n "$node_archive"
  curl -fsSLO "https://nodejs.org/dist/latest-v22.x/$node_archive"
  awk -v filename="$node_archive" '$2 == filename' SHASUMS256.txt | sha256sum -c -
  mkdir -p /opt/node22
  tar -xJf "$node_archive" -C /opt/node22 --strip-components=1
  ln -sf /opt/node22/bin/node /usr/local/bin/node
  ln -sf /opt/node22/bin/npm /usr/local/bin/npm
  ln -sf /opt/node22/bin/npx /usr/local/bin/npx
)
java -version
mvn -version
node -v
mysql --version
~~~

期望 Java 21、Node v22.x、MySQL 8.0。MySQL 仅监听本机，检查 Ubuntu MySQL 配置；云平台防火墙和系统防火墙放行实际 SSH 端口、80、443，不对公网开放 8080/3306。

## 2. 拉代码，准备用户和目录

~~~bash
git clone --branch mty-dev https://github.com/Personaowl/WHUTACM-Training.git /srv/whut-training-team
cd /srv/whut-training-team
git log -1 --oneline
id whut-acm >/dev/null 2>&1 || useradd --system --home /opt/whut-acm --shell /usr/sbin/nologin whut-acm
install -d -o whut-acm -g whut-acm -m 750 /opt/whut-acm /var/log/whut-acm
install -d -o www-data -g www-data -m 755 /var/www/whut-acm
install -d -m 700 /etc/whut-acm
~~~

迁移时先按旧机成功发布日志确认运行中的提交，再在新机检出该提交构建；源码 HEAD 不一定等于正在运行的 JAR。私有仓库为新机单独配置只读 Deploy Key，不复制个人 SSH 私钥。

<a id="database"></a>

## 3. 创建数据库和应用账号

新机运行 <code>mysql</code> 进入控制台（Ubuntu 默认 root 通常使用本机 socket；密码认证使用 <code>mysql -u root -p</code>）。执行以下 SQL，密码改为独立随机密码，可用 <code>openssl rand -hex 24</code> 生成：

~~~sql
CREATE DATABASE whut_training CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'whut_acm'@'127.0.0.1' IDENTIFIED BY '替换成至少16位随机密码';
GRANT ALL PRIVILEGES ON whut_training.* TO 'whut_acm'@'127.0.0.1';
EXIT;
~~~

只授权本项目数据库。启动会自动建表/补结构，因此应用需要库内 DDL 权限，无需全局管理员权限。上面用于新服务器；已有同名库时先核实用途。

**迁移数据时先停在这里：新后端不要启动，先按 [数据库恢复步骤](migration.md#restore) 完成恢复，再继续。**

<a id="environment"></a>

## 4. 设置生产环境变量

全新安装：

~~~bash
install -m 600 /srv/whut-training-team/deploy/whut-acm.env.example /etc/whut-acm/whut-acm.env
nano /etc/whut-acm/whut-acm.env
~~~

也可通过 VS Code Remote SSH 编辑。迁移时从旧机配置复制，勿用模板覆盖已经恢复的生产文件。

| 配置 | 内容 |
| --- | --- |
| SPRING_PROFILES_ACTIVE | prod |
| SERVER_ADDRESS / SERVER_PORT | 127.0.0.1 / 8080 |
| MYSQL_URL | 模板中的本机 JDBC 地址，库名与恢复目标一致 |
| MYSQL_USERNAME / MYSQL_PASSWORD | 新机创建的数据库账号和至少 16 位独立随机密码 |
| SUPERADMIN_USERNAME / SUPERADMIN_PASSWORD | 非默认管理员名及至少 16 位密码；迁移时沿用旧机设置 |
| CORS_ALLOWED_ORIGINS | 实际 HTTPS 域名，多个用逗号分隔，如 https://acm.example.com,https://www.acm.example.com |
| ALLOW_INSECURE_HTTP | 默认 false；只有临时 HTTP/IP 阶段才设为 true，CORS 同时改成准确的 http://新服务器IPv4 |
| LOGGING_FILE_PATH | /var/log/whut-acm |
| Token TTL | 可沿用模板中的 7200 / 21600 秒 |

管理员名迁移时要保持一致，否则可能自动新建另一个超级管理员。已有账号密码由数据库哈希决定，修改初始化环境变量不会重置它。临时 HTTP 开关也不会绕过强密码校验，不接受 localhost、回环地址、通配符或 HTTP 域名来源。

环境文件为 systemd <code>KEY=value</code> 格式；特殊字符按 EnvironmentFile 规则加引号，推荐使用 hex 随机密码减少转义问题。真实文件不能提交 Git 或公开。

<a id="build-and-start"></a>

## 5. 构建和启动

每个构建命令成功后再继续：

~~~bash
cd /srv/whut-training-team
mvn -f backend/pom.xml clean verify
cd frontend
npm ci
npm run build
cd ..
install -o whut-acm -g whut-acm -m 750 \
  backend/target/training-backend-0.0.1-SNAPSHOT.jar \
  /opt/whut-acm/training-backend.jar
rsync -a frontend/dist/ /var/www/whut-acm/
chown -R www-data:www-data /var/www/whut-acm
install -m 644 deploy/systemd/whut-acm.service /etc/systemd/system/whut-acm.service
systemctl daemon-reload
systemctl enable --now whut-acm
journalctl -u whut-acm -n 60 --no-pager
~~~

JAR 版本号变化时按实际产物名调整。systemd 使用低权限用户运行；勿另开一个手动 Java 进程占用 8080。初始化完成后检查：

~~~bash
systemctl status whut-acm --no-pager -l
curl -i --max-time 10 http://127.0.0.1:8080/api/health
~~~

应返回 HTTP 200，且 status、database 均为 UP；刚启动时需等待初始化完成。

<a id="https"></a>

## 6. 配置 Nginx 和 HTTPS

还没绑定域名、先通过 IP 访问时，跳过本节，执行下一节的 HTTP/IP 方案。不要在没有证书时直接启用 HTTPS 模板。

全新域名先配置 HTTP 站点用于签发证书。通过编辑器新建 <code>/etc/nginx/sites-available/whut-acm</code>，替换下面域名：

~~~nginx
server {
    listen 80;
    listen [::]:80;
    server_name acm.example.com www.acm.example.com;
    root /var/www/whut-acm;
    index index.html;
    location / {
        try_files $uri $uri/ /index.html;
    }
}
~~~

~~~bash
ln -s /etc/nginx/sites-available/whut-acm /etc/nginx/sites-enabled/whut-acm
nginx -t && systemctl reload nginx
~~~

已有启用链接无需重复创建。同一配置只能在 sites-enabled 或 conf.d 启用一份，避免重复 server 或限流区定义。

将实际使用域名的 A 记录指向新 IP；已有 AAAA 也须更新为可用的新 IPv6，或移除旧 AAAA。公网 80/443 通畅后：

~~~bash
certbot certonly --nginx -d acm.example.com -d www.acm.example.com
~~~

不使用 www 就删除相应参数。成功后换为完整生产模板：

~~~bash
cp /etc/nginx/sites-available/whut-acm /etc/nginx/sites-available/whut-acm.http-backup
cp /srv/whut-training-team/deploy/nginx/whut-acm.conf.example /etc/nginx/sites-available/whut-acm
nano /etc/nginx/sites-available/whut-acm
~~~

替换所有示例域名；server_name 中加入实际使用的 www。证书路径以 <code>certbot certificates</code> 为准（证书名可能带 -0001），保留 API 代理、限流与静态文件配置。

~~~bash
nginx -t && systemctl reload nginx
systemctl enable --now certbot.timer
certbot renew --dry-run
curl -i https://acm.example.com/api/health
~~~

**迁移现有域名不要提前切公网 DNS**：优先复制仍有效的旧证书和当前 Nginx 配置，按 [迁移前验证步骤](migration.md#verify) 使用 curl --resolve 测新机，再在最终数据恢复后切换。域名相同且证书有效可沿用，不需反复申请。

<a id="http-ip"></a>

## 7. 暂时通过公网 IPv4 和 HTTP 访问

此步骤在**新服务器**执行；数据库应已恢复，后端和前端也已按第 5 步安装。只开放实际 SSH 端口和 80，8080/3306 继续仅监听本机，不要为了访问网站放开数据库端口。公网 HTTP 无法防止窃听和篡改，请使用不与其他网站重复的密码，尽量缩短临时运行时间；限流、CORS 和强密码不能代替 HTTPS。

编辑 `/etc/whut-acm/whut-acm.env`，将 `203.0.113.10` 替换成新服务器真实公网 IPv4（它只是文档示例，不可直接使用）：

~~~dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=http://203.0.113.10
ALLOW_INSECURE_HTTP=true
~~~

保留真实的数据库密码和原超级管理员设置，不要用上面几行覆盖整个文件。来源不能带路径、结尾 `/` 或通配符；非 80 端口需写实际端口，但本模板使用 80。默认不设置开关或设为 false 时，HTTP 来源会让 prod 拒绝启动，这是预期保护。更改环境文件后：

~~~bash
chmod 600 /etc/whut-acm/whut-acm.env
systemctl restart whut-acm
journalctl -u whut-acm -n 60 --no-pager
curl -i --max-time 10 http://127.0.0.1:8080/api/health
~~~

首次启动尚未就绪时等待后再检查。健康状态正常才继续。若站点配置已存在，先备份再修改；HTTP/IP 和 HTTPS 模板只能启用一份，不能在 sites-enabled 与 conf.d 重复启用，否则限流区会冲突。

~~~bash
cp /srv/whut-training-team/deploy/nginx/whut-acm-http.conf.example /etc/nginx/sites-available/whut-acm
nano /etc/nginx/sites-available/whut-acm
~~~

把 `server_name NEW_SERVER_IP;` 改为真实 IP，例如 `server_name 203.0.113.10;`。该模板包含前端路由回退、API 代理、限流、敏感文件拦截及 JS/CSS 压缩，不需要证书，也不重定向 HTTPS。仅在启用链接不存在时执行：

~~~bash
ln -s /etc/nginx/sites-available/whut-acm /etc/nginx/sites-enabled/whut-acm
~~~

再执行（语法检查失败就不要重载）：

~~~bash
nginx -t && systemctl reload nginx
curl -i http://203.0.113.10/api/health
~~~

浏览器访问 `http://新服务器IP/`，前后端通过同一 Nginx 来源访问，不要打开 Vite 5173 或公网 8080。如果之前发布脚本的健康检查/结束提示仍写旧域名，更新成新机的本地健康地址 `http://127.0.0.1:8080/api/health` 和网站 `http://新服务器IP`；不要只改终端提示却继续检查旧站。

**之后切回域名 HTTPS：**按第 6 节配置 DNS、证书和 HTTPS Nginx，替换当前 HTTP 站点，不并行启用两个模板。将 CORS 改为实际 `https://域名`，设置 `ALLOW_INSECURE_HTTP=false`，重启后端并执行 `nginx -t` 后重载。重新验收登录、校验及证书续期。域名/协议变化后浏览器存储不会自动迁移，需要重新登录；建议修改 HTTP 阶段使用过的站内管理员密码。

## 8. 运维与验收

每日 MySQL 备份至少保留 14 天，并留一份异机备份，定期在临时库恢复验证。发布脚本、备份脚本和 timer 是旧机手工安装文件，**不在 Git 仓库，clone 不会自动带来**，迁移方法见 [迁移指南](migration.md)，后续更新见 [发布与日常运维](operations.md)。

未安装一键脚本时：先备份数据库和旧运行产物，再按第 5 步构建安装、重启后端并验健康。不要把旧机上存在的命令当成新机自带命令。

功能验收包含：登录、头像、绑定信息、每日题与历史、题单、推题、排行榜、管理看板和 Excel 导出。确认 Nginx/MySQL/后端、备份 timer 及证书续期正常。

完整验收清单见 [维护检查清单](todolist.md)。本指南没有执行服务器修改；旧站点、代理 include 和自装脚本必须在实际机器核对，仓库升级不会自动覆盖它们。

---

[返回文档索引](README.md) · [返回项目首页](../README.md)
