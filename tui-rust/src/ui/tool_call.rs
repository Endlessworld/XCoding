//! ToolCallWidget：工具调用卡片渲染器
//!
//! 将一条工具调用消息渲染为独立卡片行：Header（工具名 + 状态徽标 + 时间）、
//! 入参/出参正文、折叠控制（Space）。chat.rs 在遇到 ToolCall/ToolResult 消息
//! 时调用本模块，替换内联的普通消息渲染。

use crate::model::ChatMessage;
use crate::theme::TuiTheme;
use ratatui::style::Color;
use ratatui::text::{Line, Span};

/// 将一条工具调用消息渲染为卡片行。
pub fn tool_call_lines(msg: &ChatMessage, theme: &TuiTheme) -> Vec<Line<'static>> {
    let mut lines = Vec::new();

    // ---- Header ----
    let mut header = vec![
        Span::styled("┌─", theme.tool_message),
        Span::styled(" 🔧 工具 ", theme.bold(theme.tool_message)),
    ];
    if let Some(id) = &msg.tool_call_id {
        header.push(Span::styled(format!("#{}", short_id(id)), theme.text_secondary));
    }
    if let Some(status) = &msg.tool_status {
        let (icon, color) = status_style(status, theme);
        header.push(Span::styled(format!(" [{icon} {status}]"), theme.bold(color)));
    }
    header.push(Span::styled(format!("  {}", msg.timestamp), theme.text_secondary));
    header.push(Span::styled(
        if msg.is_expanded { " (Space 折叠)" } else { " (Space 展开)" },
        theme.text_secondary,
    ));
    lines.push(Line::from(header));

    // ---- 正文（入参/出参）----
    if msg.is_expanded {
        for content_line in msg.content.split('\n') {
            lines.push(Line::from(Span::styled(
                format!("│ {content_line}"),
                theme.tool_message,
            )));
        }
    } else {
        let preview: String = msg.content.chars().take(40).collect();
        let tail = if msg.content.chars().count() > 40 { "…" } else { "" };
        lines.push(Line::from(Span::styled(
            format!("│ {preview}{tail}"),
            theme.text_secondary,
        )));
    }
    lines.push(Line::from(Span::styled("└─", theme.tool_message)));

    lines
}

/// 状态 → (图标, 颜色)
fn status_style(status: &str, theme: &TuiTheme) -> (&'static str, Color) {
    match status {
        "COMPLETED" => ("✓", theme.status_connected),
        "FAILED" => ("✗", theme.error_message),
        "PENDING" => ("○", theme.system_message),
        _ => ("◌", theme.system_message),
    }
}

/// 截断工具调用 id（去前缀 + 取前 8 字符）
fn short_id(id: &str) -> String {
    let trimmed = id.trim_start_matches(|c: char| !c.is_ascii_alphanumeric());
    trimmed.chars().take(8).collect()
}

