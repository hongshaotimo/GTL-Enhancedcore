package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Shared GUI/Jade presentation: configured capacity and the actual paid recipe are separate. */
public final class AssemblyLineParallelDisplay {
    private AssemblyLineParallelDisplay() {}

    public static boolean supports(MetaMachine machine) {
        return machine != null && machine.getDefinition().getId() != null
                && "gtceu:assembly_line".equals(machine.getDefinition().getId().toString());
    }

    public static long current(WorkableElectricMultiblockMachine machine) {
        return SingleRecipeParallel.current(machine);
    }

    public static void append(MetaMachine machine, List<Component> lines) {
        if (supports(machine) && machine instanceof WorkableElectricMultiblockMachine workable) {
            lines.addAll(lines(current(workable)));
        }
    }

    public static List<Component> lines(long current) {
        return List.of(Component.translatable("tooltip.gtl_enhancedcore.assembly_line.parallel_fixed",
                        MachineRecipeModifiers.ASSEMBLY_LINE_PARALLEL).withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("gtl_enhancedcore.parallel.current", current).withStyle(ChatFormatting.GREEN));
    }
}
