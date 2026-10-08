package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNameAccess;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEPatternBufferPartMachineBase.class, remap = false)
public abstract class SuperBufferNamePersistenceMixin implements SuperBufferNameAccess {
    @Unique @Persisted @DescSynced private String enhanced$automaticName = "";
    @Unique @Persisted @DescSynced private boolean enhanced$manualName;
    @Unique private boolean enhanced$writingAutomaticName;

    @Override public String enhanced$getAutomaticName() { return enhanced$automaticName; }
    @Override public boolean enhanced$isManualName() { return enhanced$manualName; }

    @Override
    public boolean enhanced$restoreAutomaticName() {
        var self = (MEPatternBufferPartMachineBase) (Object) this;
        if (self.isRemote() || !com.gtl.enhancedcore.common.recipe.iv.IvBuffers.compatible(self)
                || !(self instanceof MEPatternBufferPartMachine buffer)) return false;
        String key = com.gtl.enhancedcore.common.recipe.iv.SuperBufferNaming.automaticKey(buffer);
        if (key.isEmpty()) return false;
        enhanced$manualName = false;
        enhanced$setAutomaticName(key);
        self.markDirty("enhanced$manualName");
        return true;
    }

    @Override
    public void enhanced$setAutomaticName(String name) {
        var self = (MEPatternBufferPartMachineBase) (Object) this;
        if (self.isRemote() || enhanced$manualName
                || !com.gtl.enhancedcore.common.recipe.iv.IvBuffers.compatible(self)) return;
        String chinese = com.gtl.enhancedcore.common.recipe.iv.SuperBufferChineseNames.resolve(name);
        enhanced$writingAutomaticName = true;
        try {
            self.setCustomName(chinese);
            enhanced$automaticName = chinese;
        } finally {
            enhanced$writingAutomaticName = false;
        }
        self.markDirty();
        self.markDirty("customName");
        self.markDirty("enhanced$automaticName");
    }

    @Inject(method = "setCustomName", at = @At("TAIL"))
    private void enhanced$persistManualName(String name, CallbackInfo ci) {
        var self = (MEPatternBufferPartMachineBase) (Object) this;
        if (!com.gtl.enhancedcore.common.recipe.iv.IvBuffers.compatible(self) || self.isRemote()
                || enhanced$writingAutomaticName) return;
        // Even confirming the same text (or an empty name) is an explicit player choice.
        enhanced$manualName = true;
        enhanced$automaticName = "";
        self.markDirty();
        self.markDirty("customName");
        self.markDirty("enhanced$automaticName");
        self.markDirty("enhanced$manualName");
    }
}
