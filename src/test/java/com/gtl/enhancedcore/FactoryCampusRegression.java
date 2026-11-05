package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.plot.FactoryArchitecture;
import com.gtl.enhancedcore.common.world.plot.FactoryCampus;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlotLayout;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Usable factory floors, full module capacity, low open megastructure courts and roof bounds. */
final class FactoryCampusRegression {
    private static int checks;
    static int run() throws Exception {
        checks = 0;
        PlotSettings settings = PlotSettings.factoryCampus();
        check(settings.isCampus() && settings.roadStyle() == PlotSettings.RoadStyle.CAMPUS_FACTORY,
                "The factory has its own campus identity");
        check(FactoryCampus.bounds().equals(new PlannedCampus.Bounds(-1000, -1000, 999, 999)),
                "Quartz Works occupies the requested continuous 2000 by 2000 island");
        check(FactoryCampus.plots().size() == 226 && FactoryCampus.sharedMachineCount() == 46,
                "The frozen retained machine list keeps its dedicated and shared capacity");
        check(FactoryCampus.workshops().size() == 4, "Four large production halls retain shared machine capacity");
        check(settings.surfaceY() + FactoryCampus.maxHeight() <= 319, "Factory architecture fits the world build height");
        for (int along=-90;along<=90;along++) for (int across=-3;across<=3;across++)
            for(int y=-6;y<=-3;y++) {
                check("minecraft:air".equals(FactoryCampus.foundationBlockAt(along,y,across)),
                        "The east-west service gallery stays walkable through its central junction");
                check("minecraft:air".equals(FactoryCampus.foundationBlockAt(across,y,along)),
                        "The north-south service gallery stays walkable through its central junction");
            }
        check(!FactoryCampus.hasTerrain(-1001, 0) && !FactoryCampus.hasTerrain(1000, 0)
                        && !FactoryCampus.hasTerrain(0, -1001) && !FactoryCampus.hasTerrain(0, 1000),
                "The complete exterior remains void");
        Map<String, JsonObject> catalog = new HashMap<>();
        try (var reader = new InputStreamReader(FactoryCampus.class.getResourceAsStream(FactoryCampus.CATALOG), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (var element : json.getAsJsonArray("reservations")) {
                var reservation = element.getAsJsonObject();
                catalog.put(reservation.get("id").getAsString(), reservation);
            }
            for (var id : json.getAsJsonObject("reservationPolicy").getAsJsonArray("excludedModuleIds"))
                check(FactoryCampus.plots().stream().noneMatch(plot -> plot.id().equals(id.getAsString())),
                        "A machine module does not acquire a standalone floor");
            for (var id : json.getAsJsonArray("effectiveSharedIds"))
                check(FactoryCampus.plots().stream().noneMatch(plot -> plot.id().equals(id.getAsString())),
                        "Shared small machines use production halls without individual megastructure reservations");
        }
        Set<String> slots = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>();
        boolean hasIndoor = false, hasOutdoor = false, hasTallOutdoor = false;
        for (var plot : FactoryCampus.plots()) {
            check(slots.add(plot.id() + "#" + plot.slotIndex()), "Every machine copy has a unique floor reservation");
            counts.merge(plot.id(), 1, Integer::sum);
            var data = catalog.get(plot.id());
            check(data != null, "No removed machine reappears in the factory");
            var size = data.getAsJsonArray("size"); var anchor = data.getAsJsonArray("groundOffset");
            check(plot.width() == size.get(0).getAsInt() && plot.height() == size.get(1).getAsInt()
                            && plot.depth() == size.get(2).getAsInt()
                            && plot.controllerX() == plot.x() + anchor.get(0).getAsInt()
                            && plot.controllerY() == 1 + anchor.get(1).getAsInt()
                            && plot.controllerZ() == plot.z() + anchor.get(2).getAsInt(),
                    "The north-facing machine and full-module envelope retain their audited dimensions and anchor");
            check(FactoryCampus.planningTier(plot.id()) == data.get("planningTier").getAsInt(),
                    "Building placement does not change the machine construction tier");
            var hall = FactoryCampus.hallAt(plot.x(), plot.z());
            check(hall != null && FactoryCampus.hallAt(plot.maxX(), plot.maxZ()) == hall,
                    "Every machine envelope belongs to exactly one industrial hall or open court");
            var floor = hall.productionBounds();
            check(floor.contains(plot.x(), plot.z()) && floor.contains(plot.maxX(), plot.maxZ())
                            && plot.height() + 10 <= hall.clearHeight(),
                    "Complete machine/module dimensions and ten blocks of headroom fit their production floor");
            boolean large = Math.max(plot.width(), plot.depth()) >= 150 || plot.height() >= 120
                    || (long) plot.width() * plot.depth() >= 14000;
            check(!large || hall.outdoor(), "Very large megastructures occupy an open court with no roof");
            hasIndoor |= !hall.outdoor(); hasOutdoor |= hall.outdoor();
            hasTallOutdoor |= hall.outdoor() && plot.height() == 291;
            for (int x = plot.x(); x <= plot.maxX(); x++) for (int z = plot.z(); z <= plot.maxZ(); z++) {
                String ground = FactoryCampus.surfaceBlock(settings, x, z);
                check(FactoryCampus.materials().contains(ground), "Every machine column has a supported level production floor");
                for (int y : new int[] {1, plot.height(), plot.height() + 9})
                    check(FactoryCampus.blockAboveAt(settings, x, y, z) == null,
                            "Factories and landscape do not consume the machine footprint or required headroom");
            }
            // Full vertical lines through corners, face centres and the centre detect crossing beams or platforms.
            for (int x : samples(plot.x(), plot.maxX())) for (int z : samples(plot.z(), plot.maxZ()))
                for (int y = 1; y <= plot.height() + 9; y++)
                    check(FactoryArchitecture.above(hall, x, y, z) == null,
                            "No wall, floor or roof crosses a reserved machine envelope");
            check(FactoryCampus.blockAboveAt(settings, plot.signX(), 1, plot.signZ()) == null
                            && FactoryCampus.blockAboveAt(settings, plot.signX(), 2, plot.signZ()) == null,
                    "The teleporter's north forecourt remains clear at player height");
            for (int y : new int[] {1, plot.height(), plot.height() + 9})
                check(PlotLayout.blockIdAt(settings, plot.controllerX(), settings.surfaceY() + y, plot.controllerZ()).equals("minecraft:air"),
                        "World generation routes factory geometry to the independent sampler");
        }
        check(hasIndoor && hasOutdoor && hasTallOutdoor, "The plan includes furnished factories and an open court for the 291-block megastructure");
        for (var entry : catalog.entrySet())
            check(counts.getOrDefault(entry.getKey(), 0) == entry.getValue().get("copies").getAsInt(),
                    "All retained machines keep exactly the requested number of copies");
        fullModules("gtceu:space_elevator", 35, 43, 35);
        fullModules("gtladditions:space_elevator_mkii", 89, 91, 89);
        fullModules("gtladditions:forge_of_the_antichrist", 127, 29, 187);
        fullModules("gtladditions:recursive_reverse_array", 67, 15, 67);
        fullModules("gtceu:molecular_assembler_matrix", 49, 41, 49);
        fullModules("gtladditions:biosphere_iii", 76, 29, 72);
        Set<Integer> styles = new HashSet<>();
        for (var hall : FactoryCampus.halls()) {
            check(FactoryCampus.bounds().contains(hall.x(), hall.z()) && FactoryCampus.bounds().contains(hall.maxX(), hall.maxZ()),
                    "Buildings and landscape stay within the island");
            if (!hall.outdoor()) styles.add(Math.floorMod(hall.variant(), 4));
            else check(FactoryArchitecture.maxHeight(hall) >= hall.clearHeight(), "Open courts retain the machine height limit");
            int middle = hall.width() / 2;
            for (int u = middle - 4; u <= middle + 4; u++) for (int v = 0; v < 22; v++) {
                int[] world = entryPoint(hall, u, v);
                for (int y = 1; y <= 8; y++) check(FactoryArchitecture.above(hall, world[0], y, world[1]) == null,
                        "The road-to-production entry is at least nine blocks wide and eight blocks high");
            }
            if (hall.id().startsWith("shared-")) {
                check(hall.entranceX() == -0.5 || hall.entranceZ() == -0.5,
                        "Each shared-hall entrance center lies on a dispatch-court axis");
            }
            for (int x : samples(hall.x(), hall.maxX())) for (int z : samples(hall.z(), hall.maxZ())) {
                int actual = 0;
                for (int y = 1; y <= FactoryArchitecture.maxHeight(hall); y++) {
                    String state = FactoryArchitecture.above(hall, x, y, z);
                    if (state != null) {
                        actual = y;
                        check(FactoryCampus.materials().contains(state), "All architectural block states belong to the generation palette");
                    }
                }
                check(FactoryArchitecture.top(hall, x, z) == actual, "Column height equals the actual topmost decoration");
                check(!hall.outdoor() || actual <= 8, "An open megastructure court has only low peripheral decoration");
            }
        }
        check(styles.size() == 4, "Enclosed industrial halls use all four articulated roof families");
        for (int a = 0; a < FactoryCampus.halls().size(); a++) for (int b = a + 1; b < FactoryCampus.halls().size(); b++) {
            var first = FactoryCampus.halls().get(a); var second = FactoryCampus.halls().get(b);
            check(first.maxX() < second.x() || second.maxX() < first.x() || first.maxZ() < second.z() || second.maxZ() < first.z(),
                    "Independent factory shells and open courts do not overlap");
        }
        return checks;
    }
    private static int[] samples(int minimum, int maximum) {
        int span = maximum - minimum;
        return new int[] {minimum, minimum + span / 4, minimum + span / 2, minimum + span * 3 / 4, maximum};
    }
    private static int[] entryPoint(FactoryArchitecture.Hall hall, int u, int v) {
        return switch (hall.entranceFacing()) {
            case "south" -> new int[] {hall.x() + u, hall.z() + hall.depth() - 1 - v};
            case "east" -> new int[] {hall.x() + hall.width() - 1 - v, hall.z() + u};
            case "west" -> new int[] {hall.x() + v, hall.z() + hall.depth() - 1 - u};
            default -> new int[] {hall.x() + u, hall.z() + v};
        };
    }
    private static void fullModules(String id, int width, int height, int depth) {
        var plot = FactoryCampus.plots().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        check(plot.width() == width && plot.height() == height && plot.depth() == depth,
                "The host includes all attached modules: " + id);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
