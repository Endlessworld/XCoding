//! ChatPanelWidget：对话消息流

use crate::model::{ChatMessage, MessageRole};
use crate::state::AppState;
use crate::theme::TuiTheme;
use crate::ui::markdown;
use crate::ui::tool_call;
use ratatui::layout::Rect;
use ratatui::style::Modifier;
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Borders, Paragraph, Wrap};
use ratatui::Frame;

pub fn render(frame: &mut Frame, area: Rect, app: &AppState, theme: &TuiTheme) {
    let focused = app.focus == crate::state::FocusPanel::Center;
    let border_type = if focused { Borders::ALL } else { Borders::ALL };
    let _ = border_type;

    let block = Block::bordered()
        .border_style(theme.border_style(focused))
        .title(Span::styled("💬 对话", theme.bold(theme.border_focused)));

    let inner = block.inner(area);
    frame.render_widget(block, area);

    let session = app.current();

    // 计算可视行数（减去边框）
    let max_rows = inner.height.saturating_sub(1) as usize;

    // 构建所有渲染行（Header + 内容）
    let mut rows: Vec<Line<'static>> = Vec::new();
    for msg in &session.messages {
        rows.extend(message_lines(msg, theme));
    }

    // 空状态
    if rows.is_empty() {
        rows.push(Line::from(Span::styled(
            "开始新的对话",
            theme.text_secondary,
        )));
        rows.push(Line::from(Span::styled(
            "输入消息后按 Enter 发送  [Ctrl+P 会话列表]",
            theme.text_secondary,
        )));
    }

    // 计算滚动偏移：BOTTOM 表示跟随最新内容（显示最后 max_rows 行）
    let total = rows.len();
    let bottom = total.saturating_sub(max_rows);
    let offset = if app.scroll.offset == crate::state::ScrollState::BOTTOM {
        bottom
    } else {
        app.scroll.offset.min(bottom)
    };

    let visible: Vec<Line<'static>> = if total <= max_rows {
        rows
    } else {
        rows.into_iter()
            .skip(offset)
            .take(max_rows)
            .collect::<Vec<_>>()
    };

    let para = Paragraph::new(visible)
        .wrap(Wrap { trim: false })
        .block(Block::default());
    frame.render_widget(para, inner);
}

/// 将一条消息转为渲染行
fn message_lines(msg: &ChatMessage, theme: &TuiTheme) -> Vec<Line<'static>> {
    // 工具调用/工具结果：使用独立 ToolCallWidget 卡片渲染
    if msg.role == MessageRole::ToolCall || msg.role == MessageRole::ToolResult {
        return tool_call::tool_call_lines(msg, theme);
    }

    let mut lines = Vec::new();

    // Header
    let mut header_spans = vec![
        Span::styled(
            format!("{} {}  [{}]", msg.role.emoji(), msg.role.label(), msg.timestamp),
            theme.bold(theme.role_color(msg.role)),
        ),
    ];

    // 工具调用状态徽标
    if let Some(status) = &msg.tool_status {
        let (icon, color) = match status.as_str() {
            "COMPLETED" => ("✓", theme.status_connected),
            "FAILED" => ("✗", theme.error_message),
            _ => ("◌", theme.system_message),
        };
        header_spans.push(Span::styled(
            format!("  [{icon} {status}]"),
            theme.bold(color),
        ));
    }
    if msg.is_streaming {
        header_spans.push(Span::styled(" ▌", Modifier::SLOW_BLINK));
    }
    lines.push(Line::from(header_spans));

    // 内容
    if !msg.content.is_empty() {
        // Assistant 消息启用 Markdown 渲染
        if msg.role == MessageRole::Assistant {
            lines.extend(markdown::render_markdown(
                &msg.content,
                theme,
                theme.assistant_message,
            ));
        } else {
            for content_line in msg.content.split('\n') {
                let span = if msg.role == MessageRole::User {
                    Span::styled(content_line.to_string(), theme.user_message)
                } else {
                    Span::styled(content_line.to_string(), theme.role_color(msg.role))
                };
                lines.push(Line::from(span));
            }
        }
    }

    lines
}
