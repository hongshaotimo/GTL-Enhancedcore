package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtl.enhancedcore.common.registration.RetiredMachines;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Never instantiate the upstream AE CPU/controller, even for command-placed blocks. */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class RetiredMachineDefinitionMixin {
    @ModifyVariable(method = "setMachineSupplier", at = @At("HEAD"), argsOnly = true, require = 1)
    private Function<IMachineBlockEntity, MetaMachine> retired$inertFactory(Function<IMachineBlockEntity, MetaMachine> original) {
        String id = ((MachineDefinition)(Object)this).getId().toString();
        if (!RetiredMachines.contains(id)) return original;
        return RetiredMachines.ARRAY.equals(id) ? MultiblockControllerMachine::new : MetaMachine::new;
    }
}
