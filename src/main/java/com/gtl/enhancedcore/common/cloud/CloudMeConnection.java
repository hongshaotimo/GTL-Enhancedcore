package com.gtl.enhancedcore.common.cloud;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData;

/** Restore only the exact saved membership; no network scan, new binding or chunk ticket. */
public final class CloudMeConnection {
    private CloudMeConnection() {}
    public static void restore(MetaMachine machine) {
        if (!(machine.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        var connected = (IGridConnectedMachine) machine;
        if (connected.getMainNode().getNode() == null) return;
        var member = new WirelessAeSavedData.MemberKey(GlobalPos.of(level.dimension(), machine.getPos()), null);
        var frequency = WirelessAeSavedData.get(level.getServer()).getExactMemberNetwork(member);
        if (frequency == null) return;
        var core = WirelessAeNetworkRuntime.getLoadedCore(level.getServer(), frequency);
        var bridge = core == null ? null : WirelessAeNetworkRuntime.findBridgeNode(core);
        if (bridge == null || connected.getMainNode().getGrid() == bridge.getGrid()) return;
        WirelessAeNetworkRuntime.connectMemberNow(level.getServer(), frequency, member);
    }
    /** A selected wireless core is the saved anchor; an arbitrary GT machine need not stay loaded. */
    static appeng.api.networking.IGrid selectedGrid(CloudPatternLibraryMachine machine) {
        var level = machine.serverLevel();
        var frequency = WirelessAeSavedData.get(level.getServer()).getMemberNetwork(GlobalPos.of(level.dimension(), machine.getPos()));
        if (frequency == null) return null;
        var core = WirelessAeNetworkRuntime.getLoadedCore(level.getServer(), frequency);
        var bridge = core == null ? null : WirelessAeNetworkRuntime.findBridgeNode(core);
        return bridge != null && bridge.isActive() ? bridge.getGrid() : null;
    }
}
