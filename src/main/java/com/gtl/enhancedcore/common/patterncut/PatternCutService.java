package com.gtl.enhancedcore.common.patterncut;

import appeng.api.networking.crafting.ICraftingProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferMethods;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferTransfer;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvSlotAccess;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.storage.LevelResource;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The screen selects a record and mode; right-click selects the destination. */
public final class PatternCutService {
    private static final Logger LOG = LoggerFactory.getLogger("GTL-Enhancedcore/PatternCut");
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final class Session { UUID selected; boolean smart; }
    private PatternCutService() {}
    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player, unused -> new Session());
    }
    public static PatternCutStore store(ServerPlayer player) {
        return new PatternCutStore(player.server.getWorldPath(LevelResource.ROOT)
                .resolve("data/gtl_enhancedcore/pattern_cut").resolve(player.getUUID().toString()));
    }
    public static Component text(String key, Object... args) {
        return Component.translatable("gtl_enhancedcore.pattern_cut." + key, args);
    }
    private static boolean holding(ServerPlayer player, InteractionHand hand) {
        return PatternCutNetwork.isTool(player.getItemInHand(hand)) && player.isAlive() && !player.isSpectator();
    }
    private static boolean mayEdit(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        if (!player.mayBuild() || !player.serverLevel().mayInteract(player, pos)) return false;
        return !FTBChunksAPI.api().isManagerLoaded() || !FTBChunksAPI.api().getManager()
                .shouldPreventInteraction(player, hand, pos, Protection.INTERACT_BLOCK, null);
    }
    private static MetaMachine loaded(ServerPlayer player, BlockPos pos) {
        var chunk = player.serverLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : MetaMachine.getMachine(chunk, pos);
    }
    private static boolean nearby(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getCenter()) <= 64;
    }
    private static List<MEPatternBufferPartMachine> targets(ServerPlayer player, InteractionHand hand, BlockPos clicked) {
        if (!nearby(player, clicked) || !mayEdit(player, hand, clicked)) return List.of();
        MetaMachine machine = loaded(player, clicked);
        if (machine instanceof MEPatternBufferPartMachine buffer) return List.of(buffer);
        if (!(machine instanceof WorkableMultiblockMachine controller) || !controller.isFormed()) return List.of();
        return controller.getParts().stream().filter(part -> part instanceof MEPatternBufferPartMachine)
                .map(part -> (MEPatternBufferPartMachine) part)
                .filter(buffer -> loaded(player, buffer.getPos()) == buffer
                        && buffer.getControllers().stream().anyMatch(owner -> owner.self() == controller))
                .sorted(Comparator.comparingLong(buffer -> buffer.getPos().asLong())).toList();
    }
    private static int patternCount(MEPatternBufferPartMachine buffer) {
        int count = 0;
        for (int i = 0; i < buffer.getPatternInventory().getSlots(); i++)
            if (!buffer.getPatternInventory().getStackInSlot(i).isEmpty()) count++;
        return count;
    }
    private static boolean idle(MEPatternBufferPartMachine buffer) {
        var state = IvBuffers.state(buffer);
        if (state != null && !state.healthy()) return false;
        if (buffer.isBusy() || !buffer.getBuffer().isEmpty() || !IvBuffers.jobs(buffer).isEmpty()) return false;
        for (Object slot : buffer.getInternalInventory()) if (((IvSlotAccess) slot).iv$hasStock()) return false;
        return true;
    }
    private static List<PatternCutStore.Entry> records(ServerPlayer player) throws IOException {
        return store(player).list().stream().filter(entry -> entry.patterns() > 0).toList();
    }
    public static PatternCutNetwork.View view(ServerPlayer player, InteractionHand hand, Component message) {
        Session session = session(player);
        List<PatternCutStore.Entry> entries = List.of();
        try {
            if (holding(player, hand)) { importHeldPayload(player, hand); entries = records(player); }
        } catch (IOException failure) {
            LOG.error("Unable to read pattern cuts for {}", player.getUUID(), failure); message = text("file_error");
        }
        if (session.selected == null || entries.stream().noneMatch(entry -> entry.id().equals(session.selected)))
            session.selected = entries.isEmpty() ? null : entries.getFirst().id();
        return new PatternCutNetwork.View(hand, session.selected, session.smart, entries,
                message == null ? text("hint") : message);
    }
    public static PatternCutNetwork.View open(ServerPlayer player, InteractionHand hand) { return view(player, hand, null); }
    public static PatternCutNetwork.View mode(ServerPlayer player, InteractionHand hand, boolean smart) {
        if (holding(player, hand)) session(player).smart = smart;
        return view(player, hand, null);
    }
    public static PatternCutNetwork.View select(ServerPlayer player, InteractionHand hand, UUID id) {
        try { if (holding(player, hand) && records(player).stream().anyMatch(entry -> entry.id().equals(id))) session(player).selected = id; }
        catch (IOException failure) { return view(player, hand, text("file_error")); }
        return view(player, hand, null);
    }
    private static String cleanName(String name) { return name == null ? "" : name; }

    public static PatternCutNetwork.View cut(ServerPlayer player, InteractionHand hand, MEPatternBufferPartMachine buffer) {
        if (!holding(player, hand) || loaded(player, buffer.getPos()) != buffer || !nearby(player, buffer.getPos())
                || !mayEdit(player, hand, buffer.getPos())) return view(player, hand, text("blocked"));
        // Empty cuts must not clear shared storage or publish a zero-pattern record.
        if (patternCount(buffer) == 0) return view(player, hand, text("no_data"));
        try {
            importHeldPayload(player, hand);
            if (!IvBufferTransfer.allowCut(buffer)) return view(player, hand, Component.translatable(IvBuffers.state(buffer).message));
            if (!idle(buffer)) return view(player, hand, text("busy"));
            for (int i = 1; i < buffer.getSharedCircuitInventory().getSlots(); i++)
                if (!buffer.getSharedCircuitInventory().getStackInSlot(i).isEmpty()) return view(player, hand, text("extra_circuits"));
            for (var pos : buffer.getBoundProxyPositions()) {
                if (loaded(player, pos) == null) return view(player, hand, text("proxy_unloaded"));
                if (!mayEdit(player, hand, pos)) return view(player, hand, text("proxy_blocked"));
            }
            UUID id = UUID.randomUUID();
            var store = store(player);
            CompoundTag cut = buffer.cutToTag(new CompoundTag());
            if (!cut.contains("cut") || cut.getCompound("cut").isEmpty()) return view(player, hand, text("cut_failed"));
            CompoundTag payload = cut.getCompound("cut");
            try { store.put(id, payload); }
            catch (IOException storageFailure) {
                if (!buffer.pasteFromTag(payload))
                    throw new IllegalStateException("Cannot restore unsaved pattern cut at " + buffer.getPos(), storageFailure);
                refresh(buffer);
                throw storageFailure;
            }
            refresh(buffer); session(player).selected = id;
            return view(player, hand, text("cut_ok", cleanName(buffer.getCustomName()), payload.getList("patterns", Tag.TAG_COMPOUND).size()));
        } catch (IOException failure) {
            LOG.error("Unable to save pattern cut for {}", player.getUUID(), failure);
            return view(player, hand, text("file_error"));
        }
    }
    private static void refresh(MEPatternBufferPartMachine buffer) {
        var methods = (IvBufferMethods) buffer;
        for (int i = 0; i < buffer.getPatternInventory().getSlots(); i++) methods.iv$patternChanged(i);
        methods.iv$refreshPatterns(); ICraftingProvider.requestUpdate(buffer.getMainNode()); buffer.markDirty();
    }
    private static void importHeldPayload(ServerPlayer player, InteractionHand hand) throws IOException {
        var stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getTagElement("GT.Behaviours");
        if (tag == null || tag.getCompound("cut").isEmpty()) return;
        CompoundTag payload = tag.getCompound("cut").copy();
        if (payload.getList("patterns", Tag.TAG_COMPOUND).isEmpty()) return;
        if (!payload.contains("dimension")) payload.putString("dimension", player.serverLevel().dimension().location().toString());
        if (!payload.contains("bufferPos")) payload.putLong("bufferPos", player.blockPosition().asLong());
        UUID id = UUID.randomUUID(); store(player).put(id, payload);
        tag.remove("cut"); player.getInventory().setChanged(); session(player).selected = id;
    }
    private static Component pasteGroup(ServerPlayer player, InteractionHand hand, List<PatternCutStore.Entry> group,
                                        MEPatternBufferPartMachine buffer) throws IOException {
        if (!mayEdit(player, hand, buffer.getPos())) return text("blocked");
        if (!idle(buffer)) return text("busy");
        var store = store(player);
        var payloads = new ArrayList<CompoundTag>();
        var proxies = new ArrayList<MEPatternBufferProxyPartMachine>();
        for (var entry : group) {
            CompoundTag payload = store.read(entry.id()); payloads.add(payload);
            if (payload.getLongArray("proxies").length > 0
                    && !payload.getString("dimension").equals(player.serverLevel().dimension().location().toString())) return text("proxy_dimension");
            if (payload.getLongArray("proxies").length > 0 && IvBuffers.isolated(buffer))
                return Component.translatable("gtl_enhancedcore.diagnostic.iv_paste");
            for (long value : payload.getLongArray("proxies")) {
                BlockPos pos = BlockPos.of(value);
                if (!(loaded(player, pos) instanceof MEPatternBufferProxyPartMachine proxy)) return text("proxy_unloaded");
                if (!mayEdit(player, hand, pos)) return text("proxy_blocked");
                proxies.add(proxy);
            }
        }
        var plan = PatternCutPaste.prepare(buffer, payloads);
        if (!plan.ready()) return Component.translatable(plan.reason());
        var ids = group.stream().map(PatternCutStore.Entry::id).toList();
        store.takeAll(ids);
        plan.apply();
        for (var proxy : proxies) proxy.setBuffer(buffer.getPos());
        refresh(buffer);
        for (var id : ids) {
            try { store.finish(id); }
            catch (IOException failure) { LOG.warn("Unable to remove consumed pattern-cut file {}", id, failure); }
        }
        return null;
    }
    private static PatternCutNetwork.View direct(ServerPlayer player, InteractionHand hand, MEPatternBufferPartMachine buffer) {
        try {
            var chosen = records(player).stream().filter(entry -> entry.id().equals(session(player).selected)).findFirst();
            if (chosen.isEmpty()) return view(player, hand, text("choose_record"));
            Component failure = pasteGroup(player, hand, List.of(chosen.get()), buffer);
            return view(player, hand, failure == null ? text("paste_ok", cleanName(buffer.getCustomName())) : failure);
        } catch (IOException failure) {
            LOG.error("Unable to paste pattern cut for {}", player.getUUID(), failure); return view(player, hand, text("file_error"));
        }
    }
    private static PatternCutNetwork.View smart(ServerPlayer player, InteractionHand hand, BlockPos clicked) {
        try {
            var records = records(player);
            if (records.isEmpty()) return view(player, hand, text("empty"));
            var candidates = targets(player, hand, clicked);
            if (candidates.isEmpty()) return view(player, hand, text("smart_no_target"));
            var names = candidates.stream().map(buffer -> cleanName(buffer.getCustomName())).toList();
            Map<String, List<PatternCutStore.Entry>> groups = new LinkedHashMap<>();
            for (var entry : records) groups.computeIfAbsent(PatternCutNames.groupKey(entry.name()), unused -> new ArrayList<>()).add(entry);
            int pasted = 0;
            for (var group : groups.values()) {
                var best = PatternCutNames.best(group.getFirst().name(), names);
                for (int index : best) {
                    if (pasteGroup(player, hand, group, candidates.get(index)) == null) { pasted += group.size(); break; }
                }
            }
            return view(player, hand, text("smart_result", pasted, records.size() - pasted));
        } catch (IOException failure) {
            LOG.error("Unable to smart paste for {}", player.getUUID(), failure); return view(player, hand, text("file_error"));
        }
    }
    public static void use(ServerPlayer player, InteractionHand hand, BlockPos clicked) {
        if (!holding(player, hand)) return;
        MetaMachine machine = loaded(player, clicked);
        PatternCutNetwork.View result;
        if (machine instanceof MEPatternBufferPartMachine buffer && player.isShiftKeyDown()) result = cut(player, hand, buffer);
        else {
            var current = view(player, hand, null);
            if (!nearby(player, clicked) || !mayEdit(player, hand, clicked)) result = view(player, hand, text("blocked"));
            else if (current.smart()) result = smart(player, hand, clicked);
            else if (machine instanceof MEPatternBufferPartMachine buffer) result = direct(player, hand, buffer);
            else result = view(player, hand, text("direct_target"));
        }
        PatternCutNetwork.sendView(player, result, false);
        player.displayClientMessage(result.message(), true);
    }
}
