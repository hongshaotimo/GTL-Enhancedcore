package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.item.PatternGeneratorRecipes;
import com.gtl.enhancedcore.common.item.PatternGeneratorSettings;
import com.gtl.enhancedcore.common.item.PatternGeneratorTransfer;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;

/** Opt-in real-world generator/inbox/WAL checks. Call step once per actual server tick after begin. */
public final class CloudGeneratorImportRuntimeChecks {
    private static CloudPatternLibraryMachine main;
    private static FakePlayer player;
    private static ServerLevel level;
    private static List<String> checks;
    private static ListTag savedInventory;
    private static Vec3 savedPosition;
    private static Map<UUID, ServerPlayer> online;
    private static ServerPlayer priorOnline;
    private static final List<ItemStack> expected = new ArrayList<>();
    private static int entriesBefore, receiptsBefore, ticks;
    private static boolean registered, checkedWaiting;
    private CloudGeneratorImportRuntimeChecks() {}

    public static void begin(ServerLevel world, CloudPatternLibraryMachine core, FakePlayer actor, List<String> results) throws Exception {
        level = world; main = core; player = actor; checks = results; ticks = 0; checkedWaiting = false; expected.clear();
        savedInventory = player.getInventory().save(new ListTag()); savedPosition = player.position();
        try {
            check(main.isFormed() && main.getLevel() == level && !main.generatorImportPending(), "formed real core has an empty physical upload slot");
            player.setPos(Vec3.atCenterOf(main.getPos()));
            check(main.generatorImportAuthorized(player), "owner can fill core without opening its menu");
            var probe = PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 3)},
                    new GenericStack[]{new GenericStack(AEItemKey.of(Items.DIAMOND), 2)});
            probe.getOrCreateTag().putUUID("generatorAudit", UUID.randomUUID());
            var outsider = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GeneratorDenied"));
            outsider.setPos(player.position());
            check(!main.stageGeneratorOriginal(outsider, probe) && !main.generatorImportPending(), "unauthorized actor cannot stage or debit an original");
            player.setPos(Vec3.atCenterOf(main.getPos()).add(20, 0, 0));
            check(!main.stageGeneratorOriginal(player, probe), "distance is rechecked at actual physical insertion");
            player.setPos(Vec3.atCenterOf(main.getPos()));
            check(!main.stageGeneratorOriginal(player, new ItemStack(Items.PAPER)), "non-processing items never enter the generator upload path");
            check(!main.stageGeneratorOriginal(player, probe.copyWithCount(2)), "one physical original per upload is enforced");
            var candidates = PatternGeneratorRecipes.recipes(player, GTRecipeTypes.BENDER_RECIPES);
            check(!candidates.isEmpty(), "fixture has a live recipe for real isolated job NBT checks");
            scaleNbt(candidates.getFirst());
            var config = new PatternGeneratorSettings();
            config.machine = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtceu", "lv_bender")));
            config.recipeType = GTRecipeTypes.BENDER_RECIPES.registryName.toString();
            var settings = new PatternGeneratorSettings(); settings.generatorId = UUID.randomUUID(); settings.transferArmed = true;
            settings.transferConfiguration = config.configuration();
            for (var recipe : candidates) {
                ItemStack original = PatternGeneratorRecipes.encode(recipe, player);
                if (original.isEmpty() || expected.stream().anyMatch(other -> ItemStack.isSameItemSameTags(other, original))) continue;
                expected.add(original); settings.transferRecipes.add(recipe.id());
                if (expected.size() == 2) break;
            }
            check(expected.size() == 2, "fixture supplies two distinct live bender recipes");
            registerActor();
            player.getInventory().clearContent();
            var tool = GTLEnhancedcoreItems.PATTERN_GENERATOR.asStack();
            tool.getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write());
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            player.getInventory().setItem(9, AEItems.BLANK_PATTERN.stack(7));
            PatternGeneratorTransfer.mark(probe, settings);
            var canonicalProbe = probe.copy(); canonicalProbe.removeTagKey("GTLPatternGeneratorSource");
            expected.add(canonicalProbe);
            player.getInventory().setItem(10, probe);
            var library = CloudPatternStore.get(level).library(main.libraryId());
            entriesBefore = library.entryCount(); receiptsBefore = library.receipts().size();
            Field formed = MultiblockControllerMachine.class.getDeclaredField("isFormed"); formed.setAccessible(true);
            formed.setBoolean(main, false);
            try {
                beginTransfer();
                check(queue().transferRecipes.size() == 2 && !main.generatorImportPending(), "unformed core preserves selected recipe queue and physical sources");
                check(!main.networkAvailable(main.serviceGrid()), "unformed core is not a new ME library binding candidate");
            } finally { formed.setBoolean(main, true); }
            beginTransfer();
            check(queue().transferRecipes.size() == 1 && ItemStack.matches(main.importInventory().getStackInSlot(0), expected.getFirst()),
                    "direct fill materializes exactly one selected recipe into the real inbox before queue debit");
            check(library.entryCount() == entriesBefore && library.receipts().size() == receiptsBefore,
                    "physical staging does not create a virtual central original or bypass the upload journal");
            var holder = main.getHolder(); var persisted = holder.getSelf().saveWithFullMetadata();
            var inboxRef = java.util.Arrays.stream(holder.getRootStorage().getPersistedFields())
                    .filter(ref -> ref.getKey().getName().equals("inbox")).findFirst().orElseThrow();
            var actorRef = java.util.Arrays.stream(holder.getRootStorage().getPersistedFields())
                    .filter(ref -> ref.getKey().getName().equals("inboxActor")).findFirst().orElseThrow();
            var persistedInbox = com.lowdragmc.lowdraglib.utils.TagUtils.getTagExtended(persisted, inboxRef.getPersistedKey());
            var persistedActor = com.lowdragmc.lowdraglib.utils.TagUtils.getTagExtended(persisted, actorRef.getPersistedKey());
            check(main.importInventory().serializeNBT().equals(persistedInbox)
                            && net.minecraft.nbt.NbtUtils.createUUID(player.getUUID()).equals(persistedActor),
                    "ordinary block persistence retains staged physical original and upload actor");
        } catch (Exception | Error failure) { cleanup(); throw failure; }
    }

    public static boolean step() throws Exception {
        try {
            if (++ticks > 1200) throw new AssertionError("Generator real upload did not finish within 1200 server ticks");
            int before = queue().transferRecipes.size();
            boolean waiting = main.generatorImportPending();
            PatternGeneratorTransfer.tick(player, player.getMainHandItem());
            if (waiting) {
                if (queue().transferRecipes.size() != before) throw new AssertionError("Generator advanced its queue during a pending upload");
                if (!checkedWaiting) { check(true, "in-flight durable upload pauses further recipe generation"); checkedWaiting = true; }
            }
            if (queue().transferArmed) return false;
            var library = CloudPatternStore.get(level).library(main.libraryId());
            check(library.entryCount() == entriesBefore + 3 && library.receipts().size() == receiptsBefore + 3,
                    "two live recipes and one existing physical original are uploaded exactly once");
            check(!main.generatorImportPending() && queue().transferRecipes.isEmpty()
                            && player.getInventory().getItem(9).getCount() == 7 && player.getInventory().getItem(10).isEmpty(),
                    "completion waits for final ACK, consumes no blanks, and debits the physical source once");
            var directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/gtl_cloud_import")
                    .resolve(UUID.nameUUIDFromBytes(level.dimension().location().toString().getBytes(StandardCharsets.UTF_8)) + "_" + main.getPos().asLong());
            for (ItemStack original : expected) {
                var entry = library.entries().stream().filter(row -> ItemStack.matches(row.original(), original)).findFirst().orElseThrow();
                check(entry.status() == CloudLedger.Status.AVAILABLE || entry.status() == CloudLedger.Status.LEASED,
                        "real central original is published after durable upload");
                check(!entry.original().getOrCreateTag().contains("GTLPatternGeneratorSource"), "transport provenance is absent from central original identity");
                var receipt = library.receipts().stream().filter(row -> row.subject().equals(entry.id()) && row.kind().equals("UPLOAD")).findFirst().orElseThrow();
                var file = directory.resolve(receipt.operation() + ".dat");
                check(receipt.status().equals("ACKNOWLEDGED") && Files.isRegularFile(file)
                                && NbtIo.readCompressed(file.toFile()).getString("status").equals("COMMITTED"),
                        "each accepted original has a real committed WAL and acknowledged authority receipt");
            }
            var settings = queue(); settings.transferArmed = true;
            var duplicate = expected.getFirst().copy(); PatternGeneratorTransfer.mark(duplicate, settings);
            player.getInventory().setItem(10, duplicate);
            player.getMainHandItem().getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write());
            beginTransfer();
            check(PatternGeneratorTransfer.available(player, settings.generatorId) == 1 && !main.generatorImportPending()
                            && library.entryCount() == entriesBefore + 3 && library.receipts().size() == receiptsBefore + 3,
                    "duplicate original is preserved outside core with no extra receipt or quarantine");
            check(!main.cloud$importQuarantined(), "rejected duplicate leaves ordinary core uploads usable");
            CloudPatternStore.get(level).checkpointLibrary(main.libraryId());
            cleanup(); return true;
        } catch (Exception | Error failure) { cleanup(); throw failure; }
    }

    private static PatternGeneratorSettings queue() { return PatternGeneratorSettings.load(player.getMainHandItem()); }
    private static void scaleNbt(PatternGeneratorRecipes.Entry entry) {
        var job = new IvJob(0, entry.recipe().copy(), new CompoundTag(), Map.of(), Map.of(), Long.MAX_VALUE);
        job.cloudPattern = UUID.randomUUID(); job.cloudMachine = UUID.randomUUID();
        for (long scale : new long[]{1, 8, Long.MAX_VALUE}) {
            job.cloudPatternOperations = scale;
            var saved = job.save(); var restored = IvJob.load(saved);
            check(saved.getLong("cloudPatternOperations") == scale && restored.cloudPatternOperations == scale
                            && restored.totalOperations == Long.MAX_VALUE && restored.cloudPattern.equals(job.cloudPattern)
                            && restored.cloudMachine.equals(job.cloudMachine),
                    "actual isolated job NBT preserves frozen positive pattern scale " + scale + " and exact order ownership");
        }
        var valid = job.save();
        for (long invalid : new long[]{0, -1, Long.MIN_VALUE}) {
            var malformed = valid.copy(); malformed.putLong("cloudPatternOperations", invalid);
            rejectsScale(() -> IvJob.load(malformed), "persisted nonpositive pattern scale " + invalid + " is rejected");
            job.cloudPatternOperations = invalid;
            rejectsScale(job::save, "nonpositive live pattern scale " + invalid + " cannot be saved into authority");
        }
        var badType = valid.copy(); badType.putInt("cloudPatternOperations", -1);
        rejectsScale(() -> IvJob.load(badType), "malformed integer pattern scale cannot masquerade as a missing field");
        var absent = valid.copy(); absent.remove("cloudPatternOperations");
        check(IvJob.load(absent).cloudPatternOperations == 1, "absent frozen scale uses the declared unit default");
    }
    private static void rejectsScale(Runnable action, String reason) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        check(rejected, reason);
    }
    private static void beginTransfer() {
        var hit = new BlockHitResult(Vec3.atCenterOf(main.getPos()), Direction.NORTH, main.getPos(), false);
        PatternGeneratorTransfer.begin(player, new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
    @SuppressWarnings("unchecked")
    private static void registerActor() throws Exception {
        var players = level.getServer().getPlayerList(); online = null;
        for (Class<?> type = players.getClass(); type != null && online == null; type = type.getSuperclass())
            for (var field : type.getDeclaredFields()) {
                String generic = field.getGenericType().getTypeName();
                if (!Map.class.isAssignableFrom(field.getType()) || !generic.contains(UUID.class.getName())
                        || !generic.contains(ServerPlayer.class.getName())) continue;
                field.setAccessible(true); online = (Map<UUID, ServerPlayer>)field.get(players); break;
            }
        if (online == null) throw new AssertionError("Isolated player UUID index is unavailable");
        priorOnline = online.put(player.getUUID(), player); registered = true;
        check(players.getPlayer(player.getUUID()) == player, "deferred production inbox tick resolves the real fixture actor");
    }
    public static void cleanup() {
        if (player == null) return;
        PatternGeneratorTransfer.cancel(player);
        if (savedInventory != null) { player.getInventory().clearContent(); player.getInventory().load(savedInventory); savedInventory = null; }
        if (savedPosition != null) { player.setPos(savedPosition); savedPosition = null; }
        if (registered) {
            if (priorOnline == null) online.remove(player.getUUID(), player); else online.put(player.getUUID(), priorOnline);
            registered = false;
        }
    }
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError("generator_import:" + reason);
        checks.add("generator_import:" + reason);
    }
}
