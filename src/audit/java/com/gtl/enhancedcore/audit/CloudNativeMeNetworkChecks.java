package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;

/** Run on loaded server fixtures after their grid node initialization task. Does not select a network. */
public final class CloudNativeMeNetworkChecks {
    private CloudNativeMeNetworkChecks() {}
    public static void run(CloudPatternLibraryMachine library, CloudPatternBufferMachine buffer, List<String> checks) {
        var level = (ServerLevel)library.getLevel();
        require(level.getServer().isSameThread(), "native_me_server_thread", checks);
        require(WirelessAeNetworkRuntime.isWirelessMeTarget(level, library.getPos()), "library_native_target", checks);
        require(WirelessAeNetworkRuntime.isWirelessMeTarget(buffer.getLevel(), buffer.getPos()), "buffer_native_target", checks);
        require(WirelessAeNetworkRuntime.canBindAsWirelessTarget(level, library.getPos()), "library_node_discoverable", checks);
        require(WirelessAeNetworkRuntime.canBindAsWirelessTarget((ServerLevel)buffer.getLevel(), buffer.getPos()), "buffer_node_discoverable", checks);
        require(library.meNetworkConnected() == library.getMainNode().isActive(), "library_me_flag_real_node", checks);
        require(buffer.meNetworkConnected() == buffer.getMainNode().isActive(), "buffer_me_flag_real_node", checks);
        var expected = library.owner() != null && library.libraryId() != null && library.getMainNode().isActive()
                ? library.getMainNode().getGrid() : null;
        require(library.serviceGrid() == expected, "library_service_follows_native_node", checks);
    }
    private static void require(boolean condition, String name, List<String> checks) {
        if (!condition) throw new AssertionError(name);
        checks.add(name);
    }
}
