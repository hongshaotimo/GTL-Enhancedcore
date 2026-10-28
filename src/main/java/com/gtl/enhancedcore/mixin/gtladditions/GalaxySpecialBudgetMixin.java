package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** The fragment stripper reads its thread field directly. Taixu already uses the
 * enhanced getter, so its ordinary budget must not receive the bonus twice. */
@Mixin(targets={
        "com.gtladd.gtladditions.common.machine.multiblock.controller.TaixuTurbidArray$Companion$TaixuTurbidArrayLogic",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.MacroAtomicResonantFragmentStripper$Companion$MacroAtomicResonantFragmentStripperLogic"
},remap=false)
public abstract class GalaxySpecialBudgetMixin {
    @ModifyVariable(method="calculateParallels",at=@At(value="STORE",ordinal=0),ordinal=0,require=1)
    private long galaxy$specialBudget(long original) {
        if (!(((RecipeLogic)(Object)this).getMachine() instanceof WorkableElectricMultiblockMachine machine)) return original;
        if ((Object)this instanceof com.gtladd.gtladditions.common.machine.multiblock.controller.TaixuTurbidArray.Companion.TaixuTurbidArrayLogic)
            return GalaxyLinks.unlimited(machine) ? (long)Integer.MAX_VALUE * Integer.MAX_VALUE : original;
        return GalaxyLinks.batchBudget(machine, original);
    }
}
