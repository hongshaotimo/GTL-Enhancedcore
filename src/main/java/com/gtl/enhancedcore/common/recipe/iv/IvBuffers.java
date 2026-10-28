package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.crafting.*;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gtl.enhancedcore.common.machine.TieredParallelMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gtl.enhancedcore.mixin.gtceu.MultiblockPartControllerPositionsAccessor;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;

/** Runtime guards are deliberately instance-local; no global current-controller or recipe cache. */
public final class IvBuffers {
    private static final Set<String> CONTROLLERS = Set.of("gtl_enhancedcore:plasma_machine_tool", "gtl_enhancedcore:hadron_catalytic_refinery",
            "gtl_enhancedcore:quantum_mass_spectrum_array", "gtl_enhancedcore:superconducting_fusion_assembler",
            "gtl_enhancedcore:universal_joint_factory", "gtl_enhancedcore:hydrothermal_leaching_complex");
    public static final String SAVE_KEY = "gtl_enhancedcore_iv_tasks";
    public static final String ROUTE_KEY = "gtl_enhancedcore_iv_route";
    private static final int MAX_QUEUED_DISPATCHES = 4096; // Does not limit the long amount in a dispatch.
    private IvBuffers() {}

    public static IvBufferState state(Object buffer) {
        return IvBufferRegistry.compatible(buffer) ? ((IvBufferAccess)buffer).iv$getState() : null;
    }
    public static boolean compatible(Object buffer) { return IvBufferRegistry.compatible(buffer); }
    public static List<MEPatternBufferPartMachine> collect(com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine machine) {
        return machine.getParts().stream().filter(IvBuffers::compatible)
                .filter(part -> !genericCloudTarget(machine) || part instanceof CloudPatternBufferMachine)
                .map(part -> (MEPatternBufferPartMachine)part).toList();
    }
    public static boolean legacyTargetController(Object controller) {
        return controller instanceof TieredParallelMachine machine && CONTROLLERS.contains(machine.getDefinition().getId().toString())
                || IvMachineScope.nativeTarget(controller);
    }
    /** Cloud adapters opt in by their actual attached part, never by broadening the super-buffer whitelist. */
    public static boolean genericCloudTarget(Object controller) {
        return controller instanceof WorkableElectricMultiblockMachine machine && !legacyTargetController(controller)
                && machine.getParts().stream().anyMatch(CloudPatternBufferMachine.class::isInstance);
    }
    /** Ordinary/native inputs keep their own execution path; never silently take their stock over. */
    public static boolean genericCloudMixedInputs(WorkableElectricMultiblockMachine machine) {
        return genericCloudTarget(machine) && machine.getParts().stream().anyMatch(part -> {
            if (part instanceof CloudPatternBufferMachine) return false;
            var block = part.self().getBlockState().getBlock();
            if (com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch.isConnectionPortBlock(block)) return false;
            return com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS.isApplicable(block)
                    || com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_FLUIDS.isApplicable(block);
        });
    }
    public static boolean targetController(Object controller) {
        return legacyTargetController(controller) || genericCloudTarget(controller);
    }
    public static boolean isolated(MEPatternBufferPartMachineBase buffer) {
        if (buffer instanceof CloudPatternBufferMachine) return true;
        IvBufferState state = state(buffer);
        if (state == null) return false;
        if(buffer.isRemote() && ((IvBufferAccess)buffer).iv$isDedicatedDisplay())return true;
        if (state.dedicated()) return true;
        return loadedControllers(buffer).stream().anyMatch(IvBuffers::legacyTargetController);
    }
    /** AE may query patterns while chunks are unloading; never load a controller chunk here. */
    private static List<IMultiController> loadedControllers(MEPatternBufferPartMachineBase buffer) {
        if (!(buffer.getLevel() instanceof ServerLevel level)) return buffer.getControllers();
        List<IMultiController> controllers = new ArrayList<>();
        for (var pos : ((MultiblockPartControllerPositionsAccessor) buffer).enhancedcore$getControllerPositions()) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(pos) instanceof IMachineBlockEntity holder
                    && holder.getMetaMachine() instanceof IMultiController controller) controllers.add(controller);
        }
        return controllers;
    }
    private static boolean controllersLoaded(MEPatternBufferPartMachineBase buffer) {
        if (!(buffer.getLevel() instanceof ServerLevel level)) return true;
        return ((MultiblockPartControllerPositionsAccessor) buffer).enhancedcore$getControllerPositions().stream()
                .allMatch(pos -> level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null);
    }
    public static String ownerKey(WorkableElectricMultiblockMachine machine) {
        return machine.getLevel().dimension().location() + "/" + machine.getPos().asLong() + "/" + machine.getDefinition().getId();
    }
    /** Several executors share a lease view, but each accepted order has exactly one executor. */
    public static List<WorkableElectricMultiblockMachine> cloudControllers(CloudPatternBufferMachine buffer) {
        return loadedControllers(buffer).stream().filter(WorkableElectricMultiblockMachine.class::isInstance)
                .map(WorkableElectricMultiblockMachine.class::cast).filter(machine -> machine.isFormed() && targetController(machine))
                .filter(machine -> buffer.cloudMode() == null || Arrays.stream(machine.getDefinition().getRecipeTypes())
                        .anyMatch(type -> buffer.cloudAcceptsMode(type.registryName)))
                .sorted(Comparator.comparing(IvBuffers::ownerKey)).toList();
    }
    public static List<IvJob> jobs(MEPatternBufferPartMachine buffer, WorkableElectricMultiblockMachine machine) {
        var state = state(buffer);
        if (state == null) return List.of();
        if (!(buffer instanceof CloudPatternBufferMachine cloud)) return state.jobs;
        UUID instance = ((com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity)machine).cloud$machineInstance(false);
        return state.jobs.stream().filter(job -> com.gtl.enhancedcore.common.cloud.CloudEndpointPolicy.ownsJob(
                job.cloudMachine, instance, cloud.cloudNode() == null ? null : cloud.cloudNode().machineInstance(),
                state.owner.equals(ownerKey(machine)))).toList();
    }
    public static WorkableElectricMultiblockMachine controller(MEPatternBufferPartMachineBase buffer) {
        if (buffer instanceof CloudPatternBufferMachine cloud) {
            return cloudControllers(cloud).stream().filter(machine -> !genericCloudMixedInputs(machine))
                    .min(Comparator.comparingInt((WorkableElectricMultiblockMachine machine) -> jobs(cloud, machine).size())
                            .thenComparing(IvBuffers::ownerKey)).orElse(null);
        }
        if (!controllersLoaded(buffer)) return null;
        var controllers = loadedControllers(buffer);
        if (controllers.size() != 1 || !(buffer instanceof CloudPatternBufferMachine ? targetController(controllers.get(0)) : legacyTargetController(controllers.get(0)))
                || !(controllers.get(0) instanceof WorkableElectricMultiblockMachine machine) || !machine.isFormed()) return null;
        if (!buffer.getBoundProxyPositions().isEmpty()) return null;
        return machine;
    }
    public static boolean bind(MEPatternBufferPartMachine buffer, WorkableElectricMultiblockMachine machine) {
        IvBufferState state = state(buffer);
        if (state == null || !state.healthy()) return false;
        if (buffer instanceof com.gtl.enhancedcore.common.cloud.transfer.CloudImportSourceAccess source && source.cloud$importQuarantined())
            return rejectBind(buffer, "gtl_enhancedcore.cloud.status.review");
        if (buffer instanceof CloudPatternBufferMachine cloud && !cloud.cloudReady(true))
            return rejectBind(buffer, "gtl_enhancedcore.cloud.status.origin");
        if (((IvBufferAccess)buffer).iv$isFoaEnabled()) return rejectBind(buffer, "gtl_enhancedcore.diagnostic.iv_foa");
        if (buffer instanceof CloudPatternBufferMachine cloud) {
            if (!cloudControllers(cloud).contains(machine) || genericCloudMixedInputs(machine))
                return rejectBind(buffer, "gtl_enhancedcore.diagnostic.iv_exclusive");
            ((IvBufferAccess)buffer).iv$syncDedicated(true);
            refresh(buffer); return true;
        }
        if (controller(buffer) != machine) return rejectBind(buffer, "gtl_enhancedcore.diagnostic.iv_exclusive");
        String key = ownerKey(machine);
        if (!state.owner.isEmpty() && !state.owner.equals(key)) return rejectBind(buffer, "gtl_enhancedcore.diagnostic.iv_owner");
        if (state.owner.isEmpty()) {
            Object[] slots = buffer.getInternalInventory();
            for (Object slot : slots) if (((IvSlotAccess)slot).iv$hasStock()) {
                return rejectBind(buffer, "gtl_enhancedcore.diagnostic.iv_stock");
            }
            if (!buffer.getBuffer().isEmpty()) return rejectBind(buffer, "gtl_enhancedcore.diagnostic.iv_old_output");
            state.owner = key; state.refreshNeeded = true; buffer.markDirty();
            IvTaskLog.event(buffer, null, "BIND", "OK", "threads", IvMachineScope.threads(machine), "parallel", IvMachineScope.parallel(machine));
        }
        ((IvBufferAccess)(Object)buffer).iv$syncDedicated(true);
        refresh(buffer); return true;
    }
    /** Retain the published signature for integrations compiled before the compatibility API. */
    public static boolean bind(com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine buffer,
                               WorkableElectricMultiblockMachine machine) {
        return bind((MEPatternBufferPartMachine) buffer, machine);
    }
    private static boolean rejectBind(MEPatternBufferPartMachineBase buffer, String reason) {
        IvBufferState state = state(buffer);
        if (!reason.equals(state.message)) IvTaskLog.event(buffer, null, "BIND", "WAIT", "reason", reason,
                "controllers", loadedControllers(buffer).stream().map(c -> c.self().getDefinition().getId() + "@" + c.self().getPos().toShortString()).toList(),
                "proxies", buffer.getBoundProxyPositions().stream().map(Object::toString).toList());
        state.message = reason; return false;
    }
    public static void refresh(MEPatternBufferPartMachineBase buffer) {
        IvBufferState state = state(buffer);
        if (buffer instanceof CloudPatternBufferMachine cloud) {
            if (state != null && state.refreshNeeded) { state.refreshNeeded = false; cloud.refreshPublished(); }
            return;
        }
        if (!buffer.isRemote() && state != null && state.dedicated() && state.healthy() && state.jobs.isEmpty()
                && controllersLoaded(buffer) && loadedControllers(buffer).stream().noneMatch(IvBuffers::targetController)) {
            state.owner = ""; state.refreshNeeded = true; buffer.markDirty();
        }
        if (state != null && state.refreshNeeded && buffer instanceof IvBufferMethods methods) {
            state.refreshNeeded = false; methods.iv$refreshPatterns();
            ICraftingProvider.requestUpdate(buffer.getMainNode());
        }
    }
    public static IPatternDetails routedPattern(MEPatternBufferPartMachine buffer, int slot, ItemStack original, IPatternDetails processed) {
        if (processed == null || !isolated(buffer)) return processed;
        IvBufferState state = state(buffer);
        if (state == null || !state.dedicated() || !state.healthy()) return null;
        ItemStack definition = processed.getDefinition().toStack();
        CompoundTag route = new CompoundTag(); route.putUUID("buffer", state.identity); route.putInt("slot", slot);
        route.put("original", original.save(new CompoundTag()));
        definition.getOrCreateTag().put(ROUTE_KEY, route);
        return PatternDetailsHelper.decodePattern(definition, buffer.getLevel());
    }
    public static Map<AEKey, Long> virtual(MEPatternBufferPartMachine buffer, int slot) {
        Map<AEKey, Long> result = slotVirtual(buffer, slot);
        sharedCatalysts(buffer).forEach((key, amount) -> addCatalyst(result, key, amount));
        if (buffer instanceof CloudPatternBufferMachine cloud)
            cloud.cloudPatternCatalysts(slot).forEach((key, amount) -> addCatalyst(result, key, amount));
        return result;
    }
    private static Map<AEKey, Long> slotVirtual(MEPatternBufferPartMachine buffer, int slot) {
        if (buffer instanceof CloudPatternBufferMachine cloud) return new LinkedHashMap<>(cloud.cloudSlotVirtual(slot));
        Object[] slots = buffer.getInternalInventory();
        Map<AEKey, Long> result = new LinkedHashMap<>(((IvSlotAccess)slots[slot]).iv$virtualStock());
        ItemStack circuit = buffer.getCircuitForRecipe(slot);
        if (!circuit.isEmpty()) result.put(AEItemKey.of(circuit), 1L);
        return result;
    }
    /** Physical catalysts remain in this buffer, never in a task's saved inventory or virtual tokens. */
    public static Map<AEKey, Long> sharedCatalysts(MEPatternBufferPartMachine buffer) {
        if (buffer instanceof CloudPatternBufferMachine cloud) return cloud.cloudCatalysts();
        Map<AEKey, Long> result = new LinkedHashMap<>();
        if (!isolated(buffer)) return result;
        var items = buffer.getSharedCatalystInventory();
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) addCatalyst(result, AEItemKey.of(stack), stack.getCount());
        }
        var fluids = buffer.getSharedCatalystTank();
        for (int i = 0; i < fluids.getTanks(); i++) {
            var stack = fluids.getFluidInTank(i);
            if (!stack.isEmpty()) addCatalyst(result, IvRecipeInputs.fluidKey(stack), stack.getAmount());
        }
        return result;
    }
    private static void addCatalyst(Map<AEKey, Long> target, AEKey key, long amount) {
        if (amount > 0) target.merge(key, amount, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b);
    }
    public static Map<AEKey, Long> availableVirtual(MEPatternBufferPartMachine buffer, IvJob job) {
        Map<AEKey, Long> result = buffer instanceof CloudPatternBufferMachine ? new LinkedHashMap<>(job.virtual) : slotVirtual(buffer, job.slot);
        result.keySet().removeIf(IvBuffers::isCircuit);
        sharedCatalysts(buffer).forEach((key, amount) -> addCatalyst(result, key, amount));
        if (buffer instanceof CloudPatternBufferMachine cloud)
            cloud.cloudPatternCatalysts(job).forEach((key, amount) -> addCatalyst(result, key, amount));
        return IvPatternAdmission.virtualStock(result, job.virtual, IvBuffers::isCircuit);
    }
    private static Map<AEKey, Long> virtualToken(AEKey key) {
        if (!(key instanceof AEItemKey item) || !GTLItems.VIRTUAL_INGREDIENT.isIn(item.toStack())) return Map.of();
        ItemStack wrapper = item.toStack();
        return IvPatternAdmission.<AEKey>token(VirtualIngredientBehavior.payloadItemKey(wrapper),
                VirtualIngredientBehavior.payloadFluidKey(wrapper), IvBuffers::isCircuit);
    }
    private static Map<AEKey, Long> declaredCircuits(MEPatternBufferPartMachine buffer, ItemStack original) {
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(original, buffer.getLevel());
        if (pattern == null) throw new IllegalArgumentException("原始样板无法解码");
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var input : pattern.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length != 1) throw new IllegalArgumentException("样板输入必须有确定身份");
            AEKey key = possible[0].what();
            if (isCircuit(key)) result.put(key, 1L);
            virtualToken(key).forEach((payload, amount) -> { if (isCircuit(payload)) result.put(payload, 1L); });
        }
        return result;
    }
    public static boolean push(MEPatternBufferPartMachine buffer, IPatternDetails details, KeyCounter[] holders) {
        IvBufferState state = state(buffer);
        if (state == null) return false;
        WorkableElectricMultiblockMachine machine = controller(buffer);
        String request = UUID.randomUUID().toString();
        IvTaskLog.event(buffer, null, "RECEIVE", "BEGIN", "request", request, "inputGroups", holders.length);
        if (machine == null) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_exclusive");
        if (!bind(buffer, machine)) return reject(buffer, request, state.message);
        if (!state.accepting) return reject(buffer,request,"gtl_enhancedcore.diagnostic.iv_off");
        if (!buffer.getMainNode().isActive()) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_ae");
        if (state.jobs.size() >= MAX_QUEUED_DISPATCHES) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_queue");
        try {
            CompoundTag definition = details.getDefinition().getTag();
            if (definition == null || !definition.contains(ROUTE_KEY)) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_route");
            CompoundTag route = definition.getCompound(ROUTE_KEY);
            if (buffer instanceof CloudPatternBufferMachine cloud && !cloud.cloudRouteValid(route))
                return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_changed");
            int slot = route.getInt("slot");
            if (!route.hasUUID("buffer") || !state.identity.equals(route.getUUID("buffer")) || slot < 0 || slot >= buffer.getPatternInventory().getSlots()) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_changed");
            ItemStack original = buffer.getPatternInventory().getStackInSlot(slot);
            if (!route.getCompound("original").equals(original.save(new CompoundTag()))) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_changed");
            IPatternDetails current = buffer instanceof CloudPatternBufferMachine cloud ? cloud.cloudRealPattern(slot, original)
                    : ((IvBufferMethods)(Object)buffer).iv$realPattern(slot, original);
            if (current == null || !current.equals(details)) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_changed");
            Map<AEKey, Long> received = new LinkedHashMap<>();
            for (KeyCounter holder : holders) for (var entry : holder) {
                if (entry.getLongValue() <= 0) throw new IllegalArgumentException("投料数量无效");
                received.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
            Map<AEKey, Long> unit = new LinkedHashMap<>();
            for (var input : details.getInputs()) {
                GenericStack[] possible = input.getPossibleInputs();
                if (possible.length != 1) throw new IllegalArgumentException("样板输入必须有确定身份");
                unit.merge(possible[0].what(), input.getMultiplier(), Math::addExact);
            }
            long multiplier = IvDispatchContext.operations(buffer, details);
            if (buffer instanceof CloudPatternBufferMachine
                    && org.gtlcore.gtlcore.integration.ae2.graph.GraphDispatchContext.selectedInputs() != null)
                multiplier = org.gtlcore.gtlcore.integration.ae2.graph.GraphDispatchContext.operations();
            for (var input : unit.entrySet()) {
                if (isCircuit(input.getKey())) continue;
                // Graph sends selector/configuration tokens once per dispatch, independently of its batch size.
                if (buffer instanceof CloudPatternBufferMachine && input.getKey() instanceof AEItemKey item
                        && GTLItems.VIRTUAL_INGREDIENT.isIn(item.toStack())) continue;
                long count = received.getOrDefault(input.getKey(), 0L);
                if (count <= 0 || count % input.getValue() != 0) throw new IllegalArgumentException("投料与样板倍率不一致");
                long ratio = count / input.getValue();
                if (multiplier >= 0 && multiplier != ratio) throw new IllegalArgumentException("整笔物品/流体倍率不一致");
                multiplier = ratio;
            }
            if (multiplier < 1) throw new IllegalArgumentException("未获得无消耗样板的智能翻倍订单数量");
            for (AEKey key : received.keySet()) if (!unit.containsKey(key)) throw new IllegalArgumentException("样板外的投料");
            IvTaskLog.event(buffer, null, "VALIDATE_DISPATCH", "OK", "request", request, "slot", slot, "multiplier", multiplier, "received", IvTaskLog.stock(received));
            Map<AEKey, Long> intrinsicVirtual = IvPatternAdmission.circuits(slotVirtual(buffer, slot),
                    declaredCircuits(buffer, original), IvBuffers::isCircuit);
            if (buffer instanceof CloudPatternBufferMachine cloud)
                cloud.cloudSlotVirtual(slot).forEach((key, amount) -> { if (!isCircuit(key)) intrinsicVirtual.put(key, amount); });
            // GTL virtual ingredient tokens belong to this dispatch, not the old slot's cumulative inventory.
            for (AEKey key : new ArrayList<>(received.keySet())) if (key instanceof AEItemKey item && GTLItems.VIRTUAL_INGREDIENT.isIn(item.toStack())) {
                intrinsicVirtual.putAll(virtualToken(key));
                received.remove(key); unit.remove(key);
            }
            Map<AEKey, Long> virtual = IvPatternAdmission.virtualStock(virtual(buffer, slot), intrinsicVirtual, IvBuffers::isCircuit);
            Set<ResourceLocation> types = PatternQuickUploadMetadata.readRecipeTypeIds(original);
            Map<AEKey, Long> expected = new LinkedHashMap<>();
            for (GenericStack output : details.getOutputs()) expected.merge(output.what(), output.amount(), Math::addExact);
            List<GTRecipe> matches = new ArrayList<>(); Map<GTRecipe, Long> scales = new IdentityHashMap<>();
            for (var type : machine.getDefinition().getRecipeTypes()) {
                if (buffer instanceof CloudPatternBufferMachine cloud && !cloud.cloudAcceptsMode(type.registryName)) continue;
                if (!IvPatternAdmission.allowsType(types, type.registryName)) continue;
                for (GTRecipe recipe : com.gtl.enhancedcore.common.cloud.CloudPatternMatching.recipes(machine.getLevel(), type)) {
                    if (!supported(recipe, machine)) continue;
                    long scale = IvRecipeInputs.patternOperations(recipe, unit, virtual, expected);
                    if (scale > 0) { matches.add(recipe); scales.put(recipe, scale); }
                }
            }
            matches.sort(Comparator.comparing(recipe -> recipe.id.toString()));
            IvTaskLog.event(buffer, null, "MATCH_RECIPE", matches.isEmpty() ? "MISS" : "OK", "request", request,
                    "types", types.stream().map(Object::toString).toList(), "matches", matches.stream().map(r -> r.id.toString()).toList(), "virtual", IvTaskLog.stock(virtual),
                    "sharedCatalysts", IvTaskLog.stock(sharedCatalysts(buffer)), "patternInputs", IvTaskLog.stock(unit),
                    "declaredOutputs", IvTaskLog.stock(expected), "inputOperations", matches.stream().map(r -> r.id + "=" + scales.get(r)).toList());
            if (matches.isEmpty()) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_match");
            if (matches.size() > 1) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_ambiguous");
            GTRecipe recipe = matches.get(0);
            long operations = Math.multiplyExact(scales.get(recipe), multiplier);
            IvTaskLog.event(buffer, null, "SIZE_ORDER", "OK", "request", request, "recipe", recipe.id.toString(),
                    "basis", IvRecipeInputs.hasConsumedInputs(recipe) ? "PHYSICAL_INPUTS" : "INPUT_FREE_OUTPUT_RATIO",
                    "operationsPerPattern", scales.get(recipe), "dispatchMultiplier", multiplier, "operations", operations,
                    "declaredOutputs", IvTaskLog.stock(expected));
            var acceptedJobs = new ArrayList<IvJob>();
            if (buffer instanceof CloudPatternBufferMachine cloud) {
                var eligible = cloudControllers(cloud).stream().filter(peer -> !genericCloudMixedInputs(peer))
                        .filter(peer -> Arrays.stream(peer.getDefinition().getRecipeTypes())
                                .anyMatch(type -> type.registryName.equals(recipe.recipeType.registryName)))
                        .filter(peer -> supported(recipe, peer)).toList();
                var executors=com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.recipients(
                        eligible,MAX_QUEUED_DISPATCHES-state.jobs.size(),state.cloudDispatchCursor);
                if (executors.isEmpty()) return reject(buffer, request, "gtl_enhancedcore.diagnostic.iv_queue");
                long[] shares = com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.shares(
                        multiplier, executors.size(), 0);
                var stocks = com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.stock(received, multiplier, shares);
                for(int i=0;i<executors.size();i++)if(shares[i]>0){
                    var part = new IvJob(slot, recipe.copy(), original.save(new CompoundTag()), stocks.get(i),
                            intrinsicVirtual, Math.multiplyExact(scales.get(recipe), shares[i]));
                    part.cloudPattern=cloud.entry(slot).id();
                    part.cloudMachine=((com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity)executors.get(i)).cloud$machineInstance(true);
                    acceptedJobs.add(part);
                }
            } else acceptedJobs.add(new IvJob(slot, recipe.copy(), original.save(new CompoundTag()), received, intrinsicVirtual, operations));
            for(var part:acceptedJobs)part.save(); // Validate every partition before accepting anything from AE.
            var acceptedStock = IvTaskLog.stock(received);
            if (buffer instanceof CloudPatternBufferMachine cloud && !cloud.cloudCaptureOrigin())
                return reject(buffer, request, "gtl_enhancedcore.cloud.status.origin");
            buffer.markDirty();
            state.jobs.addAll(acceptedJobs); state.message = "";
            state.cloudDispatchCursor = state.cloudDispatchCursor == Integer.MAX_VALUE ? 0 : state.cloudDispatchCursor+1;
            // Nothing after the commit may turn this acknowledgement into a failed AE dispatch.
            return com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.acknowledged(() ->
                    IvTaskLog.event(buffer, acceptedJobs.getFirst(), "ACCEPT", "OK", "request", request, "operations", operations,
                            "partitions",acceptedJobs.size(), "queueSize", state.jobs.size(), "input", acceptedStock));
        } catch (RuntimeException failure) {
            state.message = "gtl_enhancedcore.diagnostic.iv_receive"; IvTaskLog.error(buffer, null, "RECEIVE", failure); return false;
        }
    }
    /** Retain the published signature for integrations compiled before the compatibility API. */
    public static boolean push(com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine buffer,
                               IPatternDetails details, KeyCounter[] holders) {
        return push((MEPatternBufferPartMachine) buffer, details, holders);
    }
    private static boolean reject(MEPatternBufferPartMachineBase buffer, String request, String reason) {
        IvBuffers.state(buffer).message = reason;
        IvTaskLog.event(buffer, null, "REJECT", "NO_INPUT_ACCEPTED", "request", request, "reason", reason);
        return false;
    }
    public static boolean isCircuit(AEKey key) { return key instanceof AEItemKey item && IntCircuitBehaviour.isIntegratedCircuit(item.toStack()); }
    /** Recheck at the actual external transfer boundary, including cancellation without a controller. */
    public static boolean mayUseNetwork(MEPatternBufferPartMachineBase buffer) {
        return !(buffer instanceof CloudPatternBufferMachine cloud) || cloud.cloudMayUseNetwork();
    }
    private static boolean supported(GTRecipe recipe, WorkableElectricMultiblockMachine machine) {
        // Unknown capabilities/actions must fail before accepting, rather than silently losing semantics.
        return recipe.ingredientActions.isEmpty() && recipe.tickOutputs.isEmpty()
                && recipe.inputs.keySet().stream().allMatch(cap -> cap == com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP
                    || cap == com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP)
                && recipe.outputs.keySet().stream().allMatch(cap -> cap == com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP
                    || cap == com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP)
                && recipe.tickInputs.keySet().stream().allMatch(cap -> cap == EURecipeCapability.CAP
                    || (IvMachineScope.nativeTarget(machine) || genericCloudTarget(machine))
                    && cap == com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability.CAP);
    }
}
