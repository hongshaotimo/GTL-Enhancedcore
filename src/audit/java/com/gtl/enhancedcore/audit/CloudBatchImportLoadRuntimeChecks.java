package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.cloud.CloudPatternAllocator;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.CloudImportSourceAccess;
import com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.item.PatternGeneratorRecipes;
import com.gtl.enhancedcore.common.item.PatternGeneratorSettings;
import com.gtl.enhancedcore.common.item.PatternGeneratorTransfer;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Real physical-source import profiles. No direct authority upload, ACK, or checkpoint calls. */
public final class CloudBatchImportLoadRuntimeChecks {
    private static final int[] TARGETS = {32, 256, 1024};
    private static final int MAXIMUM = TARGETS[TARGETS.length - 1];
    private static final String PATH = System.getProperty("gtl.enhancedcore.cloudUploadLoadPath", "bulk");
    private static final List<GTRecipe> recipes = new ArrayList<>();
    private static final List<ItemStack> originals = new ArrayList<>();
    private static final List<AEKey> outputKeys = new ArrayList<>();
    private static final Map<AEKey, Integer> outputIndices = new HashMap<>();
    private static final Map<UUID, Map<String, Object>> transactions = new LinkedHashMap<>();
    private static final List<Map<String, Object>> rounds = new ArrayList<>();
    private static final List<Map<String, Object>> submissions = new ArrayList<>();
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static List<CloudPatternBufferMachine> children;
    private static final List<MEPatternBufferPartMachine> sources = new ArrayList<>();
    private static CloudPatternStore.Library library;
    private static ServerPlayer player, previousActor;
    private static List<String> checks;
    private static Map<UUID, ServerPlayer> online;
    private static ListTag savedInventory;
    private static Vec3 savedPosition;
    private static net.minecraft.world.inventory.AbstractContainerMenu savedMenu;
    private static ItemStack machineItem;
    private static Path saveDirectory;
    private static Map<String, Object> round;
    private static long began, roundStarted, originalsBytes, preparationMicros;
    private static int ticks, stage, since, tier, submitted, discovered, acknowledged, published, stable;
    private static boolean actorRegistered, cleaned;

    private CloudBatchImportLoadRuntimeChecks() {}

    /** Fixture-only recipe registration happens before measurements and never edits production recipes. */
    public static void prepareRecipes(ServerLevel world, WorkableElectricMultiblockMachine machine) {
        if (!PATH.equals("bulk") && !PATH.equals("generator"))
            throw new IllegalArgumentException("cloudUploadLoadPath must be bulk or generator");
        recipes.clear();
        machineItem = new ItemStack(machine.getBlockState().getBlock().asItem());
        var items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR && item.getMaxStackSize() == 64
                        && !IvBuffers.isCircuit(AEItemKey.of(item))
                        && !org.gtlcore.gtlcore.common.data.GTLItems.VIRTUAL_INGREDIENT.isIn(new ItemStack(item)))
                .sorted(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).iterator();
        var all = new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(world.getRecipeManager().getRecipes());
        for (int i = 0; i < MAXIMUM; i++) {
            var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_cloud_import_load_" + i), machine.getRecipeType())
                    .inputItems(items.next()).outputItems(items.next()).duration(80).EUt(8).buildRawRecipe();
            recipes.add(recipe); all.add(recipe); machine.getRecipeType().getLookup().addRecipe(recipe);
        }
        world.getRecipeManager().replaceRecipes(all);
    }

    public static void begin(ServerLevel world, CloudPatternLibraryMachine core, List<CloudPatternBufferMachine> buffers,
                             ServerPlayer actor, List<String> results) throws Exception {
        level = world; main = core; children = List.copyOf(buffers); player = actor; checks = results;
        saveDirectory = world.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path instance = saveDirectory.getParent().getParent();
        Path auditRoot = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-compat-20261105").toAbsolutePath().normalize();
        require(instance.startsWith(auditRoot) && !instance.equals(auditRoot)
                && Files.isRegularFile(instance.resolve("cloud-compat.marker")), "marked_new_isolated_world_only");
        require(recipes.size() == MAXIMUM && main.isFormed() && children.size() == 2, "real_two_provider_fixture_and_1024_live_recipes");
        library = CloudPatternStore.get(level).library(main.libraryId());
        require(library.entryCount() == 0 && !main.generatorImportPending(), "profile_starts_with_empty_authority_and_inbox");
        ticks = stage = since = tier = submitted = discovered = acknowledged = published = stable = 0;
        originalsBytes = 0; cleaned = false; transactions.clear(); rounds.clear(); submissions.clear(); originals.clear(); outputKeys.clear(); outputIndices.clear(); sources.clear();
        savedInventory = player.getInventory().save(new ListTag()); savedPosition = player.position(); savedMenu = player.containerMenu;
        registerActor(); player.getInventory().clearContent(); player.setPos(Vec3.atCenterOf(main.getPos()));
        CloudDispatchLoadRuntimeChecks.beginObservations(world, core, buffers, actor, results, "cloud-upload-load.json");
        phase("physical_original_preparation");
        long preparationStarted = System.nanoTime();
        for (int i = 0; i < recipes.size(); i++) {
            ItemStack original = PatternGeneratorRecipes.encode(PatternGeneratorRecipes.describe(recipes.get(i)), player);
            require(!original.isEmpty() && original.getCount() == 1, "live_recipe_encodes_one_physical_processing_original_" + i);
            var details = PatternDetailsHelper.decodePattern(original, world);
            require(details != null && details.getOutputs().length == 1
                    && outputIndices.put(details.getOutputs()[0].what(), i) == null, "distinct_effective_processing_output_" + i);
            originals.add(original);
            outputKeys.add(details.getOutputs()[0].what());
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) { NbtIo.write(original.save(new CompoundTag()), output); }
            originalsBytes += bytes.size();
        }
        preparationMicros = (System.nanoTime() - preparationStarted) / 1000;
        if (PATH.equals("bulk")) {
            var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtladditions", "me_super_pattern_buffer"));
            require(block != null && block != net.minecraft.world.level.block.Blocks.AIR, "supported_super_pattern_buffer_definition_exists");
            int slots = 0;
            while (slots < MAXIMUM) {
                require(sources.size() < 16, "physical_source_count_is_bounded");
                BlockPos sourcePos = main.getPos().east(6 + sources.size());
                require(level.hasChunkAt(sourcePos) && level.isEmptyBlock(sourcePos), "bounded_loaded_physical_source_position_is_air_" + sources.size());
                level.setBlockAndUpdate(sourcePos, block.defaultBlockState());
                var source = (com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine) MetaMachine.getMachine(level, sourcePos);
                source.onMachinePlaced(player, ItemStack.EMPTY); source.setFrontFacing(Direction.NORTH);
                source.getMainNode().setOwningPlayer(player); source.setFOAModeEnabled(false);
                require(source.getPatternInventory().getSlots() > 0, "supported_source_has_real_slots_" + sources.size());
                sources.add(source); slots += source.getPatternInventory().getSlots();
            }
        }
        began = System.nanoTime(); phase("upload_fixture_settle"); publishMetadata();
    }

    public static boolean step() throws Exception {
        try { return stepInternal(); }
        catch (Exception | Error failure) {
            publishMetadata();
            try { CloudDispatchLoadRuntimeChecks.finishObservations("FAIL", failure.toString()); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
            cleanup(); throw failure;
        }
    }

    private static boolean stepInternal() throws Exception {
        CloudDispatchLoadRuntimeChecks.observationTick();
        if (++ticks > 60_000) throw new AssertionError("upload_load:bounded_timeout stage=" + stage + " acknowledged=" + acknowledged);
        if (main.cloud$importQuarantined() || sources.stream().anyMatch(source -> source instanceof CloudImportSourceAccess access && access.cloud$importQuarantined()))
            throw new AssertionError("upload_load:production_import_entered_review");
        if (ticks % 20 == 0) { observeTransactions(); publishMetadata(); }
        switch (stage) {
            case 0 -> {
                if (ticks < 60 || children.stream().anyMatch(child -> !child.cloudReady(true))) return false;
                if (!sources.isEmpty()) {
                    var authorizations = new ArrayList<Map<String,Object>>();
                    for (var source : sources) {
                    var node = source.getMainNode().getNode(); if (node == null) return false;
                    if (node.getGrid() != main.serviceGrid()) GridHelper.createConnection(main.serviceGrid().getPivot(), node);
                    if (!source.getMainNode().isActive()) return false;
                    var access = new LinkedHashMap<String,Object>();
                    access.put("actor",player.getUUID().toString());access.put("actorPosition",player.position().toString());
                    access.put("sourcePosition",source.getPos().toShortString());
                    access.put("sourceNodeOwner",String.valueOf(node.getOwningPlayerProfileId()));
                    access.put("sourceNodeActive",source.getMainNode().isActive());
                    access.put("sameLevel",source.getLevel()==player.serverLevel());
                    access.put("libraryAuthorized",com.gtl.enhancedcore.common.cloud.CloudLibraryService.authorized(player,main.libraryId(),main));
                    access.put("sourceAddressAuthorized",com.gtl.enhancedcore.common.cloud.transfer.CloudImportSourceAddress.authorized(player,source));
                    access.put("sourceClaimAllowed",com.gtl.enhancedcore.common.cloud.CloudLibraryService.claimAllows(player,level,source.getPos()));
                    access.put("sourceMayInteract",level.mayInteract(player,source.getPos()));
                    access.put("actorDistanceSquaredToMain",player.distanceToSqr(Vec3.atCenterOf(main.getPos())));
                    access.put("actorDistanceSquaredToSource",player.distanceToSqr(Vec3.atCenterOf(source.getPos())));
                    access.put("mainDistanceSquaredToSource",main.getPos().distSqr(source.getPos()));
                    access.put("actualDefinition",source.getDefinition().getId().toString());
                    access.put("actualPatternSlots",source.getPatternInventory().getSlots());
                    access.put("foaModeEnabled",((com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine)source).isFOAModeEnabled());
                    authorizations.add(access);
                    require(player.getUUID().equals(node.getOwningPlayerProfileId()),"physical_source_node_has_exact_fixture_owner");
                    var inspection = com.gtl.enhancedcore.common.cloud.transfer.CloudBufferImport.inspectNetworkWithCatalysts(player,source,
                            (who,endpoint)->level.mayInteract(who,endpoint.getPos())&&com.gtl.enhancedcore.common.cloud.CloudLibraryService.claimAllows(who,level,endpoint.getPos())
                                    &&endpoint.getMainNode().isActive()&&endpoint.getMainNode().getNode().getGrid()==main.serviceGrid());
                    access.put("productionNetworkSourceInspection",inspection.reason().name());
                    require(inspection.allowed(),"supported_real_network_source_passes_production_inspection_"+access);
                    }
                    CloudDispatchLoadRuntimeChecks.putObservationMetadata("fixtureSourceAuthorizations",authorizations);
                    var ui = main.createUI(player); ui.initWidgets();
                    player.containerMenu = new com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer(ui, 119);
                    require(main.menuAuthorized(player), "native_central_menu_authorizes_bulk_action");
                }
                phase("upload_idle_baseline"); since = ticks; stage = 1;
                CloudDispatchLoadRuntimeChecks.requestTerminalProbe(0);
            }
            case 1 -> {
                if (ticks - since < 120 || !CloudDispatchLoadRuntimeChecks.terminalProbeComplete()) return false;
                startRound();
            }
            case 2 -> {
                // The three live probes accept the previous stable catalogue, so ACK duration is not an open-menu timeout.
                if (PATH.equals("generator")) {
                    long call = System.nanoTime();
                    long durationNanos;
                    try{PatternGeneratorTransfer.tick(player, player.getMainHandItem());}
                    finally{durationNanos=System.nanoTime()-call;CloudDispatchLoadRuntimeChecks.includeProductionEndWork(durationNanos);}
                    long duration = durationNanos / 1000;
                    round.put("generatorTickCalls", ((Number) round.getOrDefault("generatorTickCalls", 0L)).longValue() + 1);
                    round.put("generatorTickCallTotalMicros", ((Number) round.getOrDefault("generatorTickCallTotalMicros", 0L)).longValue() + duration);
                    round.put("generatorTickCallMaxMicros", Math.max(((Number) round.getOrDefault("generatorTickCallMaxMicros", 0L)).longValue(), duration));
                    var queue = PatternGeneratorSettings.load(player.getMainHandItem());
                    submitted = TARGETS[tier] - queue.transferRecipes.size();
                    if (!queue.transferArmed && queue.transferRecipes.isEmpty() && !main.generatorImportPending()) stage = 3;
                } else {
                    var snapshot = new CompoundTag();CloudNetworkPatternImport.snapshot(main,snapshot);
                    round.put("latestNetworkImportSnapshot",networkSnapshot(snapshot));
                    if (!snapshot.getBoolean("networkImportRunning") && snapshot.getBoolean("networkImportFinished")) {
                        require(snapshot.getInt("networkImportMoved") == ((Number)round.get("actualNewOriginals")).intValue()
                                        && snapshot.getInt("networkImportSkipped") == 0 && snapshot.getString("networkImportStopReason").isEmpty() && sourceEmpty(),
                                "real_network_session_debits_every_physical_original_"+TARGETS[tier]+"_"+networkSnapshot(snapshot));
                        round.put("productionSessionFinishedObservedTick",ticks);round.put("productionSessionFinishedObservedMicros",sinceRound());
                        stage = 3;
                    }
                }
            }
            case 3 -> {
                if (acknowledged < TARGETS[tier] || main.generatorImportPending()) return false;
                round.putIfAbsent("allAcknowledgedObservedTick", ticks);
                round.putIfAbsent("allAcknowledgedObservedMicros", sinceRound());
                phase(PATH + "_" + TARGETS[tier] + "_await_real_AE_publication");
                if (!catalogueReady(TARGETS[tier])) { stable = 0; return false; }
                if (++stable < 3 || !CloudDispatchLoadRuntimeChecks.terminalProbeComplete()) return false;
                if(PATH.equals("bulk")&&!durableNetworkSessionReady())return false;
                published = TARGETS[tier];
                round.put("allPatternsPublishedObservedTick", ticks); round.put("allPatternsPublishedObservedMicros", sinceRound());
                round.put("actualCloudProviderPatternCounts", children.stream().map(child -> child.getAvailablePatterns().size()).toList());
                phase(PATH + "_" + TARGETS[tier] + "_WAL_and_original_verification");
                verifyRound(); publishMetadata();
                if (++tier < TARGETS.length) { stage = 4; since = ticks; phase("upload_between_tiers_settle"); }
                else { stage = 5; phase("upload_full_1024_catalogue_terminal"); CloudDispatchLoadRuntimeChecks.requestTerminalProbe(MAXIMUM); }
            }
            case 4 -> { if (ticks - since >= 60) startRound(); }
            case 5 -> {
                if (!CloudDispatchLoadRuntimeChecks.terminalProbeComplete()) return false;
                require(library.entryCount() == MAXIMUM && library.storedCount() == MAXIMUM && acknowledged == MAXIMUM,
                        "1024_exact_originals_acknowledged_once_and_fully_published");
                require(!main.generatorImportPending() && sourceEmpty(), "all_physical_import_sources_are_empty");
                publishMetadata(); CloudDispatchLoadRuntimeChecks.captureObservation();
                CloudDispatchLoadRuntimeChecks.finishObservations("PASS", ""); cleanup(); return true;
            }
            default -> throw new AssertionError("upload_load:unknown_stage_" + stage);
        }
        return false;
    }

    private static void startRound() throws Exception {
        stable = 0; int before = submitted; int target = TARGETS[tier];
        round = new LinkedHashMap<>(); rounds.add(round);
        round.put("path", PATH); round.put("catalogueBefore", before); round.put("targetCatalogue", target); round.put("actualNewOriginals", target - before);
        phase(PATH + "_" + target + "_physical_source_preparation");
        long prepare = System.nanoTime();
        if (PATH.equals("generator")) {
            var config = new PatternGeneratorSettings(); config.machine = machineItem.copy();
            config.recipeType = recipes.getFirst().recipeType.registryName.toString();
            require(PatternGeneratorRecipes.machineTypes(config.machine).stream().anyMatch(type -> type.registryName.toString().equals(config.recipeType)),
                    "real_generator_machine_mode_is_valid_" + target);
            var settings = new PatternGeneratorSettings(); settings.generatorId = UUID.randomUUID(); settings.transferArmed = true;
            settings.transferConfiguration = config.configuration();
            for (int i = before; i < target; i++) settings.transferRecipes.add(recipes.get(i).id.toString());
            var tool = GTLEnhancedcoreItems.PATTERN_GENERATOR.asStack();
            tool.getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write()); player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        } else {
            require(sourceEmpty(),"all_supported_network_sources_empty_before_next_session_"+target);
            int index = before;
            for (var source : sources) for (int slot=0;slot<source.getPatternInventory().getSlots()&&index<target;slot++) {
                source.getPatternInventory().setStackInSlot(slot,originals.get(index++).copy());
                source.getPatternInventory().onContentsChanged(slot);
            }
            require(index==target,"bounded_physical_sources_hold_entire_actual_new_session_"+target);
        }
        round.put("sourcePreparationMicros", (System.nanoTime() - prepare) / 1000);
        roundStarted = System.nanoTime(); round.put("submissionStartedTick", ticks);
        phase(PATH + "_" + target + "_real_import_and_ACK");
        CloudDispatchLoadRuntimeChecks.requestTerminalProbe(before);
        if (PATH.equals("generator")) {
            var hit = new BlockHitResult(Vec3.atCenterOf(main.getPos()), Direction.NORTH, main.getPos(), false);
            var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
            long call = System.nanoTime();
            long durationNanos;
            try{PatternGeneratorTransfer.begin(player,context);}
            finally{durationNanos=System.nanoTime()-call;CloudDispatchLoadRuntimeChecks.includeProductionEndWork(durationNanos);}
            round.put("generatorBeginCallMicros", durationNanos / 1000);
            submitted = target - PatternGeneratorSettings.load(player.getMainHandItem()).transferRecipes.size();
        } else submitNetworkSession(before,target);
        stage = 2; publishMetadata();
    }

    private static void submitNetworkSession(int before,int target) {
        var row = new LinkedHashMap<String, Object>(); submissions.add(row);
        row.put("firstOriginalIndex", before); row.put("physicalOriginals", target-before); row.put("submittedTick", ticks);
        row.put("productionAction","network_import");row.put("actualPhysicalSourceCount",sources.size());
        row.put("centralMenuAuthorizedBeforeAction",main.menuAuthorized(player));
        row.put("actualActorMenuBeforeAction",player.containerMenu.getClass().getName());
        require(main.menuAuthorized(player),"central_real_menu_remains_authorized_at_bulk_submission_"+row);
        int entriesBefore = library.entryCount(); long call = System.nanoTime();
        main.cloudUiAction(player, "network_import", new CompoundTag());
        row.put("productionNetworkImportActionCallMicros", (System.nanoTime() - call) / 1000);
        row.put("authorityEntryDeltaAtReturn", library.entryCount() - entriesBefore);
        var after=main.cloudUiInitialSnapshot(player);row.put("snapshotAtReturn",networkSnapshot(after));
        require(after.getBoolean("networkImportRunning"),"actual_network_import_request_queued_"+target);
        submitted=target;row.put("cumulativeSubmitted",submitted);row.put("sourceSlotsEmptyAtReturn",sourceEmpty());
    }

    private static Map<String,Object> networkSnapshot(CompoundTag tag) {
        var result=new LinkedHashMap<String,Object>();
        for(String key:List.of("networkImportRunning","networkImportFinished","networkImportMoved","networkImportSkipped","networkImportStopReason","networkImportReasons","status","diagnostic","diagnosticDetail"))
            if(tag.contains(key))result.put(key,tag.get(key).getAsString());
        return result;
    }

    private static void observeTransactions() {
        int count = library.entryCount();
        if (count != discovered) {
            for (var receipt : library.receipts()) if (receipt.kind().equals("UPLOAD") && !transactions.containsKey(receipt.operation())) {
                var entry = library.entry(receipt.subject()); var original = entry.original();
                var details = PatternDetailsHelper.decodePattern(original, level);
                Integer index = details == null || details.getOutputs().length != 1 ? null : outputIndices.get(details.getOutputs()[0].what());
                require(index != null && ItemStack.matches(original, originals.get(index)), "authority_owns_exact_effective_original_" + receipt.subject());
                var row = new LinkedHashMap<String, Object>(); row.put("index", index); row.put("operation", receipt.operation().toString());
                row.put("entry", receipt.subject().toString()); row.put("reservationObservedTick", ticks); row.put("reservationObservedMicrosSinceProfile", (System.nanoTime() - began) / 1000);
                transactions.put(receipt.operation(), row);
            }
            discovered = count;
        }
        int ack = 0;
        for (var transaction : transactions.entrySet()) {
            var receipt = library.receipt(transaction.getKey());
            if (receipt != null && receipt.status().equals("ACKNOWLEDGED")) {
                ack++; transaction.getValue().putIfAbsent("acknowledgedObservedTick", ticks);
                transaction.getValue().putIfAbsent("acknowledgedObservedMicrosSinceProfile", (System.nanoTime() - began) / 1000);
            }
        }
        acknowledged = ack;
    }

    private static boolean sourceEmpty() {
        for(var source:sources){var inventory = source.getPatternInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private static boolean catalogueReady(int expected) {
        for (var child : children) if (CloudPatternAllocator.isScanning(child) || child.getAvailablePatterns().size() != expected) return false;
        var crafting = main.serviceGrid().getCraftingService();
        for (int i = 0; i < expected; i++) if (!crafting.isCraftable(outputKeys.get(i))) return false;
        return true;
    }

    private static boolean durableNetworkSessionReady() throws Exception {
        Path file=saveDirectory.resolve("data/gtl_cloud_import_sessions").resolve(main.libraryId()+".dat");
        if(!Files.isRegularFile(file))return false;
        var wrapper=NbtIo.readCompressed(file.toFile());var payload=wrapper.getCompound("payload");
        require(wrapper.getString("sha256").equals(CloudPatternStore.checksum(payload)),"durable_network_session_checksum_before_verification_"+TARGETS[tier]);
        if(payload.getInt("moved")!=((Number)round.get("actualNewOriginals")).intValue())return false;
        for(var raw:payload.getList("batches",net.minecraft.nbt.Tag.TAG_COMPOUND))if(!((CompoundTag)raw).getString("phase").equals("COMMITTED"))return false;
        return true;
    }

    private static void verifyRound() throws Exception {
        long verify = System.nanoTime(), walBytes = 0; int verified = 0; int target = TARGETS[tier];
        var records = new HashMap<UUID,CompoundTag>();var walPaths=new HashMap<UUID,Path>();
        var origins = sources.isEmpty()?List.of(main.getPos()):sources.stream().map(MEPatternBufferPartMachine::getPos).toList();
        for(BlockPos origin:origins){
            Path directory=saveDirectory.resolve("data/gtl_cloud_import")
                    .resolve(UUID.nameUUIDFromBytes(level.dimension().location().toString().getBytes(StandardCharsets.UTF_8))+"_"+origin.asLong());
            if(!Files.isDirectory(directory))continue;
            try(var files=Files.list(directory)){
                for(var file:files.filter(path->path.getFileName().toString().endsWith(".dat")).toList()){
                    CompoundTag raw=NbtIo.readCompressed(file.toFile());
                    if(raw.getInt("schema")==2){
                        var checked=raw.copy();checked.remove("sha256");
                        require(CloudPatternStore.checksum(checked).equals(raw.getString("sha256")),"production_batch_WAL_checksum_"+file.getFileName());
                        for(var value:raw.getList("rows",net.minecraft.nbt.Tag.TAG_COMPOUND)){
                            var record=(CompoundTag)value;records.put(record.getUUID("operation"),record);walPaths.put(record.getUUID("operation"),file);
                        }
                    }else{records.put(raw.getUUID("operation"),raw);walPaths.put(raw.getUUID("operation"),file);}
                }
            }
        }
        var countedWals=new java.util.HashSet<Path>();
        for (var transaction : transactions.entrySet()) {
            var row = transaction.getValue(); if (row.containsKey("WALVerified")) continue;
            int index = ((Number) row.get("index")).intValue();
            var receipt = library.receipt(transaction.getKey()); var entry = library.entry(receipt.subject());
            require(receipt.status().equals("ACKNOWLEDGED") && entry.leases().size() == children.size()
                    && children.stream().allMatch(child -> entry.lease(child.cloudNodeId()) != null), "durable_original_has_both_real_endpoint_leases_" + index);
            Path wal=walPaths.get(transaction.getKey());var record=records.get(transaction.getKey());
            require(wal!=null&&record!=null&&Files.isRegularFile(wal), "real_source_WAL_exists_" + index);
            require(record.getString("status").equals("COMMITTED") && record.getUUID("operation").equals(transaction.getKey())
                    && record.getUUID("entry").equals(entry.id()) && ItemStack.matches(ItemStack.of(record.getCompound("expected")), originals.get(index)),
                    "real_WAL_committed_and_exact_original_matches_ACK_" + index);
            long size = Files.size(wal);if(countedWals.add(wal))walBytes+=size;verified++;row.put("WALVerified",true);row.put("actualWALFile",wal.toString());row.put("actualWALFileBytes",size);
        }
        require(library.entryCount() == target && library.storedCount() == target && acknowledged == target,
                "source_and_authority_original_count_conservation_at_" + target);
        round.put("newCommittedRowsVerified", verified);round.put("newCommittedWALFilesVerified",countedWals.size());round.put("newCommittedWALBytes", walBytes);
        if(PATH.equals("bulk")){
            Path file=saveDirectory.resolve("data/gtl_cloud_import_sessions").resolve(main.libraryId()+".dat");
            require(Files.isRegularFile(file),"real_network_session_durable_history_exists_"+target);
            var wrapper=NbtIo.readCompressed(file.toFile());var payload=wrapper.getCompound("payload");
            require(wrapper.getString("sha256").equals(CloudPatternStore.checksum(payload)),"real_network_session_history_checksum_"+target);
            require(payload.getInt("moved")==((Number)round.get("actualNewOriginals")).intValue()
                    &&payload.getInt("skipped")==0&&payload.getString("stopReason").isEmpty(),"real_network_session_history_matches_actual_debits_"+target);
            round.put("durableNetworkSessionBytes",Files.size(file));
        }
        round.put("authorityDiskBytesAtVerification", authorityDiskBytes()); round.put("WALVerificationMicrosExcludedFromImport", (System.nanoTime() - verify) / 1000);
    }

    private static long authorityDiskBytes() throws Exception {
        String id = main.libraryId().toString();
        try (var files = Files.list(saveDirectory.resolve("data"))) {
            long bytes = 0;
            for (var file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("gtl_cloud_library_" + id + ".dat") || name.startsWith("gtl_cloud_originals_" + id + "_")) bytes += Files.size(file);
            }
            return bytes;
        }
    }

    private static long sinceRound() { return (System.nanoTime() - roundStarted) / 1000; }
    private static void phase(String value) { CloudDispatchLoadRuntimeChecks.setObservationPhase(value); }
    private static void publishMetadata() {
        var data = new LinkedHashMap<String, Object>(); data.put("path", PATH); data.put("cumulativeTargets", TARGETS);
        data.put("generatedLiveRecipes", recipes.size()); data.put("physicalOriginalPreparationMicros", preparationMicros);
        data.put("uncompressedOriginalNBTBytesPrepared", originalsBytes); data.put("submitted", submitted); data.put("authorityEntries", library.entryCount());
        data.put("actualPhysicalSources",sources.stream().map(source->Map.of("definition",source.getDefinition().getId().toString(),"position",source.getPos().toShortString(),"patternSlots",source.getPatternInventory().getSlots())).toList());
        data.put("acknowledged", acknowledged); data.put("fullyPublished", published); data.put("rounds", rounds);
        data.put("actualNetworkImportSessions", submissions); data.put("actualUploadTransactions", new ArrayList<>(transactions.values()));
        data.put("generatorProductionEntrypointIncludedInMainTick",PATH.equals("generator"));
        data.put("boundary", "One fresh marked world per import path. 32/256/1024 are cumulative distinct valid processing originals and distinct effective outputs. bulk uses bounded real owned supported Super Pattern Buffer sources with FOA disabled, seeded with all32/224/768 new originals before one real central network_import action per tier. Source capacity is read from actual instances; production naturally discovers sources, plans, debits, commits payloads, ACKs and finishes the entire network session. The helper never ticks the importer or assumes action return means debit/ACK completion. generator uses the actual tool queue, begin/tick, physical central inbox and natural deferred WAL/ACK callbacks. No direct store.upload/acknowledge/checkpoint is called. Each source original is debited by production code. Encoding/source seeding and WAL verification are fixture preparation/verification; reported separately. Explicit production generator begin/tick calls are separately measured and retained in server HIGHEST START-to-LOWEST END time, together with natural deferred production callbacks. Pure helper source seeding, queue configuration, observations and WAL verification are excluded. The network_import action is separately timed; in this helper revision it remains within excluded helper work and only its naturally deferred session ticks enter main timing. Transaction timestamps are20tick observation bounds, not internal I/O profiler events. Per-original checkpoint count/duration is not directly instrumented; ACK completion and actual durable file sizes are observed. Three terminal requests occur during import against the prior complete catalogue; the final request verifies the full1024 catalogue. Physical source providers may temporarily index originals before import; final publication requires both real cloud providers and exact current lease ownership. No job, CPU, WAL, receipt or lease state is injected.");
        CloudDispatchLoadRuntimeChecks.putObservationMetadata("physicalBatchImport", data);
    }

    @SuppressWarnings("unchecked")
    private static void registerActor() throws Exception {
        var list = level.getServer().getPlayerList(); online = null;
        for (Class<?> owner = list.getClass(); owner != null && online == null; owner = owner.getSuperclass())
            for (var field : owner.getDeclaredFields()) {
                String type = field.getGenericType().getTypeName();
                if (!Map.class.isAssignableFrom(field.getType()) || !type.contains("java.util.UUID") || !type.contains(ServerPlayer.class.getName())) continue;
                field.setAccessible(true); online = (Map<UUID, ServerPlayer>) field.get(list); break;
            }
        require(online != null, "isolated_actor_UUID_index_available"); previousActor = online.get(player.getUUID());
        require(previousActor == null || previousActor == player, "fixture_refuses_to_replace_another_online_actor");
        if (previousActor == null) { online.put(player.getUUID(), player); actorRegistered = true; }
        require(list.getPlayer(player.getUUID()) == player, "natural_deferred_import_resolves_fixture_actor");
    }

    public static void cleanup() {
        if (cleaned || player == null) return;
        PatternGeneratorTransfer.cancel(player);
        if (savedInventory != null) { player.getInventory().clearContent(); player.getInventory().load(savedInventory); }
        if (savedPosition != null) player.setPos(savedPosition);
        if (savedMenu != null) player.containerMenu = savedMenu;
        if (actorRegistered && online != null) online.remove(player.getUUID(), player);
        actorRegistered = false; cleaned = true;
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("upload_load:" + label);
        checks.add("upload_load:" + label);
    }
}
