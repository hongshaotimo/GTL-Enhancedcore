package com.gtl.enhancedcore.client.preview;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;

/** Opaque bridges generated in the target class, where its package-private types are legal. */
public interface PreviewPageAccess {
    Object[] enhanced$newPatterns(int size);
    void enhanced$replacePatterns(Object[] patterns);
    TrackedDummyWorld enhanced$previewWorld();
    BlockPos enhanced$nextRegion();
    BlockEntity enhanced$previewController(BlockInfo info, BlockPos position);
    BlockInfo enhanced$plainBlock(BlockState state);
    void enhanced$formPreview(Collection<BlockPos> positions, IMultiController controller);
    void enhanced$gatherDrop(Map<?, ?> parts, Map<?, ?> materials, Map.Entry<BlockPos, BlockInfo> entry);
    List<IMultiController> enhanced$initializeModules(IModularMachineHost<?> host, BlockPos position,
            Map<BlockPos, BlockInfo> blocks, Map<BlockPos, BlockInfo> hostBlocks, Set<BlockPos> moduleBlocks);
    void enhanced$mergeModulePredicates(IMultiController controller, Map<?, ?> predicates);
    Object enhanced$finishPattern(Map<?, ?> blocks, Set<?> moduleBlocks, IMultiController controller,
            Map<?, ?> predicates, Map<?, ?> parts, Map<?, ?> materials);
}
