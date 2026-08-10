//! TuiTheme：modernDark 配色（对齐 Java 版 TuiTheme）
//! 使用 ratatui Style + Color

use ratatui::style::{Color, Modifier, Style};

/// 主题结构：语义化颜色
#[derive(Debug, Clone, Copy)]
pub struct TuiTheme {
    pub border_normal: Color,
    pub border_focused: Color,
    pub user_message: Color,
    pub assistant_message: Color,
    pub system_message: Color,
    pub tool_message: Color,
    pub error_message: Color,
    pub status_connected: Color,
    pub status_error: Color,
    pub selected_text: Color,
    pub text_primary: Color,
    pub text_secondary: Color,
    pub todo_high: Color,
    pub todo_medium: Color,
    pub todo_low: Color,
}

impl Default for TuiTheme {
    fn default() -> Self {
        Self::modern_dark()
    }
}

impl TuiTheme {
    pub fn modern_dark() -> Self {
        Self {
            border_normal: Color::Gray,
            border_focused: Color::LightCyan,
            user_message: Color::LightBlue,
            assistant_message: Color::LightGreen,
            system_message: Color::LightYellow,
            tool_message: Color::LightMagenta,
            error_message: Color::LightRed,
            status_connected: Color::LightGreen,
            status_error: Color::LightRed,
            selected_text: Color::LightCyan,
            text_primary: Color::White,
            text_secondary: Color::Gray,
            todo_high: Color::LightRed,
            todo_medium: Color::LightYellow,
            todo_low: Color::LightBlue,
        }
    }

    /// 角色对应颜色
    pub fn role_color(&self, role: crate::model::MessageRole) -> Color {
        match role {
            crate::model::MessageRole::User => self.user_message,
            crate::model::MessageRole::Assistant => self.assistant_message,
            crate::model::MessageRole::System => self.system_message,
            crate::model::MessageRole::ToolCall | crate::model::MessageRole::ToolResult => {
                self.tool_message
            }
            crate::model::MessageRole::Error => self.error_message,
        }
    }

    pub fn border_style(&self, focused: bool) -> Style {
        if focused {
            Style::default().fg(self.border_focused)
        } else {
            Style::default().fg(self.border_normal)
        }
    }

    pub fn todo_color(&self, priority: crate::model::TodoPriority) -> Color {
        match priority {
            crate::model::TodoPriority::High => self.todo_high,
            crate::model::TodoPriority::Medium => self.todo_medium,
            crate::model::TodoPriority::Low => self.todo_low,
        }
    }

    pub fn status_style(&self, state: crate::model::ConnectionState) -> Style {
        match state {
            crate::model::ConnectionState::Connected => {
                Style::default().fg(self.status_connected)
            }
            crate::model::ConnectionState::Error => Style::default().fg(self.status_error),
            _ => Style::default().fg(self.text_secondary),
        }
    }

    pub fn bold(&self, color: Color) -> Style {
        Style::default().fg(color).add_modifier(Modifier::BOLD)
    }
}
