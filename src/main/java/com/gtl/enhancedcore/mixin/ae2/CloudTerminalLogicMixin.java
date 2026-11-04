package com.gtl.enhancedcore.mixin.ae2;

import appeng.parts.encoding.PatternEncodingLogic;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.api.inventories.InternalInventory;
import com.gtl.enhancedcore.common.cloud.transfer.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PatternEncodingLogic.class, remap = false)
public abstract class CloudTerminalLogicMixin implements CloudTerminalSource {
    @Unique private CompoundTag cloud$state = new CompoundTag();
    @Shadow @Final private IPatternTerminalLogicHost host;
    @Unique private boolean cloud$reconciled;
    @Override public CompoundTag cloud$sourceState() { return cloud$state; }
    @Override public void cloud$markReconciled() { cloud$reconciled = true; }
    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void cloud$write(CompoundTag tag, CallbackInfo ci) { if (!cloud$state.isEmpty()) tag.put("gtlCloudSource", cloud$state.copy()); }
    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void cloud$read(CompoundTag tag, CallbackInfo ci) {
        cloud$state = tag.getCompound("gtlCloudSource").copy();
        cloud$reconciled = false;
        cloud$reconcile();
    }
    @Inject(method = "readFromNBT", at = @At("HEAD"))
    private void cloud$beginRead(CompoundTag tag, CallbackInfo ci) {
        // Inventory callbacks during AE deserialization must not reconcile the previous source state.
        cloud$reconciled = true;
    }
    @Inject(method = "getEncodedPatternInv", at = @At("HEAD"))
    private void cloud$beforeAccess(CallbackInfoReturnable<InternalInventory> ci) { cloud$reconcile(); }
    @Unique private void cloud$reconcile() {
        if (cloud$reconciled) return;
        if (!cloud$state.hasUUID("identity")) { cloud$markReconciled(); return; }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread() && host.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            CloudTerminalUpload.reconcile(level, (PatternEncodingLogic)(Object)this);
        }
    }
}
