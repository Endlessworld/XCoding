//! Markdown 轻量流式渲染器
//!
//! 不依赖第三方 crate，自研解析器。支持：标题(#)、无序列表(-/*)、行内代码(`)、
//! 粗体(**)、代码块(```)。将 Markdown 文本渲染为带样式的 ratatui Line 列表。
//! 流式场景：文本逐 chunk 追加，渲染器按行即时解析，无需缓存解析树。

use crate::theme::TuiTheme;
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};

/// 渲染一段 Markdown 文本，返回带样式的行。
pub fn render_markdown(text: &str, theme: &TuiTheme, base: Color) -> Vec<Line<'static>> {
    let mut lines = Vec::new();
    let mut in_code_block = false;

    for raw in text.split('\n') {
        let trimmed = raw.trim();

        // 代码块开关
        if trimmed.starts_with("```") {
            in_code_block = !in_code_block;
            lines.push(Line::from(Span::styled(
                format!("╭ {}", lang(trimmed)),
                theme.text_secondary,
            )));
            continue;
        }
        if in_code_block {
            lines.push(Line::from(Span::styled(
                format!("│ {raw}"),
                Style::default().fg(base),
            )));
            continue;
        }

        // 标题
        if let Some(rest) = trimmed.strip_prefix("# ") {
            lines.push(Line::from(Span::styled(
                rest.to_string(),
                theme.bold(base).add_modifier(Modifier::UNDERLINED),
            )));
            continue;
        }

        // 无序列表
        if let Some(item) = trimmed.strip_prefix("- ").or_else(|| trimmed.strip_prefix("* ")) {
            let mut spans = vec![Span::styled("  • ", base)];
            spans.extend(span_with_inline(item, theme, base));
            lines.push(Line::from(spans));
            continue;
        }

        // 普通段落（行内解析）
        lines.push(Line::from(span_with_inline(raw, theme, base)));
    }
    lines
}

/// 解析代码块首行中的语言标识（```rust -> rust）
fn lang(code_fence: &str) -> &str {
    code_fence.trim_start_matches('`').trim()
}

/// 行内解析：`code` 与 **bold** 标记，返回带样式 Span 序列。
fn span_with_inline(text: &str, theme: &TuiTheme, base: Color) -> Vec<Span<'static>> {
    // 逐 token 扫描，支持行内代码 `x` 与粗体 **y**
    let mut spans: Vec<Span<'static>> = Vec::new();
    let mut buf = String::new();

    // 将缓冲的普通文本 flush 成一个 Span
    let plain_style = Style::default().fg(base);
    let flush = |spans: &mut Vec<Span<'static>>, buf: &mut String, style: Style| {
        if !buf.is_empty() {
            spans.push(Span::styled(std::mem::take(buf), style));
        }
    };

    let chars: Vec<char> = text.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        // 行内代码
        if c == '`' {
            flush(&mut spans, &mut buf, plain_style);
            i += 1;
            let mut code = String::new();
            while i < chars.len() && chars[i] != '`' {
                code.push(chars[i]);
                i += 1;
            }
            i += 1; // 跳过闭合 `
            spans.push(Span::styled(
                code,
                Style::default()
                    .fg(base)
                    .bg(theme.text_secondary)
                    .add_modifier(Modifier::BOLD),
            ));
            continue;
        }
        // 粗体
        if c == '*' && i + 1 < chars.len() && chars[i + 1] == '*' {
            flush(&mut spans, &mut buf, plain_style);
            i += 2;
            let mut bold = String::new();
            while i + 1 < chars.len() && !(chars[i] == '*' && chars[i + 1] == '*') {
                bold.push(chars[i]);
                i += 1;
            }
            i += 2; // 跳过闭合 **
            spans.push(Span::styled(bold, theme.bold(base)));
            continue;
        }
        buf.push(c);
        i += 1;
    }
    flush(&mut spans, &mut buf, plain_style);
    spans
}
