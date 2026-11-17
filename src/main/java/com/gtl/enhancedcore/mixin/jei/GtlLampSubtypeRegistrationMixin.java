package com.gtl.enhancedcore.mixin.jei;

import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Leave lamp subtype ownership to our placement-semantics interpreter, not all item NBT. */
@Mixin(targets = "org.gtlcore.gtlcore.integration.jei.GTLJEIPlugin", remap = false)
public abstract class GtlLampSubtypeRegistrationMixin {
    @Dynamic("GTLCore 1.2.3.3-fix4's two lamp-map consumers")
    @Redirect(method = {
            "lambda$registerItemSubtypes$0(Lmezz/jei/api/registration/ISubtypeRegistration;Lcom/tterrag/registrate/util/entry/BlockEntry;)V",
            "lambda$registerItemSubtypes$1(Lmezz/jei/api/registration/ISubtypeRegistration;Lcom/tterrag/registrate/util/entry/BlockEntry;)V"
    }, at = @At(value = "INVOKE", target = "Lmezz/jei/api/registration/ISubtypeRegistration;useNbtForSubtypes([Lnet/minecraft/world/item/Item;)V"),
            require = 2, allow = 2, remap = false)
    private static void enhancedcore$keepLampConfigurationInterpreter(ISubtypeRegistration registration, Item[] lamps) {
        // GTLEnhancedcoreJEIPlugin registers the eight meaningful switch combinations.
        // The rotor and integrated-circuit registrations are outside these consumers.
    }
}
