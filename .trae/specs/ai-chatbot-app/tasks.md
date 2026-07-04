# AI Chatbot App - The Implementation Plan (Decomposed and Prioritized Task List)

## [/] Task 1: 项目基础架构搭建
- **Priority**: high
- **Depends On**: None
- **Description**: 
  - 添加项目依赖：Room、Retrofit、OkHttp、Coroutines、DataStore、Navigation Compose、Coil、Markdown 渲染等
  - 建立应用包结构：data、domain、presentation 分层
  - 配置 ProGuard 规则
  - 配置 Application 类
- **Acceptance Criteria Addressed**: NFR-5, NFR-6
- **Test Requirements**:
  - `programmatic` TR-1.1: 项目可成功编译通过
  - `programmatic` TR-1.2: 所有依赖正确引入且无版本冲突
  - `human-judgement` TR-1.3: 包结构清晰，符合 MVVM 分层架构
- **Notes**: 参考 gpt_mobile 的架构设计，使用现代 Android 开发最佳实践

## [ ] Task 2: 数据库层设计与实现 (Room)
- **Priority**: high
- **Depends On**: Task 1
- **Description**: 
  - 定义实体类：Provider、Model、Agent、Conversation、Message
  - 定义 DAO 接口：ProviderDao、ModelDao、AgentDao、ConversationDao、MessageDao
  - 配置 AppDatabase
  - 实现 Repository 层
  - 添加数据库迁移支持
- **Acceptance Criteria Addressed**: AC-1, AC-3, AC-4, AC-5, AC-6, AC-11
- **Test Requirements**:
  - `programmatic` TR-2.1: 所有实体类和 DAO 定义正确
  - `programmatic` TR-2.2: 数据库增删改查操作正确
  - `programmatic` TR-2.3: 应用重启后数据持久化保留
- **Notes**: 表关系：Provider 1:N Model, Agent 1:N Conversation, Conversation 1:N Message

## [ ] Task 3: 网络层设计与实现
- **Priority**: high
- **Depends On**: Task 1
- **Description**: 
  - 定义 OpenAI 兼容 API 接口：Chat Completions、Models List
  - 实现 Retrofit Service 接口
  - 配置 OkHttp 客户端（超时、拦截器、Header 处理）
  - 实现 SSE 流式请求支持
  - 实现 API 数据模型（DTO）
- **Acceptance Criteria Addressed**: AC-2, AC-8, AC-14
- **Test Requirements**:
  - `programmatic` TR-3.1: `/v1/models` 接口可正确调用并解析返回
  - `programmatic` TR-3.2: `/v1/chat/completions` 流式接口可正确处理 SSE
  - `programmatic` TR-3.3: API Key 通过 Header 正确传递
- **Notes**: 使用 OkHttp 的 EventSource 或自定义 SSE 解析器处理流式响应

## [ ] Task 4: 主题与基础 UI 组件
- **Priority**: high
- **Depends On**: Task 1
- **Description**: 
  - 实现 Material You 动态配色主题
  - 实现深色/浅色主题切换
  - 定义通用 UI 组件（按钮、输入框、卡片、列表项等）
  - 实现底部导航栏
  - 配置 Navigation Compose 导航图
- **Acceptance Criteria Addressed**: AC-10, AC-12, FR-25, FR-27, FR-28
- **Test Requirements**:
  - `programmatic` TR-4.1: 底部导航三个页面可正常切换
  - `human-judgement` TR-4.2: 深色/浅色主题显示正常，文字清晰可读
  - `human-judgement` TR-4.3: Material You 动态配色生效
- **Notes**: 参考 Telegram 配色风格，用户消息气泡在右侧蓝色，AI 消息在左侧灰色

## [ ] Task 5: Provider 管理模块
- **Priority**: high
- **Depends On**: Task 2, Task 3
- **Description**: 
  - Provider 列表页面
  - 添加/编辑 Provider 页面（名称、Base URL、API Key）
  - Provider 详情页（模型列表管理）
  - Provider 增删改查业务逻辑
  - API Key 安全存储
- **Acceptance Criteria Addressed**: AC-1, FR-1, FR-2, FR-5, NFR-7
- **Test Requirements**:
  - `programmatic` TR-5.1: Provider 添加/编辑/删除功能正常
  - `programmatic` TR-5.2: API Key 加密存储，明文不暴露
  - `human-judgement` TR-5.3: 表单验证提示友好（必填项、URL 格式等）
- **Notes**: API Key 使用 EncryptedSharedPreferences 或 Room + SQLCipher 加密存储

## [ ] Task 6: 模型管理模块
- **Priority**: high
- **Depends On**: Task 5
- **Description**: 
  - 模型列表页面（Provider 下）
  - 手动添加模型对话框（模型 ID、显示名称）
  - 从 `/v1/models` 同步模型功能
  - 模型选择保存界面
  - 模型启用/禁用切换
- **Acceptance Criteria Addressed**: AC-2, AC-3, FR-3, FR-4, FR-6, FR-7, FR-8, FR-9
- **Test Requirements**:
  - `programmatic` TR-6.1: 手动添加模型功能正常
  - `programmatic` TR-6.2: 调用 `/v1/models` 接口成功获取模型列表
  - `programmatic` TR-6.3: 模型可选择性保存到本地
  - `programmatic` TR-6.4: 模型启用/禁用状态正确保存
- **Notes**: 同步模型时展示加载状态和错误提示

## [ ] Task 7: Agent 管理模块
- **Priority**: high
- **Depends On**: Task 6
- **Description**: 
  - Agent 列表页面
  - 添加/编辑 Agent 页面（名称、头像、系统提示词、默认模型、温度、Top P、max tokens）
  - 默认标准 Agent 初始化（首次启动时）
  - Agent 增删改查业务逻辑
  - 模型选择器组件（从已启用的模型中选择）
- **Acceptance Criteria Addressed**: AC-4, AC-5, FR-10, FR-11, FR-12, FR-13
- **Test Requirements**:
  - `programmatic` TR-7.1: Agent 添加/编辑/删除功能正常
  - `programmatic` TR-7.2: 首次启动自动创建标准 Agent
  - `programmatic` TR-7.3: Agent 配置参数正确保存和读取
  - `human-judgement` TR-7.4: Agent 列表 UI 美观，信息展示清晰
- **Notes**: 标准 Agent 使用默认系统提示词"You are a helpful assistant."

## [ ] Task 8: 对话列表模块
- **Priority**: high
- **Depends On**: Task 7
- **Description**: 
  - 对话列表页面（按 Agent 过滤）
  - 新建对话功能
  - 对话重命名、删除
  - 对话列表按时间倒序排列，显示最近消息预览
  - Agent 切换选择器
- **Acceptance Criteria Addressed**: AC-6, FR-14, FR-15, FR-16, FR-17
- **Test Requirements**:
  - `programmatic` TR-8.1: 对话创建/重命名/删除功能正常
  - `programmatic` TR-8.2: 对话仅显示当前 Agent 下的对话
  - `programmatic` TR-8.3: 对话列表按时间倒序排列
  - `programmatic` TR-8.4: 列表项显示最近消息预览和时间
- **Notes**: 对话列表是首页，显示在底部导航第一个 tab

## [ ] Task 9: 聊天页面 UI (Telegram 风格)
- **Priority**: high
- **Depends On**: Task 4, Task 8
- **Description**: 
  - 聊天页面整体布局（顶部栏、消息列表、底部输入区）
  - 用户消息气泡（右侧，蓝色背景，圆角）
  - AI 消息气泡（左侧，灰色/浅色背景，圆角）
  - 消息时间显示
  - 底部输入框（多行输入、发送按钮）
  - 消息列表自动滚动到底部
  - 消息状态指示器（发送中、已发送、错误）
- **Acceptance Criteria Addressed**: AC-7, AC-9, FR-18, FR-20, FR-21, FR-26
- **Test Requirements**:
  - `human-judgement` TR-9.1: 聊天界面类似 Telegram 风格，气泡左右分布
  - `programmatic` TR-9.2: 消息列表自动滚动到最新消息
  - `programmatic` TR-9.3: 输入框支持多行文本和发送
  - `human-judgement` TR-9.4: 消息状态指示器清晰可辨
- **Notes**: 参考 Telegram 的气泡样式和颜色，确保视觉效果接近

## [ ] Task 10: 聊天业务逻辑实现
- **Priority**: high
- **Depends On**: Task 3, Task 8, Task 9
- **Description**: 
  - 发送消息业务逻辑
  - 流式接收 AI 回复（SSE 解析 + 实时更新 UI）
  - 消息本地存储
  - 消息状态管理（sending/sent/error）
  - 重新生成回复功能
  - 中断生成功能
  - 复制消息功能
  - ViewModel 实现
- **Acceptance Criteria Addressed**: AC-8, AC-9, AC-13, AC-14, FR-18, FR-19, FR-22, FR-23, FR-24
- **Test Requirements**:
  - `programmatic` TR-10.1: 发送消息后可收到 AI 回复
  - `programmatic` TR-10.2: 流式输出逐块显示，非一次性完整显示
  - `programmatic` TR-10.3: 中断生成后回复停止，保留已生成内容
  - `programmatic` TR-10.4: 重新生成可替换原 AI 回复
  - `programmatic` TR-10.5: 消息状态正确流转（sending → sent/error）
- **Notes**: 使用 Kotlin Flow 实现流式数据传递，确保 UI 响应式更新

## [ ] Task 11: 设置页面
- **Priority**: medium
- **Depends On**: Task 4
- **Description**: 
  - 设置页面布局
  - 主题切换（浅色/深色/跟随系统）
  - Provider 管理入口
  - 关于信息
  - 清除数据选项
- **Acceptance Criteria Addressed**: AC-12, FR-25
- **Test Requirements**:
  - `programmatic` TR-11.1: 主题切换功能正常，即时生效
  - `human-judgement` TR-11.2: 设置页面布局清晰，分类合理
- **Notes**: 设置页面在底部导航第三个 tab

## [ ] Task 12: 应用集成与整体测试
- **Priority**: high
- **Depends On**: Task 10, Task 11
- **Description**: 
  - 端到端流程测试
  - 边界条件测试（空列表、网络错误等）
  - 内存泄漏检查
  - 性能优化
  - UI 细节打磨
- **Acceptance Criteria Addressed**: NFR-1, NFR-2, NFR-3, NFR-4
- **Test Requirements**:
  - `programmatic` TR-12.1: 完整流程可走通（配置 Provider → 创建 Agent → 发送消息 → 收到回复）
  - `programmatic` TR-12.2: 网络异常时有友好的错误提示
  - `human-judgement` TR-12.3: 整体使用流畅，无明显卡顿
- **Notes**: 确保所有模块集成后工作正常
