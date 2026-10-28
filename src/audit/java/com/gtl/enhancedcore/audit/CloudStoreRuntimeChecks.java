package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.*;
import java.util.*;

/** Opt-in real SavedData audit. Run seed, verify, corrupt, quarantine in FOUR separate JVMs. */
@Mod("enhancedcore_audit")
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CloudStoreRuntimeChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/cloud-pattern-ae-rework-20261023").toAbsolutePath().normalize();
    private static final Path PLAYER_ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/cloud-pattern-player-ui-20261023").toAbsolutePath().normalize();
    private static final Path WUT_ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/cloud-wut-binding-20261023").toAbsolutePath().normalize();
    private static final UUID OWNER = UUID.fromString("9260554c-42be-418b-adf4-67b31ef3bf1e");
    private static final ResourceLocation MODE = new ResourceLocation("gtceu", "assembler");
    public CloudStoreRuntimeChecks() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("gtl.cloudStoreAudit")) return;
        MinecraftServer server = event.getServer(); Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if ((!world.startsWith(ROOT) && !world.startsWith(PLAYER_ROOT) && !world.startsWith(WUT_ROOT)) || world.equals(ROOT) || world.equals(PLAYER_ROOT) || world.equals(WUT_ROOT)
                || !Files.isRegularFile(world.getParent().resolve("cloud-store.marker")))
            throw new IllegalStateException("Cloud audit only runs in a marked task-owned isolated world");
        String phase = System.getProperty("gtl.cloudStoreAuditPhase", "seed");
        if (phase.equals("player_ui")) {
            try { CloudPlayerRuntimeChecks.begin(server.overworld()); return; }
            catch (Throwable failure) { throw new RuntimeException("Cloud player UI setup failed", failure); }
        }
        if (phase.equals("flow") || phase.equals("native") || phase.equals("generic")) {
            try { CloudPatternFlowRuntimeChecks.begin(server.overworld()); return; }
            catch (Throwable failure) { throw new RuntimeException("Cloud production flow setup failed", failure); }
        }
        var checks = new ArrayList<String>(); var report = new LinkedHashMap<String, Object>();
        try { run(server.overworld(), phase, checks); report.put("status", "PASS"); }
        catch (Throwable failure) { report.put("status", "FAIL"); report.put("error", failure.toString()); failure.printStackTrace(); }
        report.put("phase", phase); report.put("checks", checks);
        report.put("scope", "Real Forge transformed controller identity, real world SavedData files, independent-JVM phase protocol; no player-world or complete AE order validation");
        try { Files.writeString(world.getParent().resolve("cloud-store-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { throw new RuntimeException(failure); }
        server.halt(false);
    }
    private static void run(ServerLevel level, String phase, List<String> checks) throws Exception {
        CloudPatternStore store = CloudPatternStore.get(level); Path data = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        Path reference = data.resolve("cloud-audit-reference.dat");
        if (phase.equals("seed")) {
            require(!Files.exists(reference), "Seed requires a fresh isolated world", checks);
            UUID libraryId = store.defaultLibrary(OWNER); var library = store.library(libraryId);
            UUID upload = UUID.randomUUID(); ItemStack source = new ItemStack(Items.PAPER, 3);
            source.getOrCreateTag().putString("author", "audit-only");
            CompoundTag config = new CompoundTag(); config.putInt("circuit", 2);
            var entry = store.upload(libraryId, OWNER, source, config, upload);
            require(source.getCount() == 2 && entry.status() == CloudLedger.Status.EXTERNAL_PENDING, "Real upload debits exactly one but does not expose pending original", checks);
            store.upload(libraryId, OWNER, source, config, upload);
            require(source.getCount() == 2, "Upload operation replay does not debit a second original", checks);
            store.checkpoint(); store.acknowledge(libraryId, OWNER, upload);
            var node = store.createNode(OWNER, libraryId, level.dimension().location(), 42L); UUID machine = UUID.randomUUID();
            require(store.claimMode(node.id(), machine, List.of(MODE, MODE)).equals(MODE.toString()), "Real persisted mode binding de-duplicates type IDs", checks);
            var contender = store.createNode(OWNER, libraryId, level.dimension().location(), 43L);
            require(store.canClaimMode(node.id(), machine, MODE) && !store.canClaimMode(contender.id(), machine, MODE),
                    "Mode preflight protects the current binding when another node requests its occupied mode", checks);
            require(store.nodesAt(level.dimension().location(), 42L).stream().anyMatch(found -> found.id().equals(node.id())),
                    "Persisted endpoint address index locates existing node authority", checks);
            Object endpoint = new Object(); require(store.activate(node.id(), endpoint, level.dimension().location(), 42L), "Original endpoint activates", checks);
            require(!store.activate(node.id(), new Object(), level.dimension().location(), 42L), "Copied endpoint cannot activate concurrently", checks);
            store.lease(node.id(), entry.id(), 1024, UUID.randomUUID());
            long locationAfterLease = library.locationRevision();
            require(locationAfterLease == 1, "Location search revision changes on successful lease", checks);
            CompoundTag state = new CompoundTag(); state.putInt("version", 1); ListTag jobs = new ListTag(); CompoundTag job = new CompoundTag();
            job.putUUID("id", UUID.randomUUID()); job.putLong("remaining", 6); job.putInt("elapsed", 40); job.putString("energyPaid", "8192"); job.putBoolean("probabilityFixed", true); jobs.add(job); state.put("jobs", jobs);
            CompoundTag inventory = new CompoundTag(); inventory.put("unconsumed", new ItemStack(Items.IRON_INGOT, 6).save(new CompoundTag()));
            CompoundTag origin = new CompoundTag(); origin.putUUID("node", node.id()); origin.putString("source", "original-A");
            store.bindNodeSnapshot(node, () -> state, () -> inventory, () -> origin);
            state.putString("changedAfterBinding", "captured-at-save"); store.markNodeDirty(node); store.checkpoint();
            require(library.locationRevision() == locationAfterLease, "Frequent task snapshots never invalidate location searches", checks);
            UUID detach = UUID.randomUUID(); var recovery = store.detach(node.id(), detach); store.detach(node.id(), detach); store.checkpoint();
            require(recovery.status().equals("FROZEN") && entry.lease() == null && node.detached(), "Busy detach preserves separate frozen ledger and immediately releases original", checks);
            require(node.ivState().getString("changedAfterBinding").equals("captured-at-save"), "Actual save captures the latest bound state", checks);
            CompoundTag ref = new CompoundTag(); ref.putUUID("library", libraryId); ref.putUUID("node", node.id()); ref.putUUID("entry", entry.id()); ref.putUUID("machine", machine);
            ref.put("origin", origin); ref.put("state", state); ref.put("inventory", inventory); NbtIo.writeCompressed(ref, reference.toFile());
            var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "universal_joint_factory"));
            require(definition != null, "Actual registered controller definition exists", checks);
            var holder = (MetaMachineBlockEntity)definition.getBlockEntityType().create(new BlockPos(100, 64, 100), definition.defaultBlockState());
            require(holder != null, "Real controller holder can be instantiated without placing structure", checks);
            holder.setLevel(level); var controller = holder.getMetaMachine();
            require(controller instanceof CloudMachineIdentity, "Controller identity Mixin applies to real MetaMachine", checks);
            var identity = (CloudMachineIdentity)controller;
            require(identity.cloud$machineInstance(false) == null, "Read/preview path creates no identity", checks);
            UUID enrolled = identity.cloud$machineInstance(true); require(enrolled.equals(identity.cloud$machineInstance(true)), "Enrollment creates stable identity once", checks);
            CompoundTag saved = new CompoundTag(), dropped = new CompoundTag(); controller.saveCustomPersistedData(saved, false); controller.saveCustomPersistedData(dropped, true);
            require(saved.hasUUID("gtl_cloud_machine_instance") && !dropped.hasUUID("gtl_cloud_machine_instance"), "Normal save preserves identity while dropped block cannot impersonate old controller", checks);
            holder.setLevel(null);
            CloudPatternMachineRuntimeChecks.run(level, checks);
            CloudModulePowerRuntimeChecks.run(level, checks);
            CloudImportBatchRuntimeChecks.run(level, checks); return;
        }
        require(Files.isRegularFile(reference), "Reference from a previous independent JVM exists", checks);
        CompoundTag ref = NbtIo.readCompressed(reference.toFile()); UUID libraryId = ref.getUUID("library"); var library = store.library(libraryId);
        if (phase.equals("quarantine")) {
            Path damagedFile = data.resolve("gtl_cloud_originals_" + libraryId + "_0.dat"); byte[] before = Files.readAllBytes(damagedFile);
            boolean rejected = false; try { library.entry(ref.getUUID("entry")); } catch (IllegalStateException expected) { rejected = true; }
            require(rejected, "Corrupted original payload shard is rejected, never replaced by an empty original", checks);
            store.checkpoint(); require(Arrays.equals(before, Files.readAllBytes(damagedFile)), "Checkpoint preserves corrupted original disk bytes for diagnosis", checks); return;
        }
        require(phase.equals("verify") || phase.equals("corrupt"), "Known independent-JVM audit phase", checks);
        var entry = library.entry(ref.getUUID("entry")); var oldNode = store.node(ref.getUUID("node"));
        require(store.defaultLibrary(OWNER).equals(libraryId), "Default library identity survives restart", checks);
        require(library.locationRevision() == 2, "Lease and detach location revision survives real independent-JVM restart", checks);
        require(entry.original().getTag().getString("author").equals("audit-only") && entry.configuration().getInt("circuit") == 2, "Original NBT and separate slot circuit survive actual disk restart", checks);
        require(oldNode.detached() && oldNode.ivState().equals(ref.getCompound("state")) && oldNode.inventory().equals(ref.getCompound("inventory")), "Frozen materials, progress, payment and probability snapshot survive restart", checks);
        require(!store.activate(oldNode.id(), new Object(), level.dimension().location(), 42L), "Restart cannot reactivate detached generation", checks);
        require(entry.lease() == null, "Released original remains released after restart", checks);
        if (phase.equals("verify")) {
            var target = store.createNode(OWNER, libraryId, level.dimension().location(), 43L);
            UUID targetMachine = UUID.randomUUID(); store.claimMode(target.id(), targetMachine, List.of(MODE));
            boolean rejected = false; CompoundTag wrong = new CompoundTag(); wrong.putString("source", "different-B");
            try { store.restore(oldNode.id(), target.id(), OWNER, targetMachine, MODE, wrong, UUID.randomUUID()); } catch (IllegalStateException expected) { rejected = true; }
            require(rejected, "Different source route cannot silently redirect preserved materials", checks);
            store.restore(oldNode.id(), target.id(), OWNER, targetMachine, MODE, ref.getCompound("origin"), UUID.randomUUID()); store.checkpoint();
            require(target.ivState().equals(ref.getCompound("state")), "Compatible authorized recovery preserves exact paid snapshot without generating outputs", checks);
        } else {
            Path file = data.resolve("gtl_cloud_originals_" + libraryId + "_0.dat"); CompoundTag original = NbtIo.readCompressed(file.toFile());
            Files.copy(file, file.resolveSibling(file.getFileName() + ".audit-original"), StandardCopyOption.REPLACE_EXISTING);
            original.getCompound("data").getCompound("data").getList("entries", Tag.TAG_COMPOUND).getCompound(0).getCompound("original").putByte("Count", (byte)2);
            NbtIo.writeCompressed(original, file.toFile()); checks.add("Intentionally corrupted only marked audit-world payload checksum; next JVM must quarantine");
        }
    }
    private static void require(boolean condition, String description, List<String> checks) {
        if (!condition) throw new AssertionError(description); checks.add(description);
    }
}
