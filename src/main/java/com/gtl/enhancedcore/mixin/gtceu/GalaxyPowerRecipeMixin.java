package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

/** NBT EU survives recipe persistence; payment is separate from int/long EU capabilities. */
@Mixin(value = RecipeLogic.class, remap = false, priority = 1400)
public abstract class GalaxyPowerRecipeMixin {
    @WrapMethod(method = "handleTickRecipe")
    private GTRecipe.ActionResult galaxy$pay(GTRecipe recipe, Operation<GTRecipe.ActionResult> original) {
        if (!recipe.data.contains(GalaxyPower.EU_TAG)
                || !(((RecipeLogic)(Object)this).getMachine() instanceof WorkableElectricMultiblockMachine machine))
            return original.call(recipe);
        var match = recipe.matchTickRecipe(machine);
        if (!match.isSuccess()) return match;
        var payment = GalaxyPower.payment(recipe);
        var receipt = GalaxyPower.withdraw(machine, payment);
        if (receipt == null) return GTRecipe.ActionResult.fail(() -> Component.translatable("gtl_enhancedcore.diagnostic.power"));
        boolean complete = false;
        try {
            var result = original.call(recipe);
            complete = result.isSuccess();
            if (complete && recipe.data.contains(GalaxyPower.LEFT_TAG)) {
                var left = new java.math.BigInteger(recipe.data.getString(GalaxyPower.LEFT_TAG));
                recipe.data.putString(GalaxyPower.LEFT_TAG, left.subtract(payment).toString());
                machine.markDirty();
            }
            return result;
        } finally { if (!complete) receipt.refund(); }
    }
}
