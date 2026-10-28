package com.gtl.enhancedcore.mixin.jade;

import com.gregtechceu.gtceu.integration.jade.provider.RecipeLogicProvider;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses ADD's real BigInteger Jade row rather than truncating the batch to long. */
@Mixin(value = RecipeLogicProvider.class, remap = false)
public abstract class GalaxyPowerRecipeLogicProviderMixin {
    @Inject(method = "write", at = @At("TAIL"), require = 1)
    private void galaxy$power(CompoundTag data, RecipeLogic logic, CallbackInfo ci) {
        var recipe = logic.getLastRecipe();
        if (recipe != null && recipe.data.contains(GalaxyPower.EU_TAG))
            data.putByteArray("wirelessTickInputs", GalaxyPower.payment(recipe).negate().toByteArray());
    }
}
