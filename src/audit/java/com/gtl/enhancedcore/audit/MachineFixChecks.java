package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Full custom geometry and real external-interface simulation; never shipped in production. */
public final class MachineFixChecks {
    private record Fixture(WorkableElectricMultiblockMachine controller, BlockPos buffer) {}
    private static final List<Fixture> CUSTOM = new ArrayList<>();
    private static int checks;
    private MachineFixChecks() {}

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new IllegalStateException(reason);
    }
    private static Block block(String id) {
        var key = new ResourceLocation(id);
        check(ForgeRegistries.BLOCKS.containsKey(key), "Missing fixture block " + id);
        return ForgeRegistries.BLOCKS.getValue(key);
    }
    public static void setup(MinecraftServer server) throws Exception {
        var world = server.overworld();
        var definitions = List.of(GTLEnhancedcoreMachines.getPlasmaMachineTool(),
                GTLEnhancedcoreMachines.getHadronRefinery(), GTLEnhancedcoreMachines.getQuantumMassArray(),
                GTLEnhancedcoreMachines.getFusionAssembler(), GTLEnhancedcoreMachines.getIntegratedUniversalFactory());
        int index = 0;
        for (var definition : definitions) {
            verifyPorts(definition);
            var shape = definition.getMatchingShapes().getFirst().getBlocks();
            WorkableElectricMultiblockMachine controller = null;
            BlockPos buffer = null;
            for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
                for (int z = 0; z < shape[x][y].length; z++) {
                    var info = shape[x][y][z];
                    if (info == null || info.getBlockState().isAir()) continue;
                    var state = info.getBlockState();
                    if (PartAbility.INPUT_ENERGY.isApplicable(state.getBlock()))
                        state = block("gtceu:zpm_energy_input_hatch").defaultBlockState();
                    if (IvNativeHatches.materials(state.getBlock()))
                        state = block("gtladditions:me_super_pattern_buffer").defaultBlockState();
                    var pos = new BlockPos(3000 + index * 128 + x, 64 + y, z);
                    world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                    world.setBlock(pos, state, 2 | 16);
                    var machine = MetaMachine.getMachine(world, pos);
                    if (machine instanceof WorkableElectricMultiblockMachine electric) controller = electric;
                    if (state.getBlock() == block("gtladditions:me_super_pattern_buffer")) buffer = pos;
                }
            check(controller != null && buffer != null, "Incomplete preview " + definition.getId());
            controller.setWorkingEnabled(false);
            CUSTOM.add(new Fixture(controller, buffer));
            index++;
        }
        for (String path : IvMachineScope.NATIVE_IDS) {
            var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation("gtceu", path));
            verifyPorts(definition);
        }
        IvNativeFixture.setup(world);
        boolean fullNative = Boolean.getBoolean("gtl.enhancedcore.fullNative");
        int expectedNative = IvMachineScope.NATIVE_IDS.size() - (fullNative ? 0 : 3);
        check(IvNativeFixture.MACHINES.size() == expectedNative
                        && IvNativeFixture.BUFFERS.size() == expectedNative,
                "Incomplete native IV fixture set: expected " + expectedNative + " controllers/buffers");
        GTLEnhancedcore.LOGGER.info("[MACHINE_FIX] native_fixtures={} fullNative={} targets={}",
                expectedNative, fullNative, IvNativeFixture.MACHINES.stream()
                        .map(machine -> machine.getDefinition().getId().toString()).sorted().toList());
        SteamLampElevatorChecks.setup(server);
    }
    private static void verifyPorts(MultiblockMachineDefinition definition) {
        boolean ordinary = false, buffer = false, sharedMaterialRule = false;
        var grid = ((BlockPatternAccessor) definition.getPatternFactory().get()).gtlEnhancedcore$getBlockMatches();
        for (var plane : grid) for (var row : plane) for (var rule : row) {
            var predicates = new ArrayList<>(rule.common);
            predicates.addAll(rule.limited);
            for (var predicate : predicates) {
                if (predicate.candidates == null || predicate.candidates.get() == null) continue;
                boolean hasOrdinary = false, hasBuffer = false;
                for (var info : predicate.candidates.get()) {
                    if (info == null) continue;
                    var candidate = info.getBlockState().getBlock();
                    if (IvBufferRegistry.contains(ForgeRegistries.BLOCKS.getKey(candidate))) {
                        buffer = true;
                        hasBuffer = true;
                    } else if (IvNativeHatches.materials(candidate)) {
                        ordinary = true;
                        hasOrdinary = true;
                    }
                }
                if (hasOrdinary && hasBuffer) {
                    sharedMaterialRule = true;
                    check(predicate.minCount == 1,
                            "Material IO rule must require one port from the combined ordinary/super set " + definition.getId());
                }
            }
        }
        check(buffer && ordinary && sharedMaterialRule,
                "Missing shared ordinary/super material port rule " + definition.getId());
    }
    public static boolean ready() {
        return CUSTOM.stream().allMatch(f -> f.controller.isFormed())
                && IvNativeFixture.MACHINES.stream().allMatch(WorkableElectricMultiblockMachine::isFormed)
                && SteamLampElevatorChecks.ready();
    }
    public static void status() {
        GTLEnhancedcore.LOGGER.info("[MACHINE_FIX] custom={} native={}",
                CUSTOM.stream().map(f -> f.controller.getDefinition().getId() + ":" + f.controller.isFormed()).toList(),
                IvNativeFixture.MACHINES.stream().map(m -> m.getDefinition().getId() + ":" + m.isFormed()
                        + (m.isFormed() ? "" : ":" + IvNativeFixture.formationDetails(m))).toList());
        SteamLampElevatorChecks.status();
    }
    public static int run(MinecraftServer server) throws Exception {
        check(ready(), "Machine fixtures are not formed");
        for (var f : CUSTOM) {
            var machine = f.controller;
            var world = (ServerLevel) machine.getLevel();
            var original = world.getBlockState(f.buffer);
            for (String port : List.of("gtceu:lv_input_bus", "gtceu:lv_output_bus",
                    "gtceu:lv_input_hatch", "gtceu:lv_output_hatch", "gtceu:me_mini_pattern_buffer")) {
                replace(f, block(port).defaultBlockState());
                check(machine.isFormed(), "Ordinary port rejected " + port + " on " + machine.getDefinition().getId());
                check(IvBuffers.collect(machine).isEmpty(), "Ordinary port entered isolated processing " + port);
                controllerUi(machine, false);
                if (MetaMachine.getMachine(world, f.buffer) instanceof MEPatternBufferPartMachine plain) {
                    check(plain instanceof IvBufferAccess, "Pattern-buffer base mixin did not publish the compatibility interface");
                    check(IvBuffers.state(plain) == null && !IvBuffers.isolated(plain), "Interface alone hijacked ordinary IO");
                    check(!((IvBufferAccess) plain).iv$isDedicatedDisplay(), "An unregistered pattern buffer retained dedicated display state");
                    Object slot = ((Object[]) plain.getInternalInventory())[0];
                    @SuppressWarnings("unchecked")
                    var stock = (it.unimi.dsi.fastutil.objects.Object2LongMap<AEItemKey>)
                            slot.getClass().getMethod("getItemInventory").invoke(slot);
                    stock.put(AEItemKey.of(new ItemStack(Items.APPLE)), 5);
                    check(stock.values().longStream().sum() == 5, "Native IO was intercepted");
                    stock.clear();
                }
            }
            replace(f, original);
            var buffer = (MEPatternBufferPartMachine) MetaMachine.getMachine(world, f.buffer);
            check(IvBuffers.bind(buffer, machine), "Restored original buffer cannot bind");
            check(IvMachineScope.crossRecipeEnabled(machine),
                    "A super pattern buffer did not enable cross-recipe mode " + machine.getDefinition().getId());
            controllerUi(machine, true);
            checks += IvPatternBufferTransferChecks.run(machine, buffer);
            GTLEnhancedcore.LOGGER.info("[MACHINE_FIX] full_structure_ordinary_ports_and_formed_transfer=OK {}", machine.getDefinition().getId());
        }
        for (int i = 0; i < IvNativeFixture.MACHINES.size(); i++) {
            var machine = IvNativeFixture.MACHINES.get(i);
            check(IvMachineScope.crossRecipeEnabled(machine),
                    "A native super pattern buffer did not enable cross-recipe mode " + machine.getDefinition().getId());
            controllerUi(machine, true);
            machine.setWorkingEnabled(false);
            checks += IvPatternBufferTransferChecks.run(machine, IvNativeFixture.BUFFERS.get(i));
        }
        GTLEnhancedcore.LOGGER.info("[MACHINE_FIX] native_transfer_runs={} targets={}",
                IvNativeFixture.MACHINES.size(), IvNativeFixture.MACHINES.stream()
                        .map(machine -> machine.getDefinition().getId().toString()).sorted().toList());
        compatible(server.overworld());
        SteamLampElevatorChecks.run(server);
        return checks;
    }
    private static void controllerUi(WorkableElectricMultiblockMachine machine, boolean isolated) {
        var widget = machine.createUIWidget();
        check(widget instanceof WidgetGroup, "Controller UI is not a widget group " + machine.getDefinition().getId());
        var buttons = ((WidgetGroup) widget).getWidgetsByType(IvActionButton.class);
        int expected = isolated ? 2 : 0;
        check(buttons.size() == expected, "Controller action button count " + machine.getDefinition().getId()
                + ": expected=" + expected + ", actual=" + buttons.size());
        GTLEnhancedcore.LOGGER.info("[MACHINE_FIX] controller_ui={} mode={} action_buttons={}",
                machine.getDefinition().getId(), isolated ? "cross_recipe" : "ordinary", buttons.size());
    }
    private static void replace(Fixture f, BlockState state) {
        var machine = f.controller;
        var lock = machine.getPatternLock();
        lock.lock();
        try {
            machine.onStructureInvalid();
            machine.getLevel().setBlock(f.buffer, state, 2 | 16);
            check(machine.checkPattern(), "Formation failed after port replacement: " + state);
            machine.onStructureFormed();
        } finally { lock.unlock(); }
    }
    private static void compatible(ServerLevel world) {
        var id = new ResourceLocation("gtceu", "me_mini_pattern_buffer");
        var predicate = IvBufferRegistry.predicate();
        check(!IvBufferRegistry.contains(id), "Compatibility test ID already registered");
        check(IvBufferRegistry.register(id), "First registration failed");
        check(!IvBufferRegistry.register(block(id.toString())), "Duplicate block registration changed the registry");
        check(java.util.Arrays.stream(predicate.common.getFirst().candidates.get())
                .anyMatch(info -> info.getBlockState().getBlock() == block(id.toString())), "Old predicate lost dynamic registration");
        try { IvBufferRegistry.registeredIds().clear(); throw new IllegalStateException("Mutable registry snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
        var f = CUSTOM.getFirst();
        var original = world.getBlockState(f.buffer);
        replace(f, block(id.toString()).defaultBlockState());
        var buffer = (MEPatternBufferPartMachine) MetaMachine.getMachine(world, f.buffer);
        check(IvBuffers.compatible(buffer) && IvBuffers.collect(f.controller).contains(buffer), "Registered external buffer not collected");
        check(!((IvBufferAccess) buffer).iv$isFoaEnabled(), "FOA default blocks buffers without FOA");
        check(IvBuffers.state(buffer) == IvBuffers.state(buffer), "Registered buffer state is not stable per instance");
        check(IvBuffers.bind(buffer, f.controller), "Registered external buffer cannot bind");
        check(!buffer.canShared(), "Registered external buffer can share an IV controller");
        var identity = IvBuffers.state(buffer).identity;
        var owner = IvBuffers.state(buffer).owner;
        var key = AEItemKey.of(new ItemStack(Items.APPLE));
        var pattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(key, 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(new ItemStack(Items.GOLD_INGOT)), 1)});
        PatternQuickUploadMetadata.writeRecipeTypeId(pattern, f.controller.getRecipeTypes()[0].registryName);
        buffer.getPatternInventory().setStackInSlot(0, pattern);
        ((IvBufferMethods) buffer).iv$patternChanged(0);
        var routed = ((IvBufferMethods) buffer).iv$realPattern(0, pattern);
        check(routed != null && routed.getDefinition().getTag().contains(IvBuffers.ROUTE_KEY), "External buffer did not route pattern");
        // A real recipe from the existing catalog avoids inventing a second scheduling mechanism.
        var recipes = world.getRecipeManager().getAllRecipesFor(f.controller.getRecipeTypes()[0]);
        check(!recipes.isEmpty(), "Real recipe catalog missing");
        CompoundTag saved = new CompoundTag();
        buffer.saveCustomPersistedData(saved, false);
        check(saved.contains(IvBuffers.SAVE_KEY), "External ledger did not persist");
        buffer.loadCustomPersistedData(saved);
        check(identity.equals(IvBuffers.state(buffer).identity) && owner.equals(IvBuffers.state(buffer).owner), "External identity/owner changed after NBT reload");
        var tips = new ArrayList<Component>();
        f.controller.getDefinition().getTooltipBuilder().accept(f.controller.getDefinition().asStack(), tips);
        check(tips.stream().anyMatch(line -> Component.Serializer.toJson(line).contains("iv_only_super_buffer")), "Runtime buffer requirement missing from tips");
        replace(f, original);
        GTLEnhancedcore.LOGGER.info("[MACHINE_FIX] external_registered_interface_route_NBT_default_FOA_and_interface_only_negative=OK");
    }
}
