package com.gtl.enhancedcore.mixin.gtlcore;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The upstream nested class is protected in InnerClasses metadata. */
@Mixin(targets = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase$InternalSlot", remap = false)
public interface CloudPatternSlotAccessor {
    @Invoker(value = "serializeNBT", remap = false) CompoundTag cloud$serialize();
    @Invoker(value = "getItemInventory", remap = false) it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<AEItemKey> cloud$items();
    @Invoker(value = "getFluidInventory", remap = false) it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<AEFluidKey> cloud$fluids();
    @Invoker(value = "getItemCatalystInventory", remap = false) Object2LongMap<AEItemKey> cloud$itemCatalysts();
    @Invoker(value = "getFluidCatalystInventory", remap = false) Object2LongMap<AEFluidKey> cloud$fluidCatalysts();
    @Invoker(value = "getConfiguredVirtualItems", remap = false) ObjectSet<AEItemKey> cloud$virtualItems();
    @Invoker(value = "getConfiguredVirtualFluids", remap = false) ObjectSet<AEFluidKey> cloud$virtualFluids();
}
