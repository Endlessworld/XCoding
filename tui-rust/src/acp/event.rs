//! 事件定义：TuiEvent（聚合所有输入源）与 AcpEvent（ACP 层投递到 UI 的动作）

/// ACP 层解析出的事件（由 UI 线程应用到 AppState）
#[derive(Debug, Clone)]
pub enum AcpEvent {
    /// 连接建立，携带 Agent 信息
    Connected { name: String, version: String },
    /// 连接断开
    Disconnected,
    /// 连接出错
    Error(String),
    /// 追加流式文本
    AppendStreaming(String),
    /// 追加思考文本
    AppendThought(String),
    /// 工具调用状态更新
    ToolCall {
        id: String,
        status: String,
        content: Option<String>,
    },
    /// 计划/Todo 更新（完整替换）
    Plan(Vec<TodoEvent>),
    /// Token 用量
    Usage { input: u64, output: u64 },
    /// 当前模型更新
    CurrentModel(String),
    /// 可用模型列表
    AvailableModels(Vec<String>),
    /// 当前模式更新
    CurrentMode(String),
    /// 可用模式列表（会话建立后一次性推送）
    AvailableModes(Vec<String>),
    /// 配置选项更新
    ConfigOption(Vec<(String, String)>),
    /// 一轮 prompt 结束
    Stop,
}

/// Todo 项（ACP PlanEntry → UI）
#[derive(Debug, Clone)]
pub struct TodoEvent {
    pub content: String,
    pub status: TodoEventStatus,
    pub priority: TodoEventPriority,
}

#[derive(Debug, Clone, Copy)]
pub enum TodoEventStatus {
    Pending,
    InProgress,
    Completed,
}

#[derive(Debug, Clone, Copy)]
pub enum TodoEventPriority {
    High,
    Medium,
    Low,
}

/// 聚合事件源
#[derive(Debug)]
pub enum TuiEvent {
    Key(crossterm::event::KeyEvent),
    Acp(AcpEvent),
    Tick,
    /// 退出请求
    Quit,
}
