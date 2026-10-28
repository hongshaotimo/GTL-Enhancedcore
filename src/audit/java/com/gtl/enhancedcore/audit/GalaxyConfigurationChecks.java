package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.config.GalaxyConnectionConfig;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Registered factories only: no all-registry geometry scan or player world is needed. */
public final class GalaxyConfigurationChecks {
    private GalaxyConfigurationChecks() {}

    public static Map<String, Object> run(BiConsumer<Boolean, String> check) {
        var ids = GalaxyConnectionConfig.defaults().stream().sorted().toList();
        check.accept(ids.size() == 56, "Complete explicit galaxy default template has 56 IDs");
        check.accept(GalaxyConnectionConfig.parse(GalaxyConnectionConfig.defaultText()).equals(GalaxyConnectionConfig.defaults()),
                "Generated galaxy template exactly matches all explicit defaults");
        var classes = new LinkedHashMap<String, String>();
        for (String id : ids) {
            var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
            check.accept(definition instanceof MultiblockMachineDefinition, "Galaxy default is registered multiblock: " + id);
            if (!(definition instanceof MultiblockMachineDefinition)) continue;
            var holder = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
            var machine = holder instanceof IMachineBlockEntity entity ? entity.getMetaMachine() : null;
            check.accept(machine instanceof WorkableElectricMultiblockMachine, "Galaxy default has electric controller factory: " + id);
            if (machine instanceof WorkableElectricMultiblockMachine electric) {
                classes.put(id, machine.getClass().getName());
                check.accept(GalaxyLinks.allowed(electric) == GalaxyConnectionConfig.allows(definition.getId()),
                        "Real controller admission follows configured full ID: " + id);
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("defaultCount", ids.size());
        result.put("defaultIds", ids);
        result.put("controllerClasses", classes);
        result.put("template", GalaxyConnectionConfig.defaultText());
        return result;
    }
}
