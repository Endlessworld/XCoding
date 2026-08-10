//! UI 渲染层：各面板 Widget

pub mod chat;
pub mod info;
pub mod input;
pub mod markdown;
pub mod session_popup;
pub mod status_bar;
pub mod tool_call;

use crate::state::AppState;
use crate::theme::TuiTheme;
use ratatui::layout::{Constraint, Direction, Layout, Rect};
use ratatui::Frame;

/// 渲染整个 TUI 界面
pub fn render(frame: &mut Frame, app: &AppState, theme: &TuiTheme) {
    let area = frame.area();
    if area.width < 80 || area.height < 24 {
        render_min_size_hint(frame, area);
        return;
    }

    let input_h = (area.height / 5).min(5).max(3);

    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Min(3),
            Constraint::Length(input_h),
            Constraint::Length(1),
        ])
        .split(area);

    // 上半区：Chat + Info
    let top = chunks[0];
    let top_chunks = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(75), Constraint::Percentage(25)])
        .split(top);

    chat::render(frame, top_chunks[0], app, theme);
    info::render(frame, top_chunks[1], app, theme);
    input::render(frame, chunks[1], app, theme);
    status_bar::render(frame, chunks[2], app, theme);

    if app.popup.visible {
        session_popup::render(frame, frame.area(), app, theme);
    }
}

fn render_min_size_hint(frame: &mut Frame, area: Rect) {
    use ratatui::widgets::{Block, Paragraph};
    let hint = Paragraph::new(format!(
        "终端尺寸过小（{w}x{h}），请调整到至少 80x24",
        w = area.width,
        h = area.height
    ))
    .block(Block::bordered().title("XAgent TUI (Rust)"));
    frame.render_widget(hint, area);
}
