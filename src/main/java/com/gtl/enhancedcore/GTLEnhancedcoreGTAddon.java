package com.gtl.enhancedcore;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gtl.enhancedcore.common.data.machine.InfinitySingularityRecipeLoader;
import com.gtl.enhancedcore.common.data.machine.EquipmentRecipeLoader;
import com.gtl.enhancedcore.common.data.machine.PlatinumRefiningRecipeLoader;
import com.gtl.enhancedcore.common.data.machine.ExoticProliferationRecipeLoader;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

/**
 * GTCEu 附属入口（@GTAddon 由 AddonFinder 扫描注解后反射无参构造实例化）。
 *
 * 作用：把本模组的命名空间登记进 GTDynamicDataPack 的 SERVER_DOMAINS，并在
 * GTRecipes.recipeAddition 阶段（AddPackFindersEvent / SERVER_DATA）输出配方 JSON。
 * 配方经动态数据包进入 RecipeManager —— 这是 JEI 唯一的配方来源
 * （GTRecipeTypeCategory.registerRecipes 只读 RecipeManager.getAllRecipesFor），
 * 同时 GTRecipeLookup 由 GTCEu RecipeManagerMixin / GregTechKubeJSPlugin 自动填充，
 * 因此这里不得再手工调用 lookup.addRecipe。
 *
 * 注意（规则 29）：initializeAddon() 在 GTCEu CommonProxy.init() 中调用，
 * 本模组的物品/机器注册由 GTLEnhancedcore 构造期的注册事件监听器负责，此处保持空实现。
 */
@GTAddon
public class GTLEnhancedcoreGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTLEnhancedcore.REGISTRATE;
    }

    @Override
    public void initializeAddon() {
        // 物品/方块/机器注册走 GTLEnhancedcore 中已有的 Registrate 与 GTCEu 注册事件，无需在此重复触发。
    }

    @Override
    public String addonModId() {
        return GTLEnhancedcore.MOD_ID;
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        EquipmentRecipeLoader.registerRecipes(provider);
        InfinitySingularityRecipeLoader.registerRecipes(provider);
        PlatinumRefiningRecipeLoader.registerRecipes(provider);
        ExoticProliferationRecipeLoader.registerRecipes(provider);
    }
}
