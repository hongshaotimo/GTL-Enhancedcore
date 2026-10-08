package com.gtl.enhancedcore.common.machine.hatch;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableComputationContainer;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * A computation reception hatch that supplies computation without an optical pipe.
 * Its recipe handler also preserves the engraving laser plant's free-CWU Mixin behavior,
 * which would otherwise be bypassed by overriding the upstream handler method.
 */
public class CreativeComputationReceiverHatchMachine extends OpticalComputationHatchMachine {

    public static final int MAX_CWU_PER_TICK = CreativeComputationPolicy.MAX_CWU_PER_TICK;
    private static final ResourceLocation ENGRAVING_LASER_PLANT_ID =
            new ResourceLocation("gtceu", "engraving_laser_plant");

    public CreativeComputationReceiverHatchMachine(IMachineBlockEntity holder) {
        super(holder, false);
    }

    @Override
    protected NotifiableComputationContainer createComputationContainer(Object... args) {
        // The parent calls this while its constructor runs, before subclass fields are initialized.
        return new CreativeComputationContainer(this);
    }

    private static final class CreativeComputationContainer extends NotifiableComputationContainer {

        private CreativeComputationContainer(CreativeComputationReceiverHatchMachine machine) {
            super(machine, IO.IN, false);
        }

        @Override
        public int requestCWUt(int cwut, boolean simulate,
                               @NotNull Collection<IOpticalComputationProvider> seen) {
            seen.add(this);
            return CreativeComputationPolicy.suppliedCWUt(cwut, isAttachedToFormedMultiblock());
        }

        @Override
        public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
            seen.add(this);
            return isAttachedToFormedMultiblock() ? MAX_CWU_PER_TICK : 0;
        }

        @Override
        public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
            seen.add(this);
            return isAttachedToFormedMultiblock();
        }

        @Override
        public IOpticalComputationProvider getComputationProvider() {
            return this;
        }

        @Override
        public List<Integer> handleRecipeInner(IO io, GTRecipe recipe, List<Integer> left,
                                               @Nullable String slotName, boolean simulate) {
            if (io != IO.IN) return left;
            if (isEngravingLaserPlant()) return null;

            long required = CreativeComputationPolicy.requiredCWUt(left);
            if (required < 0) return left;
            if (required == 0) return null;

            // GTCEu's receiver normally returns here when no optical pipe is attached.
            // This hatch provides CWU directly through its recipe handler.
            int available = requestCWUt(Integer.MAX_VALUE, true);
            if (required > available) return left;

            boolean durationIsTotalCWU = recipe.data.getBoolean("duration_is_total_cwu");
            int supplied = requestCWUt(durationIsTotalCWU ? available : (int) required, simulate);
            if (!simulate && durationIsTotalCWU) {
                advanceTotalCwuProgress(supplied);
            }
            long remaining = required - supplied;
            return remaining <= 0 ? null : List.of((int) remaining);
        }

        private void advanceTotalCwuProgress(int supplied) {
            if (machine instanceof IRecipeLogicMachine recipeMachine) {
                advance(recipeMachine.getRecipeLogic(), supplied);
            } else if (machine instanceof IMultiPart part) {
                for (IMultiController controller : part.getControllers()) {
                    if (controller instanceof IRecipeLogicMachine recipeMachine) {
                        advance(recipeMachine.getRecipeLogic(), supplied);
                    }
                }
            }
        }

        private static void advance(RecipeLogic logic, int supplied) {
            logic.setProgress(CreativeComputationPolicy.progressBeforeTickIncrement(
                    logic.getProgress(), logic.getDuration(), supplied));
        }

        private boolean isAttachedToFormedMultiblock() {
            return machine instanceof IMultiPart part && part.isFormed();
        }

        private boolean isEngravingLaserPlant() {
            if (!(machine instanceof IMultiPart part)) return false;
            boolean target = false;
            for (IMultiController controller : part.getControllers()) {
                if (controller == null || !controller.isFormed()) continue;
                var controllerMachine = controller.self();
                var definition = controllerMachine == null ? null : controllerMachine.getDefinition();
                if (definition == null || !ENGRAVING_LASER_PLANT_ID.equals(definition.getId())) return false;
                target = true;
            }
            return target;
        }
    }
}
