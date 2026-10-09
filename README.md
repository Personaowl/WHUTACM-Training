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
| 首页 | 训练概况、CF 概览、排行榜、训练热力图；手动打卡与每日抽签，打卡日历以弹窗查看 |
| 排行榜 | 按总积分排序，分页展示，不包含超级管理员 |
| 推题 | 用户提交题目、管理员审核、推题池管理、每日推送与历史记录 |
| 训练看板 | 查看现役队员每日题、CF 数据及 AtCoder ABC 完成情况，展示姓名、学院、班级等资料 |
| Excel 导出 | 最近一周、一个月或全部记录；按选项导出每日题与 CF Rated 比赛；ABC 周赛也支持导出 |
| 用户管理 | 按账号、展示名称、姓名或学院搜索，管理现役身份；超级管理员可调整角色及重置其他非超级管理员用户密码 |

### 每日题与积分规则

- 默认 Easy 为 **800～1800**，Hard 为 **1900～3000**，范围可在后端配置中调整。
- 每天只展示当前有效的 Easy 和 Hard，管理员重抽后的旧题不会继续作为额外每日题展示。
- 校验要求提交属于已绑定的 CF 账号、题目匹配且判定为 `OK`。
- 校验仅查询账号最近 **30 条提交**，找不到时不回退全量查询。请尽量在提交通过后及时校验。
- 每日积分取当天完成题目的最高分，**不将 Easy 和 Hard 相加**。先完成 1200 分 Easy，再完成 1900 分 Hard，当天总分为 1900，第二次新增 700 分。
- CF 题库缓存题目元数据，不保存题面、测试数据或用户代码；目前不包含 Gym 题目。

### 头像、趣味打卡与 ABC

- 头像仅支持自行上传。浏览器裁剪压缩后，图片以 data image 保存在数据库，不依赖 CF 头像服务器；升级时清理旧外部头像地址，保留已上传图片。
- 手动趣味打卡与每日题校验是独立功能。签文来自平台内置内容库，不依赖第三方运势 API。
- 训练热力图使用平台每日题记录，并非 CF 全部做题数据。
- AtCoder ABC 在开赛时冻结现役名单；参赛记录来自官方公开 History，赛时 AC 来自 AtCoder Problems。默认要求正式比赛期间至少 AC 1 题，支持配置、豁免与重试。数据源异常时重试，避免直接误判缺席。

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
- 前端：React 18、Vite 8、React Router 7、Tailwind CSS、Radix UI。
- 推荐环境：Java 21、Node.js 22、MySQL 8，与 CI 和现有生产环境一致；后端编译目标为 Java 17。
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
npm run build
```

前端产物位于 `frontend/dist/`，后端 JAR 位于 `backend/target/`。根目录没有 `pom.xml`，不能直接运行 `mvn clean verify`；请使用 `-f backend/pom.xml` 或先进入 `backend/`。

## 生产部署与更新

推荐单台 2 核 4 GB Linux 服务器，使用 Nginx + Spring Boot + MySQL。应用包含进程内缓存、任务状态和用户锁，目前按单后端实例部署，不依赖 Redis。约 20 人并发是目标场景，实际容量取决于请求频率和外部 API 响应。

完整步骤见 [生产部署说明](docs/deployment.md)。仓库提供：

- [环境变量模板](deploy/whut-acm.env.example)：复制到 `/etc/whut-acm/whut-acm.env`，填写实际数据库凭据、管理员凭据与域名。
- [systemd 服务](deploy/systemd/whut-acm.service)：管理后端服务。
- [Nginx 配置](deploy/nginx/whut-acm.conf.example)：静态资源、API 代理与 HTTPS。

生产环境必须使用 `prod` profile 和独立数据库账号。数据库及超级管理员密码至少 16 位，CORS 配置明确的 HTTPS 域名，不能沿用开发凭据。真实环境变量文件和备份不要提交到 Git。

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

## 性能与会话

- 每日题校验采用异步任务，前端轮询结果；CF 请求统一限速，默认启动间隔 2.1 秒，校验优先于普通资料刷新和后台同步。外部服务慢或多人同时校验时仍可能排队。
- CF 资料和比赛信息使用缓存，资料刷新、比赛聚合和校验使用有界线程池；前端按路由拆分加载。
- 默认 Access Token 为 2 小时，Refresh Token 为 6 小时；正常登录会话最长 6 小时，到期需重新登录。生产环境可通过模板中的 TTL 变量调整。
- CF、AtCoder、牛客、洛谷和 AtCoder Problems 是外部数据源，异常时可能暂时无法刷新或校验。

## 文档索引

- [生产部署说明](docs/deployment.md)
- [接口设计](docs/api-design.md)
- [数据库设计](docs/db-design.md)
- [前端设计需求](require.md)
- [历史待办](docs/todolist.md)

接口与数据库文档可能包含早期设计，具体行为以当前代码和配置为准。
