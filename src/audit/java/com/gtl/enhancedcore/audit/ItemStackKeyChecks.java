package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.lowdragmc.lowdraglib.utils.ItemStackKey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public final class ItemStackKeyChecks {
    public static void run() {
        var stacks = new ArrayList<ItemStack>();
        stacks.add(ItemStack.EMPTY);
        stacks.add(new ItemStack(Items.STONE, 1));
        stacks.add(new ItemStack(Items.STONE, 64));
        stacks.add(new ItemStack(Items.DIRT));
        for (int damage : new int[]{0, 1, 12}) {
            var stack = new ItemStack(Items.IRON_PICKAXE);
            stack.setDamageValue(damage);
            stacks.add(stack);
        }
        for (int value : new int[]{0, 1, 2, 1}) {
            var stack = new ItemStack(Items.STONE);
            var tag = new CompoundTag();
            tag.putInt("fixture", value);
            stack.setTag(tag);
            stacks.add(stack);
        }
        ForgeRegistries.ITEMS.getEntries().stream()
                .filter(entry -> List.of("gtceu", "ae2", "gtladditions").contains(entry.getKey().location().getNamespace()))
                .limit(30).forEach(entry -> stacks.add(new ItemStack(entry.getValue())));
        var keys = new ArrayList<ItemStackKey>();
        keys.add(new ItemStackKey());
        for (var stack : stacks) {
            keys.add(new ItemStackKey(stack));
            keys.add(new ItemStackKey(stack.copy()));
        }
        keys.add(new ItemStackKey(stacks.get(1), stacks.get(3)));
        keys.add(new ItemStackKey(stacks.get(3), stacks.get(1)));
        keys.add(new ItemStackKey(stacks.get(1), stacks.get(1)));
        int assertions = 0;
        for (var left : keys) {
            if (left.equals(null) || left.equals("not a key")) throw new AssertionError("Non-key equality");
            assertions += 2;
            for (var right : keys) {
                boolean original = left == right;
                if (!original && left.getItemStack().length == right.getItemStack().length) {
                    original = true;
                    for (var candidate : right.getItemStack()) {
                        boolean found = false;
                        for (var own : left.getItemStack()) {
                            if (ItemStack.matches(candidate, own)) { found = true; break; }
                        }
                        if (!found) { original = false; break; }
                    }
                }
                if (left.equals(right) != original) throw new AssertionError("ItemStackKey equality changed");
                assertions++;
            }
        }
        GTLEnhancedcore.LOGGER.info("[CHUNK_BENCH] ITEM_KEY_CHECKS assertions={}", assertions);
    }
}
