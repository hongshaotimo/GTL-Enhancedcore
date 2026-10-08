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

/** The hatch fallbacks use the controller block, so the last matching block is not the anchor. */
@Mixin(value = SparsePreviewShape.class, remap = false)
public abstract class LucidSparsePreviewShapeMixin {
    private static final int CONTROLLER_X = 148;
    private static final int CONTROLLER_Y = 104;
    private static final int CONTROLLER_Z = 76;

    @Shadow @Final @Mutable public BlockPos controller;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void enhanced$useLucidController(MultiblockShapeInfo shape, MultiblockMachineDefinition definition,
            CallbackInfo ci) {
        if (!definition.getId().getNamespace().equals("gtladditions")
                || !definition.getId().getPath().equals("lucid_etchdreamer")) return;
        BlockInfo[][][] blocks = shape.getBlocks();
        if (blocks.length <= CONTROLLER_X || blocks[CONTROLLER_X].length <= CONTROLLER_Y
                || blocks[CONTROLLER_X][CONTROLLER_Y].length <= CONTROLLER_Z) return;
        BlockInfo info = blocks[CONTROLLER_X][CONTROLLER_Y][CONTROLLER_Z];
        if (info != null && info.getBlockState().getBlock() instanceof IMachineBlock block
                && block.getDefinition() == definition) {
            controller = new BlockPos(CONTROLLER_X, CONTROLLER_Y, CONTROLLER_Z);
        }
    }
}
