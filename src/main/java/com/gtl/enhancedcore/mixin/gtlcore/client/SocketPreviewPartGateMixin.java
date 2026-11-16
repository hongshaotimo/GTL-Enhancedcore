package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.client.socketpreview.PartItems;
import org.gtlcore.gtlcore.client.socketpreview.SocketPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Socket discovery is useful only while holding an actual multiblock part. */
@Mixin(value = SocketPreview.class, remap = false)
public abstract class SocketPreviewPartGateMixin {
    @Redirect(method = "tickUnsafe", at = @At(value = "INVOKE",
            target = "Lorg/gtlcore/gtlcore/client/socketpreview/PartItems;partMachine(Lnet/minecraft/world/item/ItemStack;)Lcom/gregtechceu/gtceu/api/item/MetaMachineItem;"),
            require = 2, allow = 2)
    private static MetaMachineItem enhanced$registeredPart(ItemStack stack) {
        return PartItems.isRegisteredPart(stack) ? PartItems.partMachine(stack) : null;
    }
}
