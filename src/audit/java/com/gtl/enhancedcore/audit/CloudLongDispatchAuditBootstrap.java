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
        if (!(task.equals(previousTask) || task.equals(currentTask)) || !expected.getParent().equals(task)
                || !world.getParent().equals(expected) || !world.getFileName().toString().equals("cloud-long-world")
                || !Files.isRegularFile(expected.resolve("cloud-long.marker")))
            throw new IllegalStateException("Long dispatch audit refuses an unmarked or unrelated world: " + world);
        CloudPatternFlowRuntimeChecks.begin(event.getServer().overworld());
    }
}
