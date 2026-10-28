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
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * JEI 集成：熔炼催化剂、灯变体以及 FTB 任务详情物品的标准 GUI ingredient 接口。
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
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        var manager = runtime.getIngredientManager();
        var retired = manager.getAllIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).stream()
                .filter(stack -> {
                    var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    return id != null && com.gtl.enhancedcore.common.registration.RetiredMachines.contains(id.toString());
                }).toList();
        manager.removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, retired);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (ModList.get().isLoaded("ftbquests") && ModList.get().isLoaded("ftblibrary")) {
            FtbQuestItemGuiHandler.register(registration);
        }
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
