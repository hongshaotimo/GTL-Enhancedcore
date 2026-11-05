package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Real registered controllers and transformed serverTick in a title-screen dummy world.
 * Cloud authority/AE readiness is the sole endpoint seam; delivery stays pending.
 * This does not claim real structure formation, ME transfer or rendered GUI acceptance. */
@Mod("enhancedcore_audit")
public final class CloudSteamRuntimeChecks {
    private final List<String> checks = new ArrayList<>();
    private boolean done;

    public CloudSteamRuntimeChecks() {
        if (Boolean.getBoolean("gtl.enhancedcore.cloudSteamAudit")) MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        var report = new LinkedHashMap<String, Object>();
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            Path root = Path.of(System.getProperty("gtl.enhancedcore.auditRoot")).toRealPath();
            directory = directory.toRealPath();
            check(directory.startsWith(root) && !directory.equals(root)
                            && Files.isRegularFile(directory.resolve("cloud-steam.marker"))
                            && mc.level == null && mc.player == null && mc.getSingleplayerServer() == null,
                    "Explicitly marked isolated title screen; no player or test world opened");
            check(Boolean.getBoolean("gtl.enhancedcore.memoryAuditProductionTicks"),
                    "Environment agent preserves real production serverTick bytecode");
            CloudSteamScenarios.run(directory, checks);
            report.put("status", "PASS");
        } catch (Throwable failure) {
            failure.printStackTrace();
            report.put("status", "FAIL"); report.put("error", failure.toString());
        }
        report.put("checks", checks);
        report.put("scope", "Real registered controllers, steam modifier, transformed RecipeLogic.serverTick, isolated order inventories, "
                + "steam startup/payment and persisted IvJob recovery; native GUI tree/initial-packet synchronization and three-size offscreen frames. "
                + "Synthetic formed membership in TrackedDummyWorld; actual primitive port predicates/preview, no complete formation, live ME transfer, rendered Jade or player world.");
        try {
            Files.writeString(directory.resolve("cloud-steam-checks.json"),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report), StandardCharsets.UTF_8);
        } catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }

    private void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
}
