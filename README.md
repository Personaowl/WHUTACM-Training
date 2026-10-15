# WHUT-ACM 训练平台

武汉理工大学 ACM 协会的校内训练平台，集中管理每日一题、专题题单、比赛信息与现役队员训练记录。

当前仓库：[Personaowl/WHUTACM-Training](https://github.com/Personaowl/WHUTACM-Training)。开发与现有服务器发布使用 `mty-dev` 分支。

## 功能概览

| 模块 | 当前功能 |
| --- | --- |
| 账号与资料 | 注册、登录、修改展示名称和简介；姓名、学院、班级均为选填；自行上传头像，未上传或加载失败时显示默认头像 |
| 账号绑定 | 独立页面，支持 Codeforces 和 AtCoder 账号所有权验证 |
| 每日一题 | Easy / Hard 两个档位、提交校验、积分、标签显示开关、历史记录、加入个人题单 |
| 自主练习 | 难度上下限从 800～3500 按 100 一档选择；最多 5 个标签，按同时包含匹配；保存抽题历史，不校验提交 |
| 题单 | 用户创建一级专题题单；管理员发布全站共享题单；每日题和自主练习均可一键加入 |
| Codeforces 数据 | 查看自己及其他成员公开的 Rating、做题数、比赛记录和彩色标签分布图 |
| 近期比赛 | 聚合 Codeforces、AtCoder、牛客、洛谷比赛信息，展示受公开数据源可用性影响 |
| 首页 | 训练概况、CF 概览、排行榜、训练热力图及每日题训练入口 |
| 排行榜 | 按总积分排序，分页展示，不包含超级管理员 |
| 推题 | 用户提交题目、管理员审核、推题池管理、每日推送与历史记录 |
| 训练看板 | “每日一题”“每周一赛”“周训练记录”三个子页面，支持完成情况统计、成员资料展示、情况说明审核及当周状态调整 |
| 我的周训练 | 周一至周日汇总自己的每日题与 ABC 记录；切换历史周，提交特殊情况说明，查看管理员意见与处理记录 |
| Excel 导出 | 最近一周、一个月或全部记录；按选项导出每日题与 CF Rated 比赛；ABC 周赛也支持导出 |
| 用户管理 | 按账号、展示名称、姓名或学院搜索，管理现役身份；超级管理员可调整角色及重置其他非超级管理员用户密码 |

### 每日题与积分规则

- 默认 Easy 为 **800～1800**，Hard 为 **1900～3000**，范围可在后端配置中调整。
- 每天只展示当前有效的 Easy 和 Hard，管理员重抽后的旧题不会继续作为额外每日题展示。
- 校验要求提交属于已绑定的 CF 账号、题目匹配且判定为 `OK`。
- 校验仅查询账号最近 **30 条提交**，找不到时不回退全量查询。请尽量在提交通过后及时校验。
- 每日积分取当天完成题目的最高分，**不将 Easy 和 Hard 相加**。先完成 1200 分 Easy，再完成 1900 分 Hard，当天总分为 1900，第二次新增 700 分。
- CF 题库缓存题目元数据，不保存题面、测试数据或用户代码；目前不包含 Gym 题目。

### 头像与 ABC

- 头像仅支持自行上传。浏览器裁剪压缩后，图片以 data image 保存在数据库，不依赖 CF 头像服务器；升级时清理旧外部头像地址，保留已上传图片。
- 训练热力图使用平台每日题记录，并非 CF 全部做题数据。
- AtCoder ABC 在开赛时冻结现役名单；参赛记录来自官方公开 History，赛时 AC 来自 AtCoder Problems。默认要求正式比赛期间至少 AC 1 题，支持配置、豁免与重试。数据源异常时重试，避免直接误判缺席。
- 管理员进入“训练看板”默认显示“每日一题”（`/admin/training/daily`）；切换到“每周一赛”（`/admin/training/weekly`）后查看 ABC 历史场次、同步结果、调整规则及管理豁免。页面按需加载，Excel 导出默认勾选当前训练类型，也可组合导出。综合周记录及说明审核见 `/admin/training/records`。

### 周训练记录与特殊情况处理

- 选手入口为“我的周训练”（`/weekly-training`），个人接口始终使用当前登录用户身份，不能查询其他选手的说明；管理员通过训练看板统一查询。
- 以 **Asia/Shanghai 时区的周一 00:00 至下周一 00:00** 为一周，日期选择器会归到对应周一；支持前后周及历史日期查询，不允许查询未来周。
- 每天至少完成一道平台每日题计 1 天，Easy/Hard 和重抽记录不重复计天，周目标为 7 天；周赛沿用 ABC 的正式参赛及最低 AC 规则，周内多场 ABC 任一场达标即可。单场既有豁免仍生效。
- 本周尚未结束且未完成全部要求时显示“进行中”。历史周中，ABC 未同步、数据异常或未发现场次显示“待确认”，不直接认定缺席；确实无赛等情况可由管理员核实后调整周状态。
- 自动统计始终读取本地每日题与 ABC 数据，查看周记录不会即时逐人访问外部网站。既有 ABC 同步任务或管理员“同步结果”更新数据后，可刷新周记录。
- 选手可提交/更新最多 2000 字的特殊情况说明，更新后重新进入待审核。管理员可同意并设置“已达标 / 已豁免 / 未达标”，或恢复自动判定，也可不同意并给出意见。所有处理意见对本人可见。
- 人工状态只作用于该成员的该周，不改题目、比赛原始记录或积分。重新提交说明或不同意说明不会悄悄撤销此前人工状态；需要撤销时管理员显式选择“恢复自动判定”。
- 说明、审核和状态调整均留存操作记录，界面展示最近 20 条。保存使用事务及版本校验，避免两个管理员重复处理，或旧页面覆盖选手新提交的说明。
- 新功能启用后定期保存当周现役名单，已有周记录在退役后仍可查看。启用前的历史只能基于现存记录和当前现役名单汇总，无法还原过去未记录的入队/退役日期。
- 后端启动时自动新增 `weekly_training_record`、`weekly_training_audit` 表，不删除旧数据。上线前正常备份；迁移时完整导出业务库会一并包含周记录与审核历史。原有 Excel 导出仍导出原始每日题/比赛记录，不包含人工周状态及私人情况说明。

## 权限

角色与队员身份独立：`USER` / `ADMIN` / `SUPER_ADMIN` 控制权限，普通成员 / 现役队员用于训练统计。

| 角色 | 权限 |
| --- | --- |
| USER | 个人资料、绑定、每日题、自主练习、个人题单、公开成员数据与推题 |
| ADMIN | 普通用户功能，以及训练看板、导出、每日题管理、用户管理、推题审核与共享题单发布 |
| SUPER_ADMIN | 管理员功能，以及角色调整、密码重置等受限操作 |

超级管理员可将其他非超级管理员用户密码重置为 `123456`，同时撤销其登录会话；用户重新登录后应及时修改密码。不能通过此功能重置自己的密码或其他超级管理员密码。

## 技术与目录

- 后端：Spring Boot 3.3.5、Maven、MySQL 8、JdbcTemplate、BCrypt 密码哈希。
- 前端：React 18、Vite 8、React Router 7、Tailwind CSS 4（官方 Vite 插件）、Radix UI。
- 推荐环境：Java 21、Node.js 22（至少 22.12）、MySQL 8，与 CI 和现有生产环境一致；后端编译目标为 Java 17。
- 浏览器：Chrome / Edge 111+、Safari 16.4+、Firefox 128+。Tailwind 4 使用现代 CSS 功能，不面向旧版浏览器；保留现有墨蓝色主题与页面结构。
- 部署：Nginx 提供静态页面及 HTTPS，systemd 管理后端。Docker 为可选项。

```text
backend/                  后端接口、业务服务、数据访问与测试
frontend/                 前端页面、通用组件与接口请求
deploy/                   环境变量、Nginx 和 systemd 模板
docs/                     接口、数据库与生产部署说明
.github/workflows/ci.yml   后端验证、前端依赖审计和构建
```

## 本地启动

### 1. 获取项目

```bash
git clone --branch mty-dev https://github.com/Personaowl/WHUTACM-Training.git
cd WHUTACM-Training
```

### 2. 配置 MySQL

可以直接使用本机 MySQL，无需 Docker。在 IDEA 运行配置中设置以下环境变量，替换为本机密码：

```text
MYSQL_URL=jdbc:mysql://localhost:3306/whut_training?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的本机密码
```

IDEA 数据库工具的数据源设置不会自动配置应用连接；应用使用环境变量及 `backend/src/main/resources/application.yml`。首次初始化需要建库、建表权限。

若已安装 Docker，也可在项目根目录执行 `docker compose up -d mysql`，并按 Compose 中的数据库凭据设置环境变量。

### 3. 启动后端

在 IDEA 中运行 `TrainingBackendApplication`，或在项目根目录执行：

```bash
mvn -f backend/pom.xml spring-boot:run
```

默认地址为 `http://localhost:8080`。首次启动自动创建数据库表和配置的超级管理员，不迁移旧 SQLite 数据。默认开发账号为 `superadmin` / `superadmin123`，仅用于本地；已有账号不会因重启重设密码。

### 4. 启动前端

```bash
cd frontend
npm ci
npm run dev
```

默认访问 `http://localhost:5173`，API 请求由开发代理转发到后端。

### 5. 绑定竞赛账号

登录后进入「账号绑定」：

- Codeforces：发起验证后，按页面提示在验证时间内向 CF 1A 提交编译错误代码，再返回完成绑定。
- AtCoder：将生成的验证码临时填入 Settings 的 Affiliation，返回完成绑定；成功后可恢复原内容。

## 验证与构建

在项目根目录执行后端验证，前端命令在 `frontend/` 中运行：

```bash
mvn -f backend/pom.xml clean verify
cd frontend
npm ci
npm audit --audit-level=high
npm test
npm run build
```

前端产物位于 `frontend/dist/`，后端 JAR 位于 `backend/target/`。根目录没有 `pom.xml`，不能直接运行 `mvn clean verify`；请使用 `-f backend/pom.xml` 或先进入 `backend/`。

## 生产部署与更新

推荐单台 2 核 4 GB Linux 服务器，使用 Nginx + Spring Boot + MySQL。应用包含进程内缓存、任务状态和用户锁，目前按单后端实例部署，不依赖 Redis。约 20 人并发是目标场景，实际容量取决于请求频率和外部 API 响应。

完整步骤见 [生产部署说明](docs/deployment.md)。仓库提供：

- [环境变量模板](deploy/whut-acm.env.example)：复制到 `/etc/whut-acm/whut-acm.env`，填写实际数据库凭据、管理员凭据与域名。
- [systemd 服务](deploy/systemd/whut-acm.service)：管理后端服务。
- [Nginx 配置](deploy/nginx/whut-acm.conf.example)：静态资源、API 代理与 HTTPS。
- [临时 HTTP/IP 配置](deploy/nginx/whut-acm-http.conf.example)：先通过公网 IPv4 访问，之后再替换为 HTTPS 配置。

生产环境必须使用 `prod` profile 和独立数据库账号。数据库及超级管理员密码至少 16 位，默认只允许明确的 HTTPS 来源，不能沿用开发凭据。临时 HTTP/IP 必须显式设置 `ALLOW_INSECURE_HTTP=true` 并填写准确的 IPv4 来源，不能使用 `*`；这不是加密措施，密码和令牌仍会明文传输。真实环境变量文件和备份不要提交到 Git。

现有服务器目录约定：

```text
/srv/whut-training-team                源码目录（迁移仓库无需更名）
/opt/whut-acm/training-backend.jar      后端运行包
/var/www/whut-acm                      前端静态产物
/etc/whut-acm/whut-acm.env              生产环境变量
/usr/local/sbin/deploy-whut-acm         已安装的一键发布脚本
```

迁移后确认服务器的 `origin` 指向本仓库。可先验证拉取：

```bash
cd /srv/whut-training-team
git remote -v
git fetch origin mty-dev
git log --oneline -1 origin/mty-dev
```

上述命令不会替换正在运行的服务。若要更新源码且本地没有冲突，在 `mty-dev` 分支执行 `git pull --ff-only origin mty-dev`。

正式更新时，若服务器已安装一键发布脚本，执行：

```bash
/usr/local/sbin/deploy-whut-acm
```

脚本按现有配置拉取 `origin/mty-dev`，构建、备份数据库和旧版本、安装产物、重启并检查健康状态。它是服务器已安装的脚本，不是新克隆仓库后自动提供的命令。

运行状态检查：

```bash
systemctl is-active nginx mysql whut-acm
curl -i http://127.0.0.1:8080/api/health
journalctl -u whut-acm -n 100 --no-pager
systemctl list-timers whut-acm-backup.timer --all
```

健康接口应返回 HTTP 200，且 `status`、`database` 为 `UP`。继续保留每日数据库备份并定期验证恢复；源码迁移不会搬迁或删除服务器数据库。

## 新服务器部署（Ubuntu 24.04，无 Docker）

下面命令在 **Linux 服务器的 root SSH 终端**运行，普通用户先执行 <code>sudo -i</code>，不要直接粘贴进本地 PowerShell。示例中的 <code>OLD_SERVER_IP</code>、<code>NEW_SERVER_IP</code>、<code>acm.example.com</code> 和密码执行前必须替换。

建议 2 核 4 GB 起步，使用 Java 21、Node.js 22、MySQL 8.0。迁移时与旧机保持相同 MySQL 主版本，先运行旧机当前发布的代码版本，迁移稳定后再升级。

如果此次迁移要启用新增的临时 HTTP/IP 模式，目标代码必须包含 `ALLOW_INSECURE_HTTP` 功能；旧版本仅添加环境变量不会生效。先在演练库验证目标版本，再进行最终停写迁移，不要把不支持 HTTP 的旧版本与新配置混用。

### 1. 安装环境

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

### 2. 拉代码，准备用户和目录

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

### 3. 创建数据库和应用账号

新机运行 <code>mysql</code> 进入控制台（Ubuntu 默认 root 通常使用本机 socket；密码认证使用 <code>mysql -u root -p</code>）。执行以下 SQL，密码改为独立随机密码，可用 <code>openssl rand -hex 24</code> 生成：

~~~sql
CREATE DATABASE whut_training CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'whut_acm'@'127.0.0.1' IDENTIFIED BY '替换成至少16位随机密码';
GRANT ALL PRIVILEGES ON whut_training.* TO 'whut_acm'@'127.0.0.1';
EXIT;
~~~

只授权本项目数据库。启动会自动建表/补结构，因此应用需要库内 DDL 权限，无需全局管理员权限。上面用于新服务器；已有同名库时先核实用途。

**迁移数据时先停在这里：新后端不要启动，先完成下文数据库恢复，再继续。**

### 4. 设置生产环境变量

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

### 5. 构建和启动

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

### 6. 配置 Nginx 和 HTTPS

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
nginx -t
systemctl reload nginx
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
nginx -t
systemctl reload nginx
systemctl enable --now certbot.timer
certbot renew --dry-run
curl -i https://acm.example.com/api/health
~~~

**迁移现有域名不要提前切公网 DNS**：优先复制仍有效的旧证书和当前 Nginx 配置，用下文 curl --resolve 测新机，再在最终数据恢复后切换。域名相同且证书有效可沿用，不需反复申请。

### 6A. 暂时通过公网 IPv4 和 HTTP 访问

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

### 7. 运维与验收

每日 MySQL 备份至少保留 14 天，并留一份异机备份，定期在临时库恢复验证。发布脚本、备份脚本和 timer 是旧机手工安装文件，**不在 Git 仓库，clone 不会自动带来**，迁移方法见下节。

未安装一键脚本时：先备份数据库和旧运行产物，再按第 5 步构建安装、重启后端并验健康。不要把旧机上存在的命令当成新机自带命令。

功能验收包含：登录、头像、绑定信息、每日题与历史、题单、推题、排行榜、管理看板和 Excel 导出。确认 Nginx/MySQL/后端、备份 timer 及证书续期正常。

## 数据迁移到另一台服务器（推荐短暂停机）

### 要迁移哪些内容

| 内容 | 来源与处理 |
| --- | --- |
| 用户、密码哈希、角色、个人资料、绑定信息 | MySQL 的 whut_training 业务库 |
| 头像、题单、每日题/积分、推题和 ABC | 同一业务库；当前头像为 data image，没有独立 uploads 目录 |
| 程序 | 按旧机运行提交从 Git 获取构建；数据库不在 Git 中 |
| 环境变量及数据库备份凭据 | /etc/whut-acm/，包含秘密，须安全传输 |
| 证书、私钥及续期配置 | /etc/letsencrypt/ |
| 实际 Nginx 配置及额外 include | /etc/nginx/sites-available/whut-acm 及实际引用的自定义文件 |
| 发布/备份脚本和 timer | /usr/local/sbin/ 中相关脚本和 /etc/systemd/system/ 中的自定义单元 |
| 日志、旧备份 | 可按需归档，不是恢复业务必需数据 |
| 进程内缓存/校验任务 | 不迁移；先等待在途任务完成，重启后缓存重建 |

不要直接复制运行中的 /var/lib/mysql，也不要迁移 mysql 系统库。只导出业务库，在新机重新创建账号和权限。[MySQL 8.0 官方备份说明](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)。

推荐：**新机准备 → 演练 → 旧机停写 → 最终备份 → 通过电脑传输 → 新机恢复 → 验证 → IP 访问或切 DNS → 验收**。先演练估算维护窗口；当前小规模项目用停写迁移比双机同步更易维护。新机准备好再开始最终停写，避免整段安装时间都让旧站停机。

### 1. 盘点与准备

旧机运行：

~~~bash
mysql --version
systemctl cat whut-acm
systemctl cat whut-acm-backup.service whut-acm-backup.timer
ls -l /usr/local/sbin/deploy-whut-acm /usr/local/sbin/backup-whut-acm-db
git -C /srv/whut-training-team log -1 --oneline
~~~

结合成功发布日志确认运行版本，检查 Nginx 额外安全规则。新机完成上文环境、代码构建、建空库，后端暂不启动；域名提前降低 TTL（如 300 秒，并等待原 TTL 过去）。可以先用预备备份恢复到独立演练库；最终库不要混入测试数据。

### 2. 提前复制配置和脚本

只迁移到新 IP、暂不绑定域名时，保留环境文件、发布/备份脚本和 timer 即可；旧证书及 HTTPS Nginx 配置可另行归档，不要直接启用。HTTP/IP 访问使用上文的独立模板。

在**新机 root 终端**执行；scp 使用非默认 SSH 端口时加 -P 实际端口。需要有旧机 SSH 登录权限，不能直连时通过自己的电脑中转。

~~~bash
install -d -m 700 /root/whut-migration
scp -rp root@OLD_SERVER_IP:/etc/whut-acm /root/whut-migration/
(
  set -euo pipefail
  ssh root@OLD_SERVER_IP 'tar -C /etc -czf - letsencrypt' | tar -C /root/whut-migration -xzf -
)
scp root@OLD_SERVER_IP:/etc/nginx/sites-available/whut-acm /root/whut-migration/whut-acm.nginx
scp root@OLD_SERVER_IP:/usr/local/sbin/deploy-whut-acm /root/whut-migration/
scp root@OLD_SERVER_IP:/usr/local/sbin/backup-whut-acm-db /root/whut-migration/
scp root@OLD_SERVER_IP:/etc/systemd/system/whut-acm-backup.service /root/whut-migration/
scp root@OLD_SERVER_IP:/etc/systemd/system/whut-acm-backup.timer /root/whut-migration/
~~~

缺少文件时先检查实际路径；额外 Nginx include、续期 hook 也需盘点。证书目录通过 tar 保留符号链接（scp -r 会跟随符号链接，不适合保留证书目录结构）。暂存目录含密码/私钥，不能公开。非默认 SSH 端口还需给上面的 ssh 命令加 -p 实际端口。

~~~bash
cp -a /root/whut-migration/whut-acm/. /etc/whut-acm/
chmod 700 /etc/whut-acm
chmod 600 /etc/whut-acm/whut-acm.env
nano /etc/whut-acm/whut-acm.env
~~~

改为新机数据库账号/密码，沿用旧管理员初始化名称、域名和其余生产设置；备份凭据文件也改为新机密码。新机尚无证书目录时：

~~~bash
cp -a /root/whut-migration/letsencrypt /etc/
~~~

新机已有 /etc/letsencrypt 时先备份并有选择合并，不直接覆盖；复制完整目录才能保留 live 到 archive 的符号链接和续期配置。

### 3. 旧机维护窗口：停写并最终导出

本步在**旧机**执行，会中断 API。先通知成员暂停使用，等在途校验完成。只停应用，不停 MySQL：

~~~bash
systemctl stop whut-acm
systemctl is-active whut-acm
~~~

确认 inactive 后继续。其他程序若也写这个库，必须一起停写；保持旧后端停止直到切换完成。

整个括号块一起执行：

~~~bash
(
  set -euo pipefail
  set -o noclobber
  umask 077
  install -d -m 700 /root/whut-migration-final
  mysqldump -u root \
    --single-transaction --quick --default-character-set=utf8mb4 \
    --no-tablespaces --set-gtid-purged=OFF \
    --routines --events --triggers \
    whut_training | gzip > /root/whut-migration-final/whut_training.sql.gz
  cd /root/whut-migration-final
  gzip -t whut_training.sql.gz
  sha256sum whut_training.sql.gz > whut_training.sql.gz.sha256
  ls -lh whut_training.sql.gz
)
~~~

默认使用 Ubuntu root socket 认证；root 需密码时改为 mysqldump -u root -p，交互输入，不在命令中写明文密码。必须确认整块无错误；pipefail 防止 mysqldump 失败却得到正常 gzip 空包。文件已存在时会停止，换新的备份目录并同步修改后续路径，不要强制覆盖旧备份。导出期间不要改表、建表或发布。

### 4. 新机传输与恢复

#### 4.1 通过自己的 Windows 电脑中转（推荐）

旧机已经完成最终导出并保持停写后，在**自己电脑的 PowerShell**执行，而不是服务器终端。替换两个 IP；本地目录不放进 Git 仓库或云同步目录：

~~~powershell
$oldServer = "OLD_SERVER_IP"
$newServer = "NEW_SERVER_IP"
$migrationDir = Join-Path $env:USERPROFILE "whut-migration"
New-Item -ItemType Directory -Force $migrationDir | Out-Null
Set-Location $migrationDir

scp "root@${oldServer}:/root/whut-migration-final/whut_training.sql.gz" .
if ($LASTEXITCODE -ne 0) { throw "数据库下载失败，停止迁移" }
scp "root@${oldServer}:/root/whut-migration-final/whut_training.sql.gz.sha256" .
if ($LASTEXITCODE -ne 0) { throw "校验文件下载失败，停止迁移" }

$expectedHash = ((Get-Content .\whut_training.sql.gz.sha256 -Raw).Trim() -split '\s+')[0]
$actualHash = (Get-FileHash .\whut_training.sql.gz -Algorithm SHA256).Hash
if ($expectedHash -notmatch '^[0-9a-fA-F]{64}$' -or $actualHash -ne $expectedHash) { throw "校验不一致，停止迁移" }

ssh "root@$newServer" "install -d -m 700 /root/whut-migration"
if ($LASTEXITCODE -ne 0) { throw "新服务器目录准备失败" }
scp .\whut_training.sql.gz "root@${newServer}:/root/whut-migration/"
if ($LASTEXITCODE -ne 0) { throw "数据库上传失败，停止迁移" }
scp .\whut_training.sql.gz.sha256 "root@${newServer}:/root/whut-migration/"
if ($LASTEXITCODE -ne 0) { throw "校验文件上传失败，停止迁移" }
~~~

本地重复下载会替换同名文件；已有需要保留的备份时使用新的本地目录。scp 传输的是压缩文件本身，不要通过 PowerShell 管道解压/转码 SQL。SSH 非默认端口：`ssh -p 端口`、`scp -P 端口`；两台服务器端口可以不同。

第 2 节的配置也可通过电脑中转，例如：

~~~powershell
scp -r "root@${oldServer}:/etc/whut-acm" .
scp "root@${oldServer}:/usr/local/sbin/deploy-whut-acm" .
scp "root@${oldServer}:/usr/local/sbin/backup-whut-acm-db" .
scp "root@${oldServer}:/etc/systemd/system/whut-acm-backup.service" .
scp "root@${oldServer}:/etc/systemd/system/whut-acm-backup.timer" .
~~~

确认每条成功后，将需要的文件单独上传到 `root@新服务器IP:/root/whut-migration/`，再检查修改后安装。`whut-acm` 是目录，上传它使用 `scp -r`。环境文件及备份凭据含秘密，不要发到聊天或提交 Git；不要复制自己的 SSH 私钥给新服务器。

#### 4.2 两台服务器能直连时的替代方法

已通过电脑上传时**跳过以下两条 scp**。否则在**新机**执行：

~~~bash
scp root@OLD_SERVER_IP:/root/whut-migration-final/whut_training.sql.gz /root/whut-migration/
scp root@OLD_SERVER_IP:/root/whut-migration-final/whut_training.sql.gz.sha256 /root/whut-migration/
~~~

#### 4.3 新服务器校验与恢复

先按新机部署第 3 步创建**空业务库和独立应用账号**；dump 不迁移 MySQL 用户和授权。后端保持停止。在**新机 Linux 终端**执行：

~~~bash
cd /root/whut-migration
sha256sum -c whut_training.sql.gz.sha256
gzip -t whut_training.sql.gz
mysql -NBe "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='whut_training';"
~~~

校验失败停止并重新传输。最后一条应为 0（库存在且无表），新后端未启动。**已有表先核实备份；以下 SQL 恢复会重建同名表，不能直接覆盖业务库。** 应恢复到新建空库，改用其他库名时同步修改 JDBC 地址。

~~~bash
(
  set -euo pipefail
  gzip -dc /root/whut-migration/whut_training.sql.gz | mysql -u root --default-character-set=utf8mb4 whut_training
)
~~~

不得使用 --force 跳过 SQL 错误。业务库 dump 不含 MySQL 账号，前面必须已单独建账号授权。

如果新机 root 使用密码认证：先用编辑器创建 /root/whut-migration/mysql-root.cnf（权限 600），内容如下，填写实际密码，不写入 Git：

~~~ini
[client]
user=root
password=实际root密码
~~~

然后把导入命令中的 mysql -u root 替换为 mysql --defaults-extra-file=/root/whut-migration/mysql-root.cnf（选项必须放在 mysql 后第一位）。可先运行 mysql --defaults-extra-file=/root/whut-migration/mysql-root.cnf -e "SELECT 1;" 测试认证，避免在导入管道里交互输入。其他验库命令也使用同一认证文件。

在旧机和新机分别运行以下 SQL 对比精确行数、积分总和，并按实际表补查题单、推题等重要数据：

~~~bash
mysql whut_training -e "SHOW TABLES; SELECT COUNT(*) AS users, COALESCE(SUM(total_points),0) AS points FROM users; SELECT COUNT(*) AS daily_records FROM user_daily_status; SELECT COUNT(*) AS problems FROM cf_problem;"
~~~

原账号密码哈希、绑定、头像随库保留，用户无需重新注册。首次启动前对比最直观；启动后定时任务或表结构升级可能改变部分记录。

### 5. 新机启动与切换前验证

只用 IP 时：完成数据核对后按新机部署第 4、5、6A 节设置环境、构建启动、启用 HTTP/IP Nginx。数据库与后端继续监听本机，浏览器访问 `http://新服务器IP/`；不需要 DNS、旧域名证书或 Certbot，暂时跳过本节下方的旧 HTTPS 配置和 `curl --resolve`，也跳过下一节的 DNS 切换操作。运维脚本与备份任务仍须恢复。

恢复完成才按上文安装 JAR、前端、环境和 systemd，然后启动。使用从旧机复制的实际 Nginx 配置：

~~~bash
install -m 644 /root/whut-migration/whut-acm.nginx /etc/nginx/sites-available/whut-acm
~~~

若未启用则创建 sites-enabled 链接；证书、目录、额外 include/hook 都要存在，nginx -t 成功再 reload。不要同时启用模板和旧站点两份配置。

在电脑或能访问新机的终端，用真实域名绕过 DNS 测新 IP，不加 -k：

~~~bash
curl -i --resolve acm.example.com:443:NEW_SERVER_IP https://acm.example.com/
curl -i --resolve acm.example.com:443:NEW_SERVER_IP https://acm.example.com/api/health
~~~

浏览器验收可临时在电脑 hosts 将域名指向新 IP，结束移除此项。检查原账号登录、头像、积分和关键功能。DNS 切换前仅自己验收，不让成员使用新机；一旦产生需要保留的新数据，不能再用旧备份覆盖。

### 6. 切换域名，恢复运维任务

先使用 IP 时，仅执行本节的脚本/备份 timer 恢复与验证；DNS 和证书步骤等绑定域名时再做。

将主域名及实际使用的 www A 记录改为新 IP，同时处理 AAAA、CDN 回源和云防火墙。DNS 传播期间旧后端继续停止，旧 IP 上的用户可能暂时遇到维护错误；不能为恢复访问而重新开放旧库写入。若要兼容旧 DNS 缓存，可另配旧 Nginx 代理至新机。

检查暂存脚本的 origin、分支、目录、健康等待时间和备份凭据后安装：

~~~bash
install -m 750 /root/whut-migration/deploy-whut-acm /usr/local/sbin/deploy-whut-acm
install -m 750 /root/whut-migration/backup-whut-acm-db /usr/local/sbin/backup-whut-acm-db
install -m 644 /root/whut-migration/whut-acm-backup.service /etc/systemd/system/whut-acm-backup.service
install -m 644 /root/whut-migration/whut-acm-backup.timer /etc/systemd/system/whut-acm-backup.timer
install -d -m 700 /var/backups/whut-acm/mysql /var/backups/whut-acm/releases
systemctl daemon-reload
systemctl enable --now whut-acm-backup.timer
systemctl start whut-acm-backup.service
journalctl -u whut-acm-backup.service -n 30 --no-pager
systemctl enable --now certbot.timer
certbot renew --dry-run
~~~

脚本的额外目录、依赖按实际内容补齐。检查证书 renew 配置和 hook 路径；定时器不会随证书文件自动迁移。

确认正常后旧机执行 <code>systemctl disable whut-acm</code>，防止旧机重启后再次写旧库。保留旧机和最终 dump 一段观察期（例如 7 天），验收后再下线；新机备份至少异机保存一份。

### 7. 回退原则

- 新机**还没有业务新写入**：停新后端，DNS 切回旧 IP，旧机执行 <code>systemctl enable --now whut-acm</code>。
- 新机**已接受业务写入**：不能直接切回旧库。先暂停新机写入、备份新库，决定将最新数据恢复回旧机，或修好新机继续运行；直接切回会丢失新数据。
- 保留原 dump、校验和及旧库，不通过清库或跳过 SQL 错误强行继续。
- 真正要求接近不停机时再考虑 MySQL 主从/增量切换，目前推荐先演练的停写迁移。

证书签发、续期可对照 [Certbot 官方 Nginx 指南](https://certbot.eff.org/instructions?ws=nginx&os=snap)。

## 性能与会话

- 首页积分来自当前请求认证读取的数据库用户，而不是登录时的浏览器缓存；个人积分不放入公共首页缓存，也不额外请求个人资料。首页统计、热力图和 CF 概览独立加载，单个接口慢不会阻塞整页。普通页面已取消持续背景粒子和卡片实时模糊，保留原有墨蓝色主题。
- 每日题校验采用异步任务，前端轮询结果；CF 请求统一限速，默认启动间隔 2.1 秒，校验优先于普通资料刷新和后台同步。外部服务慢或多人同时校验时仍可能排队。
- CF 资料和比赛信息使用缓存，资料刷新、比赛聚合和校验使用有界线程池；前端按路由拆分加载。
- 默认 Access Token 为 2 小时，Refresh Token 为 6 小时；正常登录会话最长 6 小时，到期需重新登录。生产环境可通过模板中的 TTL 变量调整。
- CF、AtCoder、牛客、洛谷和 AtCoder Problems 是外部数据源，异常时可能暂时无法刷新或校验。

### 静态资源压缩与访问慢排查

Nginx 默认只压缩 HTML 时，JS/CSS 仍可能原样传输。模板已为 HTTPS 站点增加静态文本压缩。**现有服务器需要手动合并到 `/etc/nginx/sites-available/whut-acm` 的 HTTPS `server {}` 内**；不要覆盖证书、域名、代理和安全规则，已有同名指令就修改原指令，不要重复添加：

~~~nginx
gzip on;
gzip_vary on;
gzip_min_length 1024;
gzip_comp_level 4;
gzip_types text/css text/javascript application/javascript image/svg+xml;
~~~

保存后仅在语法检查成功时重载：

~~~bash
nginx -t && systemctl reload nginx
~~~

在浏览器 Network 中找到当前实际主 JS 路径（文件名每次构建会变化），然后检查：

~~~bash
curl -I -H 'Accept-Encoding: gzip' https://你的域名/assets/实际文件名.js
~~~

应出现 `Content-Encoding: gzip` 及 `Vary: Accept-Encoding`。不要用旧文件名或仅检查首页 HTML 判断 JS 已压缩。该配置不需要数据库迁移，代码发布脚本也不会自动覆盖现有 Nginx 站点。

区分网络和后端耗时可以运行：

~~~bash
curl -o /dev/null -sS --max-time 20 -w 'dns=%{time_namelookup} connect=%{time_connect} tls=%{time_appconnect} ttfb=%{time_starttransfer} total=%{time_total}\n' https://你的域名/api/health
~~~

这些字段是从请求开始累计的时间；TLS 阶段明显等待不等于 Java 接口慢。服务器上再对 `http://127.0.0.1:8080/api/health` 做本机检测，并结合浏览器 Network 的具体慢请求、`journalctl -u whut-acm` 与 `free -h`、`vmstat 1 5` 判断网络等待、接口排队或内存/CPU 压力。少量健康检查不能代替实际登录接口和并发压测。

### 上线稳定性加固（2026-10-15）

- 已移除独立每日签到、抽签和签到日历的页面、接口、服务及资源；每日题校验与训练热力图继续保留。旧库的 user_fun_check_in 表仅留作历史存档，不再使用，也不会在升级时自动删除。
- 注册支持选填姓名、学院、班级；已有账号无需补填。个人中心通过“修改个人资料”弹窗编辑，取消不保存，选填项可修改或清空。

- 每日题校验与记账分离：外部 API 请求结束后，独立短事务同时写入题位完成状态、当天积分和累计积分；按用户行加锁，避免并发重复计分和部分写入。
- 普通资料保存不更新累计积分，后台 CF 刷新仅更新对应账号的 Rating 字段，避免过期资料覆盖密码、头像、资料或积分。
- 登录不再等待 CF 请求；令牌续期在事务中一次性消费旧令牌并创建新会话，失败时回滚。过期会话改为定时清理，并增加过期时间索引。
- 前端续期后同步界面与存储的令牌；临时超时、限流或服务端故障不会直接清除登录。跨标签页登录状态变化会同步，新登录也不会被迟到的刷新响应覆盖。
- CF 单次排队及执行等待默认最多 30 秒，超时会取消尚未执行的任务；后台资料刷新失败后冷却 60 秒。外部服务异常与提交编号不存在分别提示。
- 失败的校验任务允许立即重试，进行中的同一请求继续合并；保留任务总量有上限，避免无限占用内存。
- Nginx 模板增加入口 HTML 重新验证与敏感点文件拦截。旧服务器需核对后手动合并站点配置并执行 nginx -t、reload；仅拉代码/发布 JAR 不会自动安装 Nginx 配置。
- 前端新增 npm test，无需额外测试框架，并纳入 CI；后端增加并发记账、部分写入回滚、令牌续期失败回滚等回归测试。数据库测试使用 H2 MySQL 模式，不能替代目标 MySQL 上的验收或真实服务器压测。

### 依赖审计状态

2026-10-15 已修复此前 CI 报告的前端依赖问题：Axios 升级至 1.20.0，source-map-js 升级至 1.2.2；Tailwind 升至 4.3.3 并使用官方 Vite 插件，移除旧 PostCSS 配置及包含 braces 的旧构建依赖链。干净执行 npm ci 后，npm run audit:security 当前报告 **0 vulnerabilities**；高危审计门槛继续保留，没有忽略开发依赖或屏蔽告警。审计结果是当时的依赖库状态，不代表未来永远没有新漏洞。

迁移保留原 JS 主题配置、圆角及轻阴影，并以 stack-y-* 兼容旧版可见兄弟元素间距，避免条件表单和弹窗变形；样式编译回归测试随 npm test 执行。GitHub Actions 升级至 Node 24 运行时的官方 action，固定完整提交 SHA，但应用构建仍使用 Node 22 / Java 21。

升级验证应依次在 frontend 目录运行 npm ci、npm run audit:security、npm test、npm run build。当前生产方案仍仅由 Nginx 提供静态产物，不公开 Vite 开发服务器。相关上游记录：[Tailwind 升级指南](https://tailwindcss.com/docs/upgrade-guide)、[Axios](https://github.com/advisories/GHSA-vh66-26gq-q6x8)、[braces](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm)、[source-map-js](https://github.com/advisories/GHSA-68fv-2mgg-jv7q)。

## 文档索引

- [生产部署说明](docs/deployment.md)
- [接口设计](docs/api-design.md)
- [数据库设计](docs/db-design.md)
- [历史待办](docs/todolist.md)

接口与数据库文档可能包含早期设计，具体行为以当前代码和配置为准。
