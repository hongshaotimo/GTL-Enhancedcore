package com.gtl.enhancedcore.common.data.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Consumer;

/**
 * 「铂系精炼」唯一配方加载器（铂系精炼矩阵专用）。
 * <p>
 * 按用户规格：输入 16× {@code gtceu:platinum_group_sludge_dust}，
 * 产出 16× 铂粉 / 16× 钯粉 / **16× 钌粉** / 32× 铑粉 / 18× 铱粉 / 15× 锇粉。
 * <p>
 * 2026-09-22 用户要求：**去掉金/银/镍/铜四个概率副产物**，只保留铂系产物。
 * 原先用 {@code chancedOutput(stack, 3000, 10000)} 实现 30% 概率（分母
 * {@code ChanceLogic.getMaxChancedValue()} 字节码为 10000），现已整体移除。
 * 2026-09-24 用户补正：产物清单漏了钌粉 16 个，已补入（原 KJS 配方本就含钌）。
 * <p>
 * <b>重要（2026-09-22 实测踩坑）</b>：{@code GTRecipeBuilder.outputItems(Object, int)} 与
 * {@code inputItems(Object, int)} 的 {@code Object} 重载**不接受 String**——字节码里只 instanceof
 * 判定 Item / Supplier / ItemStack / Ingredient / UnificationEntry / MachineDefinition，
 * 落到 String 分支时仅打一行 error 日志（"output item is not one of: ..."）后**静默丢弃该产物**，
 * 配方照常注册但产物为空。因此必须先把 id 解析成 {@link Item} 再传入。
 * <p>
 * 输出走标准链路（GTRecipeBuilder.save → GTDynamicDataPack → RecipeManager），
 * 因此 JEI 自动显示，GTRecipeLookup 由 GTCEu 自行填充（禁止手工 addRecipe）。
 */
public final class PlatinumRefiningRecipeLoader {

    /** 铂族矿泥粉输入数量。 */
    private static final int SLUDGE_INPUT = 16;
    /** 配方耗时（tick）：EV 阶段多步精炼，取 20 秒。 */
    private static final int DURATION = 400;

    /** 六个铂系产物：物品 id + 数量。全部必出，无概率副产物。 */
    private static final String[][] OUTPUTS = {
            {"gtceu:platinum_dust", "16"},
            {"gtceu:palladium_dust", "16"},
            {"gtceu:ruthenium_dust", "16"},
            {"gtceu:rhodium_dust", "32"},
            {"gtceu:iridium_dust", "18"},
            {"gtceu:osmium_dust", "15"},
    };

    private PlatinumRefiningRecipeLoader() {}

    /** GTCEu 附属配方阶段回调。 */
    public static void registerRecipes(Consumer<FinishedRecipe> provider) {
        GTRecipeType recipeType = GTLEnhancedcoreRecipeTypes.PLATINUM_REFINING;
        if (recipeType == null) {
            GTLEnhancedcore.LOGGER.error("[PlatinumRefining] recipe type not registered");
            return;
        }
        Item sludge = item("gtceu:platinum_group_sludge_dust");
        if (sludge == null) {
            GTLEnhancedcore.LOGGER.error("[PlatinumRefining] gtceu:platinum_group_sludge_dust not found, no recipe registered");
            return;
        }
        // 先全部解析成功再建配方：任一产物缺失就整体不注册，避免出现「有配方但产物为空」的静默坏配方。
        Item[] outputs = new Item[OUTPUTS.length];
        int[] counts = new int[OUTPUTS.length];
        for (int i = 0; i < OUTPUTS.length; i++) {
            outputs[i] = item(OUTPUTS[i][0]);
            if (outputs[i] == null) {
                GTLEnhancedcore.LOGGER.error("[PlatinumRefining] output item {} not found, no recipe registered", OUTPUTS[i][0]);
                return;
            }
            counts[i] = Integer.parseInt(OUTPUTS[i][1]);
        }

        var builder = recipeType.recipeBuilder(GTLEnhancedcore.id("platinum_refining/platinum_group_sludge_metals"))
                .inputItems(sludge, SLUDGE_INPUT);
        for (int i = 0; i < outputs.length; i++) {
            builder.outputItems(outputs[i], counts[i]);
        }
        builder.EUt(GTValues.VA[GTValues.EV]).duration(DURATION).save(provider);
        GTLEnhancedcore.LOGGER.info("[PlatinumRefining] Registered 1 recipe with {} outputs", outputs.length);
    }

    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return item == null || item == Items.AIR ? null : item;
    }
}
