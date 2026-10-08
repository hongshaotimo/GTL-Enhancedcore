package com.gtl.enhancedcore.mixin.gtceu.client;

import com.gregtechceu.gtceu.utils.input.KeyBind;
import com.lowdragmc.lowdraglib.networking.INetworking;
import com.lowdragmc.lowdraglib.networking.IPacket;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Avoid sending GTCEu key state packets after the client connection has closed. */
@Mixin(value = KeyBind.class, remap = false)
public abstract class KeyBindDisconnectedSendMixin {

    @Redirect(
            method = "onInputEvent(Lnet/minecraftforge/client/event/InputEvent$Key;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib/networking/INetworking;sendToServer(Lcom/lowdragmc/lowdraglib/networking/IPacket;)V",
                    remap = false),
            require = 1,
            allow = 1,
            remap = false)
    private static void enhancedcore$sendIfConnected(INetworking networking, IPacket packet) {
        if (Minecraft.getInstance().getConnection() != null) {
            networking.sendToServer(packet);
        }
    }
}
