package com.gtl.enhancedcore.integration.jei;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.item.LampConfiguration;
import com.gregtechceu.gtceu.api.item.LampBlockItem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 集成 —— 把大型熔炉注册为原版熔炼配方的催化剂。
 * 任务点击跳转 JEI 功能已于 2026-08-04 按用户要求整体移除（FluidJEIHelper/ItemJEIHelper 已删除）。
 *
 * 规则（见 API标准.md）：机器通过 getter 获取，避免过早触发静态注册；
 * getLargeFurnace() 可能为 null（注册事件未触发），必须判空。
 */
@JeiPlugin
public class GTLEnhancedcoreJEIPlugin implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation(com.gtl.enhancedcore.GTLEnhancedcore.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (var item : ForgeRegistries.ITEMS) {
            if (item instanceof LampBlockItem) {
                registration.registerSubtypeInterpreter(item,
                        (IIngredientSubtypeInterpreter<ItemStack>) (stack, context) -> LampConfiguration.subtype(stack));
            }
        }
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        var lamps = new java.util.ArrayList<ItemStack>();
        for (var item : ForgeRegistries.ITEMS) {
            if (item instanceof LampBlockItem lamp) {
                for (int variant = 0; variant < 8; variant++) lamps.add(lamp.getBlock().getStackFromIndex(variant));
            }
        }
        registration.addExtraItemStacks(lamps);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        MultiblockMachineDefinition machine = GTLEnhancedcoreMachines.getLargeFurnace();
        if (machine != null) {
            ItemStack stack = machine.asStack();
            registration.addRecipeCatalyst(stack, new RecipeType[]{RecipeTypes.SMELTING});
        }
    }
}
