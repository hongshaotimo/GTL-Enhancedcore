package com.gtl.enhancedcore.common.assembly;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class AssemblyManagerNetwork {
    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLEnhancedcore.MOD_ID, "assembly_manager"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;
    private static final ResourceLocation MANAGER_ITEM = new ResourceLocation(GTLEnhancedcore.MOD_ID, "assembly_manager");
    private static final java.util.Map<ServerPlayer, RequestTimes> REQUESTS = new java.util.WeakHashMap<>();
    private static final class RequestTimes {
        long snapshot = Long.MIN_VALUE / 2, opened = Long.MIN_VALUE / 2, travel = Long.MIN_VALUE / 2;
        boolean holding, ownsManager;
        ResourceLocation dimension;
        List<Target> targets;
    }

    public record Target(BlockPos pos, String name) {}
    private record OpenRequest() {}
    private record RefreshRequest() {}
    private record TeleportRequest(ResourceLocation dimension, BlockPos pos) {}
    private record Snapshot(ResourceLocation dimension, List<Target> targets, boolean open) {}
    private record Result(boolean success, Component message) {}

    private AssemblyManagerNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        CHANNEL.registerMessage(0, OpenRequest.class, (p, b) -> {}, b -> new OpenRequest(), AssemblyManagerNetwork::openPacket, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, RefreshRequest.class, (p, b) -> {}, b -> new RefreshRequest(), AssemblyManagerNetwork::refreshPacket, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, TeleportRequest.class, (p, b) -> { b.writeResourceLocation(p.dimension); b.writeBlockPos(p.pos); },
                b -> new TeleportRequest(b.readResourceLocation(), b.readBlockPos()), AssemblyManagerNetwork::teleportPacket, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, Snapshot.class, (p, b) -> {
            b.writeResourceLocation(p.dimension); b.writeBoolean(p.open);
            b.writeVarInt(p.targets.size());
            for (Target target : p.targets) { b.writeBlockPos(target.pos); b.writeUtf(target.name); }
        }, b -> {
            ResourceLocation dimension = b.readResourceLocation(); boolean open = b.readBoolean();
            int count = b.readVarInt();
            if (count < 0 || count > b.readableBytes() / 9) throw new IllegalArgumentException("invalid target count");
            var targets = new java.util.ArrayList<Target>(count);
            for (int i = 0; i < count; i++) targets.add(new Target(b.readBlockPos(), b.readUtf()));
            return new Snapshot(dimension, List.copyOf(targets), open);
        }, (p, ctx) -> {
            var context = ctx.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.gtl.enhancedcore.client.assembly.AssemblyManagerClient.accept(p.dimension, p.targets, p.open)));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, Result.class, (p, b) -> { b.writeBoolean(p.success); b.writeComponent(p.message); },
                b -> new Result(b.readBoolean(), b.readComponent()), (p, ctx) -> {
                    var context = ctx.get();
                    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> com.gtl.enhancedcore.client.assembly.AssemblyManagerClient.result(p.success, p.message)));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registered = true;
    }

    public static void open(ServerPlayer player) {
        if (isHolding(player)) AssemblyMirrorNetwork.open(player,
                isManager(player.getMainHandItem()) ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND);
        else player.displayClientMessage(Component.translatable("gtl_enhancedcore.assembly.hold_item"), true);
    }

    public static void requestRefresh() { CHANNEL.sendToServer(new RefreshRequest()); }
    public static void requestTeleport(ResourceLocation dimension, BlockPos pos) { CHANNEL.sendToServer(new TeleportRequest(dimension, pos)); }
    public static boolean isHolding(Player player) {
        return isManager(player.getMainHandItem()) || isManager(player.getOffhandItem());
    }
    private static boolean isManager(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && MANAGER_ITEM.equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private static boolean ownsManager(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            if (isManager(inventory.getItem(slot))) return true;
        return false;
    }

    private static void openPacket(OpenRequest p, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> open(player));
        ctx.setPacketHandled(true);
    }
    private static void refreshPacket(RefreshRequest p, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> { if (isHolding(player)) sendSnapshot(player, false, false); });
        ctx.setPacketHandled(true);
    }
    private static void teleportPacket(TeleportRequest p, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get(); var player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> {
            if (player.serverLevel().dimension().location().equals(p.dimension)) teleport(player, p.pos);
            else reply(player, false, "invalid_target");
        });
        ctx.setPacketHandled(true);
    }

    private static void sendSnapshot(ServerPlayer player, boolean open, boolean immediate) {
        long now = player.server.overworld().getGameTime();
        var requests = REQUESTS.computeIfAbsent(player, unused -> new RequestTimes());
        if (open) {
            if (now - requests.opened < 5) return;
            requests.opened = now;
        } else if (!immediate && now - requests.snapshot < 20) return;
        requests.snapshot = now;
        ResourceLocation dimension = player.serverLevel().dimension().location();
        List<Target> targets = AssemblyManagerIndex.get(player.serverLevel()).snapshot(player.serverLevel());
        if (!open && !immediate && requests.targets == targets
                && dimension.equals(requests.dimension)) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Snapshot(dimension, targets, open));
        requests.dimension = dimension;
        requests.targets = targets;
    }

    private static void teleport(ServerPlayer player, BlockPos pos) {
        long now = player.server.overworld().getGameTime();
        var requests = REQUESTS.computeIfAbsent(player, unused -> new RequestTimes());
        if (now - requests.travel < 10) { reply(player, false, "cooldown"); return; }
        requests.travel = now;
        if (!isHolding(player) || player.isSpectator() || !player.isAlive() || player.isPassenger() || player.isSleeping()) {
            reply(player, false, "blocked"); return;
        }
        ServerLevel level = player.serverLevel();
        if (!AssemblyManagerIndex.get(level).contains(pos)) {
            reply(player, false, "invalid_target"); return;
        }
        var chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        var machine = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(chunk, pos);
        if (!AssemblyManagerIndex.isTarget(machine)) { AssemblyManagerIndex.get(level).reconcile(level, chunk); reply(player, false, "invalid_target"); return; }
        Direction front = machine.getFrontFacing();
        BlockPos feet = AssemblyManagerTeleport.find(level, player, pos, front);
        if (feet == null) { reply(player, false, "unsafe"); return; }
        double x = feet.getX() + .5, y = feet.getY(), z = feet.getZ() + .5;
        var event = new EntityTeleportEvent.TeleportCommand(player, x, y, z);
        if (MinecraftForge.EVENT_BUS.post(event) || event.getTargetX() != x || event.getTargetY() != y || event.getTargetZ() != z) {
            reply(player, false, "cancelled"); return;
        }
        var currentChunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (!isHolding(player) || player.serverLevel() != level || currentChunk == null
                || com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(currentChunk, pos) != machine
                || !AssemblyManagerTeleport.safeAt(level, player, feet)) { reply(player, false, "unsafe"); return; }
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        boolean success = player.serverLevel() == level && player.distanceToSqr(x, y, z) <= .01;
        if (success) { player.fallDistance = 0; player.setDeltaMovement(0, 0, 0); }
        reply(player, success, success ? "success" : "cancelled");
    }

    private static void reply(ServerPlayer player, boolean success, String key) {
        Component message = Component.translatable("gtl_enhancedcore.assembly.result." + key);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Result(success, message));
    }

    @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
            AssemblyManagerIndex.get(level).reconcile(level, chunk);
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var state = REQUESTS.computeIfAbsent(player, unused -> new RequestTimes());
        boolean holding = isHolding(player);
        boolean owns = holding || ownsManager(player);
        if (owns) {
            var index = AssemblyManagerIndex.get(player.serverLevel());
            boolean immediate = (holding && !state.holding) || !state.ownsManager
                    || !player.serverLevel().dimension().location().equals(state.dimension);
            if (immediate || state.targets != index.snapshot(player.serverLevel()))
                sendSnapshot(player, false, true);
        } else {
            state.targets = null;
            state.dimension = null;
        }
        state.holding = holding;
        state.ownsManager = owns;
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) REQUESTS.remove(player);
    }

    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var state = REQUESTS.computeIfAbsent(player, unused -> new RequestTimes());
            state.dimension = null;
            state.holding = false;
            if (ownsManager(player)) sendSnapshot(player, false, true);
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) REQUESTS.remove(player);
    }

    @SubscribeEvent public static void serverStopped(ServerStoppedEvent event) { REQUESTS.clear(); }
}
