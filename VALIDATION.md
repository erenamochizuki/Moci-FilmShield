# 验证记录

2026-10-24；Starsector 0.98 本地 API；JDK 17。

改名并建立独立仓库后，重新执行带 `-LibDirectory` 的独立构建与 45 项合约检查，以及 Moci 主工程 Maven compile，全部通过。

已通过：

- 独立 Maven clean package，生成 FilmShield.jar。
- 45 项游戏接口替身合约检查：无 Moci/BoxUtil/MagicLib/LunaLib 的运行 classpath；配置校验、重复/冲突安装、无盾外控、原版开关、瞬时近战、DPS/强制硬幅能/显式软幅能、装甲恢复与旁路、母舰结算/模块清理、零幅能、破片兼容、暂停、移除与 EMP 清理。
- Moci 全量 Maven 构建，更新工作区 MociShipPack.jar。该 JAR 由整份当前工作区源码构建，包含任务开始前已有的其他未提交修改；本次文件清单仅列本任务改动。
- RS_Moci_GNShield 替代示例使用已打包 FilmShield.jar + 游戏 API 独立编译，通过；未使用 Moci JAR。
- 前置 JAR 不含 data.* 本体代码；Moci JAR 不含 org/moci/filmshield.* 重复公共类；jdeps 未检出对 Moci、BoxUtil、LunaLib、MagicLib 的引用。
- mod_info/settings JSON、通用船插 CSV 列数和类引用、图标/纹理资源存在性检查通过。

未进行：游戏内运行、GLSL 驱动编译/截图、旧存档加载、红条实机迁移。README 列有对应实机验收步骤。文件复制到游戏目录不代表已通过这些实机验证。

兼容界限：保留 Moci 船插 ID、当前仓库调用与数值 profile；不宣称外部旧 JAR 对旧渲染器继承结构的完整二进制兼容。第三方原生 shieldHit 回调行为需单独联调。
