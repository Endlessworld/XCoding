//! StatusBarWidget：状态栏（Agent名 + 连接状态 + 模型 + 会话计数 + 时间）

use crate::state::AppState;
use crate::theme::TuiTheme;
use chrono::Local;
use ratatui::layout::Rect;
use ratatui::text::{Line, Span};
use ratatui::widgets::Paragraph;
use ratatui::Frame;

pub fn render(frame: &mut Frame, area: Rect, app: &AppState, theme: &TuiTheme) {
    let mut spans = Vec::new();

    // Agent 名
    let agent_name = if app.agent_name.is_empty() {
        "XAgent v0.1".to_string()
    } else {
        format!("{} v{}", app.agent_name, app.agent_version)
    };
    spans.push(Span::styled(agent_name, theme.bold(theme.text_primary)));
    spans.push(Span::styled("  │  ", theme.text_secondary));

    // 连接状态
    spans.push(Span::styled(
        app.connection.label(),
        theme.status_style(app.connection),
    ));
    spans.push(Span::styled("  │  ", theme.text_secondary));

    // 模型
    spans.push(Span::styled("模型: ", theme.text_secondary));
    spans.push(Span::styled(
        app.current_model.clone().unwrap_or_else(|| "-".to_string()),
        theme.assistant_message,
    ));
    spans.push(Span::styled("  │  ", theme.text_secondary));

    // 会话计数
    spans.push(Span::styled(
        format!("会话: {}/{}", app.current_session + 1, app.session_count()),
        theme.text_primary,
    ));

    // 右侧时间
    let now = Local::now().format("%H:%M").to_string();
    let pad = area.width.saturating_sub(60) as usize;
    let now_span = Span::styled(format!("{}{}", " ".repeat(pad), now), theme.text_secondary);
    spans.push(now_span);

    let para = Paragraph::new(Line::from(spans));
    frame.render_widget(para, area);
}
