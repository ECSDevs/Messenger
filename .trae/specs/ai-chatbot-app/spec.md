# AI Chatbot App - Product Requirement Document

## Overview
- **Summary**: 在 Android mobile 模块中构建一个功能完整的 AI 聊天机器人应用，支持自定义模型 Provider、多 Agent 管理、对话组织，以及 Telegram 风格的即时通讯 UI。
- **Purpose**: 为用户提供一个灵活、可定制的 AI 聊天工具，允许用户自由配置不同的 AI 模型服务商和 Agent，满足多样化的对话需求。
- **Target Users**: 需要使用多个 AI 模型、希望自定义 Agent 行为、偏好 IM 风格聊天界面的 Android 用户。

## Goals
- 支持添加和管理自定义模型 Provider（兼容 OpenAI 兼容 API）
- 支持通过 `/v1/models` 接口自动获取模型列表，也支持手动添加模型 ID
- 支持多 Agent 管理，每个 Agent 可独立配置系统提示词和 API 参数
- 对话按 Agent 归类组织，每个 Agent 下可有多个对话
- 提供类似 Telegram 风格的聊天界面，体验流畅
- 数据本地持久化存储

## Non-Goals (Out of Scope)
- Wear OS 版本（仅实现 mobile 模块）
- 语音通话/视频通话功能
- 端到端加密
- 云端同步/账户系统
- 多模态（图片/文件）支持（初期仅文本）
- 插件/扩展系统

## Background & Context
- 项目基于 Android Jetpack Compose，使用 Kotlin 开发
- 现有 mobile 模块为基础模板，仅有 Hello World 页面
- 参考项目 gpt_mobile (https://github.com/taewan-p/gpt_mobile) 提供了架构和 UI 参考
- 采用 OpenAI 兼容 API 标准，可对接多种后端服务

## Functional Requirements

### Provider 管理
- **FR-1**: 用户可以添加自定义 Provider，包含名称、API Base URL、API Key
- **FR-2**: 用户可以编辑和删除已添加的 Provider
- **FR-3**: 每个 Provider 支持手动添加模型 ID
- **FR-4**: 每个 Provider 支持通过调用 `/v1/models` 接口自动获取并同步模型列表
- **FR-5**: Provider 列表页面展示所有已配置的 Provider

### 模型管理
- **FR-6**: 每个 Provider 下可管理多个模型
- **FR-7**: 模型可设置显示名称和模型 ID
- **FR-8**: 支持启用/禁用特定模型
- **FR-9**: 从 `/v1/models` 同步的模型可选择性保存

### Agent 管理
- **FR-10**: 支持创建、编辑、删除 Agent
- **FR-11**: 默认提供一个"标准 Agent"
- **FR-12**: 每个 Agent 可配置：名称、头像、系统提示词、默认模型、温度 (temperature)、Top P、最大输出 token 数
- **FR-13**: Agent 列表页面展示所有 Agent，可选择进入对话

### 对话管理
- **FR-14**: 每个 Agent 下可有多个对话（会话）
- **FR-15**: 对话可创建、重命名、删除
- **FR-16**: 对话列表按时间倒序排列，显示最近消息预览
- **FR-17**: 对话历史本地持久化存储

### 聊天功能
- **FR-18**: 支持发送文本消息
- **FR-19**: 支持流式输出（SSE）AI 回复
- **FR-20**: 消息气泡样式区分用户和 AI（类似 Telegram）
- **FR-21**: 显示消息发送状态（发送中、已发送、错误）
- **FR-22**: 支持复制消息内容
- **FR-23**: 支持重新生成 AI 回复
- **FR-24**: 支持中断正在生成的回复

### 导航与 UI
- **FR-25**: 底部导航栏：对话、Agent、设置
- **FR-26**: 对话页类似 Telegram 聊天界面
- **FR-27**: 支持深色/浅色主题
- **FR-28**: Material You 动态配色支持

## Non-Functional Requirements
- **NFR-1**: 聊天消息流式输出延迟 < 500ms（首字节）
- **NFR-2**: 应用冷启动时间 < 2s
- **NFR-3**: 本地数据库查询响应 < 100ms
- **NFR-4**: 内存使用峰值 < 200MB
- **NFR-5**: 遵循 Android 现代应用架构（MVVM + 分层架构）
- **NFR-6**: 代码使用 Kotlin，UI 使用 Jetpack Compose
- **NFR-7**: API Key 安全存储（EncryptedSharedPreferences）

## Constraints
- **Technical**: 
  - Android minSdk = 30
  - Kotlin + Jetpack Compose
  - Room 数据库本地持久化
  - Retrofit / OkHttp 网络请求
  - 仅实现 mobile 模块
- **Business**: 
  - 所有数据本地存储，不上传云端
  - 完全开源、无付费功能
- **Dependencies**:
  - OpenAI 兼容 API 规范
  - Room Persistence Library
  - Retrofit + OkHttp
  - Kotlin Coroutines + Flow
  - DataStore Preferences
  - Markdown 渲染库

## Assumptions
- 用户拥有可用的 OpenAI 兼容 API 服务（自建或第三方）
- `/v1/models` 接口遵循 OpenAI 规范返回模型列表
- 流式输出使用 Server-Sent Events (SSE) 格式
- 用户设备已安装 Android 11 (API 30) 或更高版本

## Acceptance Criteria

### AC-1: Provider 增删改查
- **Given**: 用户在 Provider 管理页面
- **When**: 用户添加/编辑/删除一个 Provider（填写名称、Base URL、API Key）
- **Then**: Provider 列表正确更新，数据持久化保存
- **Verification**: `programmatic`

### AC-2: 模型自动同步
- **Given**: 用户配置了有效的 Provider
- **When**: 用户点击"同步模型"按钮
- **Then**: 调用 Provider 的 `/v1/models` 接口，获取并展示模型列表，用户可选择保存
- **Verification**: `programmatic`

### AC-3: 模型手动添加
- **Given**: 用户在 Provider 详情页
- **When**: 用户手动输入模型 ID 和名称并保存
- **Then**: 模型被添加到该 Provider 下，可在 Agent 配置中选用
- **Verification**: `programmatic`

### AC-4: Agent 创建与配置
- **Given**: 用户在 Agent 管理页面
- **When**: 用户创建新 Agent，设置名称、系统提示词、默认模型、温度等参数
- **Then**: Agent 保存成功，出现在 Agent 列表中
- **Verification**: `programmatic`

### AC-5: 默认标准 Agent
- **Given**: 应用首次启动
- **When**: 用户进入 Agent 列表
- **Then**: 存在一个预置的"标准 Agent"，使用默认配置
- **Verification**: `programmatic`

### AC-6: 对话归类于 Agent
- **Given**: 用户进入某个 Agent
- **When**: 用户创建新对话或查看历史对话
- **Then**: 对话列表仅显示该 Agent 下的对话
- **Verification**: `programmatic`

### AC-7: Telegram 风格聊天界面
- **Given**: 用户进入一个对话
- **When**: 查看聊天界面
- **Then**: 消息气泡呈现在两侧（用户在右、AI 在左），类似 Telegram 风格，底部有输入框和发送按钮
- **Verification**: `human-judgment`

### AC-8: 流式输出
- **Given**: 用户发送了一条消息
- **When**: AI 正在回复
- **Then**: AI 回复内容以流式方式逐字/逐块显示，有打字效果
- **Verification**: `human-judgment`

### AC-9: 消息状态指示
- **Given**: 用户发送消息
- **When**: 消息处于发送中/已发送/错误状态
- **Then**: 消息旁显示对应的状态指示器
- **Verification**: `programmatic`

### AC-10: 底部导航
- **Given**: 用户在应用内任意页面
- **When**: 查看屏幕底部
- **Then**: 有底部导航栏，包含"对话"、"Agent"、"设置"三个入口，可切换
- **Verification**: `human-judgment`

### AC-11: 本地数据持久化
- **Given**: 用户关闭应用后重新打开
- **When**: 查看 Provider、Agent、对话列表
- **Then**: 所有数据与关闭前一致，无丢失
- **Verification**: `programmatic`

### AC-12: 深色主题支持
- **Given**: 系统开启深色模式或应用内切换深色主题
- **When**: 浏览各页面
- **Then**: UI 正确适配深色主题，文字可读
- **Verification**: `human-judgment`

### AC-13: 重新生成回复
- **Given**: 对话中有一条 AI 回复
- **When**: 用户长按或点击菜单选择"重新生成"
- **Then**: AI 重新生成回复，原回复被替换
- **Verification**: `programmatic`

### AC-14: 中断生成
- **Given**: AI 正在流式生成回复
- **When**: 用户点击"停止"按钮
- **Then**: 生成立即中断，显示已生成的部分内容
- **Verification**: `programmatic`

## Open Questions
- [ ] 是否需要支持消息搜索功能？
- [ ] 是否需要对话导出/导入功能？
- [ ] 是否需要支持 Markdown 渲染（代码高亮、公式等）？
- [ ] API Key 存储是否需要生物识别保护？
