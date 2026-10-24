# Moci的贴膜盾前置

源码仓库：[erenamochizuki/Moci-FilmShield](https://github.com/erenamochizuki/Moci-FilmShield)。

独立 mod ID：`moci_filmshield`；版本：1.1.0；API 主版本：1。目标环境为 **Starsector 0.98 / Java 17**。

玩家与接入者请先阅读本目录的 [接入与兼容说明.md](接入与兼容说明.md)，该文件也随游戏用发布包提供，包含多个贴膜盾同时安装时的优先规则。

这是从 Moci GN 护盾提取的公共运行库，包含伤害结算、模块跟随、状态 HUD、命中特效、GLSL 着色器、法线合成与降级渲染。只依赖游戏、LazyLib 和 GraphicsLib；不需要安装 Moci、BoxUtil、MagicLib 或 LunaLib。

## 安装与构建

玩家：将发布 ZIP 中的「Moci的贴膜盾前置」目录放进游戏 mods 并启用。mod ID 仍为 `moci_filmshield`，改名不会改变依赖标识；不要同时安装两个同 ID 的副本。使用本次 Moci 更新时必须同时安装本前置。不要只更新 Moci 的 JAR 而漏掉 mod_info.json 和前置。

开发者：本目录是独立源码工程。运行 `./build.ps1` 会独立编译、执行合约测试并生成 `jars/FilmShield.jar`，之后才是可加载的 mod 目录。源码仓库不包含游戏/第三方依赖、编译产物或发布 ZIP。构建需要 JDK 17、Maven，以及合法取得的 `starfarer.api.jar`、`lwjgl.jar`、`lwjgl_util.jar`、`LazyLib.jar`、`Graphics.jar`、`log4j-1.2.9.jar`、`json.jar`。

脚本默认使用本工作区的 Java/Maven 和上级 `lib/`，独立克隆后可指定自己的工具与依赖位置：

```powershell
./build.ps1 -JavaHome 'C:/Java/jdk-17' -Maven 'C:/Maven/bin/mvn.cmd' -LibDirectory 'D:/StarsectorDev/lib'
```

只使用 Maven 时，可运行 `mvn "-Dstarsector.lib=D:/StarsectorDev/lib" clean package`；这不执行合约测试，也不复制 JAR 到 `jars/`。

修改公共源码后，先构建本前置，再构建 Moci。主工程为接口编译加入公共源码路径，但明确从 MociShipPack.jar 排除 `org/moci/filmshield/**`；实际运行始终使用独立前置 JAR，不能把公共类再打进其他 mod。主工程编译通过不代表已更新前置 JAR。

运行 `./package.ps1` 构建并生成 `releases/Moci的贴膜盾前置-1.1.0.zip`；已完成构建时可加 `-SkipBuild`。游戏用发布包包含 mod_info.json、jars、data、graphics，以及 README、接入与兼容说明、验证记录和接入示例。src、tests 随源码仓库提供。

## 接入方式

依赖声明：

```json
{"id":"moci_filmshield","name":"Moci的贴膜盾前置","version":"1.1.0"}
```

1. **只写舰船数据**：将 `filmshield_surface` 加入 builtInMods。该通用船插隐藏于普通安装列表，不自动加入任何商店或船只；跟随原版护盾开关，要求有原版护盾。
2. **完整伤害机制**：继承 `FilmShieldHullmod`，或让自己的船插实现 `FilmShieldProvider` 并调用 `FilmShield.attach(ship, profile, hullmodId)`。也可用 `profile.hullmodId(...)` 绑定旧 attach 入口。未选中的船插返回 null，调用方必须判断；不要对它启动额外监听器或 UI。原版模式跟随原版护盾；`EXTERNAL` 模式适合没有原版护盾的船，由系统或 AI 调用 `setEnabled`，默认关闭。两种模式都在过载、排幅、相位和死亡时停用。
3. **只复用视觉**：船插实现 `FilmShieldProvider`，先用 `isSelected` 判断，再创建自己的独立护盾池/结算，并用 `registerVisual(ship, visual, hullmodId)` 注册 `ShieldVisual`；不安装公共伤害监听器。按需提供命中列表、颜色、压力比例、半径、中心和 HUD 文本，并调用 `ensureCombatServices`。

配置应只创建一次；profile.id、modifierId 使用自己 mod 的前缀。同一艘船按 `ShipVariantAPI.getHullMods()` 的顺序，选第一个未被 suppressed 且实现 `FilmShieldProvider` 的船插。它的开关关闭或过载时，不会改用后面的贴膜盾。无本地参与者的模块可继承母舰选择；模块自己的参与者优先于继承。重复调用选中配置复用控制器；未选中者安静跳过。没有绑定船插 ID 的旧式直接 attach/registerVisual 无法推断调用方，仍采用严格的单一所有者检查，冲突会报错；接入船插应迁移至以上带 ID 的入口。ShipAPI 引用仅保存在舰船/战斗生命周期内。

## 可扩展点与约束

- `ShieldProfile`：控制模式、幅能系数、旧版额外破片系数、模块跟随、状态文案、低/高压力颜色、伤害 modifier ID。
- `FilmShieldProvider` / `FilmShield.getSelectedHullmodId` / `FilmShield.isSelected`：识别参与船插并检查优先级；自有伤害/池/UI 必须先检查再创建。选择与装配列表一起在舰船创建时确定；战斗中动态修改列表应重建舰船，不支持无缝热切换已有监听器。
- `ShieldHooks`：命中是否接管、最终幅能调整、吸收后的通知；允许其他 mod 接入过滤和附加效果。回调在战斗线程执行，不得抛异常、操作 GL 或递归触发 attach。
- `ShieldVisual` / `ShieldHit`：独立于伤害实现的展示协议。以后增加默认方法保持源码接入兼容；不能重解释现有方法语义，破坏性变更提升 API 主版本。
- `FilmShield.registerNormalTextureDataCSV(path)`：按 GraphicsLib texture CSV 格式注册法线备用表，仅取 map=normal；素材 ID 和路径由内容 mod 自己命名。
- `FilmShield.detach(ship)`：清理伤害/EMP修正、监听器和登记，恢复原版护盾半径/角度/颜色/绘制开关，并清理同 profile 的继承模块。外部纯视觉实现使用 unregisterVisual 并传回原适配器实例。
- 原版模式的实际幅能 = 伤害类型/舰体及攻击方修正 × 舰体护盾效率 × profile.fluxPerDamage × 护盾属性修正。外控模式直接使用 profile.fluxPerDamage，不读取舰体护盾效率。
- 延续贴膜盾原来的非零残余伤害处理（倍率 0.0001）和已吸收命中的装甲恢复；它不是引擎原生护盾碰撞面，不承诺触发其他 mod 的 shieldHit=true 特殊逻辑。若第三方自有护盾有特殊结算，优先选视觉接口。
- 护盾溢出、特殊 EMP/碰撞和其他伤害监听器的先后顺序仍需游戏内联调；编译与替身测试不证明所有 mod 组合兼容。

## Moci 迁移边界

Moci 保留船插 ID `Moci_GNShield`、原有 moci_ms 安装限制、专属文本/数值和旧 Java 入口。GN 护盾脚本、HUD、渲染器成为兼容入口；GN 力场防御的独立护盾池/专属 UI 仍在 Moci，通过 ShieldVisual 共用前置渲染器。旧的近战瞬时伤害、硬幅能修复保留在新核心。

本家的 GN 护盾与 GN 力场都参与列表优先选择。维克多重工当前沿用本家的 `Moci_GNShield`，不是第二套护盾；其插件已直接调用前置注册自己的法线表，并显式声明前置依赖。如果以后独立出维克多船插，按上述接口接入即可参与同一选择。

Moci 的 0.25 额外破片系数和旧 modifier ID 由本体 profile 指定；公共默认不重复附加破片折减。Moci LunaLib 性能开关继续控制 Moci 的视觉；其他接入方默认读取 data/config/filmshield.json。所有性能开关只控制渲染，不能关闭伤害防御。

Moci 的旧贴图仍被灵能辉光等内容引用，保留这些资源与旧设置键；前置使用自己命名的资源，不依赖这些旧文件。原有 Moci GN 着色器文件保留作旧内容兼容，但新共享渲染器只读 data/shaders/filmshield.*。

兼容范围：本仓库调用点已经重新编译，旧船插 ID 和数据引用保留。不能承诺其他 mod 已编译代码对旧渲染器继承关系、受保护方法或嵌套类型的完整二进制兼容；这类调用应改为公共 API 并重编译。存档加载和旧战斗场景尚需实机验证。

## 红条迁移

迁移示例按 Red Stripe 2.76 fix9 中的 RS_Moci_GNShield、RS_Moci_GNShield_Script、RS_Moci_GNShieldUI 和 RS_Moci_GNShieldRendering_Shader 接口编写，其 mod ID 为 red_stripe。红条源码和资源不随本仓库分发。

`examples/RedStripeIntegration.java.txt` 是可编译的 RS_Moci_GNShield 替代实现：保留类名与原有原版护盾控制、moci_ms 限制、额外破片系数。复制到红条源码的对应路径后重编译，不需安装 Moci。

迁移时还需：

- 红条 mod_info 添加前置依赖，编译 classpath 加入 FilmShield.jar。
- 保留红条 hull_mods.csv 和船只里的原船插 ID，不给它们额外添加 filmshield_surface。
- 原 GN 脚本/UI/渲染器不再由这只船插启动；查找其余直接调用，改为 FilmShield.find、ensureCombatServices 等公共入口，避免新旧监听器和渲染器同时启动。
- 若红条独立 GN 力场也使用旧渲染器，用 ShieldVisual 适配该力场；不要简单删除仍被其他功能引用的旧类/贴图。
- 颜色和法线表可由红条自己配置，不再复制公共 GLSL/渲染源码。独立外控例子见 examples/ExternalControl.java.txt。

本次没有修改或发布红条参考包，也没有宣称红条完成实机迁移。

## 验证与实机验收

自动检查见 VALIDATION.md。合约测试只用游戏接口替身，运行 classpath 排除 Moci/BoxUtil/MagicLib/LunaLib，覆盖独立加载、开关、瞬时近战、光束、模块、冲突和清理。

实机至少验收：仅前置+依赖加载；Moci GN 与 GN 力场同场；不同颜色的第三方护盾同场；盾开关/排幅/过载/模块脱离/死亡；近战、连续光束、导弹和爆炸；装配界面与连续多场战斗；无着色器/无法线模式；战术地图、HUD隐藏、各缩放比例；旧存档载入。尚未运行这些实机项目。
