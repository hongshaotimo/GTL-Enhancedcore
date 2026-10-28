package com.gtl.enhancedcore.common.cloud;

import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/** A saved address of a real loaded AE node, never an invented grid or a saved permission token. */
public final class CloudWirelessBinding {
    private CloudWirelessBinding() {}
    public enum State { READY, MISSING, CONFLICT, DENIED, INCOMPLETE }
    public record Result(State state, IGrid grid, CompoundTag address) {
        public String translationKey() { return "gtl_enhancedcore.cloud.wireless." + state.name().toLowerCase(Locale.ROOT); }
    }
    public static boolean isTerminal(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof WirelessTerminalItem) return true;
        return hasInterface(stack.getItem().getClass(), "de.mari_023.ae2wtlib.terminal.IUniversalWirelessTerminalItem");
    }
    private static boolean hasInterface(Class<?> type, String name) {
        if (type == null) return false;
        if (type.getName().equals(name)) return true;
        for (Class<?> iface : type.getInterfaces()) if (hasInterface(iface, name)) return true;
        return hasInterface(type.getSuperclass(), name);
    }
    private static final class Scan {
        int remaining = 1024;
        boolean incomplete;
        final Set<IItemHandler> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<IGrid, Result> grids = new IdentityHashMap<>();
        boolean denied;
    }
    /** Placement scans every carried terminal; a held terminal is an explicit choice when repairing. */
    public static Result find(ServerPlayer player, ItemStack explicit) {
        if (!player.serverLevel().getServer().isSameThread()) return new Result(State.DENIED, null, null);
        if (explicit != null) return inspect(player, explicit);
        Scan scan = new Scan();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) scanStack(player, inventory.getItem(slot), scan, 0);
        scanHandler(player, player.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null), scan, 0);
        // Curios is optional: its registered inventory capability contains all equipped stacks.
        scanCurios(player, scan);
        if (scan.incomplete) return new Result(State.INCOMPLETE, null, null);
        if (scan.grids.size() > 1) return new Result(State.CONFLICT, null, null);
        if (scan.grids.size() == 1) return scan.grids.values().iterator().next();
        return new Result(scan.denied ? State.DENIED : State.MISSING, null, null);
    }
    private static void scanStack(ServerPlayer player, ItemStack stack, Scan scan, int depth) {
        if (stack.isEmpty()) return;
        if (--scan.remaining < 0) { scan.incomplete = true; return; }
        if (isTerminal(stack)) {
            Result result = inspect(player, stack);
            if (result.grid != null) {
                Result previous = scan.grids.get(result.grid);
                if (previous == null || previous.state != State.READY) scan.grids.put(result.grid, result);
            }
            if (result.state == State.DENIED) scan.denied = true;
        }
        IItemHandler nested = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (nested != null && depth >= 5) scan.incomplete = true;
        else scanHandler(player, nested, scan, depth + 1);
    }
    private static void scanHandler(ServerPlayer player, IItemHandler handler, Scan scan, int depth) {
        if (handler == null || !scan.visited.add(handler)) return;
        for (int i = 0; i < handler.getSlots() && scan.remaining >= 0; i++) scanStack(player, handler.getStackInSlot(i), scan, depth);
        if (scan.remaining < 0) scan.incomplete = true;
    }
    private static void scanCurios(ServerPlayer player, Scan scan) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("curios")) return;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object optional = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            Object handler = ((net.minecraftforge.common.util.LazyOptional<?>) optional).resolve().orElse(null);
            if (handler != null) {
                Class<?> handlerApi = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                scanHandler(player, (IItemHandler) handlerApi.getMethod("getEquippedCurios").invoke(handler), scan, 0);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) { scan.incomplete = true; }
    }
    private static Result inspect(ServerPlayer player, ItemStack stack) {
        if (!isTerminal(stack)) return new Result(State.MISSING, null, null);
        try {
            IGrid grid;
            IGridNode anchor = null;
            if (stack.getItem() instanceof WirelessTerminalItem terminal) {
                GlobalPos linked = terminal.getLinkedPosition(stack);
                // Native getLinkedGrid resolves the address. Check its chunk first so no implementation can force it.
                if (linked != null && loaded(player.serverLevel(), linked) == null) return new Result(State.MISSING, null, null);
                if (!terminal.hasPower(player, .5, stack)) return new Result(State.MISSING, null, null);
                grid = terminal.getLinkedGrid(stack, player.serverLevel(), null);
                var host = terminal.getMenuHost(player, -1, stack, null);
                if (!(host instanceof WirelessTerminalMenuHost wireless) || !wireless.rangeCheck()) return new Result(State.MISSING, null, null);
                anchor = wireless.getActionableNode();
                if (linked != null && loaded(player.serverLevel(), linked) instanceof IActionHost access) {
                    IGridNode node = access.getActionableNode();
                    if (node != null && node.getGrid() == grid) anchor = node;
                }
            } else {
                // Optional wtlib interface implementations need not inherit AE's concrete item class.
                if (!(stack.getItem() instanceof IAEItemPowerStorage power) || power.getAECurrentPower(stack) < .5)
                    return new Result(State.MISSING, null, null);
                grid = (IGrid) stack.getItem().getClass().getMethod("getLinkedGrid", ItemStack.class, Level.class,
                        net.minecraft.world.entity.player.Player.class).invoke(stack.getItem(), stack, player.serverLevel(), null);
                Object host = stack.getItem().getClass().getMethod("getMenuHost", net.minecraft.world.entity.player.Player.class,
                        appeng.menu.locator.MenuLocator.class, ItemStack.class).invoke(stack.getItem(), player,
                                appeng.menu.locator.MenuLocators.forInventorySlot(-1), stack);
                if (!(host instanceof WirelessTerminalMenuHost wireless) || !wireless.rangeCheck()) return new Result(State.MISSING, null, null);
                anchor = wireless.getActionableNode();
            }
            if (grid == null || anchor == null || anchor.getGrid() != grid) return new Result(State.MISSING, null, null);
            CompoundTag address = address(anchor);
            if (address == null || resolve(player.serverLevel(), address, player.getUUID()) != grid)
                return new Result(State.DENIED, grid, null);
            if (!anchor.getLevel().mayInteract(player, BlockPos.of(address.getLong("position"))))
                return new Result(State.DENIED, grid, null);
            return new Result(State.READY, grid, address);
        } catch (ReflectiveOperationException | RuntimeException failure) { return new Result(State.MISSING, null, null); }
    }
    private static CompoundTag address(IGridNode node) {
        if (!(node.getOwner() instanceof BlockEntity entity) || entity.getLevel() != node.getLevel()) return null;
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", node.getLevel().dimension().location().toString());
        tag.putLong("position", entity.getBlockPos().asLong());
        UUID owner = node.getOwningPlayerProfileId();
        // AE's node owner is bookkeeping, not an access-control list. Nodes created
        // by automation or older networks may have no owner at all.
        if (owner != null) tag.putUUID("nodeOwner", owner);
        tag.putString("blockEntity", net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString());
        return tag;
    }
    private static BlockEntity loaded(ServerLevel origin, GlobalPos address) {
        ServerLevel level = origin.getServer().getLevel(address.dimension());
        if (level == null) return null;
        BlockPos pos = address.pos();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }
    /** Re-resolves the live grid and claims on every use; unloaded targets remain bound but offline. */
    public static IGrid resolve(ServerLevel origin, CompoundTag address, UUID actor) {
        if (!origin.getServer().isSameThread() || actor == null) return null;
        try {
            ResourceLocation dimension = ResourceLocation.tryParse(address.getString("dimension"));
            if (dimension == null) return null;
            ServerLevel level = origin.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) return null;
            BlockPos pos = BlockPos.of(address.getLong("position"));
            BlockEntity entity = loaded(origin, GlobalPos.of(level.dimension(), pos));
            if (entity == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString().equals(address.getString("blockEntity"))) return null;
            IGridNode node = entity instanceof IActionHost host ? host.getActionableNode() : null;
            if (node == null && entity instanceof IInWorldGridNodeHost host) node = host.getGridNode(null);
            if (node == null || !node.isActive()
                    || address.hasUUID("nodeOwner") && !address.getUUID("nodeOwner").equals(node.getOwningPlayerProfileId())) return null;
            // Existing library authorization also includes the live FTB claim at this remote anchor.
            if (!claimAllows(actor, level, pos)) return null;
            return node.getGrid();
        } catch (RuntimeException unavailable) { return null; }
    }
    private static boolean claimAllows(UUID actor, ServerLevel level, BlockPos pos) {
        if (!dev.ftb.mods.ftbchunks.api.FTBChunksAPI.api().isManagerLoaded()) return false;
        var claim = dev.ftb.mods.ftbchunks.api.FTBChunksAPI.api().getManager().getChunk(
                new dev.ftb.mods.ftblibrary.math.ChunkDimPos(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4));
        if (claim == null) return true;
        var team = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager().getTeamForPlayerID(actor);
        return team.isPresent() && team.get().getId().equals(claim.getTeamData().getTeam().getId());
    }
}
