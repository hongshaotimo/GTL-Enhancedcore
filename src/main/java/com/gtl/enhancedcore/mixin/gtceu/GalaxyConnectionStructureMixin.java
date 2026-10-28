package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers all ability predicates, including third-party and IV material-port alternatives. */
@Mixin(value = BlockPattern.class, remap = false)
public abstract class GalaxyConnectionStructureMixin {
    @Inject(method = "checkPatternAt(Lcom/gregtechceu/gtceu/api/pattern/MultiblockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;ZZ)Z",
            at = @At("RETURN"), cancellable = true)
    private void galaxy$allowlist(MultiblockState state, BlockPos pos, Direction facing, Direction upwards,
                                  boolean flipped, boolean save, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        Object parts = state.getMatchContext().get("parts");
        if (!(parts instanceof Iterable<?> matched)) return;
        for (Object part : matched) {
            if (part instanceof GalaxyConnectionHatch hatch && !hatch.isSource()
                    && (!(state.getController() instanceof WorkableElectricMultiblockMachine machine)
                    || !GalaxyLinks.allowed(machine))) {
                state.setError(new PatternStringError("gtl_enhancedcore.galaxy.machine_not_allowed"));
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
