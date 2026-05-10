# Rudder Dolphin 使用文档

Rudder Dolphin 是 DolphinScheduler 的上层工作流发布服务，提供项目发布、工作流发布、任务发布三种 API，支持事务性回滚。

## 快速开始

### 环境要求

- Java 21+
- MySQL 8.0+
- DolphinScheduler 3.4.1

### 配置

复制 `.env.example` 为 `.env` 并填写：

```bash
# 数据库
RUDDER_DOLPHIN_DB_URL=jdbc:mysql://127.0.0.1:3306/rudder_dolphin?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true
RUDDER_DOLPHIN_DB_USERNAME=rudder
RUDDER_DOLPHIN_DB_PASSWORD=rudder123

# DolphinScheduler
RUDDER_DOLPHIN_DS_URL=http://localhost:12345/dolphinscheduler
RUDDER_DOLPHIN_DS_TOKEN=your-ds-admin-token

# 可选：API 认证（留空则不启用鉴权）
RUDDER_DOLPHIN_AUTH_TOKEN=

# 可选：飞书通知
RUDDER_DOLPHIN_NOTIFICATION_LARK_WEBHOOK=
```

### 启动

```bash
mvn clean package -DskipTests
java -jar rudder-dolphin-api/target/rudder-dolphin-api-0.1.0-SNAPSHOT.jar
```

默认端口 `12348`，可通过 `RUDDER_DOLPHIN_PORT` 环境变量修改。

### API 文档

启动后访问：

- Knife4j UI：http://localhost:12348/doc.html
- Swagger UI：http://localhost:12348/swagger-ui.html
- OpenAPI JSON：http://localhost:12348/v3/api-docs

---

## API 接口

所有接口均为 `POST` 请求，Content-Type 为 `application/json`。

如果配置了 `RUDDER_DOLPHIN_AUTH_TOKEN`，请求需要携带 Header：

```
token: your-rudder-dolphin-auth-token
```

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": null,
  "timestamp": 1712880000000
}
```

错误码：

| code  | 含义                        |
|-------|-----------------------------|
| 200   | 成功                        |
| 400   | 请求参数校验失败            |
| 401   | 未授权                      |
| 404   | 资源不存在                  |
| 500   | 服务器内部错误              |
| 10001 | DolphinScheduler API 调用失败 |
| 10002 | 项目不存在                  |
| 10003 | 工作流不存在                |
| 10004 | 发布失败                    |

---

### 1. 项目发布（全量）

全量发布项目下所有工作流及调度。项目不存在时自动创建。

```
POST /publish/project
```

**请求体：**

```json
{
  "projectName": "my-project",
  "description": "项目描述",
  "userName": "admin",
  "workflows": [
    {
      "name": "daily-etl",
      "description": "每日 ETL 工作流",
      "globalParams": [
        {
          "prop": "dt",
          "value": "${system.biz.date}",
          "direct": "IN",
          "type": "VARCHAR"
        }
      ],
      "timeout": 3600,
      "taskDefinitions": [
        {
          "name": "extract-task",
          "taskType": "SHELL",
          "taskParams": {
            "rawScript": "echo hello"
          },
          "workerGroup": "default",
          "retryTimes": 2,
          "retryInterval": 60,
          "timeout": 1800
        }
      ],
      "taskRelations": [
        {
          "preTaskName": null,
          "postTaskName": "extract-task"
        }
      ],
      "schedule": {
        "crontab": "0 0 2 * * ? *",
        "startTime": "2024-01-01 00:00:00",
        "endTime": "2099-12-31 23:59:59",
        "timezoneId": "Asia/Shanghai"
      }
    }
  ]
}
```

**字段说明：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| projectName | String | 是 | DolphinScheduler 项目名称 |
| description | String | 否 | 项目描述 |
| userName | String | 是 | 发布用户名（对应 DS 用户，用于匹配用户 token） |
| workflows | List | 是 | 工作流列表，至少一个 |

**WorkflowParam 字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 工作流名称 |
| description | String | 否 | 工作流描述 |
| globalParams | List\<GlobalParam\> | 否 | 全局参数 |
| timeout | Integer | 否 | 超时时间（秒），默认 0（不超时） |
| taskDefinitions | List\<TaskDefinitionParam\> | 是 | 任务定义列表 |
| taskRelations | List\<TaskRelationParam\> | 是 | 任务依赖关系 |
| schedule | ScheduleParam | 否 | 调度配置，为空则不创建调度 |

**ScheduleParam 字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| crontab | String | 是 | Cron 表达式，如 `0 0 2 * * ? *` |
| startTime | String | 否 | 生效开始时间，默认 `2024-01-01 00:00:00` |
| endTime | String | 否 | 生效结束时间，默认 `2099-12-31 23:59:59` |
| timezoneId | String | 否 | 时区，默认 `Asia/Shanghai` |

**TaskDefinitionParam 字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 任务名称 |
| description | String | 否 | 任务描述 |
| taskType | String | 是 | 任务类型（SHELL、SQL、DATAX 等） |
| taskParams | Map | 否 | 任务参数（根据 taskType 不同而异） |
| workerGroup | String | 否 | Worker 分组，默认 default |
| retryTimes | Integer | 否 | 重试次数 |
| retryInterval | Integer | 否 | 重试间隔（秒） |
| timeout | Integer | 否 | 任务超时（秒） |

**TaskRelationParam 字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| preTaskName | String | 否 | 上游任务名称，null 表示根节点 |
| postTaskName | String | 是 | 下游任务名称 |

---

### 2. 工作流发布（增量）

增量发布单个工作流及调度。项目必须已存在。

```
POST /publish/workflow
```

**请求体：**

```json
{
  "projectName": "my-project",
  "description": "项目描述",
  "userName": "admin",
  "workflow": {
    "name": "daily-etl",
    "description": "每日 ETL",
    "taskDefinitions": [...],
    "taskRelations": [...],
    "schedule": {
      "crontab": "0 0 2 * * ? *"
    }
  }
}
```

与项目发布的区别：`workflow` 为单个对象而非数组。

---

### 3. 任务发布

更新指定工作流的任务列表。项目和工作流必须已存在。

```
POST /publish/task
```

**请求体：**

```json
{
  "projectName": "my-project",
  "userName": "admin",
  "workflowName": "daily-etl",
  "taskDefinitions": [
    {
      "name": "extract-task",
      "taskType": "SHELL",
      "taskParams": {
        "rawScript": "echo updated"
      }
    }
  ],
  "taskRelations": [
    {
      "preTaskName": null,
      "postTaskName": "extract-task"
    }
  ]
}
```

---

## Client SDK

Rudder Dolphin 提供 Java Client SDK，可直接集成到上游服务中。

### 引入依赖

```xml
<dependency>
    <groupId>io.github.zzih</groupId>
    <artifactId>rudder-dolphin-client</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Spring Boot 自动配置

```yaml
rudder-dolphin:
  client:
    url: http://localhost:12348
    token: your-rudder-dolphin-auth-token  # 可选
```

注入使用：

```java
@Resource
private RudderDolphinClient rudderDolphinClient;

public void publish() {
    ProjectPublishRequest request = new ProjectPublishRequest();
    request.setProjectName("my-project");
    request.setUserName("admin");
    request.setWorkflows(List.of(...));
    rudderDolphinClient.publishProject(request);
}
```

### 手动创建

```java
RudderDolphinClient client = new RudderDolphinClient("http://localhost:12348", "your-token");
client.publishProject(request);
```

---

## 用户 Token 机制

Rudder Dolphin 支持按发布用户匹配 DolphinScheduler 用户 token：

1. 请求中的 `userName` 会与 DS 用户列表匹配
2. 匹配成功后，使用该用户的 Access Token 操作 DS API
3. 匹配失败时降级为 admin token

**前提条件：** DS 中需提前为用户创建 Access Token（安全中心 > 令牌管理）。

---

## 注意事项

- 工作流名称在项目内唯一，同名工作流会被更新而非新建
- 发布过程中任一步骤失败会自动回滚已执行的操作
- `schedule` 为空时不创建/更新调度，已有调度不受影响
- 任务发布仅更新任务定义和依赖关系，不修改工作流元信息和调度
