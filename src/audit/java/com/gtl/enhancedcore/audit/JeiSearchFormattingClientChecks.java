package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import mezz.jei.gui.filter.FilterTextSource;
import mezz.jei.gui.ingredients.IngredientFilterApi;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Exercises the real JEI class after Forge applies the production Mixin. */
@Mod("enhancedcore_audit")
public final class JeiSearchFormattingClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/jei-colored-search-20261102")
            .toAbsolutePath().normalize();
    private final List<String> checks = new ArrayList<>();
    private boolean done;

    public JeiSearchFormattingClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("gtl.enhancedcore.jeiSearchFormattingAudit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var result = new LinkedHashMap<String, Object>();
        try {
            check(directory.startsWith(ROOT) && !directory.equals(ROOT), "Isolated task directory");
            check(Files.isRegularFile(directory.resolve("jei-search.marker")), "Explicit audit marker");
            check(mc.level == null && mc.getSingleplayerServer() == null, "No world opened");
            searchQueries();
            result.put("status", "PASS");
        } catch (Throwable failure) {
            result.put("status", "FAIL");
            result.put("error", failure.toString());
            failure.printStackTrace();
        }
        result.put("checks", checks);
        result.put("scope", "Real Forge JEI filter source, public API and listener semantics; no world or hotkey interaction");
        try {
            Files.writeString(directory.resolve("jei-search-checks.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }

    private void searchQueries() {
        var source = new FilterTextSource();
        var notifications = new ArrayList<String>();
        source.addListener(notifications::add);
        check(source.setFilterText("\u00a75EV\u00a7r发射器"), "Colored query changes the filter");
        check(source.getFilterText().equals("EV发射器"), "User example stores a plain item name");
        check(notifications.equals(List.of("EV发射器")), "Search listeners receive the plain item name");
        check(!source.setFilterText("\u00a7dEV\u00a7r发射器") && notifications.size() == 1,
                "Color-only changes do not rebuild the same search");

        // The API only needs its text source for set/get; no ingredient index is touched.
        var api = new IngredientFilterApi(null, source);
        String[][] cases = {
                {"\u00a75EV\u00a7r发射器", "EV发射器"},
                {"\u00a7LEV\u00a7n发射器\u00a7R", "EV发射器"},
                {"@gtceu \"\u00a75EV\u00a7r 发射器\" | -#\u00a7aforge:ores\u00a7r $\u00a7btest\u00a7r",
                        "@gtceu \"EV 发射器\" | -#forge:ores $test"},
                {"EV发射器", "EV发射器"},
                {"  EV 发射器  ", "  EV 发射器  "},
                {"\u00a75\u00a7l\u00a7r", ""},
                {"", ""},
                {"\u00a7zEV\u00a7", "\u00a7zEV\u00a7"}
        };
        for (int i = 0; i < cases.length; i++) {
            api.setFilterText(cases[i][0]);
            check(api.getFilterText().equals(cases[i][1]), "Public API query case " + i);
        }
        for (char code : "0123456789abcdefklmnorABCDEFKLMNOR".toCharArray()) {
            api.setFilterText("\u00a7" + code + "EV\u00a7r发射器");
            check(api.getFilterText().equals("EV发射器"), "Minecraft formatting code " + code);
        }
        api.setFilterText("\u00a7r");
        check(source.getFilterText().isEmpty() && notifications.getLast().isEmpty(),
                "Clearing a formatted query restores the empty filter");
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
    }
}
