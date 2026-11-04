package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.*;
import com.gtl.enhancedcore.common.cloud.transfer.*;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Opt-in task-owned Forge fixture; all inventory and journal calls use production code. */
public final class CloudUploadRecoveryRuntimeChecks {
    private final MinecraftServer server;
    private final ServerLevel level;
    private final Path directory, reference;
    private final List<String> checks = new ArrayList<>();
    private final List<CompoundTag> snapshots = new ArrayList<>();
    private final CompoundTag saved = new CompoundTag();
    private final List<Blocker> blockers = new ArrayList<>();
    private CloudPatternLibraryMachine main;
    private ServerPlayer player;
    private ModularUIContainer menu;
    private CloudPatternStore store;
    private CloudPatternStore.Library library;
    private CloudPatternStore.Entry oldEntry, newEntry;
    private UUID operation;
    private CompoundTag catalysts;
    private ItemStack original;
    private int step, ticks, stableTicks;
    private boolean done;
    private Blocker blocker;
    private AtomicBoolean watching;
    private volatile Throwable watchFailure;
    public CloudUploadRecoveryRuntimeChecks(MinecraftServer server) throws Exception {
        this.server = server; level = server.overworld(); directory = server.getServerDirectory().toPath().toRealPath();
        Path root = Path.of(System.getProperty("gtl.enhancedcore.auditRoot")).toRealPath();
        check(directory.startsWith(root) && !directory.equals(root) && Files.isRegularFile(directory.resolve("upload-recovery.marker"))
                && server.getWorldPath(LevelResource.ROOT).toRealPath().startsWith(directory), "Fresh marked isolated Forge world; no player world opened");
        reference = server.getWorldPath(LevelResource.ROOT).resolve("upload-recovery-reference.dat");
        store = CloudPatternStore.get(level);
    }
    static void check(boolean valid, String label, List<String> checks) { if (!valid) throw new AssertionError(label); checks.add(label); }
    private void check(boolean valid, String label) { check(valid, label, checks); }
    public void tick() {
        if (done) return;
        try {
            if (main != null) set(main, "isFormed", true);
            if (player != null && menu != null) player.containerMenu = menu;
            if (++ticks > 2400) throw new AssertionError("Runtime phase exceeded 2400 ticks at step " + step);
            if (System.getProperty("gtl.enhancedcore.uploadRecoveryPhase", "seed").equals("verify")) { verify(); finish(null); return; }
            switch (step) {
                case 0 -> { setup("normal", 0); original = pattern(1); upload(original.copy()); step++; }
                case 1 -> {
                    if (busy()) return;
                    oldEntry = onlyAvailable();
                    check(main.menuAuthorized(player), "Post-upload authorization: formed=" + main.isFormed()
                            + "; sameLevel=" + (player.serverLevel() == level) + "; actorPos=" + player.position()
                            + "; distance=" + player.distanceToSqr(main.getPos().getX()+.5, main.getPos().getY()+.5, main.getPos().getZ()+.5)
                            + "; chunk=" + (level.getChunkSource().getChunkNow(main.getPos().getX() >> 4, main.getPos().getZ() >> 4) != null)
                            + "; holder=" + (level.getBlockEntity(main.getPos()) == main.getHolder().getSelf())
                            + "; interact=" + level.mayInteract(player,main.getPos()) + "; claim=" + CloudLibraryService.claimAllows(player,level,main.getPos())
                            + "; libraryActor=" + library.authorized(player.getUUID())
                            + "; ftb=" + dev.ftb.mods.ftbchunks.api.FTBChunksAPI.api().isManagerLoaded()
                            + "; menu=" + (menu.getModularUI().holder == main));
                    check(!main.cloud$importQuarantined() && library.storedCount() == 1, "Initial native cursor upload finishes without quarantine");
                    CompoundTag p = new CompoundTag(); p.putString("selected", oldEntry.id().toString());
                    main.cloudUiAction(player, "pattern_config", p);
                    var s = main.cloudUiSnapshot(player, "", "", 0, oldEntry.id().toString());
                    var items = main.cloudPatternItemCatalysts(player, s.getString("catalystEntry") + ":" + s.getLong("catalystAccessEpoch"));
                    var remainder = items.insertItem(0, new ItemStack(Items.DIAMOND, 3), false, true);
                    if (!remainder.isEmpty() || CloudLibraryService.itemContents(oldEntry.catalysts()).getStackInSlot(0).getCount() != 3)
                        throw new AssertionError("Catalyst menu insertion failed; remainder=" + remainder + "; menuAuthorized=" + main.menuAuthorized(player) + "; snapshot=" + s + "; catalysts=" + oldEntry.catalysts());
                    check(true, "Single-original real menu owns exactly three physical catalysts");
                    catalysts = oldEntry.catalysts(); UUID export = UUID.randomUUID();
                    ItemStack returned = store.extractFromCore(library.id(), player.getUUID(), oldEntry.id(), export);
                    store.acknowledge(library.id(), player.getUUID(), export); store.checkpoint();
                    check(ItemStack.matches(returned, original) && oldEntry.status() == CloudLedger.Status.EXPORTED
                            && oldEntry.catalysts().equals(catalysts), "extractFromCore exports one original and retains its exact catalyst escrow");
                    upload(returned); step++;
                }
                case 2 -> {
                    if (busy()) return;
                    newEntry = onlyAvailable();
                    check(!newEntry.id().equals(oldEntry.id()) && newEntry.catalysts().isEmpty() && oldEntry.catalysts().equals(catalysts),
                            "Re-upload keeps new entry identity independent from exported catalyst escrow");
                    int receipts = library.receipts().size(); int journals = journalCount();
                    upload(original.copy());
                    check(player.containerMenu.getCarried().getCount() == 1 && ItemStack.matches(player.containerMenu.getCarried(), original)
                            && !main.cloud$importQuarantined() && library.receipts().size() == receipts && journalCount() == journals,
                            "Second exact re-upload rejects before WAL/debit and never quarantines the healthy core");
                    check(library.storedCount() == 1 && oldEntry.catalysts().equals(catalysts) && newEntry.catalysts().isEmpty(),
                            "Rejected duplicate conserves one external original, one stored original and three old catalysts");
                    saved.putUUID("normalLibrary", library.id()); saved.putUUID("oldEntry", oldEntry.id()); saved.putUUID("newEntry", newEntry.id()); saved.put("catalysts", catalysts);
                    setup("preack", 16); blocker = new Blocker(authority()); blockers.add(blocker); blocker.install(); upload(pattern(2)); operation = uploadOperation(); step++;
                }
                case 3 -> {
                    if (busy()) return;
                    failed(false); deniedOwner(); blocker.restore(); recover();
                    var entry = onlyAvailable(); UUID identity = entry.id(); recover();
                    check(library.storedCount() == 1 && onlyAvailable().id().equals(identity) && !main.cloud$importQuarantined(),
                            "Owner pre-ACK retry is idempotent and never copies the reserved original");
                    saved.putUUID("preLibrary", library.id()); saved.putUUID("preEntry", identity);
                    setup("postack", 32); watching = new AtomicBoolean(true); watchFailure = null;
                    upload(pattern(3)); operation = uploadOperation();
                    blocker = new Blocker(authority()); blockers.add(blocker); watchCommit(operation, blocker); step++;
                }
                case 4 -> {
                    if (watchFailure != null) throw new AssertionError("COMMITTED stage observer failed", watchFailure);
                    if (busy()) return;
                    failed(true); blocker.restore(); store.retryFailedWrites();
                    var entry = library.entry(library.receipt(operation).subject());
                    var node = store.createNode(player.getUUID(), library.id(), level.dimension().location(), 320L);
                    store.claimMode(node.id(), UUID.randomUUID(), List.of(new ResourceLocation("gtceu", "assembler")));
                    store.lease(node.id(), entry.id(), 7, UUID.randomUUID()); var lease = entry.lease();
                    recover();
                    check(entry.status() == CloudLedger.Status.LEASED && Objects.equals(entry.lease(), lease)
                            && library.receipt(operation).status().equals("ACKNOWLEDGED"), "ACK retry preserves the existing lease and never downgrades ACK");
                    UUID export = UUID.randomUUID(); var output = store.extractFromCore(library.id(), player.getUUID(), entry.id(), export);
                    store.acknowledge(library.id(), player.getUUID(), export); store.checkpoint();
                    check(output.getCount() == 1, "Post-ACK original exports once after lease recovery");
                    CloudImportJournal.reviewUpload(level, operation, new IllegalStateException("Audit repeated final-write evidence"));
                    quarantine(operation); recover();
                    check(entry.status() == CloudLedger.Status.EXPORTED && entry.lease() == null && library.storedCount() == 0
                            && library.receipt(operation).status().equals("ACKNOWLEDGED"), "ACK replay cannot resurrect an exported original or its former lease");
                    saved.putUUID("postLibrary", library.id()); saved.putUUID("postEntry", entry.id()); saved.putUUID("postOperation", operation);
                    setup("unloaded", 64); watching = new AtomicBoolean(true); watchFailure = null;
                    upload(pattern(5)); operation = uploadOperation(); set(main, "uploadSourceLoaded", false);
                    blocker = new Blocker(authority()); blockers.add(blocker); watchCommit(operation, blocker); step++;
                }
                case 5 -> {
                    if (watchFailure != null) throw new AssertionError("Unloaded COMMITTED observer failed", watchFailure);
                    if (busy()) return;
                    check(blocker.installed && !main.cloud$importQuarantined(), "Unloaded old object does not receive a misleading live quarantine callback");
                    check(NbtIo.readCompressed(journal(operation).toFile()).getBoolean("review"), "Final IO failure after unload records durable world-level review evidence");
                    set(main, "uploadSourceLoaded", true); CloudImportJournal.reconcile(main);
                    check(main.cloud$importQuarantined(), "Production source reconcile restores isolation from unloaded failure evidence");
                    blocker.restore(); recover(); var restored = onlyAvailable();
                    check(library.receipt(operation).status().equals("ACKNOWLEDGED") && restored.status() == CloudLedger.Status.AVAILABLE,
                            "Owner reconciles unloaded final-write failure without downgrading acknowledged authority");
                    saved.putUUID("unloadedLibrary", library.id()); saved.putUUID("unloadedEntry", restored.id());
                    UUID delegate = UUID.randomUUID(); library.grant(player.getUUID(), delegate, true);
                    var sink = CloudImportJournal.sink(level, library.id(), delegate); var expected = pattern(6);
                    operation = sink.prepare(new CloudBufferImport.Source(level.dimension().location().toString(), main.getPos().asLong(), main.cloud$sourceIdentity()), 0, expected, new CompoundTag());
                    main.cloud$markProcessed(operation); sink.commit(operation, expected.copy());
                    UUID subject = library.receipt(operation).subject(); library.grant(player.getUUID(), delegate, false);
                    CloudImportJournal.reviewUpload(level, operation, new IllegalStateException("Audit revoked historical uploader")); quarantine(operation); recover();
                    check(!library.authorized(delegate) && library.entry(subject).status() == CloudLedger.Status.AVAILABLE
                            && library.receipt(operation).status().equals("ACKNOWLEDGED"), "Revoking historical uploader never prevents current owner recovery or re-ACKs as the revoked actor");
                    unknown(); store.checkpoint(); NbtIo.writeCompressed(saved, reference.toFile()); finish(null);
                }
                default -> throw new AssertionError("Unexpected runtime step " + step);
            }
        } catch (Throwable failure) { finish(failure); }
    }
    private void setup(String label, int x) throws Exception {
        if (player != null) player.containerMenu = player.inventoryMenu;
        BlockPos pos = new BlockPos(x, 90, 0); level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true); level.getChunkAt(pos);
        var definition = Objects.requireNonNull(GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_library")));
        level.setBlockAndUpdate(pos, definition.defaultBlockState());
        main = (CloudPatternLibraryMachine)((MetaMachineBlockEntity)level.getBlockEntity(pos)).getMetaMachine();
        UUID owner = UUID.nameUUIDFromBytes(("upload-recovery-20261103-" + label).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        player = FakePlayerFactory.get(level, new GameProfile(owner, "UploadRecovery")); player.setPos(x + .5, 90.5, .5);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE); main.onMachinePlaced(player, definition.asStack());
        set(main, "isFormed", true); set(main, "uploadSourceLoaded", true);
        var ui = main.createUI(player); ui.initWidgets(); menu = new ModularUIContainer(ui, 90 + x); player.containerMenu = menu;
        library = store.library(main.libraryId()); store.checkpoint();
        check(main.menuAuthorized(player), "Actual formed library menu is authorized for " + label);
    }
    private ItemStack pattern(int amount) {
        return PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), amount)});
    }
    private void upload(ItemStack stack) { stableTicks = 0; player.containerMenu.setCarried(stack); main.cloudUiAction(player, "import_cursor", new CompoundTag()); }
    private boolean busy() throws Exception { if (get(main, "inboxUpload") != null) { stableTicks = 0; return true; } return ++stableTicks < 4; }
    private CloudPatternStore.Entry onlyAvailable() { return library.entries().stream().filter(e -> e.status() != CloudLedger.Status.EXPORTED).findFirst().orElseThrow(); }
    private UUID uploadOperation() { return library.receipts().stream().filter(r -> r.kind().equals("UPLOAD")).findFirst().orElseThrow().operation(); }
    private Path authority() { return server.getWorldPath(LevelResource.ROOT).resolve("data/gtl_cloud_library_" + library.id() + ".dat"); }
    private Path journal(UUID tx) { return server.getWorldPath(LevelResource.ROOT).resolve("data/gtl_cloud_import/")
            .resolve(UUID.nameUUIDFromBytes(level.dimension().location().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "_" + main.getPos().asLong()).resolve(tx + ".dat"); }
    private int journalCount() throws Exception { try (var files = Files.walk(server.getWorldPath(LevelResource.ROOT).resolve("data/gtl_cloud_import"))) { return (int)files.filter(p -> p.toString().endsWith(".dat")).count(); } }
    private void watchCommit(UUID tx, Blocker target) {
        final Path evidence = journal(tx); final long deadline = System.nanoTime() + 30_000_000_000L;
        class Observer implements Runnable {
            public void run() {
                if (!watching.get()) return;
                try {
                    if (Files.isRegularFile(evidence) && NbtIo.readCompressed(evidence.toFile()).getString("status").equals("COMMITTED")) {
                        target.install(); watching.set(false); return;
                    }
                    if (System.nanoTime() > deadline) throw new AssertionError("COMMITTED journal was not observed before final checkpoint");
                    Thread.sleep(1); CloudImportIo.run(this);
                } catch (Throwable failure) { watchFailure = failure; watching.set(false); }
            }
        }
        CloudImportIo.run(new Observer());
    }
    private void failed(boolean ack) throws Exception {
        check(blocker.installed && main.cloud$importQuarantined(), "Real asynchronous " + (ack ? "post-ACK" : "pre-ACK") + " partition write failure quarantines the source");
        library = store.libraryForReview(main.libraryId());
        check(library.receipt(operation).status().equals(ack ? "ACKNOWLEDGED" : "EXTERNAL_PENDING"), "Failed write preserves exact " + (ack ? "ACKNOWLEDGED" : "EXTERNAL_PENDING") + " receipt state");
        var snapshot = main.cloudUiSnapshot(player, "", "", 0, ""); snapshots.add(snapshot.copy());
        check(snapshot.getBoolean("importQuarantined") && snapshot.getBoolean("recoveryOwner") && !snapshot.getBoolean("recoveryBusy"), "Failure snapshot exposes quarantine and owner recovery without stale busy state");
        check(snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND).stream().map(t -> (CompoundTag)t)
                .anyMatch(r -> r.getString("operation").equals(operation.toString())), "Failed upload remains a visible structured recovery row including COMMITTED plus ACK");
    }
    private void deniedOwner() {
        var outsider = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RecoveryOutsider")); outsider.setPos(player.getX(), player.getY(), player.getZ());
        boolean denied = false; try { CloudImportJournal.retry(level, library.id(), outsider, operation); } catch (SecurityException expected) { denied = true; }
        check(denied && main.cloud$importQuarantined(), "Non-owner retry cannot clear isolation or repair ownership");
    }
    private void recover() {
        CompoundTag payload = new CompoundTag(); payload.putString("recoveryId", operation.toString());
        main.cloudUiAction(player, "review_transfer", payload);
        check(!main.cloud$importQuarantined(), "Owner menu recovery completes selected transaction and clears only proven isolation");
    }
    private void quarantine(UUID tx) {
        var markers = main.cloud$saveImportMarkers(); markers.putUUID("reviewOperation", tx); main.cloud$loadImportMarkers(markers);
        main.cloud$quarantineImport("Upload " + tx + " requires review");
    }
    private void unknown() throws Exception {
        setup("unknown", 48); var expected = pattern(4);
        var sink = CloudImportJournal.sink(level, library.id(), player.getUUID());
        operation = sink.prepare(new CloudBufferImport.Source(level.dimension().location().toString(), main.getPos().asLong(), main.cloud$sourceIdentity()), 0, expected, new CompoundTag());
        quarantine(operation); var markers = main.cloud$saveImportMarkers(); var wrong = markers.copy(); wrong.putUUID("identity", UUID.randomUUID()); main.cloud$loadImportMarkers(wrong);
        boolean denied = false; try { CloudImportJournal.retry(level, library.id(), player, operation); } catch (SecurityException rejected) { denied = true; }
        check(denied && main.cloud$importQuarantined() && library.storedCount() == 0, "Wrong source incarnation remains isolated without creating an original");
        main.cloud$loadImportMarkers(markers); denied = false;
        try { CloudImportJournal.retry(level, library.id(), player, operation); } catch (IllegalStateException rejected) { denied = true; }
        check(denied && main.cloud$importQuarantined() && library.storedCount() == 0 && library.receipt(operation) == null,
                "Unknown empty-source debit evidence remains isolated and never invents a receipt");
        for (int index = 7; index <= 10; index++)
            sink.prepare(new CloudBufferImport.Source(level.dimension().location().toString(), main.getPos().asLong(), main.cloud$sourceIdentity()), 0, pattern(index), new CompoundTag());
        var firstPage = main.cloudUiSnapshot(player, "", "", 0, "", 0);
        var lastPage = main.cloudUiSnapshot(player, "", "", 0, "", 1);
        check(firstPage.getList("recoveryEntries", Tag.TAG_COMPOUND).size() == 4 && firstPage.getInt("recoveryPages") == 2
                && lastPage.getList("recoveryEntries", Tag.TAG_COMPOUND).size() == 1 && lastPage.getInt("recoveryPage") == 1,
                "Production same-library review catalog paginates five real uncertain journals as four rows plus final row");
        snapshots.add(firstPage.copy()); snapshots.add(lastPage.copy());
    }
    private void verify() throws Exception {
        check(Files.isRegularFile(reference), "Separate-JVM verification reads the completed seed reference"); var ref = NbtIo.readCompressed(reference.toFile());
        var normal = store.library(ref.getUUID("normalLibrary")); var old = normal.entry(ref.getUUID("oldEntry")); var fresh = normal.entry(ref.getUUID("newEntry"));
        check(old.status() == CloudLedger.Status.EXPORTED && old.catalysts().equals(ref.getCompound("catalysts")) && fresh.catalysts().isEmpty() && normal.storedCount() == 1,
                "Independent Forge restart preserves exported catalyst escrow and new original identity");
        check(store.library(ref.getUUID("preLibrary")).entry(ref.getUUID("preEntry")).status() == CloudLedger.Status.AVAILABLE,
                "Recovered pre-ACK original remains available exactly once after restart");
        var post = store.library(ref.getUUID("postLibrary"));
        check(post.entry(ref.getUUID("postEntry")).status() == CloudLedger.Status.EXPORTED && post.storedCount() == 0
                && post.receipt(ref.getUUID("postOperation")).status().equals("ACKNOWLEDGED"), "Restart does not resurrect exported ACK original");
        check(store.library(ref.getUUID("unloadedLibrary")).entry(ref.getUUID("unloadedEntry")).status() == CloudLedger.Status.AVAILABLE,
                "Independent restart preserves recovery of final-write failure recorded while source was unloaded");
        BlockPos pos = new BlockPos(32,90,0); level.setChunkForced(pos.getX() >> 4,pos.getZ() >> 4,true); level.getChunkAt(pos);
        main = (CloudPatternLibraryMachine)((MetaMachineBlockEntity)level.getBlockEntity(pos)).getMetaMachine();
        set(main,"isFormed",true); set(main,"uploadSourceLoaded",true); library = post; operation = ref.getUUID("postOperation");
        player = FakePlayerFactory.get(level,new GameProfile(main.owner(),"LegacyRecovery")); player.setPos(32.5,90.5,.5);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var ui = main.createUI(player); ui.initWidgets(); menu = new ModularUIContainer(ui,198); player.containerMenu = menu;
        check(main.menuAuthorized(player),"Restarted exact source is owner-menu authorized for legacy-string review");
        var wal = NbtIo.readCompressed(journal(operation).toFile()); wal.remove("review"); wal.remove("failure");
        var writer = CloudImportJournal.class.getDeclaredMethod("write",ServerLevel.class,CompoundTag.class); writer.setAccessible(true); writer.invoke(null,level,wal);
        var markers = main.cloud$saveImportMarkers(); markers.remove("reviewOperation"); markers.putString("quarantine","Upload " + operation + " requires review"); main.cloud$loadImportMarkers(markers);
        check(!wal.getBoolean("review") && !wal.contains("failure") && !markers.hasUUID("reviewOperation") && main.cloud$importQuarantined(),
                "Fixture retains only the exact legacy quarantine string with COMMITTED plus ACK evidence");
        var legacy = main.cloudUiSnapshot(player,"","",0,"");
        check(legacy.getList("recoveryEntries",Tag.TAG_COMPOUND).stream().map(t -> (CompoundTag)t).anyMatch(r -> r.getString("operation").equals(operation.toString())
                && r.getString("journalStatus").equals("COMMITTED") && r.getString("status").equals("ACKNOWLEDGED")),
                "Legacy-string-only quarantine still exposes its completed acknowledged transaction as a recovery row");
        int before = main.importInventory().getStackInSlot(0).getCount(); recover();
        check(post.entry(ref.getUUID("postEntry")).status() == CloudLedger.Status.EXPORTED && post.storedCount() == 0
                && main.importInventory().getStackInSlot(0).getCount() == before && post.receipt(operation).status().equals("ACKNOWLEDGED"),
                "Owner legacy-string recovery never re-debits source, downgrades ACK or resurrects exported original");
        store.checkpoint();
    }
    private void finish(Throwable failure) {
        done = true; if (watching != null) watching.set(false);
        for (var b : blockers) try { b.restore(); } catch (Exception error) { if (failure == null) failure = error; }
        if (failure != null) failure.printStackTrace();
        var report = new LinkedHashMap<String, Object>(); report.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) report.put("error", failure.toString()); report.put("checks", checks); report.put("step", step);
        report.put("scope", "Real Forge fresh-world production menu, inventory, CloudImportIo and partition filesystem failure; controlled formed and uploadSourceLoaded lifecycle flags, forced task chunks; not full multiblock structure, actual unload event or player AE network");
        String phase = System.getProperty("gtl.enhancedcore.uploadRecoveryPhase", "seed");
        try {
            Files.writeString(directory.resolve("upload-recovery-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (!snapshots.isEmpty()) {
                var rows = new ListTag(); snapshots.forEach(rows::add); var tags = new CompoundTag(); tags.put("snapshots", rows);
                NbtIo.writeCompressed(tags, directory.resolve("upload-recovery-snapshots.dat").toFile());
            }
        } catch (Exception error) { error.printStackTrace(); }
        server.halt(false);
    }
    private static final class Blocker {
        final Path target, backup; volatile boolean installed;
        Blocker(Path target) { this.target = target; backup = target.resolveSibling(target.getFileName() + ".audit-saved"); }
        synchronized void install() throws Exception {
            if (installed) return; if (Files.exists(backup)) throw new IllegalStateException("Unexpected audit backup");
            if (Files.exists(target)) Files.move(target, backup); Files.createDirectory(target); installed = true;
        }
        synchronized void restore() throws Exception {
            if (!installed) return; Files.delete(target); if (Files.exists(backup)) Files.move(backup, target); installed = false;
        }
    }
    private static Object get(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target); } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try { var f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
}
