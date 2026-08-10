//! InputPanelWidget：输入面板（多行 / 占位符 / 光标）

use crate::state::AppState;
use crate::theme::TuiTheme;
use ratatui::layout::Rect;
use ratatui::style::{Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Paragraph, Wrap};
use ratatui::Frame;

pub fn render(frame: &mut Frame, area: Rect, app: &AppState, theme: &TuiTheme) {
    let focused = app.focus == crate::state::FocusPanel::Input;
    let block = Block::bordered()
        .border_style(theme.border_style(focused))
        .title(Span::styled("⌨ 输入", theme.bold(theme.border_focused)));

    let inner = block.inner(area);
    frame.render_widget(block, area);

    let buffer = &app.input.buffer;
    let mut lines: Vec<Line<'static>> = Vec::new();

    if buffer.is_empty() {
        lines.push(Line::from(Span::styled(
            "> 输入指令...  [Enter 发送, Alt+Enter 换行, Ctrl+P 会话列表]",
            theme.text_secondary,
        )));
    } else {
        // 多行：每行前缀 "> "
        for (i, line) in buffer.split('\n').enumerate() {
            let line_content = line.to_string();
            let spans = if focused && i == app.input.cursor_line() {
                render_line_with_cursor(&line_content, app.input.cursor_col(), theme)
            } else {
                vec![
                    Span::styled("> ", theme.text_secondary),
                    Span::styled(line_content, theme.text_primary),
                ]
            };
            lines.push(Line::from(spans));
        }
    }

    // 字符计数
    let count = buffer.chars().count();
    lines.push(Line::from(Span::styled(
        format!("{count} chars"),
        theme.text_secondary,
    )));

    let para = Paragraph::new(lines)
        .wrap(Wrap { trim: false })
        .block(Block::default());
    frame.render_widget(para, inner);
}

/// 在当前行渲染光标（反色字符）
fn render_line_with_cursor(
    line: &str,
    col: usize,
    theme: &TuiTheme,
) -> Vec<Span<'static>> {
    let mut spans = vec![Span::styled("> ", theme.text_secondary)];
    let col = col.min(line.chars().count());
    let before: String = line.chars().take(col).collect();
    let after: String = line.chars().skip(col).collect();

    if !before.is_empty() {
        spans.push(Span::styled(before, theme.text_primary));
    }
    // 光标位置字符（或用空格占位）
    let cursor_char = after.chars().next().map(|c| c.to_string()).unwrap_or_else(|| " ".to_string());
    spans.push(Span::styled(
        cursor_char,
        Style::default()
            .fg(theme.text_primary)
            .bg(theme.border_focused)
            .add_modifier(Modifier::REVERSED),
    ));
    let after_rest: String = after.chars().skip(1).collect();
    if !after_rest.is_empty() {
        spans.push(Span::styled(after_rest, theme.text_primary));
    }
    spans
}
