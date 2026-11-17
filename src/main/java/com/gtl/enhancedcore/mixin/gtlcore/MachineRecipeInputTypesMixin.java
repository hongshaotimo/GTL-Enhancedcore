package com.gtl.enhancedcore.mixin.gtlcore;

import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

/** Adapt the two item-like inputs that Core passes to GTCEu's unsupported Object overload. */
@Mixin(targets = "org.gtlcore.gtlcore.data.recipe.MachineRecipe", remap = false)
public abstract class MachineRecipeInputTypesMixin {
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;inputItems(Ljava/lang/Object;)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;"),
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=kubejs:essence_block"),
                    to = @At(value = "FIELD", target =
                            "Lorg/gtlcore/gtlcore/common/data/GTLMachines;BLOCK_BUS:Lcom/gregtechceu/gtceu/api/machine/MachineDefinition;")),
            index = 0, require = 1, allow = 1)
    private static Object enhancedcore$essenceBlockItem(Object block) {
        return ((ItemLike) block).asItem();
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;inputItems(Ljava/lang/Object;I)Lcom/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder;"),
            slice = @Slice(from = @At(value = "FIELD", target =
                    "Lappeng/core/definitions/AEItems;SPEED_CARD:Lappeng/core/definitions/ItemDefinition;"),
                    to = @At(value = "FIELD", target =
                            "Lorg/gtlcore/gtlcore/common/data/machines/AdvancedMultiBlockMachineA;BLOCK_CONVERSION_ROOM:Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;")),
            index = 0, require = 1, allow = 1)
    private static Object enhancedcore$speedCardItem(Object definition) {
        return ((ItemLike) definition).asItem();
    }
}
