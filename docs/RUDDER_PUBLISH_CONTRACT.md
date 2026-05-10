# Rudder 发布契约对接

本文档描述 rudder-dolphin 接收 Rudder 工作流发布请求的 wire 契约，以及为对齐该契约需要完成的开发改造。

## 1. 背景与边界

Rudder 端原本在 publisher 模块中将工作流模型翻译为 DolphinScheduler（下称 DS）shape 后再调用 SDK，导致 9 处 DS 适配逻辑（字段命名、拓扑约定、时间格式、默认值兜底）散落在 Rudder 一侧。本轮调整将边界归位：

- Rudder 端仅装配自身领域模型 (`WorkflowPublishBundle` / `ProjectPublishBundle`) 并通过 SDK 发出。
- rudder-dolphin 端负责将 Rudder 模型适配为 DS shape，承担全部 DS-specific 翻译。

调整后 Rudder 不再依赖 rudder-dolphin 的任何 wire DTO；rudder-dolphin 通过依赖 `rudder-publish-api` 模块直接接收 Rudder 的 bundle 类型。

## 2. 新 wire 契约

所有 bundle 类位于 `io.github.zzih.rudder.publish.api.bundle`，由 `rudder-publish-api` 提供。

工作流引用到的数据源（含连接信息与凭证）随每次工作流发布 piggyback 在 `WorkflowPublishBundle.datasources`，接收侧每次发布前先 upsert 自身数据源注册表，再处理任务定义。无独立的"环境同步"端点。

新增 Maven 依赖：

```xml
<dependency>
    <groupId>io.github.zzih</groupId>
    <artifactId>rudder-publish-api</artifactId>
    <version>${rudder.version}</version>
</dependency>
```

### 2.1 `WorkflowPublishBundle`

单工作流发布载荷。项目归属、发起人、环境对象（数据源）一律放顶层；工作流本体放 `workflow` 字段。

| 字段 | 类型 | 说明 |
|---|---|---|
| `projectCode` | `Long` | 所属项目 code |
| `projectName` | `String` | 项目名，nullable；为 null 时由 server 使用 `String.valueOf(projectCode)` 兜底 |
| `projectDescription` | `String` | 项目描述，nullable |
| `userName` | `String` | 发起发布的用户名 |
| `datasources` | `List<DatasourceBundle>` | 工作流引用到的数据源完整快照（已去重，含连接信息与明文凭证）。接收侧每次发布前据此 upsert 自身数据源注册表，详见 §2.8、§9 |
| `resources` | `List<ResourceBundle>` | 工作流引用到的资源完整内容（JAR / 配置文件 等，已去重，含字节）。详见 §2.9、§10 |
| `workflow` | `WorkflowBundle` | 工作流本体，详见 §2.2 |

### 2.2 `WorkflowBundle`

工作流本体。**不含项目归属、发起人、数据源**（这些都属于"环境/上下文"，归顶层 `WorkflowPublishBundle` / `ProjectPublishBundle` 承载，避免批量发布时重复）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `Long` | 工作流唯一标识（雪花 ID） |
| `name` | `String` | 工作流名称 |
| `description` | `String` | nullable |
| `dagJson` | `String` | DAG 原文 JSON，含节点 `label` / `position` 视觉信息，schema 见 §5 |
| `tasks` | `List<TaskBundle>` | 任务定义集合 |
| `edges` | `List<EdgeBundle>` | DAG 拓扑边 |
| `schedule` | `ScheduleBundle` | 调度配置，nullable |
| `globalParams` | `List<Property>` | 工作流全局参数 |

### 2.3 `ProjectPublishBundle`

项目级批量发布载荷。`datasources` 是**项目内所有工作流引用到的数据源跨工作流去重后的合并集**，由 Rudder 端在发布前一次性合并，接收侧无需自行去重。

| 字段 | 类型 | 说明 |
|---|---|---|
| `projectCode` | `Long` | |
| `projectName` | `String` | |
| `projectDescription` | `String` | |
| `userName` | `String` | |
| `datasources` | `List<DatasourceBundle>` | 项目内所有工作流引用到的数据源（跨 workflow 去重） |
| `resources` | `List<ResourceBundle>` | 项目内所有工作流引用到的资源完整内容（跨 workflow 去重） |
| `workflows` | `List<WorkflowBundle>` | 该项目下一并发布的工作流集合 |

### 2.4 `TaskBundle`

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskCode` | `Long` | 任务唯一标识 |
| `name` | `String` | 任务定义名 |
| `description` | `String` | nullable |
| `taskType` | `TaskType` | Rudder 枚举字面量，全集见 §3 |
| `scriptContent` | `String` | 任务执行配置 JSON 原文（控制流任务的配置同样置于此字段），schema 见 §4 |
| `retryTimes` | `Integer` | 失败重试次数 |
| `retryInterval` | `Integer` | 重试间隔（秒） |
| `timeout` | `Integer` | 超时（分钟），nullable |

### 2.5 `EdgeBundle`

| 字段 | 类型 | 说明 |
|---|---|---|
| `sourceTaskCode` | `Long` | 源节点 taskCode |
| `targetTaskCode` | `Long` | 目标节点 taskCode |

### 2.6 `ScheduleBundle`

| 字段 | 类型 | 说明 |
|---|---|---|
| `cronExpression` | `String` | 标准 cron 表达式 |
| `timezone` | `String` | nullable；为 null 时由 server 兜底默认 `Asia/Shanghai` |
| `startTime` | `LocalDateTime` | nullable，ISO-8601 序列化 |
| `endTime` | `LocalDateTime` | nullable，ISO-8601 序列化 |
| `status` | `String` | `ONLINE` / `OFFLINE`。接收侧据此决定是否在自身调度器中启用该 schedule（`OFFLINE` 时即使 cron 已配也不触发） |

### 2.7 `Property`

来自 `io.github.zzih.rudder.common.param.Property`，作为参数键值对载体。

| 字段 | 类型 | 说明 |
|---|---|---|
| `prop` | `String` | 参数 key |
| `direct` | `Direct` | 枚举：`IN` / `OUT` |
| `type` | `DataType` | 枚举：`VARCHAR` / `INTEGER` / `LONG` 等 |
| `value` | `String` | |

### 2.8 `DatasourceBundle`

数据源完整快照。SQL 类任务的 `scriptContent.dataSourceId` 是 Rudder 内部数值 ID，接收侧通过 `id` 在 `WorkflowPublishBundle.datasources` 中查到完整连接信息与凭证，据此在自身数据源注册表中按 `name` upsert。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `Long` | 与 `TaskBundle.scriptContent.dataSourceId` 对齐的 join key |
| `name` | `String` | 数据源名称，作为接收侧匹配自身数据源注册表的业务身份 |
| `type` | `String` | 引擎类型：`STARROCKS` / `MYSQL` / `HIVE` / `TRINO` 等 |
| `host` | `String` | |
| `port` | `Integer` | |
| `defaultPath` | `String` | JDBC URL host/port 后那一段：MySQL/PG → database，Hive → default schema，Trino → catalog |
| `params` | `String` | 额外连接参数 JSON 原文 |
| `credential` | `String` | **凭证 JSON 明文**（如 `{"username":"x","password":"y"}`），由 Rudder 在发出前 AES 解密。Wire 上为明文，依赖 HTTPS 保护。解密失败时为 null |

`datasources` 列表已按 `id` 去重；非 SQL 类任务（控制流 / SHELL / Python 等）不会贡献条目。

### 2.9 `ResourceBundle`

资源完整内容（含字节）。直接随 bundle 一起序列化，接收侧无需额外调用即可拿到全部数据。

| 字段 | 类型 | 说明 |
|---|---|---|
| `path` | `String` | Rudder FileStorage 中的相对路径（如 `demo-jars/spark/foo.jar`），仅供 Rudder 端定位用 |
| `name` | `String` | 文件名（如 `foo.jar`），接收侧据此命名 DS 资源中心的条目 |
| `size` | `Long` | 字节数 |
| `sha256` | `String` | 内容 SHA-256 hex（64 字符）。接收侧应在落盘前/后再次计算并比对，防网络损坏静默落盘 |
| `content` | `byte[]` | 文件字节内容。Jackson 序列化为 Base64 字符串 |

当前贡献方：`SPARK_JAR` / `FLINK_JAR` 任务的 `scriptContent.jarPath`。后续如有更多资源类型（配置文件 / Python 包等）按同一模式扩展。

> 注：当前为 KISS 实现——直接内联字节。大文件场景（GB 级）后续视实际负载再考虑流式 / dedup 等优化。

## 3. TaskType 枚举与 DS 映射表

server 必须维护以下映射表（即原先位于 Rudder 端的 `mapTaskType`）。

| Rudder TaskType | DS taskType | datasourceType |
|---|---|---|
| `HIVE_SQL` | `SQL` | `HIVE` |
| `STARROCKS_SQL` | `SQL` | `STARROCKS` |
| `MYSQL` | `SQL` | `MYSQL` |
| `DORIS_SQL` | `SQL` | `DORIS` |
| `POSTGRES_SQL` | `SQL` | `POSTGRES` |
| `CLICKHOUSE_SQL` | `SQL` | `CLICKHOUSE` |
| `TRINO_SQL` | `SQL` | `TRINO` |
| `SPARK_SQL` | `SQL` | `SPARK` |
| `FLINK_SQL` | `SQL` | `FLINK` |
| `SPARK_JAR` | `SPARK` | — |
| `FLINK_JAR` | `FLINK` | — |
| `PYTHON` | `PYTHON` | — |
| `SHELL` | `SHELL` | — |
| `HTTP` | `HTTP` | — |
| `SEATUNNEL` | `SEATUNNEL` | — |
| `CONDITION` | `CONDITIONS` | — |
| `SUB_WORKFLOW` | `SUB_PROCESS` | — |
| `SWITCH` | `SWITCH` | — |
| `DEPENDENT` | `DEPENDENT` | — |

注意 `CONDITION` 在 DS 端为复数 `CONDITIONS`；`SUB_WORKFLOW` 在 DS 端为 `SUB_PROCESS`。

## 4. `scriptContent` 格式与适配

所有任务（含控制流）的执行配置一律置于 `TaskBundle.scriptContent`。Server 在拼装 DS `taskParams` 时按 TaskType 进行以下转换。

### 4.1 SQL 类（`*_SQL` / `MYSQL`）

```json
{ "sql": "SELECT ...", "executionMode": "BATCH", "dataSourceId": 2 }
```

转换规则：
- 添加 `type` 字段，取值为 §3 表中 datasourceType。
- 重命名 `dataSourceId` → `datasource`。

### 4.2 `PYTHON` / `SHELL`

```json
{ "content": "#!/bin/bash\necho hi" }
```

转换规则：
- 重命名 `content` → `rawScript`。

### 4.3 `SEATUNNEL`

```json
{ "content": "env { ... }\nsource { ... }\nsink { ... }", "deployMode": "cluster" }
```

转换规则：
- 重命名 `content` → `rawScript`。
- 补 DS 必填字段 `useCustom: true` 与 `startupScript: "seatunnel.sh"`（Rudder 不暴露这些 DS-only 概念，server 兜底）。
- 其他字段（如 `deployMode`）原文透传。

### 4.4 控制流 `CONDITION`

```json
{
  "dependence": {
    "dependTaskList": [{ "dependItemList": [...], "relation": "AND" }],
    "relation": "AND"
  }
}
```

原文透传至 DS `taskParams`。

### 4.5 控制流 `SUB_WORKFLOW`

```json
{ "workflowDefinitionName": "data_mart_pipeline" }
```

Rudder 端在装配 bundle 时已把 `workflowDefinitionCode`（Long）翻译为 `workflowDefinitionName`（String）。接收侧拿到名称后在 DS 自身工作流注册表中按 name 解析为 DS 内部 ID。

### 4.6 控制流 `SWITCH`

原文透传。

### 4.7 控制流 `DEPENDENT`

```json
{
  "dependence": {
    "dependTaskList": [
      {
        "dependItemList": [
          {
            "dependentType": "DEPENDENT",
            "projectName": "data-mart",
            "workflowDefinitionName": "data_mart_pipeline",
            "depTaskName": "wait_etl_done",
            "cycle": "day",
            "dateValue": "today",
            "parameterPassing": false
          }
        ],
        "relation": "AND"
      }
    ],
    "relation": "AND",
    "checkInterval": 10,
    "failurePolicy": "DEPENDENT_FAILURE_WAITING",
    "failureWaitingTime": 30
  }
}
```

Rudder 端在装配 bundle 时已把每个 `dependItem` 的三个 code 字段翻译为 name：

| 旧字段（Code） | 新字段（Name） |
|---|---|
| `projectCode` (Long) | `projectName` (String) |
| `definitionCode` (Long) | `workflowDefinitionName` (String) |
| `depTaskCode` (Long) | `depTaskName` (String) |

接收侧按 name 在 DS 自身实体注册表中查得 DS 内部 ID 后，在 DS payload 中替换为对应字段。Rudder 端找不到对应实体时 name 字段会置 null（已记 warn 日志），接收侧应优雅降级或拒绝。

### 4.8 `SPARK_JAR` / `FLINK_JAR` / `HTTP`

原文透传。

## 5. `dagJson` 格式

```json
{
  "nodes": [
    {
      "taskCode": "4000012",
      "label": "wait_etl_done",
      "position": { "x": 100, "y": 200 }
    }
  ],
  "edges": [
    { "source": "4000012", "target": "4000013" }
  ]
}
```

Server 在拼装 DS `locations`（节点坐标）时从 `nodes[].position` 提取。

注意 `dagJson` 内的 `taskCode` 为 String 类型（Rudder 端使用 `ToStringSerializer` 防止 JS 数值精度丢失），server 解析时需要转回 long。

## 6. SDK 改造清单（`rudder-dolphin-client`）

| # | 改动 |
|---|---|
| 1 | `pom.xml` 增加 `rudder-publish-api` 依赖 |
| 2 | `rudderClient.publishWorkflow` 签名调整为 `void publishWorkflow(WorkflowPublishBundle bundle)` |
| 3 | `rudderClient.publishProject` 签名调整为 `void publishProject(ProjectPublishBundle bundle)` |
| 4 | 删除 `WorkflowPublishRequest` |
| 5 | 删除 `ProjectPublishRequest` |
| 6 | 删除 `WorkflowParam` |
| 7 | 删除 `TaskDefinitionParam` |
| 8 | 删除 `TaskRelationParam` |
| 9 | 删除 `GlobalParam` |
| 10 | 删除 `ScheduleParam` |
| 11 | HTTP 序列化对齐：bundle 类基于 Lombok `@Data`，需启用 Jackson `JavaTimeModule` 以支持 `LocalDateTime` 字段（ISO-8601）；`byte[]` 字段默认按 Base64 解码即可。 |

## 7. 服务端改造清单（`rudder-dolphin-service` / Controller 层）

| # | 改动 | 来源（Rudder 端原位置） |
|---|---|---|
| 1 | Controller 入参由 `WorkflowPublishRequest` 改为 `WorkflowPublishBundle` | — |
| 2 | Controller 入参由 `ProjectPublishRequest` 改为 `ProjectPublishBundle` | — |
| 3 | TaskType 翻译表（§3） | `rudderDolphinPublisher#mapTaskType` |
| 4 | SQL 任务 `taskParams` 添加 `type` 字段（=datasourceType） | `buildTaskParams` |
| 5 | SQL 任务 `dataSourceId → datasource` 字段重命名 | `buildTaskParams` |
| 6 | `PYTHON` / `SHELL` 任务 `content → rawScript` 字段重命名 | `buildTaskParams` |
| 7 | `taskRelations` 拓扑：起始节点（无入边）`preTaskName` 设为 null。从 `workflow.edges` 推导 | `buildTaskRelations` |
| 8 | `Property → DS GlobalParam` 字段拷贝（同名 prop / value / direct / type） | `convertGlobalParams` |
| 9 | `ScheduleBundle.startTime` / `endTime`（`LocalDateTime`）转 DS 期望的 `yyyy-MM-dd HH:mm:ss` 字符串 | `convertSchedule` |
| 9.1 | `ScheduleBundle.status`：`ONLINE` 时启用 schedule（DS `releaseState=ONLINE`），`OFFLINE` 时仅保存配置不触发 | 全新 |
| 10 | `ScheduleBundle.timezone` 为 null 时兜底 `Asia/Shanghai` | `convertSchedule` |
| 11 | `projectName` 为 null 时使用 `String.valueOf(projectCode)` 兜底 | `publishWorkflow` |
| 12 | DS `locations`：解析 `workflow.dagJson.nodes[].position`，转换为 DS 期望格式 | 全新（Rudder 端先前未传输节点坐标） |
| 13 | 数据源 upsert：发布开始前先把 `bundle.datasources` 全量推到 DS 数据源注册表（按 `name` 匹配，create-or-update）；其后将 SQL 任务 `scriptContent.dataSourceId` 翻译为接收侧自身数据源标识。详见 §9 | 全新（Rudder 端不再做环境同步，发布即环境就绪） |
| 14 | 资源处理：从 `bundle.resources[].content` 拿到字节，写入 DS 资源中心；JAR 任务 `scriptContent.jarPath` 翻译为 DS 资源中心路径。详见 §10 | 全新（Rudder 端先前未传输资源） |
| 15 | 控制流 name 解析：`SUB_WORKFLOW` / `DEPENDENT` scriptContent 中已是 name（非 code），接收侧需按 name 在 DS 自身实体注册表查 DS 内部 ID。详见 §4.5 / §4.7 | 全新（Rudder 端先前下发的是内部 code，对 DS 无意义） |

## 8. 端到端样例

Rudder 端发出的 `WorkflowPublishBundle`（节选）：

```json
{
  "projectCode": 1000002,
  "projectName": "data-mart",
  "projectDescription": "数据集市加工",
  "userName": "admin",
  "datasources": [
    {
      "id": 2,
      "name": "prod_starrocks",
      "type": "STARROCKS",
      "host": "starrocks.prod.internal",
      "port": 9030,
      "defaultPath": "warehouse",
      "params": "{\"useSSL\":\"true\"}",
      "credential": "{\"username\":\"rudder_rw\",\"password\":\"s3cret\"}"
    }
  ],
  "resources": [
    {
      "path": "demo-jars/spark/spark-examples_2.12-3.5.8.jar",
      "name": "spark-examples_2.12-3.5.8.jar",
      "size": 1234567,
      "sha256": "9c0a7c8f2b...（64 字符 hex）",
      "content": "UEsDBBQACAg...（Base64 编码字节）"
    }
  ],
  "workflow": {
    "code": 3000002,
    "name": "data_mart_pipeline",
    "description": "数据集市加工",
    "dagJson": "{\"nodes\":[{\"taskCode\":\"4000012\",\"label\":\"wait_etl_done\",\"position\":{\"x\":100,\"y\":200}}],\"edges\":[{\"source\":\"4000012\",\"target\":\"4000013\"}]}",
    "tasks": [
      {
        "taskCode": 4000012,
        "name": "wait_etl_done",
        "taskType": "DEPENDENT",
        "scriptContent": "{\"dependence\":{\"dependTaskList\":[{\"dependItemList\":[{\"dependentType\":\"DEPENDENT\",\"projectName\":\"data-warehouse\",\"workflowDefinitionName\":\"daily_etl\",\"depTaskName\":\"data_validation\",\"cycle\":\"day\",\"dateValue\":\"today\",\"parameterPassing\":false}],\"relation\":\"AND\"}],\"relation\":\"AND\",\"checkInterval\":10,\"failurePolicy\":\"DEPENDENT_FAILURE_WAITING\",\"failureWaitingTime\":30}}",
        "retryTimes": 0,
        "retryInterval": 30
      },
      {
        "taskCode": 4000014,
        "name": "mart_aggregate",
        "taskType": "STARROCKS_SQL",
        "scriptContent": "{\"sql\":\"SELECT ...\",\"executionMode\":\"BATCH\",\"dataSourceId\":2}",
        "timeout": 90
      }
    ],
    "edges": [
      { "sourceTaskCode": 4000012, "targetTaskCode": 4000013 }
    ],
    "schedule": {
      "cronExpression": "0 0 5 * * ?",
      "timezone": "Asia/Shanghai",
      "startTime": "2026-01-01T00:00:00",
      "endTime": "2027-12-31T23:59:59",
      "status": "ONLINE"
    },
    "globalParams": [
      { "prop": "dt", "direct": "IN", "type": "VARCHAR", "value": "2026-04-05" }
    ]
  }
}
```

`ProjectPublishBundle` 形态（顶层项目信息 + 顶层 datasources + workflows 列表，每个 workflow 不再带 datasources）：

```json
{
  "projectCode": 1000002,
  "projectName": "data-mart",
  "projectDescription": "数据集市加工",
  "userName": "admin",
  "datasources": [
    { "id": 2, "name": "prod_starrocks", "type": "STARROCKS", ... },
    { "id": 5, "name": "ods_mysql", "type": "MYSQL", ... }
  ],
  "workflows": [
    { "code": 3000002, "name": "data_mart_pipeline", ... },
    { "code": 3000003, "name": "realtime_flink_pipeline", ... }
  ]
}
```

server 适配后 `mart_aggregate` 在 DS `taskParams` 中的形态（其中 `datasource` 字段值已由接收侧按 `dataSourceId=2` → `bundle.datasources[id=2].name="prod_starrocks"` → 接收侧数据源注册表查得 DS 数据源 ID `17`）：

```json
{
  "sql": "SELECT ...",
  "executionMode": "BATCH",
  "type": "STARROCKS",
  "datasource": 17
}
```

## 9. 数据源处理

每次发布开始前，接收侧须先按 `bundle.datasources` 把环境就绪好，再处理任务定义。

无论 `WorkflowPublishBundle` 还是 `ProjectPublishBundle`，`datasources` 都在顶层并已去重；接收侧无需关心是否单工作流、是否跨 workflow 合并，只需顺序消费这一个列表。

### 9.1 upsert 自身数据源注册表

遍历 `bundle.datasources` 中每个 `DatasourceBundle`：

1. 在自身数据源注册表（DS 数据源表 `t_ds_datasource`）中按 `name` 匹配。
2. 命中且字段全等 → 跳过。
3. 命中但字段有变化（host / port / credential / 等） → update，凭证字段以接收侧自身加密机制重新加密落库（**不应原文落库**）。
4. 未命中 → create，凭证同样重新加密。
5. `bundle.datasources` 中不存在但接收侧已有的数据源 → **不删**（可能是接收侧手工配的、外部独有的）。
6. `DatasourceBundle.type` 与已存在同名数据源的类型不一致 → WARN + 拒绝更新（避免误改外部独有数据源）。

### 9.2 SQL 任务 dataSourceId 解析

每个 SQL 任务的 `scriptContent.dataSourceId` 是 Rudder 内部数值 ID，按以下方式翻译：

1. 在 `bundle.datasources` 中按 `id` 查到对应 `DatasourceBundle`。
2. 取该 `DatasourceBundle.name` 作为业务身份，在 §9.1 已 upsert 后的接收侧数据源注册表中按名称查得**接收侧的数值 ID**。
3. 在 DS payload `taskParams.datasource` 字段填接收侧 ID。
4. 未命中（极少数边界场景，应该不会发生因为 §9.1 已经 upsert 过）→ 抛明确异常并中止发布。

### 9.3 约束

- `bundle.datasources` 已按 `id` 去重，接收侧可放心建立 `Map<Long, DatasourceBundle>` 索引。
- 非 SQL 类任务（控制流 / SHELL / Python / JAR / SeaTunnel / HTTP）不会贡献条目。
- `bundle.datasources` 为空集且存在 SQL 任务 → Rudder 端工作流配置异常，接收侧应拒绝发布。
- `credential` 为 null（凭证解密失败）时建议拒绝该数据源 upsert，避免落入认证失败的死状态。
- upsert 应在事务内完成；upsert 失败应中止整个发布流程，不应继续到任务发布步骤。

## 10. 资源处理（JAR 等）

资源字节直接随 bundle 一起到达，接收侧从 `bundle.resources[].content` 拿到字节即可，无须额外调用。

### 10.1 JAR 任务 jarPath 解析

`SPARK_JAR` / `FLINK_JAR` 任务的 `scriptContent.jarPath` 是 Rudder FileStorage 的相对路径，对接收侧无意义。接收侧按以下方式处理：

1. 在 `bundle.resources` 中按 `path` 查到对应 `ResourceBundle`，取出 `content` 字节。
2. 校验 SHA-256：对 `content` 重新计算 hash 并与 `sha256` 字段比对，不一致即拒绝该次发布（防网络/序列化损坏）。
3. 写入 DS 资源中心（HDFS / 本地文件系统等），得到 DS 资源中心的路径。
4. 在 DS payload `taskParams` 中把 `jarPath` 替换为该 DS 路径（具体字段名按 DS 期望调整，例如 SPARK 任务的 `mainJar`）。

### 10.2 约束

- `bundle.resources` 已按 `path` 去重，接收侧可放心建立 `Map<String, ResourceBundle>`（key = path）。
- 当前贡献方仅 `SPARK_JAR` / `FLINK_JAR`；其他任务类型不会贡献条目。
- `bundle.resources` 为空集且存在 JAR 任务 → Rudder 端工作流配置异常，接收侧应拒绝发布。

### 10.3 已知限制

当前为 KISS 实现，字节直接 inline 到 bundle JSON：

- 大文件（百 MB+）payload 会膨胀（Base64 ≈ +33%），HTTP request 体积偏大。
- 同一 JAR 多次发布会重复传输（无 dedup）。

GB 级或高频发布场景出现性能瓶颈时，再升级为流式 / sha256 dedup 设计。

## 11. 验证清单

- Rudder 端升级 SDK 后执行 `./mvnw -pl rudder-spi/rudder-publish/rudder-publish-rudder-dolphin compile` 应通过。
- 端到端：Rudder 发布一个包含控制流（`DEPENDENT` + `SUB_WORKFLOW`）+ SQL + SHELL + JAR 节点的工作流，DS 上应显示完整任务配置与节点坐标。
- DS UI 节点 label 应与 Rudder 编辑器一致（采用 `dagJson.nodes[].label`，而非 `tasks[].name`）。
- `schedule.timezone` 为 null 时不应报错。
- `projectName` 为 null（仅有 `projectCode`）时不应报错。
- SQL 任务在接收侧数据源注册表内能找到匹配项时发布成功；未命中时应明确报错。
- JAR 任务发布后，DS 资源中心应能找到同名文件，工作流能正常运行。

## 12. 命名与不变量约定

- wire 字段一律 camelCase（Lombok `@Data` 配合 Jackson 默认）。
- `taskCode` / `workflowCode` / `projectCode` 在 bundle 顶层为 Long；`dagJson` 内嵌为 String（前端精度保护），server 解析时需做类型还原。
- `taskType` 为 Rudder 枚举字面量（如 `STARROCKS_SQL`），不是 DS 的 `SQL`。
- 所有 nullable 字段为 null 表示"未设置"；server 不应将 null 与空串混用。
- `DatasourceBundle.id` 是 Rudder 内部 ID，仅用作 join key；跨系统业务身份请使用 `name`。
- `DatasourceBundle.credential` 在 wire 上为明文，依赖 HTTPS；接收侧落库时须以自身加密机制重新加密。
- `ResourceBundle.path` 是 Rudder 内部 storage 路径，仅 Rudder 端定位用；接收侧应以 `name` 作为业务身份。
- 控制流任务（`SUB_WORKFLOW` / `DEPENDENT`）的 scriptContent 内引用其他工作流 / 项目 / 任务一律以 **name** 为业务身份，Rudder 端已完成 code → name 翻译。无需接收侧理解 Rudder 内部 code。
- `ResourceBundle.content` Jackson 序列化为 Base64；当前简单内联，大文件性能瓶颈出现时再优化为流式。
