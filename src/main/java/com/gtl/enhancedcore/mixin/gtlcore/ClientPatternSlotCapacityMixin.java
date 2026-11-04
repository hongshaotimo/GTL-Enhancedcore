package com.gtl.enhancedcore.mixin.gtlcore;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep independent mutable inventories; client-only empty collections start at fastutil's minimum capacity. */
@Mixin(targets = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase$InternalSlot", remap = false)
public abstract class ClientPatternSlotCapacityMixin {
    @WrapOperation(method = "<init>", at = @At(value = "NEW",
            target = "()Lit/unimi/dsi/fastutil/objects/Object2LongOpenHashMap;"), require = 2, allow = 2)
    private Object2LongOpenHashMap<?> enhancedcore$smallClientMap(Operation<Object2LongOpenHashMap<?>> original,
            MEPatternBufferPartMachineBase owner, int slot) {
        return owner.isRemote() ? new Object2LongOpenHashMap<>(0) : original.call();
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW",
            target = "()Lit/unimi/dsi/fastutil/objects/ObjectOpenHashSet;"), require = 4, allow = 4)
    private ObjectOpenHashSet<?> enhancedcore$smallClientSet(Operation<ObjectOpenHashSet<?>> original,
            MEPatternBufferPartMachineBase owner, int slot) {
        return owner.isRemote() ? new ObjectOpenHashSet<>(0) : original.call();
    }
}
