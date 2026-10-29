package com.gtl.enhancedcore.audit;

import java.nio.file.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** New fixture-only entry; never reads or starts the player's world. */
@Mod("enhancedcore_audit")
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class CloudLongDispatchAuditBootstrap {
    public CloudLongDispatchAuditBootstrap() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) throws Exception {
        if (!Boolean.getBoolean("gtl.cloudLongDispatchAudit")) return;
        var previousTask = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-long-dispatch-20261027").toAbsolutePath().normalize();
        var currentTask = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-cpu-refill-20261028").toAbsolutePath().normalize();
        var task = Path.of(System.getProperty("gtl.cloudLongDispatchFixture", currentTask.resolve("fixture-verified").toString()))
                .toAbsolutePath().normalize().getParent();
        var expected = Path.of(System.getProperty("gtl.cloudLongDispatchFixture", task.resolve("forge-instance").toString())).toAbsolutePath().normalize();
        var world = event.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var repairTask = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-crash-inputs-20261028").toAbsolutePath().normalize();
        var instantTask = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-instant-upload-20261028").toAbsolutePath().normalize();
        var latencyTask = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-upload-latency-20261029").toAbsolutePath().normalize();
        var refundTask = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-refund-dissolution-20261029").toAbsolutePath().normalize();
        var integrationTask = Path.of("C:/Users/Administrator/.codex/worktrees/pr4-cloud-integration/GTL-Enhancedcore/_audit/pr4-integration-20261028").toAbsolutePath().normalize();
        if (!(task.equals(refundTask) || task.equals(latencyTask) || task.equals(integrationTask) || task.equals(previousTask) || task.equals(currentTask) || task.equals(repairTask) || task.equals(instantTask)) || !expected.getParent().equals(task)
                || !world.getParent().equals(expected) || !world.getFileName().toString().equals("cloud-long-world")
                || !Files.isRegularFile(expected.resolve("cloud-long.marker")))
            throw new IllegalStateException("Long dispatch audit refuses an unmarked or unrelated world: " + world);
        if (Boolean.getBoolean("gtl.cloudInstantReload")) CloudInstantRuntimeChecks.reload(event.getServer());
        else if (Boolean.getBoolean("gtl.cloudUploadLatencyReload")) CloudUploadLatencyRuntimeChecks.reload(event.getServer());
        else if (Boolean.getBoolean("gtl.cloudRefundDissolutionReload")) CloudRefundDissolutionRuntimeChecks.reload(event.getServer());
        else CloudPatternFlowRuntimeChecks.begin(event.getServer().overworld());
    }
}
