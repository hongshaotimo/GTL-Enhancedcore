package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Real loaded-mod checks against synthetic endpoints; never enters a player world. */
@Mod("enhancedcore_audit")
public final class StellarIvClientChecks {
    private boolean done;
    public StellarIvClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.stellarIvAudit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        List<String> checks = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (!directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/stellar-unbounded-gui-20261028"))
                    || !Files.isRegularFile(directory.resolve("galaxy.marker")))
                throw new AssertionError("Not the marked task-owned fixture");
            if (mc.level != null || mc.getSingleplayerServer() != null)
                throw new AssertionError("A world was opened");
            checks.add("No player world opened");
            List<String> failures = new ArrayList<>();
            group("GUI", StellarIvGuiChecks::run, checks, failures);
            group("wide grid", StellarIvGuiChecks::power, checks, failures);
            if (!Boolean.getBoolean("gtl.enhancedcore.stellarIvGuiOnly"))
                group("batch", () -> {
                    @SuppressWarnings("unchecked")
                    var batchChecks = (List<String>) Class.forName("com.gtl.enhancedcore.audit.StellarIvBatchChecks")
                            .getMethod("run").invoke(null);
                    return batchChecks;
                }, checks, failures);
            if (!failures.isEmpty()) {
                result.put("failures", failures);
                throw new AssertionError(failures.toString());
            }
            result.put("status", "PASS");
        } catch (Throwable failure) {
            result.put("status", "FAIL");
            result.put("error", failure.toString());
            failure.printStackTrace();
        }
        result.put("checks", checks);
        result.put("scope", "Actual Forge registrations and merged Mixins with synthetic UI/recipe fixtures; no player world or full structure gameplay");
        try {
            Files.writeString(directory.resolve("galaxy-checks.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }

    @FunctionalInterface private interface Checks { List<String> run() throws Exception; }
    private static void group(String label, Checks action, List<String> checks, List<String> failures) {
        try { checks.addAll(action.run()); }
        catch (Throwable failure) {
            Throwable cause = failure instanceof java.lang.reflect.InvocationTargetException wrapped
                    ? wrapped.getCause() : failure;
            failures.add(label + ": " + cause); cause.printStackTrace();
        }
    }
}
