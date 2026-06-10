# ToolkitRunner 事件处理最佳实践
TamboUI 源码本地目录 D:\IdeaProjects\tamboui
> 基于 TamboUI 源码（D:\IdeaProjects\tamboui）调研，分析 ToolkitRunner 处理鼠标和按键事件的最佳实践。
> 调研时间：2025年

## 一、架构概览

TamboUI 的事件处理分为三层架构：

```
TuiRunner (底层事件循环)
    ↓ 原始 Event
ToolkitRunner (事件路由层)
    ↓ 路由后的 Event
EventRouter → Element (元素处理层)
```

### 1.1 核心类职责

| 类 | 职责 |
|---|---|
| `TuiRunner` | 终端生命周期、原始事件读取、主事件循环、调度器 |
| `ToolkitRunner` | DSL 应用入口，桥接 TuiRunner 与 EventRouter |
| `EventRouter` | 事件路由：按键→焦点元素，鼠标→位置元素 |
| `FocusManager` | 焦点管理：Tab 导航、点击聚焦、自动聚焦 |
| `Element` | 元素接口：定义事件处理方法签名 |
| `StyledElement` | 抽象基类：提供事件处理器注册的 fluent API |

## 二、事件处理流程详解

### 2.1 整体流程 (ToolkitRunner.handleEvent)

```
ToolkitRunner.run()
  └─ tuiRunner.run(handler, renderer)
       ├─ handler = (event, runner) -> handleEvent(event)
       └─ renderer = frame -> { 渲染逻辑 }
```

`handleEvent` 方法 (ToolkitRunner.java:188-210)：

```java
private boolean handleEvent(Event event) {
    // 1. Tick 事件 → 触发重绘（用于动画）
    if (event instanceof TickEvent) {
        lastElapsed = ((TickEvent) event).elapsed();
        return true;
    }

    // 2. 路由到 EventRouter → 元素处理
    EventResult result = eventRouter.route(event);

    // 3. 元素未处理 → 检查退出
    if (result.isHandled()) return true;
    if (event instanceof KeyEvent && ((KeyEvent) event).isQuit()) {
        quit();
        return false;
    }
    return false;
}
```

**关键设计决策**：
- Tick 事件**不经过 EventRouter**，直接触发重绘
- 元素处理优先级高于全局退出（`q`/`Ctrl+C`）
- 返回值 `true` = 需要重绘，`false` = 不重绘

### 2.2 事件路由 (EventRouter.route)

`route` 方法 (EventRouter.java:178-211) 根据事件类型分流：

```
route(event)
  ├─ KeyEvent     → routeKeyEvent()
  ├─ PasteEvent   → routePasteEvent()
  ├─ MouseEvent   → routeGlobalHandlers() → routeMouseEvent()
  └─ 其他         → routeGlobalHandlers()
```

**重要区别**：
- **按键事件**：先路由到焦点元素，再调用全局处理器
- **鼠标事件**：先调用全局处理器，再路由到位置元素
- **粘贴事件**：直接路由到焦点元素

## 三、按键事件处理最佳实践

### 3.1 路由优先级 (routeKeyEvent)

```
routeKeyEvent(event)
  ├─ 1. 焦点导航 (Tab/Shift+Tab) → 最高优先级
  ├─ 2. Escape + 拖拽中 → 取消拖拽
  ├─ 3. 焦点元素.handleKeyEvent() → 元素内置处理
  │    └─ 返回 FOCUS_NEXT/FOCUS_PREVIOUS → 焦点导航
  ├─ 4. 焦点元素.keyEventHandler() → lambda 处理器
  │    └─ 返回 FOCUS_NEXT/FOCUS_PREVIOUS → 焦点导航
  ├─ 5. 全局处理器 (GlobalEventHandler)
  ├─ 6. 所有非焦点元素.handleKeyEvent() → 全局热键
  └─ 7. Escape → 清除焦点
```

### 3.2 最佳实践：元素内置处理 vs Lambda 处理器

**方式一：重写 handleKeyEvent (内置处理)**

```java
// TextInputElement.java:318-328
@Override
public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
    if (!focused) return EventResult.UNHANDLED;
    if (event.isConfirm() && onSubmit != null) {
        onSubmit.run();
        return EventResult.HANDLED;
    }
    return handleTextInputKey(state, event)
        ? EventResult.HANDLED : EventResult.UNHANDLED;
}
```

**方式二：Lambda 处理器 (onKeyEvent)**

```java
// 在 ToolkitApp 或 Builder 中使用
element.onKeyEvent(event -> {
    if (event.isUp()) { count++; return EventResult.HANDLED; }
    if (event.isDown()) { count--; return EventResult.HANDLED; }
    return EventResult.UNHANDLED;
});
```

**选择建议**：
- **内置处理**：元素有固定的按键逻辑（如 TextInput、List），复用性强
- **Lambda 处理器**：应用层业务逻辑，与具体状态绑定

### 3.3 焦点导航支持

元素可以通过返回 `EventResult.FOCUS_NEXT` / `FOCUS_PREVIOUS` 请求焦点移动：

```java
// 在 handleKeyEvent 中
if (event.isDown()) return EventResult.FOCUS_NEXT;
if (event.isUp()) return EventResult.FOCUS_PREVIOUS;
```

EventRouter 会自动处理焦点移动，无需手动调用 `focusManager`。

### 3.4 全局热键注册

```java
// 方式一：ActionHandler
eventRouter.addGlobalHandler(actionHandler);

// 方式二：GlobalEventHandler
eventRouter.addGlobalHandler(event -> {
    if (event instanceof KeyEvent && ((KeyEvent) event).isChar('r')) {
        refreshData();
        return EventResult.HANDLED;
    }
    return EventResult.UNHANDLED;
});
```

全局处理器在焦点元素之后、非焦点元素之前调用，适合应用级快捷键。

## 四、鼠标事件处理最佳实践

### 4.1 路由优先级 (routeMouseEvent)

```
routeMouseEvent(event)
  ├─ 拖拽中:
  │   ├─ DRAG  → dragHandler.onDrag()
  │   └─ RELEASE → endDrag()
  ├─ PRESS (左键):
  │   ├─ 1. 反向遍历元素 → 命中检测 (z-order)
  │   ├─ 2. 聚焦该元素 (如果可聚焦)
  │   ├─ 3. 检查拖拽 → startDrag()
  │   ├─ 4. 元素.handleMouseEvent()
  │   ├─ 5. 元素.mouseEventHandler()
  │   └─ 6. 未命中任何元素 → 清除焦点
  ├─ MOVE / SCROLL:
  │   └─ 反向遍历元素 → 命中检测 → 处理
  └─ 其他 → UNHANDLED
```

### 4.2 最佳实践：鼠标事件处理

**方式一：重写 handleMouseEvent**

```java
// ListElement.java:866-898
@Override
public EventResult handleMouseEvent(MouseEvent event) {
    EventResult result = super.handleMouseEvent(event);
    if (result.isHandled()) return result;

    if (lastItemCount > 0) {
        if (event.kind() == MouseEventKind.SCROLL_UP) {
            selectPrevious();
            return EventResult.HANDLED;
        }
        if (event.kind() == MouseEventKind.SCROLL_DOWN) {
            selectNext(lastItemCount);
            return EventResult.HANDLED;
        }
    }
    return EventResult.UNHANDLED;
}
```

**方式二：Lambda 处理器 (onMouseEvent)**

```java
element.onMouseEvent(event -> {
    if (event.kind() == MouseEventKind.CLICK) {
        handleClick(event.x(), event.y());
        return EventResult.HANDLED;
    }
    return EventResult.UNHANDLED;
});
```

### 4.3 拖拽处理

```java
element
    .draggable()
    .onDrag(new DragHandler() {
        @Override
        public void onDragStart(int startX, int startY) { ... }
        @Override
        public void onDrag(int currentX, int currentY, int deltaX, int deltaY) { ... }
        @Override
        public void onDragEnd(int endX, int endY) { ... }
    });
```

拖拽状态由 EventRouter 管理：
- `startDrag()` → 记录拖拽元素和起始位置
- `onDrag()` → 计算 delta 并回调
- `endDrag()` → 清理状态
- Escape → 取消拖拽

## 五、焦点管理最佳实践

### 5.1 自动聚焦

FocusManager 在渲染时自动聚焦第一个可聚焦元素：

```java
// FocusManager.java:86-88
if (isFirst && focusedId == null) {
    focusedId = elementId;
}
```

ToolkitRunner 在每次渲染后修复焦点状态：

```java
// ToolkitRunner.java:163-169
String currentFocus = focusManager.focusedId();
List<String> focusOrder = focusManager.focusOrder();
if (!focusOrder.isEmpty()) {
    if (currentFocus == null || !focusOrder.contains(currentFocus)) {
        focusManager.setFocus(focusOrder.get(0));
    }
}
```

### 5.2 点击聚焦

鼠标按下时自动聚焦点击的元素：

```java
// EventRouter.java:411-421
if (element.isFocusable() && element.id() != null) {
    String prevFocus = focusManager.focusedId();
    focusManager.setFocus(element.id());
    wasFocused = true;
}
```

### 5.3 焦点清除

- 点击空白区域 → 清除焦点
- Escape 键（无元素处理时）→ 清除焦点

## 六、事件结果 (EventResult)

```java
public enum EventResult {
    HANDLED,          // 已处理，停止传播
    UNHANDLED,        // 未处理，继续传播
    FOCUS_NEXT,       // 请求焦点移到下一个
    FOCUS_PREVIOUS;   // 请求焦点移到上一个
}
```

**使用规则**：
- 处理了事件 → 返回 `HANDLED`
- 未处理 → 返回 `UNHANDLED`
- 支持上下箭头导航 → 返回 `FOCUS_NEXT` / `FOCUS_PREVIOUS`

## 七、渲染与事件的生命周期

### 7.1 每帧渲染流程

```
1. clearFocusables()    → 清除上一帧的焦点注册
2. eventRouter.clear()  → 清除上一帧的元素注册
3. elementRegistry.clear() → 清除元素区域注册
4. root.render()        → 渲染元素树（注册焦点、注册元素）
5. renderContext.registerElement() → 注册根元素
6. 修复焦点状态
7. 后处理处理器
```

### 7.2 事件循环

```
while (running) {
    event = pollEvent(timeout);  // 优先处理输入事件
    if (event == null) continue;

    if (event instanceof UiRunnable) → 执行并继续
    if (event instanceof ResizeEvent) → 重绘并继续
    if (event == TOGGLE_DEBUG) → 切换调试层并重绘

    shouldRedraw = handler.handle(event);
    if (shouldRedraw) safeRender(renderer);
}
```

## 八、配置建议

### 8.1 TuiConfig 关键参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `pollTimeout` | 40ms | 事件轮询超时 |
| `tickRate` | 40ms (≈25fps) | 定时重绘间隔，null=禁用 |
| `mouseCapture` | false | 是否启用鼠标捕获 |
| `bracketedPaste` | false | 是否启用粘贴模式 |

### 8.2 动画应用配置

```java
TuiConfig.withAnimation(Duration.ofMillis(16)); // ≈60fps
```

### 8.3 静态应用配置

```java
TuiConfig.builder().noTick().build(); // 禁用定时重绘
```

## 九、总结：最佳实践清单

### 按键事件
1. ✅ 元素固定逻辑 → 重写 `handleKeyEvent()`
2. ✅ 应用层业务逻辑 → `onKeyEvent()` lambda
3. ✅ 上下箭头导航 → 返回 `FOCUS_NEXT`/`FOCUS_PREVIOUS`
4. ✅ 全局快捷键 → `addGlobalHandler()`
5. ✅ 退出键 → 框架自动处理 `isQuit()`

### 鼠标事件
1. ✅ 元素固定逻辑 → 重写 `handleMouseEvent()`
2. ✅ 应用层业务逻辑 → `onMouseEvent()` lambda
3. ✅ 拖拽 → `draggable().onDrag(handler)`
4. ✅ 滚动 → 检查 `SCROLL_UP`/`SCROLL_DOWN`

### 焦点管理
1. ✅ 可聚焦元素 → 设置 `.id()` + `.focusable()`
2. ✅ 自动聚焦 → 框架自动处理第一个可聚焦元素
3. ✅ 点击聚焦 → 框架自动处理
4. ✅ 焦点清除 → Escape / 点击空白

### 事件结果
1. ✅ 已处理 → `EventResult.HANDLED`
2. ✅ 未处理 → `EventResult.UNHANDLED`
3. ✅ 导航请求 → `EventResult.FOCUS_NEXT`/`FOCUS_PREVIOUS`

## 十、ToolkitRunner DSL 使用详解

ToolkitRunner 提供了一套声明式 DSL（领域特定语言），通过静态工厂方法和 Fluent API 构建 TUI 界面。

> 源码路径：`D:\IdeaProjects\tamboui\tamboui-toolkit\src\main\java\dev\tamboui\toolkit\`

### 10.1 DSL 入口

使用 DSL 前需要静态导入 Toolkit 类的所有方法：

```java
import static dev.tamboui.toolkit.Toolkit.*;
```

`Toolkit.java`（1328 行）是 DSL 的静态工厂类，提供所有内置元素的创建方法。

### 10.2 应用启动方式

DSL 应用有三种启动方式：

#### 方式一：ToolkitApp 继承（推荐）

```java
public class MyApp extends ToolkitApp {
    private int count = 0;

    @Override
    protected Element render() {
        return panel("Counter",
            text("Count: " + count).bold().cyan()
        )
        .id("main").focusable()
        .onKeyEvent(event -> {
            if (event.isUp()) { count++; return EventResult.HANDLED; }
            if (event.isDown()) { count--; return EventResult.HANDLED; }
            return EventResult.UNHANDLED;
        });
    }

    public static void main(String[] args) throws Exception {
        new MyApp().run();
    }
}
```

`ToolkitApp` 生命周期：`onStart()` → `run(render)` → `onStop()`。

#### 方式二：ToolkitRunner.create() + Lambda

```java
try (var runner = ToolkitRunner.create()) {
    int[] count = {0};
    runner.run(() ->
        panel("Counter",
            text("Count: " + count[0]).bold().cyan()
        )
        .id("counter").focusable()
        .onKeyEvent(event -> {
            if (event.isUp()) { count[0]++; return EventResult.HANDLED; }
            return EventResult.UNHANDLED;
        })
    );
}
```

#### 方式三：Builder 模式（高级配置）

```java
try (var runner = ToolkitRunner.builder()
        .bindings(BindingSets.vim())
        .app(this)
        .withAutoBindingRegistration()
        .styleEngine(styleEngine)
        .faultTolerant(true)
        .build()) {
    runner.run(() -> /* element tree */);
}
```

Builder 支持：
- `.bindings()` — 设置按键绑定方案
- `.app()` — 设置 `@OnAction` 注解的应用对象
- `.withAutoBindingRegistration()` — 自动注册注解处理器
- `.styleEngine()` — 设置 CSS 样式引擎
- `.faultTolerant()` — 启用容错渲染
- `.errorOutput()` — 设置错误输出流
- `.postRenderProcessor()` — 添加后渲染处理器

### 10.3 元素创建方法（Toolkit 静态工厂）

| 分类 | 方法 | 说明 |
|---|---|---|
| **文本** | `text(content)` | 纯文本元素 |
| | `richText(text)` | 富文本（Span 样式） |
| | `richTextArea(text)` | 可滚动富文本区域 |
| | `markupText(markup)` | BBCode 标记文本 |
| | `markupTextArea(markup)` | 可滚动标记文本区域 |
| | `waveText(text)` | 波浪动画文本 |
| **容器** | `panel(title, children...)` | 带标题面板 |
| | `row(children...)` | 水平行布局 |
| | `column(children...)` | 垂直列布局 |
| | `columns(children...)` | 多列网格布局 |
| | `grid(children...)` | CSS Grid 布局 |
| | `dock()` | 上下左右中停靠布局 |
| | `stack(children...)` | 层叠布局（画家算法） |
| | `flow(children...)` | 流式布局（自动换行） |
| | `dialog(title, children...)` | 对话框（自动居中） |
| | `spacer()` | 弹性空白 |
| **数据** | `list(items...)` | 列表 |
| | `table()` | 表格 |
| | `tabs(titles...)` | 标签页 |
| | `tree(roots...)` | 树形控件 |
| | `form(state)` | 表单容器 |
| | `formField(label, state)` | 表单字段 |
| **输入** | `textInput(state)` | 文本输入框 |
| | `textArea(state)` | 多行文本输入 |
| **图表** | `gauge(ratio)` | 仪表盘 |
| | `lineGauge(ratio)` | 线条仪表 |
| | `sparkline(data...)` | 迷你趋势图 |
| | `barChart(values...)` | 柱状图 |
| | `chart()` | 折线图 |
| | `canvas(bounds...)` | 画布 |
| **其他** | `calendar(date)` | 日历 |
| | `scrollbar(state)` | 滚动条 |
| | `spinner()` | 加载动画 |
| | `widget(widget)` | 包装底层 Widget |
| | `lazy(supplier)` | 延迟求值元素 |

所有容器类元素都支持 `Supplier<Element>` 延迟加载重载：

```java
panel("Counter", () -> text("Count: " + count))
row(() -> text("Dynamic content"))
```

### 10.4 Fluent API 链式调用

所有元素继承自 `StyledElement<T>`（986 行），提供丰富的链式方法：

#### 前景色

```java
text("Hello").black().red().green().yellow().blue().magenta().cyan().white().gray()
text("Custom").fg(Color.rgb(255, 128, 0))  // 自定义颜色
```

#### 背景色

```java
text("Hello").onBlack().onRed().onGreen().onYellow().onBlue().onMagenta().onCyan().onWhite()
text("Custom").bg(Color.rgb(0, 128, 255))
```

#### 文本修饰

```java
text("Bold").bold()
text("Dim").dim()
text("Italic").italic()
text("Underlined").underlined()
text("Reversed").reversed()
text("Crossed").crossedOut()
```

#### 布局约束

```java
element.length(10)      // 固定长度
element.percent(50)     // 父容器 50%
element.fill()          // 填充剩余空间
element.fill(2)         // 填充权重 2
element.min(5)          // 最小尺寸
element.max(100)        // 最大尺寸
element.fit()           // 自适应内容
element.constraint(Constraint.ratio(16, 9))  // 宽高比
```

#### 焦点与标识

```java
element.id("myId")          // 设置 ID（不可变）
element.focusable()          // 加入 Tab 导航链
element.focusable(false)     // 取消可聚焦
```

> 注意：`focusable()` 和 `onKeyEvent()` 是正交的。`focusable()` 控制 Tab 导航，`onKeyEvent()` 注册按键处理器。两者可独立使用。

#### CSS 类与属性

```java
element.addClass("highlight", "bordered")  // 添加 CSS 类
element.removeClass("old-class")           // 移除 CSS 类
element.toggleClass("active", isActive)    // 条件切换
element.attr("data-type", "info")          // 设置属性选择器
```

### 10.5 事件处理器注册

#### onKeyEvent — 按键事件

```java
element.onKeyEvent(event -> {
    if (event.isUp()) return EventResult.HANDLED;
    if (event.isDown()) return EventResult.HANDLED;
    if (event.isConfirm()) return EventResult.HANDLED;
    if (event.isCancel()) return EventResult.HANDLED;
    if (event.isChar('r')) { refresh(); return EventResult.HANDLED; }
    return EventResult.UNHANDLED;
});
```

常用 `KeyEvent` 方法：
- `isUp()` / `isDown()` / `isLeft()` / `isRight()` — 方向键
- `isConfirm()` — Enter
- `isCancel()` — Escape
- `isFocusNext()` — Tab
- `isFocusPrevious()` — Shift+Tab
- `isQuit()` — q / Ctrl+C
- `isChar(c)` — 指定字符
- `code()` — 键码
- `codePoint()` — Unicode 码点
- `string()` — 输入的字符串
- `modifiers()` — 修饰键状态

#### onMouseEvent — 鼠标事件

```java
element.onMouseEvent(event -> {
    if (event.kind() == MouseEventKind.CLICK) {
        handleClick(event.x(), event.y());
        return EventResult.HANDLED;
    }
    if (event.kind() == MouseEventKind.SCROLL_UP) {
        scrollUp();
        return EventResult.HANDLED;
    }
    return EventResult.UNHANDLED;
});
```

`MouseEventKind` 枚举：`PRESS`、`RELEASE`、`CLICK`、`DRAG`、`MOVE`、`SCROLL_UP`、`SCROLL_DOWN`。

#### onAction — 动作处理器（绑定系统）

```java
element.onAction(handler);  // 同时注册按键和鼠标的 ActionHandler
```

#### on(trigger, handler) — 触发器绑定

```java
element.on(MouseTrigger.click(), e -> color("RED"))
       .on(KeyTrigger.ch('r'), e -> color("RED"));
```

支持 `KeyTrigger` 和 `MouseTrigger`，多个 `on()` 调用会链式组合。

#### onDrag — 拖拽处理

```java
// 方式一：DragHandler 接口
element.onDrag(new DragHandler() {
    public void onDragStart(int startX, int startY) { }
    public void onDrag(int x, int y, int dx, int dy) { }
    public void onDragEnd(int endX, int endY) { }
});

// 方式二：增量回调
element.draggable((deltaX, deltaY) -> {
    offsetX += deltaX;
    offsetY += deltaY;
});
```

### 10.6 全局事件处理器

通过 `EventRouter` 注册全局处理器：

```java
// 方式一：GlobalEventHandler Lambda
runner.eventRouter().addGlobalHandler(event -> {
    if (event instanceof KeyEvent && ((KeyEvent) event).isChar('r')) {
        refreshData();
        return EventResult.HANDLED;
    }
    return EventResult.UNHANDLED;
});

// 方式二：ActionHandler
runner.eventRouter().addGlobalHandler(actionHandler);
```

全局处理器在按键事件中的调用时机：焦点元素之后、非焦点元素之前。

### 10.7 定时任务

```java
// 延迟执行
runner.schedule(() -> {
    runner.runOnRenderThread(() -> message = "Delayed!");
}, Duration.ofSeconds(2));

// 固定频率重复
var task = runner.scheduleRepeating(() -> {
    runner.runOnRenderThread(() -> counter++);
}, Duration.ofMillis(100));

// 固定延迟重复（等执行完成再调度下一次）
runner.scheduleWithFixedDelay(action, Duration.ofMillis(500));

// 取消任务
task.cancel();
```

> 定时任务在调度器线程执行，修改 UI 状态必须通过 `runOnRenderThread()` 确保线程安全。

### 10.8 后渲染处理器

通过 Builder 注册后渲染处理器，在每帧渲染完成后执行：

```java
ToolkitRunner.builder()
    .postRenderProcessor((frame, elementRegistry, styledAreaRegistry, focusManager, elapsed) -> {
        // 在渲染完成后执行效果、覆盖层等操作
    })
    .build();
```

### 10.9 InlineToolkitRunner（内联模式）

`InlineToolkitRunner` 适用于进度条、安装向导等内联显示场景（不切换备选屏幕）：

```java
try (var runner = InlineToolkitRunner.create(4)) {
    double[] progress = {0.0};
    runner.run(() -> column(
        waveText("Installing...").cyan(),
        gauge(progress[0]).label(String.format("%.0f%%", progress[0] * 100))
    ));
}
```

额外支持：
- `println(element)` — 在视口上方打印元素
- `println(message)` — 在视口上方打印文本
- 自动计算内容高度并调整视口

### 10.10 完整示例：计数器应用

```java
import static dev.tamboui.toolkit.Toolkit.*;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;

public class CounterApp extends ToolkitApp {
    private int count = 0;

    @Override
    protected Element render() {
        return panel("Counter Demo",
            text("Count: " + count)
                .bold().cyan()
                .id("count-display"),
            row(
                text("[Up] Increase").green(),
                spacer(),
                text("[Down] Decrease").red(),
                spacer(),
                text("[q] Quit").dim()
            )
        )
        .rounded()
        .id("main-panel")
        .focusable()
        .onKeyEvent(event -> {
            if (event.isUp()) { count++; return EventResult.HANDLED; }
            if (event.isDown()) { count--; return EventResult.HANDLED; }
            return EventResult.UNHANDLED;
        });
    }

    public static void main(String[] args) throws Exception {
        new CounterApp().run();
    }
}
```

### 10.11 DSL 设计要点总结

1. **静态工厂 + Fluent API**：`Toolkit.text()` 创建元素，`.bold().cyan()` 链式设置样式
2. **不可变 ID**：元素 ID 一旦设置不可更改
3. **自动 ID 生成**：`focusable()` 元素未设置 ID 时自动生成
4. **正交设计**：`focusable()` 与 `onKeyEvent()` 独立控制
5. **延迟加载**：容器类支持 `Supplier<Element>` 实现按需渲染
6. **线程安全**：定时任务中修改 UI 需通过 `runOnRenderThread()`
7. **容错渲染**：Builder 启用后，单个元素渲染失败不会导致整个界面崩溃
8. **CSS 集成**：通过 `styleEngine()` 和 `addClass()` 支持 CSS 样式
