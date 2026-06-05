# Mordant 使用经验笔记

> 来源：阶段二实施过程中从 mordant 源码（E:\local-github\mordant）学习的经验

## Markdown 渲染

mordant 提供了 `mordant-markdown` 子模块，内含 `Markdown` Widget，可直接渲染 GitHub Flavored Markdown。

### 用法

```kotlin
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.terminal.Terminal

val terminal = Terminal()
val rendered = terminal.render(Markdown("# Hello\n\n**bold** text"))
```

### 关键特性

- `Markdown` 实现了 `Widget` 接口，可被 `terminal.render()` 渲染为带 ANSI 转义码的字符串
- 内部使用 `MarkdownRenderer`，基于 `intellij-markdown` 库的 `GFMFlavourDescriptor` 解析
- 支持：标题、粗体/斜体、代码块、表格、列表、引用、链接等完整 GFM 语法
- `showHtml` 参数控制是否渲染 HTML 标签（默认跳过）
- `hyperlinks` 参数控制是否使用 ANSI 超链接（终端支持时自动启用）

### 与自定义解析器对比

| 特性 | mordant Markdown Widget | 项目自研 MarkdownParser |
|------|------------------------|------------------------|
| 代码块高亮 | ✅ 支持 | ⚠️ 仅加边框 |
| 表格 | ✅ 支持 | ❌ 不支持 |
| 嵌套列表 | ✅ 支持 | ⚠️ 基础支持 |
| 任务列表 | ✅ 支持 | ❌ 不支持 |
| 链接 | ✅ 超链接 | ❌ 纯文本 |
| 主题适配 | ✅ 自动适配终端主题 | ❌ 硬编码 ANSI 码 |

### 结论

项目中已有 `mordant-markdown` 依赖，应直接使用 `Markdown` widget 替换自研 `MarkdownParser`，可大幅提升渲染质量且减少维护成本。

---

## Terminal 渲染 API

```kotlin
// 渲染任意 Widget 为字符串
terminal.render(widget: Widget): String

// 渲染 Lines（Widget.render() 的返回值）
terminal.render(message: Any?, ...): String

// measure 用于计算 Widget 在不同宽度下的尺寸范围
widget.measure(t: Terminal, width: Int): WidthRange
```

## Widget 体系

mordant 的核心是可组合的 Widget 体系：

- `Text` — 基础文本
- `Panel` — 带边框的面板
- `Table` — 表格布局
- `Markdown` — Markdown 渲染
- `Padding` — 内边距
- `Grid` / `List` — 布局容器

所有 Widget 都实现 `render(t: Terminal, width: Int): Lines` 接口。
