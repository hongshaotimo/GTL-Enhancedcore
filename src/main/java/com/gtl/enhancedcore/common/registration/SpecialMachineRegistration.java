package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gtl.enhancedcore.common.data.machines.multiblock.GTLStructures;
import com.gtl.enhancedcore.common.data.machines.multiblock.FieldMachineStructures;
import com.gtl.enhancedcore.client.renderer.machine.MachineFieldRenderer;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.Map;
import java.util.function.Supplier;
import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import net.minecraft.resources.ResourceLocation;

import com.gtl.enhancedcore.common.machine.*;
import com.gtl.enhancedcore.common.recipe.MachineRecipeModifiers;
import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.*;

/** Machine builders, invoked only during the GTCEu registration event. */
public final class SpecialMachineRegistration {
    private SpecialMachineRegistration() {}

    public static MultiblockMachineDefinition registerWeatherAnchor() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("weather_anchor", holder -> new WeatherAnchorMachine(holder))
                .langValue("时相凝固气象锚点")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock((Supplier) GTBlocks.HIGH_POWER_CASING)
                .recipeTypes(GTLEnhancedcoreRecipeTypes.ANCHOR_MODES)
                .pattern(FieldMachineStructures::weather)
                .tooltipBuilder(MachineTooltips.create("weather_anchor", Map.of(
                        "tooltip.gtl_enhancedcore.weather_anchor.power",
                        new Object[]{GTValues.VN[WeatherAnchorMachine.ANCHOR_TIER] + " " + WeatherAnchorMachine.ANCHOR_AMPS + "A"}),
                        GTLEnhancedcoreRecipeTypes.ANCHOR_MODES))
                .renderer(() -> new MachineFieldRenderer(false))
                .hasTESR(true)
                .register();
    }

    public static MultiblockMachineDefinition registerCausalityTerminal() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("causality_terminal", holder -> new CausalityTerminalMachine(holder))
                .langValue("因果重构终端")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock((Supplier) GTBlocks.HIGH_POWER_CASING)
                .recipeTypes(GTLEnhancedcoreRecipeTypes.CAUSALITY_TERMINAL)
                .pattern(FieldMachineStructures::causality)
                .tooltipBuilder(MachineTooltips.create("causality_terminal", Map.of(
                        "tooltip.gtl_enhancedcore.causality_terminal.power", new Object[]{GTValues.VN[GTValues.MAX] + " 163840A"})))
                .renderer(() -> new MachineFieldRenderer(true))
                .hasTESR(true)
                .register();
    }

    public static MultiblockMachineDefinition registerInfinitySingularityCompressor() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("infinity_singularity_compressor", holder -> new WorkableElectricMultiblockMachine(holder))
                .langValue("无限奇点压缩器")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("ae2:mysterious_cube"))
                .recipeTypes(GTLEnhancedcoreRecipeTypes.INFINITY_SINGULARITY)
                .pattern(definition -> GTLStructures.infinitySingularityCompressor(definition))
                .tooltipBuilder(MachineTooltips.create("infinity_singularity", Map.of(
                        "tooltip.gtl_enhancedcore.infinity_singularity.power", new Object[]{GTValues.VN[GTValues.UIV] + " 2650A"},
                        "tooltip.gtl_enhancedcore.infinity_singularity.duration", new Object[]{1200}),
                        GTLEnhancedcoreRecipeTypes.INFINITY_SINGULARITY))
                .recipeModifier((machine, recipe, params, result) -> recipe, false)
                .renderer(() -> new com.gtl.enhancedcore.client.renderer.machine.NeutronStarInfinityRenderer())
                .hasTESR(true)
                .register();
    }

    public static MultiblockMachineDefinition registerNeutronControlFactory() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("neutron_control_factory", holder -> new WorkableElectricMultiblockMachine(holder))
                .langValue("中子控制工厂")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtlcore:process_machine_casing"))
                .recipeTypes(GTLRecipeTypes.NEUTRON_ACTIVATOR_RECIPES)
                .pattern(definition -> GTLStructures.neutronControlFactory(definition))
                .recipeModifier(MachineRecipeModifiers::neutronFactory, false)
                .tooltipBuilder(MachineTooltips.create("neutron_control_factory", Map.of(
                        "tooltip.gtl_enhancedcore.neutron_control_factory.parallel",
                        new Object[]{MachineRecipeModifiers.NEUTRON_FACTORY_PARALLEL}), GTLRecipeTypes.NEUTRON_ACTIVATOR_RECIPES))
                .workableCasingRenderer(new ResourceLocation("gtlcore", "block/casings/process_machine_casing"),
                        GTLEnhancedcore.id("block/multiblock/neutron_control_factory"))
                .register();
    }

    // ==================== 铂系精炼矩阵（EV） ====================
    /**
     * 铂系精炼矩阵：EV 阶段多方块，bxjljz.schem 专用结构（29×19×33，控制器在正面 9×4 仓室面板正中）。
     * <p>
     * 材质（用户指定）：主方块与背面/侧面/顶底 = {@code gtceu:solid_machine_casing}
     * （GTCEu 材质路径 {@code block/casings/solid/machine_casing_solid_steel}，与 GTMachines.HULL[HV] 同款）；
     * 正面 = 复用本模组「中子控制工厂」同款 overlay {@code block/multiblock/neutron_control_factory}
     * （四态：普通/运行中/暂停/发光，规则 16 双态材质）。
     * <p>
     * 仓室（用户指定）：能源仓、维护仓、并行控制仓；不支持激光靶仓。
     * recipeModifiers 必须显式包含 {@link GTRecipeModifiers#PARALLEL_HATCH}
     * —— 仅声明 {@code PartAbility.PARALLEL_HATCH} 只让结构接受并行仓，
     * 并行实际结算依赖此修饰器（已用 javap 实证 RecipeLogic 内不读取 IParallelHatch）。
     */
    public static MultiblockMachineDefinition registerPlatinumRefiningMatrix() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("platinum_refining_matrix", holder -> new WorkableElectricMultiblockMachine(holder))
                .langValue("铂系精炼矩阵")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtceu:solid_machine_casing"))
                .recipeTypes(GTLEnhancedcoreRecipeTypes.PLATINUM_REFINING)
                .recipeModifiers(new RecipeModifier[]{
                        GTLRecipeModifiers.GCYM_REDUCTION,
                        GTRecipeModifiers.PARALLEL_HATCH,
                        GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK)
                })
                .pattern(definition -> GTLStructures.platinumRefiningMatrix(definition))
                .tooltipBuilder(MachineTooltips.create("platinum_refining_matrix", GTLEnhancedcoreRecipeTypes.PLATINUM_REFINING))
                .workableCasingRenderer(
                        GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTLEnhancedcore.id("block/multiblock/neutron_control_factory"))
                .register();
    }

    // ==================== 虚空约束采矿场（ZPM） ====================
    /**
     * 虚空约束采矿场：ZPM 阶段多方块，xkckj.schem 专用结构（紧致 101×63×104，控制器在正面 z=0 边界）。
     * <p>
     * 两个机器模式（用户指定，与 gtlcore 原版大型虚空采矿厂一致）：
     * {@code gtceu:large_void_miner}（精准矿石模式，投矿脉精华采指定矿脉）与
     * {@code gtceu:random_ore}（随机矿石模式，10KB 钻井液随机采全部矿石）。
     * 两个配方类型均已用 javap 实证由 {@code GTRecipeTypes.register(name, "multiblock", ...)} 注册，
     * 命名空间为 {@code gtceu}。
     * <p>
     * 材质（用户指定）：主方块与侧背底 = {@code gtceu:solid_machine_casing}
     * （材质路径 {@code block/casings/solid/machine_casing_solid_steel}）；
     * 正面复用 GTCEu 原版矿机 overlay {@code block/multiblock/large_miner}（规则 16：优先调用原版同类型材质）。
     * <p>
     * 仓室（用户指定 + 产出/输入必需）：能源仓、维护仓、并行控制仓，
     * 另加物品输入仓（矿脉精华）、流体输入仓（钻井液）与物品输出仓（矿石产出）。
     * 不支持激光靶仓。并行同样必须挂 {@link GTRecipeModifiers#PARALLEL_HATCH} 才会生效。
     */
    public static MultiblockMachineDefinition registerVoidConstrainedMiningField() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("void_constrained_mining_field", holder -> new WorkableElectricMultiblockMachine(holder))
                .langValue("虚空约束采矿场")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtceu:solid_machine_casing"))
                .recipeTypes(new GTRecipeType[]{
                        GTLRecipeTypes.LARGE_VOID_MINER_RECIPES,
                        GTLRecipeTypes.RANDOM_ORE_RECIPES
                })
                .recipeModifiers(new RecipeModifier[]{
                        GTLRecipeModifiers.GCYM_REDUCTION,
                        GTRecipeModifiers.PARALLEL_HATCH,
                        GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK)
                })
                .pattern(definition -> GTLStructures.voidConstrainedMiningField(definition))
                .tooltipBuilder(MachineTooltips.create("void_constrained_mining_field",
                        GTLRecipeTypes.LARGE_VOID_MINER_RECIPES, GTLRecipeTypes.RANDOM_ORE_RECIPES))
                .workableCasingRenderer(
                        GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTCEu.id("block/multiblock/large_miner"))
                .register();
    }

    // ==================== 龙式场约束增殖核心（UEV） ====================
    /**
     * 龙式场约束增殖核心：UEV 阶段多方块，GT_Multiblock_UEV_DragonClaw.schem 专用结构（75×73×78）。
     * <p>
     * 配方类型：唯一自建类型 {@code gtl_enhancedcore:exotic_proliferation}「异种物质增殖」，
     * 由 {@code ExoticProliferationRecipeLoader} 输出唯一配方
     * （512 黑曜石 + 52mB 龙息 + 1 转换模拟卡[不消耗] → 512 龙尘）。
     * <p>
     * 材质（用户指定）：主方块与外壳 = {@code gtceu:uhv_machine_casing}
     * （材质路径 {@code block/casings/voltage/uhv/side}）；
     * 正面复用本模组气象锚点同款 overlay {@code block/multiblock/weather_anchor}。
     * <p>
     * 控制器位于主方块周围的整面 UHV 仓室面板正中（用户指定该面全部改为仓室位）。
     * 该面板不在包围盒边界上——GTCEu 允许控制器在结构内部（BlockPattern 以控制器为锚点），
     * SchemTool 生成器已相应放宽。
     * <p>
     * 仓室（用户指定）：物品输入/输出仓、维护仓、并行控制仓、能源仓、激光靶仓。
     * 并行必须挂 {@link GTRecipeModifiers#PARALLEL_HATCH} 才会生效。
     */
    public static MultiblockMachineDefinition registerDragonFieldProliferationCore() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("dragon_field_proliferation_core", holder -> new WorkableElectricMultiblockMachine(holder))
                .langValue("龙式场约束增殖核心")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtceu:uhv_machine_casing"))
                .recipeTypes(GTLEnhancedcoreRecipeTypes.EXOTIC_PROLIFERATION)
                .recipeModifiers(new RecipeModifier[]{
                        GTLRecipeModifiers.GCYM_REDUCTION,
                        GTRecipeModifiers.PARALLEL_HATCH,
                        GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK)
                })
                .pattern(definition -> GTLStructures.dragonFieldProliferationCore(definition))
                .tooltipBuilder(MachineTooltips.create("dragon_field_proliferation_core", GTLEnhancedcoreRecipeTypes.EXOTIC_PROLIFERATION))
                .workableCasingRenderer(
                        GTCEu.id("block/casings/voltage/uhv/side"),
                        GTLEnhancedcore.id("block/multiblock/weather_anchor"))
                .register();
    }

    // ==================== 超构化学扭曲仪（UEV） ====================
    /**
     * 超构化学扭曲仪：UEV 阶段多方块，是 gtlcore「深层化学扭曲仪」({@code gtceu:chemical_distort}) 的上位机器。
     * <p>
     * 配方类型（用户指定）：{@code gtceu:distort}（javap 实证 = {@code GTLRecipeTypes.DISTORT_RECIPES}，
     * 注册名 "distort"，形状 ≤9 物品入/出 + ≤9 流体入/出，数据键 {@code ebf_temp} 由线圈温度把关）。
     * <p>
     * 材质（用户指定）：主方块与仓室成型方块 = {@code gtlcore:iridium_casing}
     * （材质路径 {@code gtlcore:block/casings/iridium_casing}）。
     * 正面：用户给的 {@code gtladditions:nexus_satellite_factory_mk1} **在本包内不存在任何贴图资源**
     * （已逐 jar 核验：gtladditions 无该 blockstate/model/texture；该 id 是机器方块，
     * 其自身 {@code workableCasingRenderer} 用的就是 {@code gtceu:block/multiblock/fusion_reactor}）。
     * 故取该机器实际使用的正面 overlay {@code gtceu:block/multiblock/fusion_reactor}
     * （2026-09-26 修正：先前误用 {@code block/multiblock/gcym/large_chemical_reactor}，
     * 该路径**不存在**，导致主方块没有主面材质）。
     * <p>
     * 仓室（用户指定）：维护仓、并行控制仓、激光靶仓、输入输出仓、Ω-天球分歧引擎
     * （{@code GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER()}，与大型熔炉同款写法）。
     * <p>
     * 机制：控制器先验证温度，将线圈、并行仓和天球通道合并后交给原生并行结算，再完美超频。
     */
    public static MultiblockMachineDefinition registerHyperstructuralChemicalDistorter() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("hyperstructural_chemical_distorter", HyperstructuralChemicalDistorterMachine::new)
                .langValue("超构化学扭曲仪")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtlcore:iridium_casing"))
                .recipeTypes(GTLRecipeTypes.DISTORT_RECIPES)
                .recipeModifiers(new RecipeModifier[]{
                        HyperstructuralChemicalDistorterMachine.PARALLEL,
                        GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK)
                })
                .pattern(definition -> GTLStructures.hyperstructuralChemicalDistorter(definition))
                .tooltipBuilder(MachineTooltips.create("hyperstructural_chemical_distorter", GTLRecipeTypes.DISTORT_RECIPES))
                .workableCasingRenderer(
                        new ResourceLocation("gtlcore", "block/casings/iridium_casing"),
                        GTCEu.id("block/multiblock/fusion_reactor"))
                .register();
    }

    // ==================== 恒星约束聚变堆（UIV） ====================
    /**
     * 恒星约束聚变堆：UIV 阶段多方块，核聚变反应堆上位机器。
     * <p>
     * 配方类型（用户指定）：{@code gtceu:fusion_reactor}（javap 实证 = {@code GTRecipeTypes.FUSION_RECIPES}，
     * 注册名 "fusion_reactor"，0 物品入/出 + 2 流体入 + 1 流体出，数据键 {@code eu_to_start}）。
     * <p>
     * <b>取电与并行对齐 {@code gtladditions:forge_of_the_antichrist}</b>（用户 2026-10-05 指定）：
     * 控制器继承 {@link com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessWorkableElectricMultipleRecipesMachine}，
     * 由 {@code SelfWirelessNetworkHandler} 直接从放置者所属无线电网扣除 EU，不挂任何配方修饰器；
     * 并行上限取 GTLCore {@code getMaxParallel() = Integer.MAX_VALUE}，跨配方线程为多配方逻辑默认值（128 + Ω），
     * 因此可同时加工多个不同聚变配方，实际批量只受原料、输出空间与电网电力限制。
     * 不实现 forge 的连续运行预热/输出倍率/EU 折扣；正因不再继承 {@code FusionReactorMachine}，
     * 原生 {@code eu_to_start} 启动热量也不再检查。
     * <p>
     * <b>固定 1 秒</b>：单批时长取基类 {@code limitedDuration} 默认值 20 tick，控制器强制回写该值并关闭时长配置器；
     * 不启用会延长周期的普通批处理。
     * <p>
     * 材质（用户指定）：主方块与仓室成型 = {@code gtlcore:iridium_casing}；
     * 正面同样因 {@code gtladditions:nexus_satellite_factory_mk1} 无贴图资源，
     * 改用本包真实存在的聚变族正面 overlay {@code gtceu:block/multiblock/fusion_reactor}。
     * <p>
     * 仓室：维护仓、并行控制仓、输入输出仓、Ω-天球分歧引擎；不接受能源仓或激光靶仓。
     */
    public static MultiblockMachineDefinition registerStellarConfinementFusionReactor() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("stellar_confinement_fusion_reactor", StellarConfinementFusionReactorMachine::new)
                .langValue("恒星约束聚变堆")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtlcore:iridium_casing"))
                .recipeTypes(com.gregtechceu.gtceu.common.data.GTRecipeTypes.FUSION_RECIPES)
                .pattern(definition -> GTLStructures.stellarConfinementFusionReactor(definition))
                .tooltipBuilder(MachineTooltips.create("stellar_confinement_fusion_reactor",
                        com.gregtechceu.gtceu.common.data.GTRecipeTypes.FUSION_RECIPES))
                .workableCasingRenderer(
                        new ResourceLocation("gtlcore", "block/casings/iridium_casing"),
                        GTCEu.id("block/multiblock/fusion_reactor"))
                .register();
    }
}
