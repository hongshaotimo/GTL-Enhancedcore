package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.MachineDiagnostics;
import com.gtl.enhancedcore.integration.jade.MachineDiagnosticProvider;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;

public final class MachineDiagnosticChecks {
    private static int assertions;

    private MachineDiagnosticChecks() {}

    public static int inspect(Object candidate, Object inputInventory, Object outputInventory) {
        var machine = (WorkableElectricMultiblockMachine) candidate;
        var input = (IItemTransfer) inputInventory;
        var output = (IItemTransfer) outputInventory;
        int previousAssertions = assertions;
        String inputsBefore = inventory(input);
        String outputsBefore = inventory(output);
        var energy = machine.getEnergyContainer();
        long energyBefore = energy == null ? 0 : energy.getEnergyStored();
        int progressBefore = machine.getRecipeLogic().getProgress();
        var expected = MachineDiagnostics.currentDetails(machine);
        check(!expected.isEmpty(), "Rejected recipe has no diagnostic snapshot");
        check(expected.size() <= MachineDiagnostics.MAX_DETAILS, "Diagnostic snapshot exceeds its line budget");
        var gui = new ArrayList<Component>();
        machine.addDisplayText(gui);
        for (var reason : expected) check(gui.stream().anyMatch(line -> normalize(line).equals(normalize(reason))),
                "GUI omits a shared diagnostic: " + Component.Serializer.toJson(reason));
        var data = new CompoundTag();
        BlockAccessor accessor = (BlockAccessor) Proxy.newProxyInstance(BlockAccessor.class.getClassLoader(),
                new Class<?>[]{BlockAccessor.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getBlockEntity" -> machine.getLevel().getBlockEntity(machine.getPos());
                    case "getServerData" -> data;
                    case "getLevel" -> machine.getLevel();
                    case "getPosition" -> machine.getPos();
                    case "toString" -> "IsolatedMachineDiagnosticAccessor";
                    default -> null;
                });
        var provider = new MachineDiagnosticProvider();
        provider.appendServerData(data, accessor);
        var received = new ArrayList<Component>();
        ITooltip tooltip = (ITooltip) Proxy.newProxyInstance(ITooltip.class.getClassLoader(),
                new Class<?>[]{ITooltip.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("add") && arguments.length == 1 && arguments[0] instanceof Component reason) {
                        received.add(reason);
                        return null;
                    }
                    if (method.getName().equals("size")) return received.size();
                    if (method.getName().equals("isEmpty")) return received.isEmpty();
                    if (method.getName().equals("toString")) return "IsolatedMachineDiagnosticTooltip";
                    throw new IllegalStateException("Unexpected Jade callback: " + method.getName());
                });
        provider.appendTooltip(tooltip, accessor, null);
        check(received.size() == expected.size(), "Jade dropped or duplicated shared diagnostic lines");
        for (int index = 0; index < expected.size(); index++) {
            String encoded = normalize(expected.get(index));
            check(encoded.equals(normalize(received.get(index))), "Jade and GUI diagnose different reasons");
            check(encoded.contains("translate"), "Server diagnostic lost its per-player translation keys");
        }
        received.clear();
        data.remove("enhancedMachineDiagnosticDetails");
        data.putString("enhancedMachineDiagnostic", Component.Serializer.toJson(expected.getFirst()));
        provider.appendTooltip(tooltip, accessor, null);
        check(received.size() == 1 && normalize(received.getFirst()).equals(normalize(expected.getFirst())),
                "Previous single-component Jade responses are incompatible");
        received.clear();
        var invalid = new ListTag();
        invalid.add(StringTag.valueOf("{broken"));
        invalid.add(StringTag.valueOf("null"));
        data.remove("enhancedMachineDiagnostic");
        data.put("enhancedMachineDiagnosticDetails", invalid);
        provider.appendTooltip(tooltip, accessor, null);
        check(received.isEmpty(), "Malformed Jade data escaped into the overlay");
        check(inputsBefore.equals(inventory(input)) && outputsBefore.equals(inventory(output)), "Diagnostic reads changed inventory");
        check(energyBefore == (energy == null ? 0 : energy.getEnergyStored()), "Diagnostic reads paid recipe energy");
        check(progressBefore == machine.getRecipeLogic().getProgress(), "Diagnostic reads advanced a recipe");
        return assertions - previousAssertions;
    }

    private static String inventory(IItemTransfer inventory) {
        var contents = new ArrayList<String>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) contents.add(inventory.getStackInSlot(slot).serializeNBT().toString());
        return contents.toString();
    }

    private static String normalize(Component component) {
        return Component.Serializer.toJson(component.copy().setStyle(Style.EMPTY));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
