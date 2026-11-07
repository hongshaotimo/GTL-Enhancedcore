package com.gtl.enhancedcore.audit;

import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.grid.AENetworkInvBlockEntity;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.me.InWorldGridNode;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Opt-in checks of the real AE2 node and transformed block entity in a fresh dedicated world. */
@Mod("enhancedcore_audit")
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class AeGridNodeUnloadRuntimeChecks {
    private static final List<String> CHECKS = new ArrayList<>();
    private static final List<String> EXPECTED = new ArrayList<>();
    private static boolean finished;
    private static int ticks;
    public AeGridNodeUnloadRuntimeChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("gtl.aeUnload.audit") || finished || event.phase != TickEvent.Phase.END || ++ticks < 20) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        finished = true;
        Path fixture = Path.of(System.getProperty("gtl.aeUnload.fixture")).toAbsolutePath().normalize();
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            Path allowed = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/ae-grid-node-unload-20261107").toAbsolutePath().normalize();
            if (!fixture.getParent().equals(allowed) || !server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(fixture.resolve("audit-world"))
                    || !Files.isRegularFile(fixture.resolve("ae-grid-node-unload.marker"))) throw new AssertionError("Refused non-fixture world");
            check(server.isDedicatedServer(), "Real dedicated Forge server");
            run(server.overworld());
            report.put("status", "PASS");
        } catch (Throwable failure) {
            report.put("status", "FAIL"); report.put("failure", failure.toString());
            failure.printStackTrace();
        } finally {
            report.put("mode", System.getProperty("gtl.aeUnload.mode"));
            report.put("assertions", CHECKS.size()); report.put("checks", CHECKS); report.put("expectedBaselineExceptions", EXPECTED);
            try { Files.writeString(fixture.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Exception failure) { throw new RuntimeException(failure); }
            System.out.println("[AE_GRID_NODE_UNLOAD] " + report.get("status") + " assertions=" + CHECKS.size() + " baselineExceptions=" + EXPECTED.size());
            server.halt(false);
        }
    }

    private static void run(ServerLevel level) throws Exception {
        QuantumBridgeBlockEntity ring = ring(level, new BlockPos(160, 90, 160));
        check(ring.getMainNode().getNode() == null, "Constructor preserves pre-initialization state");
        Object data = field(ring.getMainNode(), "initData");
        check(((java.util.Set<?>) field(data, "exposedOnSides")).size() == 6, "Superclass constructor preserves its initial six-side configuration");
        ring.updateStatus(null, (byte) -1, true);
        check(((java.util.Set<?>) field(data, "exposedOnSides")).isEmpty(), "Pre-initialization unformed update closes initialization sides");
        ring.updateStatus(null, (byte) 0, true);
        check(((java.util.Set<?>) field(data, "exposedOnSides")).size() == 6, "Pre-initialization updateStatus changes all six initialization sides");
        ring.onReady();
        check(ring.getMainNode().isReady(), "Real quantum ring onReady creates a live node");
        sides(ring, 6, "Initialized node retains pre-initialization side update");
        ring.updateStatus(null, (byte) -1, true);
        sides(ring, 0, "Active quantum ring closes actual node connection sides");
        ring.updateStatus(null, (byte) 0, true);
        sides(ring, 6, "Active quantum ring reopens actual node connection sides");
        ring.onChunkUnloaded();
        check(ring.getMainNode().getNode() == null && !ring.getMainNode().isReady(), "Chunk unload destroys initialized node");
        terminated(ring, "onChunkUnloaded");
        ring.onChunkUnloaded();
        terminated(ring, "repeated onChunkUnloaded");

        QuantumBridgeBlockEntity removed = ring(level, new BlockPos(164, 90, 160));
        removed.onReady(); removed.setRemoved();
        check(removed.isRemoved() && removed.getMainNode().getNode() == null, "setRemoved destroys the real initialized node");
        terminated(removed, "setRemoved");

        QuantumBridgeBlockEntity replacement = ring(level, ring.getBlockPos());
        replacement.onReady(); replacement.updateStatus(null, (byte) 0, true);
        sides(replacement, 6, "New block entity at old position has an independent working node");
        replacement.onChunkUnloaded();

        BlockPos orientedPos = new BlockPos(176, 90, 160);
        var state = AEBlocks.DRIVE.block().defaultBlockState();
        AENetworkInvBlockEntity oriented = AEBlockEntities.DRIVE.create(orientedPos, state);
        oriented.setLevel(level);
        Field pending = AEBaseBlockEntity.class.getDeclaredField("pendingOrientationChange"); pending.setAccessible(true);
        pending.set(oriented, BlockOrientation.EAST_UP);
        oriented.onReady();
        check(pending.get(oriented) == null && oriented.getMainNode().isReady(), "onReady applies pending orientation before creating its real node");
        check(oriented.getReadyInvoked() == 1, "Pending orientation initialization executes exactly once");
        oriented.setRemoved(); level.setBlock(orientedPos, Blocks.AIR.defaultBlockState(), 2);

        // Real world-owned neighboring quantum block entities establish and destroy actual grid connections.
        BlockPos first = new BlockPos(192, 90, 192), second = first.east();
        level.setBlock(first, AEBlocks.QUANTUM_RING.block().defaultBlockState(), 2);
        level.setBlock(second, AEBlocks.QUANTUM_RING.block().defaultBlockState(), 2);
        QuantumBridgeBlockEntity a = (QuantumBridgeBlockEntity) level.getBlockEntity(first);
        QuantumBridgeBlockEntity b = (QuantumBridgeBlockEntity) level.getBlockEntity(second);
        a.updateStatus(null, (byte) 0, true); b.updateStatus(null, (byte) 0, true);
        a.onReady(); b.onReady();
        check(a.getMainNode().getNode().getConnections().size() > 0 && b.getMainNode().getNode().getConnections().size() > 0, "Adjacent world-owned quantum rings establish real grid connections");
        a.onChunkUnloaded();
        check(b.getMainNode().getNode() != null && b.getMainNode().getNode().getConnections().isEmpty(), "Destroying first neighbor removes the second node connection");
        terminated(a, "world-owned neighbor after first unload");
        level.getChunkAt(first).clearAllBlockEntities();
        check(a.isRemoved() && b.isRemoved() && b.getMainNode().getNode() == null, "Real LevelChunk.clearAllBlockEntities finishes both neighbor lifecycles");
        terminated(b, "world-owned neighbor after real chunk clear");
    }

    private static QuantumBridgeBlockEntity ring(ServerLevel level, BlockPos position) {
        var result = new QuantumBridgeBlockEntity(AEBlockEntities.QUANTUM_BRIDGE, position, AEBlocks.QUANTUM_RING.block().defaultBlockState());
        result.setLevel(level); return result;
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void sides(QuantumBridgeBlockEntity ring, int count, String message) {
        InWorldGridNode node = (InWorldGridNode) ring.getMainNode().getNode(); int actual = 0;
        for (Direction side : Direction.values()) if (node.isExposedOnSide(side)) actual++;
        check(actual == count, message + " (actual=" + actual + ")");
    }
    private static void terminated(QuantumBridgeBlockEntity ring, String label) {
        boolean baseline = "baseline".equals(System.getProperty("gtl.aeUnload.mode"));
        try { ring.updateStatus(null, (byte) 0, true); }
        catch (IllegalStateException failure) {
            if (!baseline || !failure.getMessage().equals("The node has already been initialized. Initialization data cannot be changed anymore.")
                    || !failure.getStackTrace()[0].getClassName().equals("appeng.me.ManagedGridNode")
                    || !failure.getStackTrace()[0].getMethodName().equals("getInitData")) throw failure;
            EXPECTED.add(label + ": " + failure); check(true, label + " precisely reproduces reported AE2 lifecycle exception"); return;
        }
        check(!baseline, label + " skips terminal exposure update without throwing");
        check(ring.getMainNode().getNode() == null, label + " does not resurrect a node");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); CHECKS.add(message);
        System.out.println("[AE_GRID_NODE_UNLOAD] CHECK " + message);
    }
}
