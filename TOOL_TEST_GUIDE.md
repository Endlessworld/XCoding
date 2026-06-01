# Agent 工具测试指南

本文档提供 XAgent 所有工具的标准化测试流程，用于验证工具功能的正确性。

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

#### 1.1 ls - 列出目录文件
```bash
ls(arg0="D:/IdeaProjects/agi_working", arg1=3)
```
- **功能**: 列出工作目录下的所有文件
- **参数**: 
  - `arg0`: 目录路径
  - `arg1`: 遍历深度 (默认5, 最大10)
- **预期**: 返回文件列表和行数信息

#### 1.2 read_file - 读取文件内容
```bash
# 基本读取
read_file(arg0=["D:/IdeaProjects/agi_working/README.md"], arg1=0, arg2=50)

# 分页读取 (读取前2行)
read_file(arg0=["文件路径"], arg1=0, arg2=2)
```
- **功能**: 读取一个或多个文件的内容
- **参数**:
  - `arg0`: 文件路径数组
  - `arg1`: 行偏移量 (默认0)
  - `arg2`: 最大行数 (默认500)
- **预期**: 返回文件内容，带行号

#### 1.3 write_file - 创建/写入文件
```bash
write_file(arg0="D:/IdeaProjects/agi_working/test.md", arg1="文件内容")
```
- **功能**: 创建新文件或覆盖已有文件
- **参数**:
  - `arg0`: 绝对路径
  - `arg1`: 文件内容
- **预期**: 返回写入结果和文件信息

#### 1.4 edit_file - 编辑文件内容
```bash
# 单次替换
edit_file(arg0="文件路径", arg1="旧文本", arg2="新文本")

# 全部替换
edit_file(arg0="文件路径", arg1="旧文本", arg2="新文本", arg3=true)
```
- **功能**: 精确字符串替换编辑文件
- **参数**:
  - `arg0`: 文件绝对路径
  - `arg1`: 要查找的文本
  - `arg2`: 替换的新文本
  - `arg3`: 是否替换所有出现 (默认false)
- **预期**: 返回替换次数和位置信息

#### 1.5 glob - 文件模式匹配
```bash
# 查找所有 md 文件
glob(arg0="**/*.md")

# 查找 Kotlin 文件
glob(arg0="**/*.kt")
```
- **功能**: 查找与模式匹配的文件
- **参数**:
  - `arg0`: glob 模式 (如 `**/*.java`, `*.txt`)
- **预期**: 返回匹配的文件路径列表

#### 1.6 grep - 文件内容搜索
```bash
# 仅返回文件名
grep(arg0="搜索文本", arg1="目录路径", arg2="*.md", arg3="files_with_matches")

# 返回匹配内容
grep(arg0="搜索文本", arg1="目录路径", arg2="*.md", arg3="content")

# 返回匹配计数
grep(arg0="搜索文本", arg1="目录路径", arg2="*.md", arg3="count")
```
- **功能**: 在文件中搜索文本
- **参数**:
  - `arg0`: 要搜索的文本 (字面字符串，非正则)
  - `arg1`: 搜索目录路径
  - `arg2`: 文件过滤模式 (如 `*.java`)
  - `arg3`: 输出格式 (files_with_matches/content/count)
- **预期**: 返回匹配的文件和位置信息

---

### 2. 终端命令工具

#### 2.1 Bash - 执行 Shell 命令
```bash
# 普通命令
Bash(arg0="java -version", arg2="检查 Java 版本")

# 带超时 (毫秒)
Bash(arg0="ping -n 5 127.0.0.1", arg2="测试命令", arg1=30000)

# 后台执行
Bash(arg0="ping -n 10 127.0.0.1", arg2="后台命令", arg3=true)

# 交互式 shell (支持 stdin 输入)
Bash(arg0="cmd /k", arg2="启动交互式shell", arg3=true, arg4=true)
```
- **功能**: 执行 Shell 命令
- **参数**:
  - `arg0`: 要执行的命令
  - `arg1`: 超时时间 (毫秒, 默认120000, 最大600000)
  - `arg2`: 命令描述 (5-10字)
  - `arg3`: 是否后台运行 (默认false)
  - `arg4`: 是否交互式 shell (默认false, 需要 runInBackground=true)
- **预期**: 返回命令输出和退出码

#### 2.1.1 可重入功能 (增量输出获取)
```bash
# 启动长时间运行的命令
Bash(arg0="powershell -Command 'for($i=1; $i -le 30; $i++) { Write-Host $i; Start-Sleep 1 }'", arg2="长时间任务", arg3=true)

# 第一次获取输出 (返回前 N 行)
BashOutput(arg0="shell_xxx")

# 第二次获取输出 (只返回新增的输出 - 可重入)
BashOutput(arg0="shell_xxx")
```
- **功能**: 后台命令支持增量输出获取，每次调用只返回自上次检查后的新输出
- **用途**: 监控长时间运行的构建、测试等任务

#### 2.2 BashOutput - 获取后台命令输出
```bash
# 获取输出
BashOutput(arg0="shell_xxx")

# 带正则过滤
BashOutput(arg0="shell_xxx", arg1="^test$")
```
- **功能**: 获取后台命令的输出
- **参数**:
  - `arg0`: 后台 shell ID
  - `arg1`: 可选正则表达式过滤
- **预期**: 返回命令输出和状态 (isAlive)

#### 2.3 KillShell - 杀死后台进程
```bash
KillShell(arg0="shell_xxx")
```
- **功能**: 终止正在运行的后台命令
- **参数**:
  - `arg0`: 后台 shell ID
- **预期**: 返回成功/失败状态

#### 2.4 ShellInput - 向交互式 shell 发送命令
```bash
# 先创建交互式 shell
Bash(arg0="cmd /k", arg2="交互式shell", arg3=true, arg4=true)

# 发送命令到交互式 shell
ShellInput(arg0="shell_xxx", arg1="dir")
```
- **功能**: 向交互式 shell 会话发送命令
- **参数**:
  - `arg0`: 交互式 shell ID
  - `arg1`: 要发送的命令
- **注意**: 需要先通过 Bash 工具创建 `interactive=true` 的 shell

#### 2.5 ShellSessions - 列出所有活跃会话
```bash
ShellSessions()
```
- **功能**: 列出所有活跃的 shell 会话（交互式和后台）
- **返回**: 每个会话的 ID、类型、状态和命令

---

### 3. 技能系统工具

#### 3.1 read_skill - 读取技能
```bash
read_skill(skill_name="pdf-extractor")
```
- **功能**: 从 SkillRegistry 读取已注册的技能
- **参数**:
  - `skill_name`: 技能名称
- **预期**: 返回技能完整内容 (SKILL.md)
- **注意**: 需要项目中有注册的技能文件才能测试完整功能

---

### 4. 网络工具

#### 4.1 webSearch - 网络搜索
```bash
webSearch(arg0=["搜索关键词1", "搜索关键词2"], arg1="oneYear", arg2=true, arg3=3)
```
- **功能**: 从搜索引擎检索网络信息
- **参数**:
  - `arg0`: 搜索关键词数组 (最多5个)
  - `arg1`: 时间范围 (noLimit/oneYear/oneMonth/oneWeek/oneDay)
  - `arg2`: 是否返回摘要 (默认true)
  - `arg3`: 结果数量 (1-10, 默认3)
- **预期**: 返回搜索结果列表

---

## 📝 标准化测试流程

### 完整测试清单

```
□ 1. ls 工具 - 列出目录文件
□ 2. read_file 工具 - 读取文件内容
□ 3. read_file 工具 - 分页读取 (offset/limit)
□ 4. write_file 工具 - 创建/写入文件
□ 5. edit_file 工具 - 编辑文件内容
□ 6. edit_file 工具 - replace_all 参数
□ 7. glob 工具 - 模式匹配 (*.md)
□ 8. glob 工具 - 模式匹配 (*.kt)
□ 9. grep 工具 - 搜索文本 (files_with_matches)
□ 10. grep 工具 - 搜索文本 (content)
□ 11. grep 工具 - 搜索文本 (count)
□ 12. read_skill 工具 - 读取技能
□ 13. Bash 工具 - 执行 shell 命令
□ 14. Bash 工具 - 超时参数
□ 15. Bash 工具 - 后台执行
□ 16. Bash 工具 - 交互式 shell (interactive=true)
□ 17. BashOutput 工具 - 获取后台输出
□ 18. BashOutput 工具 - 可重入功能 (多次获取增量输出)
□ 19. BashOutput 工具 - 正则过滤
□ 20. KillShell 工具 - 杀死后台进程
□ 21. ShellInput 工具 - 向交互式 shell 发送命令
□ 22. ShellSessions 工具 - 列出所有活跃会话
□ 23. web_search 工具 - 网络搜索
```

### 快速测试脚本 (PowerShell)

```powershell
# 清理测试文件
Get-ChildItem "D:\IdeaProjects\agi_working" -Filter "test_*.md" | Remove-Item

# 测试 write_file
write_file(arg0="D:/IdeaProjects/agi_working/test_quick.md", arg1="测试内容")

# 测试 read_file
read_file(arg0=["D:/IdeaProjects/agi_working/test_quick.md"])

# 测试 edit_file
edit_file(arg0="D:/IdeaProjects/agi_working/test_quick.md", arg1="测试", arg2="验证")

# 测试 glob
glob(arg0="**/*.md")

# 测试 grep
grep(arg0="XAgent", arg1="D:/IdeaProjects/agi_working", arg2="*.md", arg3="count")

# 测试 Bash
Bash(arg0="java -version", arg2="检查Java版本")

# 测试后台命令
Bash(arg0="ping -n 3 127.0.0.1", arg2="后台测试", arg3=$true)

# 清理
del "D:\IdeaProjects\agi_working\test_quick.md"
```

---

## ⚠️ 注意事项

1. **路径格式**: Windows 路径使用正斜杠 `D:/IdeaProjects/...`
2. **文件清理**: 测试完成后务必删除创建的测试文件
3. **后台命令**: 使用 `arg3=true` 启动后台命令，保存返回的 `bash_id`
4. **网络限制**: web_search 可能因网络限制返回空结果
5. **网页抓取**: web_fetch 依赖目标网站可访问性，超时时间 15 秒
5. **技能系统**: 需要项目中有 `.claude/settings/` 目录下的技能配置

---

## 🔧 常用测试命令速查

| 工具 | 最小测试命令 |
|------|------------|
| ls | `ls("D:/IdeaProjects/agi_working", 3)` |
| read_file | `read_file(["文件路径"])` |
| write_file | `write_file("路径", "内容")` |
| edit_file | `edit_file("路径", "旧", "新")` |
| glob | `glob("**/*.md")` |
| grep | `grep("文本", "目录", "*.md", "count")` |
| Bash | `Bash("命令", "描述")` |
| web_fetch | `web_fetch("https://example.com")` |
| KillShell | `KillShell("shell_id")` |

---

*最后更新: 2026-03-11*