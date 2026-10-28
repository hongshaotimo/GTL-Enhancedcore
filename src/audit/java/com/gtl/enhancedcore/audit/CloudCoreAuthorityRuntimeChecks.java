package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.cloud.CloudLibraryService;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

/** Opt-in helper for an isolated Forge fixture. No event subscriber and never runs in a player world automatically.
 * Store ownership and persisted task bytes are tested here; full recipe execution requires the flow fixture.
 * Save seed's returned reference, then call verifyReloaded in a separate JVM to test real restart loading. */
public final class CloudCoreAuthorityRuntimeChecks {
    private CloudCoreAuthorityRuntimeChecks() {}
    public static CompoundTag seed(ServerLevel level, List<String> checks) throws Exception {
        var store = CloudPatternStore.get(level); UUID owner = UUID.randomUUID();
        UUID libraryId = store.defaultLibrary(owner); var library = store.library(libraryId);
        ItemStack source = new ItemStack(Items.PAPER, 2);
        source.getOrCreateTag().putString("authorityAudit", UUID.randomUUID().toString());
        ItemStack original = source.copyWithCount(1); UUID upload = UUID.randomUUID();
        var entry = store.upload(libraryId, owner, source, new CompoundTag(), upload);
        store.acknowledge(libraryId, owner, upload);
        check(source.getCount() == 1 && library.storedCount() == 1, "Upload owns exactly one original", checks);
        ItemStack duplicate = original.copy();
        rejects(() -> store.upload(libraryId, owner, duplicate, new CompoundTag(), UUID.randomUUID()),
                "Exact complete NBT duplicate remains rejected before debit", checks);
        check(duplicate.getCount() == 1, "Rejected duplicate preserves external source", checks);
        var items = CloudLibraryService.itemContents(new CompoundTag());
        items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 11));
        var catalysts = new CompoundTag(); catalysts.put("items", items.serializeNBT());
        entry.setCatalysts(owner, catalysts);
        var node = store.createNode(owner, libraryId, level.dimension().location(), 12024L);
        store.claimMode(node.id(), UUID.randomUUID(), List.of(new ResourceLocation("gtceu", "assembler")));
        store.lease(node.id(), entry.id(), 73, UUID.randomUUID());
        var state = new CompoundTag(); var jobs = new ListTag(); var job = new CompoundTag();
        job.putUUID("id", UUID.randomUUID()); job.putUUID("cloudPattern", entry.id());
        job.putLong("remaining", 7); job.putLong("elapsed", 24); job.putBoolean("energyPaid", true);
        jobs.add(job); state.put("jobs", jobs);
        var inventory = new CompoundTag(); inventory.put("unconsumed", new ItemStack(Items.APPLE, 7).save(new CompoundTag()));
        store.bindNodeSnapshot(node, () -> state, () -> inventory, CompoundTag::new);
        store.markNodeDirty(node); store.checkpoint();
        rejects(() -> store.extract(libraryId, owner, entry.id(), entry.revision(), UUID.randomUUID()),
                "Ordinary extraction still cannot take allocated catalyst-bearing original", checks);
        rejects(() -> store.extractFromCore(libraryId, UUID.randomUUID(), entry.id(), UUID.randomUUID()),
                "Core extraction still enforces library authorization", checks);
        long generation = node.generation(); UUID operation = UUID.randomUUID();
        var result = store.extractFromCore(libraryId, owner, entry.id(), operation);
        check(ItemStack.isSameItemSameTags(result, original) && result.getCount() == 1, "Core can take leased catalyst-bearing original exactly once", checks);
        check(entry.status() == CloudLedger.Status.EXPORTED && entry.lease() == null && !node.slots().containsKey(73),
                "Core export revokes only original publication authority", checks);
        check(node.generation() == generation && !node.detached() && node.ivState().equals(state) && node.inventory().equals(inventory),
                "Core export preserves independent busy task and unconsumed inventory snapshots", checks);
        check(entry.catalysts().equals(catalysts) && library.storedCount() == 0, "Export keeps catalysts in escrow and excludes original from stock count", checks);
        check(store.extractFromCore(libraryId, owner, entry.id(), operation).isEmpty(), "Export operation replay never returns another stack", checks);
        rejects(() -> store.extractFromCore(libraryId, owner, entry.id(), UUID.randomUUID()),
                "Fresh export operation also cannot duplicate exported original", checks);
        store.acknowledge(libraryId, owner, operation);
        check(store.extractFromCore(libraryId, owner, entry.id(), operation).isEmpty(), "Acknowledged export replay stays empty", checks);
        var page = CloudLibraryService.page(level, libraryId, "", "", 0, entry.id().toString());
        check(page.getList("entries", Tag.TAG_COMPOUND).stream().map(v -> (CompoundTag)v)
                        .anyMatch(row -> row.getString("id").equals(entry.id().toString()) && row.getBoolean("catalystsOnly")),
                "Exported catalyst escrow remains reachable in central catalogue", checks);
        check(page.getInt("libraryTotal") == 0, "Catalogue stock total excludes exported escrow record", checks);
        // Re-upload must create independent ownership, while old jobs retain their original catalyst identity.
        UUID secondUpload = UUID.randomUUID(); var replacement = store.upload(libraryId, owner, result, new CompoundTag(), secondUpload);
        store.acknowledge(libraryId, owner, secondUpload);
        check(!replacement.id().equals(entry.id()) && replacement.catalysts().isEmpty() && entry.catalysts().equals(catalysts),
                "Same NBT re-upload leaves busy-order catalyst escrow attached to old identity", checks);
        check(library.storedCount() == 1 && library.containsOriginal(original), "Re-upload restores exactly one owned original and duplicate index", checks);
        check(result.isEmpty(), "Re-upload debits returned original once", checks);
        // Retained EXPORTED entries remain editable, allowing actual catalyst withdrawal from their detail page.
        entry.setCatalysts(owner, new CompoundTag());
        check(CloudLibraryService.catalysts(entry).isEmpty(), "Exported escrow supports removing its catalysts", checks);
        entry.setCatalysts(owner, catalysts); store.checkpoint();
        var ref = new CompoundTag(); ref.putUUID("owner", owner); ref.putUUID("library", libraryId);
        ref.putUUID("entry", entry.id()); ref.putUUID("replacement", replacement.id()); ref.putUUID("node", node.id());
        ref.putUUID("operation", operation); ref.putLong("generation", generation);
        ref.put("catalysts", catalysts.copy()); ref.put("state", state.copy()); ref.put("inventory", inventory.copy());
        Path data = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        var shard = disk(data.resolve("gtl_cloud_originals_" + libraryId + "_0.dat"));
        var persisted = shard.getList("entries", Tag.TAG_COMPOUND).stream().map(v -> (CompoundTag)v)
                .filter(row -> row.getUUID("id").equals(entry.id())).findFirst().orElseThrow();
        check(persisted.getCompound("catalysts").equals(catalysts), "Real payload file retains exported catalysts", checks);
        check(disk(data.resolve("gtl_cloud_node_" + node.id() + ".dat")).getCompound("ivState").equals(state),
                "Real node file retains independent busy task bytes", checks);
        return ref;
    }
    /** Call only in the isolated flow fixture after a real AE dispatch has created the target's live job. */
    public static ItemStack busyExport(CloudPatternBufferMachine child, CloudPatternStore.Entry entry, List<String> checks) {
        var tasks = IvBuffers.state(child);
        if (tasks == null || tasks.jobs.stream().noneMatch(job -> entry.id().equals(job.cloudPattern)))
            throw new AssertionError("Busy export fixture requires a real accepted job for the selected original");
        var lease = entry.lease();
        if (lease == null || !lease.nodeId().equals(child.cloudNodeId()))
            throw new AssertionError("Busy export fixture requires the target's current live lease");
        var cached = child.cloudRealPattern(lease.stableSlot(), entry.original());
        if (cached == null) throw new AssertionError("Busy export fixture requires a real published processing pattern");
        var route = cached.getDefinition().getTag().getCompound(IvBuffers.ROUTE_KEY).copy();
        check(child.cloudRouteValid(route), "Cached AE route is valid immediately before core export", checks);
        var jobs = tasks.jobs.stream().map(job -> job.save().copy()).toList();
        var originalJobs = List.copyOf(tasks.jobs);
        var catalystStock = originalJobs.stream().map(child::cloudPatternCatalysts).toList();
        var store = child.cloudStore(); var library = store.library(child.cloudLibraryId());
        UUID operation = UUID.randomUUID();
        var original = store.extractFromCore(library.id(), library.owner(), entry.id(), operation);
        check(!original.isEmpty() && entry.status() == CloudLedger.Status.EXPORTED, "Busy original can be exported by central authority", checks);
        check(!child.cloudRouteValid(route) && child.entry(lease.stableSlot()) == null,
                "Export invalidates even previously cached AE routes", checks);
        check(!child.pushPattern(cached, new appeng.api.stacks.KeyCounter[0]), "Actual dispatch entry rejects cached exported route", checks);
        check(tasks.jobs.size() == originalJobs.size() && tasks.jobs.stream().map(job -> job.save().copy()).toList().equals(jobs),
                "Busy export and rejected dispatch preserve all original live job accounting", checks);
        check(originalJobs.stream().map(child::cloudPatternCatalysts).toList().equals(catalystStock),
                "Live jobs retain their exact shared catalyst lookup after original export", checks);
        check(child.getAvailablePatterns().stream().noneMatch(cached::equals),
                "Provider stops advertising exported original immediately", checks);
        store.acknowledge(library.id(), library.owner(), operation); store.checkpoint();
        return original;
    }
    public static void verifyReloaded(ServerLevel level, CompoundTag ref, List<String> checks) {
        var store = CloudPatternStore.get(level); UUID libraryId = ref.getUUID("library"); var library = store.library(libraryId);
        var entry = library.entry(ref.getUUID("entry")); var replacement = library.entry(ref.getUUID("replacement"));
        var node = store.node(ref.getUUID("node"));
        check(entry.status() == CloudLedger.Status.EXPORTED && entry.lease() == null && !node.slots().containsKey(73),
                "Reload retains revoked original and does not resurrect old slot", checks);
        check(entry.catalysts().equals(ref.getCompound("catalysts")) && replacement.catalysts().isEmpty(),
                "Reload preserves exported catalysts independently of re-uploaded original", checks);
        check(node.ivState().equals(ref.getCompound("state")) && node.inventory().equals(ref.getCompound("inventory"))
                        && node.generation() == ref.getLong("generation") && !node.detached(),
                "Reload preserves busy task bytes inventory and endpoint generation", checks);
        check(library.storedCount() == 1 && library.containsOriginal(replacement.original()), "Reload counts only re-uploaded ownership and restores NBT duplicate index", checks);
        check(store.extractFromCore(libraryId, ref.getUUID("owner"), entry.id(), ref.getUUID("operation")).isEmpty(),
                "Reload preserves acknowledged export idempotence", checks);
    }
    private static CompoundTag disk(Path file) throws Exception {
        var checked = NbtIo.readCompressed(file.toFile()).getCompound("data");
        var payload = checked.getCompound("data");
        if (checked.getInt("formatVersion") != 1 || !CloudPatternStore.checksum(payload).equals(checked.getString("sha256")))
            throw new AssertionError("Invalid cloud checkpoint checksum: " + file);
        return payload;
    }
    private static void rejects(Runnable action, String message, List<String> checks) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException | SecurityException expected) { rejected = true; }
        check(rejected, message, checks);
    }
    private static void check(boolean condition, String message, List<String> checks) {
        if (!condition) throw new AssertionError("core_authority:" + message); checks.add("core_authority:" + message);
    }
}
