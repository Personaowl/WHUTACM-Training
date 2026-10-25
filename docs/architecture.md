# 架构与重构记录

[文档索引](README.md) · [本地开发](development.md)

## 重构前基线

- 日期：2026-10-25。
- 稳定提交：`e5f22f024383ba54a00bf85b344d9f8d6b85bf10`，分支 `mty-dev`。
- 本地附注标签：`pre-architecture-refactor-20261025`，指向该提交。重构开始前已跟踪文件无未提交修改；私人笔记不纳入本轮。
- 标签记录的是源码，不包含数据库、生产环境变量、Nginx 配置或服务器运行产物。标签目前只在本地，正式发布前应随代码保存到自己的远程仓库。

基线验证：前端测试及后端 verify 通过；后端真实外站与 MySQL 专项测试默认跳过，不能把 H2 回归当作生产环境验收。

## 设计边界

继续采用单实例、模块化单体，不增加微服务、消息队列或新的前端状态库。重构不改变路由、响应字段、权限、登录有效期、积分算法、最近 30 条提交校验、页面布局或数据库结构。已有进程内缓存、限速和有界后台队列保持原样。

### 前端

```text
pages / components       页面、交互与展示
        ↓
hooks                    页面数据生命周期、轮询与取消后的结果隔离
        ↓
api                      按功能组织请求，复用现有 http 认证刷新机制
        ↓
后端接口

utils                    纯表单与展示规则，不反向依赖页面或请求层
```

- 唯一入口仍是 `main.jsx → App.jsx`，路由懒加载不变。移除未接入入口的 `views/HomeView.jsx`、`views/MainView.jsx` 和旧 `styles.css`；不是删除当前页面。
- `api/user.js` 只管注册、用户资料和站内热力图；`api/accountBinding.js` 管四平台绑定，`api/platformOverview.js` 管平台概览及 CF 详情，`api/practice.js` 管自主练习，`api/dailyProblem.js` 管每日题与异步校验。
- 绑定组件经 API 模块请求，不直接使用 Axios/底层 http；保留原有错误响应语义、认证头、超时和 AbortSignal。
- 首页请求、初始化题库轮询、错误与重试状态由 `useHomeOverview` 管理；`HomePage` 保留原 JSX 和布局。
- 移除未使用的第二套 `refreshTokens` 函数。会话刷新仍由 `http.js` 合并并发请求，不能在业务组件中重复实现。
- 架构测试从实际入口检查 JS 模块可达性，并防止 UI 绕过 API 层、底层反向引用 UI。

### 后端

```text
UserController → UserService → UserServiceImpl → UserRepository
  注册 / 资料 / 用户查询与管理

ContestAccountBindingController → service.binding.ContestAccountBindingService
                               → UserRepository + CF / AtCoder 客户端 + TimeProvider
  保留 CF CE 提交验证与 AtCoder Affiliation 验证

PlatformBindingController → PlatformBindingService
                          → PlatformBindingRepository + PublicProfileVerifier
  保留洛谷 / Nowcoder 简介验证及事务边界
```

- 站内用户服务不再注入外站客户端或绑定计时器，避免普通资料流程与外部验证耦合。
- 四条 CF / AtCoder 绑定路由从用户控制器移至专用控制器；地址、DTO、校验顺序和“仅本人操作，管理员也不例外”的规则不变。
- 原绑定测试随职责迁移，并补充过期、外站失败、账号占用、角色边界和 HTTP 兼容测试；移除没有调用方的旧资料补全方法。
- 不为每个类创建多余接口，不把不同验证协议强行合并成一个抽象。已有 Repository、用例接口和事务机制继续复用。

## 第二阶段：共享题库与自主练习

```text
DailyProblemController → DailyProblemServiceImpl → DailyProblemRepository
  每日题生成 / 重抽 / 校验                         题位 / 历史 / 打卡
                         ↓
                  ProblemCatalogRepository ← ProblemPoolService ← CF 客户端
                         ↑                    启动、定时、空题库同步
PracticeController → PracticeService → PracticeDrawRepository
  自主抽题 / 标签 / 个人记录              已抽题目快照
```

- `ProblemCatalogRepository` 接管原题库 upsert、随机筛选和标签缓存，原 SQL 和缓存失效机制不变；每日题的历史排重查询也保留原口径。
- `ProblemPoolService` 接管应用就绪同步、原定时同步及空题库后台同步；仍以单个 AtomicBoolean 闸门防止重复任务。启动开关、cron 配置键、默认值及空题库 503 均不变，不增设一套练习同步任务。
- `PracticeService` 只负责练习筛选、保存、历史和删除；练习记录独立持久化为快照，不随题库刷新变化，不参与每日计分。
- 四条练习路由地址、可选请求体、字段和失败状态不变；省略上下限仍使用原配置的 1200～2000，标签保持归一化、去重及 AND 匹配，历史 limit 仍收敛到 1～100。
- 删除练习记录仍同时匹配记录 ID 和当前用户 ID；管理员也不能删除他人记录。每日题生成锁、重抽失效顺序和 `DailyCheckInRecorder` 的积分事务不动。
- 合并两份内容完全相同的打卡 RowMapper，整理每日题位映射的缩进和类型导入；不删除旧每日题表兼容路径和历史数据。
- 第一、二阶段完成后保存为独立本地提交检查点。阶段二开始前另在本地工作目录保存了 11 个将修改文件的副本；它不是 Git 标签，也不包含后来新增的类。原稳定标签仍代表整轮大重构之前的状态，不能把它当作阶段二完成后的检查点。

## 回退办法

可先核对基线和改动范围：

```bash
git rev-parse pre-architecture-refactor-20261025^{commit}
git diff --stat pre-architecture-refactor-20261025
git log --oneline pre-architecture-refactor-20261025..HEAD
```

- **尚未提交**：先保存本轮差异和新增文件，再针对本轮文件恢复到基线。不要使用全仓库硬重置，也不要覆盖私人笔记或其他未关联的修改。
- **已经提交/推送**：先确认本轮实际提交列表，再用 `git revert` 生成反向提交；不要强制推送或撤销后来无关的工作。
- **已经部署**：以标签对应代码重新构建前后端，并按现有发布流程安装，或使用发布脚本保留的运行产物。本轮无数据库迁移，不需要为源码回退删除或还原业务数据；生产配置仍单独保管。

## 第一阶段验证

2026-10-25 本地结果：

- `npm test`：92 项通过；`npm run build`：生产构建通过。
- `mvn --batch-mode verify`：151 项测试，147 项通过、4 项专项测试跳过，打包成功。
- 浏览器模拟数据回归：9 个内部页面 × 6 种窗口尺寸，共 54 组通过；额外检查首页自然对齐、多平台概览、登录/注册表单及减少动态效果。
- 交互回归覆盖：首页失败重试、离页后迟到响应隔离、题库初始化结束后停止轮询，以及四平台绑定开始/完成请求和成功状态。

浏览器检查使用本地模拟数据，不会修改生产账号或调用真实外站进行绑定。标准测试命令可在项目内重复运行；真实外部平台、MySQL 与服务器发布仍需部署环境单独验收。

## 第二阶段验证

- 保留此前测试并新增 24 项后端测试：共 175 项，171 项通过，4 项专项测试跳过。
- `mvn --batch-mode clean verify` 打包通过；前端 92 项测试与生产构建再次通过。拆分前后的 33 段 SQL 文本逐项一致，配置与表结构未修改。
- 新增覆盖：40 个并发空题库请求与定时任务共享同步闸门、失败后再同步、空上游不覆盖题库、练习标签归一化、默认范围、错误条件与历史字段、真实仓储的用户隔离。
- Spring Boot + MockMvc + H2 集成测试覆盖四条练习路由的鉴权、完整抽题/查询/删除流程，以及超级管理员操作他人记录时仍被隔离。
- 每日题回归保留 Easy → Hard 差额计分；新增只补缺失题位、不重新抽已有题、Hard 重抽先失效再插入的检查。
- 本阶段前端实现和数据库结构未修改；H2 集成测试不代表已在真实 MySQL 或生产服务器完成验收。

## 后续拆分顺序

前两阶段先解决已确认的死代码和清晰的职责混合，不以改目录名代替解耦。后续重点为：

1. 每日题生成与提交校验的进一步拆分，以及旧表兼容路径；必须保持重抽去重和积分原子记账。
2. 导出查询与工作簿渲染，保留现有 Excel 列、格式、权限与内存边界。
3. 题单编辑状态和对话框，保留协作题单与只读分享的权限差异。
4. 活跃 CSS 的模块化，必须保持级联顺序和多尺寸视觉回归；看似重复的选择器不一定可以直接删除。

测试、构建和本地模拟页面不替代真实服务器、真实 MySQL、外部平台和发布演练。
