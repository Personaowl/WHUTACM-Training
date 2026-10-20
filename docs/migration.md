# 服务器与数据库迁移

[文档索引](README.md) · [项目首页](../README.md) · [生产部署](deployment.md)

推荐短暂停写迁移。备份、下载、上传和恢复均为人工操作；不要在未确认目标库为空前执行导入。通过自己电脑中转的命令见 [第 4 节](#transfer)。

## 要迁移哪些内容

| 内容 | 来源与处理 |
| --- | --- |
| 用户、密码哈希、角色、个人资料、绑定信息 | MySQL 的 whut_training 业务库 |
| 头像、题单、每日题/积分、推题、ABC、周训练说明及审核记录 | 同一业务库；当前头像为 data image，没有独立 uploads 目录 |
| 程序 | 按旧机运行提交从 Git 获取构建；数据库不在 Git 中 |
| 环境变量及数据库备份凭据 | /etc/whut-acm/，包含秘密，须安全传输 |
| 证书、私钥及续期配置 | /etc/letsencrypt/ |
| 实际 Nginx 配置及额外 include | /etc/nginx/sites-available/whut-acm 及实际引用的自定义文件 |
| 发布/备份脚本和 timer | /usr/local/sbin/ 中相关脚本和 /etc/systemd/system/ 中的自定义单元 |
| 日志、旧备份 | 可按需归档，不是恢复业务必需数据 |
| 进程内缓存/校验任务 | 不迁移；先等待在途任务完成，重启后缓存重建 |

不要直接复制运行中的 /var/lib/mysql，也不要迁移 mysql 系统库。只导出业务库，在新机重新创建账号和权限。[MySQL 8.0 官方备份说明](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)。

推荐：**新机准备 → 演练 → 旧机停写 → 最终备份 → 通过电脑传输 → 新机恢复 → 验证 → IP 访问或切 DNS → 验收**。先演练估算维护窗口；当前小规模项目用停写迁移比双机同步更易维护。新机准备好再开始最终停写，避免整段安装时间都让旧站停机。

## 1. 盘点与准备

旧机运行：

~~~bash
mysql --version
systemctl cat whut-acm
systemctl cat whut-acm-backup.service whut-acm-backup.timer
ls -l /usr/local/sbin/deploy-whut-acm /usr/local/sbin/backup-whut-acm-db
git -C /srv/whut-training-team log -1 --oneline
~~~

结合成功发布日志确认运行版本，检查 Nginx 额外安全规则。新机按 [生产部署指南](deployment.md) 准备环境、代码构建和空库，后端暂不启动；域名提前降低 TTL（如 300 秒，并等待原 TTL 过去）。可以先用预备备份恢复到独立演练库；最终库不要混入测试数据。

## 2. 提前复制配置和脚本

只迁移到新 IP、暂不绑定域名时，保留环境文件、发布/备份脚本和 timer 即可；旧证书及 HTTPS Nginx 配置可另行归档，不要直接启用。HTTP/IP 访问使用 [临时 HTTP/IP 模板与配置步骤](deployment.md#http-ip)。

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

改为新机数据库账号/密码，沿用旧管理员初始化名称；站点来源按实际阶段设置：HTTPS 用实际域名，临时 HTTP/IP 按部署文档设置新 IP 和显式开关。备份凭据文件也改为新机密码。下方证书复制仅适用于继续迁移 HTTPS 站点，新机尚无证书目录时执行：

~~~bash
cp -a /root/whut-migration/letsencrypt /etc/
~~~

新机已有 /etc/letsencrypt 时先备份并有选择合并，不直接覆盖；复制完整目录才能保留 live 到 archive 的符号链接和续期配置。

## 3. 旧机维护窗口：停写并最终导出

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

<a id="transfer"></a>

## 4. 新机传输与恢复

### 4.1 通过自己的 Windows 电脑中转（推荐）

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

### 4.2 两台服务器能直连时的替代方法

已通过电脑上传时**跳过以下两条 scp**。否则在**新机**执行：

~~~bash
scp root@OLD_SERVER_IP:/root/whut-migration-final/whut_training.sql.gz /root/whut-migration/
scp root@OLD_SERVER_IP:/root/whut-migration-final/whut_training.sql.gz.sha256 /root/whut-migration/
~~~

<a id="restore"></a>

### 4.3 新服务器校验与恢复

先按 [数据库与应用账号配置](deployment.md#database) 创建**空业务库和独立应用账号**；dump 不迁移 MySQL 用户和授权。后端保持停止。在**新机 Linux 终端**执行：

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

在旧机和新机分别运行以下 SQL 对比精确行数、积分总和，并按 [数据库结构](db-design.md) 补查题单、推题、ABC、weekly_training_record 与 weekly_training_audit 等重要数据；旧版本没有的表不必强行查询：

~~~bash
mysql whut_training -e "SHOW TABLES; SELECT COUNT(*) AS users, COALESCE(SUM(total_points),0) AS points FROM users; SELECT COUNT(*) AS daily_records FROM user_daily_status; SELECT COUNT(*) AS problems FROM cf_problem;"
~~~

原账号密码哈希、绑定、头像随库保留，用户无需重新注册。首次启动前对比最直观；启动后定时任务或表结构升级可能改变部分记录。

<a id="verify"></a>

## 5. 新机启动与切换前验证

只用 IP 时：完成数据核对后依次按 [生产环境变量](deployment.md#environment)、[构建与启动](deployment.md#build-and-start)、[HTTP/IP 部署](deployment.md#http-ip) 设置新机。数据库与后端继续监听本机，浏览器访问 `http://新服务器IP/`；不需要 DNS、旧域名证书或 Certbot，暂时跳过本节下方的旧 HTTPS 配置和 `curl --resolve`，也跳过下一节的 DNS 切换操作。运维脚本与备份任务仍须恢复。

恢复完成后，按 [构建与启动](deployment.md#build-and-start) 安装 JAR、前端、环境和 systemd，然后启动。使用从旧机复制的实际 Nginx 配置：

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

## 6. 切换域名，恢复运维任务

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

上面代码块中的最后两条 Certbot 命令仅在迁移 HTTPS 站点时执行，临时 HTTP/IP 阶段跳过。

脚本的额外目录、依赖按实际内容补齐。检查证书 renew 配置和 hook 路径；定时器不会随证书文件自动迁移。

确认正常后旧机执行 <code>systemctl disable whut-acm</code>，防止旧机重启后再次写旧库。保留旧机和最终 dump 一段观察期（例如 7 天），验收后再下线；新机备份至少异机保存一份。

## 7. 回退原则

- 新机**还没有业务新写入**：停新后端，DNS 切回旧 IP，旧机执行 <code>systemctl enable --now whut-acm</code>。
- 新机**已接受业务写入**：不能直接切回旧库。先暂停新机写入、备份新库，决定将最新数据恢复回旧机，或修好新机继续运行；直接切回会丢失新数据。
- 保留原 dump、校验和及旧库，不通过清库或跳过 SQL 错误强行继续。
- 真正要求接近不停机时再考虑 MySQL 主从/增量切换，目前推荐先演练的停写迁移。

证书签发、续期可对照 [Certbot 官方 Nginx 指南](https://certbot.eff.org/instructions?ws=nginx&os=snap)。

---

[返回文档索引](README.md) · [返回项目首页](../README.md)
