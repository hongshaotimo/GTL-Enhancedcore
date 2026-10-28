package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sole mod entry for this round's reduced isolated Forge helper. */
@Mod("enhancedcore_audit")
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class CloudInteractionAuditBootstrap {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/cloud-network-actions-20261024/forge-instance").toAbsolutePath().normalize();
    public CloudInteractionAuditBootstrap() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("gtl.cloudPatternInteractionAudit")) return;
        var server = event.getServer(); Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String phase = System.getProperty("gtl.cloudStoreAuditPhase", "player_ui");
        boolean passive = phase.equals("passive") || phase.equals("network_import") || phase.equals("authority_flow");
        boolean authority = phase.equals("authority_seed") || phase.equals("authority_verify");
        if (!world.getParent().equals(ROOT) || !world.getFileName().toString().equals(authority ? "cloud-authority-world" : phase.equals("authority_flow") ? "cloud-authority-flow-world" : phase.equals("network_import") ? "cloud-import-world" : passive ? "cloud-passive-world" : "cloud-wut-world")
                || !Files.isRegularFile(ROOT.resolve("cloud-store.marker")))
            throw new IllegalStateException("Interaction audit refused an unmarked or non-task world: " + world);
        if (authority) {
            var checks = new ArrayList<String>(); var report = new LinkedHashMap<String, Object>();
            try {
                var ref = ROOT.resolve("cloud-authority-ref.dat").toFile();
                if (phase.equals("authority_seed")) net.minecraft.nbt.NbtIo.writeCompressed(CloudCoreAuthorityRuntimeChecks.seed(server.overworld(), checks), ref);
                else CloudCoreAuthorityRuntimeChecks.verifyReloaded(server.overworld(), net.minecraft.nbt.NbtIo.readCompressed(ref), checks);
                report.put("status", "PASS");
            } catch (Throwable failure) { report.put("status", "FAIL"); report.put("error", failure.toString()); failure.printStackTrace(); }
            report.put("phase", phase); report.put("checks", checks);
            try { Files.writeString(ROOT.resolve("cloud-store-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Exception failure) { throw new RuntimeException(failure); }
            server.halt(false); return;
        }
        if (passive) {
            try { CloudPatternFlowRuntimeChecks.begin(server.overworld()); return; }
            catch (Exception failure) { throw new RuntimeException("Passive fixture setup failed", failure); }
        }
        if (!System.getProperty("gtl.cloudStoreAuditPhase", "player_ui").equals("interaction_verify")) {
            CloudPlayerRuntimeChecks.begin(server.overworld()); return;
        }
        var checks = new ArrayList<String>(); var report = new LinkedHashMap<String, Object>();
        try { CloudPatternInteractionRuntimeChecks.verifyReload(server.overworld(), checks); report.put("status", "PASS"); }
        catch (Throwable failure) { report.put("status", "FAIL"); report.put("error", failure.toString()); failure.printStackTrace(); }
        report.put("phase", "interaction_verify"); report.put("checks", checks);
        report.put("scope", "Fresh Forge JVM reload of exact original deduplication authority; no player world");
        try { Files.writeString(ROOT.resolve("cloud-store-interaction_verify.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { throw new RuntimeException(failure); }
        server.halt(false);
    }
}
