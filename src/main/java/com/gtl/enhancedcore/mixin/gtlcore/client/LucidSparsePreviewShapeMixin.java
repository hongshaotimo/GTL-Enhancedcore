package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import org.gtlcore.gtlcore.client.preview.SparsePreviewShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Anchor the sparse page at the controller coordinates of the current schematic. */
@Mixin(value = SparsePreviewShape.class, remap = false)
public abstract class LucidSparsePreviewShapeMixin {
    @Shadow @Final @Mutable public BlockPos controller;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void enhanced$useLucidController(MultiblockShapeInfo shape, MultiblockMachineDefinition definition,
            CallbackInfo ci) {
        if (!definition.getId().getNamespace().equals("gtladditions")
                || !definition.getId().getPath().equals("lucid_etchdreamer")) return;
        BlockPos anchor = com.gtl.enhancedcore.common.structure.LucidEtchdreamerPreview.controllerPosition();
        int x = anchor.getX(), y = anchor.getY(), z = anchor.getZ();
        BlockInfo[][][] blocks = shape.getBlocks();
        if (blocks.length <= x || blocks[x].length <= y || blocks[x][y].length <= z) return;
        BlockInfo info = blocks[x][y][z];
        if (info != null && info.getBlockState().getBlock() instanceof IMachineBlock block
                && block.getDefinition() == definition) {
            controller = anchor;
        }
    }
}
