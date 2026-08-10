//! 端到端联调（v1 协议）：连接真实后端 XAgent WS 服务器。
//! 后端仅协商 ACP v1，因此使用 Client.builder()（v1）。
//! 验证：握手 → 建会话 → 读取 config_options → 切换 model/mode → 发送 prompt 收流式事件。

use agent_client_protocol::schema::ProtocolVersion;
use agent_client_protocol::schema::v1::{
    ContentBlock, Implementation, InitializeRequest, NewSessionRequest, PromptRequest,
    SessionConfigOptionValue, SessionNotification, SetSessionConfigOptionRequest,
    SetSessionModeRequest, TextContent,
};
use agent_client_protocol::{Agent, Client, ConnectionTo};
use anyhow::Result;
use tokio::sync::mpsc;

#[tokio::main]
async fn main() -> Result<()> {
    let args: Vec<String> = std::env::args().collect();
    let mut ws_url = "ws://127.0.0.1:8080/acp".to_string();
    let mut i = 1;
    while i < args.len() {
        if args[i] == "--ws-url" {
            if let Some(v) = args.get(i + 1) {
                ws_url = v.clone();
                i += 1;
            }
        }
        i += 1;
    }
    println!("[e2e] 连接: {ws_url}");

    let transport = agent_client_protocol_http::HttpClient::with_endpoint(&ws_url)?;
    let (evt_tx, mut evt_rx) = mpsc::unbounded_channel::<String>();

    Client
        .builder()
        .on_receive_notification(
            async move |notif: SessionNotification, _cx| {
                let _ = evt_tx.send(describe_update(&notif));
                Ok(())
            },
            agent_client_protocol::on_receive_notification!(),
        )
        .connect_with(transport, async |cx: ConnectionTo<Agent>| {
            // 握手
            let init = cx
                .send_request(
                    InitializeRequest::new(ProtocolVersion::V1)
                        .client_info(Implementation::new("IntelliJ IDEA", "2026.1")),
                )
                .block_task()
                .await?;
            println!("[e2e] 握手完成: {:?}", init.agent_info);

            // 建会话
            let cwd = std::env::current_dir()
                .map_err(|e| anyhow::anyhow!("读取 cwd 失败: {e}"))?;
            let new_sess = cx
                .send_request(NewSessionRequest::new(cwd))
                .block_task()
                .await?;
            let sid = new_sess.session_id.clone();
            println!("[e2e] 会话建立: {sid}");
            let config_opts = new_sess.config_options.clone().unwrap_or_default();
            println!("[e2e] config_options 数量: {}", config_opts.len());
            for opt in &config_opts {
                println!("  config_id={} kind={:?}", opt.id, opt.kind);
            }

            // 切换 mode / 模型（通过 set_config_option / set_mode）
            println!("[e2e] 尝试 set_config_option(thought_level, medium)");
            let _ = cx
                .send_request(SetSessionConfigOptionRequest::new(
                    sid.clone(),
                    "thought_level",
                    SessionConfigOptionValue::from("medium"),
                ))
                .block_task()
                .await;
            println!("[e2e] 尝试 set_mode(...)");
            let _ = cx
                .send_request(SetSessionModeRequest::new(sid.clone(), "yolo"))
                .block_task()
                .await;

            // 发送 prompt
            let text = "请只回复：你好，联调成功".to_string();
            println!("[e2e] 发送 prompt: {text}");
            let _prompt = cx
                .send_request(PromptRequest::new(
                    sid.clone(),
                    vec![ContentBlock::Text(TextContent::new(text))],
                ))
                .block_task()
                .await;
            println!("[e2e] prompt 已受理，等待事件流...");

            // 收集事件
            let mut count = 0;
            for _ in 0..80 {
                if let Ok(ev) = evt_rx.try_recv() {
                    println!("[evt] {ev}");
                    count += 1;
                } else {
                    tokio::time::sleep(std::time::Duration::from_millis(100)).await;
                }
            }
            while let Ok(ev) = evt_rx.try_recv() {
                println!("[evt] {ev}");
                count += 1;
            }
            println!("[e2e] 共收到 {count} 条事件");

            Ok(())
        })
        .await?;

    println!("[e2e] 连接结束");
    Ok(())
}

fn describe_update(notif: &SessionNotification) -> String {
    use agent_client_protocol::schema::v1::SessionUpdate;
    match &notif.update {
        SessionUpdate::AgentMessageChunk(c) => {
            if let ContentBlock::Text(t) = &c.content {
                format!("AgentMessageChunk: {}", t.text)
            } else {
                "AgentMessageChunk(non-text)".to_string()
            }
        }
        SessionUpdate::AgentThoughtChunk(c) => {
            if let ContentBlock::Text(t) = &c.content {
                format!("AgentThoughtChunk: {}", t.text)
            } else {
                "AgentThoughtChunk(non-text)".to_string()
            }
        }
        SessionUpdate::ToolCallUpdate(u) => format!(
            "ToolCallUpdate id={} status={:?}",
            u.tool_call_id, u.fields.status
        ),
        SessionUpdate::ToolCall(u) => format!("ToolCall id={}", u.tool_call_id),
        SessionUpdate::CurrentModeUpdate(m) => {
            format!("CurrentModeUpdate mode={}", m.current_mode_id)
        }
        SessionUpdate::ConfigOptionUpdate(c) => {
            format!("ConfigOptionUpdate options={}", c.config_options.len())
        }
        SessionUpdate::UsageUpdate(u) => {
            format!("UsageUpdate used={} size={}", u.used, u.size)
        }
        other => format!("Other: {:?}", std::mem::discriminant(other)),
    }
}
