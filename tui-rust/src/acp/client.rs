//! AcpClient：WebSocket 连接 XAgent 后端 jar（ACP 协议 v1），建立会话并驱动事件分发
//!
//! 采用 actor 模式：后台 tokio 任务持有连接句柄，通过 mpsc 通道接收
//! 「发送 prompt / 切换模型 / 切换模式 / 关闭」请求；入站 SessionNotification
//! 由 on_receive_notification 处理器实时转换为 AcpEvent 投递给 UI 线程。
//!
//! 协议 v1 关键点（后端仅协商 v1）：
//! - 使用 Client.builder()（v1 连接），非 Client.v2()
//! - 建会话用 NewSessionRequest，获取 session_id 后用 session_id 发 prompt
//! - 模型/模式切换通过 SetSessionConfigOptionRequest("model"/"mode") 下发
//!   （后端仅在客户端名为 IntelliJ 2026 时暴露 model/mode 配置，故 client_info
//!    声明为 IntelliJ 2026 以启用这些选项）
//! - 事件字段：SessionConfigOption.id、ToolCallUpdate.fields.status、
//!   UsageUpdate.used/size、ContentChunk.content

use crate::acp::event::AcpEvent;
use agent_client_protocol::schema::ProtocolVersion;
use agent_client_protocol::schema::v1::{
    ContentBlock, Implementation, InitializeRequest, NewSessionRequest, PromptRequest,
    SessionConfigOptionValue, SessionNotification, SetSessionConfigOptionRequest, TextContent,
};
use agent_client_protocol::{Agent, Client, ConnectionTo};
use anyhow::Result;
use tokio::sync::mpsc;

/// 向 ACP 会话发送的命令请求
#[derive(Debug)]
pub enum AcpRequest {
    /// 发送 prompt
    Prompt(String),
    /// 切换模型（config option "model"）
    SetModel(String),
    /// 切换模式（config option "mode"）
    SetMode(String),
    /// 关闭会话
    Shutdown,
}

/// 连接参数
#[derive(Debug, Clone)]
pub struct AcpConfig {
    pub ws_url: String,
    pub cwd: std::path::PathBuf,
}

impl Default for AcpConfig {
    fn default() -> Self {
        Self {
            ws_url: "ws://127.0.0.1:8080/acp".to_string(),
            cwd: std::env::current_dir().unwrap_or_else(|_| ".".into()),
        }
    }
}

/// 启动 ACP 客户端。
/// 返回请求发送器，供 UI 线程发送 prompt / 切换模型模式 / 关闭。
pub async fn spawn(
    config: AcpConfig,
    event_tx: mpsc::UnboundedSender<AcpEvent>,
) -> Result<mpsc::UnboundedSender<AcpRequest>> {
    let (req_tx, mut req_rx) = mpsc::unbounded_channel::<AcpRequest>();

    tokio::spawn(async move {
        let result = run(config, &mut req_rx, &event_tx).await;
        if let Err(e) = result {
            let _ = event_tx.send(AcpEvent::Error(format!("ACP 连接结束: {e}")));
        }
        let _ = event_tx.send(AcpEvent::Disconnected);
    });

    Ok(req_tx)
}

async fn run(
    config: AcpConfig,
    req_rx: &mut mpsc::UnboundedReceiver<AcpRequest>,
    event_tx: &mpsc::UnboundedSender<AcpEvent>,
) -> Result<()> {
    let transport = agent_client_protocol_http::HttpClient::with_endpoint(&config.ws_url)?;

    let _ = event_tx.send(AcpEvent::Connected {
        name: "XAgent".to_string(),
        version: "0.0.1".to_string(),
    });

    Client
        .builder()
        // 注册入站 SessionNotification 处理器：实时转换为 AcpEvent 投递 UI
        .on_receive_notification(
            async move |notif: SessionNotification, _cx| {
                handle_update(notif, event_tx);
                Ok(())
            },
            agent_client_protocol::on_receive_notification!(),
        )
        .connect_with(transport, async move |cx: ConnectionTo<Agent>| {
            // 初始化（协议 v1）。client_info 声明为 IntelliJ 2026，以启用后端的
            // model/mode 配置选项（SessionConfigOptionsFactory 仅对 IntelliJ 客户端暴露）。
            let init = cx
                .send_request(
                    InitializeRequest::new(ProtocolVersion::V1).client_info(
                        Implementation::new("IntelliJ IDEA", "2026.1"),
                    ),
                )
                .block_task()
                .await?;
            let agent_name = init
                .agent_info
                .as_ref()
                .map(|i| i.name.clone())
                .unwrap_or_else(|| "XAgent".to_string());
            let agent_version = init
                .agent_info
                .as_ref()
                .map(|i| i.version.clone())
                .unwrap_or_else(|| "0.0.1".to_string());
            let _ = event_tx.send(AcpEvent::Connected {
                name: agent_name,
                version: agent_version,
            });

            // 建立会话（v1: NewSessionRequest）
            let new_sess = cx
                .send_request(NewSessionRequest::new(&config.cwd))
                .block_task()
                .await?;
            let sid = new_sess.session_id.clone();

            // 从会话响应的 config_options 提取模型/模式列表
            if let Some(config_opts) = new_sess.config_options {
                emit_config_options(config_opts, event_tx);
            }

            // 命令循环：处理 UI 线程发来的请求（prompt / set_model / set_mode / shutdown）
            loop {
                match req_rx.recv().await {
                    Some(AcpRequest::Prompt(text)) => {
                        let _ = cx
                            .send_request(PromptRequest::new(
                                sid.clone(),
                                vec![ContentBlock::Text(TextContent::new(text))],
                            ))
                            .block_task()
                            .await;
                    }
                    Some(AcpRequest::SetModel(model)) => {
                        let _ = cx
                            .send_request(SetSessionConfigOptionRequest::new(
                                sid.clone(),
                                "model",
                                SessionConfigOptionValue::from(model.as_str()),
                            ))
                            .block_task()
                            .await;
                        let _ = event_tx.send(AcpEvent::CurrentModel(model));
                    }
                    Some(AcpRequest::SetMode(mode)) => {
                        let _ = cx
                            .send_request(SetSessionConfigOptionRequest::new(
                                sid.clone(),
                                "mode",
                                SessionConfigOptionValue::from(mode.as_str()),
                            ))
                            .block_task()
                            .await;
                        let _ = event_tx.send(AcpEvent::CurrentMode(mode));
                    }
                    Some(AcpRequest::Shutdown) | None => break,
                }
            }

            Ok(())
        })
        .await?;

    Ok(())
}

/// 解析 config_options 列表，提取模型/模式选择器及当前值，发送 AcpEvent。
fn emit_config_options(
    config_options: Vec<agent_client_protocol::schema::v1::SessionConfigOption>,
    event_tx: &mpsc::UnboundedSender<AcpEvent>,
) {
    use agent_client_protocol::schema::v1::{SessionConfigKind, SessionConfigSelectOptions};
    for opt in config_options {
        let id = opt.id.to_string();
        match opt.kind {
            SessionConfigKind::Select(select) => {
                // 收集可选项
                let options: Vec<String> = match select.options {
                    SessionConfigSelectOptions::Ungrouped(list) => list
                        .into_iter()
                        .map(|o| o.value.to_string())
                        .collect(),
                    SessionConfigSelectOptions::Grouped(groups) => groups
                        .into_iter()
                        .flat_map(|g| g.options.into_iter())
                        .map(|o| o.value.to_string())
                        .collect(),
                    _ => vec![],
                };
                let current = select.current_value.to_string();
                match id.as_str() {
                    "model" => {
                        let _ = event_tx.send(AcpEvent::CurrentModel(current));
                        let _ = event_tx.send(AcpEvent::AvailableModels(options));
                    }
                    "mode" => {
                        let _ = event_tx.send(AcpEvent::CurrentMode(current));
                        let _ = event_tx.send(AcpEvent::AvailableModes(options));
                    }
                    _ => {}
                }
            }
            _ => {}
        }
    }
}

/// 将一条 v1::SessionNotification 转换为 AcpEvent 投递到 UI 线程。
fn handle_update(
    notif: SessionNotification,
    event_tx: &mpsc::UnboundedSender<AcpEvent>,
) {
    use agent_client_protocol::schema::v1::SessionUpdate;
    match notif.update {
        SessionUpdate::AgentMessageChunk(chunk) => {
            if let ContentBlock::Text(text) = chunk.content {
                let _ = event_tx.send(AcpEvent::AppendStreaming(text.text));
            }
        }
        SessionUpdate::AgentThoughtChunk(chunk) => {
            if let ContentBlock::Text(text) = chunk.content {
                let _ = event_tx.send(AcpEvent::AppendThought(text.text));
            }
        }
        SessionUpdate::ToolCallUpdate(u) => {
            emit_tool_call(u, event_tx);
        }
        SessionUpdate::CurrentModeUpdate(m) => {
            let _ = event_tx.send(AcpEvent::CurrentMode(m.current_mode_id.to_string()));
        }
        SessionUpdate::ConfigOptionUpdate(c) => {
            emit_config_options(c.config_options, event_tx);
        }
        SessionUpdate::UsageUpdate(u) => {
            // v1 UsageUpdate 字段为 used/size
            let _ = event_tx.send(AcpEvent::Usage {
                input: u.used,
                output: u.size,
            });
        }
        _ => {}
    }
}

/// 将 v1::ToolCallUpdate 转换为 AcpEvent::ToolCall
fn emit_tool_call(
    u: agent_client_protocol::schema::v1::ToolCallUpdate,
    event_tx: &mpsc::UnboundedSender<AcpEvent>,
) {
    use agent_client_protocol::schema::v1::ToolCallStatus;
    // v1 ToolCallUpdate 状态在 fields 中
    let status = match u.fields.status {
        Some(ToolCallStatus::Pending) => "PENDING",
        Some(ToolCallStatus::InProgress) => "IN_PROGRESS",
        Some(ToolCallStatus::Completed) => "COMPLETED",
        _ => "IN_PROGRESS",
    };
    let content = u
        .fields
        .raw_output
        .as_ref()
        .map(|v| v.to_string())
        .or_else(|| u.fields.raw_input.as_ref().map(|v| v.to_string()));

    let _ = event_tx.send(AcpEvent::ToolCall {
        id: u.tool_call_id.to_string(),
        status: status.to_string(),
        content,
    });
}
