package com.gtl.enhancedcore.common.cloud;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.IGrid;
import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gtl.enhancedcore.common.cloud.store.*;
import com.gtl.enhancedcore.common.cloud.transfer.CloudAeOriginGuard;
import com.gtl.enhancedcore.common.cloud.ui.*;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;

/** Sparse, read-only leases. Physical originals and task stock never live in block drops. */
public class CloudPatternBufferMachine extends MEPatternBufferPartMachine implements IvBufferAccess, CloudPatternUiHost {
    private static final ManagedFieldHolder FIELDS = new ManagedFieldHolder(CloudPatternBufferMachine.class, MEPatternBufferPartMachine.MANAGED_FIELD_HOLDER);
    private UUID owner, libraryId, nodeId;
    private boolean manualBinding;
    private IGrid validatedLibraryGrid;
    private String boundDefinition = "";
    private long placementOrder;
    private CloudPatternStore.Node node;
    private IvBufferState tasks = new IvBufferState();
    private final CloudAeOriginGuard origin = new CloudAeOriginGuard();
    private boolean attached, removed, displayDedicated, faulted;
    private TickableSubscription cloudTick;
    private final Map<Integer, IPatternDetails> published = new TreeMap<>();
    private List<IPatternDetails> publishedSnapshot = List.of();
    private final Map<Integer, ItemStack> circuits = new HashMap<>();
    private final List<UUID> leaseOrder = new ArrayList<>();
    private record LeaseDisplay(String name, String normalizedName, String nameJson, List<AEKey> outputs) {}
    private final Map<UUID, LeaseDisplay> leaseDisplays = new HashMap<>();
    private record Query(String text, CloudPatternSearch localized) {}
    private final LinkedHashMap<Query, List<UUID>> searches = new LinkedHashMap<>(8, .75f, true);
    private boolean searchScopeDirty = true;
    private Map<Integer, UUID> uiSlotSnapshot;
    private Set<UUID> uiInvalidSnapshot = Set.of();
    private List<UUID> uiEntrySnapshot = List.of();
    private Map<UUID, LeaseDisplay> uiDisplaySnapshot = Map.of();
    private long[] searchItems = new long[0], searchFluids = new long[0];
    private final Set<UUID> invalidLeases = new HashSet<>();
    private final Set<UUID> validatedLeases = new HashSet<>();
    private int slotExtent;
    private long uiRevision, lastLibraryRevision = -1;
    private boolean publicationDirty;
    private boolean allocationEligible;
    private long allocationEpoch;
    private UUID publishedTaskIdentity;
    private long publishedGeneration = Long.MIN_VALUE;
    private boolean publishedKeepByProduct;
    @DescSynced private String modeLabel = "";
    @DescSynced private String statusLabel = "gtl_enhancedcore.cloud.status.unbound";
    @DescSynced private int matched;
    @DescSynced private boolean connected;
    @DescSynced private boolean meNetworkActive;
    private final ItemStackTransfer leaseView = new ItemStackTransfer(0) {
        @Override public int getSlots() { return slotExtent; }
        @Override public ItemStack getStackInSlot(int slot) {
            var entry = entry(slot); return entry == null ? ItemStack.EMPTY : entry.original();
        }
        @Override public void setStackInSlot(int slot, ItemStack stack) {}
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notify) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notify) { return ItemStack.EMPTY; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        @Override public CompoundTag serializeNBT() { return new CompoundTag(); }
        @Override public void deserializeNBT(CompoundTag tag) {}
    };
    private final InternalInventory terminalView = new InternalInventory() {
        @Override public int size() { return slotExtent; }
        @Override public ItemStack getStackInSlot(int slot) { return leaseView.getStackInSlot(slot); }
        @Override public void setItemDirect(int slot, ItemStack stack) {}
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    };

    public CloudPatternBufferMachine(IMachineBlockEntity holder) { super(holder, 0, IO.IN); }
    @Override public ManagedFieldHolder getFieldHolder() { return FIELDS; }
    @Override public IvBufferState iv$getState() { return tasks; }
    @Override public boolean iv$isDedicatedDisplay() { return displayDedicated || isRemote(); }
    @Override public void iv$syncDedicated(boolean value) { displayDedicated = value; }
    public UUID cloudLibraryId() { return libraryId; }
    public UUID cloudNodeId() { return nodeId; }
    public String cloudModeLabel() { return modeLabel; }
    public int cloudMatchedCount() { return matched; }
    public String cloudStatusLabel() { return statusLabel; }
    @Override public boolean cloudUiConnected() { return connected; }
    public boolean meNetworkConnected() { return isRemote() ? meNetworkActive : getMainNode().isActive(); }
    public boolean mayConfigureMeNetwork(ServerPlayer player) { return authorized(player, true); }
    public CloudPatternStore cloudStore() { return CloudPatternStore.get((ServerLevel)getLevel()); }
    public CloudPatternStore.Node cloudNode() { return attached ? node : null; }
    public ResourceLocation cloudMode() { return node == null ? null : node.mode(); }
    @Override public ItemStackTransfer getPatternInventory() { return leaseView; }
    @Override public InternalInventory getTerminalPatternInventory() { return terminalView; }
    // The upstream pattern terminal assumes finite, writable slots. The dedicated cloud UI is authoritative.
    @Override public boolean isVisibleInTerminal() { return false; }
    @Override public boolean canShared() { return true; }
    @Override public void addProxy(MEPatternBufferProxyPartMachine proxy) {}
    @Override public java.util.Set<MEPatternBufferProxyPartMachine> getProxies() { return java.util.Set.of(); }
    @Override public java.util.List<BlockPos> getBoundProxyPositions() { return java.util.List.of(); }
    @Override public List<IPatternDetails> getAvailablePatterns() {
        return cloudReady(true) && tasks.accepting ? publishedSnapshot : List.of();
    }
    @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) { return IvBuffers.push(this, pattern, inputs); }
    @Override public boolean gtlcore$canProduceGraphPattern(IPatternDetails pattern) {
        return cloudReady(true) && tasks.accepting && publishedSnapshot.contains(pattern) && tasks.jobs.size() < 4096;
    }
    @Override public long gtlcore$graphCapacity(IPatternDetails pattern, Map<AEKey, Long> perOperation, long requested) {
        if (!gtlcore$canProduceGraphPattern(pattern) || requested < 1) return 0;
        return CloudOrderCapacity.limit(cloudDispatchCapacity(pattern, requested), 1, perOperation);
    }
    /** Both native AE CPUs and graph CPUs must clip whole patterns before GTLCore extracts inputs. */
    public long cloudDispatchCapacity(IPatternDetails pattern, long requested) {
        Map<AEKey, Long> inputs = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length != 1 || input.getMultiplier() < 1) return 0;
            if (!IvBuffers.isCircuit(possible[0].what()))
                inputs.merge(possible[0].what(), input.getMultiplier(), Math::addExact);
        }
        for (var output : pattern.getOutputs()) outputs.merge(output.what(), output.amount(), Math::addExact);
        long bounded = CloudOrderCapacity.limit(requested, graphOperationsPerPattern(pattern), inputs);
        return CloudOrderCapacity.limit(bounded, 1, outputs);
    }
    private long graphOperationsPerPattern(IPatternDetails pattern) {
        Integer slot = getSlotIndexForPattern(pattern);
        CloudPatternStore.Entry entry = slot == null ? null : entry(slot);
        if (entry == null) return 0;
        CloudPatternMatching.Probe probe = cloudProbe(entry);
        if (probe == null) return 0;
        var types = org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata.readRecipeTypeIds(entry.original());
        long maximum = 0;
        for (var machine : IvBuffers.cloudControllers(this)) {
            if (IvBuffers.genericCloudMixedInputs(machine)) continue;
            for (var type : machine.getDefinition().getRecipeTypes()) {
                if (!cloudAcceptsMode(type.registryName)
                        || !IvPatternAdmission.allowsType(types, type.registryName)) continue;
                for (var recipe : CloudPatternMatching.candidates(getLevel(), type, probe.outputs())) {
                    if (!CloudPatternMatching.supported(recipe, machine)) continue;
                    maximum = Math.max(maximum, IvRecipeInputs.patternOperations(
                            recipe, probe.inputs(), probe.virtual(), probe.outputs()));
                }
            }
        }
        return maximum;
    }
    @Override protected Integer getSlotIndexForPattern(IPatternDetails pattern) {
        for (var entry : published.entrySet()) if (entry.getValue().equals(pattern)) return entry.getKey();
        return null;
    }
    @Override protected boolean hasPatternInSlot(int slot) { return entry(slot) != null; }
    @Override public ItemStack getCircuitForRecipe(int slot) { return circuits.getOrDefault(slot, ItemStack.EMPTY).copy(); }
    @Override protected void refreshAllByProduct() { refreshPublished(); }
    @Override protected void onPatternChange(int slot) { refreshPublished(); }
    @Override public CompoundTag cutToTag(CompoundTag tag) { return tag; }
    @Override public CompoundTag copyToTag(CompoundTag tag) { return tag; }
    @Override public void copyFromTag(CompoundTag tag, ServerPlayer player) {}
    @Override public boolean pasteFromTag(CompoundTag tag) { return false; }
    @Override public com.lowdragmc.lowdraglib.side.item.IItemTransfer getItemTransferCap(Direction side, boolean useCover) { return null; }
    @Override public com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer getFluidTransferCap(Direction side, boolean useCover) { return null; }

    @Override public void onMachinePlaced(LivingEntity placer, ItemStack stack) {
        super.onMachinePlaced(placer, stack);
        if (!isRemote() && placer instanceof ServerPlayer player) {
            owner = player.getUUID(); placementOrder = getLevel().getGameTime();
            synchronizeLibrary(); attach(); markDirty();
        }
    }
    @Override public void onLoad() {
        super.onLoad(); removed = false;
        if (!isRemote()) cloudTick = subscribeServerTick(this::cloudMaintenance);
    }
    @Override public void onUnload() {
        meNetworkActive = false;
        if (cloudTick != null) { cloudTick.unsubscribe(); cloudTick = null; }
        CloudPatternAllocator.remove(this);
        if (attached) { cloudStore().deactivate(nodeId, this); attached = false; }
        validatedLibraryGrid = null;
        super.onUnload();
    }
    @Override public void onMachineRemoved() {
        meNetworkActive = false;
        if (!isRemote() && !attached && !removed) attach();
        if (!isRemote() && attached && !removed) {
            tasks.accepting = false; markDirty();
            cloudStore().detach(nodeId, UUID.randomUUID()); attached = false; removed = true;
            published.clear(); publishedSnapshot = List.of(); CloudPatternAllocator.remove(this);
            ICraftingProvider.requestUpdate(getMainNode());
        }
        super.onMachineRemoved(); // Parent inventories have size zero and contain no cloud originals.
    }
    @Override public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.remove(IvBuffers.SAVE_KEY);
        if (!forDrop) {
            CompoundTag address = new CompoundTag();
            if (owner != null) address.putUUID("owner", owner);
            if (libraryId != null) address.putUUID("library", libraryId);
            if (nodeId != null) address.putUUID("node", nodeId);
            address.putLong("placed", placementOrder); address.putBoolean("manual", manualBinding);
            tag.put("cloudAddress", address);
        } else tag.remove("cloudAddress");
    }
    @Override public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        var address = tag.getCompound("cloudAddress");
        owner = address.hasUUID("owner") ? address.getUUID("owner") : null;
        libraryId = address.hasUUID("library") ? address.getUUID("library") : null;
        nodeId = address.hasUUID("node") ? address.getUUID("node") : null;
        placementOrder = address.getLong("placed"); manualBinding = address.getBoolean("manual");
    }
    @Override public void markDirty() {
        super.markDirty();
        if (attached && !removed && node != null && !isRemote()) cloudStore().markNodeDirty(node);
    }
    /** Auto binding requires a real, uniquely bound AE grid; address sticks remain an explicit remote option. */
    private boolean synchronizeLibrary() {
        if (owner == null || removed) return false;
        if (manualBinding) return libraryId != null;
        var grid = getGrid();
        var binding = CloudLibraryNetwork.resolve(grid, owner);
        if (!binding.available()) {
            if (binding.state() == CloudLibraryNetwork.State.DISCONNECTED && grid != null
                    && grid == validatedLibraryGrid && libraryId != null && getMainNode().isActive()) return true;
            statusLabel = binding.translationKey(); return false;
        }
        if (!Objects.equals(libraryId, binding.libraryId())) {
            if (!tasks.healthy() || !tasks.jobs.isEmpty()) { statusLabel = "gtl_enhancedcore.cloud.network.busy"; return false; }
            selectLibrary(binding.libraryId());
        }
        validatedLibraryGrid = grid;
        return true;
    }
    public boolean cloudNetworkConnected() {
        if (isRemote() || owner == null || libraryId == null || !getMainNode().isActive()) return false;
        if (manualBinding) return CloudLibraryService.runtimeAuthorized(owner, libraryId, (ServerLevel)getLevel(), getPos());
        var grid = getGrid(); var binding = CloudLibraryNetwork.resolve(grid, owner);
        if (binding.available() && libraryId.equals(binding.libraryId())) { validatedLibraryGrid = grid; return true; }
        return binding.state() == CloudLibraryNetwork.State.DISCONNECTED && grid != null && grid == validatedLibraryGrid;
    }
    private void selectLibrary(UUID requested) {
        CloudPatternAllocator.remove(this);
        if (attached) cloudStore().detach(nodeId, UUID.randomUUID());
        attached = false; nodeId = null; node = null; libraryId = requested;
        tasks = new IvBufferState(); origin.clearWhenDrained(false); boundDefinition = "";
        slotExtent = 0; matched = 0; modeLabel = ""; published.clear(); publishedSnapshot = List.of();
        circuits.clear(); leaseOrder.clear(); leaseDisplays.clear(); invalidateSearches(); invalidLeases.clear(); validatedLeases.clear();
        lastLibraryRevision = -1; allocationEpoch++; uiRevision++; publicationDirty = true;
        ICraftingProvider.requestUpdate(getMainNode()); markDirty();
    }
    private void attach() {
        if (attached || owner == null || libraryId == null || removed) return;
        var store = cloudStore(); var library = store.library(libraryId);
        if (!library.authorized(owner)) { status("denied"); return; }
        // A crash may persist the authority before the block's address. Preserve the old task
        // partition and release its leases instead of leaving invisible claims at this position.
        for (var previous : store.nodesAt(getLevel().dimension().location(), getPos().asLong())) {
            if (!previous.id().equals(nodeId) && previous.owner().equals(owner) && previous.libraryId().equals(libraryId)
                    && !previous.detached() && !store.isNodeActive(previous.id())) store.detach(previous.id(), UUID.randomUUID());
        }
        node = nodeId == null ? store.createNode(owner, libraryId, getLevel().dimension().location(), getPos().asLong()) : store.node(nodeId);
        nodeId = node.id();
        if (!owner.equals(node.owner()) || !libraryId.equals(node.libraryId()) || !store.activate(nodeId, this, getLevel().dimension().location(), getPos().asLong())) {
            status("duplicate"); return;
        }
        var saved = node.ivState();
        if (!saved.isEmpty()) tasks.load(saved);
        if (!node.sourceRoute().isEmpty()) origin.load(node.sourceRoute());
        boundDefinition = node.sourceRoute().getString("machineDefinition");
        attached = true;
        publishedTaskIdentity = null;
        lastLibraryRevision = -1; invalidLeases.clear(); validatedLeases.clear();
        CloudPatternAllocator.register(this);
        store.bindNodeSnapshot(node, tasks::save, CompoundTag::new, this::originSnapshot);
        slotExtent = node.slots().keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        refreshPublished(); markDirty();
    }
    private void cloudMaintenance() {
        meNetworkActive = getMainNode().isActive();
        if (getOffsetTimer() % 5 != 0) return;
        try {
            if (!synchronizeLibrary()) { connected = false; return; }
            attach(); if (!attached) return;
            var library = cloudStore().library(libraryId);
            if (!CloudLibraryService.runtimeAuthorized(owner, libraryId, (ServerLevel)getLevel(), getPos())) { status("denied"); connected = false; return; }
            var machine = IvBuffers.controller(this);
            if (machine == null) { status("unbound"); connected = false; return; }
            if (IvBuffers.genericCloudMixedInputs(machine)) { status("mixed_inputs"); connected = false; return; }
            UUID instance = ((CloudMachineIdentity)machine).cloud$machineInstance(true);
            if (node.machineInstance() != null && !instance.equals(node.machineInstance())
                    && tasks.jobs.isEmpty() && IvBuffers.cloudControllers(this).stream().noneMatch(controller ->
                    Objects.equals(((CloudMachineIdentity)controller).cloud$machineInstance(false), node.machineInstance()))) {
                // Rebuilding a controller must not silently transfer its old work or mode claim.
                CloudPatternAllocator.remove(this);
                cloudStore().detach(nodeId, UUID.randomUUID()); attached = false;
                tasks = new IvBufferState(); nodeId = null; node = null; origin.clearWhenDrained(false);
                published.clear(); publishedSnapshot = List.of(); slotExtent = 0; attach();
            }
            if (node.mode() == null) {
                // Structure discovery order is not placement order. Claim all eligible peers in stable order.
                machine.getParts().stream().filter(p -> p instanceof CloudPatternBufferMachine)
                        .map(p -> (CloudPatternBufferMachine)p)
                        .sorted(Comparator.comparingLong((CloudPatternBufferMachine p) -> p.placementOrder).thenComparingLong(p -> p.getPos().asLong()))
                        .forEach(p -> p.claim(machine, instance));
                if (node.mode() == null) { status("no_mode"); connected = false; return; }
            }
            modeLabel = node.mode().toString();
            connected = cloudReady(true);
            status(!tasks.healthy() ? "quarantined" : !cloudMayUseNetwork() ? "origin" : !tasks.accepting ? "paused" : "online");
            if (lastLibraryRevision != library.allocationRevision()) CloudPatternAllocator.enqueue(this);
            // Finish the catalogue pass before releasing stale slots; each release wakes
            // availability and must not repeatedly restart the still-running prefix scan.
            if (!CloudPatternAllocator.isScanning(this)) for (UUID id : List.copyOf(invalidLeases)) {
                var e = library.entry(id);
                if (e.lease() == null || !e.lease().nodeId().equals(nodeId)) { invalidLeases.remove(id); continue; }
                if (!tasks.occupied(e.lease().stableSlot())) { cloudStore().releaseLease(nodeId, id, UUID.randomUUID()); invalidLeases.remove(id); }
            }
            if (publicationDirty) {
                publishedSnapshot = List.copyOf(published.values()); publicationDirty = false;
                needPatternSync = true; ICraftingProvider.requestUpdate(getMainNode());
            }
        } catch (RuntimeException failure) {
            connected = false; faulted = true; status("quarantined");
            if (!tasks.message.equals("gtl_enhancedcore.cloud.status.quarantined")) IvTaskLog.error(this, null, "CLOUD_ATTACH", failure);
            tasks.message = "gtl_enhancedcore.cloud.status.quarantined";
        } finally {
            boolean eligible = false;
            try { eligible = cloudReady(true) && tasks.accepting; } catch (RuntimeException unavailable) { /* Fail closed. */ }
            if (eligible != allocationEligible) {
                allocationEligible = eligible; CloudPatternAllocator.availabilityChanged(this);
            }
        }
    }
    private void status(String suffix) { statusLabel = "gtl_enhancedcore.cloud.status." + suffix; }
    private void claim(WorkableElectricMultiblockMachine machine, UUID instance) {
        // Every peer must resolve its actual network before the ordered claim pass;
        // otherwise fresh peers race their independent maintenance tick offsets.
        if (IvBuffers.controller(this) != machine || !synchronizeLibrary()
                || !CloudLibraryService.runtimeAuthorized(owner, libraryId, (ServerLevel)getLevel(), getPos())) return;
        attach();
        if (!attached || node.mode() != null || IvBuffers.controller(this) != machine) return;
        // A single fresh endpoint follows the controller's selected recipe map. A second
        // endpoint claims the next unoccupied map; established claims never follow UI toggles.
        var types = CloudEndpointPolicy.preferredModes(Arrays.stream(machine.getDefinition().getRecipeTypes())
                .map(t -> t.registryName).toList(), machine.getRecipeType() == null ? null : machine.getRecipeType().registryName);
        String claimed = cloudStore().claimMode(nodeId, instance, types);
        if (!claimed.isEmpty()) { tasks.owner = IvBuffers.ownerKey(machine); boundDefinition = machine.getDefinition().getId().toString(); modeLabel = claimed; markDirty(); }
    }
    private CompoundTag originSnapshot() { var tag = origin.save(); tag.putString("machineDefinition", boundDefinition); return tag; }
    public boolean cloudReady(boolean checkController) {
        if (isRemote() || !attached || removed || faulted || node == null || !tasks.healthy() || node.mode() == null || !getMainNode().isActive()) return false;
        if (!cloudStore().active(nodeId, this) || !CloudLibraryService.runtimeAuthorized(owner, libraryId, (ServerLevel)getLevel(), getPos())) return false;
        if (checkController) {
            var machine = IvBuffers.controller(this);
            if (machine == null || IvBuffers.genericCloudMixedInputs(machine)) return false;
        }
        return cloudMayUseNetwork();
    }
    public boolean cloudMayUseNetwork() {
        if (isRemote() || !attached || removed || faulted
                || !CloudLibraryService.runtimeAuthorized(owner, libraryId, (ServerLevel)getLevel(), getPos())
                || !cloudNetworkConnected()) return false;
        if (origin.canUse(this, !tasks.jobs.isEmpty())) return true;
        return getMainNode().isOnline() && CloudOriginAuthorization.authorizeAutomatic(
                ((ServerLevel)getLevel()).getServer(), origin.origin(), getGrid())
                && origin.authorizeReconnect(getGrid(), origin.origin(), true);
    }
    public boolean cloudCaptureOrigin() {
        boolean hasJobs = !tasks.jobs.isEmpty();
        if (!cloudReady(true) || !origin.captureOnAccept(this, endpoint(), hasJobs)) return false;
        var server = ((ServerLevel)getLevel()).getServer();
        if (hasJobs) CloudOriginAuthorization.capture(server, endpoint(), getGrid(), getMainNode().getNode());
        else CloudOriginAuthorization.beginWork(server, endpoint(), getGrid(), getMainNode().getNode());
        return true;
    }
    private CloudAeOriginGuard.Origin endpoint() {
        return new CloudAeOriginGuard.Origin(nodeId, getLevel().dimension().location().toString(), getPos().asLong(), node.generation());
    }
    public boolean cloudAcceptsMode(ResourceLocation type) { return node != null && type.equals(node.mode()); }
    public boolean cloudRouteValid(CompoundTag route) {
        var entry = entry(route.getInt("slot")); var lease = entry == null ? null : entry.lease();
        return lease != null && route.hasUUID("lease") && route.getUUID("lease").equals(lease.id())
                && route.getLong("generation") == node.generation() && lease.generation() == node.generation();
    }
    public CloudPatternStore.Entry entry(int slot) {
        if (!attached || node == null) return null;
        UUID id = node.slots().get(slot); return id == null ? null : cloudStore().library(libraryId).entry(id);
    }
    public Map<AEKey, Long> cloudSlotVirtual(int slot) { var entry = entry(slot); return entry == null ? Map.of() : CloudPatternMatching.virtual(entry.configuration(), getCircuitForRecipe(slot)); }
    public Map<AEKey, Long> cloudCatalysts() {
        return attached ? CloudLibraryService.catalysts((ServerLevel)getLevel(), libraryId) : Map.of();
    }
    public Map<AEKey, Long> cloudPatternCatalysts(int slot) {
        var entry = entry(slot); return entry == null ? Map.of() : CloudLibraryService.catalysts(entry);
    }
    public Map<AEKey, Long> cloudPatternCatalysts(IvJob job) {
        if (!attached || job.cloudPattern == null) return Map.of();
        var original = cloudStore().library(libraryId).entry(job.cloudPattern);
        return job.pattern.equals(original.original().save(new CompoundTag())) ? CloudLibraryService.catalysts(original) : Map.of();
    }
    public boolean cloudMayEditPattern(ServerPlayer player, UUID entryId) {
        if (!authorized(player) || !attached || removed || faulted || node == null || !cloudNetworkConnected()) return false;
        var entry = cloudStore().library(libraryId).entry(entryId); var lease = entry.lease();
        return lease != null && lease.nodeId().equals(nodeId) && entryId.equals(node.slots().get(lease.stableSlot()));
    }
    public IPatternDetails cloudRealPattern(int slot, ItemStack original) {
        var entry = entry(slot); if (entry == null || entry.lease() == null) return null;
        IPatternDetails processed = realPatternHelper.processPatternWithCircuit(original, circuit -> circuits.put(slot, IntCircuitBehaviour.stack(circuit)), getLevel(), keepByProduct);
        if (processed == null) return null;
        CompoundTag config = entry.configuration();
        if (config.contains("circuit")) circuits.put(slot, IntCircuitBehaviour.stack(config.getInt("circuit")));
        ItemStack definition = processed.getDefinition().toStack(); CompoundTag route = new CompoundTag();
        route.putUUID("buffer", tasks.identity); route.putInt("slot", slot); route.put("original", original.save(new CompoundTag()));
        route.putUUID("lease", entry.lease().id()); route.putLong("generation", node.generation());
        definition.getOrCreateTag().put(IvBuffers.ROUTE_KEY, route);
        return PatternDetailsHelper.decodePattern(definition, getLevel());
    }
    public void refreshPublished() {
        if (isRemote() || !attached) return;
        // Upstream refresh callbacks also occur for task/display changes. Only a
        // route identity or decoding option change requires rebuilding all leases;
        // catalogue validation adds/removes individual publications below.
        if (Objects.equals(publishedTaskIdentity, tasks.identity) && publishedGeneration == node.generation()
                && publishedKeepByProduct == keepByProduct) return;
        var previousOrder = List.copyOf(leaseOrder);
        var previousDisplays = Map.copyOf(leaseDisplays);
        published.clear(); circuits.clear(); leaseOrder.clear(); leaseDisplays.clear();
        for (var lease : node.leases()) {
            if (!validatedLeases.contains(lease.entryId())) continue;
            var entry = entry(lease.stableSlot()); var pattern = entry == null ? null : cloudRealPattern(lease.stableSlot(), entry.original());
            if (pattern != null) {
                var display = publishedKeepByProduct == keepByProduct ? previousDisplays.get(entry.id()) : null;
                published.put(lease.stableSlot(), pattern); leaseOrder.add(entry.id());
                leaseDisplays.put(entry.id(), display == null ? leaseDisplay(entry, pattern) : display);
            }
        }
        if (!previousOrder.equals(leaseOrder) || !previousDisplays.equals(leaseDisplays)) invalidateSearches();
        publishedTaskIdentity = tasks.identity; publishedGeneration = node.generation(); publishedKeepByProduct = keepByProduct;
        publishedSnapshot = List.copyOf(published.values()); matched = published.size(); uiRevision++;
        needPatternSync = true; ICraftingProvider.requestUpdate(getMainNode());
    }
    public void cloudLeased(CloudPatternStore.Entry entry) {
        if (slotExtent == Integer.MAX_VALUE) throw new IllegalStateException("Cloud slot address space exhausted");
        int slot = slotExtent;
        cloudStore().lease(nodeId, entry.id(), slot, UUID.randomUUID());
        validatedLeases.add(entry.id());
        slotExtent++;
        var pattern = cloudRealPattern(slot, entry.original());
        if (pattern != null) {
            published.put(slot, pattern); leaseOrder.add(entry.id()); leaseDisplays.put(entry.id(), leaseDisplay(entry, pattern)); invalidateSearches();
        }
        publicationDirty = true; matched = published.size(); uiRevision++; markDirty();
    }
    public CloudPatternMatching.Probe cloudProbe(CloudPatternStore.Entry entry) {
        final ItemStack[] circuit = {ItemStack.EMPTY};
        var processed = realPatternHelper.processPatternWithCircuit(entry.original(), n -> circuit[0] = IntCircuitBehaviour.stack(n), getLevel(), keepByProduct);
        var config = entry.configuration(); if (config.contains("circuit")) circuit[0] = IntCircuitBehaviour.stack(config.getInt("circuit"));
        var virtual = new LinkedHashMap<>(CloudPatternMatching.virtual(config, circuit[0]));
        cloudCatalysts().forEach((key, amount) -> virtual.merge(key, amount, Math::max));
        CloudLibraryService.catalysts(entry).forEach((key, amount) -> virtual.merge(key, amount, Math::max));
        return CloudPatternMatching.probe(processed, virtual);
    }
    public void cloudScanFinished(long revision) { lastLibraryRevision = revision; }
    public void cloudAllocationChanged() {
        lastLibraryRevision = -1; allocationEpoch++;
        // Readiness/membership changes are events, not a periodic catalogue refresh.
        CloudPatternAllocator.enqueue(this);
    }
    public long cloudAllocationEpoch() { return allocationEpoch; }
    public void cloudRecipesChanged() {
        lastLibraryRevision = -1; published.clear(); publishedSnapshot = List.of(); matched = 0;
        validatedLeases.clear();
        leaseOrder.clear(); leaseDisplays.clear(); invalidateSearches(); publicationDirty = true;
        needPatternSync = true; ICraftingProvider.requestUpdate(getMainNode());
        cloudAllocationChanged();
    }
    public void cloudClassificationFailed() {
        faulted = true; connected = false; status("quarantined"); tasks.message = "gtl_enhancedcore.cloud.status.quarantined";
        publishedSnapshot = List.of(); uiRevision++;
    }
    public void cloudOriginalRemoved(UUID entry, int slot) {
        validatedLeases.remove(entry); invalidLeases.remove(entry); published.remove(slot); circuits.remove(slot);
        if (leaseOrder.remove(entry)) invalidateSearches();
        leaseDisplays.remove(entry);
        matched = published.size(); publishedSnapshot = List.copyOf(published.values()); publicationDirty = false;
        uiRevision++; needPatternSync = true; ICraftingProvider.requestUpdate(getMainNode()); markDirty();
    }
    public void cloudInvalidLease(CloudPatternStore.Entry entry) {
        var lease = entry.lease(); if (lease == null || !lease.nodeId().equals(nodeId)) return;
        validatedLeases.remove(entry.id());
        invalidLeases.add(entry.id()); published.remove(lease.stableSlot());
        if (leaseOrder.remove(entry.id())) invalidateSearches();
        leaseDisplays.remove(entry.id());
        matched = published.size(); publicationDirty = true; uiRevision++;
    }
    public void cloudValidateLease(CloudPatternStore.Entry entry) {
        var lease = entry.lease(); if (lease == null || !lease.nodeId().equals(nodeId)) return;
        validatedLeases.add(entry.id());
        invalidLeases.remove(entry.id());
        if (!published.containsKey(lease.stableSlot())) {
            var pattern = cloudRealPattern(lease.stableSlot(), entry.original());
            if (pattern != null) {
                published.put(lease.stableSlot(), pattern); leaseOrder.add(entry.id()); leaseDisplays.put(entry.id(), leaseDisplay(entry, pattern)); invalidateSearches();
            }
            matched = published.size(); publicationDirty = true; uiRevision++;
        }
    }
    public long cloudAllocationOrder() { return placementOrder; }
    private LeaseDisplay leaseDisplay(CloudPatternStore.Entry entry, IPatternDetails pattern) {
        var component = displayComponent(entry, pattern);
        String name = component.getString();
        return new LeaseDisplay(name, name.toLowerCase(Locale.ROOT), Component.Serializer.toJson(component),
                Arrays.stream(pattern.getOutputs()).map(output -> output.what()).toList());
    }
    private Component displayComponent(CloudPatternStore.Entry entry, IPatternDetails pattern) {
        return pattern.getOutputs().length == 0 ? entry.original().getHoverName() : pattern.getOutputs()[0].what().getDisplayName();
    }
    private void invalidateSearches() {
        searches.clear(); searchScopeDirty = true;
    }
    private List<UUID> uiLeaseEntries() {
        if (nodeId == null || libraryId == null || !(getLevel() instanceof ServerLevel)) return List.of();
        var store = cloudStore();
        var savedNode = node != null ? node : store.node(nodeId);
        if (!savedNode.libraryId().equals(libraryId)) return List.of();
        var slots = savedNode.slots();
        if (uiSlotSnapshot == slots && uiInvalidSnapshot.equals(invalidLeases)) return uiEntrySnapshot;
        var entries = new ArrayList<UUID>();
        for (var slot : new TreeMap<>(slots).entrySet()) {
            var entry = store.library(libraryId).entry(slot.getValue());
            var lease = entry.lease();
            if (lease != null && lease.nodeId().equals(nodeId) && lease.stableSlot() == slot.getKey()
                    && !invalidLeases.contains(entry.id())) entries.add(entry.id());
        }
        uiSlotSnapshot = slots; uiInvalidSnapshot = Set.copyOf(invalidLeases);
        uiEntrySnapshot = List.copyOf(entries); invalidateSearches();
        return uiEntrySnapshot;
    }
    private LeaseDisplay cachedLeaseDisplay(CloudPatternStore.Entry entry) {
        CompoundTag metadata = entry.displayMetadata();
        if (metadata.getInt("displaySchema") != 3) {
            CloudLibraryService.prepareDisplay(entry, (ServerLevel)getLevel());
            metadata = entry.displayMetadata();
        }
        var outputs = new ArrayList<AEKey>();
        for (var value : metadata.getList("outputItemKeys", Tag.TAG_STRING)) {
            var key = net.minecraft.resources.ResourceLocation.tryParse(value.getAsString());
            if (key != null && BuiltInRegistries.ITEM.containsKey(key)) outputs.add(AEItemKey.of(BuiltInRegistries.ITEM.get(key)));
        }
        for (var value : metadata.getList("outputFluidKeys", Tag.TAG_STRING)) {
            var key = net.minecraft.resources.ResourceLocation.tryParse(value.getAsString());
            if (key != null && BuiltInRegistries.FLUID.containsKey(key)) outputs.add(AEFluidKey.of(BuiltInRegistries.FLUID.get(key)));
        }
        String name = metadata.getString("name");
        return new LeaseDisplay(name, name.toLowerCase(Locale.ROOT), metadata.getString("nameJson"), List.copyOf(outputs));
    }
    private void appendSearchScope(CompoundTag tag, Map<UUID, LeaseDisplay> displays) {
        if (searchScopeDirty) {
            var items = new BitSet(); var fluids = new BitSet();
            for (var display : displays.values()) for (var output : display.outputs()) {
                if (output instanceof AEItemKey item) {
                    int id = BuiltInRegistries.ITEM.getId(item.getItem());
                    if (id >= 0) items.set(id);
                } else if (output instanceof AEFluidKey fluid) {
                    int id = BuiltInRegistries.FLUID.getId(fluid.getFluid());
                    if (id >= 0) fluids.set(id);
                }
            }
            searchItems = items.toLongArray(); searchFluids = fluids.toLongArray(); searchScopeDirty = false;
        }
        tag.putLongArray("searchItems", searchItems); tag.putLongArray("searchFluids", searchFluids);
    }

    @Override public ModularUI createUI(Player player) { return CloudPatternUi.create(this, this, player, false); }
    @Override public Widget createUIWidget() { return new CloudPatternBufferWidget(this); }
    @Override public CompoundTag cloudUiInitialSnapshot(ServerPlayer player) {
        try {
            if (authorized(player, true) && IvBuffers.controller(this) != null && !attached
                    && synchronizeLibrary()) attach();
            return snapshot(player, "", "", 0, "", 0, CloudPatternSearch.EMPTY, true);
        }
        catch (RuntimeException failure) {
            var tag = new CompoundTag(); tag.putString("status", "gtl_enhancedcore.cloud.status.quarantined");
            tag.putString("diagnostic", failure.getClass().getSimpleName()); return tag;
        }
    }
    @Override public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected) {
        return cloudUiSnapshot(player, search, type, page, selected, 0);
    }
    @Override public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected, int recoveryPage) {
        return cloudUiSnapshot(player, search, type, page, selected, recoveryPage, CloudPatternSearch.EMPTY);
    }
    @Override public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected, int recoveryPage, CloudPatternSearch localized) {
        try { return snapshot(player, search, type, page, selected, recoveryPage, localized, false); }
        catch (RuntimeException failure) {
            var tag = new CompoundTag(); tag.putString("status", "gtl_enhancedcore.cloud.status.quarantined");
            tag.putString("diagnostic", failure.getClass().getSimpleName()); return tag;
        }
    }
    private CompoundTag snapshot(ServerPlayer player, String search, String type, int page, String selected, int recoveryPage, CloudPatternSearch localized, boolean opening) {
        CompoundTag tag = new CompoundTag();
        if (!authorized(player, opening)) { tag.putString("status", "gtl_enhancedcore.cloud.status.denied"); return tag; }
        tag.putLong("revision", uiRevision); tag.putString("status", statusLabel); tag.putString("mode", modeLabel);
        tag.putBoolean("networkConnected", cloudNetworkConnected());
        tag.putString("networkStatus", statusLabel.startsWith("gtl_enhancedcore.cloud.network.")
                ? statusLabel : CloudLibraryNetwork.resolve(getGrid(), player).translationKey());
        tag.putBoolean("manualBinding", manualBinding);
        var uiEntries = uiLeaseEntries();
        if (searchScopeDirty) {
            var displays = new LinkedHashMap<UUID, LeaseDisplay>();
            if (libraryId != null) for (UUID id : uiEntries) {
                var display = leaseDisplays.get(id);
                if (display == null) display = cachedLeaseDisplay(cloudStore().library(libraryId).entry(id));
                displays.put(id, display);
            }
            uiDisplaySnapshot = Map.copyOf(displays);
        }
        var uiDisplays = uiDisplaySnapshot;
        appendSearchScope(tag, uiDisplays);
        if (libraryId == null) { tag.putInt("pages", 1); tag.put("entries", new ListTag()); return tag; }
        ListTag modes = new ListTag(); var machine = IvBuffers.controller(this);
        if (machine != null && node != null) for (var recipeType : machine.getDefinition().getRecipeTypes()) {
            var row = new CompoundTag(); row.putString("id", recipeType.registryName.toString());
            row.putBoolean("current", recipeType.registryName.equals(node.mode()));
            UUID machineId = ((CloudMachineIdentity)machine).cloud$machineInstance(false);
            row.putBoolean("available", machineId != null && cloudStore().canClaimMode(nodeId, machineId, recipeType.registryName));
            modes.add(row);
        }
        tag.put("modes", modes);
        tag.putString("library", libraryId.toString()); tag.putString("target", tasks.owner);
        tag.putInt("matched", matched); tag.putInt("active", (int)tasks.jobs.stream().filter(j -> j.parallel > 0).count());
        tag.putInt("queued", tasks.jobs.size());
        var recoveries = cloudStore().library(libraryId).recoveries().stream().filter(r -> r.status().equals("FROZEN")).toList();
        tag.putInt("recovery", recoveries.size()); ListTag recoveryRows = new ListTag();
        int recoveryPages = Math.max(1, (recoveries.size() + 3) / 4);
        int currentRecovery = Math.max(0, Math.min(recoveryPages - 1, recoveryPage));
        tag.putInt("recoveryPage", currentRecovery); tag.putInt("recoveryPages", recoveryPages);
        for (var r : recoveries.stream().skip(currentRecovery * 4L).limit(4).toList()) {
            var row = new CompoundTag(); row.putString("id", r.nodeId().toString()); row.putString("kind", "NODE"); row.putString("status", r.status());
            row.putString("name", r.mode() == null ? "" : r.mode().toString()); row.putString("target", r.nodeId().toString()); recoveryRows.add(row);
        }
        tag.put("recoveryEntries", recoveryRows);
        String query = search.toLowerCase(Locale.ROOT);
        var library = cloudStore().library(libraryId);
        List<UUID> filtered;
        if (query.isEmpty() || localized.matchesType(modeLabel)) {
            // An empty/type-wide query already has its complete stable order.
            filtered = uiEntries;
        } else {
            filtered = searches.computeIfAbsent(new Query(query, localized), ignored -> {
                var matches = new ArrayList<UUID>();
                for (UUID id : uiEntries) {
                    var display = uiDisplays.get(id);
                    if (id.toString().contains(query) || display != null && (display.normalizedName().contains(query)
                            || display.outputs().stream().anyMatch(localized::matches))) matches.add(id);
                }
                return List.copyOf(matches);
            });
            while (searches.size() > 8) searches.remove(searches.keySet().iterator().next());
        }
        int pages = Math.max(1, (filtered.size() + CloudLibraryService.PAGE_SIZE - 1) / CloudLibraryService.PAGE_SIZE); int current = Math.max(0, Math.min(pages - 1, page));
        tag.putInt("total", filtered.size());
        tag.putInt("page", current); tag.putInt("pages", pages); ListTag rows = new ListTag();
        int first = current * CloudLibraryService.PAGE_SIZE;
        int end = Math.min(filtered.size(), first + CloudLibraryService.PAGE_SIZE);
        for (int index = first; index < end; index++) {
            UUID id = filtered.get(index);
            var e = library.entry(id); var row = CloudLibraryService.entry(e, (ServerLevel)getLevel());
            var display = uiDisplays.get(id);
            if (display != null) { row.putString("name", display.name()); row.putString("nameJson", display.nameJson()); }
            row.putString("type", modeLabel); row.putBoolean("leased", true); rows.add(row);
        }
        tag.putBoolean("indexing", false); tag.putInt("indexed", uiEntries.size()); tag.putInt("libraryTotal", uiEntries.size());
        String diagnostic = tasks.message.isEmpty() ? tasks.jobs.stream().map(j -> j.error).filter(error -> !error.isEmpty()).findFirst().orElse("") : tasks.message;
        tag.put("entries", rows); tag.putString("diagnostic", diagnostic);
        CloudPatternInventories.decorate(this, player, tag); return tag;
    }
    private boolean authorized(ServerPlayer player) {
        return authorized(player, false);
    }
    private boolean authorized(ServerPlayer player, boolean opening) {
        return (libraryId != null ? CloudLibraryService.authorized(player, libraryId, this)
                : player.getUUID().equals(owner) && getLevel() instanceof ServerLevel level && player.serverLevel() == level
                && level.getChunkSource().getChunkNow(getPos().getX() >> 4, getPos().getZ() >> 4) != null
                && level.getBlockEntity(getPos()) == getHolder().getSelf()
                && level.mayInteract(player, getPos()) && CloudLibraryService.claimAllows(player, level, getPos()))
                && player.distanceToSqr(getPos().getX() + .5, getPos().getY() + .5, getPos().getZ() + .5) <= 64
                && (opening || player.containerMenu instanceof com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer menu
                && menu.getModularUI().holder == this);
    }
    @Override public void cloudUiAction(ServerPlayer player, String action, CompoundTag payload) {
        if (!authorized(player) || !attached) return;
        if (CloudPatternInventories.action(this, player, libraryId, action, payload)) return;
        if (payload.getLong("revision") != uiRevision) return;
        try { switch (action) {
            case "pause", "drain" -> IvCancellation.setAccepting(this, false);
            case "resume" -> { if (cloudReady(true)) { IvCancellation.resume(this); lastLibraryRevision = -1; } }
            case "cancel" -> IvCancellation.cancel(this, -1);
            case "reconnect" -> {
                if (CloudOriginAuthorization.authorize(player, origin.origin(), getGrid())
                        && origin.authorizeReconnect(getGrid(), origin.origin(), true)) markDirty();
            }
            case "set_mode" -> switchMode(player, ResourceLocation.tryParse(payload.getString("type")));
            case "recover" -> recover(player, payload.getString("recoveryId"));
            case "diagnose" -> { faulted = false; cloudRecipesChanged(); cloudMaintenance(); }
            default -> { return; }
        }
        uiRevision++; markDirty();
        } catch (RuntimeException failure) { tasks.message = "gtl_enhancedcore.cloud.status.review"; uiRevision++; }
    }
    @Override public com.lowdragmc.lowdraglib.side.item.IItemTransfer cloudPatternItemCatalysts(Player player, String id) {
        return CloudPatternInventories.items(this, player, libraryId, id, () -> player instanceof ServerPlayer server && authorized(server), () -> player instanceof ServerPlayer server && authorized(server));
    }
    @Override public com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer cloudPatternFluidCatalysts(Player player, String id) {
        return CloudPatternInventories.fluids(this, player, libraryId, id, () -> player instanceof ServerPlayer server && authorized(server), () -> player instanceof ServerPlayer server && authorized(server));
    }
    private void switchMode(ServerPlayer player, ResourceLocation requested) {
        var machine = IvBuffers.controller(this);
        if (machine == null || requested == null || !tasks.healthy() || !tasks.jobs.isEmpty()) return;
        var types = Arrays.stream(machine.getDefinition().getRecipeTypes()).map(t -> t.registryName).toList();
        if (!types.contains(requested) || requested.equals(node.mode())
                || !cloudStore().canClaimMode(nodeId, ((CloudMachineIdentity)machine).cloud$machineInstance(true), requested)) return;
        CloudPatternAllocator.remove(this);
        cloudStore().detach(nodeId, UUID.randomUUID()); attached = false; nodeId = null; tasks = new IvBufferState();
        origin.clearWhenDrained(false); slotExtent = 0; published.clear(); publishedSnapshot = List.of(); attach();
        cloudStore().claimMode(nodeId, ((CloudMachineIdentity)machine).cloud$machineInstance(true), List.of(requested));
        tasks.owner = IvBuffers.ownerKey(machine); modeLabel = node.mode() == null ? "" : node.mode().toString(); lastLibraryRevision = -1;
        boundDefinition = machine.getDefinition().getId().toString();
    }
    private void recover(ServerPlayer player, String id) {
        UUID recoveryId; try { recoveryId = UUID.fromString(id); } catch (IllegalArgumentException invalid) { return; }
        if (!tasks.jobs.isEmpty() || node.mode() == null || !cloudReady(true)) return;
        var source = cloudStore().node(recoveryId);
        var machine = IvBuffers.controller(this);
        if (machine == null || !machine.getDefinition().getId().toString().equals(source.sourceRoute().getString("machineDefinition"))) {
            status("review"); return;
        }
        // One explicit recovery action cannot silently adopt orders paid by several shared
        // controller instances. Such a partition remains frozen for individual review.
        var assigned = new HashSet<UUID>();
        for (Tag value : source.ivState().getList("jobs", Tag.TAG_COMPOUND)) {
            var savedJob = (CompoundTag)value;
            if (savedJob.hasUUID("cloudMachine")) assigned.add(savedJob.getUUID("cloudMachine"));
        }
        if (assigned.size() > 1) { status("review"); return; }
        CloudAeOriginGuard sourceOrigin = new CloudAeOriginGuard(); sourceOrigin.load(source.sourceRoute());
        if (!CloudOriginAuthorization.authorize(player, sourceOrigin.origin(), getGrid())) { status("origin"); return; }
        // Establish the live target route before transferring ownership. An offline AE node
        // may still expose an IGrid, but cannot become an execution/refund endpoint.
        if (!origin.captureOnAccept(this, endpoint(), false)) { status("origin"); return; }
        cloudStore().restore(recoveryId, nodeId, player.getUUID(), node.machineInstance(), node.mode(), source.sourceRoute(), UUID.randomUUID());
        tasks.load(node.ivState());
        for (var job : tasks.jobs) job.cloudMachine = ((CloudMachineIdentity)machine).cloud$machineInstance(true);
        // This explicit action authenticates the chosen destination; source origin is retained in the recovery record.
        CloudOriginAuthorization.beginWork(((ServerLevel)getLevel()).getServer(), endpoint(), getGrid(), getMainNode().getNode());
        tasks.owner = IvBuffers.ownerKey(machine); boundDefinition = machine.getDefinition().getId().toString();
        cloudStore().bindNodeSnapshot(node, tasks::save, CompoundTag::new, this::originSnapshot);
        // Restoring a partition changes the task identity encoded in every provider route.
        // Rebuild those routes now, so a later resume cannot advertise the old target identity.
        refreshPublished(); markDirty();
    }
    @Override public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch.isDataStick(stack)) {
            if (world.isClientSide) return InteractionResult.SUCCESS;
            var tag = stack.getTag(); var address = tag == null ? new CompoundTag() : tag.getCompound("gtl_cloud_library");
            if (!(player instanceof ServerPlayer serverPlayer) || !address.hasUUID("library") || !tasks.jobs.isEmpty()) return InteractionResult.FAIL;
            UUID requested = address.getUUID("library");
            if (!CloudLibraryService.authorized(serverPlayer, requested, this)) return InteractionResult.FAIL;
            if (!tasks.healthy()) return InteractionResult.FAIL;
            owner = player.getUUID(); manualBinding = true; validatedLibraryGrid = null;
            selectLibrary(requested); attach(); markDirty();
            serverPlayer.displayClientMessage(Component.translatable("gtl_enhancedcore.cloud.status.bound"), true);
            return InteractionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hand, hit);
    }
}
