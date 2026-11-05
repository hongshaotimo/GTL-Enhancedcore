package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.CloudBufferImport;
import com.gtl.enhancedcore.common.cloud.transfer.CloudImportIo;
import com.gtl.enhancedcore.common.cloud.transfer.CloudImportJournal;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.item.PatternGeneratorSettings;
import com.gtl.enhancedcore.common.item.PatternGeneratorTransfer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;

/** Pauses only the real file worker; all source checks and production callbacks use natural server ticks. */
public final class CloudInboxPreparedRuntimeChecks {
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static CloudPatternStore.Library library;
    private static ServerPlayer player, priorActor;
    private static List<String> checks;
    private static ListTag savedInventory;
    private static Vec3 savedPosition;
    private static Map<UUID, ServerPlayer> online;
    private static net.minecraft.world.inventory.AbstractContainerMenu savedMenu;
    private static java.lang.reflect.Field pendingField, activeField;
    private static CountDownLatch workerEntered, releaseWorker;
    private static CompletableFuture<Void> gate, injectedCompletion;
    private static UUID operation;
    private static ItemStack expected;
    private static Set<UUID> activeBefore;
    private static Path reportDirectory;
    private static int stage, ticks, since, baselineEntries, baselineUploads, successful, removalProbes;
    private static boolean registered, cleaned;
    private static final List<Map<String, Object>> evidence = new ArrayList<>();
    private static final List<UUID> successfulOperations = new ArrayList<>();
    private static final List<ItemStack> retained = new ArrayList<>();

    private CloudInboxPreparedRuntimeChecks() {}

    public static void begin(ServerLevel world, CloudPatternLibraryMachine core, ServerPlayer actor, List<String> results) throws Exception {
        level = world; main = core; player = actor; checks = results;
        Path save = world.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        reportDirectory = save.getParent().getParent();
        Path auditRoot = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-compat-20261105").toAbsolutePath().normalize();
        require(reportDirectory.startsWith(auditRoot) && !reportDirectory.equals(auditRoot)
                && Files.isRegularFile(reportDirectory.resolve("cloud-compat.marker")), "new_marked_fixture_only");
        require(main.isFormed() && main.serviceGrid() != null && !main.generatorImportPending(), "real_formed_core_has_empty_physical_inbox");
        savedInventory = player.getInventory().save(new ListTag()); savedPosition = player.position(); savedMenu = player.containerMenu;
        stage = ticks = since = successful = removalProbes = 0; cleaned = false; registered = false;
        operation = null; evidence.clear(); successfulOperations.clear(); retained.clear();
        pendingField = CloudPatternLibraryMachine.class.getDeclaredField("inboxUpload"); pendingField.setAccessible(true);
        activeField = CloudImportJournal.class.getDeclaredField("ACTIVE_UPLOADS"); activeField.setAccessible(true);
        registerActor(); player.getInventory().clearContent(); player.setPos(Vec3.atCenterOf(main.getPos()));
        library = CloudPatternStore.get(level).library(main.libraryId());
        baselineEntries = library.entryCount(); baselineUploads = uploadCount();
        verifyRemovalProtection(false, "initial_idle");
        openWorkerGate(); write("RUNNING", "");
    }

    public static boolean step() throws Exception {
        try { return stepInternal(); }
        catch (Exception | Error failure) {
            releaseGate();
            try { write("FAIL", failure.toString()); } catch (Exception reporting) { failure.addSuppressed(reporting); }
            cleanup(); throw failure;
        }
    }

    private static boolean stepInternal() throws Exception {
        if (++ticks > 2400) throw new AssertionError("inbox_prepared:timeout_stage_" + stage);
        if (ticks % 40 == 0) write("RUNNING", "");
        switch (stage) {
            case 0 -> {
                if (!gateEntered()) return false;
                beginPhysicalToolOriginal("authorization_changed"); stage = 1;
            }
            case 1 -> {
                if (!nativeUploadWaiting()) return false;
                verifyWriterBlockedWithoutDebit("authorization_changed");
                verifyRemovalProtection(true, "before_PREPARED_authorization_changed");
                player.setPos(Vec3.atCenterOf(main.getPos()).add(20, 0, 0));
                require(!main.generatorImportAuthorized(player), "live_authorization_changes_after_prepare_was_queued");
                releaseGate(); since = ticks; stage = 2;
            }
            case 2 -> {
                if (!nativeUploadFinished()) return false;
                require(ticks > since && ItemStack.matches(main.importInventory().getStackInSlot(0), expected), "post_PREPARED_authorization_recheck_preserves_physical_inbox_original");
                verifyAbortedWithoutReceipt("authorization_changed"); retainInboxOriginal();
                player.setPos(Vec3.atCenterOf(main.getPos())); verifyRemovalProtection(false, "authorization_abort_finished");
                openWorkerGate(); stage = 3;
            }
            case 3 -> {
                if (!gateEntered()) return false;
                beginPhysicalToolOriginal("source_changed"); stage = 4;
            }
            case 4 -> {
                if (!nativeUploadWaiting()) return false;
                verifyWriterBlockedWithoutDebit("source_changed");
                verifyRemovalProtection(true, "before_PREPARED_source_changed");
                var extracted = main.importInventory().extractItem(0, 1, false, true);
                require(ItemStack.matches(extracted, expected), "ordinary_source_inventory_extraction_moves_exact_original_while_PREPARED_waits");
                retained.add(extracted); player.getInventory().setItem(20 + retained.size(), extracted.copy());
                releaseGate(); stage = 5;
            }
            case 5 -> {
                if (!nativeUploadFinished()) return false;
                require(main.importInventory().getStackInSlot(0).isEmpty(), "changed_source_is_never_recreated_or_debited_twice");
                verifyAbortedWithoutReceipt("source_changed"); verifyRemovalProtection(false, "source_abort_finished");
                PatternGeneratorTransfer.cancel(player); stage = 6;
            }
            case 6 -> {
                // A throwing authorization callback leaves a genuinely written PREPARED with no debit marker.
                // This is a controlled pre-debit callback failure, not a forged WAL or receipt.
                expected = pattern("pre_debit_callback_failure");
                require(main.stageGeneratorOriginal(player, expected.copy()), "physical_original_stages_before_controlled_callback_failure");
                var actorField = CloudPatternLibraryMachine.class.getDeclaredField("inboxActor"); actorField.setAccessible(true);
                actorField.set(main, null); // Prevent a second natural upload from racing the isolated recovery case.
                var pending = CloudImportJournal.prepareInboxUploadAsync(level, main.libraryId(), player.getUUID(),
                        new CloudBufferImport.Source(level.dimension().location().toString(), main.getPos().asLong(), main.cloud$sourceIdentity()),
                        expected, ignored -> { throw new IllegalStateException("AUDIT pre-debit authorization callback failure"); });
                operation = pending.operation(); injectedCompletion = pending.completion(); stage = 7;
            }
            case 7 -> {
                if (!injectedCompletion.isDone() || active().contains(operation)) return false;
                require(injectedCompletion.isCompletedExceptionally(), "controlled_pre_debit_callback_failure_is_observed");
                var wal = readWal(operation);
                require(wal.getString("status").equals("PREPARED") && wal.getBoolean("inboxAuthorizationPending")
                        && !main.cloud$processed(operation) && library.receipt(operation) == null, "real_PREPARED_has_no_active_upload_no_debit_marker_and_no_receipt");
                require(ItemStack.matches(main.importInventory().getStackInSlot(0), expected), "pre_debit_callback_failure_retains_exact_original");
                CloudImportJournal.reconcile(main);
                verifyAbortedWithoutReceipt("reconcile_unauthed_PREPARED");
                require(ItemStack.matches(main.importInventory().getStackInSlot(0), expected), "production_reload_reconcile_aborts_unauthed_intent_without_debit");
                CloudImportJournal.reconcile(main);
                require(ItemStack.matches(main.importInventory().getStackInSlot(0), expected) && library.receipt(operation) == null,
                        "repeat_reload_reconcile_is_idempotent_and_preserves_source");
                retainInboxOriginal(); openWorkerGate(); stage = 8;
            }
            case 8 -> {
                if (!gateEntered()) return false;
                beginPhysicalToolOriginal("success_0"); stage = 9;
            }
            case 9 -> {
                if (!nativeUploadWaiting()) return false;
                verifyWriterBlockedWithoutDebit("success_0"); verifyRemovalProtection(true, "before_PREPARED_success");
                // Enqueue this second gate after PREPARED but before its server
                // callback can submit DEBITED. This observes a real physical debit
                // behind the forced PREPARED file, without forging journal state.
                releaseGate(); openWorkerGate(); stage = 13;
            }
            case 10 -> {
                if (!nativeUploadFinished()) return false;
                verifyCommitted();
                verifyRemovalProtection(false, "successful_commit_" + successful);
                if (++successful < 3) { beginPhysicalToolOriginal("success_" + successful); stage = 11; }
                else stage = 12;
            }
            case 11 -> {
                if (operation == null) {
                    var changed = new java.util.HashSet<>(active()); changed.removeAll(activeBefore);
                    if (!changed.isEmpty()) {
                        require(changed.size() == 1, "one_real_async_operation_per_successful_source"); operation = changed.iterator().next();
                    } else {
                        // Fast callbacks may complete between helper ticks; exact payload identifies their real receipt.
                        for (var receipt : library.receipts()) if (receipt.kind().equals("UPLOAD")
                                && ItemStack.matches(library.entry(receipt.subject()).original(), expected)) {
                            operation = receipt.operation(); break;
                        }
                        if (operation == null) return false;
                    }
                }
                if (!nativeUploadFinished()) return false;
                stage = 10;
            }
            case 12 -> {
                require(successfulOperations.size() == 3 && library.entryCount() == baselineEntries + 3 && uploadCount() == baselineUploads + 3,
                        "exactly_three_successful_physical_originals_have_three_authority_upload_receipts");
                require(main.importInventory().getStackInSlot(0).isEmpty() && !main.generatorImportPending() && !main.cloud$importQuarantined(),
                        "three_real_uploads_finish_with_empty_inbox_and_healthy_core");
                require(retained.size() == 3 && retained.stream().allMatch(stack -> stack.getCount() == 1),
                        "three_cancelled_or_unauthed_originals_are_retained_once_outside_authority");
                exportSuccessfulOriginals();
                write("PASS", ""); cleanup(); return true;
            }
            case 13 -> {
                if (!gateEntered() || !main.cloud$processed(operation)) return false;
                var receipt = library.receipt(operation); var wal = readWal(operation);
                require(main.inboxUploadInProgress() && active().contains(operation) && main.importInventory().getStackInSlot(0).isEmpty()
                        && receipt != null && receipt.status().equals("EXTERNAL_PENDING") && wal.getString("status").equals("PREPARED")
                        && wal.getBoolean("inboxAuthorizationPending"), "real_debit_is_pending_behind_forced_PREPARED_before_DEBITED_worker_write");
                verifyRemovalProtection(true, "after_real_debit_before_durable_commit");
                CloudImportJournal.reconcile(main);
                require(readWal(operation).equals(wal) && library.receipt(operation).equals(receipt)
                        && !main.cloud$importQuarantined() && main.inboxUploadInProgress(),
                        "active_processed_PREPARED_reconcile_defers_to_live_commit_without_rewriting_or_duplicate_debit");
                evidence.add(Map.of("case", "after_real_debit_before_durable_commit", "operation", operation.toString(), "tick", ticks,
                        "WAL", "PREPARED", "receipt", receipt.status(), "sourceProcessed", true, "physicalInboxEmpty", true));
                releaseGate(); stage = 10;
            }
            default -> throw new AssertionError("inbox_prepared:unknown_stage_" + stage);
        }
        return false;
    }

    private static ItemStack pattern(String label) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
        stack.getOrCreateTag().putString("inboxPreparedAudit", label);
        // Effect identity is intentionally the same; full original NBT distinguishes these transaction fixtures.
        return stack;
    }

    private static void beginPhysicalToolOriginal(String label) throws Exception {
        require(!main.generatorImportPending() && !main.cloud$importQuarantined(), "inbox_available_for_" + label);
        expected = pattern(label); operation = null; activeBefore = Set.copyOf(active());
        var settings = new PatternGeneratorSettings(); settings.generatorId = UUID.randomUUID(); settings.transferArmed = true;
        var tool = GTLEnhancedcoreItems.PATTERN_GENERATOR.asStack(); tool.getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        var physical = expected.copy(); PatternGeneratorTransfer.mark(physical, settings); player.getInventory().setItem(9, physical);
        var hit = new BlockHitResult(Vec3.atCenterOf(main.getPos()), Direction.NORTH, main.getPos(), false);
        PatternGeneratorTransfer.begin(player, new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        require(player.getInventory().getItem(9).isEmpty() && ItemStack.matches(main.importInventory().getStackInSlot(0), expected),
                "real_tool_moves_one_original_to_physical_inbox_" + label);
    }

    private static void openWorkerGate() {
        workerEntered = new CountDownLatch(1); releaseWorker = new CountDownLatch(1);
        CountDownLatch entered = workerEntered, released = releaseWorker;
        gate = CloudImportIo.run(() -> {
            entered.countDown();
            try { if (!released.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("AUDIT file-worker gate expired"); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        });
    }

    private static boolean gateEntered() {
        if (gate.isCompletedExceptionally()) gate.join();
        return workerEntered.getCount() == 0;
    }

    private static void releaseGate() { if (releaseWorker != null) releaseWorker.countDown(); }

    private static boolean nativeUploadWaiting() throws Exception {
        if (pendingField.get(main) == null) return false;
        var changed = new java.util.HashSet<>(active()); changed.removeAll(activeBefore);
        require(changed.size() == 1, "one_real_async_operation_enters_PREPARED_worker_queue"); operation = changed.iterator().next(); return true;
    }

    private static boolean nativeUploadFinished() throws Exception {
        return operation != null && pendingField.get(main) == null && !active().contains(operation);
    }

    private static void verifyRemovalProtection(boolean pending, String label) {
        require(main.inboxUploadInProgress() == pending && !main.cloud$importQuarantined(), "removal_probe_has_expected_live_upload_state_" + label);
        var pos = main.getPos(); var state = level.getBlockState(pos); var holder = level.getBlockEntity(pos);
        var breaking = new BlockEvent.BreakEvent(level, pos, state, player);
        require(!breaking.isCanceled(), "real_Forge_BreakEvent_starts_uncancelled_" + label);
        MinecraftForge.EVENT_BUS.post(breaking);
        require(breaking.isCanceled() == pending, "real_Forge_BreakEvent_cancels_only_while_upload_is_pending_" + label);

        var explosion = new Explosion(level, player, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                4F, false, Explosion.BlockInteraction.DESTROY);
        var control = pos.above();
        explosion.getToBlow().add(pos); explosion.getToBlow().add(control);
        var detonating = new ExplosionEvent.Detonate(level, explosion, new ArrayList<>());
        MinecraftForge.EVENT_BUS.post(detonating);
        require(detonating.getAffectedBlocks().contains(pos) != pending,
                "real_Forge_ExplosionEvent_filters_core_only_while_upload_is_pending_" + label);
        require(detonating.getAffectedBlocks().contains(control), "real_Forge_ExplosionEvent_retains_other_candidate_" + label);
        require(level.getBlockEntity(pos) == holder && level.getBlockState(pos).equals(state), "event_list_probe_keeps_fixture_core_in_place_" + label);
        removalProbes++;
        evidence.add(Map.of("case", "removal_events_" + label, "tick", ticks, "pending", pending,
                "breakCancelled", breaking.isCanceled(), "explosionCoreRetained", detonating.getAffectedBlocks().contains(pos),
                "explosionControlRetained", detonating.getAffectedBlocks().contains(control)));
    }

    private static void verifyWriterBlockedWithoutDebit(String label) throws Exception {
        require(releaseWorker.getCount() == 1 && workerEntered.getCount() == 0 && !Files.exists(walPath(operation)),
                "real_PREPARED_file_cannot_be_written_before_worker_gate_release_" + label);
        require(ItemStack.matches(main.importInventory().getStackInSlot(0), expected) && !main.cloud$processed(operation)
                && library.receipt(operation) == null && library.entryCount() == baselineEntries,
                "blocked_PREPARED_never_debits_inbox_marks_processed_or_reserves_authority_" + label);
        evidence.add(Map.of("case", label, "operation", operation.toString(), "tick", ticks, "writerBlocked", true,
                "WALExists", false, "sourceOriginalRetained", true, "receiptExists", false));
    }

    private static void verifyAbortedWithoutReceipt(String label) throws Exception {
        var wal = readWal(operation);
        require(wal.getString("status").equals("ABORTED") && library.receipt(operation) == null
                && !main.cloud$processed(operation) && !active().contains(operation) && !main.cloud$importQuarantined(),
                "durable_ABORTED_has_no_receipt_no_debit_no_active_upload_" + label);
        evidence.add(Map.of("case", label, "operation", operation.toString(), "tick", ticks, "WAL", "ABORTED",
                "receiptExists", false, "active", false));
    }

    private static void verifyCommitted() throws Exception {
        var receipt = library.receipt(operation); var wal = readWal(operation);
        require(receipt != null && receipt.kind().equals("UPLOAD") && receipt.status().equals("ACKNOWLEDGED")
                && wal.getString("status").equals("COMMITTED") && wal.getUUID("entry").equals(receipt.subject())
                && !wal.getBoolean("inboxAuthorizationPending") && ItemStack.matches(ItemStack.of(wal.getCompound("expected")), expected)
                && ItemStack.matches(library.entry(receipt.subject()).original(), expected) && main.cloud$processed(operation),
                "successful_real_upload_has_exact_COMMITTED_WAL_ACK_payload_and_debit_marker_" + successful);
        require(!active().contains(operation) && main.importInventory().getStackInSlot(0).isEmpty(), "successful_upload_releases_active_set_after_completion_" + successful);
        successfulOperations.add(operation);
        evidence.add(Map.of("case", "success_" + successful, "operation", operation.toString(), "entry", receipt.subject().toString(), "tick", ticks,
                "WAL", "COMMITTED", "receipt", "ACKNOWLEDGED", "WALBytes", Files.size(walPath(operation))));
        PatternGeneratorTransfer.cancel(player);
    }

    private static void retainInboxOriginal() {
        PatternGeneratorTransfer.cancel(player);
        var original = main.importInventory().extractItem(0, 1, false, true);
        require(ItemStack.matches(original, expected), "cancelled_original_can_be_recovered_by_ordinary_inventory_extraction");
        retained.add(original); player.getInventory().setItem(20 + retained.size(), original.copy());
    }

    private static void exportSuccessfulOriginals() throws Exception {
        var store = CloudPatternStore.get(level);
        for (int index = 0; index < successfulOperations.size(); index++) {
            var upload = successfulOperations.get(index); var uploadReceipt = library.receipt(upload);
            var entry = library.entry(uploadReceipt.subject()); var original = entry.original(); var wal = readWal(upload);
            var export = UUID.randomUUID();
            var returned = store.extractFromCore(main.libraryId(), player.getUUID(), entry.id(), export);
            require(returned.getCount() == 1 && ItemStack.matches(original, returned),
                    "cleanup_returns_exact_successful_original_NBT_once_" + index);
            store.acknowledge(main.libraryId(), player.getUUID(), export); store.checkpointLibrary(main.libraryId());
            require(entry.status() == CloudLedger.Status.EXPORTED && entry.leases().isEmpty()
                            && store.extractFromCore(main.libraryId(), player.getUUID(), entry.id(), export).isEmpty(),
                    "cleanup_revokes_successful_original_routes_and_export_replay_cannot_duplicate_" + index);
            require(library.receipt(upload).equals(uploadReceipt) && readWal(upload).equals(wal),
                    "cleanup_preserves_successful_COMMITTED_WAL_and_ACKNOWLEDGED_upload_receipt_" + index);
            evidence.add(Map.of("case", "cleanup_success_" + index, "uploadOperation", upload.toString(),
                    "exportOperation", export.toString(), "entry", entry.id().toString(),
                    "returnedOriginal", returned.save(new CompoundTag()).toString(), "status", entry.status().name(),
                    "remainingLeases", entry.leases().size()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> active() throws Exception {
        var values = (Map<MinecraftServer, Set<UUID>>) activeField.get(null);
        return values.getOrDefault(level.getServer(), Set.of());
    }

    private static int uploadCount() { return (int) library.receipts().stream().filter(receipt -> receipt.kind().equals("UPLOAD")).count(); }
    private static Path walPath(UUID id) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/gtl_cloud_import")
                .resolve(UUID.nameUUIDFromBytes(level.dimension().location().toString().getBytes(StandardCharsets.UTF_8)) + "_" + main.getPos().asLong())
                .resolve(id + ".dat");
    }
    private static CompoundTag readWal(UUID id) throws Exception { return NbtIo.readCompressed(walPath(id).toFile()); }

    private static void write(String status, String failure) throws Exception {
        var report = new LinkedHashMap<String, Object>(); report.put("status", status); report.put("failure", failure); report.put("stage", stage); report.put("ticks", ticks);
        report.put("evidence", evidence); report.put("successfulOperations", successfulOperations); report.put("retainedCancelledOriginals", retained.size());
        report.put("checks", checks.stream().filter(check -> check.startsWith("inbox_prepared:")).toList());
        report.put("removalEventProbes", removalProbes);
        report.put("scope", "Real formed isolated central core, real physical generator transfer and natural importTick/callbacks. Only the serial file worker waits on bounded latches; the server never waits on them. A second naturally queued worker gate observes the real processed marker, empty physical inbox and EXTERNAL_PENDING receipt while the durable file is still PREPARED; production reconcile must leave that active operation untouched. Actual Forge EVENT_BUS BreakEvent and ExplosionEvent.Detonate are posted before PREPARED, after physical debit and after abort/commit: the real registered handlers must cancel breaks/filter the core from the affected-block list only during pending upload and preserve another list candidate. Events are probed without destroying blocks or executing an explosion. Distance changes exercise the live authorization predicate after PREPARED was queued; this is not a real FTB team or claim revocation test. Source change uses the public real inventory extraction. One actual journal API callback intentionally throws before debit, leaving a real PREPARED file; after active tracking naturally clears, production reconcile must write ABORTED and preserve the original without any receipt. That case controls inboxActor to stop an unrelated second upload and is not a separate JVM restart. No WAL/receipt/lease/processed/active state is injected. Actual chunk eviction, physical block removal/replacement, old-history retirement, PREPARED/ABORTED filesystem failure and server-stop timing are not exercised here. Three later normal physical uploads each require exact COMMITTED WAL plus ACKNOWLEDGED authority and source debit marker. Their three originals are then returned exactly once through normal extractFromCore, ACK and checkpoint; full original NBT, revoked leases, export replay rejection and preserved upload WAL/receipts are verified before the next scenario.");
        Files.writeString(reportDirectory.resolve("cloud-inbox-prepared.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    @SuppressWarnings("unchecked")
    private static void registerActor() throws Exception {
        var list = level.getServer().getPlayerList(); online = null;
        for (Class<?> type = list.getClass(); type != null && online == null; type = type.getSuperclass())
            for (var field : type.getDeclaredFields()) {
                String signature = field.getGenericType().getTypeName();
                if (!Map.class.isAssignableFrom(field.getType()) || !signature.contains("java.util.UUID") || !signature.contains(ServerPlayer.class.getName())) continue;
                field.setAccessible(true); online = (Map<UUID, ServerPlayer>) field.get(list); break;
            }
        require(online != null, "fixture_online_actor_index_available"); priorActor = online.get(player.getUUID());
        require(priorActor == null || priorActor == player, "fixture_never_replaces_another_online_actor");
        if (priorActor == null) { online.put(player.getUUID(), player); registered = true; }
        require(list.getPlayer(player.getUUID()) == player, "natural_importTick_resolves_registered_fixture_actor");
    }

    public static void cleanup() {
        releaseGate();
        if (cleaned || player == null) return;
        PatternGeneratorTransfer.cancel(player);
        if (savedInventory != null) { player.getInventory().clearContent(); player.getInventory().load(savedInventory); }
        if (savedPosition != null) player.setPos(savedPosition);
        if (savedMenu != null) player.containerMenu = savedMenu;
        if (registered && online != null) online.remove(player.getUUID(), player);
        registered = false; cleaned = true;
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("inbox_prepared:" + label);
        checks.add("inbox_prepared:" + label);
    }
}
