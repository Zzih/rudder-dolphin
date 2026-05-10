# Rudder Dolphin 架构文档

## 概述

Rudder Dolphin 是 DolphinScheduler 的上层工作流发布服务，位于 Rudder 和 DolphinScheduler 之间：

```
Rudder → RudderDolphin → DolphinScheduler REST API
```

核心职责：将上游系统的发布请求转化为对 DolphinScheduler 的一系列 API 调用，并提供事务性回滚保障。

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 3.5.13 |
| MyBatis-Plus | 3.5.9 |
| DolphinScheduler DAO | 3.4.1 |
| Springdoc OpenAPI | 2.8.6 |
| Knife4j | 4.5.0 |
| MySQL | 8.0+ |

## 模块结构

```
rudder-dolphin (parent)
├── rudder-dolphin-common     核心工具类、常量、异常、统一响应
├── rudder-dolphin-domain     DTO、请求对象、值对象
├── rudder-dolphin-dao        数据访问层（MyBatis-Plus）
├── rudder-dolphin-service    业务逻辑：策略、处理器链、DS 客户端
├── rudder-dolphin-api        REST API 入口、拦截器、异常处理
└── rudder-dolphin-client     轻量级 SDK，供外部服务集成
```

**依赖关系：**

```
rudder-dolphin-api → rudder-dolphin-service → rudder-dolphin-dao → rudder-dolphin-domain → rudder-dolphin-common
rudder-dolphin-client → rudder-dolphin-domain
```

---

## 核心设计模式

### 1. 策略模式（Strategy Pattern）

根据发布类型选择不同的策略，每种策略组装不同的处理器链。

```
PublishService
├── publishProject()  → ProjectPublishStrategy
├── publishWorkflow() → ProjectPublishStrategy
└── publishTask()     → TaskPublishStrategy
```

### 2. 责任链模式（Chain of Responsibility）

每个策略维护一个有序的 Handler 列表，顺序执行。任一 Handler 失败时逆序回滚。

```java
public abstract class AbstractPublishStrategy {
    protected List<PublishHandler> handlers;

    public void publish() {
        List<PublishHandler> executed = new ArrayList<>();
        try {
            for (PublishHandler handler : handlers) {
                if (handler.isInterrupt()) break;
                if (!handler.canHandle()) continue;
                handler.handle();
                executed.add(handler);
            }
        } catch (Exception e) {
            // 逆序回滚
            Collections.reverse(executed);
            for (PublishHandler h : executed) {
                h.rollBack();
            }
            throw e;
        } finally {
            ThreadParamMapUtils.clear();
        }
    }
}
```

### 3. 上下文传递（ThreadLocal Context）

Handler 之间通过 `ThreadParamMapUtils`（ThreadLocal\<Map\<String, Object\>\>）传递上下文数据，键定义在 `PublishConstants` 中。

```
PROJECT_CODE       → long       项目 code
IS_NEW_PROJECT     → boolean    是否新建项目
OLD_WORKFLOW_LIST  → List       旧工作流列表
WORKFLOW_ADD_LIST  → List       需新建的工作流
WORKFLOW_UPDATE_LIST → List     需更新的工作流
ACCESS_TOKEN       → String     用户 DS token
...
```

---

## 处理器链

### Handler 接口

```java
public interface PublishHandler {
    void handle();              // 执行操作
    void rollBack();            // 回滚操作
    boolean canHandle();        // 是否需要执行
    boolean isInterrupt();      // 是否中断链
}
```

### 项目发布链（ProjectPublishStrategy）

```
 1. PublishBeforeHandler      初始化上下文，查询旧数据，设置用户 token
 2. OfflineWorkflowHandler    下线目标工作流（更新前需先下线）
 3. CreateProjectHandler      创建新项目（仅 IS_NEW_PROJECT=true）
 4. UpdateProjectHandler      更新项目元信息（仅全量发布）
 5. ExistWorkflowHandler      区分新增/更新工作流列表
 6. CreateWorkflowHandler     创建新工作流
 7. UpdateWorkflowHandler     更新已有工作流
 8. CreateScheduleHandler     为新工作流创建调度
 9. UpdateScheduleHandler     更新已有工作流的调度
10. OnlineWorkflowHandler     上线所有工作流
11. PublishAfterHandler       发布完成日志
```

### 任务发布链（TaskPublishStrategy）

```
1. PublishBeforeHandler        初始化上下文
2. ExistWorkflowTaskHandler    查找目标工作流
3. OfflineWorkflowHandler      下线目标工作流
4. UpdateWorkflowTaskListHandler  更新任务定义和关系
5. OnlineWorkflowHandler       上线工作流
6. PublishAfterHandler         发布完成日志
```

---

## 回滚机制

每个 Handler 在 `handle()` 中执行操作前会保存旧状态到 ThreadLocal，`rollBack()` 时恢复。

| Handler | 回滚行为 |
|---------|----------|
| CreateProjectHandler | 删除已创建的项目 |
| UpdateProjectHandler | 恢复旧的项目名称和描述 |
| CreateWorkflowHandler | 逐个删除已创建的工作流 |
| UpdateWorkflowHandler | 从缓存的 DagData 恢复旧工作流定义 |
| CreateScheduleHandler | 下线并删除已创建的调度 |
| UpdateScheduleHandler | 删除新创建的调度 + 恢复旧调度配置 |
| OfflineWorkflowHandler | 重新上线被下线的工作流 |
| OnlineWorkflowHandler | 重新下线被上线的工作流 |
| UpdateWorkflowTaskListHandler | 从缓存的 DagData 恢复旧任务定义 |

**回滚流程：**

```
Handler1.handle() ✓
Handler2.handle() ✓
Handler3.handle() ✗ → 异常
                ↓
Handler2.rollBack()
Handler1.rollBack()
                ↓
ThreadParamMapUtils.clear()
                ↓
抛出异常给调用方
```

---

## DolphinScheduler 客户端

`DolphinSchedulerClient` 封装了所有 DS REST API 调用。

### Token 策略

```
┌────────────────────────────┐
│ ThreadLocal 有用户 token？  │
│                            │
│   有 → 使用用户 token       │  ← 业务接口（项目/工作流/调度 CRUD）
│   无 → 使用 admin token     │
└────────────────────────────┘

┌────────────────────────────┐
│ 始终使用 admin token        │  ← listUsers()、getAccessTokens()
└────────────────────────────┘
```

用户 token 在 `PublishBeforeHandler.setToken()` 中设置：
1. 用 admin token 调用 `GET /users/list` 获取所有 DS 用户
2. 按 `userName` 匹配发布用户
3. 调用 `GET /access-tokens/user/{userId}` 获取该用户 token
4. 存入 `ThreadParamMapUtils.put(ACCESS_TOKEN, token)`
5. 匹配失败时降级到 admin token

### API 端点映射

| 方法 | DS API 端点 |
|------|-------------|
| queryProjectByName | GET /projects?searchVal={name} |
| createProject | POST /projects |
| updateProject | PUT /projects/{code} |
| deleteProject | DELETE /projects/{code} |
| listWorkflows | GET /projects/{code}/workflow-definition/list |
| queryWorkflowByCode | GET /projects/{code}/workflow-definition/{wfCode} |
| createWorkflow | POST /projects/{code}/workflow-definition |
| updateWorkflow | PUT /projects/{code}/workflow-definition/{wfCode} |
| deleteWorkflow | DELETE /projects/{code}/workflow-definition/{wfCode} |
| releaseWorkflow | POST /projects/{code}/workflow-definition/{wfCode}/release |
| listSchedules | GET /projects/{code}/schedules?pageSize=9999 |
| createSchedule | POST /projects/{code}/schedules |
| updateSchedule | PUT /projects/{code}/schedules/{id} |
| onlineSchedule | POST /projects/{code}/schedules/{id}/online |
| offlineSchedule | POST /projects/{code}/schedules/{id}/offline |
| deleteSchedule | DELETE /projects/{code}/schedules/{id} |
| listUsers | GET /users/list |
| getAccessTokens | GET /access-tokens/user/{userId} |

### 超时配置

- 连接超时：10 秒
- 读取超时：30 秒

---

## 发布流程详解

### 项目发布（全量）

```
请求 POST /publish/project
  │
  ├─ projectName="my-project"
  ├─ userName="admin"
  └─ workflows=[wf1, wf2]
      │
      ▼
  PublishBeforeHandler
      │ 查询 DS 项目是否存在
      │ 匹配用户 token
      │ 拉取旧工作流和 DAG 数据
      ▼
  项目存在？
  ├─ 是 → OfflineWorkflowHandler（下线旧工作流）
  │       → UpdateProjectHandler（更新项目信息）
  │       → ExistWorkflowHandler
  │           │ 按名称对比 → 新增列表 + 更新列表
  │           ▼
  │       → CreateWorkflowHandler（创建新工作流）
  │       → UpdateWorkflowHandler（更新已有工作流）
  │
  └─ 否 → CreateProjectHandler（创建新项目）
         → ExistWorkflowHandler（全部为新增）
         → CreateWorkflowHandler（创建所有工作流）

      │
      ▼
  CreateScheduleHandler（为新工作流创建调度）
  UpdateScheduleHandler（为已有工作流更新调度）
  OnlineWorkflowHandler（上线所有工作流）
      │
      ▼
  返回 Result.ok()
```

### 任务发布

```
请求 POST /publish/task
  │
  ├─ projectName="my-project"
  ├─ workflowName="daily-etl"
  └─ taskDefinitions=[...]
      │
      ▼
  PublishBeforeHandler
      │ 项目必须存在，否则报错
      ▼
  ExistWorkflowTaskHandler
      │ 按 workflowName 查找目标工作流
      ▼
  OfflineWorkflowHandler（下线工作流）
      ▼
  UpdateWorkflowTaskListHandler（更新任务定义和关系）
      ▼
  OnlineWorkflowHandler（上线工作流）
      ▼
  返回 Result.ok()
```

---

## 包结构

```
io.github.zzih.rudder.dolphin
├── api
│   ├── RudderDolphinApplication.java           启动类
│   ├── advice
│   │   └── GlobalExceptionHandler.java 全局异常处理
│   ├── config
│   │   └── WebConfig.java              拦截器配置
│   ├── controller
│   │   └── PublishController.java      发布 API
│   └── interceptor
│       └── AuthInterceptor.java        Token 认证拦截器
├── common
│   ├── constants
│   │   └── PublishConstants.java       上下文键常量
│   ├── entity
│   │   └── BaseEntity.java            数据库实体基类
│   ├── enums
│   │   └── SystemErrorCode.java       系统错误码
│   ├── exception
│   │   └── BizException.java          业务异常
│   ├── result
│   │   ├── ErrorCode.java             错误码接口
│   │   └── Result.java                统一响应
│   └── utils
│       └── ThreadParamMapUtils.java    ThreadLocal 上下文
├── domain
│   ├── dto
│   │   ├── ProjectPublishDto.java
│   │   ├── TaskPublishDto.java
│   │   └── WorkflowPublishDto.java
│   └── qo
│       ├── GlobalParam.java
│       ├── ProjectPublishRequest.java
│       ├── ScheduleParam.java
│       ├── TaskDefinitionParam.java
│       ├── TaskPublishRequest.java
│       ├── TaskRelationParam.java
│       ├── WorkflowParam.java
│       └── WorkflowPublishRequest.java
├── service
│   ├── client
│   │   └── DolphinSchedulerClient.java DS REST 客户端
│   ├── enums
│   │   └── PublishErrorCode.java       发布错误码
│   └── publish
│       ├── PublishService.java
│       ├── PublishServiceImpl.java
│       ├── handler
│       │   ├── PublishHandler.java              接口
│       │   ├── AbstractPublishHandler.java      基类
│       │   ├── PublishBeforeHandler.java         初始化
│       │   ├── CreateProjectHandler.java         创建项目
│       │   ├── UpdateProjectHandler.java         更新项目
│       │   ├── ExistWorkflowHandler.java         工作流分类
│       │   ├── ExistWorkflowTaskHandler.java     任务发布定位
│       │   ├── CreateWorkflowHandler.java        创建工作流
│       │   ├── UpdateWorkflowHandler.java        更新工作流
│       │   ├── UpdateWorkflowTaskListHandler.java 更新任务列表
│       │   ├── OfflineWorkflowHandler.java       下线工作流
│       │   ├── OnlineWorkflowHandler.java        上线工作流
│       │   ├── CreateScheduleHandler.java        创建调度
│       │   ├── UpdateScheduleHandler.java        更新调度
│       │   └── PublishAfterHandler.java          完成处理
│       ├── strategy
│       │   ├── AbstractPublishStrategy.java      策略基类
│       │   ├── ProjectPublishStrategy.java       项目发布策略
│       │   └── TaskPublishStrategy.java          任务发布策略
│       └── util
│           └── ScheduleJsonBuilder.java          调度 JSON 构建
└── client
    ├── RudderDolphinClient.java                SDK 客户端
    ├── RudderDolphinClientAutoConfiguration.java 自动配置
    ├── RudderDolphinClientProperties.java       配置属性
    └── RudderDolphinException.java             SDK 异常
```
