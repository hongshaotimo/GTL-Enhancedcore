package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Real registered RecipeLogic instances and merged entry points; no player world. */
@Mod("enhancedcore_audit")
public final class PrimitiveRecipeTickChecks {
    private boolean done;
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> machines = new LinkedHashMap<>();
    public PrimitiveRecipeTickChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        checks.add(message);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.primitiveTickAudit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var result = new LinkedHashMap<String, Object>();
        var failures = new ArrayList<String>();
        try {
            check(directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/primitive-ore-tick-crash-20261029"))
                    && Files.isRegularFile(directory.resolve("primitive-tick.marker"))
                    && mc.level == null && mc.getSingleplayerServer() == null, "Marked fixture; no player world opened");
            boolean baseline = Boolean.getBoolean("gtl.primitiveTickBaseline");
            var fixture = StellarIvGuiChecks.fixture("gtceu:mage_assembler", false, false, false);
            var world = (com.lowdragmc.lowdraglib.utils.TrackedDummyWorld)fixture.player().level();
            try {
                var handler = Arrays.stream(RecipeLogic.class.getDeclaredMethods())
                        .filter(method -> method.getName().contains("iv$tick") && method.getParameterCount() == 1).findFirst().orElseThrow();
                handler.setAccessible(true);
                var ids = new LinkedHashSet<>(List.of("gtceu:primitive_void_ore", "gtceu:primitive_blast_furnace", "gtceu:coke_oven", "gtceu:lv_macerator"));
                // Select actual steam IDs rather than assuming a registration naming convention.
                for (var definition : GTRegistries.MACHINES.values()) {
                    String path = definition.getId().getPath();
                    if (path.contains("macerator") && (path.contains("bronze") || path.contains("steel"))
                            || path.contains("steam_grinder")) ids.add(definition.getId().toString());
                }
                check(ids.size() > 4, "Registered steam machine fixtures discovered");
                int index = 0;
                for (var id : ids) {
                    var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
                    check(definition != null, "Fixture registered: " + id);
                    var position = new BlockPos(5 + index++ * 5, 68, 11);
                    var holder = (MetaMachineBlockEntity)definition.getBlockEntityType().create(position, definition.defaultBlockState());
                    holder.setLevel(world); world.blockEntities.put(position, holder);
                    world.renderedBlocks.put(position, new com.lowdragmc.lowdraglib.utils.BlockInfo(definition.defaultBlockState()));
                    try {
                        var machine = holder.getMetaMachine();
                        check(machine instanceof IRecipeLogicMachine && !(machine instanceof WorkableElectricMultiblockMachine), "Actual controller outside electric multiblock scope: " + id);
                        var logic = ((IRecipeLogicMachine)machine).getRecipeLogic();
                        check(!IvMachineScope.crossRecipeEnabled(machine), "Scope excludes actual controller: " + id);
                        var callback = new CallbackInfo("serverTick", true);
                        if (baseline) {
                            boolean reproduced = false;
                            try { handler.invoke(logic, callback); }
                            catch (InvocationTargetException failure) { reproduced = failure.getCause() instanceof ClassCastException; }
                            check(reproduced, "Old global injection reproduces ClassCastException: " + id);
                            machines.put(id, Map.of("controller", machine.getClass().getName(), "baselineClassCast", true));
                            continue;
                        }
                        handler.invoke(logic, callback);
                        check(!callback.isCancelled() && field(logic, "iv$engine").get(logic) == null, "IV injection leaves native tick and engine untouched: " + id);
                        logic.onMachineLoad(); logic.resetRecipeLogic();
                        logic.setWorkingEnabled(false); logic.updateTickSubscription();
                        check(!logic.isWorkingEnabled(), "Native suspend survives load/reset/subscription: " + id);
                        set(logic, "fuelTime", 100);
                        for (int tick = 0; tick < 20; tick++) logic.serverTick();
                        check(logic.getFuelTime() == 80, "20 real serverTick calls retain native fuel countdown: " + id);
                        check(field(logic, "iv$engine").get(logic) == null, "No isolated engine created for native machine: " + id);
                        if (id.equals("gtceu:primitive_void_ore")) {
                            set(machine, "isFormed", true);
                            ((com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine)machine).getMultiblockState().error = null;
                            var recipe = new GTRecipe(null, new ResourceLocation("gtl_enhancedcore", "primitive_tick_fixture"),
                                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(), new CompoundTag(), 100, false);
                            set(logic, "lastRecipe", recipe); set(logic, "duration", 100); set(logic, "progress", 0);
                            logic.setWorkingEnabled(true); set(logic, "status", RecipeLogic.Status.WORKING);
                            for (int tick = 0; tick < 20; tick++) logic.serverTick();
                            check(logic.getProgress() == 20 && logic.getLastRecipe() == recipe, "Real primitive controller advances native working recipe for 20 ticks");
                        }
                        machines.put(id, Map.of("controller", machine.getClass().getName(), "logic", logic.getClass().getName(), "nativeTickPass", true));
                    } catch (Throwable failure) {
                        Throwable cause = failure instanceof InvocationTargetException wrapped ? wrapped.getCause() : failure;
                        failures.add(id + ": " + cause); cause.printStackTrace();
                    } finally { holder.setLevel(null); world.blockEntities.remove(position); world.renderedBlocks.remove(position); }
                }
                if (!baseline) {
                    var electric = fixture.machine();
                    check(IvMachineScope.crossRecipeEnabled(electric), "Real electric machine with super buffer still enables isolation");
                    var callback = new CallbackInfo("serverTick", true);
                    handler.invoke(electric.getRecipeLogic(), callback);
                    check(callback.isCancelled() && field(electric.getRecipeLogic(), "iv$engine").get(electric.getRecipeLogic()) != null,
                            "IV electric controller still enters isolated engine and owns the tick");
                }
            } finally { fixture.holders().forEach(holder -> holder.setLevel(null)); }
            check(failures.isEmpty(), "All controller tick cases pass: " + failures);
            result.put("status", "PASS");
        } catch (Throwable failure) {
            result.put("status", "FAIL"); result.put("error", failure.toString()); failure.printStackTrace();
        }
        result.put("checks", checks); result.put("machines", machines); result.put("failures", failures);
        result.put("scope", "Merged IV injection and native RecipeLogic ticks on actual registered machines in synthetic world; no player save or full production recipe chain");
        try { Files.writeString(directory.resolve("primitive-tick.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object object, String name, Object value) throws Exception { field(object, name).set(object, value); }
}
