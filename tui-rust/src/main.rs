//! XAgent TUI (Rust) 入口
//!
//! 用法:
//!   cargo run -- --ws-url ws://127.0.0.1:8080/acp
//!   cargo run -- --ws-port 8080
//!   cargo run -- --cwd <path>

// MVP 脚手架：部分模型字段/方法/枚举变体为阶段二预留，暂未使用
#![allow(dead_code)]

mod acp;
mod app;
mod model;
mod state;
mod theme;
mod ui;

use acp::client::{self, AcpConfig};
use acp::event::AcpEvent;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let args: Vec<String> = std::env::args().collect();
    let config = parse_args(&args);

    println!("连接 ACP WebSocket: {}", config.ws_url);

    // Windows 终端检测：ratatui 依赖 ANSI 转义序列渲染。
    // 经典 conhost（未启用 VT 处理）等终端不支持时直接提示退出，避免界面乱码。
    if !crossterm::ansi_support::supports_ansi() {
        println!("错误：当前终端不支持 ANSI 转义序列，无法渲染 TUI 界面。");
        println!("请使用 Windows Terminal / VS Code 集成终端等支持 VT 的终端运行。");
        return Ok(());
    }

    // ACP 事件通道：ACP 后台任务 -> UI
    let (acp_event_tx, mut acp_event_rx) = tokio::sync::mpsc::unbounded_channel::<AcpEvent>();

    // 启动 ACP 客户端，获取 prompt 发送器
    let acp_tx = client::spawn(config.clone(), acp_event_tx.clone()).await?;

    // 运行 UI（阻塞直到退出）
    app::run(acp_tx, &mut acp_event_rx).await?;

    Ok(())
}

/// 解析 CLI 参数
fn parse_args(args: &[String]) -> AcpConfig {
    let mut config = AcpConfig::default();
    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--ws-url" => {
                if let Some(v) = args.get(i + 1) {
                    config.ws_url = v.clone();
                    i += 1;
                }
            }
            "--ws-port" => {
                if let Some(v) = args.get(i + 1) {
                    if let Ok(port) = v.parse::<u16>() {
                        config.ws_url = format!("ws://127.0.0.1:{port}/acp");
                    }
                    i += 1;
                }
            }
            "--cwd" => {
                if let Some(v) = args.get(i + 1) {
                    config.cwd = std::path::PathBuf::from(v);
                    i += 1;
                }
            }
            _ => {}
        }
        i += 1;
    }
    config
}
