# NovelToScript

将小说章节改编为影视剧本的 Web 应用。支持按章流式生成、多轮改编、YAML 结构化输出与作品管理。

## 功能

- 按章输入小说原文，流式生成中文 YAML 剧本
- 多轮改编对话，保留历史版本并可切换回旧稿
- 人物设定跨章节共享，生成时自动注入 Prompt
- 作品 / 章节 / 剧本结果持久化与历史加载
- LLM 多模型路由、流式降级与 Trace 链路

## 技术栈

### 后端核心

| 类别 | 技术 | 版本 / 说明 |
|------|------|-------------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 4.0.6 |
| Web | Spring MVC | REST API + `SseEmitter` 流式响应 |
| 安全 | Spring Security | 登录 / 注册 / 会话鉴权 |
| ORM | MyBatis-Plus | 3.5.7（`mybatis-plus-spring-boot3-starter`） |
| 数据库驱动 | MySQL Connector/J | 运行时依赖 |
| JSON / YAML | Jackson | `jackson-databind`、`jackson-dataformat-yaml` |
| AOP | Spring AOP + AspectJ | LLM Trace 注解式埋点 |
| 工具 | Lombok | 简化样板代码 |
| 并发上下文 | Transmittable Thread Local | 2.14.5，跨线程传递 Trace 上下文 |
| 构建 | Maven | Wrapper：`mvnw` / `mvnw.cmd` |

### 业务模块（`com.zkp.my12306.ntc`）

| 模块 | 职责 |
|------|------|
| `controller` | HTTP 接口：剧本生成、改编、作品、人物、Trace 查询 |
| `service` | 业务编排：按章生成、多轮改编、作品与记录持久化 |
| `script` | 剧本领域：Prompt 构建、YAML 解析、Schema 校验、流式退化守卫 |
| `llm` | 大模型抽象：Provider 适配、模型路由、流式执行、SSE 解析 |
| `auth` / `config` | 会话安全、UTF-8 编码、MyBatis 配置 |
| `dto` | 请求 / 响应数据传输对象 |

### 大模型（LLM）层

| 能力 | 实现 |
|------|------|
| 统一接口 | `LLMService` / `ChatClient` SPI |
| Provider 适配 | SiliconFlow、DeepSeek、OpenAI、Ollama、百炼（阿里云） |
| 协议 | OpenAI 兼容 `POST /v1/chat/completions` |
| 路由与降级 | `ModelSelector` + `ModelRoutingExecutor`，按 priority  failover |
| 熔断 | 失败阈值 + 开路时长（`ai.selection`） |
| 流式 | 专用线程池（`llm-stream-*`）、首包超时、SSE 事件解析 |
| 流式协议 | `open` / `meta` / `token` / `warn` / `artifact` / `done` / `error` |
| 链路追踪 | `LlmTraceAspect` + `t_llm_trace_run` / `t_llm_trace_node` 持久化 |
| 配置 | `application.yml` → `ai.providers` / `ai.chat` / `ai.generation` |

### 数据与存储

| 组件 | 用途 |
|------|------|
| MySQL | 主库：用户、作品、章节记录、改编消息、人物、LLM Trace |
| 核心表 | `t_script_work`、`t_script_record`、`t_script_message`、`t_character`、`t_llm_trace_*` |
| 建表脚本 | `src/main/resources/database/schema_mysql.sql`（全量） |
| 开发重置 | `src/main/resources/database/dev_reset_mysql.sql`（删表后重跑 schema） |
| 会话 | Spring Security HttpSession（`application.yml` 可配置 Redis 会话扩展） |

### 前端

| 类别 | 技术 | 版本 / 说明 |
|------|------|-------------|
| 框架 | Vue | ^3.5 |
| 路由 | Vue Router | ^4.5 |
| 构建 | Vite | ^7.0 |
| 插件 | `@vitejs/plugin-vue` | ^6.0 |
| 样式 | 原生 CSS | 无 UI 组件库，自定义工作台样式 |
| 通信 | Fetch API | REST + SSE 流式读取 |
| 本地状态 | `localStorage` | 工作台草稿、侧边栏折叠状态 |
| 开发代理 | Vite Dev Server | `5173` → 后端 `8080`，流式接口禁用缓冲 |

### 测试

| 类别 | 技术 |
|------|------|
| 单元 / 集成 | JUnit 5、Spring Boot Test |
| 安全测试 | `spring-security-test`（`@WithMockUser`） |
| 内存数据库 | H2（测试环境替代 MySQL） |
| Mock | Mockito（Service 层单测） |

### 架构示意

```
Vue 工作台 ──REST/SSE──▶ Spring Boot API
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
               MyBatis-Plus  LLM路由   Prompt/YAML
                    │         │         解析校验
                    ▼         ▼
                 MySQL    多Provider API
```

## 本地运行

### 1. 数据库

新库直接执行全量建表脚本（含用户表、业务表、默认 admin 账号）：

```bash
mysql -u root -p < src/main/resources/database/schema_mysql.sql
```

开发环境需要清空业务数据时：

```bash
mysql -u root -p < src/main/resources/database/dev_reset_mysql.sql
mysql -u root -p < src/main/resources/database/schema_mysql.sql
```

### 2. 后端

在 `src/main/resources/application.yml` 配置数据源与 `ai.chat` 模型 API Key，然后：

```bash
./mvnw spring-boot:run
```

默认端口：`8080`

### 3. 前端

```bash
cd frontend
npm install
npm run dev
```

开发地址：`http://localhost:5173`（`/api` 代理到 `8080`）

## 目录说明

```
src/main/resources/
  prompt/          # 生成 / 改编 Prompt 模板
  schema/          # YAML 样例（sample_chapter_fragment.yaml）
  database/        # MySQL 全量建表与开发重置脚本
frontend/          # Vue 工作台
docs/prompt/       # Prompt 维护说明
```

## YAML 输出

生成与改编均输出**按章剧本片段** YAML，Schema 以 `src/main/resources/schema/sample_chapter_fragment.yaml` 为准。

维护 Prompt 时请编辑 `src/main/resources/prompt/` 与上述样例文件。

