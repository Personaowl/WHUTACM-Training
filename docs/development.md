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
- 后端：Controller 处理 HTTP 与入口权限，Service 承担业务流程，Repository 使用 JdbcTemplate 访问 MySQL，DTO 定义请求/响应；部分关键业务在服务层再次校验权限。
- AccessTokenInterceptor 验证令牌并将当前数据库用户放入 UserContext；GlobalExceptionHandler 统一常见失败。不要根据老源码注释推断所有接口都返回相同结构，首页与文件导出有例外。
- DailyCheckInJobService 在内存管理校验任务；外部 CF 请求经 CodeforcesRequestCoordinator 排队，记账由 DailyCheckInRecorder 在独立短事务中执行。
- ABC 跟踪与周训练统计是两个服务：前者同步外部比赛，后者从本地记录聚合并管理情况说明。MySqlInitializer 负责建表/有限补结构，DataInitializer 负责初始化配置的超管。
- 当前按单后端实例部署，缓存、任务和部分锁在进程内；没有微服务拆分、分布式任务队列或跨实例调度去重。层次分离不等于可直接横向扩容。
- 四平台基础统计由 PlatformStatisticsProvider 统一返回，首页与绑定页共用展示组件。新增三平台经 PublicPlatformStatisticsService 异步写入 platform_statistics_snapshot；默认 30 分钟缓存、失败退避 5 分钟，网页请求不等待外部网站。测试配置关闭外部同步，使用解析样例和 H2 快照回归；口径见[接口参考](api-design.md)。

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

CI 对 main、mty-dev 的 push 及 pull request 运行后端 verify、前端锁文件安装、构建和测试。自动依赖审计已按维护选择关闭，仍可手动运行 `npm run audit:security`；关闭检查不代表依赖漏洞已修复。前端测试基于 Node test runner，后端数据库用例使用 H2 MySQL 模式；这些检查不代替真实 MySQL、真实外部 OJ 和目标服务器并发验收。不要把过往测试数量写成固定能力保证。

---

[返回文档索引](README.md) · [返回项目首页](../README.md)
