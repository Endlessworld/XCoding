# Agent 工具测试指南

本文档提供 XAgent 所有工具的标准化测试流程，用于验证工具功能正确性，覆盖 tools 包 15 个工具类、19 个工具。本版已用 `smart_edit` 取代已移除的 `edit_file`，并补充 `Sleep`、`contextCacheTool`、`write_todos`、`run_groovy_script`、`worker`、`msg` 等工具。

---

## 📋 测试前准备

### 工作目录
```
D:/IdeaProjects/agi_working
```

### 测试文件清理
测试完成后清理所有测试文件：
```bash
del "D:\IdeaProjects\agi_working\<测试文件>"
```

---

## 🧪 核心工具测试流程

### 1. 文件操作工具

#### 1.1 read_file - 读取文件内容
```json
read_file(filePaths=["D:/IdeaProjects/agi_working/README.md"], offset=0, limit=50)
```
- **功能**: 读取一个或多个文件/目录内容，支持批量、分页、目录递归
- **参数**:
  - `filePaths`: 绝对路径列表（必填）
  - `offset`: 行偏移量（默认 0）
  - `limit`: 最大行数（默认 500）
  - `workspaceOnly`: 是否仅允许工作目录内（默认 true）
- **预期**: 返回带行号内容（cat -n 格式），超长行自动截断

#### 1.2 write_file - 创建/写入文件
```json
write_file(filePath="D:/IdeaProjects/agi_working/test.md", content="文件内容")
```
- **功能**: 创建新文件或覆盖已有文件（≤500 字符）
- **参数**:
  - `filePath`: 绝对路径（必填）
  - `content`: 文件内容（必填，≤500 字符）
  - `workspaceOnly`: 是否仅允许工作目录内（默认 true）
- **预期**: 自动创建父目录，返回写入结果、字节数、行数

#### 1.3 smart_edit - 智能编辑文件
```json
// search_replace 模式：按唯一文本替换
smart_edit(edits=[
  {filePath:"文件路径", mode:"search_replace", searchText:"旧文本", replaceText:"新文本"}
])

// insert_at_line 模式：在指定行插入
smart_edit(edits=[
  {filePath:"文件路径", mode:"insert_at_line", line:5, newContent:"新增代码", position:"before"}
])

// 批量编辑：一次提交多个操作
smart_edit(edits=[
  {filePath:"文件", mode:"search_replace", searchText:"旧A", replaceText:"新A"},
  {filePath:"文件", mode:"insert_at_line", line:10, newContent:"新B"}
])
```
- **功能**: 高效智能编辑，支持 search_replace 与 insert_at_line 两种模式及批量执行
- **参数**:
  - `edits`: 编辑操作数组（必填，≤50 个）
    - `search_replace`: 需 `searchText`（唯一）+ `replaceText`
    - `insert_at_line`: 需 `line`（≥1）+ `newContent` + `position`(before/after)
- **预期**: 返回每个编辑的成功/失败结果、行号、耗时

#### 1.4 ls - 列出目录文件
```json
ls(directory="D:/IdeaProjects/agi_working", maxDepth=3, workspaceOnly=true)
```
- **功能**: 列出目录下文件，过滤 .gitignore，附行数统计
- **参数**:
  - `directory`: 目录绝对路径
  - `maxDepth`: 遍历深度（默认 3，最大 5）
  - `workspaceOnly`: 是否仅限工作目录内（默认 true）
- **预期**: 返回目录统计与文件列表（含行数）

#### 1.5 glob - 文件模式匹配
```json
glob(patterns=["**/*.md"])
glob(patterns=["**/*.kt", "**/*.java"])  // 支持多模式
```
- **功能**: 查找与 glob 模式匹配的文件（最多 25 个结果）
- **参数**:
  - `patterns`: 模式数组（必填），如 `**/*.md`、`*.txt`、`/src/**/*.xml`
- **预期**: 返回匹配文件的绝对路径列表，跳过 .gitignore 与重型目录

#### 1.6 grep - 文件内容搜索
```json
// 仅返回文件名
grep(pattern="XAgent", path="D:/IdeaProjects/agi_working", glob="*.md", outputMode="files_with_matches")
// 返回匹配内容
grep(pattern="TODO", path="D:/IdeaProjects/agi_working", glob="*.java", outputMode="content")
// 返回匹配计数
grep(pattern="XAgent", path="D:/IdeaProjects/agi_working", glob="*.md", outputMode="count")
```
- **功能**: 在文件中搜索文本（字面字符串，非正则，默认大小写敏感）
- **参数**:
  - `pattern`: 搜索文本（必填）
  - `path`: 搜索目录（默认工作区根）
  - `glob`: 文件过滤模式
  - `outputMode`: files_with_matches / content / count（默认 files_with_matches）
- **预期**: 返回匹配文件/行，附行号定位；>10MB 大文件自动跳过

---

### 2. 终端命令工具

#### 2.1 Bash - 执行 Shell 命令
```json
// 普通命令（once 模式）
Bash(command="java -version", title="检查 Java 版本", timeout=30000, mode="once")

// 后台运行
Bash(command="ping -n 10 127.0.0.1", title="后台命令", mode="once", timeout=30000)

// 交互式 shell（保持状态）
Bash(command="cmd", title="启动交互式 shell", mode="interactive")
```
- **功能**: 在超时保护的持久 shell 会话中执行命令
- **参数**:
  - `command`: 命令（必填）
  - `timeout`: 超时毫秒（默认 120000，最小 1000，最大 600000）
  - `mode`: once（默认，一次性）/ interactive（持久交互式，返回 bash_id）
- **预期**: 返回命令输出与退出码；超时未完成时返回交互式会话

#### 2.2 BashOutput - 获取后台命令输出
```json
BashOutput(bash_id="shell_xxx")
BashOutput(bash_id="shell_xxx", filter="^test$")  // 正则过滤
```
- **功能**: 读取后台/交互式 shell 会话的输出（只返回自上次检查后的新增输出）
- **参数**:
  - `bash_id`: shell 会话 ID
  - `filter`: 可选正则过滤
- **预期**: 返回增量输出与会话状态

#### 2.3 ShellInput - 向交互式 shell 发送命令
```json
// 先创建交互式 shell
Bash(command="cmd", title="交互式 shell", mode="interactive")
// 再向该会话发送命令
ShellInput(shell_id="shell_xxx", input="dir")
```
- **功能**: 向交互式 shell 会话发送命令
- **参数**:
  - `shell_id`: 交互式 shell ID
  - `input`: 要发送的命令
- **预期**: 命令被送入会话执行，通过 BashOutput 读取结果

#### 2.4 ShellSessions - 列出活跃会话
```json
ShellSessions()
```
- **功能**: 列出所有活跃 shell 会话
- **预期**: 返回各会话 ID、状态与命令

#### 2.5 KillShell - 终止 shell 会话
```json
KillShell(bash_id="shell_xxx")
```
- **功能**: 终止后台/交互式 shell 会话
- **参数**:
  - `bash_id`: 目标会话 ID
- **预期**: 返回终止成功/失败状态

#### 2.6 Sleep - 休眠等待
```json
Sleep(seconds=30)
```
- **功能**: 休眠指定秒数后唤醒，等待长耗时任务
- **参数**:
  - `seconds`: 秒数（必填正整数，最大 600）
- **预期**: 返回实际休眠秒数

---

### 3. 网络工具

#### 3.1 web_search - 网络搜索
```json
web_search(queryList=["XAgent 工具", "Spring AI"], count=3, freshness="oneYear", summary=true)
```
- **功能**: 使用 Bing 搜索引擎检索网络信息
- **参数**:
  - `queryList`: 查询列表（必填，≤5 个）
  - `count`: 结果数（1-10，默认 3）
  - `freshness`: noLimit/oneYear/oneMonth/oneWeek/oneDay
  - `summary`: 是否返回摘要（默认 true）
- **预期**: 返回标题、URL、摘要列表

#### 3.2 web_fetch - 网页抓取
```json
web_fetch(url="https://example.com", maxLength=1000)
```
- **功能**: 抓取指定 URL，清洗 HTML 提取纯文本
- **参数**:
  - `url`: 完整 URL（必填）
  - `maxLength`: 返回最大字符数（默认 1000，最大 5000）
- **预期**: 返回页面标题、正文、总长度与返回长度

---

### 4. 上下文管理工具

#### 4.1 contextCacheTool - 指针数据读取器
```json
contextCacheTool(refs=["$ref+arg:tool_call_123", "$ref+resp:tool_call_456"])
```
- **功能**: 根据指针地址重新获取超长工具调用的参数/结果
- **参数**:
  - `refs`: 指针地址列表（必填），格式 `$ref+arg:工具调用id` 或 `$ref+resp:工具调用id`
- **预期**: 返回各指针对应的原始内容

#### 4.2 write_todos - ACP 任务管理
```json
write_todos(entries=[
  {content:"分析项目结构", status:"IN_PROGRESS", priority:"HIGH"},
  {content:"实现功能A", status:"PENDING", priority:"MEDIUM"},
  {content:"测试功能A", status:"COMPLETED", priority:"MEDIUM"}
])
```
- **功能**: 创建/管理结构化任务列表（ACP Plan 模式）
- **参数**:
  - `entries`: 任务数组（必填），含 content、status、priority
- **预期**: 向客户端发送实时 Plan 更新

---

### 5. 编排与 Worker 工具

#### 5.1 run_groovy_script - 多工具编排
```groovy
// 查看可用工具
def names = tools.names

// 调用工具（命名参数）
def r = tools.read_file([filePaths: ['/a.txt']])
println(r)

// 位置参数
def r2 = tools.read_file(['/a.txt'], 0, 100)

// 单参数工具
tools.Sleep([seconds: 3])
```
- **功能**: 执行 Groovy 脚本，动态调用已注册工具进行并发/分支编排
- **参数**:
  - `script`: Groovy 源码（必填）
  - `cwd`: 脚本工作目录（可选，可经绑定变量 `cwd` 访问）
- **预期**: 返回脚本执行结果（序列化为 JSON/文本）

#### 5.2 worker - 启动隔离 Worker
```json
worker(
  worker_type="general-purpose",
  task_id="task-001",
  title="调研并返回报告",
  description="研究 XAgent 工具并返回综合报告",
  result_type="json",
  file_name="report.md"
)
```
- **功能**: 启动短暂 Worker 处理复杂、独立的隔离任务
- **参数**:
  - `worker_type`: Worker 类型（必填）
  - `task_id`: 唯一任务 ID（必填）
  - `title`: 简短描述（必填，5-10 词）
  - `description`: 任务描述（必填）
  - `result_type`: 期望返回格式 text/boolean/json/file（可选）
  - `file_name`: 期望成果物文件路径（可选）
- **预期**: 返回 Worker 执行结果（含 content 或 filePath）

#### 5.3 msg - Worker 回传结果
```json
// worker 完成任务后调用，回传成果
msg(success=true, content="任务完成", worker_type="general-purpose", result_type="text")
```
- **功能**: Worker 专有工具，将执行成果回传给主智能体
- **参数**:
  - `success`: 是否完成目标（必填）
  - `content`: 成果内容（必填）
  - `worker_type`: Worker 类型
  - `file_name`: 成果物文件名（可选）
  - `result_type`: text/boolean/json/file（可选）
- **预期**: 返回 JSON `{success, worker_type, result_type, content 或 filePath}`；内容过大自动写文件

---

## 📝 标准化测试流程

### 完整测试清单

```
□ 1. read_file 工具 - 基本读取
□ 2. read_file 工具 - 分页读取 (offset/limit)
□ 3. write_file 工具 - 创建/写入文件
□ 4. smart_edit 工具 - search_replace 替换
□ 5. smart_edit 工具 - insert_at_line 插入
□ 6. smart_edit 工具 - 批量编辑
□ 7. ls 工具 - 列出目录文件
□ 8. glob 工具 - 模式匹配 (支持多模式)
□ 9. grep 工具 - 搜索文本 (files_with_matches)
□ 10. grep 工具 - 搜索文本 (content)
□ 11. grep 工具 - 搜索文本 (count)
□ 12. Bash 工具 - 执行 shell 命令
□ 13. Bash 工具 - 交互式 shell (interactive)
□ 14. BashOutput 工具 - 获取后台输出
□ 15. ShellInput 工具 - 向交互式 shell 发送命令
□ 16. ShellSessions 工具 - 列出活跃会话
□ 17. KillShell 工具 - 终止会话
□ 18. Sleep 工具 - 休眠等待
□ 19. web_search 工具 - 网络搜索
□ 20. web_fetch 工具 - 网页抓取
□ 21. contextCacheTool 工具 - 指针数据读取
□ 22. write_todos 工具 - ACP 任务管理
□ 23. run_groovy_script 工具 - 多工具编排
□ 24. worker 工具 - 启动隔离 Worker
□ 25. msg 工具 - Worker 回传结果
```

### 快速测试脚本

```
# 清理测试文件
# 测试 write_file
write_file(filePath="D:/IdeaProjects/agi_working/test_quick.md", content="XAgent 测试内容")
# 测试 read_file
read_file(filePaths=["D:/IdeaProjects/agi_working/test_quick.md"])
# 测试 smart_edit (search_replace)
smart_edit(edits=[{filePath:"D:/IdeaProjects/agi_working/test_quick.md", mode:"search_replace", searchText:"测试", replaceText:"验证"}])
# 测试 glob
glob(patterns=["**/*.md"])
# 测试 grep
grep(pattern="XAgent", path="D:/IdeaProjects/agi_working", glob="*.md", outputMode="count")
# 测试 Bash
Bash(command="java -version", title="检查 Java 版本")
# 测试 Sleep
Sleep(seconds=2)
# 清理
del "D:\IdeaProjects\agi_working\test_quick.md"
```

---

## ⚠️ 注意事项

1. **路径格式**: Windows 路径建议使用正斜杠 `D:/IdeaProjects/...`
2. **文件清理**: 测试完成后务必删除创建的测试文件
3. **后台命令**: 使用 Bash 的 `mode="interactive"` 启动交互式会话，保存返回的 `bash_id`
4. **网络限制**: web_search / web_fetch 可能因网络限制返回空结果，超时 15 秒
5. **workspaceOnly**: 默认仅允许工作目录内操作，如需跨目录请显式传 `workspaceOnly=false`
6. **smart_edit**: search_replace 的 searchText 必须唯一，否则报错并返回匹配位置
7. **worker/msg**: msg 为 Worker 专有工具，仅在 Worker 生命周期内有效

---

## 🔧 常用测试命令速查

| 工具 | 最小测试命令 |
|------|------------|
| read_file | `read_file(filePaths=["路径"])` |
| write_file | `write_file(filePath="路径", content="内容")` |
| smart_edit | `smart_edit(edits=[{filePath:"路径",mode:"search_replace",searchText:"旧",replaceText:"新"}])` |
| ls | `ls(directory="D:/IdeaProjects/agi_working", maxDepth=3)` |
| glob | `glob(patterns=["**/*.md"])` |
| grep | `grep(pattern="文本", path="目录", glob="*.md", outputMode="count")` |
| Bash | `Bash(command="命令", title="描述")` |
| BashOutput | `BashOutput(bash_id="shell_id")` |
| ShellInput | `ShellInput(shell_id="shell_id", input="命令")` |
| ShellSessions | `ShellSessions()` |
| KillShell | `KillShell(bash_id="shell_id")` |
| Sleep | `Sleep(seconds=30)` |
| web_search | `web_search(queryList=["关键词"])` |
| web_fetch | `web_fetch(url="https://example.com")` |
| contextCacheTool | `contextCacheTool(refs=["$ref+arg:id"])` |
| write_todos | `write_todos(entries=[{content:"任务",status:"PENDING",priority:"MEDIUM"}])` |
| run_groovy_script | `run_groovy_script(script="println(tools.names)")` |
| worker | `worker(worker_type="general-purpose", task_id="task-001", title="任务", description="描述")` |
| msg | `msg(success=true, content="结果")` |

---

*最后更新: 2026-03-11（扫描重写版，对齐当前 tools 包）*
