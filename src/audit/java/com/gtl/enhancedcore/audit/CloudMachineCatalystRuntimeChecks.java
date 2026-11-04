package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Real independent-JVM SavedData, production inventory and recipe admission checks. */
public final class CloudMachineCatalystRuntimeChecks {
    private static final UUID OWNER = UUID.fromString("5368d5c6-924e-4a86-b948-2da56fcda926");
    private static final String ASSEMBLER = "gtceu:circuit_assembly_line", OTHER = "gtceu:pyrolyse_oven";
    private CloudMachineCatalystRuntimeChecks() {}
    static void check(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
    static CompoundTag stock(Item item, int amount) {
        var state = new CompoundTag(); var items = CloudLibraryService.itemContents(state);
        items.setStackInSlot(0, new ItemStack(item, amount)); state.put("items", items.serializeNBT());
        var fluids = CloudLibraryService.fluidContents(state);
        fluids.fill(FluidStack.create(Fluids.WATER, 1000), false); state.put("fluids", fluids.serializeNBT());
        return state;
    }
    static void runServer(MinecraftServer server) {
        var checks = new ArrayList<String>(); var report = new LinkedHashMap<String, Object>();
        String phase = System.getProperty("gtl.enhancedcore.machineCatalystPhase", "seed");
        Path directory = server.getServerDirectory().toPath().toAbsolutePath().normalize();
        try {
            Path root = Path.of(System.getProperty("gtl.enhancedcore.auditRoot")).toRealPath();
            check(directory.toRealPath().startsWith(root) && !directory.toRealPath().equals(root)
                    && Files.isRegularFile(directory.resolve("machine-catalyst.marker"))
                    && server.getWorldPath(LevelResource.ROOT).toRealPath().startsWith(directory),
                    "Marked disposable server and fresh task-owned world", checks);
            runStore(server.overworld(), phase, checks);
            report.put("status", "PASS");
        } catch (Throwable failure) {
            failure.printStackTrace(); report.put("status", "FAIL"); report.put("error", failure.toString());
        }
        report.put("phase", phase); report.put("checks", checks);
        report.put("scope", "Real SavedData restart, real library menu transfer and recipe admission; controlled formed field, no full structure build or live AE CPU scheduling");
        try { Files.writeString(directory.resolve("machine-catalyst-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { throw new RuntimeException(failure); }
        server.halt(false);
    }
    private static void runStore(ServerLevel level, String phase, List<String> checks) throws Exception {
        var store = CloudPatternStore.get(level); UUID id = store.defaultLibrary(OWNER); var library = store.library(id);
        Path reference = level.getServer().getWorldPath(LevelResource.ROOT).resolve("machine-catalyst-reference.dat");
        if (phase.equals("seed")) {
            check(!Files.exists(reference), "Seed starts without a reference from a prior world", checks);
            var a = stock(Items.APPLE, 1); var b = stock(Items.DIAMOND, 2);
            library.setMachineCatalysts(OWNER, ASSEMBLER, a); library.setMachineCatalysts(OWNER, OTHER, b);
            a.getCompound("items").putInt("Size", 0);
            check(CloudLibraryService.itemContents(library.machineCatalysts(ASSEMBLER)).getSlots() == 9,
                    "Setter copies the submitted inventory", checks);
            var read = library.machineCatalysts(ASSEMBLER); read.remove("items");
            check(library.machineCatalysts(ASSEMBLER).contains("items"), "Getter cannot mutate authority", checks);
            check(CloudLibraryService.itemContents(library.machineCatalysts(OTHER)).getStackInSlot(0).getCount() == 2
                    && CloudLibraryService.itemContents(library.machineCatalysts(ASSEMBLER)).getStackInSlot(0).is(Items.APPLE),
                    "Two machine definitions retain independent physical item inventories", checks);
            check(CloudLibraryService.fluidContents(library.machineCatalysts(ASSEMBLER)).getTanks() == 9
                    && CloudLibraryService.machineCatalysts(level, id, ASSEMBLER).get(AEFluidKey.of(Fluids.WATER)) == 1000,
                    "Nine real fluid tanks preserve exact shared fluid stock", checks);
            UUID outsider = UUID.randomUUID(); boolean denied = false;
            try { library.setMachineCatalysts(outsider, ASSEMBLER, new CompoundTag()); } catch (SecurityException expected) { denied = true; }
            check(denied && !library.machineCatalysts(ASSEMBLER).isEmpty(), "Unauthorized actor cannot clear machine catalyst stock", checks);
            library.grant(OWNER, outsider, true); library.setMachineCatalysts(outsider, OTHER, b);
            check(library.authorized(outsider), "Authorized delegate can edit machine catalyst stock", checks);
            library.grant(OWNER, outsider, false);
            var second = store.library(store.defaultLibrary(UUID.randomUUID()));
            check(second.machineCatalysts(ASSEMBLER).isEmpty(), "Same machine definition in another library has no borrowed stock", checks);
            var entries = new ListTag();
            for (int count : new int[]{1, 2}) {
                ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2)},
                        new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), count)});
                UUID op = UUID.randomUUID(); var entry = store.upload(id, OWNER, pattern, new CompoundTag(), op);
                store.acknowledge(id, OWNER, op); entries.add(NbtUtils.createUUID(entry.id()));
                check(entry.catalysts().isEmpty(), "Original " + count + " owns no duplicate physical catalyst", checks);
            }
            var ref = new CompoundTag(); ref.putUUID("library", id); ref.put("entries", entries);
            ref.put("assembler", library.machineCatalysts(ASSEMBLER)); ref.put("other", library.machineCatalysts(OTHER));
            store.checkpoint(); NbtIo.writeCompressed(ref, reference.toFile());
            check(Files.isRegularFile(reference), "Seed flushed actual cloud partitions and restart reference", checks);
        } else if (phase.equals("verify")) {
            check(Files.isRegularFile(reference), "Reference exists from a separate completed JVM", checks);
            var ref = NbtIo.readCompressed(reference.toFile());
            check(ref.getUUID("library").equals(id) && ref.getCompound("assembler").equals(library.machineCatalysts(ASSEMBLER))
                    && ref.getCompound("other").equals(library.machineCatalysts(OTHER)),
                    "Independent Forge restart restores exact machine-keyed item and fluid NBT", checks);
            check(library.machineCatalystDefinitions().containsAll(Set.of(ASSEMBLER, OTHER)), "Machine inventory directory survives restart", checks);
            check(library.entries().size() == 2 && library.entries().stream().allMatch(e -> e.catalysts().isEmpty()),
                    "Two originals survive restart without per-pattern copies of one shared robot", checks);
            for (String definition : List.of(ASSEMBLER, OTHER)) {
                var state = library.machineCatalysts(definition);
                check(CloudLibraryService.machineCatalysts(level, id, definition).equals(com.gtl.enhancedcore.common.cloud.transfer.CloudCatalystSource.snapshot(
                        CloudLibraryService.itemContents(state), CloudLibraryService.fluidContents(state))),
                        "Production machine snapshot reads only requested definition " + definition, checks);
            }
            executionStock(level, id, checks);
            menu(level, checks);
            store.checkpoint();
        } else throw new IllegalArgumentException("Unknown audit phase " + phase);
    }
    private static void executionStock(ServerLevel level, UUID libraryId, List<String> checks) throws Exception {
        var bufferHolder = place(level, "gtl_enhancedcore:cloud_pattern_buffer", new BlockPos(8, 90, 0));
        var buffer = (CloudPatternBufferMachine)bufferHolder.getMetaMachine();
        var first = (com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine)place(level, ASSEMBLER, new BlockPos(9, 90, 0)).getMetaMachine();
        var same = (com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine)place(level, ASSEMBLER, new BlockPos(10, 90, 0)).getMetaMachine();
        var other = (com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine)place(level, OTHER, new BlockPos(11, 90, 0)).getMetaMachine();
        for (var controller : List.of(first, same, other)) {
            set(controller, "isFormed", true); set(controller, "parts", new ArrayList<>(List.of(buffer)));
            set(controller, "partPositions", new BlockPos[]{buffer.getPos()});
            ((com.gtl.enhancedcore.mixin.gtceu.MultiblockPartControllerPositionsAccessor)buffer).enhancedcore$getControllerPositions().add(controller.getPos());
        }
        set(buffer, "attached", true); set(buffer, "libraryId", libraryId);
        try {
        var store = CloudPatternStore.get(level); var library = store.library(libraryId);
        var actualDefinitions = buffer.cloudMachineDefinitions();
        check(actualDefinitions.containsAll(Set.of(ASSEMBLER, OTHER)), "One physical endpoint discovers three real formed executors across two definitions: actual=" + actualDefinitions
                + " positions=" + ((com.gtl.enhancedcore.mixin.gtceu.MultiblockPartControllerPositionsAccessor)buffer).enhancedcore$getControllerPositions()
                + " controllers=" + List.of(first, same, other).stream().map(m -> m.getDefinition().getId() + ":formed=" + m.isFormed()
                        + ":target=" + com.gtl.enhancedcore.common.recipe.iv.IvBuffers.targetController(m)
                        + ":parts=" + m.getParts().size() + ":chunk=" + (level.getChunkSource().getChunkNow(m.getPos().getX() >> 4, m.getPos().getZ() >> 4) != null)
                        + ":holder=" + (level.getBlockEntity(m.getPos()) == m.getHolder().getSelf())).toList(), checks);
        check(buffer.cloudMachineCatalysts(first).equals(buffer.cloudMachineCatalysts(same)), "Two controllers of one definition read exactly one machine-owned robot", checks);
        check(buffer.cloudMachineCatalysts(first).containsKey(AEItemKey.of(Items.APPLE))
                && !buffer.cloudMachineCatalysts(other).containsKey(AEItemKey.of(Items.APPLE))
                && buffer.cloudMachineCatalysts(other).get(AEItemKey.of(Items.DIAMOND)) == 2,
                "Different executors sharing one endpoint cannot borrow each other's robot", checks);
        for (var original : library.entries()) {
            var a = buffer.cloudProbe(original, first); var b = buffer.cloudProbe(original, other);
            check(a != null && b != null && a.virtual().containsKey(AEItemKey.of(Items.APPLE))
                    && !b.virtual().containsKey(AEItemKey.of(Items.APPLE)) && b.virtual().containsKey(AEItemKey.of(Items.DIAMOND)),
                    "Production cloudProbe builds independent machine context for original " + original.id(), checks);
        }
        var recipe = first.getDefinition().getRecipeTypes()[0].recipeBuilder("machine_catalyst_live_batch").inputItems(Items.IRON_INGOT, 2)
                .notConsumable(Items.APPLE).outputItems(Items.GOLD_INGOT).duration(20).EUt(32).buildRawRecipe();
        var inputs = Map.<AEKey, Long>of(AEItemKey.of(Items.IRON_INGOT), 16L); var outputs = Map.<AEKey, Long>of(AEItemKey.of(Items.GOLD_INGOT), 1L);
        var job = new com.gtl.enhancedcore.common.recipe.iv.IvJob(0, recipe, new CompoundTag(), inputs, Map.of(), 8);
        var saved = library.machineCatalysts(ASSEMBLER);
        var firstBatch = com.gtl.enhancedcore.common.recipe.iv.IvBuffers.availableVirtual(buffer, job, first);
        check(IvRecipeInputs.patternOperations(recipe, job.inventory, firstBatch, outputs) == 8,
                "Actual next-batch virtual resolver admits eight operations using one shared physical robot", checks);
        check(library.machineCatalysts(ASSEMBLER).equals(saved), "Reading next-batch stock never consumes the shared robot", checks);
        library.setMachineCatalysts(OWNER, ASSEMBLER, new CompoundTag());
        var afterRemoval = com.gtl.enhancedcore.common.recipe.iv.IvBuffers.availableVirtual(buffer, job, first);
        check(!afterRemoval.containsKey(AEItemKey.of(Items.APPLE)) && IvRecipeInputs.patternOperations(recipe, job.inventory, afterRemoval, outputs) == 0,
                "Removing shared robot is observed by actual next-batch resolver and blocks strict admission", checks);
        library.setMachineCatalysts(OWNER, ASSEMBLER, stock(Items.DIAMOND, 1));
        check(buffer.cloudProbe(library.entries().getFirst(), first).virtual().containsKey(AEItemKey.of(Items.DIAMOND))
                && !buffer.cloudProbe(library.entries().getFirst(), first).virtual().containsKey(AEItemKey.of(Items.APPLE)),
                "Replacing one shared robot refreshes production probe for existing originals", checks);
        library.setMachineCatalysts(OWNER, ASSEMBLER, saved);
        // No world tick runs during this controlled fixture; detach before the subsequent actual menu checks.
        } finally { set(buffer, "attached", false); }
    }
    private static MetaMachineBlockEntity place(ServerLevel level, String id, BlockPos pos) {
        level.getChunkAt(pos); var definition = Objects.requireNonNull(GTRegistries.MACHINES.get(new ResourceLocation(id)));
        level.setBlockAndUpdate(pos, definition.defaultBlockState()); return (MetaMachineBlockEntity)level.getBlockEntity(pos);
    }
    static void matching(List<String> checks) throws Exception {
        var fixture = StellarIvGuiChecks.fixture(ASSEMBLER, true, false, false);
        try {
            var machine = fixture.machine(); var type = machine.getDefinition().getRecipeTypes()[0];
            var iron = AEItemKey.of(Items.IRON_INGOT); var gold = AEItemKey.of(Items.GOLD_INGOT);
            var oldRobot = AEItemKey.of(Items.APPLE); var upgradedRobot = AEItemKey.of(Items.DIAMOND);
            var before = stock(Items.APPLE, 1); var after = stock(Items.DIAMOND, 1);
            for (int output : new int[]{1, 2}) {
                var old = type.recipeBuilder("machine_catalyst_old_" + output).inputItems(Items.IRON_INGOT, 2).notConsumable(Items.APPLE)
                        .outputItems(Items.GOLD_INGOT, output).duration(40).EUt(32).buildRawRecipe();
                var upgraded = type.recipeBuilder("machine_catalyst_new_" + output).inputItems(Items.IRON_INGOT, 2).notConsumable(Items.DIAMOND)
                        .outputItems(Items.GOLD_INGOT, output).duration(20).EUt(128).buildRawRecipe();
                for (boolean upgrade : new boolean[]{false, true}) {
                    var state = upgrade ? after : before;
                    var virtual = com.gtl.enhancedcore.common.cloud.transfer.CloudCatalystSource.snapshot(CloudLibraryService.itemContents(state), CloudLibraryService.fluidContents(state));
                    var probe = new CloudPatternMatching.Probe(Map.of(iron, 16L), virtual, Map.of(gold, (long)output));
                    var matches = new CloudPatternMatching.MatchSet(); matches.add(old, machine, probe); matches.add(upgraded, machine, probe);
                    check(matches.unique() == (upgrade ? upgraded : old), "One machine robot selects " + (upgrade ? "upgraded" : "original") + " cost for original " + output, checks);
                    check(IvRecipeInputs.patternOperations(matches.unique(), probe.inputs(), virtual, probe.outputs()) == 8,
                            "One physical robot authorizes eight operations without multiplication for original " + output, checks);
                    check(state.equals(upgrade ? stock(Items.DIAMOND, 1) : stock(Items.APPLE, 1)), "Admission does not consume robot or fluid stock", checks);
                    check(IvRecipeInputs.patternOperations(matches.unique(), probe.inputs(), Map.of(), probe.outputs()) == 0,
                            "Removing the machine robot blocks strict admission of the next batch", checks);
                }
            }
            check(before.getCompound("items").equals(stock(Items.APPLE, 1).getCompound("items")) && !oldRobot.equals(upgradedRobot),
                    "All shared-original matching checks leave physical stock unchanged", checks);
        } finally { fixture.holders().forEach(h -> h.setLevel(null)); }
    }
    private static void menu(ServerLevel level, List<String> checks) throws Exception {
        var pos = new BlockPos(0, 90, 0); level.getChunkAt(pos);
        var definition = Objects.requireNonNull(GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore:cloud_pattern_library")));
        level.setBlockAndUpdate(pos, definition.defaultBlockState());
        var holder = (MetaMachineBlockEntity)level.getBlockEntity(pos); var main = (CloudPatternLibraryMachine)holder.getMetaMachine();
        UUID menuOwner = UUID.fromString("8e0ca846-2048-4ca4-9a23-ff3d83a96609");
        var player = FakePlayerFactory.get(level, new GameProfile(menuOwner, "MachineCatalystAudit")); player.setPos(.5, 90.5, .5);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        main.onMachinePlaced(player, definition.asStack()); set(main, "isFormed", true);
        var ui = main.createUI(player); ui.initWidgets(); var container = new ModularUIContainer(ui, 79); player.containerMenu = container;
        var panel = ui.getFirstWidgetById("cloud.library");
        check(panel.getGui() != null && panel.getGui().entityPlayer == player,
                "Panel retains its actual server UI and FakePlayer: gui=" + (panel.getGui() != null)
                        + ":serverPlayer=" + (panel.getGui() != null && panel.getGui().entityPlayer instanceof net.minecraft.server.level.ServerPlayer), checks);
        check(main.menuAuthorized(player), "Real menu authorization prerequisites: formed=" + main.isFormed() + ":sameLevel=" + (player.serverLevel() == level)
                + ":library=" + main.libraryId() + ":owner=" + main.owner() + ":actor=" + player.getUUID()
                + ":holder=" + (level.getBlockEntity(pos) == main.getHolder().getSelf()) + ":interact=" + level.mayInteract(player, pos)
                + ":runtime=" + CloudLibraryService.runtimeAuthorized(player.getUUID(), main.libraryId(), level, pos)
                + ":authorized=" + CloudLibraryService.authorized(player, main.libraryId(), main)
                + ":distance=" + player.distanceToSqr(.5, 90.5, .5) + ":uiHolder=" + (ui.holder == main), checks);
        var library = CloudPatternStore.get(level).library(main.libraryId());
        library.setMachineCatalysts(menuOwner, ASSEMBLER, stock(Items.IRON_INGOT, 10));
        library.setMachineCatalysts(menuOwner, OTHER, stock(Items.DIAMOND, 2));
        action(panel, "machine_config", library.revision());
        var snapshot = main.cloudUiSnapshot(player, "", "", 0, "");
        check(snapshot.getBoolean("patternCatalystsMachine"), "Real server menu opens the machine-shared inventory: snapshot=" + snapshot, checks);
        String selected = snapshot.getString("catalystMachineDefinition"), handle = snapshot.getString("catalystEntry");
        check(!selected.isEmpty() && !handle.isEmpty(), "Server issues a selected machine and inventory handle", checks);
        // The initial directory order is authoritative; set stock only after reading the selected definition.
        library.setMachineCatalysts(menuOwner, selected, stock(Items.IRON_INGOT, 10));
        action(panel, "machine_config", library.revision());
        snapshot = main.cloudUiSnapshot(player, "", "", 0, ""); handle = snapshot.getString("catalystEntry");
        var widget = ui.getFirstWidgetById("cloud.catalyst.0");
        var slot = ui.getSlotMap().entrySet().stream().filter(e -> e.getValue() == widget).findFirst().orElseThrow().getKey();
        container.setCarried(ItemStack.EMPTY); container.clicked(slot.index, 0, ClickType.PICKUP, player);
        check(container.getCarried().getCount() == 10 && CloudLibraryService.itemContents(library.machineCatalysts(selected)).getStackInSlot(0).isEmpty(),
                "Native server cursor extracts ten shared catalysts once", checks);
        container.clicked(slot.index, 0, ClickType.PICKUP, player);
        check(container.getCarried().isEmpty() && CloudLibraryService.itemContents(library.machineCatalysts(selected)).getStackInSlot(0).getCount() == 10,
                "Native cursor return conserves and persists all ten shared catalysts", checks);
        var selection = handle + ":" + snapshot.getLong("catalystAccessEpoch");
        var old = main.cloudPatternItemCatalysts(player, selection); old.getStackInSlot(0);
        var oldFluid = main.cloudPatternFluidCatalysts(player, selection);
        var drained = oldFluid.drain(0, FluidStack.create(Fluids.WATER, 250), false, true);
        check(drained.getAmount() == 250 && CloudLibraryService.fluidContents(library.machineCatalysts(selected)).getFluidInTank(0).getAmount() == 750,
                "Real menu fluid handler extracts exactly 250 shared fluid units", checks);
        check(oldFluid.fill(0, drained, false, true) == 250 && CloudLibraryService.fluidContents(library.machineCatalysts(selected)).getFluidInTank(0).getAmount() == 1000,
                "Real menu fluid return conserves and persists shared fluid stock", checks);
        var playerSlot = container.slots.stream().filter(s -> s.container == player.getInventory() && s.getContainerSlot() == 9).findFirst().orElseThrow();
        player.getInventory().clearContent(); player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 8));
        container.quickMoveStack(player, playerSlot.index);
        check(player.getInventory().getItem(9).isEmpty() && CloudLibraryService.itemContents(library.machineCatalysts(selected)).getStackInSlot(0).getCount() == 18,
                "Native Shift transfer adds eight items to shared stock and conserves the total", checks);
        long oldEpoch = snapshot.getLong("catalystAccessEpoch");
        action(panel, "machine_catalysts_next", library.revision(), oldEpoch);
        var next = main.cloudUiSnapshot(player, "", "", 0, "");
        check(!next.getString("catalystMachineDefinition").equals(selected) && old.extractItem(0, 1, false, true).isEmpty(),
                "Changing machine definition revokes the old real-menu inventory handle", checks);
        check(oldFluid.drain(0, FluidStack.create(Fluids.WATER, 1), false, true).isEmpty(), "Machine cycle also revokes the former fluid handle", checks);
        action(panel, "machine_catalysts_next", library.revision(), oldEpoch);
        check(main.cloudUiSnapshot(player, "", "", 0, "").getString("catalystMachineDefinition").equals(next.getString("catalystMachineDefinition")),
                "Replayed machine cycle with stale epoch cannot change the active definition", checks);
        var currentHandle = next.getString("catalystEntry") + ":" + next.getLong("catalystAccessEpoch");
        var beforeSwitch = main.cloudPatternItemCatalysts(player, currentHandle); beforeSwitch.getStackInSlot(0);
        UUID originalLibrary = main.libraryId();
        UUID otherOwner = UUID.randomUUID(); var otherLibrary = CloudPatternStore.get(level).library(CloudPatternStore.get(level).defaultLibrary(otherOwner));
        otherLibrary.grant(otherOwner, menuOwner, true);
        var oldContents = library.machineCatalysts(next.getString("catalystMachineDefinition"));
        set(main, "libraryId", otherLibrary.id());
        check(beforeSwitch.extractItem(0, 1, false, true).isEmpty()
                && library.machineCatalysts(next.getString("catalystMachineDefinition")).equals(oldContents),
                "Changing the endpoint library without closing its menu revokes old physical inventory writes", checks);
        // A central library never changes libraries in production. The child switch path
        // explicitly calls this production invalidator before changing its library address.
        CloudPatternInventories.invalidate(main);
        set(main, "libraryId", originalLibrary);
        check(beforeSwitch.extractItem(0, 1, false, true).isEmpty(), "Explicit production invalidation permanently revokes the old selection when a former library is restored", checks);
        action(panel, "close_pattern_config", library.revision());
        check(old.extractItem(0, 1, false, true).isEmpty(), "Closing machine configuration never reactivates a stale handle", checks);
        container.setCarried(ItemStack.EMPTY); player.containerMenu = player.inventoryMenu;
    }
    private static void action(Widget panel, String name, long revision) {
        action(panel, name, revision, 0);
    }
    private static void action(Widget panel, String name, long revision, long epoch) {
        var payload = new CompoundTag(); payload.putLong("revision", revision); payload.putLong("catalystAccessEpoch", epoch);
        payload.putString("type", "gtceu:circuit_assembly_line");
        var packet = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.writeUtf(name, 32); packet.writeNbt(payload); panel.handleClientAction(211, packet); }
        finally { packet.release(); }
    }
    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
