# TamboUI 框架事件系统深度分析

> 研究范围：`E:\local-github\tamboui` 框架源码  
> 核心模块：`tamboui-toolkit`、`tamboui-tui`  
> 分析维度：DSL 鼠标事件、按键事件、焦点转移、事件传播机制

---

## 1. 事件体系总览

TamboUI 的事件系统采用**分层架构**，由四个核心层组成：

| 层级        | 核心类/接口                                          | 职责                    |
|-----------|-------------------------------------------------|-----------------------|
| **事件定义层** | `Event`, `KeyEvent`, `MouseEvent`, `PasteEvent` | 封装原始输入数据              |
| **触发器层**  | `InputTrigger`, `KeyTrigger`, `MouseTrigger`    | 定义事件匹配规则              |
| **绑定层**   | `Bindings`, `ActionHandler`, `BindingSets`      | 语义动作映射（键盘/鼠标 -> 业务动作） |
| **路由层**   | `EventRouter`, `FocusManager`                   | 事件分发、焦点管理、传播控制        |

事件处理的结果由 `EventResult` 枚举表达：

```java
public enum EventResult {
    HANDLED,        // 事件被消费，停止传播
    UNHANDLED,      // 事件未处理，继续传播
    FOCUS_NEXT,     // 请求将焦点移到下一个元素
    FOCUS_PREVIOUS  // 请求将焦点移到上一个元素
}
```

---

## 2. 鼠标事件机制

### 2.1 事件模型

`MouseEvent` 是不可变值对象，包含以下字段：

```
+ kind: MouseEventKind    // PRESS | RELEASE | DRAG | MOVE | SCROLL_UP | SCROLL_DOWN
+ button: MouseButton     // LEFT | RIGHT | MIDDLE | NONE
+ x, y: int               // 终端坐标（0-based）
+ modifiers: KeyModifiers // Ctrl / Alt / Shift 状态
+ bindings: Bindings      // 语义动作匹配用的绑定集
```

**坐标系**：直接使用终端单元格坐标，左上角为 `(0,0)`，与渲染帧对齐。

### 2.2 触发器匹配（MouseTrigger）

`MouseTrigger` 通过组合匹配鼠标事件：

```java
MouseTrigger.click()          // 左键按下
MouseTrigger.

rightClick()     // 右键按下
MouseTrigger.

scrollUp()       // 滚轮上滚（button = NONE）
MouseTrigger.

drag(MouseButton.LEFT)  // 左键拖拽
MouseTrigger.

of(MouseEventKind.PRESS, MouseButton.RIGHT, true,false,false)
// Alt+右键按下（自定义）
```

匹配规则（`matchesMouse`）：

1. `kind` 必须完全相等
2. `button` 非 NONE 时必须相等；SCROLL 类事件 button 为 NONE，跳过此检查
3. `Ctrl` / `Alt` / `Shift` 修饰符必须全部精确匹配

### 2.3 事件路由流程（EventRouter）

鼠标事件通过 `EventRouter.routeMouseEvent()` 分发，整体流程如下：

```
1. 检查是否处于拖拽状态
   ├─ DRAG 事件 -> 直接发给 draggingElement 的 DragHandler
   └─ RELEASE 事件 -> 结束拖拽

2. 新按下事件（PRESS + 左键）
   └─ 按 z-order 反向遍历 elements（后渲染的在最上层）
      ├─ 命中测试：area.contains(x, y)
      ├─ 若元素 focusable -> 自动 setFocus(element.id)
      ├─ 若元素 draggable 且有 DragHandler -> 启动拖拽，返回 HANDLED
      ├─ 调用 element.handleMouseEvent(event)
      ├─ 调用 element.mouseEventHandler().handle(event)
      ├─ 若以上任一返回 HANDLED -> 停止遍历
      └─ 若只是 focus 了但没 handler -> 也返回 HANDLED（吞掉点击）
         
3. 其他事件（MOVE / SCROLL_UP / SCROLL_DOWN）
   └─ 同样按 z-order 反向遍历，但仅检查 area.contains，不自动 focus

4. 点击空白处 -> focusManager.clearFocus()
```

**关键源码**（`EventRouter.java:378-499`）：

```java
private EventResult routeMouseEvent(long routeId, MouseEvent event) {
    // 1. 拖拽状态优先
    if (draggingElement != null) {
        if (event.kind() == MouseEventKind.DRAG) {
            dragHandler.onDrag(event.x(), event.y(), deltaX, deltaY);
            return EventResult.HANDLED;
        }
        if (event.kind() == MouseEventKind.RELEASE) {
            endDrag(event.x(), event.y());
            return EventResult.HANDLED;
        }
    }

    // 2. 左键按下：z-order 反向遍历，focus + drag + handler
    if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            Element element = elements.get(i);
            Rect area = getArea(element);
            if (area != null && area.contains(event.x(), event.y())) {
                // 自动 focus
                if (element.isFocusable() && element.id() != null) {
                    focusManager.setFocus(element.id());
                }
                // 检查拖拽
                if (element.isDraggable() ...) {
                    startDrag(...); return HANDLED;
                }
                // handler 调用
                EventResult result = element.handleMouseEvent(event);
                if (result.isHandled()) return result;
                MouseEventHandler handler = element.mouseEventHandler();
                if (handler != null) {
                    result = handler.handle(event);
                    if (result.isHandled()) return result;
                }
            }
        }
        focusManager.clearFocus(); // 点击空白
    }
    ...
}
```

### 2.4 DSL 注册方式

在 `StyledElement` 中，DSL 提供以下鼠标事件注册 API：

| DSL 方法                               | 作用                        |
|--------------------------------------|---------------------------|
| `onMouseEvent(MouseEventHandler)`    | 设置原始鼠标处理器                 |
| `on(MouseTrigger.click(), e -> ...)` | 按触发器精确绑定，自动标记 HANDLED     |
| `onAction(ActionHandler)`            | 通过 Bindings 语义系统同时绑定按键和鼠标 |
| `onDrag(DragHandler)`                | 启用拖拽并设置生命周期回调             |
| `draggable((dx, dy) -> ...)`         | 简化版拖拽，仅接收增量位移             |

**`on(InputTrigger, Consumer)` 的链式机制**：

```java
public T on(InputTrigger trigger, Consumer<Event> handler) {
    MouseEventHandler prev = this.mouseHandler;
    onMouseEvent(event -> {
        if (trigger.matches(event)) {
            handler.accept(event);
            return EventResult.HANDLED;
        }
        return prev != null ? prev.handle(event) : EventResult.UNHANDLED;
    });
}
```

每次调用 `on()` 都会把旧 handler 包装进新 lambda 中，形成**反向注册顺序的调用链**：后注册的先检查，不匹配则委托给前一个。

### 2.5 拖拽生命周期

拖拽由 `EventRouter` 内部维护状态，不依赖底层鼠标报告协议：

```
PRESS (在 draggable 元素上) -> startDrag()
   ├─ 记录 draggingElement, dragHandler, dragStartX/Y
   ├─ 调用 handler.onDragStart(x, y)
   │
   ├─ DRAG 事件 -> handler.onDrag(x, y, deltaX, deltaY)
   │   └─ delta 是相对于 dragStartX/Y 的累积值
   │
   └─ RELEASE 事件 -> handler.onDragEnd(x, y) -> 清理状态
```

`Escape` 键也可在 `routeKeyEvent` 中取消当前拖拽。

---

## 3. 按键事件机制

### 3.1 事件模型

`KeyEvent` 是不可变值对象：

```
+ code: KeyCode         // CHAR | UP | DOWN | LEFT | RIGHT | ENTER | ESCAPE | TAB | ...
+ modifiers: KeyModifiers
+ character: int        // Unicode code point，仅在 code == CHAR 时有效
+ bindings: Bindings
```

`KeyCode` 枚举覆盖所有特殊键，可打印字符统一用 `KeyCode.CHAR`。

### 3.2 触发器匹配（KeyTrigger）

```java
KeyTrigger.ch('j')                     // 精确字符 'j'
KeyTrigger.

chIgnoreCase('j')           // 'j' 或 'J'
KeyTrigger.

key(KeyCode.UP)             // 方向键上
KeyTrigger.

ctrl('u')                   // Ctrl+U
KeyTrigger.

key(KeyCode.TAB, false,false,true) // Shift+Tab
```

匹配规则（`matchesKey`）：

1. `keyCode` 必须相等
2. `Ctrl` / `Alt` 必须**精确匹配**
3. `Shift` 仅当触发器明确要求时才检查（`shift && !mods.shift()` 则失败）
4. 对 `CHAR` 事件，字符按 `ignoreCase` 标志比较

### 3.3 事件路由流程（EventRouter）

按键事件通过 `EventRouter.routeKeyEvent()` 分发，优先级严格分层：

```
1. 焦点导航快捷键（最高优先级）
   ├─ Shift+Tab -> focusManager.focusPrevious()
   └─ Tab       -> focusManager.focusNext()

2. Escape 取消拖拽
   └─ 若正在拖拽 -> endDrag()，返回 HANDLED

3. 聚焦元素优先处理（focused element first）
   ├─ 遍历 elements 找到 focusedId 匹配的元素
   ├─ 调用 element.handleKeyEvent(event, true)   // focused=true
   ├─ 检查返回值：
   │   ├─ HANDLED -> 停止
   │   ├─ FOCUS_NEXT -> focusManager.focusNext()
   │   └─ FOCUS_PREVIOUS -> focusManager.focusPrevious()
   ├─ 调用 element.keyEventHandler().handle(event)
   │   └─ 同样检查 FOCUS_NEXT / FOCUS_PREVIOUS
   └─ 若未处理，继续下一步

4. 全局处理器（Global Handlers）
   └─ 按注册顺序遍历 globalHandlers
      └─ 任一返回 HANDLED 则停止

5. 非聚焦元素兜底（global hotkeys）
   └─ 遍历所有非 focused 的 elements
      └─ 调用 element.handleKeyEvent(event, false) // focused=false

6. Escape 清除焦点
   └─ 若仍未处理且是 Escape -> clearFocus()
```

**设计意图**：

- **Text Input 优先**：文本输入框在 focused 时会先收到字符键，避免被全局快捷键拦截
- **Global Actions 兜底**：如 `q` 退出、Ctrl+C 等，在文本输入未消费时生效
- **容器内子元素也能收按键**：`ContainerElement` 会把事件 forward 给 children

### 3.4 DSL 注册方式

| DSL 方法                             | 作用                          |
|------------------------------------|-----------------------------|
| `onKeyEvent(KeyEventHandler)`      | 设置原始按键处理器                   |
| `on(KeyTrigger.ch('q'), e -> ...)` | 按触发器精确绑定                    |
| `onAction(ActionHandler)`          | 语义动作系统，支持多套快捷键方案            |
| `focusable()`                      | 使元素参与 TAB 导航（不自动添加 handler） |

**`focusable()` 与 `onKeyEvent()` 是正交的**：

- 仅用 `focusable()` → 元素可 TAB 导航，但收到按键后无 handler，默认 UNHANDLED
- 仅用 `onKeyEvent()` → 元素可处理按键但不参与 TAB 导航（适合容器内快捷键）
- 两者都用 → 完整交互体验

---

## 4. 焦点转移机制

### 4.1 FocusManager 数据结构

```java
public final class FocusManager {
    private String focusedId;                    // 当前焦点元素 ID
    private final List<String> focusOrder;       // TAB 遍历顺序（按注册顺序）
    private final Map<String, Rect> focusableAreas; // 焦点元素区域（用于 focusAt）
}
```

### 4.2 焦点注册

焦点元素在渲染时通过 `DefaultRenderContext` 自动注册：

```java
// StyledElement.render()
if(isFocusable() &&elementId ==null){
elementId =IdGenerator.

newId(this);  // 自动生成 ID
}
        ctx.

registerElement(this,area);

// DefaultRenderContext.registerElement()
focusManager.

registerFocusable(element.id(),area);
```

`registerFocusable` 行为：

- 将元素 ID 加入 `focusOrder`（去重，保持首次注册顺序）
- 记录元素区域到 `focusableAreas`
- **自动焦点**：若当前无任何焦点，`focusedId = 第一个注册的 focusable 元素`

### 4.3 焦点导航方式

| 方式               | 触发源            | 实现                              | 行为                               |
|------------------|----------------|---------------------------------|----------------------------------|
| **TAB 正向**       | 按键 `Tab`       | `focusNext()`                   | 按 `focusOrder` 顺序循环到下一个          |
| **Shift+TAB 反向** | 按键 `Shift+Tab` | `focusPrevious()`               | 按 `focusOrder` 逆序循环到上一个          |
| **点击聚焦**         | 鼠标左键按下         | `setFocus(id)`                  | 在 `routeMouseEvent` 中自动执行        |
| **焦点请求**         | handler 返回值    | `FOCUS_NEXT` / `FOCUS_PREVIOUS` | `EventRouter` 收到后调用 focusManager |
| **坐标聚焦**         | 程序调用           | `focusAt(x, y)`                 | 遍历 `focusableAreas` 找包含坐标的元素     |
| **清除焦点**         | Escape / 点击空白  | `clearFocus()`                  | `focusedId = null`               |

**循环遍历算法**：

```java
public boolean focusNext() {
    int index = focusOrder.indexOf(focusedId);
    int nextIndex = (index + 1) % focusOrder.size();
    focusedId = focusOrder.get(nextIndex);
    return true;
}
```

### 4.4 焦点状态对事件处理的影响

- **`focused=true`**：元素及其容器内的 children 都会以 `focused=true` 接收 `handleKeyEvent`
- **渲染反馈**：`RenderContext` 提供 `isFocused(element.id())` 查询，用于渲染高亮/光标等视觉状态
- **鼠标点击自动 focus**：即使元素没有 mouse handler，只要 `focusable=true` 且被点击，就会获得焦点

---

## 5. 事件传播机制

TamboUI **不采用 DOM 的事件捕获/冒泡模型**，而是使用基于 `EventResult` 的**处理即停止（consume-on-handle）**模型。

### 5.1 传播控制核心规则

```
HANDLED    -> 立即停止，后续元素/处理器不再收到事件
UNHANDLED  -> 继续传播到下一个候选
FOCUS_NEXT/FOCUS_PREVIOUS -> 触发焦点转移后停止
```

### 5.2 两层传播体系

#### 层 A：EventRouter 层（跨元素）

```
按键事件：
focused element (handleKeyEvent + keyEventHandler)
    -> global handlers
    -> unfocused elements (按注册顺序)

鼠标/其他事件：
global handlers
    -> element at position (z-order 反向，先 hit 先处理)
```

#### 层 B：ContainerElement 层（父子元素间）

```java
// 按键在容器内的传播
public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
    EventResult result = super.handleKeyEvent(event, focused);
    if (result.isHandled()) return result;   // 父容器处理了 -> 停止

    for (Element child : children) {
        if (child.handleKeyEvent(event, focused) == HANDLED) {
            return HANDLED;                     // 任一子元素处理 -> 停止
        }
    }
    return UNHANDLED;
}

// 鼠标在容器内的传播
public EventResult handleMouseEvent(MouseEvent event) {
    EventResult result = super.handleMouseEvent(event);
    if (result.isHandled()) return result;

    for (Element child : children) {
        Rect area = child.renderedArea();
        if (area != null && area.contains(event.x(), event.y())) {
            if (child.handleMouseEvent(event) == HANDLED) {
                return HANDLED;
            }
        }
    }
    return UNHANDLED;
}
```

**关键点**：

- 父容器先尝试处理，若未处理则按顺序交给 children
- 子元素继承父容器的 `focused` 状态（`ContainerElement` 中 `focused` 参数原样传给 children）
- 鼠标事件只发给 `renderedArea` 包含鼠标坐标的子元素

### 5.3 Global Handlers

`EventRouter` 支持全局处理器，用于应用级快捷键：

```java
public void addGlobalHandler(GlobalEventHandler handler);

public void addGlobalHandler(ActionHandler handler);  // 便捷方法
```

- 按键事件：**在 focused element 之后**调用 global handlers，避免拦截文本输入
- 鼠标事件：**在 element handler 之前**调用 global handlers，允许全局拦截

### 5.4 拖拽事件的旁路传播

拖拽一旦启动，后续 `DRAG` 和 `RELEASE` 事件**不再走常规 z-order 路由**，而是被 `draggingElement` 独占：

```java
if(draggingElement !=null){
        if(event.

kind() ==DRAG){dragHandler.

onDrag(...); return HANDLED; }
        if(event.

kind() ==RELEASE){

endDrag(...); return HANDLED; }
        }
```

这意味着拖拽期间，鼠标即使移过其他元素，也不会触发那些元素的 hover/click 处理。

### 5.5 与 DOM 事件模型的差异

| 特性   | TamboUI                       | DOM/Browser               |
|------|-------------------------------|---------------------------|
| 传播方向 | 基于优先级的顺序分发                    | 捕获 -> 目标 -> 冒泡            |
| 停止方式 | 返回 `HANDLED`                  | `event.stopPropagation()` |
| 默认行为 | 无"默认行为"概念，不处理即无效果             | `event.preventDefault()`  |
| 父子关系 | `ContainerElement` 显式 forward | 自动冒泡/捕获                   |
| 全局监听 | `GlobalEventHandler` 显式注册     | `window.addEventListener` |
| 焦点系统 | `FocusManager` 显式管理列表         | 浏览器原生焦点链                  |

---

## 6. Bindings（语义动作映射）系统

### 6.1 核心概念

Bindings 将**底层物理输入**（如 "按下 j 键"）映射为**高层语义动作**（如 "moveDown"），使业务代码与快捷键方案解耦。

```java
public interface Bindings {
    boolean matches(Event event, String action);

    Optional<String> actionFor(Event event);      // 反向查找：事件 -> 动作名

    List<InputTrigger> triggersFor(String action);
}
```

### 6.2 预设绑定集

`BindingSets` 从 classpath 的 `.properties` 文件加载：

| 预设           | 来源文件                  | 特点                            |
|--------------|-----------------------|-------------------------------|
| `standard()` | `standard.properties` | 仅方向键，不占用字母键                   |
| `vim()`      | `vim.properties`      | hjkl 导航，g/G, Ctrl+u/d         |
| `emacs()`    | `emacs.properties`    | Ctrl+n/p/f/b, Alt+v, Ctrl+a/e |
| `intellij()` | `intellij.properties` | IDE 风格                        |
| `vscode()`   | `vscode.properties`   | VS Code 风格                    |

Properties 格式示例：

```properties
moveUp=Up, k
moveDown=Down, j
confirm=Enter
quit=q, Ctrl+c
click=Mouse.Left.Press
scrollUp=Mouse.ScrollUp
```

### 6.3 ActionHandler 工作流程

```java
ActionHandler handler = new ActionHandler(BindingSets.vim())
        .on(Actions.QUIT, e -> runner.quit())
        .on("save", this::save);

// 使用
boolean handled = handler.dispatch(event);  // event -> bindings -> action -> handler
```

`StyledElement.onAction(handler)` 内部同时注册按键和鼠标 handler，实现**同一动作多输入源触发**。

---

## 7. 关键源码索引

| 文件路径                                                    | 核心内容                                                          |
|---------------------------------------------------------|---------------------------------------------------------------|
| `tamboui-toolkit/src/.../event/EventRouter.java`        | 事件路由总入口，分发按键/鼠标/粘贴事件                                          |
| `tamboui-toolkit/src/.../focus/FocusManager.java`       | 焦点状态、TAB 导航、焦点区域管理                                            |
| `tamboui-toolkit/src/.../element/StyledElement.java`    | DSL API：focusable, onKeyEvent, onMouseEvent, onAction, onDrag |
| `tamboui-toolkit/src/.../element/ContainerElement.java` | 父子事件转发逻辑                                                      |
| `tamboui-toolkit/src/.../element/Element.java`          | 元素接口：isFocusable, handleKeyEvent, handleMouseEvent            |
| `tamboui-toolkit/src/.../event/EventResult.java`        | 事件处理结果枚举                                                      |
| `tamboui-tui/src/.../event/KeyEvent.java`               | 按键事件模型、语义查询方法                                                 |
| `tamboui-tui/src/.../event/MouseEvent.java`             | 鼠标事件模型、语义查询方法                                                 |
| `tamboui-tui/src/.../bindings/KeyTrigger.java`          | 按键触发器匹配规则                                                     |
| `tamboui-tui/src/.../bindings/MouseTrigger.java`        | 鼠标触发器匹配规则                                                     |
| `tamboui-tui/src/.../bindings/ActionHandler.java`       | 语义动作分发器                                                       |
| `tamboui-tui/src/.../bindings/BindingSets.java`         | 预设绑定集加载与解析                                                    |
| `tamboui-tui/src/.../bindings/Actions.java`             | 标准语义动作常量                                                      |

---

## 8. 设计模式总结

1. **策略模式**：`InputTrigger` / `KeyTrigger` / `MouseTrigger` 将匹配逻辑与事件分发解耦
2. **责任链模式**：`ContainerElement` 按顺序将事件传递给 children，直到有人 HANDLED
3. **命令模式**：`Bindings` + `ActionHandler` 将输入事件映射为语义动作，再调用具体 handler
4. **模板方法模式**：`StyledElement.render()` 定义渲染骨架，子类实现 `renderContent()`
5. **状态模式**：`EventRouter` 内部维护 `draggingElement` 状态，改变鼠标事件处理路径
