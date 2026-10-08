package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Native RecipeLogic remains the machine's real trait, preserving upstream subtype contracts. */
public interface IvNativeAccess {
    IvNativeEngine iv$engine();
    CompoundTag iv$summary();
    String iv$message();
    boolean iv$managed();
    void iv$managed(boolean value);
    void iv$publish(CompoundTag summary, String message, GTRecipe display, int progress, int duration, RecipeLogic.Status status);
    default List<Component> iv$details() {
        var reasons = iv$summary().getList("reasons", Tag.TAG_STRING);
        var result = new ArrayList<Component>();
        for (int index = 0; index < Math.min(8, reasons.size()); index++) {
            try {
                var reason = Component.Serializer.fromJson(reasons.getString(index));
                if (reason != null) result.add(reason);
            } catch (RuntimeException invalid) {
                result.add(Component.translatable("gtl_enhancedcore.diagnostic.iv_failure"));
            }
        }
        return List.copyOf(result);
    }
}
