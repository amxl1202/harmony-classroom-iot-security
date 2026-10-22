# 智能教室安防系统 · 后端（ClassGuard Backend）

面向校园教室的安防监护后端：接收 RK2206 设备端经 MQTT 上报的烟雾/红外/门磁数据，落库并推送给鸿蒙 APP；
同时接受 APP 的控制指令，经 MQTT QoS1 下发到设备并同步等待执行回执。

南向（设备）走 MQTT，北向（APP）走 REST，后端是两端唯一的汇合点。

## 技术栈

| 层 | 选型 |
|---|---|
| 运行时 | JDK 17、Spring Boot 3.0.2 |
| 持久层 | MyBatis 3.0 + MySQL 8.0 |
| 消息 | Eclipse Paho MQTT 1.2.5（MQTT 3.1.1，broker 用 EMQX 5.3） |
| 鉴权 | JWT（jjwt 0.11.5，HS256）+ BCrypt 口令哈希 |
| 测试 | JUnit 5 + Mockito（15 个用例） |

## 目录结构

```
src/main/java/com/example/classguard/
├── config/        拦截器、JWT 工具、MQTT 配置、启动自检、Jackson 序列化
├── controller/    REST 接口（auth / alarms / devices / records / users / invites）
├── service/       业务逻辑；impl/ 下含 MQTT 消息分发器
├── mapper/        MyBatis Mapper 接口（XML 在 resources/mappers）
├── entity/ dto/   表实体与传输对象
├── constant/      指令白名单枚举
└── exception/     ErrorCode 与统一异常
sql/upgrade_v0.5_alarm_dedup.sql   告警幂等键升级脚本（幂等可重复执行）
```

## 快速开始

1. **建库与账号**：执行 `classroom_security_init.sql` 建库建表，`classroom_security_user.sql` 创建最小权限账号
   `classroom_app`（仅 SELECT/INSERT/UPDATE，不给 DDL/DELETE）。
2. **配置本地口令**：
   ```bash
   # 在项目根目录执行
   cp application-local.properties.example application-local.properties
   # 填入 DB 口令、EMQX 口令、JWT 密钥（openssl rand -hex 32）
   ```
   真实口令不进版本库；也可改用环境变量 `CLASSROOM_DB_PASSWORD` / `CLASSROOM_MQTT_PASSWORD` / `CLASSROOM_JWT_SECRET`。
3. **启动 broker 与应用**：
   ```bash
   emqx start                      # 默认 TCP 11883（见 application.properties 的 mqtt.broker）
   mvn spring-boot:run
   ```
   启动自检会依次校验 MySQL 连通、预置管理员、EMQX 订阅，任一失败即终止启动
   （演示环境不希望"带病运行"导致告警丢失）；离线调试可加 `--app.startup-check.enabled=false`。
4. **跑测试**：`mvn test`（`app.startup-check.enabled=false` 已配在 test profile，不依赖真实 MySQL/EMQX）。

## 关键约定

**REST 响应壳**：`{"code":0,"message":"success","data":...}`。错误码：1001 参数错误、1002 账号不存在、
1003 密码错误、1004 账号已存在、1005 邀请码无效、2001 未登录或 token 失效、2003 无权限、
3001 告警不存在、3002 设备指令执行超时、3003 邀请码不存在、5000 服务器内部错误。

**MQTT 主题**（`{cid}`=教室号、`{did}`=设备号）：

| 主题 | 方向 | QoS |
|---|---|---|
| `classroom/{cid}/device/{did}/data` | 设备 → 后端/APP | 0 |
| `classroom/{cid}/device/{did}/status` | 设备 → 后端/APP | 1（retain，含 LWT） |
| `classroom/{cid}/device/{did}/alarm` | 设备 → 后端/APP | 0 |
| `classroom/{cid}/device/{did}/cmd` | 后端 → 设备 | 1 |
| `classroom/{cid}/device/{did}/cmd_resp` | 设备 → 后端/APP | 1 |

## 三个值得说明的设计点

**1. 告警幂等键不是 alarm_id 单键，而是 (alarm_id, 接收时间)**
设备端无 RTC，`alarm_id` 的日期段与序号都靠上电后的内存计数，断电重启必然撞号。若用 `alarm_id` 单键
唯一索引，重启后的第一条同类告警会被当成重复消息永久丢弃。现在唯一索引是 `(alarm_id, created_at)`，
入库时只判"10 秒窗口内是否已有同 alarm_id"——窗口内重复上报仍丢弃（挡住设备重传），
超出窗口则允许新增（重启后的新告警能落库）。
迁移脚本：`sql/upgrade_v0.5_alarm_dedup.sql`。

**2. MQTT 回调里不做业务**
Paho 的 `messageArrived` 运行在收包线程上，回调不返回就不会发 PUBACK，QoS1 会不断重投。
所以回调只把消息投递到单线程有界队列（容量 1000，满了由调用线程兜底执行），
解码与入库在 `dispatch()` 里完成，异常就地吞掉并记日志，不打断收包线程。

**3. 指令下发先注册等待、后发布**
API-09 是同步语义：后端发布 cmd（QoS1）后在 3 秒内等 `cmd_resp`，超时返回 3002 并在错误体里带 `cmd_id`。
顺序必须是"先注册 CompletableFuture → 再 publish"，否则回执可能早于等待注册到达而丢失唤醒。
超时后不立即删除下发上下文，而是保留 5 分钟——晚到的回执仍能补录入执行历史；
上下文由每分钟一次的惰性清扫回收，避免内存堆积。

## 相关文档

设计与契约文档随课程交付单独维护（需求分析、系统设计、数据库设计、REST 契约、MQTT 契约、测试用例）。
本仓库只保留代码与数据库脚本。
