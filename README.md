# Rudder Dolphin

**Rudder → DolphinScheduler 工作流发布适配器**

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-green.svg)](https://spring.io/projects/spring-boot)
[![DolphinScheduler](https://img.shields.io/badge/DolphinScheduler-3.4.1-purple.svg)](https://dolphinscheduler.apache.org/)

中文 | English

---

把 [Rudder](https://github.com/Zzih/rudder) 的发布契约 (`ProjectPublishBundle` / `WorkflowPublishBundle`) 翻译成对 DolphinScheduler REST API 的一系列调用。Rudder 不感知 DolphinScheduler,只发出契约定义的 bundle;Rudder Dolphin 负责把 bundle 拆成数据源同步、资源上传、工作流定义装配、调度上下线等步骤,支持失败回滚。

```
┌─────────┐   ProjectPublishBundle   ┌────────────────┐   REST API   ┌──────────────────┐
│ Rudder  │ ───────────────────────▶ │ Rudder Dolphin │ ───────────▶ │ DolphinScheduler │
└─────────┘   WorkflowPublishBundle  └────────────────┘              └──────────────────┘
```

## 核心特性

- **契约驱动**:Rudder 不依赖 DolphinScheduler 任何类型,通过 `rudder-publish-api` 的 bundle 描述发布意图;DS 适配完全在本服务里。
- **事务性回滚**:发布走 handler 责任链,任一步失败按已执行顺序逆向回滚(删工作流、还原项目元信息、恢复调度状态)。
- **跨实体引用按 name**:`SUB_WORKFLOW` 子工作流、`DEPENDENT` 跨工作流/任务依赖在 bundle 里用 name 表达,适配层翻译成 DS 的 code,跨发布批次也成立。
- **多任务类型**:覆盖 `SHELL` / `SQL`(MySQL/Hive/StarRocks/Doris/ClickHouse/Trino/Spark/Flink)/ `SPARK_JAR` / `FLINK_JAR` / `PYTHON` / `HTTP` / `SEATUNNEL` / `CONDITION` / `SWITCH` / `SUB_WORKFLOW` / `DEPENDENT`。
- **资源中心同步**:JAR 资源通过 `ResourceBundle` 携带字节流,适配层按 rudder 的 path 结构在 DS 资源中心建目录树并上传,SHA-256 校验防损坏。
- **数据源同步**:bundle 里的 `DatasourceBundle` 在发布前 upsert 到 DS 资源中心(按 hash 跳过未变更),SQL 任务的 `dataSourceId` 在适配层翻译成 DS 注册表里的 id。
- **多用户 token**:按 `bundle.userName` 匹配 DS 用户的 access token 调 DS API,匹配失败降级 admin token,不串权限。
- **轻量 Client SDK**:`RudderDolphinClient` 提供 Spring Boot starter,上游服务依赖 `rudder-dolphin-client` 即可。

## 快速开始

### 环境

- JDK 21+
- MySQL 8.0+
- DolphinScheduler 3.4.x(已配置 admin token)

### 构建运行

```bash
git clone https://github.com/Zzih/rudder-dolphin.git
cd rudder-dolphin
./mvnw clean package -DskipTests

cp .env.example .env
# 编辑 .env,填 DS URL / token / DB 连接

java -jar rudder-dolphin-api/target/rudder-dolphin-api-*.jar
```

默认端口 `12348`,可通过 `RUDDER_DOLPHIN_PORT` 修改。

### 配置项

```bash
# DolphinScheduler
RUDDER_DOLPHIN_DS_URL=http://localhost:12345/dolphinscheduler
RUDDER_DOLPHIN_DS_TOKEN=<ds-admin-token>
RUDDER_DOLPHIN_DS_RESOURCE_BASE_DIR=rudder      # DS 资源中心子目录,默认 rudder

# 数据库
RUDDER_DOLPHIN_DB_URL=jdbc:mysql://127.0.0.1:3306/rudder_dolphin?...
RUDDER_DOLPHIN_DB_USERNAME=rudder
RUDDER_DOLPHIN_DB_PASSWORD=rudder123

# 服务认证(留空则不启用)
RUDDER_DOLPHIN_AUTH_TOKEN=
```

完整变量表见 [`.env.example`](.env.example) 和 [`docs/USAGE.md`](docs/USAGE.md)。

### API 文档

启动后访问:

- Swagger UI:`http://localhost:12348/swagger-ui.html`
- Scalar:`http://localhost:12348/scalar`
- OpenAPI JSON:`http://localhost:12348/v3/api-docs`

## API 入口

| 端点 | 用途 |
|---|---|
| `POST /publish/project` | 项目级全量发布:数据源同步 + 资源上传 + 工作流批量增删改 + 调度上下线 |
| `POST /publish/workflow` | 单工作流增量发布:在已存在项目内新建/更新一条工作流及其调度 |

请求体严格按 Rudder 发布契约,见 [`docs/RUDDER_PUBLISH_CONTRACT.md`](docs/RUDDER_PUBLISH_CONTRACT.md)。

## Client SDK

上游服务通过 `rudder-dolphin-client` 调用,依赖只引一个轻量模块,**不会**把 DolphinScheduler 类传进调用方:

```xml
<dependency>
    <groupId>io.github.zzih</groupId>
    <artifactId>rudder-dolphin-client</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
rudder-dolphin:
  client:
    url: http://localhost:12348
    token: ${RUDDER_DOLPHIN_AUTH_TOKEN:}
```

```java
@Resource
private RudderDolphinClient client;

public void publish(ProjectPublishBundle bundle) {
    client.publishProject(bundle);
}
```

## 架构

```
PublishService
  └─ ProjectPublishStrategy (handler 责任链 + 逆序回滚)
       │
       ├─ PublishBeforeHandler        初始化上下文,匹配用户 token,拉旧工作流
       ├─ EnvSyncHandler              upsert 数据源到 DS
       ├─ ResourceSyncHandler         按 rb.path 建目录树 + 上传 jar/资源
       ├─ OfflineWorkflowHandler      下线待更新工作流
       ├─ CreateProjectHandler        新建项目(仅 IS_NEW_PROJECT)
       ├─ UpdateProjectHandler        更新项目元信息
       ├─ ExistWorkflowHandler        按 name 拆分新增 / 更新两组
       ├─ CreateWorkflowHandler       新建工作流
       ├─ UpdateWorkflowHandler       更新工作流定义
       ├─ CreateScheduleHandler       新工作流的调度(按 schedule.status 决定是否上线)
       ├─ UpdateScheduleHandler       已有工作流的调度变更
       └─ OnlineWorkflowHandler       上线全部本次涉及的工作流
```

任一 handler 抛 `BizException`,strategy 反向遍历已执行 handler 调 `rollBack()` —— 删除新建工作流、还原旧 DAG / 项目元信息、恢复调度上下线状态。详细每条 handler 的回滚语义见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

## 模块结构

| 模块 | 职责 |
|---|---|
| `rudder-dolphin-common` | 异常、统一响应、ThreadLocal 上下文工具 |
| `rudder-dolphin-domain` | 发布结果 DTO |
| `rudder-dolphin-dao` | MyBatis-Plus 数据访问层 |
| `rudder-dolphin-service` | 发布策略 / handler 链 / DS 客户端 / 任务参数 builder / 资源 & 数据源同步器 |
| `rudder-dolphin-api` | Spring Boot 入口、控制器、拦截器、全局异常处理 |
| `rudder-dolphin-client` | 上游服务集成用的 SDK + Spring Boot 自动配置 |

依赖方向:`api → service → dao → domain → common`,`client → publish-api(rudder)`。

## 文档

- [架构 (`docs/ARCHITECTURE.md`)](docs/ARCHITECTURE.md) — handler 链、回滚机制、DS 客户端 token 策略
- [使用 (`docs/USAGE.md`)](docs/USAGE.md) — API 字段、配置项、SDK 集成示例
- [发布契约 (`docs/RUDDER_PUBLISH_CONTRACT.md`)](docs/RUDDER_PUBLISH_CONTRACT.md) — Rudder ↔ Rudder Dolphin 之间的 bundle 字段定义和翻译规则

## 贡献

主分支为 `dev`,所有 PR 以 `dev` 为目标分支。dev 受规则保护:禁止删除、禁止 force-push、必须线性历史、必须走 PR。

```bash
./mvnw spotless:apply   # 格式化
./mvnw compile          # 提交前会自动跑 spotless:check
```

Commit Message 走 [Conventional Commits](https://www.conventionalcommits.org/),示例:

```
feat(publish): translate workflowDefinitionName to DS code
fix(resource): preserve rudder path hierarchy on upload
chore(deps): bump dolphinscheduler to 3.4.1
```

## License

[Apache License 2.0](LICENSE)。提交 Pull Request 即视为以同等条款授权所贡献的代码。
