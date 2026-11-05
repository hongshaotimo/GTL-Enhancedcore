package com.gtl.enhancedcore.mixin.gtlcore.client;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.client.preview.PreviewMaterialAccess;
import com.gtl.enhancedcore.client.preview.PreviewPatternMemory;
import com.gtl.enhancedcore.common.structure.ChamberPositions;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Clear retained data even when a stale JEI layout still retains a material slot. */
@Mixin(targets = "org.gtlcore.gtlcore.api.gui.PatternPreviewWidget$MBPattern", remap = false)
public abstract class PreviewPatternMemoryMixin implements PreviewPatternMemory, PreviewMaterialAccess {
    @Shadow @Final List<List<ItemStack>> parts;
    @Shadow @Final Map<BlockPos, TraceabilityPredicate> predicateMap;
    @Shadow @Final Map<BlockPos, BlockInfo> blockMap;
    @Shadow @Final Set<BlockPos> moduleOnlyBlocks;
    @Shadow @Final IMultiController controllerBase;
    @Unique private List<BlockPos> enhanced$chambers;

    @Override public Map<BlockPos, AEItemKey> enhanced$materials() {
        var materials = new LinkedHashMap<BlockPos, AEItemKey>();
        var level = controllerBase.self().getLevel();
        blockMap.forEach((pos, info) -> {
            if (moduleOnlyBlocks.contains(pos)) return;
            var state = info.getBlockState();
            var stack = state.getBlock().getCloneItemStack(level, pos, state);
            if (stack.isEmpty() && !state.getFluidState().isEmpty()) {
                Fluid fluid = state.getFluidState().getType();
                stack = fluid.getBucket().getDefaultInstance();
            }
            if (!stack.isEmpty()) materials.put(pos, AEItemKey.of(stack));
        });
        return materials;
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
        enhanced$chambers = null;
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
