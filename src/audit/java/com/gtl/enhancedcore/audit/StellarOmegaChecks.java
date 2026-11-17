package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyWirelessEnergyHandler;
import com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeEngine;
import com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import com.gtladd.gtladditions.common.machine.multiblock.part.ThreadPartMachine;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Registered Omega lifecycle and transformed thread getters; synthetic loaded-link fixture only. */
public final class StellarOmegaChecks {
    private StellarOmegaChecks() {}

    public static List<Map<String, Object>> run() throws Exception {
        check(Minecraft.getInstance().level == null && Minecraft.getInstance().getSingleplayerServer() == null,
                "Omega probe requires the title screen");
        var rows = new ArrayList<Map<String, Object>>();
        for (String id : List.of("gtceu:field_extruder_factory", "gtceu:mage_assembler",
                "gtl_enhancedcore:basic_ore_processing_plant", "gtl_enhancedcore:hyperstructural_chemical_distorter")) {
            rows.add(probe(id, false));
            if (IvMachineScope.nativeTarget(new ResourceLocation(id))) rows.add(probe(id, true));
        }
        return rows;
    }

    private static Map<String, Object> probe(String id, boolean isolated) throws Exception {
        var fixture = StellarIvGuiChecks.fixture(id, false, false, 0);
        var machine = fixture.machine();
        try {
            // Stabilize tier-based native threads. Voltage-based parallel getters
            // still follow the real linked/physical voltage and are checked below.
            field(machine, "tier").setInt(machine, com.gregtechceu.gtceu.api.GTValues.MAX);
            if (!isolated) setParts(machine, machine.getParts().stream().filter(p -> !IvBuffers.compatible(p)).toList());
            var station = GalaxyLinks.linkedStation(machine);
            check(station != null, id + " linked station resolves");
            field(station, "userId").set(station, UUID.randomUUID());
            var link = machine.getParts().stream().filter(p -> p instanceof GalaxyConnectionHatch
                    && p.getClass().getSimpleName().equals("LinkProbe")).findFirst().orElseThrow();
            var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtladditions", "thread_modifier_hatch"));
            check(definition != null, "Registered Omega hatch exists");
            var holder = (MetaMachineBlockEntity)definition.getBlockEntityType().create(
                    new BlockPos(8, 80, 0), definition.defaultBlockState());
            holder.setLevel(machine.getLevel()); fixture.holders().add(holder);
            var omega = (ThreadPartMachine)holder.getMetaMachine();
            var inventory = field(omega, "astralArrayInventory");
            ((com.lowdragmc.lowdraglib.misc.ItemStackTransfer)inventory.get(omega)).setStackInSlot(0,
                    com.gtladd.gtladditions.common.items.GTLAddItems.INSTANCE.getASTRAL_ARRAY().asStack(1));
            var parts = new ArrayList<IMultiPart>(machine.getParts()); parts.add(omega); setParts(machine, parts);
            omega.addedToController(machine);
            check(machine instanceof IThreadModifierMachine modifier
                            && modifier.getThreadPartMachine() == omega && modifier.getAdditionalThread() == omega.getThreadCount()
                            && omega.getThreadCount() > 0,
                    id + " real Omega binds its actual thread inventory");
            check(IvMachineScope.crossRecipeEnabled(machine) == isolated, id + " retains requested IV ownership");
            if (!isolated) check(GalaxyIndependentRecipeEngine.supports(machine.getRecipeLogic()),
                    id + " registered trait supports independent stellar work with Omega");

            field(link, "active").setBoolean(link, false);
            int nativeThreads = threads(machine, isolated);
            int nativeParallel = IvMachineScope.parallel(machine);
            check(!GalaxyLinks.active(machine) && !GalaxyLinks.unlimited(machine), id + " detached source grants no capacity");
            field(link, "active").setBoolean(link, true);
            int expected = (int)Math.min(Integer.MAX_VALUE, (long)nativeThreads + GalaxyLinks.EXTRA_THREADS);
            for (int arrays : new int[]{0, 63}) {
                field(station, "astralArrayCount").setInt(station, arrays);
                check(GalaxyLinks.active(machine) && GalaxyLinks.crossRecipeEnabledByConnection(machine)
                                && !GalaxyLinks.unlimited(machine) && threads(machine, isolated) == expected,
                        id + " finite source adds exactly 10000 after native Omega threads");
                int expectedParallel = machine instanceof com.gtl.enhancedcore.common.machine.BasicOreProcessingPlantMachine basic
                        ? basic.getParallelForTier() : nativeParallel;
                check(IvMachineScope.parallel(machine) == expectedParallel,
                        id + " finite source retains native per-lane parallel at the effective voltage");
                check(GalaxyWirelessEnergyHandler.forMachine(machine) != null
                                && GalaxyWirelessEnergyHandler.forMachine(machine).isOnline(),
                        id + " Omega retains the linked wireless payment handler");
                if (!isolated) check(GalaxyRecipeBatch.capacity(machine) == (long)expectedParallel * expected,
                        id + " actual independent capacity uses the same thread count");
            }
            field(station, "astralArrayCount").setInt(station, 64);
            check(GalaxyLinks.unlimited(machine) && threads(machine, isolated) == Integer.MAX_VALUE
                            && IvMachineScope.parallel(machine) == Integer.MAX_VALUE,
                    id + " full source takes precedence over Omega and grants both unlimited capacities");
            field(link, "active").setBoolean(link, false);
            check(threads(machine, isolated) == nativeThreads && IvMachineScope.parallel(machine) == nativeParallel,
                    id + " disconnect restores the original Omega thread and parallel counts");
            var row = new LinkedHashMap<String, Object>();
            row.put("machine", id); row.put("isolated", isolated);
            row.put("logic", machine.getRecipeLogic().getClass().getName());
            row.put("omegaThreads", omega.getThreadCount()); row.put("nativeThreads", nativeThreads);
            row.put("finiteThreads", expected); row.put("fullThreads", Integer.MAX_VALUE);
            row.put("nativeParallel", nativeParallel); row.put("disconnectThreads", threads(machine, isolated));
            return row;
        } finally { fixture.holders().forEach(holder -> holder.setLevel(null)); }
    }

    private static int threads(WorkableElectricMultiblockMachine machine, boolean isolated) {
        return isolated ? IvMachineScope.threads(machine) : GalaxyIndependentRecipeEngine.threads(machine.getRecipeLogic());
    }
    private static void setParts(WorkableElectricMultiblockMachine machine, List<IMultiPart> parts) throws Exception {
        field(machine, "parts").set(machine, new ArrayList<>(parts));
        field(machine, "partPositions").set(machine, parts.stream().map(part -> part.self().getPos()).toArray(BlockPos[]::new));
    }
    private static Field field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { var result = type.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
