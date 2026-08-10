//! TuiApp：主应用。负责事件循环（键盘 + ACP + 定时器）、状态更新与渲染。

use crate::acp::client::AcpRequest;
use crate::acp::event::{AcpEvent, TuiEvent, TodoEventPriority, TodoEventStatus};
use crate::state::{AppState, FocusPanel};
use crate::theme::TuiTheme;
use crate::ui;
use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
use ratatui::backend::CrosstermBackend;
use ratatui::Terminal;
use std::io;
use std::time::Duration;
use tokio::sync::mpsc::UnboundedSender;

/// 处理一次事件
pub fn handle_event(
    app: &mut AppState,
    theme: &TuiTheme,
    event: &TuiEvent,
    acp_tx: &UnboundedSender<AcpRequest>,
) -> bool {
    let _ = theme;
    match event {
        TuiEvent::Quit => return true,
        TuiEvent::Tick => {
            // 状态栏时间由渲染时读取当前时间，无需额外处理
        }
        TuiEvent::Key(key) => handle_key(app, key, acp_tx),
        TuiEvent::Acp(acp_event) => handle_acp(app, acp_event),
    }
    false
}

fn handle_acp(app: &mut AppState, ev: &AcpEvent) {
    match ev {
        AcpEvent::Connected { name, version } => {
            app.connection = crate::model::ConnectionState::Connected;
            app.agent_name = name.clone();
            app.agent_version = version.clone();
        }
        AcpEvent::Disconnected => {
            app.connection = crate::model::ConnectionState::Disconnected;
        }
        AcpEvent::Error(msg) => {
            app.connection = crate::model::ConnectionState::Error;
            app.add_error_message(msg.clone());
        }
        AcpEvent::AppendStreaming(text) => {
            app.append_streaming_content(text);
        }
        AcpEvent::AppendThought(text) => {
            app.add_system_message(format!("💭 {text}"));
            app.finish_streaming();
        }
        AcpEvent::ToolCall { id, status, content } => {
            let content = content.clone();
            app.update_tool_call(id, status, content.as_deref());
            if status == "COMPLETED" {
                app.finish_streaming();
            }
        }
        AcpEvent::Plan(todos) => {
            app.clear_todos();
            for t in todos {
                let status = match t.status {
                    TodoEventStatus::Pending => crate::model::TodoStatus::Pending,
                    TodoEventStatus::InProgress => crate::model::TodoStatus::InProgress,
                    TodoEventStatus::Completed => crate::model::TodoStatus::Completed,
                };
                let priority = match t.priority {
                    TodoEventPriority::High => crate::model::TodoPriority::High,
                    TodoEventPriority::Medium => crate::model::TodoPriority::Medium,
                    TodoEventPriority::Low => crate::model::TodoPriority::Low,
                };
                app.todos.push(crate::model::TodoItem::new(
                    t.content.clone(),
                    status,
                    priority,
                ));
            }
        }
        AcpEvent::Usage { input, output } => {
            app.update_token_usage(*input, *output, *input + *output);
        }
        AcpEvent::CurrentModel(model) => {
            app.current_model = Some(model.clone());
            if !app.available_models.contains(model) {
                app.available_models.push(model.clone());
            }
        }
        AcpEvent::AvailableModels(models) => {
            app.available_models = models.clone();
        }
        AcpEvent::CurrentMode(mode) => {
            app.current_mode = Some(mode.clone());
            if !app.available_modes.contains(mode) {
                app.available_modes.push(mode.clone());
            }
        }
        AcpEvent::AvailableModes(modes) => {
            app.available_modes = modes.clone();
        }
        AcpEvent::ConfigOption(options) => {
            app.config_options = options
                .iter()
                .map(|(id, value)| crate::state::ConfigOption {
                    id: id.clone(),
                    value: value.clone(),
                })
                .collect();
        }
        AcpEvent::Stop => {
            app.finish_streaming();
        }
    }
}

fn handle_key(app: &mut AppState, key: &KeyEvent, acp_tx: &UnboundedSender<AcpRequest>) {
    // 弹窗优先消费
    if app.popup.visible {
        handle_popup_key(app, key, acp_tx);
        return;
    }

    match key.code {
        KeyCode::Enter => {
            if app.focus == FocusPanel::Input {
                if key.modifiers.contains(KeyModifiers::ALT) {
                    // Alt+Enter：插入换行（多行输入）
                    app.input.insert_char('\n');
                } else {
                    let text = app.input.buffer.clone();
                    if !text.trim().is_empty() {
                        app.send_message(text.clone());
                        app.input.push_history(&text);
                        let _ = acp_tx.send(AcpRequest::Prompt(text));
                    }
                }
            }
        }
        KeyCode::Esc => {}
        KeyCode::Tab => {
            if key.modifiers.contains(KeyModifiers::SHIFT) {
                app.focus_previous();
            } else {
                app.focus_next();
            }
        }
        KeyCode::Up => {
            if app.focus == FocusPanel::Input {
                app.input.history_prev();
            } else {
                app.scroll.scroll_up(1);
            }
        }
        KeyCode::Down => {
            if app.focus == FocusPanel::Input {
                app.input.history_next();
            } else {
                app.scroll.scroll_down(1);
            }
        }
        KeyCode::Left => {
            if app.focus == FocusPanel::Input {
                app.input.cursor_left();
            }
        }
        KeyCode::Right => {
            if app.focus == FocusPanel::Input {
                app.input.cursor_right();
            }
        }
        KeyCode::PageUp => app.scroll.page_up(20),
        KeyCode::PageDown => app.scroll.page_down(20),
        KeyCode::Home => app.scroll.offset = 0,
        KeyCode::End => app.scroll.to_bottom(),
        KeyCode::Backspace => {
            if app.focus == FocusPanel::Input {
                app.input.backspace();
            }
        }
        KeyCode::Delete => {
            if app.focus == FocusPanel::Input {
                app.input.delete_after();
            }
        }
        KeyCode::Char(' ') => {
            if app.focus == FocusPanel::Center {
                // 折叠/展开最近一条工具消息
                let session = app.current_mut();
                if let Some(msg) = session.messages.iter_mut().rev().find(|m| {
                    m.role == crate::model::MessageRole::ToolCall
                        || m.role == crate::model::MessageRole::ToolResult
                }) {
                    msg.is_expanded = !msg.is_expanded;
                }
            } else if app.focus == FocusPanel::Input {
                app.input.insert_char(' ');
            }
        }
        KeyCode::Char('c') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            if app.is_streaming {
                app.finish_streaming();
            }
            // MVP 不做退出，避免误触；退出用 Ctrl+Q
        }
        KeyCode::Char('n') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.new_session();
        }
        KeyCode::Char('w') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.close_current_session();
        }
        KeyCode::Char('q') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            // 退出由调用方处理
        }
        KeyCode::Char('p') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.toggle_session_popup();
        }
        KeyCode::Char('k') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.clear_conversation();
        }
        KeyCode::Char('v') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            // 粘贴占位（后续接入剪贴板）
        }
        KeyCode::Char(c) => {
            if app.focus == FocusPanel::Input {
                if key.modifiers.contains(KeyModifiers::ALT) {
                    // Alt+Enter 已在 Enter 分支；其他 Alt 组合忽略
                } else {
                    app.input.insert_char(c);
                }
            }
        }
        _ => {}
    }
}

fn handle_popup_key(
    app: &mut AppState,
    key: &KeyEvent,
    acp_tx: &UnboundedSender<AcpRequest>,
) {
    match key.code {
        KeyCode::Esc => {
            app.popup.visible = false;
            app.focus = FocusPanel::Input;
        }
        KeyCode::Tab => {
            app.popup.tab_index = (app.popup.tab_index + 1) % 3;
            app.popup.selected = 0;
        }
        KeyCode::Up => {
            app.popup.selected = app.popup.selected.saturating_sub(1);
        }
        KeyCode::Down => {
            app.popup.selected += 1;
        }
        KeyCode::Enter => {
            match app.popup.tab_index {
                0 => {
                    if app.popup.selected < app.sessions.len() {
                        app.current_session = app.popup.selected;
                    }
                }
                1 => {
                    if app.popup.selected < app.available_models.len() {
                        let m = app.available_models[app.popup.selected].clone();
                        app.current_model = Some(m.clone());
                        // 通过 v2 set_config_option("model") 真正切换后端模型
                        let _ = acp_tx.send(AcpRequest::SetModel(m));
                    }
                }
                2 => {
                    if app.popup.selected < app.available_modes.len() {
                        let m = app.available_modes[app.popup.selected].clone();
                        app.current_mode = Some(m.clone());
                        // 通过 v2 set_config_option("mode") 真正切换后端模式
                        let _ = acp_tx.send(AcpRequest::SetMode(m));
                    }
                }
                _ => {}
            }
            app.popup.visible = false;
            app.focus = FocusPanel::Input;
        }
        KeyCode::Char('n') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.new_session();
        }
        KeyCode::Char('w') if key.modifiers.contains(KeyModifiers::CONTROL) => {
            app.close_current_session();
        }
        _ => {}
    }
}

/// 运行主循环：crossterm 事件 + ACP 事件 + 定时 tick
pub async fn run(
    acp_tx: UnboundedSender<AcpRequest>,
    acp_event_rx: &mut tokio::sync::mpsc::UnboundedReceiver<AcpEvent>,
) -> io::Result<()> {
    let mut app = AppState::new();
    let theme = TuiTheme::modern_dark();

    let mut terminal = Terminal::new(CrosstermBackend::new(io::stdout()))?;
    crossterm::terminal::enable_raw_mode()?;
    crossterm::execute!(io::stdout(), crossterm::terminal::EnterAlternateScreen)?;

    let result = run_loop(&mut terminal, &mut app, &theme, acp_tx, acp_event_rx).await;

    // 清理
    crossterm::execute!(io::stdout(), crossterm::terminal::LeaveAlternateScreen)?;
    crossterm::terminal::disable_raw_mode()?;
    terminal.show_cursor()?;

    result
}

async fn run_loop(
    terminal: &mut Terminal<CrosstermBackend<io::Stdout>>,
    app: &mut AppState,
    theme: &TuiTheme,
    acp_tx: UnboundedSender<AcpRequest>,
    acp_event_rx: &mut tokio::sync::mpsc::UnboundedReceiver<AcpEvent>,
) -> io::Result<()> {
    let mut tick_interval = tokio::time::interval(Duration::from_secs(1));
    let mut quit = false;

    // 键盘事件通过独立线程读取，经 tokio channel 投递
    let (key_tx, mut key_rx) = tokio::sync::mpsc::unbounded_channel::<crossterm::event::KeyEvent>();
    let key_thread = std::thread::spawn(move || loop {
        use crossterm::event::{Event, KeyEventKind};
        if let Ok(true) = crossterm::event::poll(Duration::from_millis(100)) {
            if let Ok(Event::Key(key)) = crossterm::event::read() {
                // 只处理 Press（避免重复）
                if key.kind == KeyEventKind::Press {
                    if key_tx.send(key).is_err() {
                        break;
                    }
                }
            }
        }
    });

    while !quit {
        // 渲染
        terminal.draw(|f| ui::render(f, app, theme))?;

        tokio::select! {
            // 定时器：每秒触发，驱动状态栏时间刷新
            _ = tick_interval.tick() => {
                if handle_event(app, theme, &TuiEvent::Tick, &acp_tx) {
                    quit = true;
                }
            }
            // crossterm 事件
            Some(key) = key_rx.recv() => {
                let tui_event = if is_quit_key(&key) {
                    TuiEvent::Quit
                } else {
                    TuiEvent::Key(key)
                };
                if handle_event(app, theme, &tui_event, &acp_tx) {
                    quit = true;
                }
            }
            // ACP 事件
            Some(acp_event) = acp_event_rx.recv() => {
                if handle_event(app, theme, &TuiEvent::Acp(acp_event), &acp_tx) {
                    quit = true;
                }
            }
        }
    }

    let _ = key_thread; // 线程随进程退出
    // 优雅关闭 ACP 会话，使后台事件循环正常退出
    let _ = acp_tx.send(AcpRequest::Shutdown);
    Ok(())
}

fn is_quit_key(key: &KeyEvent) -> bool {
    key.code == KeyCode::Char('q') && key.modifiers.contains(KeyModifiers::CONTROL)
}
