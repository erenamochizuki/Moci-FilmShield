# 验证记录

## 1.3.2：拆下不再回写原版护盾（2026-11-08）

现场问题：装配界面右侧舰船属性面板的「护盾角度」显示 360，而不是贴膜盾实际使用的弧度（1.3.1 之前配置成 0 时显示 0）。逐级取证（`[GN_ARC]` 诊断日志）确认：装配界面预览舰创建时 hullmod 副作用正常执行，贴膜盾把弧度写成 0.01；随后 `updateFromCurrentVariant()` 对预览舰调用一次 `advance(1.0E-4F)`，`FilmShieldController.advance` 的守卫 `if(!ship.isAlive()||ship.isHulk()||ship.wasRemoved()) dispose()` 命中（预览舰出生即 `alive=false`、`removed=true`，从未真正入场），`dispose()` 把原版护盾弧度写回 `originalArc`（船体的 360）。面板 `Math.round(arc)` 因此显示 360。

修复：`dispose()`／`detach` 只清理伤害修正器、EMP 修正、命中列表、归属登记与监听器，不再回写原版护盾的弧度、半径、隐藏渲染开关和颜色；`FilmShieldController` 相应删除 `originalArc/originalRadius/originalInner/originalRing/originalSkip` 五个字段。理由是船插在战斗中不会卸下、refit 每次改装配都会重建 `ShipAPI`，回写只可能命中已消失的舰或从未入场的预览舰。

契约测试：`tests/FilmShieldContractTest.java` 中四处「拆下后应还原」的断言改为「拆下不写回」（preserve 模式的半径/渲染、自定义弧只写一次、母子模块保留弧度、原版护盾的弧度/半径/渲染与颜色），其余断言不变，仍然全部通过。

已知副作用：运行时主动 `detach` 一艘仍在场的舰时，贴膜盾写过的原版护盾参数会留在舰上，需要调用方自行复原；库内没有这样的调用路径。

同一轮补回抽库重构时丢掉的两项运行时行为（逐行对照抽库前实现确认）：

- 原版护盾朝向：开启期间 `forceFacing(Misc.normalizeAngle(ship.getFacing() + 180f))`。原版护盾拦截锥在 `isWithinArc` 与碰撞检测里都有 `max(1, activeArc)` 下限，弧角再小也留着 1° 的缝，把它甩到船尾才是「开盾瞬间不会有漏出的攻击被原版护盾结算」的正确做法。
- 装甲冻结：`init()` 存一次 `saveArmor()`，`advance()` 里护盾开启时先 `retainArmor()` 再于末尾 `saveArmor()`。`AdvanceableListener.advance` 在碰撞与伤害之前执行，所以末尾快照必然是受损前的值，下一帧开头还原即等于「盾开着装甲不掉」。抽库后曾退化为「只回滚被吸收的命中」，观感上装甲会掉。

另外记录两条实机语义：

- 近战瞬时命中：`Moci_MeleeDamage.applyDamage` 把 `BeamAPI` 当伤害来源传给 `CombatEngine.applyDamage`，引擎建出的是 `isDps=false`、`dpsDuration=0` 的瞬时伤害。旧实现按「来源是 BeamAPI」判光束并乘 `dpsDuration`，结果恒为 0，贴膜盾对近战不吸收也不涨幅能。现按 `DamageAPI.isDps()` 判定，近战按全额伤害折算硬幅能。
- 颜色 uniform：`lowColor`／`highColor` 取不到（location 为 -1）时只打 `[GN_SHIELD] color uniforms unavailable (lowColor=…, highColor=…)` 警告，不参与 `baseUniformsValid`，避免一个「盾变黑」的问题升级成「整条着色器路径失效」。

发布包：`releases/Moci的贴膜盾前置-1.3.2.zip`。

## 1.3.1：渲染首帧着色器初始化（2026-11-08）

现场问题：GN 护盾开启后的头 1 秒左右没有着色器，走固定管线回退，整艘船被一块纯色实心色块覆盖；进战斗后等几秒再开盾正常，出问题后原地等几秒（即使暂停）也会自行恢复。定位到 `FilmShieldRenderer.ensureShaderInitializedFromRender`：`shaderRetryTimer += elapsed` 在比较之前执行，第一次 `initializeShader()` 也必须先攒满 `SHADER_RETRY_INTERVAL = 1f` 秒。修复为第一帧立即尝试初始化，失败后再回到 1 秒节流重试。

本次使用 Starsector 0.98 本地 API、JDK 17，运行 `build.ps1` 独立 Maven clean package 与 246 项游戏接口替身合约检查，全部通过（契约测试未改，本次无接口变化，API 主版本保持 1）；更新工作区 `jars/FilmShield.jar`（sha256 `0793594e…f97f6273`，59547 字节）。

库 JAR 与 1.3.0 的 `e6eeffdc…247a53ef`（59280 字节）逐条目比对：25 个条目中新增 0、删除 0、变化 5——`FilmShieldRenderer.class`（真实逻辑变化）、`FilmShieldRenderer$1.class` 与 `FilmShieldRenderer$ShieldRenderData.class`（指令完全相同，仅行号表平移）、`pom.properties`、`pom.xml`。全部 25 个 class 的 `javap -public` 逐行对比无差异，类清单一致，二进制兼容，Moci 与维克多无需重编。

游戏内取证：`starsector.log` 中 `[GN_SHIELD] shader ready (first render frame)` 在每场战斗登场时各出现一次，均紧跟在 `[MOCI-BUS] 接管战斗` 之后，说明首帧初始化已生效。

已知未修：渲染器的顶点缓冲、平面法线贴图和 512×512 法线合成纹理及帧缓冲仍按场创建；引擎不调用图层插件的 `cleanup`，这些资源每场战斗遗留一份（合成纹理约 0.75 MB）。抽离前的 Moci 实现同样如此，本次未改动。

发布包：`releases/Moci的贴膜盾前置-1.3.1.zip`。

## 1.3.0：关闭贴膜盾兼容开关与抽离复查修复（2026-11-05）

本次使用 Starsector 0.98 本地 API、JDK 17，运行 `build.ps1` 独立 Maven clean package 与 246 项游戏接口替身合约检查，全部通过；更新工作区 `jars/FilmShield.jar`。Moci 主工程使用更新后的公共源码 Maven 编译通过；Moci 与维克多的源码和 JAR 未修改。API 主版本保持 1，新增接口均为增量。

新增覆盖：开关在船插列表前后均生效；不考虑开关的原始查询；关闭后船插回调跳过，原版盾角、半径、颜色和绘制开关不变；带 ID 的 attach 返回 null；无 ID 的旧式 attach 返回不起作用的控制器（无监听器、不登记、不能开启，不产生幅能、伤害、EMP 与装甲效果）；直接构造控制器与 claim 不生效；两种 registerVisual 均被忽略；无原版护盾的船使用原版模式 profile 时不报错；母舰开关覆盖全部模块，包括模块自有的参与者；attachModules 跳过关闭的模块；只关闭单个模块时其他模块照常继承；被 suppressed 的开关不生效；开关从不出现在装配列表且始终可装；母舰自引用不死循环；无全局设置时可用。另检查 hull_mods.csv 两行的列数、脚本类、图标存在性，以及开关行全隐藏（hidden 与 hiddenEverywhere 为 true，unlocked 为 false）与标签。原有 182 项继续通过。

抽离复查修复新增 13 项：原版模式的 profile 遇到没有原版护盾的船时不抛异常，返回不起作用的控制器（不登记、无监听器、无自定义数据、不能开启、不吸收命中、不加幅能、不改舰船属性），只记录一条带 profile ID 的警告；直接构造同样不起作用；之后 `EXTERNAL` 接入方仍可正常接管和卸载；装有关闭开关的船静默跳过，不产生该警告。着色器程序进程内共享与停用武器不参与遮罩两项依赖 GL 和战斗引擎，替身测试无法覆盖，需游戏内确认。

已知未修：渲染器的顶点缓冲、平面法线贴图和 512×512 法线合成纹理及帧缓冲仍按场创建；引擎不调用图层插件的 `cleanup`，这些资源每场战斗遗留一份（合成纹理约 0.75 MB）。抽离前的 Moci 实现同样如此，本次未改动。

发布包：`releases/Moci的贴膜盾前置-1.3.0.zip`。未进行游戏内装配界面、战斗、空间站模块和其他 mod 同场验证。游戏同步状态以任务最终回复为准。

## 1.2.0：原版盾角可选配置（2026-10-26）

本次使用 Starsector 0.98 本地 API、JDK 17，运行 `build.ps1` 独立 Maven clean package 与 182 项游戏接口替身合约检查，全部通过；更新工作区 `jars/FilmShield.jar`。API 主版本保持 1，旧 profile 默认继续将原版盾角设为 0 度。本次未修改或重建 Moci/维克多本体。

新增覆盖：保留盾角模式在接入、advance、卸载时不写盾角，并保留第三方后续修改；0、0.1、90、360 度自定义值及原角恢复；负值、越界、NaN、正负无穷、Float.MAX_VALUE 拒绝；两种控制模式、重复 attach、builder 策略调用顺序、母舰/模块继承与各自原角恢复、无盾外控；非零盾角下 `shieldHit=true` 不重复添加贴膜幅能/伤害修正，舰体命中仍使用贴膜结算。原有船插优先选择、伤害、清理等检查继续通过。

发布包：`releases/Moci的贴膜盾前置-1.2.0.zip`，包含本次 JAR、版本声明、资源与更新后的接入文档。未进行游戏内碰撞、其他 mod 联调、GLSL 驱动或旧存档验证；非零盾角的真实碰撞及原版/贴膜命中分流仍按接入说明中的场景实测。游戏同步状态以任务最终回复为准。

## 1.1.0 历史验证

2026-10-24；Starsector 0.98 本地 API；JDK 17。

1.1.0 新增船插列表优先选择及随包接入说明。独立构建与 111 项合约检查通过，Moci 与维克多工作区完整构建通过。未启动或同步游戏。

已通过：

- 独立 Maven clean package，生成 FilmShield.jar。
- 111 项游戏接口替身合约检查：无 Moci/BoxUtil/MagicLib/LunaLib 的运行 classpath；配置校验、重复/冲突安装、无盾外控、原版开关、瞬时近战、DPS/强制硬幅能/显式软幅能、装甲恢复与旁路、母舰结算/模块清理、零幅能、破片兼容、暂停、移除与 EMP 清理；另含三家全部 6 种排列与逆序回调、标准船插回调、赢家关闭不接替、suppressed 跳过、未安装/落选船插无副作用、纯视觉赢家与模块自身船插优先。
- Moci 全量 Maven 构建，更新工作区 MociShipPack.jar。该 JAR 由整份当前工作区源码构建，包含任务开始前已有的其他未提交修改；本次文件清单仅列本任务改动。
- RS_Moci_GNShield 替代示例使用已打包 FilmShield.jar + 游戏 API 独立编译，通过；未使用 Moci JAR。
- 前置 JAR 不含 data.* 本体代码；Moci JAR 不含 org/moci/filmshield.* 重复公共类；jdeps 未检出对 Moci、BoxUtil、LunaLib、MagicLib 的引用。
- mod_info/settings JSON、通用船插 CSV 列数和类引用、图标/纹理资源存在性检查通过。
- 维克多显式依赖本前置并直接注册法线表；独立 Maven clean package，通过，更新工作区 VectorIndustries.jar。

未进行：游戏内运行、GLSL 驱动编译/截图、旧存档加载、红条实机迁移。README 列有对应实机验收步骤。文件复制到游戏目录不代表已通过这些实机验证。

兼容界限：保留 Moci 船插 ID、当前仓库调用与数值 profile；不宣称外部旧 JAR 对旧渲染器继承结构的完整二进制兼容。第三方原生 shieldHit 回调行为需单独联调。
