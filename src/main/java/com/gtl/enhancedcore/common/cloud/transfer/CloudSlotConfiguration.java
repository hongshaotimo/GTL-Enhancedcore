package com.gtl.enhancedcore.common.cloud.transfer;

import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gtl.enhancedcore.mixin.gtlcore.CloudPatternBufferAccessor;
import com.gtl.enhancedcore.mixin.gtlcore.CloudPatternSlotAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Selector data only. Physical stock and the complete pattern stack travel separately. */
public final class CloudSlotConfiguration {
    private CloudSlotConfiguration() {}

    public static CompoundTag capture(MEPatternBufferPartMachine source, int index) {
        Object[] slots = source.getInternalInventory();
        var slot = (CloudPatternSlotAccessor) slots[index];
        CompoundTag saved = slot.cloud$serialize();
        CompoundTag config = new CompoundTag();
        config.putInt("schema", 1);
        for (String key : new String[]{"virtualItemSupply", "virtualFluidSupply", "virtualCircuit"}) {
            if (saved.contains(key)) config.put(key, saved.get(key).copy());
        }
        ListTag items = new ListTag();
        slot.cloud$virtualItems().forEach(key -> items.add(key.toTag()));
        ListTag fluids = new ListTag();
        slot.cloud$virtualFluids().forEach(key -> fluids.add(key.toTag()));
        config.put("configuredVirtualItems", items);
        config.put("configuredVirtualFluids", fluids);
        var circuit = source.getCircuitForRecipe(index);
        if (!circuit.isEmpty() && IntCircuitBehaviour.isIntegratedCircuit(circuit))
            config.putInt("circuit", IntCircuitBehaviour.getCircuitConfiguration(circuit));
        if (source instanceof CloudPatternBufferAccessor access)
            config.putByte("cacheRecipeCount", access.cloud$cacheRecipeCount()[index]);
        return config;
    }
}
