package com.gtl.enhancedcore.common.data.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Consumer;

/**
 * 「异种物质增殖」唯一配方加载器（龙式场约束增殖核心专用）。
 * <p>
 * 按用户规格：输入 512× {@code minecraft:obsidian} + 52mB {@code gtceu:dragon_breath}
 * + 1× {@code gtlcore:conversion_simulate_card}（**不消耗**），
 * 产出 512× {@code kubejs:draconium_dust}，UEV 电压。
 * <p>
 * <b>不消耗输入的实现（2026-09-24 javap 实证）</b>：{@code GTRecipeBuilder} 没有
 * {@code notConsumable(...)} 方法；GTCEu 自带的 KubeJS 集成
 * （{@code GTRecipeSchema$GTRecipeJS.notConsumable}）的做法是
 * **临时把 builder 的 {@code chance} 置 0、调用 {@code inputItems(...)}、再还原 chance**，
 * 于是该输入的 {@code Content.chance == 0}，引擎按「不消耗」处理。
 * 本类按同一机制实现：{@link #notConsumableItems} 负责置 0 与还原。
 * <p>
 * 注意 {@code inputItems(Object, int)} 的 {@code Object} 重载**不接受 String**（见 API标准.md 第三十三节），
 * 因此这里统一先把 id 解析成 {@link Item}。
 */
public final class ExoticProliferationRecipeLoader {

    /** 黑曜石输入数量。 */
    private static final int OBSIDIAN_INPUT = 512;
    /** 龙息输入量（mB，用户指定）。 */
    private static final int DRAGON_BREATH_INPUT = 52;
    /** 龙尘产出数量。 */
    private static final int DRACONIUM_OUTPUT = 512;
    /** 配方耗时（tick）：UEV 阶段增殖反应，取 20 秒。 */
    private static final int DURATION = 400;

    private ExoticProliferationRecipeLoader() {}

    /** GTCEu 附属配方阶段回调。 */
    public static void registerRecipes(Consumer<FinishedRecipe> provider) {
        GTRecipeType recipeType = GTLEnhancedcoreRecipeTypes.EXOTIC_PROLIFERATION;
        if (recipeType == null) {
            GTLEnhancedcore.LOGGER.error("[ExoticProliferation] recipe type not registered");
            return;
        }

        Item obsidian = item("minecraft:obsidian");
        Item catalyst = item("gtlcore:conversion_simulate_card");
        Item draconium = item("kubejs:draconium_dust");
        Fluid dragonBreath = fluid("gtceu:dragon_breath");
        if (obsidian == null || catalyst == null || draconium == null || dragonBreath == null) {
            GTLEnhancedcore.LOGGER.error("[ExoticProliferation] missing ingredient (obsidian={}, catalyst={}, output={}, fluid={}), no recipe registered",
                    obsidian != null, catalyst != null, draconium != null, dragonBreath != null);
            return;
        }

        var builder = recipeType.recipeBuilder(GTLEnhancedcore.id("exotic_proliferation/draconium_dust"))
                .inputItems(obsidian, OBSIDIAN_INPUT)
                .inputFluids(FluidIngredient.of(DRAGON_BREATH_INPUT, dragonBreath));
        notConsumableItems(builder, catalyst);
        builder.outputItems(draconium, DRACONIUM_OUTPUT)
                .EUt(GTValues.VA[GTValues.UEV])
                .duration(DURATION)
                .save(provider);
        GTLEnhancedcore.LOGGER.info("[ExoticProliferation] Registered 1 recipe");
    }

    /**
     * 按 GTCEu KubeJS 集成同款机制标记「不消耗」物品输入：临时把 builder 的 chance 置 0，
     * 添加输入后还原。chance == 0 的 Content 即被引擎视为催化剂。
     */
    private static void notConsumableItems(com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder builder, Item item) {
        int previousChance = builder.chance;
        builder.chance(0);
        builder.inputItems(item, 1);
        builder.chance(previousChance);
    }

    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null || item == Items.AIR ? null : item;
    }

    private static Fluid fluid(String id) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        return fluid == null || fluid == Fluids.EMPTY ? null : fluid;
    }
}
