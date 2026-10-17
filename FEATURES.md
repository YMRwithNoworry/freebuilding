# FreeBuilding 功能说明

FreeBuilding 是一个面向 Minecraft 1.20.1 Forge 的城市建造与世界生成模组。它可以把玩家选中的建筑保存为可复用预设，再通过城市规划器、创建世界设置和自动世界生成系统，构建由多个建筑组成的城市、乡村和荒地聚落。

## 1. 环境要求

- Minecraft `1.20.1`
- Minecraft Forge `47.4.28` 或兼容的 Forge 47.x 版本
- Java `17`
- 可选：The Lost Cities 1.20.1 Forge 版本

FreeBuilding 不把 Lost Cities 声明为编译期依赖。没有安装 Lost Cities 时，模组仍可以独立运行。

## 2. 核心物品

### 城市法杖

城市法杖用于选择建筑区域：

- 右键方块：设置第一个角点。
- 左键方块：设置第二个角点。
- Shift+右键方块：取消当前选区。
- 右键空气：取消当前选区。
- 客户端会显示选区线框、半透明区域、坐标和体积。
- 选区会按玩家保存，重新登录或切换维度后仍可同步。

选择完成后，可以使用 `/fb save <名称>` 保存建筑预设。

### 城市规划器

城市规划器用于打开城市 GUI。也可以执行：

```text
/fb gui
```

创造模式下，城市法杖和城市规划器可以从“自由建筑”创造模式物品栏中获取。

## 3. 建筑预设

### 保存内容

建筑预设使用 Minecraft `StructureTemplate` 保存，包含：

- 建筑方块和方块状态。
- 部分支持的方块实体数据，例如箱子等。
- 建筑的宽度、长度和高度。
- 用于预览、按需生成和自动生成的结构数据。

实体不会被保存，避免把玩家、生物或其他动态实体复制进建筑。

### 共享位置

预设库位于 Minecraft 实例的配置目录：

```text
.minecraft/config/freebuilding/presets/
```

预设库属于实例级数据，因此可以被不同存档和不同世界共同使用。

每个预设通常同时保存两种文件：

```text
my_house.nbt
my_house.fbp
```

- `.nbt`：标准 Minecraft 结构文件，方便使用标准结构工具。
- `.fbp`：FreeBuilding 可移植预设格式，包含格式版本、Minecraft 版本和压缩后的结构 NBT。

旧版本存档中的以下目录会在服务器启动时自动迁移：

```text
<存档>/freebuilding/presets/
```

如果同名 `.nbt` 和 `.fbp` 同时存在，FreeBuilding 优先使用 `.fbp`，以便执行格式检查和 DataFixer 数据升级。

### 跨存档和跨版本复制

导出预设：

```text
/fb export <名称>
```

文件会写入：

```text
.minecraft/config/freebuilding/exports/
```

导入预设时，把 `.fbp` 文件放入：

```text
.minecraft/config/freebuilding/imports/
```

然后执行：

```text
/fb import preset <文件>
```

覆盖同名预设：

```text
/fb import preset <文件> force
```

`.fbp` 可以直接复制到另一个实例的 `presets/` 目录中，启动时会被加载，也可以通过 `imports/` 和命令导入。加载旧 Minecraft 版本的结构数据时，FreeBuilding 会尝试使用目标版本的 Minecraft DataFixer。已经删除、改名或无法在目标版本中表达的方块，不能保证完全还原。

### 预设限制

默认限制如下：

- 单个轴向最大 `64` 格。
- 总体积最大 `110,592` 个方块。
- 限制可以在 `config/freebuilding-common.toml` 中调整。

如果建筑大于当前城市地块，保存时会自动扩大地块。城市 GUI 中仍然显示 `!` 标记，用于提示当前地块不足以容纳某个预设。

## 4. 城市规划 GUI

城市 GUI 提供以下功能：

### 建筑页

- 查看共享预设列表。
- 调整每个预设的生成权重。
- 启用或禁用预设。
- 删除预设。
- 查看尺寸、来源和预览信息。
- 导入 Lost Cities 建筑。

### 布局页

- 城市样式：现代、废墟、工业、自然。
- 城市网格大小。
- 地块大小。
- 道路宽度。
- 建筑位置抖动。
- 旋转模式。
- 城市种子。
- 自动适配最大预设。

### 装饰页

- 废墟概率。
- 路灯概率。
- 公园概率。
- 地基、道路、人行道和装饰配置。

### 世界页

- 开启或关闭自动城市生成。
- 调整城市生成概率。
- 查找最近的自动城市。
- 导出当前城市预览图。
- 导入 Lost Cities 预设。

### 乡村页

- 开启或关闭乡村生成。
- 选择独栋、村庄或混合模式。
- 调整乡村生成概率。
- 调整乡村地块大小。

城市预览可以保存为：

```text
.minecraft/screenshots/freebuilding_preview_<时间>.png
```

## 5. 按需生成城市

在城市 GUI 中确认后，FreeBuilding 可以在玩家附近逐 tick 生成一座城市。

按需生成包括：

- 地形平整。
- 城市地基。
- 道路和人行道。
- 建筑预设随机放置。
- 公园和路灯。
- 废墟装饰。
- 建筑旋转和权重选择。

生成前会进行确认。生成会直接修改世界，没有撤销功能。

快速流程：

```text
/fb wand
```

用城市法杖选择建筑：

```text
/fb save my_house
/fb gui
```

在 GUI 中配置布局和预设后，点击生成城市。

## 6. 自动城市生成

FreeBuilding 的自动城市使用固定的 `256×256` 方块城市单元：

- 每个单元根据世界种子和单元坐标确定是否生成城市。
- 每个区块只生成自己负责的城市切片。
- 支持跨区块建筑。
- 生成使用当前存档配置和启用的预设权重。
- 没有可用预设时，使用内置程序化建筑作为后备。
- 只影响新区块，已经生成的区块不会补生成城市。
- 海洋等不适合平整的区域会跳过。
- FreeBuilding 自动城市只在主世界生成，不会把城市特征加入 Lost Cities 的自定义维度。

默认自动城市概率为 `25%`。创建世界界面可以把新世界城市覆盖率设置为 `100%`，表示所有符合地形条件的新陆地区块都参与城市生成。

查找最近的自动城市：

```text
/fb city locate
```

需要注意，“全地图城市”只作用于符合地形条件且尚未生成的区块，不会修改已经存在的区块，也不会强行在海洋中填海建城。

## 7. 乡村和荒地生成

乡村系统使用独立的 `128×128` 方块单元，生成在没有 FreeBuilding 城市的区域。

### 独栋模式

每个乡村单元可以生成一座独立房屋，并带有少量道路、围栏、农田或装饰。

### 村庄模式

村庄使用最多 `3×3` 的小型布局，包含：

- 房屋。
- 土径。
- 农田。
- 水井。
- 栅栏。
- 路灯。
- 小型公园或空地。

### 荒地样式

沙漠和恶地会使用更干燥、破败的材料和装饰，例如干枯花园与废墟风格。

乡村生成会跳过：

- 海洋、河流和沙滩生物群系。
- 与 FreeBuilding 城市单元重叠的区域。
- 中心已有原版村庄的位置。
- Lost Cities 城区中心。
- 已经生成的区块不会补生成乡村建筑。

乡村默认关闭。可以在城市 GUI 的“乡村”页中开启，并选择 `SINGLE`、`VILLAGE` 或 `MIXED` 模式。

## 8. 创建世界时选择预设

打开原版“创建新世界”页面后，点击“自由建筑预设”按钮，可以配置新世界的 FreeBuilding 生成方案。

可配置内容包括：

- 是否启用城市生成。
- 城市覆盖率。
- 参与生成的共享预设。
- 全选预设。
- 清空选择。
- 单独启用或禁用某个预设。

创建世界页面打开时，如果检测到 Lost Cities，FreeBuilding 会尝试把 Lost Cities 建筑缓存到共享预设库，因此可以在创建世界前直接选择 `lc_*` 预设。

创建世界的设置会保存到新世界目录：

```text
<存档>/freebuilding/creation.json
```

服务器第一次启动时会读取并消费该文件，然后写入：

```text
<存档>/freebuilding/config.json
```

之后该存档的城市配置独立保存，不会继续跟随其他世界变化。新世界创建设置默认关闭乡村生成，城市覆盖率默认可以设置为 `100%`。

## 9. The Lost Cities 兼容

### 建筑预设导入

安装与当前 Minecraft、Forge 版本匹配的 Lost Cities 后，FreeBuilding 会读取 Lost Cities 的建筑、部件、调色板和变体数据，将代表性单栋建筑转换为普通 FreeBuilding 预设：

```text
lc_building1
lc_town00
lc_library00
```

这些预设与普通建筑完全相同，可以用于：

- 城市 GUI。
- 按需城市生成。
- FreeBuilding 自动城市生成。
- 创建世界预设选择。
- `.fbp` 导出和跨存档复制。

导入命令：

```text
/fb import lostcities
```

强制刷新：

```text
/fb import lostcities force
```

也可以在城市 GUI 的“世界”页点击“导入失落的城市”。

### 世界生成互斥

FreeBuilding 和 Lost Cities 同时启用时，两个系统使用以下互斥规则：

1. FreeBuilding 根据世界种子把主世界划分为 `256×256` 城市单元。
2. 如果某个单元被 FreeBuilding 选中，FreeBuilding 负责该单元的城市生成。
3. Lost Cities 的运行时事件会把该单元内的城市特征标记为非城市，并取消可取消的城市区块生成事件。
4. 如果某个单元没有被 FreeBuilding 选中，Lost Cities 保留其原生城市生成。
5. 乡村生成还会查询 Lost Cities 城市信息，避开 Lost Cities 城区中心。

这样在新生成区域中，每个城市单元由 FreeBuilding 或 Lost Cities 其中一方负责，不会同时放置两套城市建筑。

这部分兼容通过反射接入 Lost Cities API，不需要在 `mods.toml` 中增加 Lost Cities 编译期依赖。没有安装 Lost Cities 时，相关钩子不会启用。

互斥规则只对新区块生效。如果两个模组在规则加入前已经生成了重叠城市，FreeBuilding 不会自动拆除或重建已有区块。

### 导入范围

当前导入器主要支持单栋建筑。以下内容不会作为一个完整巨型预设导入：

- `multibuildings` 多建筑组合。
- 地下室完整生态。
- `parts2` 内部部件。
- Lost Cities 的完整道路规划、损坏随机化和地下系统。

多建筑组合引用的单栋建筑仍可能出现在 `lc_*` 预设列表中。箱子、刷怪笼和战利品等方块实体数据可能需要在目标版本中重新检查。

## 10. 命令列表

| 命令 | 权限 | 功能 |
|---|---:|---|
| `/fb wand` | 2 | 获取城市法杖 |
| `/fb planner` | 2 | 获取城市规划器 |
| `/fb pos1` | 0 | 使用视线目标方块设置第一个选点 |
| `/fb pos2` | 0 | 使用视线目标方块设置第二个选点 |
| `/fb clear` | 0 | 清空当前选区 |
| `/fb save <名称> [force]` | 2 | 保存选区为建筑预设 |
| `/fb delete <名称>` | 2 | 删除建筑预设 |
| `/fb list` | 0 | 列出预设、尺寸和权重 |
| `/fb reload` | 2 | 重新加载配置和预设 |
| `/fb export <名称>` | 2 | 导出 `.fbp` 可移植预设 |
| `/fb import preset <文件> [force]` | 2 | 导入 `.fbp` 预设 |
| `/fb import lostcities` | 2 | 导入缺失的 Lost Cities 建筑 |
| `/fb import lostcities force` | 2 | 强制重新导入 Lost Cities 建筑 |
| `/fb gui` | 2 | 打开城市规划 GUI |
| `/fb city build` | 0 | 显示当前城市生成范围 |
| `/fb city build confirm` | 2 | 在玩家位置生成城市 |
| `/fb city locate` | 0 | 查找最近的自动城市 |
| `/fb city preview` | 2 | 导出当前城市预览图 |
| `/fb demo` | 2 | 生成示例小屋并保存为 `demo_house` |

## 11. 数据文件

世界专属数据：

```text
<存档>/freebuilding/
  config.json                 # 当前存档的城市和乡村配置
  creation.json               # 新世界首次启动前的一次性设置
  preview-export.png          # /fb city preview 导出的预览图
  lostcities_imported.marker  # Lost Cities 自动导入标记
```

实例共享数据：

```text
.minecraft/config/freebuilding/
  presets/
    my_house.nbt
    my_house.fbp
    lc_building1.fbp
  imports/                    # 外部 .fbp 文件放置目录
  exports/                    # /fb export 输出目录
```

## 12. 快速开始

### 手动保存建筑并生成城市

```text
/fb wand
```

用城市法杖设置两个角点：

```text
/fb save my_house
/fb gui
```

在 GUI 中启用 `my_house`、调整权重和布局，然后点击“生成城市”。

### 使用 Lost Cities 建筑生成 FreeBuilding 城市

1. 安装匹配的 Lost Cities 版本。
2. 启动世界并执行：

```text
/fb import lostcities
```

3. 打开 `/fb gui`，确认 `lc_*` 预设已启用。
4. 调整自动城市概率，或在创建世界界面选择这些预设。
5. 探索新区块，FreeBuilding 和 Lost Cities 会按城市单元互斥生成。

### 创建全地图城市新世界

1. 从主菜单点击“创建新世界”。
2. 点击“自由建筑预设”。
3. 启用城市生成。
4. 设置城市覆盖率为 `100%`。
5. 选择要参与生成的预设。
6. 创建世界并探索尚未生成的陆地区块。

## 13. 已知限制

- 自动城市、乡村和 Lost Cities 互斥规则只作用于新生成区块。
- 海洋、高山和不适合整平的地形可能被跳过或产生边缘地形。
- 按需生成会直接修改方块，没有撤销功能。
- 出生点附近的区块可能在模组初始化前已经生成，因此不保证出生点一定出现自动城市。
- 乡村只检查中心位置的原版村庄；偏离中心的村庄仍可能造成局部重叠。
- Lost Cities 导入是代表性建筑转换，不是完整复刻 Lost Cities 的道路、多建筑组合、地下室和随机损坏系统。
- 跨 Minecraft 版本导入时，目标版本不存在的方块、状态或方块实体不能保证完整恢复。
- 使用 Lost Cities 时必须安装与 Minecraft 1.20.1 和当前 Forge 版本匹配的 Lost Cities 构建。

## 14. 构建

项目使用 Gradle 和 Java 17。Windows 下可以使用：

```bash
export JAVA_HOME="D:/MC/jdk/graalvm-community-openjdk-17.0.9+9.1"
./gradlew clean build --no-daemon
```

当前版本产物：

```text
build/libs/freebuilding-1.3.1+1.20.1.jar
```

许可证：MIT
