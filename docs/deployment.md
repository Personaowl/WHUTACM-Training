# 生产部署说明

从零安装及跨服务器数据迁移的完整操作已统一维护在 [README：新服务器部署](../README.md#新服务器部署ubuntu-2404无-docker) 与 [README：数据迁移](../README.md#数据迁移到另一台服务器推荐短暂停机)。下面保留部署要点；新机安装和迁移请按 README 顺序执行，尤其注意先恢复数据库再启动应用。

推荐使用单台 2 核 4 GB Linux 服务器：Nginx 提供 HTTPS 和前端静态资源，Spring Boot 仅监听本机 8080，MySQL 仅监听本机 3306。当前应用包含进程内缓存、任务状态和用户锁，因此生产环境保持单后端实例；扩容到多实例前需引入共享缓存与分布式锁。

## 1. 上线前检查

```bash
cd backend && mvn clean verify
cd ../frontend && npm ci && npm audit --audit-level=high && npm run build
```

生产库必须使用独立的非 root MySQL 账号。不要把真实密码写入仓库，也不要沿用开发环境默认账号。
生产模式会在启动时校验安全配置：MySQL 与超级管理员密码必须至少 16 位，超级管理员用户名不能使用默认值，CORS 默认只允许明确的 HTTPS 来源。临时 HTTP/IP 必须显式设置 `ALLOW_INSECURE_HTTP=true` 并使用准确的 `http://IPv4` 来源；这不会提供传输加密，也不会绕过强密码校验。具体操作见 README 的“6A. 暂时通过公网 IPv4 和 HTTP 访问”。校验失败时应用会拒绝启动。

## 2. 准备目录与账号

```bash
sudo useradd --system --home /opt/whut-acm --shell /usr/sbin/nologin whut-acm
sudo mkdir -p /opt/whut-acm /var/www/whut-acm /var/log/whut-acm /etc/whut-acm
sudo chown -R whut-acm:whut-acm /opt/whut-acm /var/log/whut-acm
```

将后端 `target/training-backend-*.jar` 上传为 `/opt/whut-acm/training-backend.jar`，将 `frontend/dist/` 中的文件同步到 `/var/www/whut-acm/`。

## 3. 环境变量与服务

复制 `deploy/whut-acm.env.example` 到 `/etc/whut-acm/whut-acm.env`，填写真实域名和随机密码：

```bash
sudo chmod 600 /etc/whut-acm/whut-acm.env
sudo cp deploy/systemd/whut-acm.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now whut-acm
sudo systemctl status whut-acm
curl http://127.0.0.1:8080/api/health
```

生产 profile 缺少 `MYSQL_*`、`SUPERADMIN_*` 或 `CORS_ALLOWED_ORIGINS` 时会拒绝启动，避免意外使用开发密码。

## 4. Nginx 与 HTTPS

暂未绑定域名时使用 `deploy/nginx/whut-acm-http.conf.example`，按 README 的 HTTP/IP 步骤启用；不要在没有证书时直接启用下面的 HTTPS 模板。两份模板只能启用一份。

修改 `deploy/nginx/whut-acm.conf.example` 中的域名后安装到 `/etc/nginx/conf.d/whut-acm.conf`。首次签发证书前先临时移除 443 证书段，使用 Certbot 签发后恢复完整配置。

```bash
sudo nginx -t
sudo systemctl reload nginx
```

防火墙只开放 SSH、80 和 443；不要向公网开放 8080 和 3306。

## 5. 数据备份与发布

每天执行 `mysqldump --single-transaction`，备份文件至少保留 14 天，并定期在临时数据库中验证恢复。每次发布先备份数据库，再替换 JAR 与前端文件，最后检查：

- `/api/health` 返回 HTTP 200，且数据库状态为 `UP`；
- 登录、注册、Token 刷新正常；
- Codeforces/AtCoder 绑定及外部数据异常降级正常；
- 每日题打卡、管理员看板与 Excel 导出正常；
- Nginx、应用和 MySQL 日志中没有持续错误。
