package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.client.preview.SparsePreviewShape;

/** Isolated title-screen probe for the real lucid preview supplier and controller cell. */
final class LucidPreviewProbe {
    private LucidPreviewProbe() {}

    static void run() {
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(
                new ResourceLocation("gtladditions:lucid_etchdreamer"));
        require(definition != null, "missing machine definition");
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] registered=true jeiEnabled={}",
                definition.isRenderXEIPreview());
        require(definition.isRenderXEIPreview(), "JEI preview disabled");
        var pattern = definition.getPatternFactory().get();
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] patternReplaced={}",
                GtlMegastructurePatterns.needsLucidPreview(pattern));
        require(GtlMegastructurePatterns.needsLucidPreview(pattern), "pattern was not replaced");
        var matches = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] sourceControllerPredicate={} candidateBlocks={}",
                matches[156][104][84].isController,
                matches[156][104][84].common.stream().filter(it -> it.candidates != null)
                        .flatMap(it -> java.util.Arrays.stream(it.candidates.get()))
                        .map(it -> it.getBlockState().getBlock().toString()).toList());
        var casing = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtlcore:iridium_casing"));
        var oldFallback = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtladditions:lucid_etchdreamer"));
        var xPredicate = matches[156][102][82];
        var xCandidates = Stream.concat(xPredicate.limited.stream(), xPredicate.common.stream())
                .filter(it -> it.candidates != null)
                .flatMap(it -> Arrays.stream(it.candidates.get()))
                .filter(Objects::nonNull)
                .map(it -> it.getBlockState().getBlock()).toList();
        require(xCandidates.contains(casing), "X does not accept original iridium casing");
        require(xCandidates.contains(oldFallback), "X no longer accepts old controller fallback");
        var shapes = definition.getMatchingShapes();
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] shapes={}", shapes.size());
        require(shapes.size() == 1, "unexpected shape count");
        BlockInfo[][][] blocks = shapes.getFirst().getBlocks();
        require(blocks.length == 233 && blocks[0].length == 233 && blocks[0][0].length == 233,
                "unexpected dimensions");
        BlockInfo center = blocks[148][104][76];
        require(center != null, "controller cell is null");
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] centerBlock={} centerEntity={}",
                center.getBlockState().getBlock(), center.hasBlockEntity());
        var controllerCells = new ArrayList<String>();
        for (int x = 146; x <= 150; x++) for (int y = 102; y <= 106; y++) for (int z = 75; z <= 77; z++) {
            var info = blocks[x][y][z];
            if (info != null && info.getBlockState().getBlock() instanceof IMachineBlock machineBlock
                    && machineBlock.getDefinition() == definition) {
                controllerCells.add(x + "," + y + "," + z + ":" + info.hasBlockEntity());
            }
        }
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] controllerCells={}", controllerCells);
        require(controllerCells.size() == 1, "preview requires extra controller blocks");
        int casingSlots = 0;
        for (int xPos = 146; xPos <= 150; xPos++) for (int yPos = 102; yPos <= 106; yPos++) {
            if (xPos == 148 && yPos == 104) continue;
            var info = blocks[xPos][yPos][76];
            if (info != null && info.getBlockState().is(casing)) casingSlots++;
        }
        require(casingSlots == 20, "preview should show iridium casing in 20 unused X slots: " + casingSlots);
        require(center.hasBlockEntity(), "controller cell has no block entity");
        var entity = center.getBlockEntity(new BlockPos(148, 104, 76));
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] controllerEntityType={}",
                entity == null ? "null" : entity.getClass().getName());
        require(entity != null, "controller entity creation failed");
        var sparse = new SparsePreviewShape(shapes.getFirst(), definition);
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] sparsePositions={} sparseController={}",
                sparse.positions.length, sparse.controller);
        require(new BlockPos(148, 104, 76).equals(sparse.controller), "sparse controller mismatch");
        GTLEnhancedcore.LOGGER.info("[LUCID_PREVIEW] COMPLETE");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
