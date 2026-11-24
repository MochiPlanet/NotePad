# NotePad 记事本

项目基于 Android 官方早期 NotePad 示例扩展而来，完成了「移动软件开发期中实验」要求的基础功能，并额外实现了笔记分类、置顶、排序、导出等扩展功能。

## 一、已实现的功能

### 基础要求

1. **笔记列表显示时间戳**
   - 每张笔记卡片右下角显示修改时间。
   - 时间会智能格式化：`今天 09:30`、`昨天 18:02`、`3月8日 14:20`、`2024年3月8日`。
   - 编辑器中显示完整的创建时间 / 修改时间。

2. **笔记查询**
   - 顶部搜索框实时搜索，同时匹配**标题**和**正文**。
   - 输入后自动过滤（220ms 防抖），命中文字会以黄色高亮显示。
   - 可清空搜索；无结果时显示对应的空状态提示。

### 扩展功能（已完成 5 项）

3. **笔记分类**：默认 / 工作 / 学习 / 生活，列表顶部可按分类筛选，编辑器内可切换分类，不同分类使用不同颜色。
4. **笔记置顶**：长按笔记可置顶/取消置顶，置顶笔记始终排在最前面，并显示置顶图标。
5. **笔记排序**：右上角排序按钮支持「最近修改优先、最早修改优先、标题 A→Z、创建时间优先」，选择会被记住。
6. **导出笔记**：右上角导出按钮将全部笔记导出为 UTF-8 文本文件（通过系统文件选择器保存，带 UTF-8 BOM，Windows 记事本可直接打开）。
7. **UI 美化**：渐变标题栏、圆角卡片、分类色条、搜索高亮、空状态插画、悬浮新建按钮等。

### 其他细节

- 编辑内容在离开页面时自动保存，空笔记自动删除。
- 长按笔记可打开、置顶、分享、删除。
- 编辑器支持实时字数统计、分类选择、置顶切换、删除确认。
- 笔记数量统计。

## 二、主要文件

| 文件 | 说明 |
| --- | --- |
| `NotesList.java` | 笔记列表：加载、搜索、分类筛选、排序、置顶、删除、导出 |
| `NoteEditor.java` | 新建/编辑笔记：自动保存、分类、置顶、字数统计、删除 |
| `NoteListAdapter.java` | 列表卡片适配器：时间、分类颜色、搜索高亮 |
| `NoteUtils.java` | 时间格式化、分类颜色、搜索转义等工具 |
| `NotePad.java` | ContentProvider 契约，新增 `category`、`pinned` 字段和排序常量 |
| `NotePadProvider.java` | SQLite 数据访问，数据库版本升级到 3（保留旧数据） |
| `activity_notes_list.xml` | 列表页布局 |
| `activity_note_editor.xml` | 编辑页布局 |
| `noteslist_item.xml` | 笔记卡片布局 |

## 三、数据库

表名仍为 `notes`，在原有字段基础上新增：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `category` | TEXT | 分类，默认 `默认` |
| `pinned` | INTEGER | 是否置顶，1 置顶，0 普通 |

数据库版本从 2 升级到 3 时使用 `ALTER TABLE` 原地升级，不会清空已有笔记。

## 四、运行

用 Android Studio 打开项目，等待 Gradle Sync 完成后直接运行 `app` 即可。

- `compileSdk 37`（使用本机已安装的 Android SDK Platform 37）
- `targetSdk 34`
- `minSdk 21`
- 无第三方依赖，只使用 Android 平台 API。

### 模拟器

本机已安装 Android 官方 **AEHD 2.2** 加速驱动，并创建了 AVD `NotePadAPI23`（Android 6.0）。双击桌面「启动NotePad模拟器」，或运行项目根目录的 `start_emulator.bat`，脚本会自动启动模拟器、安装并打开 App。之后在 Android Studio 设备下拉框选择 `emulator-5554` 即可 Run。

详细说明见 [运行说明.txt](运行说明.txt)。

## 五、界面截图

- [笔记列表](docs/screenshots/01_note_list.png)
- [搜索高亮](docs/screenshots/02_search.png)
- [笔记编辑](docs/screenshots/03_note_editor.png)
- [分类筛选](docs/screenshots/04_category_filter.png)
- [长按菜单](docs/screenshots/05_note_actions.png)
- [排序方式](docs/screenshots/06_sort.png)
- [导出完成](docs/screenshots/07_export.png)
- [空状态](docs/screenshots/08_empty_state.png)

## 六、说明

- 本项目保留了原示例的 `ContentProvider` 数据共享结构，列表通过 `CursorLoader` 异步读取数据，数据变化后自动刷新。
- 原来的 `TitleEditor`（旧式标题编辑对话框）、`NotesLiveFolder`（已废弃的 Live Folder）和基于 `android.test` 的旧测试已移除，标题可直接在编辑器中修改。
- 搜索使用 `LIKE` 并转义了 `%`、`_` 等通配符，因此搜索这些字符不会出错。

原始示例说明见下：

> 项目来自于官方早期的数据库操作的基本教程。
> 更多的解析参考 [Android Sample--NotePad解析](https://blog.csdn.net/llfjfz/article/details/67638499)
