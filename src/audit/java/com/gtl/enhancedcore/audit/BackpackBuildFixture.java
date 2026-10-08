package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses the installed backpack's real saved inventory and ordinary stacking upgrades. */
final class BackpackBuildFixture {
    private BackpackBuildFixture() {}

    @SuppressWarnings("unchecked")
    static ItemStack create() throws Exception {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("sophisticatedbackpacks:netherite_backpack"));
        if (item == null) throw new IllegalStateException("Sophisticated Backpacks is required for this audit");
        var stack = new ItemStack(item);
        var capability = (Capability<Object>) Class.forName(
                "net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper")
                .getField("BACKPACK_WRAPPER_CAPABILITY").get(null);
        var wrapper = stack.getCapability(capability).orElseThrow(
                () -> new IllegalStateException("Backpack wrapper unavailable"));
        wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
        var upgrades = (IItemHandler) wrapper.getClass().getMethod("getUpgradeHandler").invoke(wrapper);
        var upgrade = ForgeRegistries.ITEMS.getValue(new ResourceLocation("sophisticatedbackpacks:stack_upgrade_tier_4"));
        for (int slot = 0; slot < 2; slot++) {
            if (!upgrades.insertItem(slot, new ItemStack(upgrade), false).isEmpty()) {
                throw new IllegalStateException("Ordinary stacking upgrade rejected");
            }
        }
        if (inventory(stack).getSlotLimit(0) < 16384) throw new IllegalStateException("Stacking upgrades inactive");
        return stack;
    }

    static IItemHandler inventory(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(
                () -> new IllegalStateException("Backpack ITEM_HANDLER unavailable"));
    }

    static void insert(ItemStack backpack, ItemStack material) {
        if (!ItemHandlerHelper.insertItemStacked(inventory(backpack), material, false).isEmpty()) {
            throw new IllegalStateException("Backpack supply insertion failed: " + material);
        }
    }

    static Map<AEItemKey, Long> contents(ItemStack backpack) {
        Map<AEItemKey, Long> result = new HashMap<>();
        var handler = inventory(backpack);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var item = handler.getStackInSlot(slot);
            if (!item.isEmpty()) result.merge(AEItemKey.of(item), (long) item.getCount(), Long::sum);
        }
        return result;
    }
}
