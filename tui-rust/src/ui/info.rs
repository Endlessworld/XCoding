//! InfoPanelWidget：信息面板（Token / Todo / 模型 / 配置 / 连接状态）

use crate::state::AppState;
use crate::theme::TuiTheme;
use ratatui::layout::Rect;
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Paragraph, Wrap};
use ratatui::Frame;

pub fn render(frame: &mut Frame, area: Rect, app: &AppState, theme: &TuiTheme) {
    let block = Block::bordered()
        .border_style(theme.border_style(false))
        .title(Span::styled("📊 信息", theme.bold(theme.system_message)));

    let inner = block.inner(area);
    frame.render_widget(block, area);

    let mut rows: Vec<Line<'static>> = Vec::new();

    // 连接状态
    rows.push(Line::from(vec![
        Span::styled("连接: ", theme.text_secondary),
        Span::styled(app.connection.label(), theme.status_style(app.connection)),
    ]));

    // Agent 信息
    if !app.agent_name.is_empty() {
        rows.push(Line::from(vec![
            Span::styled("Agent: ", theme.text_secondary),
            Span::styled(
                format!("{} {}", app.agent_name, app.agent_version),
                theme.text_primary,
            ),
        ]));
    }

    // 模型 / 模式
    rows.push(Line::from(vec![
        Span::styled("模型: ", theme.text_secondary),
        Span::styled(
            app.current_model.clone().unwrap_or_else(|| "-".to_string()),
            theme.assistant_message,
        ),
    ]));
    rows.push(Line::from(vec![
        Span::styled("模式: ", theme.text_secondary),
        Span::styled(
            app.current_mode.clone().unwrap_or_else(|| "-".to_string()),
            theme.system_message,
        ),
    ]));

    rows.push(Line::from(""));

    // Token 用量
    rows.push(Line::from(Span::styled("Token 用量", theme.bold(theme.text_primary))));
    rows.push(Line::from(format!(
        "  Prompt: {}",
        app.token_usage.prompt_tokens
    )));
    rows.push(Line::from(format!(
        "  生成: {}",
        app.token_usage.completion_tokens
    )));
    rows.push(Line::from(format!(
        "  总计: {}",
        app.token_usage.total_tokens
    )));

    rows.push(Line::from(""));

    // Todo 列表
    rows.push(Line::from(Span::styled(
        "Todo 列表",
        theme.bold(theme.text_primary),
    )));
    let completed = app.todos.iter().filter(|t| t.status == crate::model::TodoStatus::Completed).count();
    rows.push(Line::from(Span::styled(
        format!("  ({completed}/{})", app.todos.len()),
        theme.text_secondary,
    )));
    for todo in &app.todos {
        let color = theme.todo_color(todo.priority);
        rows.push(Line::from(vec![
            Span::styled("  ● ", color),
            Span::styled(todo.status.icon(), theme.system_message),
            Span::styled(format!(" {}", todo.content), theme.text_primary),
        ]));
    }

    let para = Paragraph::new(rows)
        .wrap(Wrap { trim: false })
        .block(Block::default());
    frame.render_widget(para, inner);
}
