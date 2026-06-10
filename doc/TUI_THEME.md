调查报告：TamboUI 框架中自动切换 dark/light 模式
核心机制：StyleEngine + 命名样式表（Named Stylesheets）
TamboUI 的 dark/light 主题切换基于 StyleEngine 的命名样式表（Named Stylesheets） 机制实现。核心思路是：加载多套主题 CSS，通过
setActiveStylesheet(name) 在运行时动态切换。
一、架构概览
┌─────────────────────────────────────────────────────┐
│ StyleEngine (tamboui-css)                          │
│ ┌────────────────────────────────────────────────┐ │
│ │ 内联样式表 (inlineStylesheets) ← 始终生效 │ │
│ │ 命名样式表 (namedStylesheets)                  │ │
│ │ ├─ "dark"  → dark.tcss │ │
│ │ └─ "light" → light.tcss │ │
│ │ 活动样式表: activeStylesheetName │ │
│ └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
│
▼
┌─────────────────────────────────────────────────────┐
│ ToolkitRunner / DefaultRenderContext │
│ → 渲染时调用 styleEngine.resolve(element) 解析样式 │
└─────────────────────────────────────────────────────┘
二、关键 API

1. StyleEngine（tamboui-css 模块）
   // 创建引擎
   StyleEngine engine = StyleEngine.create();

// 加载命名样式表（主题）
engine.loadStylesheet("dark", "/themes/dark.tcss");
engine.loadStylesheet("light", "/themes/light.tcss");

// 激活某个主题
engine.setActiveStylesheet("dark"); // 切换到 dark
engine.setActiveStylesheet("light"); // 切换到 light

// 监听主题变化
engine.addChangeListener(() -> { /* 主题已切换 */ });
核心原理：StyleEngine 维护两种样式表：
•
内联样式表（Inline）：始终生效，适合基础样式
•
命名样式表（Named）：同一时间只有一个激活，适合主题切换
切换时，collectRules() 和 collectVariables() 方法会重新收集规则和 CSS 变量，实现实时刷新。

2. ToolkitRunner（tamboui-toolkit 模块）
   // 方式一：通过 create() + styleEngine() 方法
   StyleEngine engine = StyleEngine.create();
   engine.loadStylesheet("dark", "/themes/dark.tcss");
   engine.loadStylesheet("light", "/themes/light.tcss");
   engine.setActiveStylesheet("dark");

try (var runner = ToolkitRunner.create(config)) {
runner.styleEngine(engine); // 注入 StyleEngine
runner.run(() -> this);
}

// 方式二：通过 Builder
try (var runner = ToolkitRunner.builder()
.config(config)
.styleEngine(engine)     // 注入 StyleEngine
.build()) {
runner.run(() -> this);
}
三、完整实现步骤（以 CssDemo 为例）
步骤 1：准备两套 TCSS 主题文件
dark.tcss（深色主题）：
$bg-primary: black;
$fg-primary: white;
$accent: cyan;
$border-color: dark-gray;
$focus-color: cyan;
$highlight-bg: #333333;

* { color: $fg-primary; }
  Panel { background: $bg-primary; border-color: $border-color; }
  Panel:focus { border-color: $focus-color; }
  .primary { color: cyan; text-style: bold; }
  #theme-indicator { color: cyan; text-style: bold; }
  light.tcss（浅色主题）：
  $bg-primary: #eeeeee;
  $fg-primary: #1a1a1a;
  $accent: #0066cc;
  $border-color: #888888;
  $focus-color: #0066cc;
  $highlight-bg: #d0d0d0;

* { color: $fg-primary; }
  Panel { background: $bg-primary; border-color: $border-color; }
  Panel:focus { border-color: $focus-color; }
  .primary { color: #0055aa; text-style: bold; }
  #theme-indicator { color: #0066cc; text-style: bold; }
  步骤 2：初始化 StyleEngine 并加载主题
  private String currentTheme = "dark";
  private final StyleEngine styleEngine;

private CssDemo() {
styleEngine = StyleEngine.create();
styleEngine.loadStylesheet("dark", "/themes-css/dark.tcss");
styleEngine.loadStylesheet("light", "/themes-css/light.tcss");
styleEngine.setActiveStylesheet(currentTheme);
}
步骤 3：注入到 ToolkitRunner
public void run() throws Exception {
var config = TuiConfig.builder()
.mouseCapture(true)
.tickRate(Duration.ofMillis(100))
.build();

    try (var runner = ToolkitRunner.create(config)) {
        runner.styleEngine(styleEngine);  // ← 关键：注入引擎
        runner.run(() -> this);
    }

}
步骤 4：在事件处理中切换主题
@Override
public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
if (event.isCharIgnoreCase('t')) {
toggleTheme();
return EventResult.HANDLED;
}
return EventResult.UNHANDLED;
}

private void toggleTheme() {
currentTheme = currentTheme.equals("dark") ? "light" : "dark";
styleEngine.setActiveStylesheet(currentTheme); // ← 切换主题
}
四、自动切换的实现方式
如果需要自动切换（而非手动按键），有几种方案：
方案 A：基于时间自动切换
// 在 ToolkitRunner 中使用 scheduleRepeating
runner.scheduleRepeating(() -> {
runner.runOnRenderThread(() -> {
var hour = java.time.LocalTime.now().getHour();
var newTheme = (hour >= 6 && hour < 18) ? "light" : "dark";
if (!newTheme.equals(currentTheme)) {
currentTheme = newTheme;
styleEngine.setActiveStylesheet(currentTheme);
}
});
}, Duration.ofMinutes(1));
方案 B：监听 StyleChangeListener
styleEngine.addChangeListener(() -> {
System.out.println("Theme switched to: " + styleEngine.getActiveStylesheet());
// 可在此处触发其他 UI 更新
});
方案 C：检测终端背景色（自动适配）
// 通过终端转义序列查询背景色
源码示例
E:\local-github\tamboui\tamboui-core\src\main\java\dev\tamboui\terminal\Mode2027Support.java
五、关键文件位置
文件
路径
StyleEngine.java
tamboui-css/src/main/java/dev/tamboui/css/engine/StyleEngine.java
ToolkitRunner.java
tamboui-toolkit/src/main/java/dev/tamboui/toolkit/app/ToolkitRunner.java
CssDemo.java（完整示例）
tamboui-css/demos/css-demo/src/main/java/dev/tamboui/demo/CssDemo.java
CssNoToolkitDemo.java（无 Toolkit 示例）
tamboui-css/demos/css-no-toolkit-demo/src/main/java/dev/tamboui/demo/CssNoToolkitDemo.java
dark.tcss
tamboui-css/demos/css-demo/src/main/resources/themes-css/dark.tcss
light.tcss
tamboui-css/demos/css-demo/src/main/resources/themes-css/light.tcss
六、总结
使用 TamboUI 框架实现 dark/light 模式切换的完整流程：

1.

编写两套 TCSS 主题文件（dark.tcss / light.tcss），使用 CSS 变量统一管理颜色

2.

创建 StyleEngine，用 loadStylesheet("dark", ...) 和 loadStylesheet("light", ...) 加载主题

3.

注入到 ToolkitRunner：通过 runner.styleEngine(engine) 或 Builder.styleEngine(engine)

4.

切换主题：调用 styleEngine.setActiveStylesheet("dark"/"light")，UI 会在下一帧自动刷新

5.

自动切换：通过定时器或终端检测逻辑，自动调用 setActiveStylesheet() 即可
整个切换过程是实时生效的——StyleEngine 内部通过 StyleChangeListener 通知机制，ToolkitRunner 在下一渲染周期自动使用新的样式表重新解析所有元素的样式。