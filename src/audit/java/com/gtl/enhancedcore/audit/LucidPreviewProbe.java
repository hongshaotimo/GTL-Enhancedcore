package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.client.preview.SparsePreviewShape;

/** Isolated title-screen probe for the native ADD Lucid preview. */
final class LucidPreviewProbe {
    private LucidPreviewProbe() {}

    static void run() {
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(
                new ResourceLocation("gtladditions:lucid_etchdreamer"));
        require(definition != null, "missing machine definition");
        require(definition.isRenderXEIPreview(), "JEI preview disabled");

        var pattern = definition.getPatternFactory().get();
        var matches = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        // ADD 3.6.2 ships Lucid as a 21×21×22 pattern, stored in depth/height/width order here.
        require(matches.length == 22 && matches[0].length == 21 && matches[0][0].length == 21,
                "unexpected native Lucid pattern dimensions");

        var shapes = definition.getMatchingShapes();
        require(shapes.size() == 1, "unexpected native shape count");
        var shape = shapes.getFirst();
        var blocks = shape.getBlocks();
        BlockPos controller = null;
        for (int x = 0; x < blocks.length; x++) {
            for (int y = 0; y < blocks[x].length; y++) {
                for (int z = 0; z < blocks[x][y].length; z++) {
                    var info = blocks[x][y][z];
                    if (info == null || !(info.getBlockState().getBlock() instanceof IMachineBlock machineBlock)
                            || machineBlock.getDefinition() != definition) continue;
                    require(controller == null, "preview contains multiple Lucid controllers");
                    controller = new BlockPos(x, y, z);
                    require(info.hasBlockEntity() && info.getBlockEntity(controller) != null,
                            "preview controller is missing its block entity");
                }
            }
        }
        require(controller != null, "preview contains no Lucid controller");
        var sparse = new SparsePreviewShape(shape, definition);
        require(controller.equals(sparse.controller), "sparse controller does not match the native preview");
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] COMPLETE controller={} sparsePositions={}",
                controller, sparse.positions.length);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
