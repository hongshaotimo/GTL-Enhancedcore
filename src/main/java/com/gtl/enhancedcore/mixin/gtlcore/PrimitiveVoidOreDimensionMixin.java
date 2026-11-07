package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.recipe.PrimitiveVoidOreRouting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.common.machine.multiblock.noenergy.PrimitiveOreMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes each selected circuit choose the native ore pool independently of machine location. */
@Mixin(value = PrimitiveOreMachine.class, remap = false)
public abstract class PrimitiveVoidOreDimensionMixin {
    @Redirect(method = "onWorking",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;",
                    remap = true), require = 3, allow = 3, remap = false)
    private ResourceKey<Level> enhancedcore$selectedDimension(Level level) {
        PrimitiveOreMachine machine = (PrimitiveOreMachine) (Object) this;
        int target = PrimitiveVoidOreRouting.target(machine.getRecipeLogic().getLastRecipe());
        return switch (target) {
            case 1 -> Level.OVERWORLD;
            case 2 -> Level.NETHER;
            case 3 -> Level.END;
            default -> null;
        };
    }
}
