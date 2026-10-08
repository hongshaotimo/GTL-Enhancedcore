package com.gtl.enhancedcore.common.data;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.GTLEnhancedcore;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * GTL-Enhancedcore 自定义配方类型。
 *
 * 气象锚点的 5 个环境模式（白昼/黑夜/晴天/雨天/雷暴）、因果重构终端、无线充能器都是空配方表，
 * 仅作为 GUI 侧栏的模式切换器（与蒸汽机 13 模式同款机制），不参与任何配方查找。
 *
 * 无限奇点压缩器的配方类型是真实配方表：配方由 GTLEnhancedcoreGTAddon.addRecipes 按
 * GTCEu 标准链路输出（GTDynamicDataPack → RecipeManager），JEI 自动显示。
 *
 * 注册方式与 GTRecipeTypes.register 一致，但命名空间必须是 gtl_enhancedcore（规则 9）。
 */
public final class GTLEnhancedcoreRecipeTypes {

    public static final GTRecipeType ANCHOR_DAY = register("anchor_day");
    public static final GTRecipeType ANCHOR_NIGHT = register("anchor_night");
    public static final GTRecipeType ANCHOR_CLEAR = register("anchor_clear");
    public static final GTRecipeType ANCHOR_RAIN = register("anchor_rain");
    public static final GTRecipeType ANCHOR_THUNDER = register("anchor_thunder");

    /** 气象锚点的 5 个模式；顺序即 GUI 侧栏列表顺序（用户指定，勿改）。 */
    public static final GTRecipeType[] ANCHOR_MODES = {
            ANCHOR_DAY, ANCHOR_NIGHT, ANCHOR_CLEAR, ANCHOR_RAIN, ANCHOR_THUNDER
    };

    /** 因果重构终端的唯一配方类型（空表，仅占位——机器不走 RecipeMap，逻辑全自写）。 */
    public static final GTRecipeType CAUSALITY_TERMINAL = register("causality_terminal");

    /** 无线充能器的唯一配方类型（空表，仅占位——机器不走 RecipeMap，逻辑全自写）。 */
    public static final GTRecipeType WIRELESS_CHARGER = register("wireless_charger");

    /** 无限奇点压缩器的唯一配方类型（1 物品或 1 流体输入 → 1 无限元件输出，持续耗电）。 */
    public static final GTRecipeType INFINITY_SINGULARITY = register("infinity_singularity")
            .setMaxIOSize(1, 1, 1, 0)
            .setEUIO(IO.IN);

    /**
     * 铂系精炼矩阵的唯一配方类型「铂系精炼」（显示名走翻译键 gtl_enhancedcore.platinum_refining）。
     * 真实配方表（唯一配方：16×铂族矿泥粉 → 铂16/钯16/钌16/铑32/铱18/锇15 六种铂系粉尘，无概率副产物），
     * 配方由 PlatinumRefiningRecipeLoader 在 GTCEu 附属配方阶段按标准链路输出（JEI 自动显示）。
     * setMaxIOSize 参数顺序为（物品入, 物品出, 流体入, 流体出）——GTCEu 字节码实证，勿改顺序。
     * 本配方类型无流体，物品出 = 6 个铂系产物（2026-09-24 补入钌粉后由 5 调整为 6）。
     */
    public static final GTRecipeType PLATINUM_REFINING = register("platinum_refining")
            .setMaxIOSize(1, 6, 0, 0)
            .setEUIO(IO.IN);

    /**
     * 龙式场约束增殖核心的唯一配方类型「异种物质增殖」
     * （显示名走翻译键 gtl_enhancedcore.exotic_proliferation，JEI 与 tooltip 共用该键）。
     * 真实配方表（唯一配方：512×黑曜石 + 52mB 龙息 + 1×转换模拟卡[不消耗] → 512×龙尘），
     * setMaxIOSize 参数顺序为（物品入, 物品出, 流体入, 流体出）——GTCEu 字节码实证，勿改顺序。
     * 物品入 = 2（黑曜石 + 不消耗的转换模拟卡），物品出 = 1，流体入 = 1。
     */
    public static final GTRecipeType EXOTIC_PROLIFERATION = register("exotic_proliferation")
            .setMaxIOSize(2, 1, 1, 0)
            .setEUIO(IO.IN);

    private GTLEnhancedcoreRecipeTypes() {
    }

    private static GTRecipeType register(String name) {
        GTRecipeType recipeType = new GTRecipeType(GTLEnhancedcore.id(name), "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, recipeType.registryName, recipeType);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, recipeType.registryName, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(recipeType.registryName, recipeType);
        return recipeType;
    }

    public static void init() {
        // 静态字段注册在类加载时完成，此方法仅用于主动触发类加载。
    }
}
