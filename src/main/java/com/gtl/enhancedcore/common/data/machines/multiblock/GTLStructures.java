package com.gtl.enhancedcore.common.data.machines.multiblock;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtladd.gtladditions.api.machine.GTLAddPartAbility;
import com.gtl.enhancedcore.common.structure.DistorterCoils;
import com.gtl.enhancedcore.common.data.machines.multiblock.BxjljzStructure.BxjljzStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.VoidConstrainedMiningFieldStructure.VoidConstrainedMiningFieldStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.DragonFieldProliferationCoreStructure.DragonFieldProliferationCoreStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.HyperstructuralChemicalDistorterStructure.HyperstructuralChemicalDistorterStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.StellarConfinementFusionReactorStructure.StellarConfinementFusionReactorStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.SteamPlatformStructure.SteamPlatformStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.LVWirelessChargerStructure.LVWirelessChargerStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.MVWirelessChargerStructure.MVWirelessChargerStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.OrePlantStructure.OrePlantStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.IvsijiantaoStructure.IvsijiantaoStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.ZzkzgcStructure.ZzkzgcStructure;
import com.gtl.enhancedcore.common.data.machines.multiblock.QdysStructure.QdysStructure;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/**
 * GTL-Enhancedcore 各多方块机器结构（2026-07-31 逐台设计，禁止套模板）。
 * 每台机器的轮廓按功能设计：厂房带烟囱 / 环形约束室 / 精馏塔 / 双轨道环 /
 * 托卡马克 / 时相尖塔 / 太极殿 / 山墙厂房 / 避雷塔。
 * 规范：只用整方块；材料按电压阶段；'.' 为空气（不填）；控制器 C 在正面可达位置；
 * 'A' 为功能位（机器外壳 + 仓口）。
 */
public final class GTLStructures {
    private GTLStructures() {}

    private static TraceabilityPredicate block(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Objects.requireNonNull(location, "Invalid block id: " + id);
        Block b = ForgeRegistries.BLOCKS.getValue(location);
        if (b == null || b == Blocks.AIR) {
            throw new IllegalStateException("Required block is not registered: " + id);
        }
        if (b instanceof com.gregtechceu.gtceu.common.block.LampBlock) {
            return com.gtl.enhancedcore.integration.terminal.LampPlacement.invertedStructureLamp(b);
        }
        return Predicates.blocks(new Block[]{b});
    }

    private static TraceabilityPredicate controller(MultiblockMachineDefinition def) {
        return Predicates.controller(Predicates.blocks(new IMachineBlock[]{def.get()}));
    }

    /**
     * 蒸汽机仓室能力集（2026-09-01 用户要求：蒸汽机所有蒸汽/青铜机械方块位都可装仓室）。
     * 蒸汽机无能源仓（内部蒸汽缓存代付 EU），故不开放 INPUT_ENERGY；维护仓全结构最多 1 个。
     */
    private static TraceabilityPredicate steamPlatformCasing(String casingId) {
        return block(casingId)
                .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1));
    }

    private static TraceabilityPredicate tieredA() {
        return block("gtceu:robust_machine_casing")
                .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(2).setMaxGlobalLimited(2))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1));
    }

    // ==================== 蒸汽机（LV）：fyzqj2.schem 结构，7×6×8 ====================
    // 由 SchemTool 生成（SteamPlatformStructure + Part1），已裁剪 schem 最外侧全空列（原 8×6×8）。字符映射：
    // C=控制器，H=外壳槽（ULV~IV，决定并行/电压，必需 1 个），X=钻石矿位（蒸汽机械外壳或仓室），
    // A=青铜框架，B=蒸汽机械外壳，D=青铜机械外壳，E=青铜砖，F=铁栏杆，
    // ' '=任意方块（不校验）。禁止把空格绑定为空气，否则结构外围空白会成为成型硬性要求。
    public static BlockPattern steamPlatform(MultiblockMachineDefinition def, TraceabilityPredicate hull) {
        return SteamPlatformStructure.create()
                .where('C', controller(def))
                .where('H', hull)
                .where('X', steamPlatformCasing("gtceu:steam_machine_casing"))
                .where('A', block("gtceu:bronze_frame"))
                // 2026-09-01 用户要求：蒸汽机械方块（B）与青铜机械外壳（D）位也可装仓室。
                .where('B', steamPlatformCasing("gtceu:steam_machine_casing"))
                .where('D', steamPlatformCasing("gtceu:bronze_machine_casing"))
                .where('E', block("gtceu:bronze_brick_casing"))
                .where('F', block("minecraft:iron_bars"))
                .where(' ', Predicates.any())
                .build();
    }

    // ==================== 等离子机床（IV）：环形约束室，7×7×5 ====================
    // ==================== IV 四件套共用结构：ivsijiantao.schem，18×18×20 ====================
    // 由 SchemTool 从 ivsijiantao.schem 生成（clean 已移除白色羊毛定位块）。字符映射：
    // C=控制器（正面最后 aisle z=17，y=1 中间行），X=钻石矿位（仓室/功能位，替代方块 robust_machine_casing），
    // A=大型装配机外壳，B=层压玻璃，D=铁块，E=信标（结构内装饰），F=plascrete，G=抗应力外壳，
    // I=robust_machine_casing，J=二硅化钼线圈，' '=任意方块（不校验，禁止绑定空气）。
    public static BlockPattern ivSijiantao(MultiblockMachineDefinition def) {
        return IvsijiantaoStructure.create()
                .where('C', controller(def))
                .where('X', block("gtceu:robust_machine_casing")
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(2).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1)))
                .where('A', block("gtceu:large_scale_assembler_casing"))
                .where('B', block("gtceu:laminated_glass"))
                .where('D', block("minecraft:iron_block"))
                .where('E', block("minecraft:beacon"))
                .where('F', block("gtceu:plascrete"))
                .where('G', block("gtceu:stress_proof_casing"))
                .where('I', block("gtceu:robust_machine_casing"))
                .where('J', block("gtceu:molybdenum_disilicide_coil_block"))
                .where(' ', Predicates.any())
                .build();
    }
    // 八角环：外圈钨钢，内圈钢化玻璃，中央功能柱；正面控制器。
    public static BlockPattern plasmaMachine(MultiblockMachineDefinition def) {
        return FactoryBlockPattern.start()
                .aisle(".RRRRR.", "RRTTTRR", "RTATATR", "RRTTTRR", ".RRRRR.")
                .aisle(".RRRRR.", "RT...TR", "RA.A.AR", "RT...TR", ".RRRRR.")
                .aisle(".RRRRR.", "RT...TR", "RA.A.AR", "RT...TR", ".RRRRR.")
                .aisle(".RRRRR.", "RT...TR", "RA.A.AR", "RT...TR", ".RRRRR.")
                .aisle(".RRRRR.", "RARARAR", "RATACAR", "RARARAR", ".RRRRR.")
                .where('C', controller(def))
                .where('A', tieredA())
                .where('R', block("gtceu:robust_machine_casing"))
                .where('T', block("gtceu:tempered_glass"))
                .where('.', Predicates.air())
                .build();
    }

    // ==================== 精炼塔（IV）：精馏塔，5×5×9 ====================
    // 双层基座 + 3×3 塔身，玻璃/不锈钢带交替，塔顶收口；控制器在基座正面。
    public static BlockPattern hadronRefinery(MultiblockMachineDefinition def) {
        return FactoryBlockPattern.start()
                .aisle("..R..", ".RTR.", ".R.R.", ".R.R.", ".R.R.", ".R.R.", ".R.R.", "RRRRR", "RRRRR")
                .aisle("..R..", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", "RRRRR", "RRRRR")
                .aisle("..R..", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", "RRRRR", "RRRRR")
                .aisle("..R..", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", "RRRRR", "RRRRR")
                .aisle("..R..", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", "RRRRR", "RRRRR")
                .aisle("..R..", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", "RRRRR", "RRRRR")
                .aisle("..R..", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", "RRRRR", "RRRRR")
                .aisle("..R..", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", ".A.A.", "RRRRR", "RRRRR")
                .aisle("..R..", ".A.A.", ".T.T.", ".S.S.", ".T.T.", ".A.A.", ".A.A.", "RACAR", "RRRRR")
                .where('C', controller(def))
                .where('A', tieredA())
                .where('R', block("gtceu:robust_machine_casing"))
                .where('T', block("gtceu:tempered_glass"))
                .where('S', block("gtceu:stainless_evaporation_casing"))
                .where('.', Predicates.air())
                .build();
    }

    // ==================== 质谱阵列（IV）：双轨道环，7×7×5 ====================
    // 两层环形轨道（玻璃环/钨钢环）环绕中央功能柱，正面控制器。
    public static BlockPattern quantumArray(MultiblockMachineDefinition def) {
        return FactoryBlockPattern.start()
                .aisle("..TTT..", ".T...T.", "T..A..T", ".T...T.", "..TTT..")
                .aisle("..RRR..", ".R...R.", "R..A..R", ".R...R.", "..RRR..")
                .aisle("..TTT..", ".T...T.", "T..A..T", ".T...T.", "..TTT..")
                .aisle("..RRR..", ".R...R.", "R..A..R", ".R...R.", "..RRR..")
                .aisle("..RRR..", ".RARAR.", "RATACAR", ".RARAR.", "..RRR..")
                .where('C', controller(def))
                .where('A', tieredA())
                .where('R', block("gtceu:robust_machine_casing"))
                .where('T', block("gtceu:tempered_glass"))
                .where('.', Predicates.air())
                .build();
    }

    // ==================== 融合组装机（IV）：托卡马克，7×7×5 ====================
    // 八角聚变环（聚变机壳）+ 玻璃芯环 + 中央功能柱，正面控制器。
    public static BlockPattern fusionAssembler(MultiblockMachineDefinition def) {
        return FactoryBlockPattern.start()
                .aisle(".FFFFF.", "FFTTTFF", "FTATATF", "FFTTTFF", ".FFFFF.")
                .aisle(".FFFFF.", "FT...TF", "FA.A.AF", "FT...TF", ".FFFFF.")
                .aisle(".TTTTT.", "TT...TT", "TA.A.AT", "TT...TT", ".TTTTT.")
                .aisle(".FFFFF.", "FT...TF", "FA.A.AF", "FT...TF", ".FFFFF.")
                .aisle(".FFFFF.", "FARARAF", "FATACAF", "FARARAF", ".FFFFF.")
                .where('C', controller(def))
                .where('A', tieredA())
                .where('R', block("gtceu:robust_machine_casing"))
                .where('F', block("gtceu:fusion_casing"))
                .where('T', block("gtceu:tempered_glass"))
                .where('.', Predicates.air())
                .build();
    }

    // ==================== 气象锚点（UXV）：默认 3×3×3 ====================
    // 默认 3×3×3 高功率机器外壳，控制器正面居中。
    public static BlockPattern weatherAnchor(MultiblockMachineDefinition def) {
        return FactoryBlockPattern.start()
                .aisle("AAA", "AAA", "AAA")
                .aisle("AAA", "AAA", "AAA")
                .aisle("AAA", "ACA", "AAA")
                .where('C', controller(def))
                .where('A', block("gtceu:high_power_casing")
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(2).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.INPUT_LASER).setPreviewCount(1).setMaxGlobalLimited(1)))
                .build();
    }

    // ==================== 因果终端（MAX）：默认 3×3×3 ====================
    // 默认 3×3×3 高功率机器外壳，控制器正面居中（2026-09-09 恢复默认）。
    public static BlockPattern causalityTerminal(MultiblockMachineDefinition def) {
        return FactoryBlockPattern.start()
                .aisle("AAA", "AAA", "AAA")
                .aisle("AAA", "AAA", "AAA")
                .aisle("AAA", "ACA", "AAA")
                .where('C', controller(def))
                .where('A', block("gtceu:high_power_casing")
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.INPUT_LASER).setPreviewCount(1).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1).setMaxGlobalLimited(4)))
                .build();
    }

    // ==================== 无限奇点压缩器：qdys.schem 结构（2026-09-09 用户要求） ====================
    // 结构来自 qdys.schem（283 aisles × 283 宽 × 116 高，控制器 ~ 结构中部）；
    // 仓室位 X（钻石矿标记）成型方块 ae2:mysterious_cube，仓室能力：维护≤1、能源≤2、激光≤2、物品 I/O≤4、流体输入≤4。
    public static BlockPattern infinitySingularityCompressor(MultiblockMachineDefinition def) {
        return QdysStructure.create(def);
    }

    // ==================== 基础矿石处理厂：jcksclc.schem 结构，7×12×6 ====================

    // ==================== 中子控制工厂：zzkzgc.schem 结构，13×15×21 ====================
    // 由 SchemTool 生成（ZzkzgcStructure）。字符映射：
    // C=控制器（正面最后 aisle）、A=ZPM 消声仓必须（2026-09-05 用户要求）、
    // B=gtlcore:process_machine_casing（主体外壳），D=钻石矿位（仓室位，替换方块 gtlcore:process_machine_casing），
    // E=层压玻璃、F=石英玻璃、G=speeding_pipe、I=HSSG 线圈、' '=任意（不校验，规则 14/15）。
    // 2026-09-05 用户要求：ZPM 消声仓必须（A 位 PartAbility.MUFFLER setMinGlobalLimited(1)）；取消维护仓需求（不含 MAINTENANCE）；
    // 仓室位替换方块为 gtlcore:process_machine_casing。不开放并行控制仓/激光仓（用户历史确认）。
    public static BlockPattern neutronControlFactory(MultiblockMachineDefinition def) {
        return ZzkzgcStructure.create()
                .where('C', controller(def))
                .where('A', Predicates.blocks(GTMachines.MUFFLER_HATCH[GTValues.ZPM].getBlock()).setPreviewCount(1).setMinGlobalLimited(1).setMaxGlobalLimited(1))
                .where('B', block("gtlcore:process_machine_casing"))
                .where('D', block("gtlcore:process_machine_casing")
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1).setMaxGlobalLimited(4))
                        .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1).setMaxGlobalLimited(4)))
                .where('E', block("gtceu:laminated_glass"))
                .where('F', block("ae2:quartz_glass"))
                .where('G', block("kubejs:speeding_pipe"))
                .where('I', block("gtceu:hssg_coil_block"))
                .where(' ', Predicates.any())
                .build();
    }
    // 由 SchemTool 从 jcksclc.schem 生成（clean 已移除白色羊毛定位块）。字符映射：
    // C=控制器（正面最后一个 aisle z=11，y=1 中间行），X=钻石矿位（蒸汽机械方块或仓室），
    // A=青铜框架，B=蒸汽机械方块，D=青铜齿轮箱，E=水（必须真实放水，无水不成形），
    // ' '=任意方块（不校验，禁止绑定空气，规则 14/15）。
    public static BlockPattern orePlant(MultiblockMachineDefinition def) {
        return OrePlantStructure.create()
                .where('C', controller(def))
                // 2026-09-02 用户要求：去掉维护仓要求；X 与 B 两类蒸汽机械方块位均可装仓室。
                .where('X', orePlantCasing())
                .where('A', block("gtceu:bronze_frame"))
                .where('B', orePlantCasing())
                .where('D', block("gtceu:bronze_gearbox"))
                .where('P', block("gtceu:bronze_pipe_casing"))
                .where('E', block("minecraft:water"))
                .where(' ', Predicates.any())
                .build();
    }

    /**
     * 基础矿石处理厂仓室能力集（2026-09-02）：蒸汽机械方块位（X/B）均可装仓室。
     * 不开放 MAINTENANCE（用户要求去掉维护仓）；能源仓≤2 保留（机器电压/并行由能源仓决定）。
     */
    private static TraceabilityPredicate orePlantCasing() {
        return block("gtceu:steam_machine_casing")
                .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(2).setMaxGlobalLimited(2))
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1));
    }

    // ==================== LV 无线充能器：LVwxcnq.schem 结构，7×6×9 ====================
    // 由 SchemTool 从 LVwxcnq.schem 生成（clean 已移除白色羊毛定位块）。
    // 用户确认：此结构不需要支持任何仓室。字符映射：
    // C=控制器，B=实心机械外壳（gtceu:solid_machine_casing），避雷针已按用户要求移除（2026-08-02），
    // ' '=任意方块（不校验，禁止绑定空气，规则 15）。
    public static BlockPattern lvWirelessCharger(MultiblockMachineDefinition def) {
        return LVWirelessChargerStructure.create()
                .where('C', controller(def))
                .where('B', block("gtceu:solid_machine_casing"))
                .where(' ', Predicates.any())
                .build();
    }

    // ==================== MV 无线充能器：mvwxcnq.schem 结构，13×8×8 ====================
    // 由 SchemTool 从 mvwxcnq.schem 生成（clean 已移除白色羊毛定位块，控制器为机器方块自动映射 C）。
    // 用户确认：此结构不需要支持任何仓室。字符映射：
    // C=控制器，A=MV 机械方块（gtceu:mv_machine_casing），B=实心机械外壳（gtceu:solid_machine_casing），
    // ' '=任意方块（不校验，禁止绑定空气，规则 15）。
    public static BlockPattern mvWirelessCharger(MultiblockMachineDefinition def) {
        return MVWirelessChargerStructure.create()
                .where('C', controller(def))
                .where('A', block("gtceu:mv_machine_casing"))
                .where('B', block("gtceu:solid_machine_casing"))
                .where(' ', Predicates.any())
                .build();
    }

    // ==================== 铂系精炼矩阵（EV）：bxjljz.schem 结构，29×19×33 ====================
    /**
     * 由 SchemTool 从 bxjljz.schem 生成（clean 已移除白色羊毛定位块）。控制器 (14,3,0) 位于 z=0 正面边界，
     * 正处于 9×4 仓室面板正中心。字符映射：
     * C=控制器（正面最后 aisle，即源 z=0），X=钻石矿位（仓室位），E=solid_machine_casing（主体外壳），
     * G=heatproof_machine_casing，A=steel_pipe_casing，B=steel_gearbox，D=tempered_glass，
     * F=bronze_pipe_casing，' '=任意方块（不校验，禁止绑定空气，规则 12）。
     * <p>
     * 仓室位（用户指定）：能源仓、维护仓、并行控制仓；<b>不支持激光靶仓</b>（不绑定 INPUT_LASER/OUTPUT_LASER）。
     * 注意（规则 12）：声明 PARALLEL_HATCH 能力只让结构允许装并行仓，并行实际生效还需机器侧 recipeModifier
     * （见 PlatinumRefiningMatrixRecipeModifiers），否则并行仓装了也不起作用。
     */
    public static BlockPattern platinumRefiningMatrix(MultiblockMachineDefinition def) {
        return BxjljzStructure.create()
                .where('C', controller(def))
                .where('X', block("gtceu:solid_machine_casing")
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1)))
                .where('E', block("gtceu:solid_machine_casing"))
                .where('G', block("gtceu:heatproof_machine_casing"))
                .where('A', block("gtceu:steel_pipe_casing"))
                .where('B', block("gtceu:steel_gearbox"))
                .where('D', block("gtceu:tempered_glass"))
                .where('F', block("gtceu:bronze_pipe_casing"))
                .where(' ', Predicates.any())
                .build();
    }

    // ==================== 虚空约束采矿场（ZPM）：xkckj.schem 结构，101×63×104 ====================
    /**
     * 由 SchemTool 从 xkckj.schem 生成。原始 112×78×111 含大量空白边距与 27 个装饰性钻石块，
     * 经 `SchemTool prepare xkckj.schem 60,3,1` 裁剪到紧致包围盒（101×63×104，原点 10,0,1）
     * 并把装饰钻石块降级为空气（下游绑定为任意方块，不校验）。
     * 控制器在正面 z=0 边界（局部 50,3,0）。字符映射：
     * C=控制器，X=钻石矿位（仓室位），A=solid_machine_casing（主体外壳 46%），
     * B=black_metal_sheet（42%），E=stable_machine_casing，G=titanium_pipe_casing，
     * I=clean_machine_casing，D=titanium_gearbox，J=tempered_glass，
     * F=cyan_lamp，N=purple_lamp，K=raw_iron_block，L=raw_copper_block，
     * ' '=任意方块（不校验，禁止绑定空气，规则 12）。
     * <p>
     * 仓室（用户指定 + 产出/输入必需）：能源仓、维护仓、并行控制仓，
     * 另加物品输入仓（精准模式需投矿脉精华 kubejs:*_vein_essence）、
     * 流体输入仓（两种模式都需钻井液 gtceu:drilling_fluid）与物品输出仓
     * （两种模式都产出矿石，随机模式一次可达数十种，必须有输出空间）。
     * 参考 gtlcore 原版大型虚空采矿厂：其全部变体都含 IMPORT_ITEMS / IMPORT_FLUIDS / EXPORT_ITEMS。
     * <b>不支持激光靶仓</b>（不绑定 INPUT_LASER/OUTPUT_LASER）。
     */
    public static BlockPattern voidConstrainedMiningField(MultiblockMachineDefinition def) {
        return VoidConstrainedMiningFieldStructure.create()
                .where('C', controller(def))
                .where('X', block("gtceu:solid_machine_casing")
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1)))
                .where('A', block("gtceu:solid_machine_casing"))
                .where('B', block("gtceu:black_metal_sheet"))
                .where('E', block("gtceu:stable_machine_casing"))
                .where('G', block("gtceu:titanium_pipe_casing"))
                .where('I', block("gtceu:clean_machine_casing"))
                .where('D', block("gtceu:titanium_gearbox"))
                .where('J', block("gtceu:tempered_glass"))
                .where('F', block("gtceu:cyan_lamp"))
                .where('N', block("gtceu:purple_lamp"))
                .where('K', block("minecraft:raw_iron_block"))
                .where('L', block("minecraft:raw_copper_block"))
                .where(' ', Predicates.any())
                .build();
    }

    // ==================== 龙式场约束增殖核心（UEV）：GT_Multiblock_UEV_DragonClaw.schem，75×73×78 ====================
    /**
     * 由 SchemTool 从 GT_Multiblock_UEV_DragonClaw.schem 生成。原始 28 个钻石块中有 27 个是装饰性
     * 3×3×3 立方体，经 `SchemTool prepare` 降级为空气（下游绑定为任意方块，不校验）。
     * <p>
     * <b>控制器在主方块所在的那一整面 UHV 仓室面板上（用户指定）</b>：面板位于 y=3~8，
     * 控制器在面板正中（资源坐标 37,5,70）。原 14 个钻石矿仓室位之外，
     * 正面面板 x=29..45、y=3..8 的 87 个 UHV 外壳位也开放为仓室，共 101 个位置。
     * 该面板不在结构包围盒边界上（z=7 而包围盒 z=0..77）——GTCEu 允许控制器在结构内部
     * （`BlockPattern` 以控制器为锚点、用 `centerOffset` 记录偏移），
     * 生成器已由「必须位于边界」放宽为「就近选面」。
     * <p>
     * 字符映射：C=控制器，X=原钻石矿位与新增面板仓室位（替代方块 uhv_machine_casing），
     * A=uhv_machine_casing，B=gtlcore:fusion_casing_mk5，D=fusion_casing_mk3，E=uev_machine_casing，
     * F=iridium_casing，G=fusion_coil，I=cyan_lamp，J=fusion_casing_mk2，K=purple_lamp，
     * L=fusion_glass，N=black_metal_sheet，O=purple_metal_sheet，P=white_lamp，
     * ' '=任意方块（不校验，禁止绑定空气，规则 12）。
     * <p>
     * 仓室（用户指定）：物品输入/输出仓、维护仓、并行控制仓、能源仓、激光靶仓。
     * 并行同样必须挂 {@link GTRecipeModifiers#PARALLEL_HATCH} 才会生效。
     */
    public static BlockPattern dragonFieldProliferationCore(MultiblockMachineDefinition def) {
        return DragonFieldProliferationCoreStructure.create()
                .where('C', controller(def))
                .where('X', block("gtceu:uhv_machine_casing")
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.INPUT_LASER).setPreviewCount(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                        // 2026-09-25 用户要求：为龙式场约束增殖核心补上 Ω-天球分歧引擎（THREAD_MODIFIER）仓室位。
                        // 与大型熔炉同款写法（ProcessingMachineRegistration 第 59 行）；上限 1 个，与包内 23 处用法一致。
                        .or(Predicates.abilities(GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER()).setPreviewCount(1).setMaxGlobalLimited(1)))
                .where('A', block("gtceu:uhv_machine_casing"))
                .where('B', block("gtlcore:fusion_casing_mk5"))
                .where('D', block("gtceu:fusion_casing_mk3"))
                .where('E', block("gtceu:uev_machine_casing"))
                .where('F', block("gtlcore:iridium_casing"))
                .where('G', block("gtceu:fusion_coil"))
                .where('I', block("gtceu:cyan_lamp"))
                .where('J', block("gtceu:fusion_casing_mk2"))
                .where('K', block("gtceu:purple_lamp"))
                .where('L', block("gtceu:fusion_glass"))
                .where('N', block("gtceu:black_metal_sheet"))
                .where('O', block("gtceu:purple_metal_sheet"))
                .where('P', block("gtceu:white_lamp"))
                .where(' ', Predicates.any())
                .build();
    }

    // ==================== 超构化学扭曲仪（UEV）：hyperstructural_chemical_distorter.schem，109×111×137 ====================
    /**
     * 由 SchemTool 从 hyperstructural_chemical_distorter.schem 生成。原始 schem 已含恰好 1 个钻石块
     * （控制器 54,15,22，位于机体内部的面板正中）与 24 个钻石矿仓室位，无需降级处理。
     * 控制器不在包围盒边界上——生成器已按就近选面处理（`BlockPattern` 以控制器为锚点，GTCEu 允许内部控制器）。
     * <p>
     * 字符映射：C=控制器，X=钻石矿位（仓室位，替代方块 gtlcore:iridium_casing），
     * E=iridium_casing（用户指定的主方块与仓室成型方块），A=high_power_casing，
     * B=ptfe_pipe_casing，N=三钛及以上温度的同种线圈，K=inert_machine_casing，L=laminated_glass，
     * D=uev_hermetic_casing，F=uev_machine_casing，P=molecular_casing，J=superconducting_coil，
     * I=computer_heat_vent，Q=containment_field_generator，G=cyan_lamp，O=purple_lamp，
     * ' '=任意方块（不校验，禁止绑定空气，规则 12）。
     * <p>
     * 仓室（用户指定）：维护仓、并行控制仓、激光靶仓、输入输出仓、Ω-天球分歧引擎
     * （{@code GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER()}）。不绑定 MUFFLER。
     */
    public static BlockPattern hyperstructuralChemicalDistorter(MultiblockMachineDefinition def) {
        return HyperstructuralChemicalDistorterStructure.create()
                .where('C', controller(def))
                .where('X', block("gtlcore:iridium_casing")
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.INPUT_LASER).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setPreviewCount(1).setMaxGlobalLimited(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                        .or(Predicates.abilities(GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER()).setPreviewCount(1).setMaxGlobalLimited(1)))
                .where('E', block("gtlcore:iridium_casing"))
                .where('A', block("gtceu:high_power_casing"))
                .where('B', block("gtceu:ptfe_pipe_casing"))
                .where('N', DistorterCoils.create())
                .where('K', block("gtceu:inert_machine_casing"))
                .where('L', block("gtceu:laminated_glass"))
                .where('D', block("gtlcore:uev_hermetic_casing"))
                .where('F', block("gtceu:uev_machine_casing"))
                .where('P', block("gtlcore:molecular_casing"))
                .where('J', block("gtceu:superconducting_coil"))
                .where('I', block("gtceu:computer_heat_vent"))
                .where('Q', block("kubejs:containment_field_generator"))
                .where('G', block("gtceu:cyan_lamp"))
                .where('O', block("gtceu:purple_lamp"))
                .where(' ', Predicates.any())
                .build();
    }

    // ==================== 恒星约束聚变堆（UIV）：stellar_confinement_fusion_reactor.schem，209×119×209 ====================
    /**
     * 由 SchemTool 从 stellar_confinement_fusion_reactor.schem 生成。原始 schem 已含恰好 1 个钻石块
     * （控制器 104,59,1）与 24 个钻石矿仓室位，无需降级处理。
     * <p>
     * 字符映射：C=控制器，X=钻石矿位（仓室位，替代方块 gtlcore:iridium_casing），
     * G=iridium_casing（用户指定的主方块与仓室成型方块），D=high_power_casing，
     * B=uiv_machine_casing，L=fusion_glass，I=superconducting_coil，E=fusion_casing_mk5，
     * F=fusion_coil_mk2，N=neutronium_pipe_casing，K=computer_heat_vent，P=orange_lamp，
     * J=laser_cooling_casing，O=containment_field_generator，A=white_lamp，
     * ' '=任意方块（不校验，禁止绑定空气，规则 12）。
     * <p>
     * 仓室（用户 2026-10-05 指定）：只允许输入/输出仓室，不接受维护仓、并行控制仓、Ω-天球分歧引擎、能源仓或激光靶仓。
     * 控制器直接从放置者所属电网取电，并行与线程由控制器自身提供。
     */
    public static BlockPattern stellarConfinementFusionReactor(MultiblockMachineDefinition def) {
        return StellarConfinementFusionReactorStructure.create()
                .where('C', controller(def))
                .where('X', block("gtlcore:iridium_casing")
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1)))
                .where('G', block("gtlcore:iridium_casing"))
                .where('D', block("gtceu:high_power_casing"))
                .where('B', block("gtceu:uiv_machine_casing"))
                .where('L', block("gtceu:fusion_glass"))
                .where('I', block("gtceu:superconducting_coil"))
                .where('E', block("gtlcore:fusion_casing_mk5"))
                .where('F', block("gtlcore:fusion_coil_mk2"))
                .where('N', block("kubejs:neutronium_pipe_casing"))
                .where('K', block("gtceu:computer_heat_vent"))
                .where('P', block("gtceu:orange_lamp"))
                .where('J', block("kubejs:laser_cooling_casing"))
                .where('O', block("kubejs:containment_field_generator"))
                .where('A', block("gtceu:white_lamp"))
                .where(' ', Predicates.any())
                .build();
    }

    /** Shared 3x3x3 body used by the four restored MV passive production lines. */
    public static BlockPattern plusFactory(MultiblockMachineDefinition def, String centerBlock) {
        return FactoryBlockPattern.start()
                .aisle("BBB", "BBB", "BBB")
                .aisle("BBB", "BXB", "BBB")
                .aisle("BBB", "BCB", "BBB")
                .where('C', controller(def))
                .where('X', block(centerBlock))
                .where('B', block("gtlcore:multi_functional_casing")
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1).setMinGlobalLimited(2).setMaxGlobalLimited(2))
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setPreviewCount(1).setMinGlobalLimited(1).setMaxGlobalLimited(1)))
                .build();
    }

}
