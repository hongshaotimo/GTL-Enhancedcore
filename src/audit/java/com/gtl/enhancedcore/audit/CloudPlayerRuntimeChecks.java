package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in real structure, wireless terminal and native catalyst-menu checks in a marked isolated fixture. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class CloudPlayerRuntimeChecks {
    private static final BlockPos ACCESS_A = new BlockPos(640, 100, 640);
    private static final BlockPos ACCESS_B = ACCESS_A.south(7);
    private static final BlockPos ACCESS_UNOWNED = ACCESS_A.east(7);
    private static final BlockPos CORE = new BlockPos(720, 100, 640);
    private static final List<String> CHECKS = new ArrayList<>();
    private static ServerLevel level;
    private static FakePlayer player;
    private static CloudPatternLibraryMachine main;
    private static ItemStack terminalA, terminalB;
    private static CompoundTag persistedBinding;
    private static String persistedBindingKey;
    private static String stage = "inactive";
    private static long started;
    private static int ticks, stageTick;
    private static boolean universal;
    private CloudPlayerRuntimeChecks() {}
    public static void begin(ServerLevel world) {
        level = world; started = System.nanoTime(); ticks = 0; CHECKS.clear(); stage = "networks";
        universal = Boolean.getBoolean("gtl.cloudUniversalAudit");
        player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("2af331d4-3cf1-4f3e-b5cc-9b3968bfd86f"), "CloudPlayerAudit"));
        player.setGameMode(GameType.CREATIVE); player.setPos(CORE.getX() + .5, CORE.getY() + .5, CORE.getZ() - 1);
        player.getInventory().clearContent();
        level.setChunkForced(ACCESS_A.getX() >> 4, ACCESS_A.getZ() >> 4, true);
        level.setChunkForced(CORE.getX() >> 4, CORE.getZ() >> 4, true);
        buildNetwork(ACCESS_A); buildNetwork(ACCESS_B);
        if (universal) buildNetwork(ACCESS_UNOWNED);
        terminalA = terminal(ACCESS_A); terminalB = terminal(ACCESS_B);
    }
    private static void buildNetwork(BlockPos position) {
        require(level.isEmptyBlock(position) && level.isEmptyBlock(position.south()), "AE network positions are empty fixture-owned blocks " + position);
        var state = AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState();
        level.setBlock(position, state, 3);
        var access = (WirelessAccessPointBlockEntity) level.getBlockEntity(position);
        // The native access point exposes only its orientation's BACK side.
        Direction back = access.getOrientation().getSide(appeng.api.orientation.RelativeSide.BACK);
        BlockPos cellPosition = position.relative(back);
        require(level.isEmptyBlock(cellPosition), "Creative cell is outside other network at " + cellPosition);
        level.setBlock(cellPosition, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var cell = (CreativeEnergyCellBlockEntity) level.getBlockEntity(cellPosition);
        if (!universal || !position.equals(ACCESS_UNOWNED)) {
            var recordedOwner = universal && position.equals(ACCESS_A)
                    ? FakePlayerFactory.get(level, new GameProfile(UUID.fromString("51477dfc-94a1-42ba-8596-a080a57af349"), "OtherNodeCreator")) : player;
            access.getMainNode().setOwningPlayer(recordedOwner); cell.getMainNode().setOwningPlayer(recordedOwner);
        }
        access.getInternalInventory().setItemDirect(0, AEItems.WIRELESS_BOOSTER.stack(64));
    }
    private static ItemStack terminal(BlockPos position) {
        ItemStack stack = AEItems.WIRELESS_TERMINAL.stack();
        if (universal) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("ae2wtlib:wireless_universal_terminal"));
            require(item != null, "Actual ae2wtlib:wireless_universal_terminal is registered");
            stack = new ItemStack(item);
            try {
                var terminals = (Map<?, ?>)Class.forName("de.mari_023.ae2wtlib.wut.WUTHandler").getField("wirelessTerminals").get(null);
                String mode = terminals.keySet().iterator().next().toString();
                stack.getOrCreateTag().putString("currentTerminal", mode); stack.getOrCreateTag().putBoolean(mode, true);
            } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
        }
        var item = (WirelessTerminalItem) stack.getItem();
        WirelessTerminalItem.LINKABLE_HANDLER.link(stack, GlobalPos.of(level.dimension(), position));
        item.injectAEPower(stack, item.getAEMaxPower(stack), Actionable.MODULATE);
        require(item.getLinkedPosition(stack).equals(GlobalPos.of(level.dimension(), position)) && item.hasPower(player, .5, stack),
                "Actual native terminal has a real link and charged battery " + position);
        return stack;
    }
    private static WirelessAccessPointBlockEntity access(BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(position) instanceof WirelessAccessPointBlockEntity access ? access : null;
    }
    private static CloudPatternLibraryMachine place(BlockPos position) {
        require(level.isEmptyBlock(position), "Controller position is empty fixture-owned block " + position);
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_library"));
        level.setBlock(position, definition.defaultBlockState(), 3);
        var result = (CloudPatternLibraryMachine) MetaMachine.getMachine(level, position);
        result.setFrontFacing(Direction.NORTH); result.onMachinePlaced(player, ItemStack.EMPTY); return result;
    }
    private static void next(String value) { stage = value; stageTick = ticks; }
    private static CompoundTag savedWirelessBinding() {
        // The LDLib BlockEntity mixin invokes saveManagedPersistentData from the real
        // vanilla saveAdditional hook. MetaMachine.saveCustomPersistedData alone
        // only writes extension/trait data and does not serialize @Persisted refs.
        var holder = main.getHolder();
        var references = Arrays.stream(holder.getRootStorage().getPersistedFields())
                .filter(ref -> ref.getKey().getName().equals("wirelessBinding")).toList();
        require(references.size() == 1, "Actual holder registers exactly one persisted wirelessBinding field");
        persistedBindingKey = references.getFirst().getPersistedKey();
        CompoundTag saved = holder.getSelf().saveWithFullMetadata();
        var tag = com.lowdragmc.lowdraglib.utils.TagUtils.getTagExtended(saved, persistedBindingKey);
        require(tag instanceof CompoundTag, "Real holder BE save includes the managed wireless address at " + persistedBindingKey);
        CompoundTag dropped = new CompoundTag(); holder.saveManagedPersistentData(dropped, true);
        require(com.lowdragmc.lowdraglib.utils.TagUtils.getTagExtended(dropped, persistedBindingKey) == null,
                "Actual managed drop serialization excludes the bound wireless address");
        return ((CompoundTag)tag).copy();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || level == null || stage.equals("done")) return;
        try {
            ticks++;
            if (ticks % 200 == 0) com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info(
                    "[CLOUD_PLAYER] stage={} tick={} mainFormed={} remoteLoaded={} service={}", stage, ticks,
                    main != null && main.isFormed(), access(ACCESS_A) != null, main == null ? null : main.serviceGrid());
            if (ticks > 2400) throw new AssertionError("Player audit timed out stage=" + stage + " mainError=" + (main == null ? "none" : main.getMultiblockState().error));
            switch (stage) {
                case "networks" -> {
                    var a = access(ACCESS_A); var b = access(ACCESS_B);
                    if (a == null || b == null || !a.isActive() || !b.isActive()) return;
                    IGrid gridA = a.getGrid(), gridB = b.getGrid();
                    if (gridA == null || gridB == null) return;
                    require(gridA != gridB, "Two native access points and creative cells form separate actual AE grids");
                    if (universal) {
                        require(!a.getActionableNode().getOwningPlayerProfileId().equals(player.getUUID()), "Universal terminal network node records a different player than the authorized terminal user");
                        var unowned = access(ACCESS_UNOWNED);
                        if (unowned == null || !unowned.isActive()) return;
                        require(unowned.getActionableNode().getOwningPlayerProfileId() == null, "Actual automation-created AE node has no recorded player");
                        var unownedResult = CloudWirelessBinding.find(player, terminal(ACCESS_UNOWNED));
                        require(unownedResult.state() == CloudWirelessBinding.State.READY && unownedResult.grid() == unowned.getGrid(), "Charged linked universal terminal binds an unowned actual network node");
                        var otherOwner = CloudWirelessBinding.find(player, terminalA);
                        require(otherOwner.state() == CloudWirelessBinding.State.READY && otherOwner.grid() == gridA, "Charged linked universal terminal binds a network node created by another player");
                    } else require(a.getActionableNode().getOwningPlayerProfileId().equals(player.getUUID())
                                    && b.getActionableNode().getOwningPlayerProfileId().equals(player.getUUID()),
                            "Actual AE access nodes retain the placing player's identity");
                    require(((WirelessTerminalItem)terminalA.getItem()).getLinkedGrid(terminalA, level, null) == gridA,
                            "Actual native getLinkedGrid resolves access point A");
                    var missing = place(CORE.south(8));
                    require(missing.libraryId() == null && missing.serviceGrid() == null,
                            "Actual placed controller without a terminal creates no service library");
                    level.removeBlock(missing.getPos(), false);
                    player.getInventory().setItem(0, terminalA); player.getInventory().setItem(1, terminalB);
                    require(CloudWirelessBinding.find(player, null).state() == CloudWirelessBinding.State.CONFLICT,
                            "Two real linked terminal networks reject automatic selection");
                    var conflicted = place(CORE.south(8));
                    require(conflicted.libraryId() == null && conflicted.serviceGrid() == null,
                            "Actual placement with two different grids remains unbound");
                    player.setItemInHand(InteractionHand.MAIN_HAND, terminalB);
                    var hit = new BlockHitResult(Vec3.atCenterOf(conflicted.getPos()), Direction.NORTH, conflicted.getPos(), false);
                    require(conflicted.onUse(conflicted.getBlockState(), level, conflicted.getPos(), player, InteractionHand.MAIN_HAND, hit) == InteractionResult.SUCCESS
                                    && conflicted.libraryId() != null && conflicted.serviceGrid() == gridB,
                            "Holding the real linked terminal explicitly selects network B through actual onUse");
                    level.removeBlock(conflicted.getPos(), false);
                    player.getInventory().clearContent(); player.getInventory().setItem(0, terminalA);
                    main = place(CORE);
                    require(main.libraryId() != null && main.serviceGrid() == gridA,
                            "Actual placement automatically binds the player's single real linked terminal network");
                    require(!main.networkAvailable(gridA), "Unformed wireless main cannot serve the real network");
                    main.getPattern().autoBuild(player, main.getMultiblockState()); next("formed");
                }
                case "formed" -> {
                    if (!main.isFormed()) return;
                    IGrid grid = access(ACCESS_A).getGrid();
                    require(main.serviceGrid() == grid && main.networkAvailable(grid),
                            "Production autoBuild forms the real main and exposes its actual wireless service grid");
                    var binding = CloudLibraryNetwork.resolve(grid, player);
                    require(binding.available() && binding.main() == main && binding.libraryId().equals(main.libraryId()),
                            "Production network resolver discovers the formed wireless main");
                    require(main.getMainNode().getGrid() != grid && !main.getMainNode().isActive(),
                            "Remote service works while the main's own AE node has no wired power or network connection");
                    require(main.getPos().distSqr(ACCESS_A) >= 6400, "The main is physically distant from AE infrastructure");
                    if (Boolean.getBoolean("gtl.cloudPatternInteractionAudit")) {
                        CloudPatternCatalystRuntimeChecks.run(level, main, player, CHECKS);
                        CloudPatternInteractionRuntimeChecks.begin(level, main, player, CHECKS); next("interaction"); break;
                    }
                    CloudPatternCatalystRuntimeChecks.run(level, main, player, CHECKS);
                    CloudWirelessBindingRuntimeChecks.run(level, CHECKS);
                    persistedBinding = savedWirelessBinding();
                    require(!persistedBinding.isEmpty(), "Normal main serialization persists the actual wireless node address");
                    require(CloudWirelessBinding.resolve(level, persistedBinding, player.getUUID()) == grid,
                            "Address serialized by the real holder resolves the exact live AE network");
                    level.setChunkForced(ACCESS_A.getX() >> 4, ACCESS_A.getZ() >> 4, false); next("unload");
                }
                case "interaction" -> {
                    if (CloudPatternInteractionRuntimeChecks.step()) finish(null);
                }
                case "unload" -> {
                    if (level.getChunkSource().getChunkNow(ACCESS_A.getX() >> 4, ACCESS_A.getZ() >> 4) != null) return;
                    require(main.isFormed() && main.serviceGrid() == null, "Actual remote chunk unload leaves the formed main offline");
                    require(level.getChunkSource().getChunkNow(ACCESS_A.getX() >> 4, ACCESS_A.getZ() >> 4) == null,
                            "Offline main service lookup never force-loads the remote chunk");
                    require(savedWirelessBinding().equals(persistedBinding), "Remote unload preserves the exact persisted address");
                    level.setChunkForced(ACCESS_A.getX() >> 4, ACCESS_A.getZ() >> 4, true); next("reload");
                }
                case "reload" -> {
                    var a = access(ACCESS_A);
                    if (a == null || !a.isActive() || main.serviceGrid() == null) return;
                    require(main.serviceGrid() == a.getGrid() && main.networkAvailable(a.getGrid()),
                            "Actual remote chunk reload restores wireless service from the persisted address");
                    require(CloudLibraryNetwork.resolve(a.getGrid(), player).available(), "Network resolver recovers after actual remote reload");
                    finish(null);
                }
            }
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); CHECKS.add(message); }
    private static void finish(Throwable failure) {
        if (Boolean.getBoolean("gtl.cloudPatternInteractionAudit")) CloudPatternInteractionRuntimeChecks.cleanup();
        stage = "done"; var report = new LinkedHashMap<String, Object>();
        report.put("phase", "player_ui"); report.put("status", failure == null ? "PASS" : "FAIL");
        report.put("checks", CHECKS); report.put("ticks", ticks); report.put("seconds", (System.nanoTime() - started) / 1_000_000_000.0);
        report.put("persistedBindingKey", persistedBindingKey);
        report.put("scope", "Actual production autoBuild geometry, separately powered real AE access-point networks, native charged linked terminals, no wired main connection, native catalyst menu actions and actual remote chunk unload/reload; marked isolated fixture only");
        report.put("notCovered", List.of("Independent JVM restart", "Quantum terminal cross-dimension links", "Multiplayer claim/team changes", "Long running cloud execution"));
        if (Boolean.getBoolean("gtl.cloudPatternInteractionAudit")) {
            report.put("phase", "pattern_interaction");
            report.put("scope", "3.9.4 actual formed wireless library, real native player menu actions, exact original deduplication and initial packet; marked task-owned fixture");
            report.put("notCovered", List.of("Independent restart is reported by separate interaction_verify phase", "Client initial packet decoding and visual rendering are reported by separate client audit", "Long running cloud execution"));
        }
        if (failure != null) report.put("error", failure.toString());
        try { Files.writeString(level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent().resolve("cloud-store-player_ui.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { throw new RuntimeException(error); }
        level.getServer().halt(false);
    }
}
