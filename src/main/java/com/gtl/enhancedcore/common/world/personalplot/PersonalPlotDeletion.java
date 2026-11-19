package com.gtl.enhancedcore.common.world.personalplot;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-thread world shutdown followed by bounded-path, IO-only background cleanup. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class PersonalPlotDeletion {
    private static final Map<MinecraftServer, Map<UUID, Job>> JOBS = new WeakHashMap<>();
    private static final class Job {
        final Path root; final UUID owner; final ServerLevel level;
        CompletableFuture<Void> worker; boolean closed, failed; int ticks;
        Job(Path root, UUID owner, ServerLevel level) { this.root = root; this.owner = owner; this.level = level; }
    }
    private PersonalPlotDeletion() {}
    private static java.util.Set<String> deletedPaths(Path root) throws IOException {
        var paths = new java.util.HashSet<String>();
        for (UUID owner : PersonalPlotStore.deletions(root)) paths.add(PersonalPlotStore.dimensionPath(owner));
        return paths;
    }
    public static com.mojang.datafixers.util.Pair<net.minecraft.world.level.storage.WorldData, net.minecraft.world.level.levelgen.WorldDimensions.Complete>
            filterLoaded(Path root, com.mojang.datafixers.util.Pair<net.minecraft.world.level.storage.WorldData, net.minecraft.world.level.levelgen.WorldDimensions.Complete> loaded) {
        if (loaded == null) return null;
        try {
            var deleted = deletedPaths(root); if (deleted.isEmpty()) return loaded;
            var previous = loaded.getSecond(); var registry = previous.dimensions();
            var filtered = new net.minecraft.core.MappedRegistry<net.minecraft.world.level.dimension.LevelStem>(
                    net.minecraft.core.registries.Registries.LEVEL_STEM, registry.registryLifecycle());
            for (var entry : registry.entrySet()) {
                var id = entry.getKey().location();
                if (!GTLEnhancedcore.MOD_ID.equals(id.getNamespace()) || !deleted.contains(id.getPath()))
                    filtered.register(entry.getKey(), entry.getValue(), registry.lifecycle(entry.getValue()));
            }
            return com.mojang.datafixers.util.Pair.of(loaded.getFirst(),
                    new net.minecraft.world.level.levelgen.WorldDimensions.Complete(filtered.freeze(), previous.specialWorldProperty()));
        } catch (IOException failure) { throw new java.io.UncheckedIOException("Cannot read personal deletion journal", failure); }
    }
    public static net.minecraft.nbt.CompoundTag filterSaved(Path root, net.minecraft.nbt.CompoundTag data) {
        try {
            var deleted = PersonalPlotStore.deletions(root);
            if (deleted.isEmpty()) return data;
            var dimensions = data.getCompound("WorldGenSettings").getCompound("dimensions");
            for (UUID owner : deleted) dimensions.remove(GTLEnhancedcore.MOD_ID + ":" + PersonalPlotStore.dimensionPath(owner));
            return data;
        } catch (IOException failure) { throw new java.io.UncheckedIOException("Cannot read personal deletion journal", failure); }
    }
    public static boolean isDeleting(MinecraftServer server, UUID owner) {
        Map<UUID, Job> jobs = JOBS.get(server); return jobs != null && jobs.containsKey(owner);
    }
    public static void begin(MinecraftServer server, Path root, UUID owner, ServerLevel level) {
        JOBS.computeIfAbsent(server, unused -> new HashMap<>()).put(owner, new Job(root, owner, level));
        if (level != null) { server.forgeGetWorldMap().remove(PersonalPlotWorlds.key(owner), level); server.markWorldsDirty(); }
        // END tick advances the job, after Minecraft's cached world iteration has finished.
    }
    public static void resume(MinecraftServer server, Path root) throws IOException {
        for (UUID owner : PersonalPlotStore.deletions(root)) {
            if (Files.exists(PersonalPlotStore.packPath(root, owner), LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(PersonalPlotStore.dimensionDirectory(root, owner), LinkOption.NOFOLLOW_LINKS))
                begin(server, root, owner, server.getLevel(PersonalPlotWorlds.key(owner)));
        }
    }
    public static void stopped(MinecraftServer server) { JOBS.remove(server); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer(); Map<UUID, Job> jobs = JOBS.get(server);
        if (jobs == null) return;
        // Incomplete drains remain part of normal stopServer close/save; tombstones suppress the next load.
        for (Job job : jobs.values()) {
            // The next server may open this save as soon as shutdown completes.
            if (job.worker != null) {
                try { job.worker.join(); }
                catch (java.util.concurrent.CompletionException failure) {
                    GTLEnhancedcore.LOGGER.error("Personal dimension cleanup incomplete for {}", job.owner, failure);
                }
            }
            if (job.level == null || job.closed) continue;
            server.forgeGetWorldMap().put(PersonalPlotWorlds.key(job.owner), job.level); server.markWorldsDirty();
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer(); Map<UUID, Job> jobs = JOBS.get(server);
        if (jobs == null) return;
        for (Job job : java.util.List.copyOf(jobs.values())) {
            if (job.failed) continue;
            try {
                if (job.worker != null) {
                    if (!job.worker.isDone()) continue;
                    job.worker.join();
                    jobs.remove(job.owner);
                    PersonalPlotNetwork.broadcastDimensionRemoval(PersonalPlotWorlds.key(job.owner));
                    notify(server, job.owner, "deleted");
                    continue;
                }
                if (job.level != null && !job.closed) {
                    if (!job.level.players().isEmpty()) throw new IOException("A player entered a deleting dimension");
                    if (server.forgeGetWorldMap().remove(PersonalPlotWorlds.key(job.owner), job.level)) server.markWorldsDirty();
                    var chunks = job.level.getChunkSource(); chunks.removeTicketsOnClosing();
                    chunks.tick(() -> true, false);
                    long deadline = System.nanoTime() + 5_000_000L;
                    while (System.nanoTime() < deadline && chunks.pollTask()) { }
                    if (chunks.chunkMap.hasWork()) {
                        if (++job.ticks > 2400) throw new IOException("Personal dimension did not finish unloading within 2400 ticks");
                        continue;
                    }
                    job.level.save(null, true, false);
                    MinecraftForge.EVENT_BUS.post(new LevelEvent.Unload(job.level));
                    job.level.close(); job.closed = true;
                }
                String packId = PersonalPlotStore.packId(job.owner);
                for (var pack : server.getResourceManager().listPacks().filter(p -> p.packId().equals(packId)).toList()) {
                    if (!(pack instanceof PersonalPlotPackAccess access)) throw new IOException("Personal pack cannot be safely retired");
                    access.enhancedcore$retirePersonalPack();
                }
                server.getPackRepository().removePack(packId);
                job.worker = CompletableFuture.runAsync(() -> {
                    try { cleanup(job.root, job.owner); }
                    catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }, Util.backgroundExecutor());
            } catch (IOException | RuntimeException failure) { job.failed = true; failed(server, job, failure); }
        }
    }
    private static void failed(MinecraftServer server, Job job, Throwable failure) {
        GTLEnhancedcore.LOGGER.error("Personal dimension deletion remains pending for {}; restart retries durable cleanup", job.owner, failure);
        notify(server, job.owner, "delete_failed");
    }
    private static void notify(MinecraftServer server, UUID owner, String key) {
        for (var player : server.getPlayerList().getPlayers()) if (player.getUUID().equals(owner) || server.isDedicatedServer() && player.hasPermissions(2))
            player.sendSystemMessage(Component.translatable("gtl_enhancedcore.personal_plot." + key));
    }
    /** Only the two exact UUID-derived targets are removable; links and junction escapes fail closed. */
    public static void cleanup(Path root, UUID owner) throws IOException {
        Path base = root.toRealPath();
        deleteTree(base, PersonalPlotStore.dimensionDirectory(base, owner));
        Path pack = PersonalPlotStore.packPath(base, owner); verifyPath(base, pack);
        if (Files.exists(pack, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(pack, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Personal archive is not a regular file");
        Files.deleteIfExists(pack);
    }
    private static void verifyPath(Path root, Path target) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        if (normalized.equals(root) || !normalized.startsWith(root)) throw new IOException("Personal deletion path escaped save root");
        for (Path cursor = normalized; !cursor.equals(root); cursor = cursor.getParent()) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor)))
                throw new IOException("Personal deletion refuses linked paths");
        }
    }
    private static void deleteTree(Path root, Path target) throws IOException {
        verifyPath(root, target); if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                verifyPath(root, directory); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                verifyPath(root, file); Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error; Files.delete(directory); return FileVisitResult.CONTINUE;
            }
        });
    }
}
