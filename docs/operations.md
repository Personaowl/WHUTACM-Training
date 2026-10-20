# 发布与日常运维

[文档索引](README.md) · [项目首页](../README.md) · [生产部署](deployment.md) · [数据迁移](migration.md)

本文面向已经部署好的服务器。首次安装、临时 HTTP/IP 和证书配置见部署指南；更换服务器先按迁移指南处理数据，不要只拉代码。

<a id="release"></a>

## 更新代码与发布

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

该路径是服务器自装脚本，不在仓库内，本次文档同步没有读取服务器脚本，不能保证它与历史版本相同。运行前核对它指向自己的 origin/mty-dev，并包含构建、数据库/旧产物备份、安装、重启、健康等待及失败回退；不要只根据文件名推断行为。新克隆仓库不会自动提供该命令，未安装时使用部署指南的手工步骤。

## 运行状态检查

```bash
systemctl is-active nginx mysql whut-acm
curl -i http://127.0.0.1:8080/api/health
journalctl -u whut-acm -n 100 --no-pager
systemctl list-timers whut-acm-backup.timer --all
```

健康接口应返回 HTTP 200，且 `status`、`database` 为 `UP`。继续保留每日数据库备份并定期验证恢复；源码迁移不会搬迁或删除服务器数据库。

健康检查只探测本应用及一次数据库查询，不验证 CF、ABC、邮件、备份或所有业务流程。systemd 刚显示 active 也不等于 Spring 已监听端口；结合启动日志与健康响应判断。

日志通过 journalctl 与 LOGGING_FILE_PATH 指定目录查看。prod 默认关闭服务方法级日志，但保留请求级 INFO；连续 request 日志不等于报错，需看 HTTP 状态和异常。前端生产是静态文件，没有 Vite 后台进程：页面问题看浏览器 Network/Console 与 Nginx access/error 日志，确认扩展脚本报错与本站请求分开排查。

## 限流与安全配置核对

下表是仓库两份 Nginx 模板的值，**不是读取线上配置得到的结论**。均按来源 IP 使用漏桶限流，burst 为突发额度，不是固定时间窗内绝对请求数；登录/注册共用 whut_auth 区，同一校园出口可能互相影响。

| 请求 | 速率 | burst（nodelay） |
| --- | --- | --- |
| 一般 /api/ | 5 次/秒 | 30 |
| 登录 | 10 次/分钟 | 5 |
| 注册 | 与登录共用 10 次/分钟 | 3 |
| 刷新令牌 | 60 次/分钟 | 20 |

HTTP/IP 模板设置 limit_req_status=429；当前 HTTPS 模板未显式设置，Nginx 默认拒绝状态为 503。因此不能仅凭 503 判定 Java 挂了，需查看 Nginx error.log 是否出现 limiting requests。旧服务器有自定义规则时以 nginx -T 的实际展开配置为准，输出可能包含敏感配置，不要直接公开。

两份模板都会拦截敏感点文件路径；并不等于具备 WAF 或能阻止所有扫描。HTTPS 模板还依赖系统 proxy_params，启用前核实实际传递的 Host 和转发头；代理信任应限制在本机 Nginx，不能为解决访问问题把 8080 暴露公网。修改规则、限流区或代理 include 后只在 nginx -t 通过时 reload。

## 性能与会话

- 首页积分来自当前请求认证读取的数据库用户，而不是登录时的浏览器缓存；个人积分不放入公共首页缓存，也不额外请求个人资料。首页统计、热力图和 CF 概览独立加载，单个接口慢不会阻塞整页。普通页面已取消持续背景粒子和卡片实时模糊，保留原有墨蓝色主题。
- 每日题校验采用异步任务，前端轮询结果；CF 请求统一限速，默认启动间隔 2.1 秒，校验优先于普通资料刷新和后台同步。外部服务慢或多人同时校验时仍可能排队。
- CF 资料和比赛信息使用缓存，资料刷新、比赛聚合和校验使用有界线程池；前端按路由拆分加载。
- 默认 Access Token 为 2 小时，Refresh Token 为 6 小时；正常登录会话最长 6 小时，到期需重新登录。生产环境可通过模板中的 TTL 变量调整。
- CF、AtCoder、牛客、洛谷和 AtCoder Problems 是外部数据源，异常时可能暂时无法刷新或校验。

当前默认配置见 [application.yml](../backend/src/main/resources/application.yml) 与 [异步执行器](../backend/src/main/java/com/whut/training/config/AsyncExecutionConfig.java)：Hikari 最大 10 连接；CF 后台 2 线程/队列 40，校验 2 线程/队列 100，比赛聚合 4 线程/队列 16。CF 统一请求协调器另有 200 个排队名额和默认 30 秒等待上限，不与校验任务队列混为同一限制。

每日题默认上海时间 00:05 生成、周一 00:00 同步 CF 题库（以 YAML 覆盖值为准，不用注解中的兜底值推断）；推题默认 00:01。ABC 定时任务默认启动后 30 秒、之后每次完成再间隔 30 分钟执行，周名册默认启动后 45 秒、之后每次完成间隔 30 分钟。它们不是独立服务器 cron，也不是每次打开页面都立即重抓全部数据。

当前没有“40 人并发全部业务已压测通过”的结论。普通页面读取与 40 人同时点击 CF 校验不是同一种负载：前者可受缓存帮助，后者受串行外部请求与队列限制。扩容前先测页面响应、校验端到端等待、错误率、数据库连接和资源使用；不要直接开启多个后端实例绕过进程内锁/统一限速。

### 静态资源压缩与访问慢排查

仓库两份模板都已启用静态文本压缩；旧站点若只压缩 HTML，JS/CSS 仍可能原样传输。**现有服务器需核对后合并到实际站点 server {} 内**；不要覆盖证书、域名、代理和安全规则，已有同名指令就修改原指令，不要重复添加：

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

## 备份与验收

- 每日数据库备份至少保留 14 天，并留一份异机副本；定期在独立临时库验证恢复。
- 发布和备份脚本、systemd timer 是服务器上的手工安装文件，clone 不会自动带来；搬迁方法见 [数据迁移指南](migration.md)。
- 发布前备份数据库和旧运行产物，发布后验收登录、绑定、每日题、题单、训练看板、导出与权限。
- 当前配置与程序的历史加固说明见 [维护记录](changelog.md)；历史审计结果不代表实时安全状态。


---

[返回文档索引](README.md) · [返回项目首页](../README.md)
