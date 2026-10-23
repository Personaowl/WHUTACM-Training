# WHUT-ACM 训练平台

武汉理工大学 ACM 协会的校内训练平台，用于日常刷题、比赛跟踪与现役队员训练管理。

仓库：[Personaowl/WHUTACM-Training](https://github.com/Personaowl/WHUTACM-Training) · 当前开发与发布分支：`mty-dev`。

## 核心功能

- 每日一题：Easy / Hard 两档、提交校验、积分和历史记录。
- 自主练习与题单：按难度和标签抽题，管理个人题单；区分公共投稿题单与管理员只读专题分享。
- 补题计划：个人待补题清单，手动完成与撤销、备注编辑，并可收藏到题单。
- 竞赛账号与数据：验证绑定 Codeforces / AtCoder / 洛谷 / 牛客；现有统计接入 CF、ABC 与平台每日题记录。
- 比赛信息：聚合四个平台的比赛安排；成员可通过共享题单直接分享题目。
- 周训练记录：汇总每日题与每周 ABC 完成情况，支持历史查询和特殊情况说明。
- 管理后台：现役队员训练看板、用户管理、情况说明审核及 Excel 导出。

具体积分规则、ABC 要求和角色权限见 [功能与权限说明](docs/features.md)。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端 | React 18、Vite 8、React Router 7、Tailwind CSS 4、Radix UI |
| 后端 | Spring Boot 3.3.5、JdbcTemplate、Maven |
| 数据存储 | MySQL 8 |
| 生产运行 | Nginx、systemd；支持临时 HTTP/IP 与域名 HTTPS 部署 |

推荐开发环境：Java 21、Node.js 22（至少 22.12）、MySQL 8。

## 项目结构

```text
backend/                  后端接口、业务服务、数据访问与测试
frontend/                 前端页面、组件与接口请求
deploy/                   环境变量、Nginx 和 systemd 模板
docs/                     功能、开发、部署、迁移与运维文档
.github/workflows/ci.yml   持续集成检查
```

## 快速开始

先安装上述开发环境，并按 [本地开发指南](docs/development.md) 配置 MySQL 连接。

获取项目并启动后端：

```bash
git clone --branch mty-dev https://github.com/Personaowl/WHUTACM-Training.git
cd WHUTACM-Training
mvn -f backend/pom.xml spring-boot:run
```

另开终端，在项目根目录启动前端：

```bash
cd frontend
npm ci
npm run dev
```

前端默认地址为 `http://localhost:5173`，后端为 `http://localhost:8080`。本地账号、环境变量、竞赛账号绑定及验证构建步骤均见开发指南。生产部署不要沿用开发凭据。

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [文档索引](docs/README.md) | 按用途选择阅读顺序 |
| [功能与权限](docs/features.md) | 功能模块、积分、ABC、周训练和角色权限 |
| [接口参考](docs/api-design.md) | 当前路由、鉴权、参数、响应与兼容入口 |
| [数据库结构](docs/db-design.md) | 当前表、关键字段、约束与数据流 |
| [本地开发](docs/development.md) | 环境准备、本地启动、账号绑定、测试与构建 |
| [生产部署](docs/deployment.md) | 新服务器安装、环境变量、HTTP/IP 与 HTTPS 配置 |
| [服务器与数据库迁移](docs/migration.md) | 电脑中转、备份校验、数据库恢复、切换与回退 |
| [发布与日常运维](docs/operations.md) | 更新发布、健康检查、备份要求、会话与性能排查 |
| [维护记录](docs/changelog.md) | 已归档的稳定性加固和依赖审计说明 |

文档已按当前实现核对；后续发布与运维事项见 [维护检查清单](docs/todolist.md)。仓库模板不等于服务器上的实际配置，部署后仍需验收。
