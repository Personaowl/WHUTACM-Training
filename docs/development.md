# 本地开发指南

[文档索引](README.md) · [项目首页](../README.md)

生产服务器安装请阅读 [生产部署指南](deployment.md)，不要直接套用本地开发账号或环境。

## 技术与目录

- 后端：Spring Boot 3.3.5、Maven、MySQL 8、JdbcTemplate、BCrypt 密码哈希。
- 前端：React 18、Vite 8、React Router 7、Tailwind CSS 4（官方 Vite 插件）、Radix UI。
- 推荐环境：Java 21、Node.js 22（至少 22.12）、MySQL 8，与 CI 和现有生产环境一致；后端编译目标为 Java 17。
- 前端采用 Tailwind 4 的现代 CSS 功能，不面向旧浏览器；具体兼容性需在目标浏览器验收，不将本文当作完整浏览器认证列表。
- 部署：Nginx 提供静态页面及 API 反向代理，systemd 管理后端。仓库的 Compose 只提供开发 MySQL，没有一键启动前后端的生产 Docker 镜像/编排。

```text
backend/                  后端接口、业务服务、数据访问与测试
frontend/                 前端页面、通用组件与接口请求
deploy/                   环境变量、Nginx 和 systemd 模板
docs/                     接口、数据库与生产部署说明
.github/workflows/ci.yml   后端验证、前端构建和测试
```

### 当前代码分层

- 前端：pages 为路由页面，components 为通用/业务组件，api 为 HTTP 封装，utils 为表单和展示规则；懒加载页面，服务端数据与本地登录状态分别处理。
- 首页数据生命周期由 `useHomeOverview` 管理；绑定、平台统计、自主练习分别使用独立 API 模块。旧版 views 和旧 styles.css 已移除，实际入口仍是 main.jsx → App.jsx。
- 全局 CSS 的唯一入口是 `frontend/src/index.css`，按原级联顺序导入 `src/styles` 中的 13 个职责模块；顺序约定见该目录 README。页面/组件 CSS 仍在原入口加载，不能按文件名重排导入或直接删除同名覆盖规则。
- 题单状态与保存流程由 `useProblemLists` 管理，页面负责组合 `components/problem-lists` 中的侧栏、详情和编辑弹窗；路由范围切换后旧响应不能更新新页面。
- 后端：Controller 处理 HTTP 与入口权限，Service 承担业务流程，Repository 使用 JdbcTemplate 访问 MySQL，DTO 定义请求/响应；部分关键业务在服务层再次校验权限。
- CF / AtCoder 所有权验证位于独立的 `ContestAccountBindingController` 和 `service.binding.ContestAccountBindingService`，不再混入站内用户资料服务。重构基线及回退步骤见[架构记录](architecture.md)。
- AccessTokenInterceptor 验证令牌并将当前数据库用户放入 UserContext；GlobalExceptionHandler 统一常见失败。不要根据老源码注释推断所有接口都返回相同结构，首页与文件导出有例外。
- DailyCheckInJobService 在内存管理校验任务；外部 CF 请求经 CodeforcesRequestCoordinator 排队，记账由 DailyCheckInRecorder 在独立短事务中执行。
- 每日题与自主练习分开：DailyProblemService / DailyProblemRepository 负责题位、历史和打卡；PracticeController / PracticeService / PracticeDrawRepository 负责抽题与个人记录。二者共用 ProblemCatalogRepository 和 ProblemPoolService，标签缓存、启动/定时同步及同步去重只保留一份。
- Excel 导出入口仍是 TrainingExcelExportService；`service.exporting` 中 DataLoader 负责读取与范围选择，Data 为只读快照，WorkbookWriter / WorkbookStyles 负责生成文件，不反向查询业务数据。
- ABC 跟踪与周训练统计是两个服务：前者同步外部比赛，后者从本地记录聚合并管理情况说明。MySqlInitializer 负责建表/有限补结构，DataInitializer 负责初始化配置的超管。
- 当前按单后端实例部署，缓存、任务和部分锁在进程内；没有微服务拆分、分布式任务队列或跨实例调度去重。层次分离不等于可直接横向扩容。
- 四平台基础统计由 PlatformStatisticsProvider 统一返回；首页和个人中心共用 PlatformDataPanel，绑定页使用 PlatformStatistics，三者共用统计数字字号。新增三平台经 PublicPlatformStatisticsService 异步写入 platform_statistics_snapshot；默认 30 分钟缓存、失败退避 5 分钟，网页请求不等待外部网站。测试配置关闭外部同步，使用解析样例和 H2 快照回归；口径见[接口参考](api-design.md)。

精确路径见 [后端源码](../backend/src/main/java/com/whut/training/) 与 [前端源码](../frontend/src/)，接口和表结构分别见 [接口参考](api-design.md)、[数据库结构](db-design.md)。

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

若已安装 Docker，也可在项目根目录执行 `docker compose up -d mysql`。当前 [compose.yaml](../compose.yaml) 使用 MySQL 8.4，开发 root 密码为 `whut_dev_password`，映射主机 3306，数据保存在 mysql_data 卷；不要与已有本机 MySQL 争用同一端口，也不要把该配置用于公网生产。应用的 MYSQL_PASSWORD 要与实际数据库一致。

环境变量须设置在运行 Java 的进程/IDE 中；仓库没有自动读取根目录 .env 的依赖。修改变量后重启后端，切勿把真实生产凭据写进 application.yml 或提交 Git。

### 3. 启动后端

在 IDEA 中运行 `TrainingBackendApplication`，或在项目根目录执行：

```bash
mvn -f backend/pom.xml spring-boot:run
```

默认地址为 `http://localhost:8080`。首次启动自动创建数据库表和配置的超级管理员，不迁移旧 SQLite 数据。默认开发账号为 `superadmin` / `superadmin123`，仅用于本地；已有账号不会因重启重设密码。

默认 profile 和显式 dev profile 不同。仅需页面联调时无需启用 dev；需要隔离调试时间/跳过 CF 校验时才用 `mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"`。这些调试能力不能开放公网，生产必须为 prod。

空题库会在应用就绪后异步同步；健康检查 UP 不代表题库已经同步结束。题库未就绪时每日题可能提示初始化中/503，先看外部 CF 连接与日志，不要重复启动 Java 进程。

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

CI 对 main、mty-dev 的 push 及 pull request 运行后端 verify、前端锁文件安装、构建和测试。自动依赖审计已按维护选择关闭，仍可手动运行 `npm run audit:security`；关闭检查不代表依赖漏洞已修复。前端测试基于 Node test runner；后端默认使用 H2 MySQL 模式，也可以按下节运行隔离 MySQL 回归。它们不替代真实外部 OJ 和目标服务器并发验收，不要把过往测试数量写成固定能力保证。

### 隔离 MySQL 回归（Windows）

已安装 MySQL 8 的服务端和客户端程序、Java 和 Maven 后，在项目根目录的 PowerShell 中执行：

```powershell
.\backend\scripts\test-mysql.ps1 -MySqlBin 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
# Maven 不在 PATH 时，用 -Maven 指定自己的 mvn.cmd 完整路径。
```

- 脚本不安装或重启已有 MySQL 服务；使用 `--no-defaults`、新临时数据目录、随机端口和仅绑定 `127.0.0.1` 的实例。
- 在任何建库/改账号操作前，先只读核对服务端 `@@datadir` 确实属于本次临时目录。创建随机 `whut_acm_test_<12位hex>` 库和仅有该库权限的 `whut_test` 账号，不使用项目开发库或生产库。
- 随机密码只经子进程环境和标准输入传递，不写进脚本、命令行参数或项目配置。Maven 子进程启用测试专用 `mysql-test` profile；前置检查会拒绝默认端口、外部地址、非测试库、root 和空密码。
- Spring 集成测试使用真实 MySQL；显式创建独立 H2 的单元测试仍使用 H2。身份测试会确认数据库产品为 MySQL，脚本也会拒绝“跳过测试却报告成功”。外站联网专项测试仍需单独开启。
- 成功后关闭临时实例并仅清理其生成的数据文件；失败也关闭实例，但保留临时目录诊断。不会删除已有数据库。脚本会恢复原进程环境变量。
- 日志为 `backend/target/mysql-regression.log`，XML 副本为 `backend/target/mysql-regression-reports/`，正常 H2 再测试不会覆盖该副本；`mvn clean` 会删除整个 target，需保留的报告请另存。

2026-10-26 已在本机 MySQL 8.0.19 完成回归，并修复历史/导出计算列的 JDBC 数字映射兼容问题。这个结果不等于新服务器的版本、配置和实际数据量已验收；服务器上线仍需按目标环境再检查。

---

[返回文档索引](README.md) · [返回项目首页](../README.md)
