package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Real source inventories on the caller's already formed isolated library and AE grid. */
public final class CloudNetworkImportRuntimeChecks {
    private CloudNetworkImportRuntimeChecks() {}
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static ServerPlayer player;
    private static List<String> checks;
    private static PatternProviderBlockEntity provider;
    private static DriveBlockEntity drive;
    private static PatternEncodingTerminalPart terminal;
    private static MEPatternBufferPartMachine ordinary, superBuffer, blocked;
    private static ItemStack providerOriginal, ordinaryOriginal, superOriginal, storageOriginal, terminalOriginal, blockedOriginal;
    private static int stage, ticks, before;
    private static Map<UUID, ServerPlayer> onlineIndex;
    private static net.minecraft.world.inventory.AbstractContainerMenu previousMenu;
    private static boolean onlineRegistered;

    public static void begin(ServerLevel world, CloudPatternLibraryMachine library, ServerPlayer actor, List<String> results) throws Exception {
        Path root = Path.of("C:/IDEA/GTL-Enhancedcore/_work").toAbsolutePath().normalize();
        Path save = world.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!save.startsWith(root) || !Files.isRegularFile(save.getParent().resolve("cloud-store.marker")))
            throw new IllegalStateException("Network import audit requires a marked task-owned isolated world");
        level = world; main = library; player = actor; checks = results; stage = 0; ticks = 0;
        previousMenu = player.containerMenu; registerFixtureActor();
        require(main.isFormed() && main.serviceGrid() != null, "formed_library_has_actual_service_grid");
        BlockPos base = main.getPos().east(24);
        for (int offset = 0; offset <= 10; offset += 2) {
            BlockPos pos = base.east(offset);
            require(level.hasChunkAt(pos) && level.isEmptyBlock(pos), "source_position_is_loaded_fixture_air_" + offset);
        }
        level.setBlock(base, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(), 3);
        provider = (PatternProviderBlockEntity)level.getBlockEntity(base);
        ordinary = buffer("gtceu:me_pattern_buffer", base.east(2));
        superBuffer = buffer("gtladditions:me_super_pattern_buffer", base.east(4));
        blocked = buffer("gtladditions:me_super_pattern_buffer", base.east(6));
        level.setBlock(base.east(8), AEBlocks.DRIVE.block().defaultBlockState(), 3);
        drive = (DriveBlockEntity)level.getBlockEntity(base.east(8));
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        terminal = (PatternEncodingTerminalPart)PartHelper.setPart(level, base.east(10), Direction.NORTH, player,
                (IPartItem<?>)AEParts.PATTERN_ENCODING_TERMINAL.stack().getItem());
        require(terminal != null, "fixed_encoding_terminal_is_real_installed_part");
    }

    /** Call once per server tick until true. This helper does not launch Forge. */
    public static boolean step() {
        if (++ticks > 1200) throw new AssertionError("network_import:timeout_stage_" + stage);
        if (stage == 0) {
            IGridNode[] nodes = {provider.getMainNode().getNode(), ordinary.getMainNode().getNode(), superBuffer.getMainNode().getNode(),
                    blocked.getMainNode().getNode(), drive.getMainNode().getNode(), terminal.getMainNode().getNode()};
            for (IGridNode node : nodes) if (node == null) return false;
            for (IGridNode node : nodes) if (node.getGrid() != main.serviceGrid()) GridHelper.createConnection(main.serviceGrid().getPivot(), node);
            stage = 1; return false;
        }
        if (stage == 1) {
            if (!provider.getMainNode().isActive() || !ordinary.getMainNode().isActive() || !superBuffer.getMainNode().isActive()
                    || !blocked.getMainNode().isActive() || !drive.getMainNode().isActive() || !terminal.getMainNode().isActive()) return false;
            require(ordinary.getMainNode().getNode().getOwner() == ordinary, "GT_grid_node_owner_is_actual_MetaMachine");
            require(terminal.getHost().getPart(terminal.getSide()) == terminal, "terminal_part_facet_resolves_same_live_instance");
            providerOriginal = pattern("provider"); ordinaryOriginal = pattern("ordinary"); superOriginal = pattern("super");
            storageOriginal = pattern("storage"); terminalOriginal = pattern("terminal"); blockedOriginal = pattern("materials_rejected");
            provider.getLogic().getPatternInv().setItemDirect(0, providerOriginal.copy());
            seed(ordinary, 0, ordinaryOriginal); seed(ordinary, 1, providerOriginal); seed(superBuffer, 0, superOriginal); seed(blocked, 0, blockedOriginal);
            blocked.getSharedCatalystInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND));
            terminal.getLogic().getEncodedPatternInv().setItemDirect(0, terminalOriginal.copy());
            require(main.serviceGrid().getStorageService().getInventory().insert(AEItemKey.of(storageOriginal), 1, Actionable.MODULATE,
                    IActionSource.ofPlayer(player)) == 1, "real_ME_storage_accepts_one_original");
            before = CloudPatternStore.get(level).library(main.libraryId()).entryCount();
            player.setPos(main.getPos().getX() + .5, main.getPos().getY() + .5, main.getPos().getZ() - 1);
            player.containerMenu = new com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer(main.createUI(player), 86);
            require(main.menuAuthorized(player), "native_live_library_menu_authorizes_fixture_actor");
            CloudNetworkPatternImport.start(player, main);
            require(CloudNetworkPatternImport.running(main), "confirmed_import_starts_incremental_real_network_scan");
            stage = 2; return false;
        }
        CloudNetworkPatternImport.tick(main);
        if (CloudNetworkPatternImport.running(main)) return false;
        var library = CloudPatternStore.get(level).library(main.libraryId());
        if (stage == 2) {
            require(library.entryCount() == before + 5, "five_unique_originals_move_into_library");
            for (ItemStack original : List.of(providerOriginal, ordinaryOriginal, superOriginal, storageOriginal, terminalOriginal))
                require(library.containsOriginal(original), "library_preserves_complete_original_NBT_" + original.getTag().getString("networkImportAudit"));
            require(ordinary.getPatternInventory().getStackInSlot(0).isEmpty() && superBuffer.getPatternInventory().getStackInSlot(0).isEmpty(),
                    "ordinary_and_super_physical_source_slots_are_debited");
            int duplicates = provider.getLogic().getPatternInv().getStackInSlot(0).getCount() + ordinary.getPatternInventory().getStackInSlot(1).getCount();
            require(duplicates == 1, "duplicate_original_remains_at_one_source_without_copying_or_deleting");
            require(terminal.getLogic().getEncodedPatternInv().isEmpty(), "fixed_encoding_terminal_real_encoded_slot_is_debited");
            require(main.serviceGrid().getStorageService().getInventory().extract(AEItemKey.of(storageOriginal), 1, Actionable.SIMULATE,
                    IActionSource.ofPlayer(player)) == 0, "ME_storage_original_is_really_removed");
            require(ItemStack.matches(blocked.getPatternInventory().getStackInSlot(0), blockedOriginal)
                            && blocked.getSharedCatalystInventory().getStackInSlot(0).is(Items.DIAMOND) && !library.containsOriginal(blockedOriginal),
                    "material_bearing_super_buffer_retains_original_and_materials");
            require(CloudNetworkPatternImport.moved(main) == 5 && CloudNetworkPatternImport.skipped(main) > 0, "public_counts_match_actual_transfers");
            CloudNetworkPatternImport.start(player, main); stage = 3; return false;
        }
        require(library.entryCount() == before + 5 && CloudNetworkPatternImport.moved(main) == 0, "repeat_network_import_does_not_duplicate_originals");
        close(); return true;
    }
    @SuppressWarnings("unchecked")
    private static void registerFixtureActor() throws Exception {
        var list = level.getServer().getPlayerList();
        for (Class<?> owner = list.getClass(); owner != null && onlineIndex == null; owner = owner.getSuperclass())
            for (var field : owner.getDeclaredFields()) {
                String type = field.getGenericType().getTypeName();
                if (!Map.class.isAssignableFrom(field.getType()) || !type.contains("java.util.UUID")
                        || !type.contains(ServerPlayer.class.getName())) continue;
                field.setAccessible(true); onlineIndex = (Map<UUID, ServerPlayer>)field.get(list); break;
            }
        if (onlineIndex == null) throw new AssertionError("Cannot locate isolated server player UUID index");
        ServerPlayer prior = onlineIndex.get(player.getUUID());
        if (prior != null && prior != player) throw new AssertionError("Fixture refuses to replace an existing online actor");
        if (prior == null) { onlineIndex.put(player.getUUID(), player); onlineRegistered = true; }
        require(list.getPlayer(player.getUUID()) == player, "native_online_lookup_resolves_real_fixture_actor");
    }
    /** Also called by the parent fixture's failure path. Never invokes real login/logout. */
    public static void close() {
        if (player != null) {
            if (onlineRegistered && onlineIndex != null) onlineIndex.remove(player.getUUID(), player);
            if (previousMenu != null) player.containerMenu = previousMenu;
        }
        onlineRegistered = false; onlineIndex = null; previousMenu = null;
        level = null; main = null; player = null; checks = null; provider = null; drive = null; terminal = null;
        ordinary = null; superBuffer = null; blocked = null;
    }

    private static MEPatternBufferPartMachine buffer(String id, BlockPos pos) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
        if (definition == null) throw new AssertionError("Missing real source definition " + id);
        level.setBlock(pos, definition.defaultBlockState(), 3);
        var machine = (MEPatternBufferPartMachine)MetaMachine.getMachine(level, pos);
        machine.onMachinePlaced(player, ItemStack.EMPTY); return machine;
    }
    private static void seed(MEPatternBufferPartMachine source, int slot, ItemStack pattern) {
        source.getPatternInventory().setStackInSlot(slot, pattern.copy()); source.getPatternInventory().onContentsChanged(slot);
    }
    private static ItemStack pattern(String marker) {
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_NUGGET), 1)});
        pattern.getOrCreateTag().putString("networkImportAudit", marker); return pattern;
    }
    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError("network_import:" + label); checks.add("network_import:" + label);
    }
}
