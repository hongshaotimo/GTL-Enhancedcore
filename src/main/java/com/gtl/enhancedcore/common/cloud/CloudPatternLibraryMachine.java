package com.gtl.enhancedcore.common.cloud;

import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.feature.*;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;
import appeng.api.networking.*;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.*;
import com.gtl.enhancedcore.common.cloud.ui.*;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.lowdragmc.lowdraglib.syncdata.annotation.*;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** A small loaded control panel for the world-owned library; original patterns never live in BE sync. */
public final class CloudPatternLibraryMachine extends MultiblockControllerMachine implements IFancyUIMachine, IMachineLife,
        IInteractedMachine, CloudPatternUiHost, CloudImportSourceAccess, IGridConnectedMachine {
    private static final ManagedFieldHolder FIELDS = new ManagedFieldHolder(CloudPatternLibraryMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);
    @Persisted @DescSynced protected UUID owner;
    @Persisted @DescSynced protected UUID libraryId;
    @Persisted protected final ItemStackTransfer inbox = new ItemStackTransfer(1);
    @Persisted protected UUID inboxActor;
    @Persisted protected CompoundTag importMarkers = new CompoundTag();
    @Persisted protected long selectedSource;
    @Persisted protected boolean hasSource;
    @Persisted protected UUID selectedSourceIdentity;
    @DescSynced protected boolean connected;
    @Persisted private final GridNodeHolder nodeHolder;
    @DescSynced private boolean online;
    private String diagnostic = "", diagnosticDetail = "", importReason = "", importUnresolved = "";
    private boolean hasImportResult, lastImportPreview;
    private int importCount, importScanned, importTotalSlots;
    private UUID importingPlayer;
    private int importCursor;
    private java.util.concurrent.CompletableFuture<Void> inboxUpload;
    private long uploadUiChange;
    private boolean uploadSourceRemoved;
    private boolean uploadSourceLoaded;
    @Override public long cloudUiChangeToken() { return uploadUiChange; }

    public CloudPatternLibraryMachine(IMachineBlockEntity holder) { super(holder); nodeHolder = new GridNodeHolder(this); }
    @Override public IManagedGridNode getMainNode() { return nodeHolder.getMainNode(); }
    @Override public boolean isOnline() { return online; }
    @Override public void setOnline(boolean value) { online = value; }
    @Override public boolean shouldSyncME() { return false; }
    public boolean networkAvailable(IGrid grid) {
        if (!(getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread() || grid == null
                || libraryId == null || cloud$importQuarantined()) return false;
        var chunk = level.getChunkSource().getChunkNow(getPos().getX() >> 4, getPos().getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(getPos()) != getHolder().getSelf()
                || CloudLibraryNetwork.serviceGrid(this) != grid) return false;
        try { return CloudPatternStore.get(level).library(libraryId).healthy(); }
        catch (RuntimeException invalid) { return false; }
    }
    /** The SavedData anchor resolves the library's live grid independently of GTLCore selection. */
    public IGrid serviceGrid() {
        return CloudLibraryNetwork.serviceGrid(this);
    }
    public boolean meNetworkConnected() { return isRemote() ? online : serviceGrid() != null; }
    public boolean mayConfigureMeNetwork(ServerPlayer player) { return openingAuthorized(player); }
    @Override public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing); getMainNode().setExposedOnSides(java.util.EnumSet.allOf(Direction.class));
    }
    @Override public ManagedFieldHolder getFieldHolder() { return FIELDS; }
    public UUID libraryId() { return libraryId; }
    public UUID owner() { return owner; }
    public ItemStackTransfer importInventory() { return inbox; }
    public ServerLevel serverLevel() { return (ServerLevel)getLevel(); }
    @Override public void onMachinePlaced(LivingEntity placer, ItemStack stack) {
        if (placer instanceof ServerPlayer player) {
            owner = player.getUUID();
            getMainNode().setOwningPlayer(player);
            libraryId = CloudPatternStore.get(player.serverLevel()).defaultLibrary(owner);
            markDirty();
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        uploadSourceLoaded = true;
        getMainNode().setExposedOnSides(java.util.EnumSet.allOf(Direction.class));
        if (getLevel() instanceof ServerLevel) {
            CloudLibraryNetwork.loaded(this);
            CloudImportJournal.reconcile(this);
            subscribeServerTick(this::importTick);
            online = serviceGrid() != null;
            connected = serviceGrid() != null && !cloud$importQuarantined();
        }
    }
    @Override public void onStructureFormed() {
        super.onStructureFormed();
        connected = serviceGrid() != null && !cloud$importQuarantined();
    }
    @Override public void onStructureInvalid() {
        super.onStructureInvalid();
    }
    @Override public void onUnload() {
        uploadSourceLoaded = false;
        if (getLevel() instanceof ServerLevel) {
            CloudNetworkPatternImport.forget(this);
            CloudLibraryNetwork.unloaded(this);
        }
        importingPlayer = null; connected = false; online = false;
        super.onUnload();
    }
    @Override public void onMachineRemoved() {
        if (getLevel() instanceof ServerLevel) {
            CloudNetworkPatternImport.forget(this);
            CloudLibraryNetwork.unloaded(this);
            uploadSourceRemoved = true;
            if (inboxUpload == null) CloudImportJournal.retire(this);
            clearInventory(inbox);
        }
        importingPlayer = null; connected = false; online = false;
    }
    @Override public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            tag.remove("importMarkers"); tag.remove("wirelessBinding"); tag.remove("owner"); tag.remove("libraryId"); tag.remove("inboxActor");
        }
    }
    @Override public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return !(player instanceof ServerPlayer server) || openingAuthorized(server);
    }
    @Override public ModularUI createUI(Player player) { return CloudPatternUi.create(this, this, player, true); }
    @Override public boolean cloudUiConnected() { return connected && isFormed() && (!(getLevel() instanceof ServerLevel) || serviceGrid() != null) && !cloud$importQuarantined(); }
    public boolean menuAuthorized(ServerPlayer player) {
        return isFormed() && viewAuthorized(player);
    }
    boolean viewAuthorized(ServerPlayer player) {
        return openingAuthorized(player)
                && player.containerMenu instanceof com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer menu
                && menu.getModularUI().holder == this;
    }
    private boolean openingAuthorized(ServerPlayer player) {
        return isFormed() && player.serverLevel() == getLevel() && CloudLibraryService.authorized(player, libraryId, this)
                && player.distanceToSqr(getPos().getX()+.5, getPos().getY()+.5,getPos().getZ()+.5) <= 64;
    }
    @Override public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(held.getItem());
        if (id == null || !(id.toString().equals("gtceu:data_stick") || id.toString().equals("gtmadvancedhatch:tool_net_data_stick"))) return super.onUse(state, level, pos, player, hand, hit);
        if (player instanceof ServerPlayer server) {
            if (!openingAuthorized(server)) return InteractionResult.FAIL;
            CompoundTag sourceAddress = held.getTag() == null ? new CompoundTag() : held.getTag().getCompound(CloudImportSourceAddress.KEY);
            if (!sourceAddress.isEmpty() && !server.isShiftKeyDown()) {
                if (!sourceAddress.hasUUID("identity") || !sourceAddress.getString("dimension").equals(level.dimension().location().toString())) {
                    server.displayClientMessage(net.minecraft.network.chat.Component.translatable("gtl_enhancedcore.cloud.status.no_source"), true); return InteractionResult.FAIL;
                }
                var candidate = loadedBuffer(BlockPos.of(sourceAddress.getLong("position")));
                if (candidate == null || !((CloudImportSourceAccess)candidate).cloud$sourceIdentity().equals(sourceAddress.getUUID("identity"))) {
                    server.displayClientMessage(net.minecraft.network.chat.Component.translatable("gtl_enhancedcore.cloud.status.no_source"), true); return InteractionResult.FAIL;
                }
                if (!selectImportSource(server, candidate)) return InteractionResult.FAIL;
                diagnostic = "gtl_enhancedcore.cloud.source.selected";
                server.displayClientMessage(net.minecraft.network.chat.Component.translatable(diagnostic), true);
                return InteractionResult.SUCCESS;
            }
            held.getOrCreateTag().remove(CloudImportSourceAddress.KEY);
            CloudLibraryService.writeAddress(held, libraryId, owner);
            server.displayClientMessage(net.minecraft.network.chat.Component.translatable("gtl_enhancedcore.cloud.status.bound"), true);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected) {
        return cloudUiSnapshot(player, search, type, page, selected, 0);
    }
    @Override public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected, int recoveryPage) {
        return cloudUiSnapshot(player, search, type, page, selected, recoveryPage, CloudPatternSearch.EMPTY);
    }
    @Override public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected, int recoveryPage, CloudPatternSearch localized) {
        return snapshot(player, search, type, page, selected, recoveryPage, localized, false);
    }
    @Override public CompoundTag cloudUiInitialSnapshot(ServerPlayer player) {
        return snapshot(player, "", "", 0, "", 0, CloudPatternSearch.EMPTY, true);
    }
    private CompoundTag snapshot(ServerPlayer player, String search, String type, int page, String selected, int recoveryPage, CloudPatternSearch localized, boolean opening) {
        CompoundTag result = new CompoundTag();
        if (!(opening ? openingAuthorized(player) : viewAuthorized(player))) { result.putString("status", "gtl_enhancedcore.cloud.status.denied"); return result; }
        try {
            result = CloudLibraryService.page(serverLevel(), libraryId, search, type, page, selected, localized);
            result.merge(CloudImportJournal.reviewPage(serverLevel(), libraryId, recoveryPage));
            result.putString("status", cloud$importQuarantined() ? "gtl_enhancedcore.cloud.status.review" : !isFormed() ? "gtl_enhancedcore.cloud.status.unformed" : "gtl_enhancedcore.cloud.status.ready");
            result.putString("diagnostic", diagnostic); result.putString("diagnosticDetail", diagnosticDetail); result.putString("library", libraryId.toString());
            var network = CloudLibraryNetwork.resolve(serviceGrid(), player);
            result.putBoolean("networkConnected", network.available()); result.putString("networkStatus", network.translationKey());
            result.put("terminalReviewDiagnostics", CloudTerminalUpload.reviewDiagnostics(libraryId));
            result.putBoolean("importResult", hasImportResult); result.putBoolean("importPreview", lastImportPreview);
            result.putInt("importCount", importCount); result.putInt("importScanned", importScanned); result.putInt("importTotalSlots", importTotalSlots);
            result.putString("importReason", importReason); result.putString("importUnresolved", importUnresolved);
            var source = source();
            if (source != null) { result.putString("sourceName", source.getDefinition().getId().toString()); result.putString("sourcePos", source.getPos().toShortString()); }
        } catch (RuntimeException failure) {
            result.putString("status", "gtl_enhancedcore.cloud.status.review"); result.putString("diagnostic", "gtl_enhancedcore.cloud.status.review");
            result.putString("diagnosticDetail", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
        CloudNetworkPatternImport.snapshot(this, result);
        CloudPatternInventories.decorate(this, player, result);
        return result;
    }
    @Override public void cloudUiAction(ServerPlayer player, String action, CompoundTag payload) {
        if (!menuAuthorized(player)) return;
        try {
            diagnosticDetail = "";
            if (CloudPatternInventories.action(this, player, libraryId, action, payload)) return;
            var store = CloudPatternStore.get(serverLevel()); var library = store.library(libraryId);
            switch (action) {
                case "terminal_diagnostics" -> {
                    var diagnostics = CloudTerminalUpload.reviewDiagnostics(libraryId);
                    for (int index = 0; index < diagnostics.size(); index++) {
                        String text = diagnostics.getString(index);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(text).withStyle(style -> style
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        net.minecraft.network.chat.Component.translatable("gtl_enhancedcore.cloud.terminal.copy_diagnostic")))));
                    }
                }
                case "import" -> uploadInbox(player);
                case "import_cursor" -> uploadCarried(player);
                case "network_import" -> CloudNetworkPatternImport.start(player, this);
                case "network_import_undo" -> CloudNetworkPatternImport.undo(player, this);
                case "withdraw", "take_pattern" -> withdrawPattern(player, payload, action.equals("withdraw"));
                case "archive", "restore" -> {
                    library.archive(player.getUUID(), UUID.fromString(payload.getString("selected")), payload.getLong("revision"), action.equals("archive")); store.checkpoint();
                }
                case "grant", "revoke" -> {
                    UUID delegate = UUID.fromString(payload.getString("delegate"));
                    if (action.equals("grant") && !CloudLibraryService.sameTeam(owner, delegate)) throw new SecurityException("Delegate must remain on the owner team");
                    library.grant(player.getUUID(), delegate, action.equals("grant")); store.checkpoint();
                }
                case "preview_import", "bulk_import" -> {
                    hasImportResult = false; importCount = 0; importScanned = 0; importTotalSlots = 0; importUnresolved = "";
                    importingPlayer = null;
                    if (action.equals("bulk_import")) importCursor = 0;
                    var source = source(); if (source == null) { diagnostic = "gtl_enhancedcore.cloud.import.not_loaded"; importReason = diagnostic; break; }
                    importTotalSlots = source.getPatternInventory().getSlots();
                    var result = importPage(player, source, action.equals("preview_import"));
                    recordImportResult(result, action.equals("preview_import"), false);
                    if (action.equals("bulk_import") && result.rejection() == CloudBufferImport.Rejection.NONE && result.unresolved() == null
                            && result.nextSlot() < source.getPatternInventory().getSlots()) { importingPlayer = player.getUUID(); importCursor = result.nextSlot(); }
                }
                case "diagnose" -> { diagnostic = "gtl_enhancedcore.cloud.status." + (store.healthy() ? "ready" : "review"); diagnosticDetail = store.diagnostic(); }
                case "priority_up", "priority_down" -> {
                    CloudLibraryService.adjustPriority(serverLevel(), libraryId, player.getUUID(), UUID.fromString(payload.getString("selected")), action.equals("priority_up") ? 1 : -1);
                }
                case "recover", "review_transfer" -> {
                    UUID operation = UUID.fromString(payload.getString("recoveryId"));
                    if (library.recoveries().stream().anyMatch(r -> r.nodeId().equals(operation))) {
                        diagnostic = "gtl_enhancedcore.cloud.recovery.visit_node"; break;
                    }
                    CloudImportJournal.retry(serverLevel(), libraryId, player, operation);
                    diagnostic = "gtl_enhancedcore.cloud.status.ready";
                }
                default -> diagnostic = "gtl_enhancedcore.cloud.status.unsupported";
            }
        } catch (RuntimeException failure) {
            recordFailure(failure);
            if (failure instanceof CloudPatternStore.DuplicatePatternException)
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(diagnostic), true);
        }
    }
    private void recordImportResult(CloudBufferImport.Result result, boolean preview, boolean append) {
        hasImportResult = true; lastImportPreview = preview; importScanned = result.nextSlot();
        int count = preview ? result.simulated() : result.moved(); importCount = append ? Math.addExact(importCount, count) : count;
        importUnresolved = result.unresolved() == null ? "" : result.unresolved().toString();
        importReason = result.unresolved() == null ? "gtl_enhancedcore.cloud.import." + result.rejection().name().toLowerCase(java.util.Locale.ROOT)
                : "gtl_enhancedcore.cloud.status.review";
        diagnostic = importReason; diagnosticDetail = "";
    }
    private void recordFailure(RuntimeException failure) {
        diagnostic = failure instanceof CloudPatternStore.DuplicatePatternException
                ? "gtl_enhancedcore.cloud.status.duplicate" : "gtl_enhancedcore.cloud.status.review";
        diagnosticDetail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (hasImportResult) importReason = diagnostic;
    }
    private void uploadInbox(ServerPlayer player) {
        if (inboxUpload != null) return;
        if (cloud$importQuarantined()) throw new IllegalStateException("Pending import requires review");
        ItemStack pattern = inbox.getStackInSlot(0).copyWithCount(1);
        if (pattern.isEmpty()) return;
        var decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(pattern, getLevel());
        if (decoded == null || decoded instanceof appeng.crafting.pattern.AECraftingPattern) throw new IllegalArgumentException("Processing pattern required");
        CloudPatternStore.get(serverLevel()).library(libraryId).requireUniqueOriginal(pattern);
        var sink = CloudImportJournal.sink(serverLevel(), libraryId, player.getUUID());
        UUID operation = sink.prepare(new CloudBufferImport.Source(getLevel().dimension().location().toString(), getPos().asLong(), cloud$sourceIdentity()), 0, pattern, new CompoundTag());
        try {
            ItemStack actual = inbox.extractItem(0, 1, false, true);
            if (!ItemStack.matches(actual, pattern)) throw new IllegalStateException("Upload source changed");
            cloud$markProcessed(operation); inboxActor = null;
            diagnostic = "gtl_enhancedcore.cloud.status.uploading"; uploadUiChange++; markDirty();
            final ServerLevel uploadLevel = serverLevel();
            inboxUpload = CloudImportJournal.commitUploadAsync(uploadLevel, operation, actual);
            inboxUpload.whenComplete((ignored, failure) -> uploadLevel.getServer().execute(() -> {
                inboxUpload = null;
                try {
                    if (failure != null) throw new IllegalStateException("Upload durable commit failed", failure);
                    if (uploadSourceRemoved) CloudImportJournal.retireCompletedUpload(uploadLevel, operation);
                    // Transaction completion is world authority, not proof this old block
                    // object is still loaded or represents the block now at its position.
                    if (!uploadSourceLoaded || uploadSourceRemoved || MetaMachine.getMachine(getLevel(), getPos()) != this) return;
                    diagnostic = "gtl_enhancedcore.cloud.status.ready";
                } catch (RuntimeException completionFailure) {
                    if (!uploadSourceLoaded || uploadSourceRemoved || MetaMachine.getMachine(getLevel(), getPos()) != this) return;
                    cloud$quarantineImport("Upload " + operation + " requires review");
                    recordFailure(completionFailure);
                }
                uploadUiChange++; markDirty();
            }));
        } catch (RuntimeException failure) { cloud$quarantineImport("Upload " + operation + " requires review"); sink.uncertain(operation, failure); throw failure; }
    }
    /** Defer until the native slot click has finished its temporary clear/restore probes. */
    void scheduleInboxUpload(ServerPlayer player) {
        inboxActor = inbox.getStackInSlot(0).isEmpty() ? null : player.getUUID(); markDirty();
    }
    /** AE management menu owns the carried original; stage it in the existing durable inbox/WAL. */
    public boolean acceptTerminalOriginal(ServerPlayer player, appeng.api.networking.IGrid grid) {
        if (!networkAvailable(grid) || !CloudLibraryService.authorized(player,libraryId,this)
                || inboxUpload!=null || !inbox.getStackInSlot(0).isEmpty()) return false;
        var carried=player.containerMenu.getCarried();
        if (!CloudTerminalUpload.processingOriginal(carried,getLevel())) return false;
        var original=carried.copyWithCount(1);
        CloudPatternStore.get(serverLevel()).library(libraryId).requireUniqueOriginal(original);
        inbox.setStackInSlot(0,original); carried.shrink(1); player.containerMenu.setCarried(carried);
        scheduleInboxUpload(player); uploadInbox(player); return true;
    }
    private void uploadCarried(ServerPlayer player) {
        if (inboxUpload != null) { diagnostic = "gtl_enhancedcore.cloud.status.uploading"; return; }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) return;
        ItemStack original = carried.copyWithCount(1);
        if (!CloudTerminalUpload.processingOriginal(original, getLevel())) throw new IllegalArgumentException("Processing pattern required");
        CloudPatternStore.get(serverLevel()).library(libraryId).requireUniqueOriginal(original);
        if (!inbox.getStackInSlot(0).isEmpty()) { diagnostic = "gtl_enhancedcore.cloud.status.inbox_busy"; return; }
        inbox.setStackInSlot(0, original); carried.shrink(1); player.containerMenu.setCarried(carried);
        scheduleInboxUpload(player);
        // This is a dedicated menu action, outside native Slot.set/getMaxStackSize.
        uploadInbox(player);
        player.containerMenu.broadcastChanges();
    }
    private void withdrawPattern(ServerPlayer player, CompoundTag payload, boolean toInventory) {
        var store = CloudPatternStore.get(serverLevel()); var library = store.library(libraryId);
        UUID id = UUID.fromString(payload.getString("selected")); var entry = library.entry(id);
        if (CloudNetworkPatternImport.entryLocked(libraryId, id)) return;
        if (toInventory ? player.getInventory().getFreeSlot() < 0 : !player.containerMenu.getCarried().isEmpty()) {
            diagnostic = "gtl_enhancedcore.cloud.status.inventory_full"; return;
        }
        UUID operation = UUID.randomUUID(); ItemStack output = store.extractFromCore(libraryId, player.getUUID(), id, operation);
        store.checkpoint();
        if (output.isEmpty()) return;
        if (toInventory && !player.getInventory().add(output)) { store.needsReview(libraryId, player.getUUID(), operation); store.checkpoint(); return; }
        if (!toInventory) player.containerMenu.setCarried(output);
        store.acknowledge(libraryId, player.getUUID(), operation); store.checkpoint();
        diagnostic = "gtl_enhancedcore.cloud.status.ready"; player.containerMenu.broadcastChanges();
    }
    public boolean selectImportSource(ServerPlayer player, MEPatternBufferPartMachine source) {
        if (!CloudLibraryService.authorized(player, libraryId, this) || player.distanceToSqr(getPos().getX()+.5,getPos().getY()+.5,getPos().getZ()+.5) > 64
                || source.getLevel() != getLevel() || getPos().distSqr(source.getPos()) > 64 || !CloudImportSourceAddress.authorized(player, source)) return false;
        selectedSource = source.getPos().asLong(); selectedSourceIdentity = ((CloudImportSourceAccess)source).cloud$sourceIdentity();
        hasSource = true; importingPlayer = null; importCursor = 0; markDirty(); return true;
    }
    private MEPatternBufferPartMachine source() {
        if (hasSource) {
            var loaded = loadedBuffer(BlockPos.of(selectedSource));
            return loaded instanceof CloudImportSourceAccess access && access.cloud$sourceIdentity().equals(selectedSourceIdentity) ? loaded : null;
        }
        for (Direction direction : Direction.values()) for (int distance = 1; distance <= 7; distance++) {
            var candidate = loadedBuffer(getPos().relative(direction, distance)); if (candidate != null) return candidate;
        }
        return null;
    }
    private MEPatternBufferPartMachine loadedBuffer(BlockPos pos) {
        var chunk = serverLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        var entity = chunk.getBlockEntity(pos);
        return entity instanceof IMachineBlockEntity holder && holder.getMetaMachine() instanceof MEPatternBufferPartMachine buffer ? buffer : null;
    }
    private CloudBufferImport.Result importPage(ServerPlayer player, MEPatternBufferPartMachine source, boolean simulate) {
        return CloudBufferImport.transfer(player, source, (actor, endpoint) -> {
            var sourceNode = endpoint.getMainNode().getNode();
            var sourceOwner = sourceNode == null ? null : sourceNode.getOwningPlayerProfileId();
            return sourceOwner != null && CloudLibraryService.sameTeam(sourceOwner, actor.getUUID())
                    && CloudLibraryService.claimAllows(actor, actor.serverLevel(), endpoint.getPos());
        }, CloudImportJournal.sink(serverLevel(), libraryId, player.getUUID()), simulate ? 0 : importCursor, 64, simulate);
    }
    private void importTick() {
        online = serviceGrid() != null;
        connected = serviceGrid() != null && !cloud$importQuarantined();
        CloudNetworkPatternImport.tick(this);
        if (inboxActor != null && !cloud$importQuarantined() && isFormed()) {
            ServerPlayer actor = serverLevel().getServer().getPlayerList().getPlayer(inboxActor);
            if (actor != null && openingAuthorized(actor)) {
                try { uploadInbox(actor); }
                catch (RuntimeException failure) { inboxActor = null; markDirty(); recordFailure(failure); }
            }
        }
        if (importingPlayer == null || serverLevel().getGameTime() % 10 != 0) return;
        ServerPlayer player = serverLevel().getServer().getPlayerList().getPlayer(importingPlayer);
        var source = source();
        if (player == null || !menuAuthorized(player) || source == null) { importingPlayer = null; return; }
        try {
            var result = importPage(player, source, false); importCursor = result.nextSlot();
            recordImportResult(result, false, true);
            if (result.rejection() != CloudBufferImport.Rejection.NONE || result.unresolved() != null || importCursor >= source.getPatternInventory().getSlots()) importingPlayer = null;
        } catch (RuntimeException failure) { importingPlayer = null; recordFailure(failure); }
    }
    @Override public IItemTransfer cloudPatternItemCatalysts(Player player, String entryId) {
        return CloudPatternInventories.items(this, player, libraryId, entryId,
                () -> player instanceof ServerPlayer server && menuAuthorized(server),
                () -> player instanceof ServerPlayer server && viewAuthorized(server));
    }
    @Override public IFluidTransfer cloudPatternFluidCatalysts(Player player, String entryId) {
        return CloudPatternInventories.fluids(this, player, libraryId, entryId,
                () -> player instanceof ServerPlayer server && menuAuthorized(server),
                () -> player instanceof ServerPlayer server && viewAuthorized(server));
    }
    @Override public IItemTransfer cloudItemCatalysts(Player player) { return CloudLibraryInventories.items(this, player, false); }
    @Override public IItemTransfer cloudImportInventory(Player player) { return CloudLibraryInventories.items(this, player, true); }
    @Override public IFluidTransfer cloudFluidCatalysts(Player player) { return CloudLibraryInventories.fluids(this, player); }
    @Override public UUID cloud$sourceIdentity() { if (!importMarkers.hasUUID("identity")) { importMarkers.putUUID("identity", UUID.randomUUID()); markDirty(); } return importMarkers.getUUID("identity"); }
    @Override public boolean cloud$processed(UUID tx) { return importMarkers.getCompound("processed").getBoolean(tx.toString()); }
    @Override public void cloud$markProcessed(UUID tx) { CompoundTag processed = importMarkers.getCompound("processed"); processed.putBoolean(tx.toString(), true); importMarkers.put("processed", processed); markDirty(); }
    @Override public boolean cloud$importQuarantined() { return importMarkers.contains("quarantine"); }
    @Override public void cloud$quarantineImport(String reason) { importMarkers.putString("quarantine", reason == null ? "Uncertain import" : reason); markDirty(); }
    @Override public CompoundTag cloud$saveImportMarkers() { return importMarkers.copy(); }
    @Override public void cloud$loadImportMarkers(CompoundTag tag) { importMarkers = tag.copy(); }
}
