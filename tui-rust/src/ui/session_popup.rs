//! SessionListPopupWidget：会话列表弹窗（会话 + 模型/模式切换）

use crate::state::AppState;
use crate::theme::TuiTheme;
use ratatui::layout::{Constraint, Direction, Layout, Rect};
use ratatui::style::{Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Clear, Paragraph, Wrap};
use ratatui::Frame;

pub fn render(frame: &mut Frame, area: Rect, app: &AppState, theme: &TuiTheme) {
    // 弹窗尺寸
    let w = area.width / 2;
    let h = area.height * 2 / 3;
    let x = (area.width - w) / 2;
    let y = (area.height - h) / 2;
    let popup_area = Rect::new(x, y, w, h);

    frame.render_widget(Clear, popup_area);

    let block = Block::bordered()
        .border_style(theme.border_style(true))
        .title(Span::styled(
            "会话列表 + 模型/模式",
            theme.bold(theme.selected_text),
        ));
    let inner = block.inner(popup_area);
    frame.render_widget(block, popup_area);

    // 双区域：左会话 / 右模型模式
    let chunks = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(50), Constraint::Percentage(50)])
        .split(inner);

    render_session_list(frame, chunks[0], app, theme);
    render_model_mode(frame, chunks[1], app, theme);
}

fn render_session_list(frame: &mut Frame, area: Rect, app: &AppState, theme: &TuiTheme) {
    let mut rows: Vec<Line<'static>> = Vec::new();
    rows.push(Line::from(Span::styled(
        "会话",
        theme.bold(theme.system_message),
    )));

    for (i, session) in app.sessions.iter().enumerate() {
        let marker = if i == app.current_session {
            "●"
        } else if i == app.popup.selected && app.popup.tab_index == 0 {
            "▸"
        } else {
            " "
        };
        let is_selected = app.popup.visible && app.popup.selected == i && app.popup.tab_index == 0;
        let style = if is_selected {
            theme.bold(theme.selected_text)
        } else if i == app.current_session {
            theme.bold(theme.text_primary)
        } else {
            Style::default().fg(theme.text_primary)
        };
        rows.push(Line::from(Span::styled(
            format!("{marker} {}", session.name),
            style,
        )));
    }

    rows.push(Line::from(""));
    rows.push(Line::from(Span::styled(
        "↑↓ 选择  Tab 切换区域  Enter 确认  Esc 关闭",
        theme.text_secondary,
    )));

    let para = Paragraph::new(rows)
        .wrap(Wrap { trim: false })
        .block(Block::default());
    frame.render_widget(para, area);
}

fn render_model_mode(frame: &mut Frame, area: Rect, app: &AppState, theme: &TuiTheme) {
    let mut rows: Vec<Line<'static>> = Vec::new();

    // 模型区
    rows.push(Line::from(Span::styled(
        "模型",
        theme.bold(theme.assistant_message),
    )));
    let models = if app.available_models.is_empty() {
        vec![app.current_model.clone().unwrap_or_else(|| "默认".to_string())]
    } else {
        app.available_models.clone()
    };
    for (i, m) in models.iter().enumerate() {
        let selected = app.popup.visible
            && app.popup.tab_index == 1
            && app.popup.selected == i;
        let style = if selected {
            theme.bold(theme.selected_text).add_modifier(Modifier::REVERSED)
        } else if Some(m.clone()) == app.current_model {
            theme.bold(theme.assistant_message)
        } else {
            Style::default().fg(theme.text_primary)
        };
        rows.push(Line::from(Span::styled(format!("  {m}"), style)));
    }

    rows.push(Line::from(""));

    // 模式区
    rows.push(Line::from(Span::styled(
        "模式",
        theme.bold(theme.system_message),
    )));
    let modes = if app.available_modes.is_empty() {
        vec![app.current_mode.clone().unwrap_or_else(|| "Agent".to_string())]
    } else {
        app.available_modes.clone()
    };
    for (i, m) in modes.iter().enumerate() {
        let selected = app.popup.visible
            && app.popup.tab_index == 2
            && app.popup.selected == i;
        let style = if selected {
            theme.bold(theme.selected_text).add_modifier(Modifier::REVERSED)
        } else if Some(m.clone()) == app.current_mode {
            theme.bold(theme.system_message)
        } else {
            Style::default().fg(theme.text_primary)
        };
        rows.push(Line::from(Span::styled(format!("  {m}"), style)));
    }

    let para = Paragraph::new(rows)
        .wrap(Wrap { trim: false })
        .block(Block::default());
    frame.render_widget(para, area);
}
