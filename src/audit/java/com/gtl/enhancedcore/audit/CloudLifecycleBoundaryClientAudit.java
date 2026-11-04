package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in title-screen audit: fixtures only, with a marker in an explicitly owned audit root. */
@Mod("enhancedcore_audit")
public final class CloudLifecycleBoundaryClientAudit {
    private boolean done;

    public CloudLifecycleBoundaryClientAudit() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("gtl.enhancedcore.cloudLifecycleAudit")) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || !(minecraft.screen instanceof TitleScreen)) return;
        done = true;
        var report = new LinkedHashMap<String, Object>();
        var checks = new ArrayList<String>();
        var directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            String configuredRoot = System.getProperty("gtl.enhancedcore.auditRoot", "");
            if (configuredRoot.isBlank()
                    || !directory.startsWith(Path.of(configuredRoot).toAbsolutePath().normalize())
                    || !Files.isRegularFile(directory.resolve("cloud-feedback.marker"))
                    || minecraft.level != null || minecraft.getSingleplayerServer() != null)
                throw new AssertionError("Expected marked task-owned audit root and an unopened player world");
            checks.add("marked isolated client at title screen; no saved player world opened");
            checks.addAll(CloudLifecycleBoundaryRuntimeChecks.run());
            report.put("status", "PASS");
        } catch (Throwable failure) {
            failure.printStackTrace();
            report.put("status", "FAIL"); report.put("failure", failure.toString());
        }
        report.put("checks", checks);
        try {
            Files.writeString(directory.resolve("cloud-lifecycle-boundary-checks.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { failure.printStackTrace(); }
        minecraft.stop();
    }
}
