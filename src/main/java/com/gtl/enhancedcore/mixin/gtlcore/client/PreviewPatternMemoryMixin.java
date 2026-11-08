package com.gtl.enhancedcore.mixin.gtlcore.client;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.client.preview.PreviewMaterialAccess;
import com.gtl.enhancedcore.client.preview.PreviewMaterialRows;
import com.gtl.enhancedcore.client.preview.PreviewPatternMemory;
import com.gtl.enhancedcore.common.structure.ChamberPositions;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Clear retained data even when a stale JEI layout still retains a material slot. */
@Mixin(targets = "org.gtlcore.gtlcore.api.gui.PatternPreviewWidget$MBPattern", remap = false)
public abstract class PreviewPatternMemoryMixin implements PreviewPatternMemory, PreviewMaterialAccess {
    @Shadow @Final List<List<ItemStack>> parts;
    @Shadow @Final Map<BlockPos, AEItemKey> materials;
    @Shadow @Final Map<BlockPos, TraceabilityPredicate> predicateMap;
    @Shadow @Final Map<BlockPos, BlockInfo> blockMap;
    @Shadow @Final Set<BlockPos> moduleOnlyBlocks;
    @Shadow @Final IMultiController controllerBase;
    @Unique private List<BlockPos> enhanced$chambers;
    @Unique private Map<BlockPos, AEItemKey> enhanced$materials;
    @Unique private Map<TraceabilityPredicate, Set<AEItemKey>> enhanced$candidateCache;
    @Unique private Set<BlockPos> enhanced$replaceable;

    @Override public Map<BlockPos, AEItemKey> enhanced$materials() {
        if (enhanced$materials != null) return enhanced$materials;
        var materials = new LinkedHashMap<BlockPos, AEItemKey>();
        var level = controllerBase.self().getLevel();
        blockMap.forEach((pos, info) -> {
            if (moduleOnlyBlocks.contains(pos)) return;
            var item = PreviewMaterialRows.material(level, pos, info);
            if (item != null) materials.put(pos, item);
        });
        return enhanced$materials = materials;
    }

    @Override public Set<AEItemKey> enhanced$candidates(TraceabilityPredicate predicate) {
        if (predicate == null) return Set.of();
        if (enhanced$candidateCache == null) enhanced$candidateCache = new IdentityHashMap<>();
        return enhanced$candidateCache.computeIfAbsent(predicate, PreviewMaterialRows::candidates);
    }

    @Override public Set<BlockPos> enhanced$replaceablePositions() {
        if (enhanced$replaceable == null) {
            enhanced$replaceable = new HashSet<>();
            predicateMap.forEach((pos, predicate) -> {
                if (enhanced$candidates(predicate).size() > 1) enhanced$replaceable.add(pos);
            });
        }
        return enhanced$replaceable;
    }

    @Override public void enhanced$cacheMaterials(Map<BlockPos, AEItemKey> materials,
            Map<TraceabilityPredicate, Set<AEItemKey>> candidates, Set<BlockPos> replaceable) {
        enhanced$materials = materials;
        enhanced$candidateCache = candidates;
        enhanced$replaceable = replaceable;
    }

    @Override public Map<BlockPos, TraceabilityPredicate> enhanced$predicates() { return predicateMap; }
    @Override public BlockPos enhanced$controllerPosition() { return controllerBase.self().getPos(); }

    @Override public List<BlockPos> enhanced$chamberPositions() {
        if (enhanced$chambers == null) {
            var classifier = new ChamberPositions();
            enhanced$chambers = predicateMap.entrySet().stream().filter(e -> classifier.isChamber(e.getValue()))
                    .map(e -> e.getKey().immutable()).toList();
        }
        return enhanced$chambers;
    }

    @Override
    public void enhanced$releasePreviewMemory() {
        // These objects are exclusively created by the client preview widget.
        // Do not touch a controller attached to a real level.
        if (!(controllerBase.self().getLevel() instanceof TrackedDummyWorld)) return;
        parts.clear();
        materials.clear();
        enhanced$chambers = null;
        if (enhanced$materials != null) enhanced$materials.clear();
        if (enhanced$candidateCache != null) enhanced$candidateCache.clear();
        if (enhanced$replaceable != null) enhanced$replaceable.clear();
        enhanced$materials = null;
        enhanced$candidateCache = null;
        enhanced$replaceable = null;
        predicateMap.clear();
        blockMap.clear();
        moduleOnlyBlocks.clear();
        // A stale JEI material handler can retain this MBPattern. Clearing its
        // entries alone leaves the large fastutil backing arrays allocated.
        if (parts instanceof ArrayList<?> list) list.trimToSize();
        if (predicateMap instanceof Object2ObjectOpenHashMap<?, ?> map) map.trim();
        if (blockMap instanceof Object2ObjectOpenHashMap<?, ?> map) map.trim();
        if (moduleOnlyBlocks instanceof ObjectOpenHashSet<?> set) set.trim();
        PreviewPatternMemory.releaseState(controllerBase.getMultiblockState());
    }
}
