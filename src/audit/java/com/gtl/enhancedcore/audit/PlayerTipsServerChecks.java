package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.ResearchData;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferMethods;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferAutoName;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNameAccess;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNaming;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import com.gtl.enhancedcore.common.util.TooltipPolicy;
import com.gtl.enhancedcore.mixin.gtceu.IvNativeMutableAccessor;
import com.gtl.enhancedcore.mixin.gtceu.MultiblockMachineDefinitionAccessor;
import com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutableSuprachronalAssemblyLineMachine;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.SuprachronalAssemblyLineModuleMachine;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

public final class PlayerTipsServerChecks {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BOOT_ID = UUID.randomUUID().toString();
    private static final String MARKER = "[PLAYER_TIPS_SERVER]";
    private static Session session;

    private PlayerTipsServerChecks() {}

    public static void begin(MinecraftServer server) throws Exception {
        if (!Boolean.getBoolean("gtl.enhancedcore.playerTipsServer")) return;
        if (session != null) throw new IllegalStateException("The audit cannot be restarted by a script reload");
        session = new Session(server);
        try {
            session.prepare();
        } catch (Throwable failure) {
            session.fail(failure);
        }
    }

    public static void tick(MinecraftServer server) throws Exception {
        if (session == null || session.finished) return;
        if (session.server != server || !server.isSameThread()) {
            throw new IllegalStateException("The audit must run synchronously on its original server thread");
        }
        try {
            session.advance();
        } catch (Throwable failure) {
            session.fail(failure);
        }
    }

    private static final class Session {
        private final MinecraftServer server;
        private final ServerLevel world;
        private final Path game;
        private final Path worldPath;
        private final Path resultPath;
        private final Path checkpointPath;
        private final JsonObject request;
        private final String runId;
        private final String phase;
        private final boolean restart;
        private final List<WorkableElectricMultiblockMachine> natives = new ArrayList<>();
        private final List<String> scenarios = new ArrayList<>();
        private JsonObject checkpoint;
        private MutableSuprachronalAssemblyLineMachine controller;
        private SuprachronalAssemblyLineModuleMachine legacyModule;
        private WorkableElectricMultiblockMachine ordinaryController;
        private MESuperPatternBufferPartMachine detached;
        private MESuperPatternBufferPartMachine detachedEmpty;
        private MESuperPatternBufferPartMachine paidBuffer;
        private IvNativeFixture.Wireless wireless;
        private int checks;
        private int ticks;
        private boolean finished;
        private boolean networkPrepared;
        private BlockPos legacyInput;

        private Session(MinecraftServer server) throws Exception {
            this.server = server;
            require(server.isSameThread(), "Entry must use the real server thread");
            Path allowed = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/player-facing-tips-20261001/server").toRealPath();
            game = FMLPaths.GAMEDIR.get().toRealPath();
            runId = System.getProperty("gtl.enhancedcore.playerTipsRunId", "");
            phase = System.getProperty("gtl.enhancedcore.playerTipsPhase", "");
            require(runId.matches("tips-[0-9]{8}-[0-9]{6}-[0-9a-f]{32}"), "Invalid explicit runId");
            require(phase.equals("fresh") || phase.equals("restart"), "Invalid phase");
            restart = phase.equals("restart");
            require(game.equals(allowed.resolve("runs").resolve(runId).resolve("instance")),
                    "The audit cannot operate outside its unique isolated instance");
            request = readJson(game.resolve("kubejs/player_tips_server_request.json"));
            JsonObject marker = readJson(game.resolve("player-tips-server.marker.json"));
            require(marker.get("isolated").getAsBoolean() && runId.equals(marker.get("runId").getAsString()),
                    "Missing matching isolated marker");
            require(runId.equals(request.get("runId").getAsString())
                    && phase.equals(request.get("phase").getAsString()), "Request identity differs from JVM properties");
            require(request.get("schema").getAsInt() == 1, "Unknown request schema");
            require(Files.isRegularFile(game.resolve("mods/GTL-Enhancedcore-2.8.9.jar"), LinkOption.NOFOLLOW_LINKS),
                    "The frozen 2.8.9 core is absent");
            require(sha256(game.resolve("mods/GTL-Enhancedcore-2.8.9.jar"))
                    .equals(request.get("candidateSha256").getAsString()), "Core changed after freezing");
            require(sha256(game.resolve("mods/enhancedcore-audit-runtime.jar"))
                    .equals(request.get("helperSha256").getAsString()), "Helper changed after freezing");
            require(ModList.get().getModContainerById("gtl_enhancedcore").orElseThrow()
                    .getModInfo().getVersion().toString().equals("2.8.9"), "The loaded core is not 2.8.9");
            require(!Boolean.getBoolean("gtl.enhancedcore.fullNative"), "This test requires the audited compact native fixture");
            world = server.overworld();
            worldPath = server.getWorldPath(LevelResource.ROOT).toRealPath();
            require(worldPath.equals(game.resolve("world")), "The saved world escaped the isolated instance");
            resultPath = game.resolve("kubejs/player_tips_server_" + phase + "_result.json");
            checkpointPath = game.resolve("kubejs/player_tips_server_checkpoint.json");
            require(!Files.exists(resultPath), "A phase receipt already exists; never overwrite evidence");
        }

        private void prepare() throws Exception {
            if (restart) {
                checkpoint = readJson(checkpointPath);
                require(checkpoint.get("status").getAsString().equals("AWAIT_RESTART"), "No pending fresh checkpoint");
                require(runId.equals(checkpoint.get("runId").getAsString()), "Checkpoint belongs to another run");
                require(!BOOT_ID.equals(checkpoint.get("jvmBootId").getAsString()), "A script reload is not a JVM restart");
                require(ProcessHandle.current().pid() != checkpoint.get("pid").getAsLong(), "The fresh JVM is still in use");
                require(worldPath.toString().equals(checkpoint.get("worldPath").getAsString()), "Restart changed worlds");
                for (String key : List.of("candidateSha256", "helperSha256")) {
                    require(request.get(key).equals(checkpoint.get(key)), "Restart changed frozen input: " + key);
                }
                var mainDefinition = definition("gtceu:suprachronal_assembly_line");
                nativePattern(mainDefinition, true);
                natives.add(machine(position(checkpoint.getAsJsonArray("controllerPos")), MutableSuprachronalAssemblyLineMachine.class));
            } else {
                require(!Files.exists(checkpointPath), "Fresh world unexpectedly contains a checkpoint");
                require(IvNativeFixture.MACHINES.isEmpty() && IvNativeFixture.BUFFERS.isEmpty(),
                        "The native fixture was already used in this JVM");
                IvNativeFixture.setup(world);
                natives.addAll(IvNativeFixture.MACHINES);
            }
            controller = natives.stream().filter(MutableSuprachronalAssemblyLineMachine.class::isInstance)
                    .map(MutableSuprachronalAssemblyLineMachine.class::cast).findFirst().orElseThrow();
            nativePattern(controller.getDefinition(), true);
            if (!restart) {
                int baseX = natives.indexOf(controller) * 16;
                int added = 0;
                for (int offsetX = 0; offsetX < 3 && added < 3; offsetX++) {
                    for (int offsetY = 0; offsetY < 3 && added < 3; offsetY++) {
                        for (int offsetZ = 0; offsetZ < 3 && added < 3; offsetZ++) {
                            BlockPos extra = new BlockPos(baseX + offsetX, 100 + offsetY, offsetZ);
                            if (world.getBlockState(extra).getBlock() == block("gtceu:solid_machine_casing")) {
                                world.setBlock(extra, block("gtladditions:me_super_pattern_buffer").defaultBlockState(), 3);
                                added++;
                            }
                        }
                    }
                }
                require(added == 3, "Missing three additional real assemblies in the compact main shell");
            }
            var legacyDefinition = definition("gtceu:suprachronal_assembly_line_module");
            var ordinaryDefinition = definition("gtceu:large_chemical_reactor");
            var legacyPattern = modulePattern(legacyDefinition);
            var ordinaryPattern = nativePattern(ordinaryDefinition, false);
            if (restart) {
                legacyModule = machine(position(checkpoint.getAsJsonArray("legacyPos")), SuprachronalAssemblyLineModuleMachine.class);
                ordinaryController = machine(position(checkpoint.getAsJsonArray("ordinaryPos")), WorkableElectricMultiblockMachine.class);
                detached = machine(position(checkpoint.getAsJsonArray("detachedPos")), MESuperPatternBufferPartMachine.class);
                detachedEmpty = machine(position(checkpoint.getAsJsonArray("detachedEmptyPos")), MESuperPatternBufferPartMachine.class);
                legacyInput = position(checkpoint.getAsJsonArray("legacyInputPos"));
            } else {
                legacyModule = (SuprachronalAssemblyLineModuleMachine) placeShell(legacyPattern, new BlockPos(256, 100, 0));
                ordinaryController = (WorkableElectricMultiblockMachine) placeShell(ordinaryPattern, new BlockPos(352, 100, 0));
                detached = placeBuffer(new BlockPos(400, 100, 0));
                detachedEmpty = placeBuffer(new BlockPos(401, 100, 0));
                detached.setCustomName("Audit detached / 孤立总成");
                detachedEmpty.setCustomName("");
                legacyInput = findBlock(new BlockPos(256, 100, 0), block("gtceu:iv_input_bus"));
            }
            if (!restart) {
                for (var nativeMachine : natives) nativeMachine.getRecipeLogic().setWorkingEnabled(false);
                ordinaryController.getRecipeLogic().setWorkingEnabled(false);
            }
            GTLEnhancedcore.LOGGER.info("{} BEGIN runId={} phase={} fixture=compact-native-real-controllers", MARKER, runId, phase);
        }

        private void advance() throws Exception {
            ticks++;
            if (ticks > 2400) throw new AssertionError("Timed out waiting for real formation or the ME node");
            if (ticks < 80 || !controller.isFormed() || !controller.isRecipeLogicAvailable() || !legacyModule.isFormed()
                    || !ordinaryController.isFormed() || !ordinaryController.isRecipeLogicAvailable()) return;
            if (!networkPrepared) {
                var mainBuffers = buffers(controller);
                require(mainBuffers.size() == 4, "The formed main controller must own four independent real assemblies");
                paidBuffer = mainBuffers.getFirst();
                if (restart) {
                    require(paidBuffer.getPos().equals(position(checkpoint.getAsJsonArray("paidBufferPos"))),
                            "The saved paid assembly changed its deterministic position order");
                } else {
                    mainBuffers.get(2).setCustomName("Audit manual / 我的总成");
                    mainBuffers.get(3).setCustomName("");
                    var network = IvNativeFixture.networkPosition(paidBuffer);
                    world.setBlock(network, block("ae2:creative_energy_cell").defaultBlockState(), 3);
                }
                wireless = IvMachineScope.batching(controller) ? IvNativeFixture.attachWireless(controller) : null;
                if (wireless != null) wireless.available = BigInteger.TEN.pow(30);
                networkPrepared = true;
                return;
            }
            if (!paidBuffer.getMainNode().isActive()) return;
            verifyTips();
            if (restart) verifyRestartState();
            else createPaidState();
            verifyModuleGate();
            verifyJade();
            if (restart) finishRestart();
            else finishFresh();
        }

        private void createPaidState() throws Exception {
            require(IvBuffers.bind(paidBuffer, controller), "The real assembly rejected its exclusive native controller");
            require(IvBuffers.state(paidBuffer).jobs.isEmpty(), "Fresh assembly unexpectedly contains an order");
            var recipeType = controller.getRecipeTypes()[0];
            var expectedRecipeId = new ResourceLocation("gtl_enhancedcore",
                    recipeType.registryName.getPath() + "/player_tips_server_paid_" + recipeType.registryName.getPath());
            long expectedRecipeCount = world.getRecipeManager().getAllRecipesFor(recipeType).stream()
                    .filter(candidateRecipe -> candidateRecipe.id.equals(expectedRecipeId)).count();
            require(expectedRecipeCount == 1, "The exact audit recipe is not uniquely registered: " + expectedRecipeId);
            var pattern = PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{new GenericStack(AEItemKey.of(new ItemStack(Items.NETHER_STAR)), 1)},
                    new GenericStack[]{new GenericStack(AEItemKey.of(new ItemStack(Items.HEART_OF_THE_SEA)), 1)});
            PatternQuickUploadMetadata.writeRecipeTypeId(pattern, recipeType.registryName);
            paidBuffer.getTerminalPatternInventory().setItemDirect(0, pattern);
            var details = ((IvBufferMethods) (Object) paidBuffer).iv$realPattern(0, pattern);
            require(details != null, "The real encoded pattern did not enter native routing");
            KeyCounter[] holders = Arrays.stream(details.getInputs()).map(input -> {
                var holder = new KeyCounter();
                holder.add(input.getPossibleInputs()[0].what(), input.getMultiplier() * 72);
                return holder;
            }).toArray(KeyCounter[]::new);
            var inputKey = AEItemKey.of(new ItemStack(Items.NETHER_STAR));
            require(holders.length == 1 && holders[0].get(inputKey) == 72,
                    "The real dispatch did not contain the exact material sentinel");
            require(paidBuffer.pushPattern(details, holders), "The real AE dispatch was rejected");
            require(holders[0].get(inputKey) == 72, "Accepted dispatch changed caller-owned accounting counters");
            var state = IvBuffers.state(paidBuffer);
            require(state.jobs.size() == 1, "A single accepted dispatch did not create exactly one isolated order");
            IvJob job = state.jobs.getFirst();
            require(job.inventory.size() == 1 && job.inventory.getOrDefault(inputKey, 0L) == 72,
                    "The accepted isolated ledger did not take over the exact dispatched material");
            holders[0].clear();
            require(job.inventory.size() == 1 && job.inventory.getOrDefault(inputKey, 0L) == 72,
                    "Reusing the caller-owned counters changed the accepted isolated material");
            require(job.totalOperations == 72, "The accepted order changed the exact operation count: " + job.totalOperations);
            require(job.recipe.id.equals(expectedRecipeId),
                    "The order used " + job.recipe.id + " instead of the exact registered recipe " + expectedRecipeId);
            controller.getEnergyContainer().addEnergy(controller.getEnergyContainer().getEnergyCapacity());
            var logic = controller.getRecipeLogic();
            require(logic instanceof IvNativeAccess && logic instanceof IvNativeMutableAccessor,
                    "The original MutableRecipesLogic lost its IV/research accessors");
            require(((IvNativeMutableAccessor) logic).iv$recipeCheck() != null, "The original research predicate was removed");
            var research = new ResearchData();
            research.add(new ResearchData.ResearchEntry("gtl_enhancedcore:player_tips_server_missing_research", new ItemStack(Items.PAPER)));
            var missingResearch = new ResearchCondition(research);
            String materialsBefore = job.save().getList("input", Tag.TAG_COMPOUND).toString();
            long energyBefore = controller.getEnergyContainer().getEnergyStored();
            BigInteger wirelessBefore = wireless == null ? BigInteger.ZERO : wireless.available;
            job.recipe.conditions.add(missingResearch);
            logic.setWorkingEnabled(true);
            try {
                logic.serverTick();
                require(!job.active() && job.remaining == 72, "Missing research consumed or started the isolated order");
                require(materialsBefore.equals(job.save().getList("input", Tag.TAG_COMPOUND).toString()),
                        "The research gate erased accepted material");
                require(controller.getEnergyContainer().getEnergyStored() == energyBefore
                        && (wireless == null || wirelessBefore.equals(wireless.available)), "The research gate paid energy");
                require(((IvNativeAccess) logic).iv$summary().toString().contains("iv_native_research_"),
                        "The preserved research gate lost its native missing-research diagnostic");
            } finally {
                job.recipe.conditions.remove(missingResearch);
            }
            logic.serverTick();
            require(job.active() && job.elapsed == 1 && !job.halted, "The funded order did not make real paid progress");
            require(job.energyTotal.signum() > 0 && job.energyLeft.compareTo(job.energyTotal) < 0,
                    "The retained native ledger has no actual paid energy");
            require(((IvNativeAccess) logic).iv$managed(), "The real native recipe logic did not enter isolated scheduling");
            logic.setWorkingEnabled(false);
            IvNativeRecoveryChecks.seedFailedCache(logic, job.recipe);
            require(!logic.checkMatchedRecipeAvailable(job.recipe), "The native failed-match cache bypassed isolated scheduling");
            CompoundTag paidBefore = state.save();
            for (int retry = 0; retry < 8; retry++) logic.serverTick();
            require(paidBefore.equals(state.save()), "A paused native recipe tick changed the paid ledger");
            var moduleLogic = legacyModule.getRecipeLogic();
            GTRecipe legacyRecipe = legacyModule.getRecipeTypes()[0]
                    .recipeBuilder(new ResourceLocation("gtl_enhancedcore:player_tips_server_legacy_paid"))
                    .inputItems(Items.EMERALD).outputItems(Items.DIAMOND).duration(311).EUt(2048).buildRawRecipe();
            writeField(RecipeLogic.class, moduleLogic, "lastRecipe", legacyRecipe);
            writeField(RecipeLogic.class, moduleLogic, "lastOriginRecipe", legacyRecipe.copy());
            writeField(RecipeLogic.class, moduleLogic, "duration", 311);
            moduleLogic.setProgress(43);
            moduleLogic.setWorkingEnabled(false);
            require(legacyStock().insertItem(0, new ItemStack(Items.EMERALD, 37), false).isEmpty(),
                    "The physical module input bus rejected the legacy material sentinel");
            legacyModule.getEnergyContainer().addEnergy(legacyModule.getEnergyContainer().getEnergyCapacity());
            require(legacyModule.getEnergyContainer().getEnergyStored() > 0, "The disabled module has no real saved energy");
            legacyModule.markDirty();
            world.getBlockEntity(legacyInput).setChanged();
            scenarios.add("native_research_material_energy_gate");
            scenarios.add("native_real_dispatch_paid_progress");
            scenarios.add("native_failed_cache_and_pause");
        }

        private void verifyRestartState() throws Exception {
            require(!controller.getRecipeLogic().isWorkingEnabled(), "Restart discarded the main operator pause");
            require(TagParser.parseTag(checkpoint.get("paidOrders").getAsString()).equals(IvBuffers.state(paidBuffer).save()),
                    "The independent JVM changed paid orders, identities, material or settled output");
            require(checkpoint.getAsJsonObject("moduleState").equals(moduleState()),
                    "Reload or the disabled-module gate erased physical material, energy, recipe or paid progress");
            for (JsonElement entry : checkpoint.getAsJsonArray("names")) {
                JsonObject saved = entry.getAsJsonObject();
                var buffer = machine(position(saved.getAsJsonArray("pos")), MESuperPatternBufferPartMachine.class);
                var names = (SuperBufferNameAccess) (Object) buffer;
                require(Objects.equals(saved.get("stored").getAsString(), buffer.getCustomName()), "Restart changed a saved assembly name");
                require(saved.get("automatic").getAsString().equals(names.enhanced$getAutomaticName())
                        && saved.get("manual").getAsBoolean() == names.enhanced$isManualName(), "Restart changed name ownership");
            }
            require(!legacyModule.findAndConnectToHost() && legacyModule.getHost() == null
                    && legacyModule.getHostPosition() == null, "The disk-restored hostPosition reconnected the disabled module");
            require(!legacyModule.isRecipeLogicAvailable(), "The disabled module became available after real restart");
            scenarios.add("independent_jvm_saved_paid_state");
            scenarios.add("persisted_legacy_host_position_detached");
        }

        private void verifyModuleGate() throws Exception {
            require(controller.isFormed() && controller.isRecipeLogicAvailable() && legacyModule.isFormed(),
                    "The module audit requires an actually formed main and legacy extension");
            var paidBefore = IvBuffers.state(paidBuffer).save();
            var moduleBefore = moduleState();
            var mainLogic = controller.getRecipeLogic();
            long mainEnergy = controller.getEnergyContainer().getEnergyStored();
            BigInteger wirelessBefore = wireless == null ? BigInteger.ZERO : wireless.available;
            var capabilities = (IRecipeCapabilityMachine) (Object) controller;
            var dataHatch = capabilities.getDataAccessHatch();
            var predicate = ((IvNativeMutableAccessor) mainLogic).iv$recipeCheck();
            require(predicate != null && Arrays.asList(controller.getRecipeTypes()).contains(GTRecipeTypes.ASSEMBLY_LINE_RECIPES),
                    "The original research predicate or main assembly-line recipe type was removed");
            int delegated = SuprachronalModuleRemovalChecks.run(controller, legacyModule);
            require(delegated > 0, "The concrete Carson audit did not execute");
            checks += delegated;
            require(moduleBefore.equals(moduleState()), "The removal gate changed physical module material or paid progress");
            require(paidBefore.equals(IvBuffers.state(paidBuffer).save()), "The removal gate changed isolated main orders");
            require(mainLogic == controller.getRecipeLogic() && mainLogic instanceof IvNativeAccess
                    && predicate == ((IvNativeMutableAccessor) mainLogic).iv$recipeCheck()
                    && dataHatch == capabilities.getDataAccessHatch(), "The gate replaced IV logic, research or the data hatch");
            require(mainEnergy == controller.getEnergyContainer().getEnergyStored()
                    && (wireless == null || wirelessBefore.equals(wireless.available)), "The gate changed main energy");
            scenarios.add("carson_real_formed_available_main");
            scenarios.add("module_material_energy_saved_progress_conservation");
            scenarios.add("research_recipe_types_native_iv_retained");
        }

        private void verifyJade() throws Exception {
            var mainBuffers = buffers(controller);
            require(mainBuffers.size() == 4 && controller.getRecipeTypes().length >= 2,
                    "The multi-assembly scenario needs two genuine recipe types and four genuine parts");
            for (int index = 0; index < 2; index++) {
                var buffer = mainBuffers.get(index);
                require(IvBuffers.isolated(buffer), "The main automatic assembly did not remain isolated");
                checks += SuperBufferJadeNameChecks.inspectAutomatic(buffer, controller.getRecipeTypes()[index].registryName.toLanguageKey());
            }
            require(mainBuffers.get(2).getCustomName().equals("Audit manual / 我的总成")
                    && ((SuperBufferNameAccess) (Object) mainBuffers.get(2)).enhanced$isManualName(), "The manual name was overwritten");
            require(mainBuffers.get(3).getCustomName().isEmpty()
                    && ((SuperBufferNameAccess) (Object) mainBuffers.get(3)).enhanced$isManualName(), "The explicit empty name was filled");
            checks += SuperBufferJadeNameChecks.inspect(mainBuffers.get(2));
            checks += SuperBufferJadeNameChecks.inspect(mainBuffers.get(3));
            var ordinaryBuffers = buffers(ordinaryController);
            require(!ordinaryBuffers.isEmpty() && !IvBuffers.targetController(ordinaryController), "Missing non-isolated formed controller");
            for (var buffer : ordinaryBuffers) {
                require(!IvBuffers.isolated(buffer), "The non-isolated assembly became an isolated order host");
                checks += SuperBufferJadeNameChecks.inspectAutomatic(buffer, SuperBufferNaming.automaticKey(buffer));
            }
            require(detached.getControllers().isEmpty() && detachedEmpty.getControllers().isEmpty(), "Detached assemblies acquired a controller");
            require(!IvBuffers.isolated(detached) && !IvBuffers.isolated(detachedEmpty), "Detached clean assemblies acquired an order owner");
            checks += SuperBufferJadeNameChecks.inspect(detached);
            checks += SuperBufferJadeNameChecks.inspect(detachedEmpty);
            scenarios.add("jade_formed_automatic_multi_manual_empty");
            scenarios.add("jade_nonisolated_and_detached");
            scenarios.add("jade_read_only_paid_orders_and_names");
        }

        @SuppressWarnings("unchecked")
        private void verifyTips() throws Exception {
            var definitions = new ArrayList<MultiblockMachineDefinition>();
            definitions.add(controller.getDefinition());
            definitions.add(legacyModule.getDefinition());
            Field originalBuilder = com.gregtechceu.gtceu.api.machine.MachineDefinition.class.getDeclaredField("tooltipBuilder");
            originalBuilder.setAccessible(true);
            for (var definition : definitions) {
                var actual = new ArrayList<Component>();
                definition.getTooltipBuilder().accept(definition.asStack(), actual);
                var inherited = new ArrayList<Component>();
                var raw = (BiConsumer<ItemStack, List<Component>>) originalBuilder.get(definition);
                if (raw != null) raw.accept(definition.asStack(), inherited);
                require(count(actual, TooltipPolicy.SOURCE_KEY) == 1, "Registered foreign tips lost or duplicated the modification source");
                require(count(actual, TooltipPolicy.SHIFT_HINT) == count(inherited, TooltipPolicy.SHIFT_HINT),
                        "A new project Shift menu hid foreign-machine defaults");
                boolean disabledModule = definition.getId().toString().equals("gtceu:suprachronal_assembly_line_module");
                if (disabledModule) {
                    require(count(actual, TooltipPolicy.RECIPE_HEADER) == 0 && count(actual, TooltipPolicy.RECIPE_GROUP) == 0,
                            "The disabled extension still advertises an active recipe catalog");
                    require(count(actual, "gtceu.machine.available_recipe_map_1.tooltip") == 0
                                    && count(actual, "gtceu.machine.available_recipe_map_2.tooltip") == 0,
                            "The disabled extension retained an obsolete recipe promise");
                } else {
                    var recipeTypes = definition.getRecipeTypes();
                    require(recipeTypes != null && recipeTypes.length > 0, "The real controller lost its registered recipe types");
                    require(count(actual, TooltipPolicy.RECIPE_HEADER) == (recipeTypes.length + TooltipPolicy.RECIPES_PER_LINE - 1)
                                    / TooltipPolicy.RECIPES_PER_LINE,
                            "Registered recipe catalogs are incomplete or not default-visible");
                    for (var type : recipeTypes) {
                        require(type != null && type.registryName != null, "Invalid real registered recipe metadata");
                        require(count(actual, type.registryName.toLanguageKey()) > 0, "A real recipe type is missing from the default catalog");
                    }
                }
                boolean nativeTarget = IvMachineScope.nativeTarget(definition.getId());
                require(count(actual, TooltipPolicy.CROSS_RECIPE_KEY) == (nativeTarget && definition.getRecipeTypes().length > 1 ? 1 : 0),
                        "Cross-recipe claims differ from the registered native contract");
                var removed = new ArrayList<String>();
                var pages = nativeTarget ? TooltipPolicy.nativePages(definition.getId().getPath()) : List.of("suprachronal_module");
                for (String page : pages) {
                    removed.addAll(TooltipPolicy.replacedKeys(page));
                    for (String key : TooltipPolicy.page(page).summary()) require(count(actual, key) == 1, "Missing default-visible summary: " + key);
                    for (String key : TooltipPolicy.page(page).details()) require(count(actual, key) == 1, "Hidden foreign detail: " + key);
                }
                for (Component original : inherited) {
                    if (removed.stream().anyMatch(key -> MachineTooltips.containsKey(original, key))) continue;
                    require(actual.stream().anyMatch(line -> line.getString().equals(original.getString())),
                            "An unreplaced upstream requirement or flavor row was removed");
                }
                for (Component row : actual) {
                    if (MachineTooltips.containsKey(row, TooltipPolicy.SOURCE_KEY)
                            || MachineTooltips.containsKey(row, TooltipPolicy.CROSS_RECIPE_KEY)) {
                        require(row.getStyle().getColor() != null, "The actual default source/parallel row lost native rainbow coloring");
                    }
                }
            }
            require(count(tips(controller.getDefinition()), "gtceu.machine.suprachronal_assembly_line.tooltip.1") == 0,
                    "The removed extension promise remains on the main item");
            require(count(tips(legacyModule.getDefinition()), "gtceu.machine.suprachronal_assembly_line_module.tooltip.0") == 0,
                    "The disabled extension item still promises operation");
            scenarios.add("registered_default_player_tips_catalog_and_limits");
            scenarios.add("registered_host_and_module_disable_warnings");
        }

        private IItemHandler legacyStock() {
            return world.getBlockEntity(legacyInput).getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .orElseThrow(() -> new AssertionError("The module input bus has no real item capability"));
        }

        private MetaMachine placeShell(com.gregtechceu.gtceu.api.pattern.BlockPattern pattern, BlockPos origin) {
            var repetitions = java.util.Arrays.stream(pattern.aisleRepetitions).mapToInt(range -> range[0]).toArray();
            var shape = pattern.getPreview(repetitions);
            MetaMachine found = null;
            for (int offsetX = 0; offsetX < shape.length; offsetX++) {
                for (int offsetY = 0; offsetY < shape[offsetX].length; offsetY++) {
                    for (int offsetZ = 0; offsetZ < shape[offsetX][offsetY].length; offsetZ++) {
                        BlockPos target = origin.offset(offsetX, offsetY, offsetZ);
                        force(target);
                        world.setBlock(target, shape[offsetX][offsetY][offsetZ].getBlockState(), 3);
                        var candidate = MetaMachine.getMachine(world, target);
                        if (candidate instanceof WorkableElectricMultiblockMachine) found = candidate;
                    }
                }
            }
            require(found != null, "Missing actual registered controller in the compact shell");
            return found;
        }

        private MESuperPatternBufferPartMachine placeBuffer(BlockPos target) {
            force(target);
            world.setBlock(target, block("gtladditions:me_super_pattern_buffer").defaultBlockState(), 3);
            return machine(target, MESuperPatternBufferPartMachine.class);
        }

        private BlockPos findBlock(BlockPos origin, Block expected) {
            for (BlockPos target : BlockPos.betweenClosed(origin, origin.offset(2, 2, 2))) {
                if (world.getBlockState(target).getBlock() == expected) return target.immutable();
            }
            throw new AssertionError("The compact shell lacks its real material bus");
        }

        private void force(BlockPos target) {
            world.setChunkForced(target.getX() >> 4, target.getZ() >> 4, true);
        }

        private <MachineType> MachineType machine(BlockPos target, Class<MachineType> expected) {
            force(target);
            var actual = MetaMachine.getMachine(world, target);
            require(expected.isInstance(actual), "Saved fixture has the wrong actual machine class at " + target);
            return expected.cast(actual);
        }

        private JsonObject moduleState() {
            var state = new JsonObject();
            var logic = legacyModule.getRecipeLogic();
            state.addProperty("progress", logic.getProgress());
            state.addProperty("duration", logic.getDuration());
            state.addProperty("status", logic.getStatus().toString());
            state.addProperty("energy", Long.toString(legacyModule.getEnergyContainer().getEnergyStored()));
            state.addProperty("stock", legacyStock().getStackInSlot(0).save(new CompoundTag()).toString());
            state.addProperty("recipe", logic.getLastRecipe() == null ? "" : GTRecipeSerializer.CODEC
                    .encodeStart(NbtOps.INSTANCE, logic.getLastRecipe()).getOrThrow(false, ignored -> {}).toString());
            return state;
        }

        private JsonObject identity(String status) {
            var result = new JsonObject();
            for (String key : List.of("schema", "runId", "phase", "candidateSha256", "helperSha256")) {
                result.add(key, request.get(key).deepCopy());
            }
            result.addProperty("status", status);
            result.addProperty("checks", checks);
            result.addProperty("jvmBootId", BOOT_ID);
            result.addProperty("pid", ProcessHandle.current().pid());
            result.addProperty("worldPath", worldPath.toString());
            result.addProperty("fixtureKind", "compact-native-real-controllers");
            result.addProperty("playerInstanceModified", false);
            result.addProperty("otherHostNegativeControl", "NOT_RUN_REDUCED_SCOPE");
            result.addProperty("clientTitleAuditCalled", false);
            result.addProperty("moduleRenderListScope", net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()
                    ? "CLIENT_DIST_API_AVAILABLE_LOGIC_ONLY" : "NOT_RUN_DEDICATED_SERVER_CLIENT_ONLY_API");
            result.add("scenarios", JSON.toJsonTree(scenarios));
            return result;
        }

        private void finishFresh() throws Exception {
            checkpoint = identity("AWAIT_RESTART");
            checkpoint.add("controllerPos", coordinates(controller.getPos()));
            checkpoint.add("legacyPos", coordinates(legacyModule.getPos()));
            checkpoint.add("legacyInputPos", coordinates(legacyInput));
            checkpoint.add("ordinaryPos", coordinates(ordinaryController.getPos()));
            checkpoint.add("detachedPos", coordinates(detached.getPos()));
            checkpoint.add("detachedEmptyPos", coordinates(detachedEmpty.getPos()));
            checkpoint.add("paidBufferPos", coordinates(paidBuffer.getPos()));
            checkpoint.addProperty("paidOrders", IvBuffers.state(paidBuffer).save().toString());
            checkpoint.add("moduleState", moduleState());
            var savedNames = new JsonArray();
            var namedBuffers = new ArrayList<>(buffers(controller));
            namedBuffers.addAll(buffers(ordinaryController));
            namedBuffers.add(detached);
            namedBuffers.add(detachedEmpty);
            for (var buffer : namedBuffers) {
                var names = (SuperBufferNameAccess) (Object) buffer;
                var saved = new JsonObject();
                saved.add("pos", coordinates(buffer.getPos()));
                saved.addProperty("stored", buffer.getCustomName());
                saved.addProperty("automatic", names.enhanced$getAutomaticName());
                saved.addProperty("manual", names.enhanced$isManualName());
                savedNames.add(saved);
                buffer.markDirty();
            }
            checkpoint.add("names", savedNames);
            writeField(SuprachronalAssemblyLineModuleMachine.class, legacyModule, "host", null);
            writeField(SuprachronalAssemblyLineModuleMachine.class, legacyModule, "hostPosition", controller.getPos());
            legacyModule.markDirty();
            world.getBlockEntity(legacyModule.getPos()).setChanged();
            var serialized = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE,
                    world.getBlockEntity(legacyModule.getPos()).saveWithFullMetadata());
            JsonElement hostTag = findHostTag(serialized);
            require(!hostTag.isJsonNull(), "The real persisted module field did not enter block-entity NBT");
            checkpoint.add("legacySavedHostPosition", hostTag);
            require(server.saveEverything(true, true, true), "The real fresh world could not be flushed");
            checkpoint.addProperty("checks", checks);
            writeJson(checkpointPath, checkpoint);
            writeJson(resultPath, checkpoint);
            finished = true;
            GTLEnhancedcore.LOGGER.info("{} AWAIT_RESTART runId={} phase=fresh checks={}", MARKER, runId, checks);
            server.halt(false);
        }

        private void finishRestart() throws Exception {
            require(TagParser.parseTag(checkpoint.get("paidOrders").getAsString()).equals(IvBuffers.state(paidBuffer).save()),
                    "The final Jade/module reads changed the disk-restored order ledger");
            require(checkpoint.getAsJsonObject("moduleState").equals(moduleState()), "The final gates changed restored module material/progress");
            legacyModule.markDirty();
            var result = identity("PASS");
            result.addProperty("independentJvmRestart", true);
            result.addProperty("freshJvmBootId", checkpoint.get("jvmBootId").getAsString());
            result.add("legacyPos", checkpoint.get("legacyPos").deepCopy());
            result.add("detachedHostPosition", findHostTag(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE,
                    world.getBlockEntity(legacyModule.getPos()).saveWithFullMetadata())));
            require(server.saveEverything(true, true, true), "The real restarted world could not be flushed");
            result.addProperty("checks", checks);
            writeJson(resultPath, result);
            finished = true;
            GTLEnhancedcore.LOGGER.info("{} PASS runId={} phase=restart checks={}", MARKER, runId, checks);
            server.halt(false);
        }

        private void fail(Throwable failure) throws Exception {
            finished = true;
            var result = identity("FAIL");
            result.addProperty("failureType", failure.getClass().getName());
            result.addProperty("failure", String.valueOf(failure.getMessage()));
            if (!Files.exists(resultPath)) writeJson(resultPath, result);
            GTLEnhancedcore.LOGGER.error("{} FAIL runId={} phase={}", MARKER, runId, phase, failure);
            server.halt(false);
        }

        private void require(boolean passed, String message) {
            checks++;
            if (!passed) throw new AssertionError(message);
        }
    }

    private static com.gregtechceu.gtceu.api.pattern.BlockPattern nativePattern(MultiblockMachineDefinition definition, boolean multiple) {
        var casing = Predicates.blocks(block("gtceu:solid_machine_casing"));
        if (multiple) casing = casing.or(Predicates.blocks(block("gtladditions:me_super_pattern_buffer")));
        var pattern = FactoryBlockPattern.start().aisle("CCC", "CSC", "CBC").aisle("CCC", "CKC", "CMC")
                .aisle("CCC", "CEC", "CPC")
                .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                .where('C', casing).where('B', Predicates.blocks(block("gtladditions:me_super_pattern_buffer")))
                .where('E', Predicates.blocks(block("gtceu:zpm_energy_input_hatch")))
                .where('P', Predicates.blocks(block("gtceu:luv_parallel_hatch")))
                .where('M', Predicates.blocks(block("gtceu:auto_maintenance_hatch")))
                .where('K', Predicates.heatingCoils()).build();
        ((MultiblockMachineDefinitionAccessor) definition).gtlEnhancedcore$setPatternFactory(() -> pattern);
        return pattern;
    }

    private static com.gregtechceu.gtceu.api.pattern.BlockPattern modulePattern(MultiblockMachineDefinition definition) {
        var pattern = FactoryBlockPattern.start().aisle("CCC", "CSC", "CIC").aisle("CCC", "CCC", "COC")
                .aisle("CCC", "CEC", "CFC").where('S', Predicates.controller(Predicates.blocks(definition.get())))
                .where('C', Predicates.blocks(block("gtceu:solid_machine_casing")))
                .where('I', Predicates.blocks(block("gtceu:iv_input_bus")))
                .where('O', Predicates.blocks(block("gtceu:iv_output_bus")))
                .where('F', Predicates.blocks(block("gtceu:iv_input_hatch")))
                .where('E', Predicates.blocks(block("gtceu:zpm_energy_input_hatch"))).build();
        ((MultiblockMachineDefinitionAccessor) definition).gtlEnhancedcore$setPatternFactory(() -> pattern);
        return pattern;
    }

    private static MultiblockMachineDefinition definition(String id) {
        return (MultiblockMachineDefinition) Objects.requireNonNull(GTRegistries.MACHINES.get(new ResourceLocation(id)), "Missing registered machine " + id);
    }

    private static Block block(String id) {
        var key = new ResourceLocation(id);
        if (!ForgeRegistries.BLOCKS.containsKey(key)) throw new IllegalStateException("Missing registered block " + id);
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(key));
    }

    private static List<MESuperPatternBufferPartMachine> buffers(WorkableElectricMultiblockMachine controller) {
        return controller.getParts().stream().filter(MESuperPatternBufferPartMachine.class::isInstance)
                .map(MESuperPatternBufferPartMachine.class::cast)
                .sorted(Comparator.comparingLong(buffer -> SuperBufferAutoName.positionKey(buffer.getPos().getX(),
                        buffer.getPos().getY(), buffer.getPos().getZ()))).toList();
    }

    private static JsonArray coordinates(BlockPos position) {
        var result = new JsonArray();
        result.add(position.getX()); result.add(position.getY()); result.add(position.getZ());
        return result;
    }

    private static BlockPos position(JsonArray coordinates) {
        return new BlockPos(coordinates.get(0).getAsInt(), coordinates.get(1).getAsInt(), coordinates.get(2).getAsInt());
    }

    private static List<Component> tips(MultiblockMachineDefinition definition) {
        var result = new ArrayList<Component>();
        definition.getTooltipBuilder().accept(definition.asStack(), result);
        return result;
    }

    private static int count(List<Component> rows, String key) {
        return (int) rows.stream().filter(row -> MachineTooltips.containsKey(row, key)).count();
    }

    private static void writeField(Class<?> owner, Object instance, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static JsonElement findHostTag(JsonElement node) {
        if (node.isJsonObject()) {
            if (node.getAsJsonObject().has("hostPosition")) return node.getAsJsonObject().get("hostPosition").deepCopy();
            for (var entry : node.getAsJsonObject().entrySet()) {
                JsonElement found = findHostTag(entry.getValue());
                if (!found.isJsonNull()) return found;
            }
        } else if (node.isJsonArray()) {
            for (var entry : node.getAsJsonArray()) {
                JsonElement found = findHostTag(entry);
                if (!found.isJsonNull()) return found;
            }
        }
        return JsonNull.INSTANCE;
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void writeJson(Path path, JsonObject value) throws Exception {
        if (Files.exists(path)) throw new IllegalStateException("Evidence already exists: " + path.getFileName());
        Path temporary = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Files.writeString(temporary, JSON.toJson(value) + "\n", StandardCharsets.UTF_8);
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String sha256(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            var bytes = new byte[65536];
            int count;
            while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
