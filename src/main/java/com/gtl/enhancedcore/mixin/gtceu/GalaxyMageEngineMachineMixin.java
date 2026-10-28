package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart;
import org.spongepowered.asm.mixin.Mixin;

/** Mage is a plain electric controller; ADD recalculates a part only for this interface. */
@Mixin(value = WorkableElectricMultiblockMachine.class, remap = false)
public abstract class GalaxyMageEngineMachineMixin implements IThreadModifierMachine {
    @Override public IThreadModifierPart getThreadPartMachine() {
        var machine = (WorkableElectricMultiblockMachine)(Object)this;
        if (!machine.getDefinition().getId().toString().equals("gtceu:mage_assembler")) return null;
        return machine.getParts().stream().filter(part -> part instanceof IThreadModifierPart)
                .map(part -> (IThreadModifierPart)part).findFirst().orElse(null);
    }
}
