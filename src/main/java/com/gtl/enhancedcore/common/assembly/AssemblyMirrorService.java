package com.gtl.enhancedcore.common.assembly;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartHost;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.item.AssemblyManagerItem;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferProxyPartMachine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;

/** Resolves the tool's saved anchor against loaded AE nodes, never against a global machine index. */
public final class AssemblyMirrorService {
    public static final String SAVE_KEY = "AssemblyMirror";
    private AssemblyMirrorService() {}

    private record Binding(ResourceLocation dimension, BlockPos anchor, Direction face, BlockPos selected, String selectedName) {}
    private record Network(Binding binding, IGridNode node, List<MEPatternBufferPartMachine> sources) {
        boolean online() { return node != null && node.isActive(); }
    }

    public static boolean holding(ServerPlayer player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem() instanceof AssemblyManagerItem;
    }

    private static Binding binding(ServerPlayer player, InteractionHand hand) {
        CompoundTag root = player.getItemInHand(hand).getTag();
        if (root == null || !root.contains(SAVE_KEY, Tag.TAG_COMPOUND))
            return new Binding(player.serverLevel().dimension().location(), null, Direction.UP, null, "");
        CompoundTag tag = root.getCompound(SAVE_KEY);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null || !tag.contains("anchor", Tag.TAG_LONG))
            return new Binding(player.serverLevel().dimension().location(), null, Direction.UP, null, "");
        return new Binding(dimension, BlockPos.of(tag.getLong("anchor")), Direction.from3DDataValue(tag.getByte("face")),
                tag.contains("selectedSource", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("selectedSource")) : null,
                tag.getString("selectedName"));
    }

    private static CompoundTag writable(ServerPlayer player, InteractionHand hand) {
        CompoundTag root = player.getItemInHand(hand).getOrCreateTag();
        if (!root.contains(SAVE_KEY, Tag.TAG_COMPOUND)) root.put(SAVE_KEY, new CompoundTag());
        return root.getCompound(SAVE_KEY);
    }

    private static IGridNode node(ServerLevel level, BlockPos pos, Direction face) {
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return null;
        MetaMachine machine = loadedMachine(level, pos);
        if (machine instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEIOPartMachine meMachine
                && meMachine.getMainNode().getNode() != null) return meMachine.getMainNode().getNode();
        var entity = level.getBlockEntity(pos);
        if (entity instanceof IPartHost host) {
            var part = host.getPart(face);
            if (part != null && part.getGridNode() != null) return part.getGridNode();
        }
        return GridHelper.getExposedNode(level, pos, face);
    }

    private static Network resolve(ServerPlayer player, InteractionHand hand) {
        Binding binding = binding(player, hand);
        IGridNode node = binding.anchor == null || !binding.dimension.equals(player.serverLevel().dimension().location())
                ? null : node(player.serverLevel(), binding.anchor, binding.face);
        List<MEPatternBufferPartMachine> sources = new ArrayList<>();
        if (node != null) {
            IGrid grid = node.getGrid();
            for (IGridNode member : grid.getNodes()) {
                if (member.getOwner() instanceof MEPatternBufferPartMachine source && source.getLevel() == player.serverLevel()
                        && source.getMainNode().getNode() == member
                        && loadedMachine(player.serverLevel(), source.getPos()) == source) sources.add(source);
            }
        }
        sources.sort(Comparator.comparingLong(source -> source.getPos().asLong()));
        return new Network(binding, node, sources);
    }

    private static MetaMachine loadedMachine(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : MetaMachine.getMachine(chunk, pos);
    }

    private static String name(MEPatternBufferPartMachine source) {
        String name = source.getCustomName();
        return name == null ? "" : name;
    }

    private static Component displayName(MEPatternBufferPartMachine source) {
        String name = name(source);
        return name.isBlank() ? Component.translatable("gtl_enhancedcore.assembly.unnamed") : Component.literal(name);
    }

    public static AssemblyMirrorNetwork.View view(ServerPlayer player, InteractionHand hand, Component message) {
        Network network = resolve(player, hand);
        var binding = network.binding;
        List<AssemblyMirrorNetwork.Source> sources = new ArrayList<>();
        List<AssemblyMirrorNetwork.Mirror> mirrors = new ArrayList<>();
        for (var source : network.sources) {
            String name = name(source);
            boolean superBuffer = source instanceof MESuperPatternBufferPartMachine;
            var positions = source.getBoundProxyPositions();
            int mirrorStart = mirrors.size();
            for (BlockPos pos : positions) {
                var chunk = player.serverLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                boolean loaded = chunk != null;
                var machine = loaded ? MetaMachine.getMachine(chunk, pos) : null;
                if (loaded && (!(machine instanceof MEPatternBufferProxyPartMachine proxy)
                        || !source.getPos().equals(proxy.getBufferPos()))) continue;
                mirrors.add(new AssemblyMirrorNetwork.Mirror(pos, source.getPos(), name,
                        loaded ? machine instanceof MESuperPatternBufferProxyPartMachine : superBuffer, loaded));
            }
            int count = mirrors.size() - mirrorStart;
            sources.add(new AssemblyMirrorNetwork.Source(source.getPos(), name, superBuffer, count,
                    IvBuffers.isolated(source) ? "iv_exclusive" : !source.getMainNode().isActive() ? "source_offline" : ""));
        }
        mirrors.sort(Comparator.comparingLong(mirror -> mirror.pos().asLong()));
        if (message == null) message = text(binding.anchor == null ? "choose_network"
                : !binding.dimension.equals(player.serverLevel().dimension().location()) ? "same_dimension"
                : !network.online() ? "network_offline" : "ready");
        String selectedName = network.sources.stream().filter(source -> source.getPos().equals(binding.selected))
                .findFirst().map(AssemblyMirrorService::name).filter(value -> !value.isBlank()).orElse(binding.selectedName);
        return new AssemblyMirrorNetwork.View(hand, player.serverLevel().dimension().location(), binding.anchor,
                binding.anchor == null ? "" : coordinates(binding.anchor), network.online(), binding.selected,
                selectedName, List.copyOf(sources), List.copyOf(mirrors), message);
    }

    private static String coordinates(BlockPos pos) { return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    static Component text(String key, Object... args) { return Component.translatable("gtl_enhancedcore.assembly.mirror." + key, args); }
    private static boolean mayEdit(ServerPlayer player, BlockPos pos) {
        return !player.isSpectator() && player.isAlive() && player.mayBuild() && player.serverLevel().mayInteract(player, pos);
    }

    public static Component bindNetwork(ServerPlayer player, InteractionHand hand, BlockPos pos, Direction face) {
        if (!mayEdit(player, pos) || player.distanceToSqr(pos.getCenter()) > 64) return text("blocked");
        IGridNode node = node(player.serverLevel(), pos, face);
        if (node == null) return text("not_network");
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", player.serverLevel().dimension().location().toString());
        tag.putLong("anchor", pos.asLong()); tag.putByte("face", (byte) face.get3DDataValue());
        player.getItemInHand(hand).getOrCreateTag().put(SAVE_KEY, tag);
        player.getInventory().setChanged();
        return text("network_selected");
    }

    public static boolean isMirror(ServerPlayer player, BlockPos pos) {
        return loadedMachine(player.serverLevel(), pos) instanceof MEPatternBufferProxyPartMachine;
    }

    public static Component bindMirror(ServerPlayer player, InteractionHand hand, BlockPos pos, boolean requireKnown) {
        if (!mayEdit(player, pos)) return text("blocked");
        if (!requireKnown && player.distanceToSqr(pos.getCenter()) > 64) return text("blocked");
        Network network = resolve(player, hand);
        if (!network.binding.dimension.equals(player.serverLevel().dimension().location())) return text("same_dimension");
        if (!network.online()) return text("network_offline");
        if (!(loadedMachine(player.serverLevel(), pos) instanceof MEPatternBufferProxyPartMachine proxy)) return text("mirror_unloaded");
        if (requireKnown && !known(network, proxy)) return text("invalid_mirror");
        var source = network.sources.stream().filter(candidate -> candidate.getPos().equals(network.binding.selected)).findFirst().orElse(null);
        if (source == null) return text("choose_source");
        if (IvBuffers.isolated(source)) return text("iv_exclusive");
        if (!source.getMainNode().isActive()) return text("source_offline");
        if (proxy instanceof MESuperPatternBufferProxyPartMachine && !(source instanceof MESuperPatternBufferPartMachine)) return text("type_mismatch");
        var oldSource = proxy.getBufferPos() == null ? null : loadedMachine(player.serverLevel(), proxy.getBufferPos());
        proxy.setBuffer(source.getPos());
        proxy.markDirty(); source.markDirty();
        if (oldSource != null && oldSource != source) oldSource.markDirty();
        return source.getPos().equals(proxy.getBufferPos()) && proxy.getBuffer() == source
                ? text("bound", coordinates(pos), displayName(source)) : text("bind_failed");
    }

    private static boolean known(Network network, MEPatternBufferProxyPartMachine proxy) {
        return network.sources.stream().anyMatch(source -> source.getPos().equals(proxy.getBufferPos())
                && source.getBoundProxyPositions().contains(proxy.getPos()));
    }

    public static Component action(ServerPlayer player, InteractionHand hand, int action, BlockPos pos) {
        if (action == AssemblyMirrorNetwork.CLEAR_NETWORK) {
            CompoundTag root = player.getItemInHand(hand).getTag();
            if (root != null) root.remove(SAVE_KEY);
            player.getInventory().setChanged();
            return text("network_cleared");
        }
        if (action == AssemblyMirrorNetwork.REBIND) return bindMirror(player, hand, pos, true);
        Network network = resolve(player, hand);
        if (!network.binding.dimension.equals(player.serverLevel().dimension().location())) return text("same_dimension");
        if (!network.online()) return text("network_offline");
        if (action == AssemblyMirrorNetwork.SELECT_SOURCE) {
            var source = network.sources.stream().filter(candidate -> candidate.getPos().equals(pos)).findFirst().orElse(null);
            if (source == null) return text("invalid_source");
            if (IvBuffers.isolated(source)) return text("iv_exclusive");
            if (!source.getMainNode().isActive()) return text("source_offline");
            if (!mayEdit(player, pos)) return text("blocked");
            CompoundTag tag = writable(player, hand);
            tag.putLong("selectedSource", pos.asLong()); tag.putString("selectedName", name(source));
            player.getInventory().setChanged();
            return text("source_selected", displayName(source));
        }
        if (action == AssemblyMirrorNetwork.UNBIND) {
            if (!mayEdit(player, pos)) return text("blocked");
            if (!(loadedMachine(player.serverLevel(), pos) instanceof MEPatternBufferProxyPartMachine proxy)) return text("mirror_unloaded");
            if (!known(network, proxy)) return text("invalid_mirror");
            var oldSource = proxy.getBuffer();
            proxy.setBuffer(null); proxy.markDirty();
            if (oldSource != null) oldSource.markDirty();
            return proxy.getBufferPos() == null ? text("unbound", coordinates(pos)) : text("bind_failed");
        }
        return text("invalid_action");
    }
}
