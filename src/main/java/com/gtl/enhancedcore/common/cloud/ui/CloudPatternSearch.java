package com.gtl.enhancedcore.common.cloud.ui;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import java.util.BitSet;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;

/** Immutable client-language search aliases; these IDs only filter authorized server results. */
public final class CloudPatternSearch {
    public static final CloudPatternSearch EMPTY = new CloudPatternSearch(new BitSet(), new BitSet(), Set.of());
    private final BitSet items, fluids;
    private final Set<String> types;

    public CloudPatternSearch(BitSet items, BitSet fluids, Set<String> types) {
        this.items = (BitSet) items.clone(); this.fluids = (BitSet) fluids.clone();
        this.types = Set.copyOf(types);
    }
    public BitSet items() { return (BitSet) items.clone(); }
    public BitSet fluids() { return (BitSet) fluids.clone(); }
    public Set<String> types() { return Set.copyOf(types); }
    public boolean matchesItem(int id) { return id >= 0 && items.get(id); }
    public boolean matchesFluid(int id) { return id >= 0 && fluids.get(id); }
    public boolean matchesType(String id) { return types.contains(id); }
    public boolean matches(AEKey key) {
        return key instanceof AEItemKey item ? matchesItem(BuiltInRegistries.ITEM.getId(item.getItem()))
                : key instanceof AEFluidKey fluid && matchesFluid(BuiltInRegistries.FLUID.getId(fluid.getFluid()));
    }
    public void write(FriendlyByteBuf buffer) {
        buffer.writeLongArray(items.toLongArray()); buffer.writeLongArray(fluids.toLongArray());
        buffer.writeVarInt(types.size());
        for (String id : new TreeSet<>(types)) buffer.writeUtf(id, 128);
    }
    public static CloudPatternSearch read(FriendlyByteBuf buffer) {
        BitSet items = readBits(buffer, BuiltInRegistries.ITEM.size());
        BitSet fluids = readBits(buffer, BuiltInRegistries.FLUID.size());
        int count = buffer.readVarInt();
        if (count < 0 || count > GTRegistries.RECIPE_TYPES.values().size()) throw new IllegalArgumentException("Invalid cloud recipe-type alias count");
        Set<String> types = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf(128);
            var location = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (location == null || !GTRegistries.RECIPE_TYPES.containKey(location)) throw new IllegalArgumentException("Unknown cloud recipe-type alias");
            types.add(id);
        }
        return new CloudPatternSearch(items, fluids, types);
    }
    private static BitSet readBits(FriendlyByteBuf buffer, int size) {
        BitSet bits = BitSet.valueOf(buffer.readLongArray(null, (size + 63) / 64));
        if (bits.length() > size) throw new IllegalArgumentException("Cloud registry alias is outside the synchronized registry");
        return bits;
    }
    @Override public boolean equals(Object other) {
        return other instanceof CloudPatternSearch query && items.equals(query.items) && fluids.equals(query.fluids) && types.equals(query.types);
    }
    @Override public int hashCode() { return java.util.Objects.hash(items, fluids, types); }
}
