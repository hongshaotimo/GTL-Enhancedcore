package com.gtl.enhancedcore.mixin.ae2;

import appeng.api.storage.ITerminalHost;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.guisync.GuiSync;
import appeng.parts.encoding.PatternEncodingLogic;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.transfer.CloudTerminalUpload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public abstract class CloudTerminalMenuMixin extends MEStorageMenu implements CloudTerminalMenu {
    @Shadow @Final private PatternEncodingLogic encodingLogic;
    @GuiSync(261) public boolean cloud$bound;
    @GuiSync(262) public String cloud$review = "";
    protected CloudTerminalMenuMixin(MenuType<?> type, int id, Inventory inventory, ITerminalHost host) { super(type, id, inventory, host); }
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/IPatternTerminalMenuHost;Z)V", at = @At("TAIL"))
    private void cloud$setup(CallbackInfo ci) {
        registerClientAction("gtlCloudUpload", this::cloud$upload);
        if (getPlayer() instanceof ServerPlayer player) CloudTerminalUpload.reconcile(player.serverLevel(), encodingLogic);
    }
    @Unique private CloudLibraryNetwork.Binding cloud$binding() {
        if (!(getPlayer() instanceof ServerPlayer player) || player.containerMenu != this || !isValidMenu() || !stillValid(player)
                || !canInteractWithGrid() || !isPowered())
            return CloudLibraryNetwork.resolve(null, (java.util.UUID)null);
        var block = getBlockEntity();
        if (block != null && (!(block.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !CloudLibraryService.claimAllows(player, level, block.getBlockPos())))
            return CloudLibraryNetwork.resolve(null, (java.util.UUID)null);
        var node = getNetworkNode();
        return CloudLibraryNetwork.resolve(node == null || !node.isActive() ? null : node.getGrid(), player);
    }
    @Inject(method = {"broadcastChanges()V", "m_38946_()V"}, at = @At("HEAD"), require = 1)
    private void cloud$refresh(CallbackInfo ci) {
        if (!isClientSide()) { cloud$bound = cloud$binding().available(); cloud$review = CloudTerminalUpload.diagnostic(encodingLogic); }
    }
    @Override public boolean cloud$libraryBound() { return cloud$bound; }
    @Override public String cloud$diagnostic() { return cloud$review; }
    @Override public boolean cloud$hasOriginal() {
        if (!cloud$review.isEmpty() || CloudTerminalUpload.quarantined(encodingLogic)) return false;
        var stack = encodingLogic.getEncodedPatternInv().getStackInSlot(0);
        return CloudTerminalUpload.processingOriginal(stack, getPlayer().level());
    }
    @Override public void cloud$upload() {
        if (isClientSide()) { sendClientAction("gtlCloudUpload"); return; }
        if (!(getPlayer() instanceof ServerPlayer player)) return;
        var binding = cloud$binding();
        if (!binding.available()) { cloud$bound = false; return; }
        if (CloudTerminalUpload.quarantined(encodingLogic)) return;
        try {
            CloudTerminalUpload.upload(player, binding.libraryId(), encodingLogic);
            player.displayClientMessage(Component.translatable("gtl_enhancedcore.cloud.terminal.uploaded"), true);
        } catch (RuntimeException failure) {
            player.displayClientMessage(Component.translatable(failure instanceof com.gtl.enhancedcore.common.cloud.store.CloudPatternStore.DuplicatePatternException
                    ? "gtl_enhancedcore.cloud.status.duplicate" : failure instanceof CloudTerminalUpload.RetryableSourceChangeException
                    ? "gtl_enhancedcore.cloud.terminal.source_changed" : "gtl_enhancedcore.cloud.terminal.failed"), true);
        }
        cloud$bound = cloud$binding().available();
    }
}
