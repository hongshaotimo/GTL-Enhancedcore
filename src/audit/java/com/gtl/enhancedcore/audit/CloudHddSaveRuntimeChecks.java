package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.CloudImportIo;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real SavedData I/O counts, blocked worker and independent-JVM recovery in a fresh marked world. */
@Mod("enhancedcore_audit")
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class CloudHddSaveRuntimeChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-hdd-save-20261029");
    private static final List<String> checks = new ArrayList<>();
    private static CloudPatternStore store;
    private static CloudPatternStore.Library library;
    private static CloudPatternStore.Node changed;
    private static final List<CloudPatternStore.Node> nodes = new ArrayList<>();
    private static final List<CompoundTag> states = new ArrayList<>();
    private static Path directory, data;
    private static Map<Path, FileTime> before;
    private static CountDownLatch release;
    private static CompletableFuture<Void> barrier;
    private static int stage, ticks;
    public CloudHddSaveRuntimeChecks() {}
    private static void check(boolean valid, String label) { if (!valid) throw new AssertionError(label); checks.add(label); }
    private static Path nodePath(CloudPatternStore.Node node) { return data.resolve("gtl_cloud_node_" + node.id() + ".dat"); }
    private static CompoundTag payload(Path path) throws Exception { return NbtIo.readCompressed(path.toFile()).getCompound("data").getCompound("data"); }
    private static Map<Path, FileTime> stamps() throws Exception {
        var result = new HashMap<Path, FileTime>();
        try (var paths = Files.list(data)) { for (var path : paths.filter(p -> p.getFileName().toString().startsWith("gtl_cloud_")).toList())
            result.put(path, Files.getLastModifiedTime(path)); }
        return result;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("gtl.cloudHddSaveAudit")) return;
        var server = event.getServer(); directory = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent();
        if (!directory.startsWith(ROOT) || directory.equals(ROOT) || !Files.isRegularFile(directory.resolve("cloud-hdd.marker")))
            throw new IllegalStateException("Refuse unmarked/player world");
        data = server.getWorldPath(LevelResource.ROOT).resolve("data");
        try {
            store = CloudPatternStore.get(server.overworld());
            if (Boolean.getBoolean("gtl.cloudHddSaveReload")) { verify(server); return; }
            UUID owner = UUID.randomUUID(), id = store.defaultLibrary(owner); library = store.library(id);
            for (int i = 0; i < 48; i++) {
                var node = store.createNode(owner, id, server.overworld().dimension().location(), i); nodes.add(node);
                var state = new CompoundTag(); state.putInt("progress", 0); states.add(state);
                store.bindNodeSnapshot(node, () -> state, CompoundTag::new, CompoundTag::new);
            }
            var original = new ItemStack(Items.PAPER); UUID op = UUID.randomUUID();
            var entry = store.upload(id, owner, original, new CompoundTag(), op); store.acknowledge(id, owner, op);
            store.checkpoint(); before = stamps();
            long revision = library.revision();
            for (int i = 0; i < 155; i++) { for (var node : nodes) store.markNodeDirty(node); store.checkpointLibrary(id); }
            check(stamps().equals(before), "155 checkpoints x 48 unchanged nodes produce zero additional cloud file writes");
            check(library.revision() == revision, "No-op order dirty notifications never advance authority revision");
            changed = nodes.get(17); states.get(17).putInt("progress", 7); store.markNodeDirty(changed); store.checkpoint();
            var after = stamps();
            var modified = new HashSet<Path>(); for (var path : after.keySet()) if (!Objects.equals(after.get(path), before.get(path))) modified.add(path);
            check(modified.size() == 4 && modified.contains(nodePath(changed))
                    && modified.contains(data.resolve("gtl_cloud_library_" + id + ".dat")), "One changed node writes only node/authority and their previous files (4 paths)");
            before = after;
            release = new CountDownLatch(1); var entered = new CountDownLatch(1);
            CloudImportIo.run(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException failure) { throw new RuntimeException(failure); } });
            check(entered.await(5, TimeUnit.SECONDS), "Controlled I/O worker stall established");
            states.get(17).putInt("progress", 8); store.markNodeDirty(changed);
            long start = System.nanoTime(); ((CloudPatternStore.CheckedData) changed).save(nodePath(changed).toFile());
            check(System.nanoTime() - start < 1_000_000_000L && release.getCount() == 1, "Vanilla save returns while the disk worker is blocked");
            for (int i = 0; i < 155; i++) ((CloudPatternStore.CheckedData) changed).save(nodePath(changed).toFile());
            check(stamps().equals(before), "Repeated vanilla saves do not write on the server thread");
            states.get(17).putInt("progress", 9); store.markNodeDirty(changed);
            release.countDown(); barrier = CloudImportIo.run(() -> {}); stage = 1;
        } catch (Throwable failure) { if (release != null) release.countDown(); finish(server, failure); }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (stage == 0 || event.phase != TickEvent.Phase.END) return;
        try {
            if (++ticks > 600) throw new AssertionError("Background save timed out");
            if (stage == 1 && barrier.isDone()) {
                check(changed.isDirty(), "Mutation during async persistence remains dirty");
                check(payload(nodePath(changed)).getCompound("ivState").getInt("progress") == 8, "Worker persisted detached capture, never live mutated state");
                before = stamps();
                for (int i = 0; i < 155; i++) ((CloudPatternStore.CheckedData) changed).save(nodePath(changed).toFile());
                check(stamps().equals(before), "Ten-second throttle coalesces further vanilla requests");
                var ref = new CompoundTag(); ref.putUUID("library", library.id()); ref.putUUID("node", changed.id());
                ref.putUUID("quietNode", nodes.get(0).id()); ref.put("quiet", payload(nodePath(nodes.get(0))));
                NbtIo.writeCompressed(ref, directory.resolve("reference.dat").toFile());
                check(true, "Leaving newest progress dirty for normal server stop flush");
                stage = 0; finish(event.getServer(), null);
            }
        } catch (Throwable failure) { stage = 0; finish(event.getServer(), failure); }
    }
    private static void verify(MinecraftServer server) throws Exception {
        var ref = NbtIo.readCompressed(directory.resolve("reference.dat").toFile());
        var node = store.node(ref.getUUID("node")); var authority = store.library(ref.getUUID("library"));
        check(authority.healthy() && node.healthy(), "Independent restart loads healthy authority and changed node");
        check(node.ivState().getInt("progress") == 9, "Normal server stop flushed newest throttled progress");
        check(store.node(ref.getUUID("quietNode")).snapshot().equals(ref.getCompound("quiet")), "Quiet node remains byte-equivalent after restart");
        check(authority.storedCount() == 1, "Original ownership survives ordered background persistence and restart");
        finish(server, null);
    }
    private static void finish(MinecraftServer server, Throwable failure) {
        var report = new LinkedHashMap<String,Object>(); report.put("status", failure == null ? "PASS" : "FAIL"); report.put("checks", checks);
        if (failure != null) { report.put("error", failure.toString()); failure.printStackTrace(); }
        try { Files.writeString(directory.resolve(Boolean.getBoolean("gtl.cloudHddSaveReload") ? "hdd-reload.json" : "hdd-save.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { throw new RuntimeException(error); }
        server.halt(false);
    }
}
