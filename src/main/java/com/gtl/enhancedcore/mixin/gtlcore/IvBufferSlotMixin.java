package com.gtl.enhancedcore.mixin.gtlcore;

import appeng.api.stacks.*;
import com.gtl.enhancedcore.common.recipe.iv.IvSlotAccess;
import it.unimi.dsi.fastutil.objects.*;
import java.util.*;
import org.spongepowered.asm.mixin.*;

@Mixin(targets = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase$InternalSlot", remap = false)
public abstract class IvBufferSlotMixin implements IvSlotAccess {
    @Shadow @Final private Object2LongOpenHashMap<AEItemKey> itemInventory;
    @Shadow @Final private Object2LongOpenHashMap<AEFluidKey> fluidInventory;
    @Shadow @Final private ObjectSet<AEItemKey> configuredVirtualItems;
    @Shadow @Final private ObjectSet<AEFluidKey> configuredVirtualFluids;
    @Shadow public abstract Object2LongMap<AEItemKey> getItemCatalystInventory();
    @Shadow public abstract Object2LongMap<AEFluidKey> getFluidCatalystInventory();
    @Override public boolean iv$hasStock() { return !itemInventory.isEmpty() || !fluidInventory.isEmpty(); }
    @Shadow public abstract void clearVirtualSupply();
    @Override public void iv$discardStock() {
        itemInventory.clear();fluidInventory.clear();clearVirtualSupply();
    }
    @Override public Map<AEKey, Long> iv$virtualStock() {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        getItemCatalystInventory().forEach((key, amount) -> { if (amount > 0) result.put(key, amount); });
        getFluidCatalystInventory().forEach((key, amount) -> { if (amount > 0) result.put(key, amount); });
        configuredVirtualItems.forEach(key -> result.put(key, Long.MAX_VALUE));
        configuredVirtualFluids.forEach(key -> result.put(key, Long.MAX_VALUE));
        return result;
    }
}
