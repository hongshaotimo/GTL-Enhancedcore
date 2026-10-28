package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.cloud.transfer.CloudImportSourceAccess;
import com.gtl.enhancedcore.common.cloud.transfer.CloudImportJournal;
import java.util.*;
import net.minecraft.nbt.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = MEPatternBufferPartMachine.class, remap = false)
public abstract class CloudImportSourceMixin implements CloudImportSourceAccess {
    @Unique private UUID cloud$sourceId;
    @Unique private final Set<UUID> cloud$processed = new LinkedHashSet<>();
    @Unique private String cloud$quarantine = "";
    @Override public UUID cloud$sourceIdentity() {
        if (cloud$sourceId == null) { cloud$sourceId = UUID.randomUUID(); ((MEPatternBufferPartMachine)(Object)this).markDirty(); }
        return cloud$sourceId;
    }
    @Override public boolean cloud$processed(UUID transaction) { return cloud$processed.contains(transaction); }
    @Override public void cloud$markProcessed(UUID transaction) {
        cloud$processed.add(transaction); ((MEPatternBufferPartMachine)(Object)this).markDirty();
    }
    @Override public boolean cloud$importQuarantined() { return !cloud$quarantine.isEmpty(); }
    @Override public void cloud$quarantineImport(String reason) { cloud$quarantine = reason; ((MEPatternBufferPartMachine)(Object)this).markDirty(); }
    @Override public CompoundTag cloud$saveImportMarkers() {
        CompoundTag tag = new CompoundTag(); tag.putUUID("identity", cloud$sourceIdentity());
        ListTag ids = new ListTag(); for (UUID id : cloud$processed) { CompoundTag row = new CompoundTag(); row.putUUID("id", id); ids.add(row); }
        tag.put("processed", ids); tag.putString("quarantine", cloud$quarantine); return tag;
    }
    @Override public void cloud$loadImportMarkers(CompoundTag tag) {
        cloud$processed.clear(); cloud$sourceId = tag.hasUUID("identity") ? tag.getUUID("identity") : null;
        for (Tag item : tag.getList("processed", Tag.TAG_COMPOUND)) cloud$processed.add(((CompoundTag)item).getUUID("id"));
        cloud$quarantine = tag.getString("quarantine");
    }
    @Inject(method = "saveCustomPersistedData", at = @At("TAIL"))
    private void cloud$saveMarkers(CompoundTag tag, boolean forDrop, CallbackInfo ci) {
        if (forDrop) { tag.remove("gtl_cloud_import"); return; }
        tag.put("gtl_cloud_import", cloud$saveImportMarkers());
    }
    @Inject(method = "loadCustomPersistedData", at = @At("TAIL"))
    private void cloud$loadMarkers(CompoundTag tag, CallbackInfo ci) { cloud$loadImportMarkers(tag.getCompound("gtl_cloud_import")); }
    @Inject(method = "onLoad", at = @At("HEAD"))
    private void cloud$reconcile(CallbackInfo ci) { CloudImportJournal.reconcile((MEPatternBufferPartMachine)(Object)this); }
    @Inject(method = "onMachineRemoved", at = @At("HEAD"))
    private void cloud$retire(CallbackInfo ci) { CloudImportJournal.retire((MEPatternBufferPartMachine)(Object)this); }
    @Inject(method = "copyFromTag", at = @At("HEAD"), cancellable = true)
    private void cloud$blockCopyFrom(CompoundTag tag, net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        if (cloud$importQuarantined() || com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport.sourceLocked(this)) ci.cancel();
    }
    @Inject(method = "copyToTag", at = @At("HEAD"), cancellable = true)
    private void cloud$blockCopyTo(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (cloud$importQuarantined() || com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport.sourceLocked(this)) cir.setReturnValue(new CompoundTag());
    }
    @Inject(method = "cutToTag", at = @At("HEAD"), cancellable = true)
    private void cloud$blockCutTo(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (cloud$importQuarantined() || com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport.sourceLocked(this)) cir.setReturnValue(new CompoundTag());
    }
    @Inject(method = "pasteFromTag", at = @At("HEAD"), cancellable = true)
    private void cloud$blockPasteFrom(CompoundTag tag, CallbackInfoReturnable<Boolean> cir) {
        if (cloud$importQuarantined() || com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport.sourceLocked(this)) cir.setReturnValue(false);
    }
    @Inject(method = "getAvailablePatterns", at = @At("HEAD"), cancellable = true)
    private void cloud$blockPublication(CallbackInfoReturnable<java.util.List<appeng.api.crafting.IPatternDetails>> cir) {
        if (cloud$importQuarantined() || com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport.sourceLocked(this)) cir.setReturnValue(java.util.List.of());
    }
    @Inject(method = "createUIWidget", at = @At("HEAD"), cancellable = true)
    private void cloud$blockMenu(CallbackInfoReturnable<com.lowdragmc.lowdraglib.gui.widget.Widget> cir) {
        if (cloud$importQuarantined() || com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport.sourceLocked(this)) cir.setReturnValue(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(4, 4,
                "gtl_enhancedcore.cloud.status.review"));
    }
}
