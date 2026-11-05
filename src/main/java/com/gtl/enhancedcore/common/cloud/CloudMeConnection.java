package com.gtl.enhancedcore.common.cloud;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.capabilities.Capabilities;
import appeng.me.InWorldGridNode;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessNetworkCoreBlockEntity;

import java.util.UUID;

/** Restore only exact saved memberships; passive core/bridge reads never request a chunk. */
public final class CloudMeConnection {
    private CloudMeConnection() {}
    public static void restore(MetaMachine machine) {
        if (!(machine.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        var connected = (IGridConnectedMachine) machine;
        if (connected.getMainNode().getNode() == null) return;
        var member = new WirelessAeSavedData.MemberKey(GlobalPos.of(level.dimension(), machine.getPos()), null);
        var frequency = WirelessAeSavedData.get(level.getServer()).getExactMemberNetwork(member);
        if (frequency == null) return;
        var core = loadedCore(level.getServer(), frequency);
        var bridge = core == null ? null : loadedBridge(core);
        if (bridge == null || connected.getMainNode().getGrid() == bridge.getGrid()) return;
        WirelessAeNetworkRuntime.connectMemberNow(level.getServer(), frequency, member);
    }
    record SelectedNetwork(boolean selected, appeng.api.networking.IGrid grid) {}
    /** A saved but unavailable member remains selected, so callers cannot fall back to another wired grid. */
    static SelectedNetwork selectedNetwork(MetaMachine machine) {
        var level = (ServerLevel) machine.getLevel();
        var member = new WirelessAeSavedData.MemberKey(GlobalPos.of(level.dimension(), machine.getPos()), null);
        var frequency = WirelessAeSavedData.get(level.getServer()).getExactMemberNetwork(member);
        if (frequency == null) return new SelectedNetwork(false, null);
        var core = loadedCore(level.getServer(), frequency);
        var bridge = core == null ? null : loadedBridge(core);
        return new SelectedNetwork(true, bridge != null && bridge.isActive() ? bridge.getGrid() : null);
    }
    /** A selected wireless endpoint cannot publish or accept against an unrelated wired grid. */
    static boolean selectedNetworkAvailable(MetaMachine machine) {
        var selected = selectedNetwork(machine);
        return !selected.selected() || selected.grid() != null
                && selected.grid() == ((IGridConnectedMachine) machine).getMainNode().getGrid();
    }

    private static WirelessNetworkCoreBlockEntity loadedCore(MinecraftServer server, UUID frequency) {
        var position = WirelessAeSavedData.get(server).getCore(frequency);
        if (position == null) return null;
        var level = server.getLevel(position.dimension());
        var chunk = level == null ? null : level.getChunkSource().getChunkNow(position.pos().getX() >> 4, position.pos().getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(position.pos()) instanceof WirelessNetworkCoreBlockEntity core
                && !core.isRemoved() && frequency.equals(core.getFrequency()) ? core : null;
    }

    /** AE2 15.4.10 node-host/capability and exposed-side rules, using only loaded chunk contents. */
    private static IGridNode loadedBridge(WirelessNetworkCoreBlockEntity core) {
        var own = core.getActionableNode();
        var side = core.getConnectionSide();
        try {
            var connection = own == null ? null : own.getInWorldConnections().get(side);
            var bridge = connection == null ? null : connection.getOtherSide(own);
            if (usableBridge(own, bridge)) return bridge;
            var level = (ServerLevel) core.getLevel();
            var position = core.getBlockPos().relative(side);
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            var blockEntity = chunk == null ? null : chunk.getBlockEntity(position);
            var host = blockEntity instanceof IInWorldGridNodeHost direct ? direct : blockEntity == null ? null
                    : blockEntity.getCapability(Capabilities.IN_WORLD_GRID_NODE_HOST).orElse(null);
            bridge = host == null ? null : host.getGridNode(side.getOpposite());
            return bridge instanceof InWorldGridNode inWorld && inWorld.isExposedOnSide(side.getOpposite())
                    && usableBridge(own, bridge) ? bridge : null;
        } catch (RuntimeException unavailable) { return null; }
    }

    private static boolean usableBridge(IGridNode own, IGridNode candidate) {
        return candidate != null && candidate != own && !(candidate.getOwner() instanceof WirelessNetworkCoreBlockEntity)
                && candidate.getGrid() != null;
    }
}
