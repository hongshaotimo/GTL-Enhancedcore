package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.stream.Stream;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Explicit block qualification shared by structure candidates and the isolated-order engine. */
public final class IvBufferRegistry {
    private static final Set<ResourceLocation> BLOCKS = ConcurrentHashMap.newKeySet();
    static { BLOCKS.add(new ResourceLocation("gtladditions", "me_super_pattern_buffer")); }

    private IvBufferRegistry() {}

    /** Register during mod setup on both sides, before multiblock previews are first requested. */
    public static boolean register(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        if (id.equals(new ResourceLocation("minecraft", "air")))
            throw new IllegalArgumentException("Air cannot qualify as an IV pattern buffer");
        return BLOCKS.add(id);
    }

    /** The block must already be registered; use the ID overload before registry completion. */
    public static boolean register(Block block) {
        Objects.requireNonNull(block, "block");
        var id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null || block == Blocks.AIR)
            throw new IllegalArgumentException("An IV pattern buffer must be a registered non-air block");
        return register(id);
    }

    public static boolean contains(ResourceLocation id) { return id != null && BLOCKS.contains(id); }
    public static Set<ResourceLocation> registeredIds() { return Set.copyOf(BLOCKS); }

    /** Registered GTLCore pattern-buffer parts receive the runtime contract from the base-class mixin. */
    public static boolean compatible(Object machine) {
        return machine instanceof MEPatternBufferPartMachine buffer && machine instanceof IvBufferAccess
                && contains(buffer.getDefinition().getId());
    }

    /** Dynamic candidates keep registrations visible to predicates created earlier in mod setup. */
    public static TraceabilityPredicate predicate() {
        return new TraceabilityPredicate(world -> contains(ForgeRegistries.BLOCKS.getKey(
                world.getBlockState().getBlock())), () -> registeredIds().stream().sorted()
                .map(ForgeRegistries.BLOCKS::getValue).filter(block -> block != null && block != Blocks.AIR)
                .map(block -> new BlockInfo(block.defaultBlockState())).toArray(BlockInfo[]::new));
    }

    /** Qualified buffers cannot use an ordinary ability branch to evade the assembly count limit. */
    public static TraceabilityPredicate ordinaryMaterialPorts() {
        return new TraceabilityPredicate(world -> {
            var block = world.getBlockState().getBlock();
            return !contains(ForgeRegistries.BLOCKS.getKey(block)) && IvNativeHatches.materials(block);
        }, () -> Stream.of(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS,
                        PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS)
                .flatMap(ability -> ability.getAllBlocks().stream()).distinct()
                .filter(block -> !contains(ForgeRegistries.BLOCKS.getKey(block)))
                .map(block -> new BlockInfo(block.defaultBlockState())).toArray(BlockInfo[]::new));
    }

    /** One shared quota accepts either ordinary material ports or isolated buffers, never both. */
    public static TraceabilityPredicate materialPorts(int maxSuperBuffers) {
        if (maxSuperBuffers < 0 && maxSuperBuffers != -1)
            throw new IllegalArgumentException("Maximum buffer count must be -1 (unlimited) or non-negative");
        return new TraceabilityPredicate(world -> {
            var block = world.getBlockState().getBlock();
            boolean isolated = contains(ForgeRegistries.BLOCKS.getKey(block));
            if (!isolated && !IvNativeHatches.materials(block)) return false;

            MaterialPortSelection selection = world.getMatchContext()
                    .getOrCreate("gtlEnhancedcoreMaterialPortSelection", MaterialPortSelection::new);
            if (selection.isolated == null) selection.isolated = isolated;
            if (selection.isolated != isolated) return false;
            if (isolated) {
                if (maxSuperBuffers >= 0 && selection.superBuffers >= maxSuperBuffers) return false;
                selection.superBuffers++;
            }
            return true;
        }, () -> Stream.concat(Stream.of(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS,
                                PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS)
                        .flatMap(ability -> ability.getAllBlocks().stream())
                        .filter(block -> !contains(ForgeRegistries.BLOCKS.getKey(block))),
                registeredIds().stream().sorted().map(ForgeRegistries.BLOCKS::getValue)
                        .filter(block -> block != null && block != Blocks.AIR))
                .distinct().map(block -> new BlockInfo(block.defaultBlockState())).toArray(BlockInfo[]::new))
                .setMinGlobalLimited(1).setPreviewCount(1);
    }

    private static final class MaterialPortSelection {
        private Boolean isolated;
        private int superBuffers;
    }
}
