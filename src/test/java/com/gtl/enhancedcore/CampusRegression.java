package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus.Plot;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.HashSet;
import java.util.ArrayDeque;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Geometric contracts: a usable empty machine interior and a connected, safe central AE reserve. */
final class CampusRegression {
    private static int checks;
    static int run() throws Exception {
        checks = 0;
        PlotSettings defaults = PlotSettings.plannedCampus(), framed = defaults.withMachineFrames(true);
        check(!defaults.showMachineFrames(), "A newly selected campus has no tall helper frames");
        var plots = PlannedCampus.plots();
        check(!plots.isEmpty(), "The machine catalog creates dedicated lots");
        var ids = new HashSet<String>();
        try (var reader = new InputStreamReader(PlannedCampus.class.getResourceAsStream(PlannedCampus.CATALOG), StandardCharsets.UTF_8)) {
            var catalog = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : catalog.getAsJsonArray("machines")) {
                var json = entry.getAsJsonObject(); String id = json.get("id").getAsString();
                int tier = json.get("planningTier").getAsInt();
                check(tier >= 0 && tier <= 14 && PlannedCampus.planningTier(id) == tier,
                        "Actual construction tier stays independent of reservation priority: " + id);
            }
            int expectedLots = 0;
            for (var entry : catalog.getAsJsonArray("reservations")) {
                var json = entry.getAsJsonObject(); String id = json.get("id").getAsString();
                int copies = json.get("copies").getAsInt(); expectedLots += copies;
                var size = json.getAsJsonArray("size"); var controller = json.getAsJsonArray("groundOffset");
                var matches = plots.stream().filter(plot -> plot.id().equals(id)).toList();
                check(matches.size() == copies, "Every catalog id and requested parallel copy is included: " + id);
                for (Plot plot : matches) check(plot.slotIndex() >= 0 && plot.slotIndex() < copies,
                        "Each dedicated copy has an in-range slot: " + id);
                for (Plot plot : matches) check(plot.width() == size.get(0).getAsInt() && plot.height() == size.get(1).getAsInt()
                                && plot.depth() == size.get(2).getAsInt() && plot.controllerX() - plot.x() == controller.get(0).getAsInt()
                                && plot.controllerY() == controller.get(1).getAsInt() + 1 && plot.controllerZ() - plot.z() == controller.get(2).getAsInt(),
                        "Actual dimensions and the north-facing controller transform match the source catalog: " + id);
            }
            check(expectedLots == plots.size(), "No extra lot or omitted catalog entry");
            var shared = catalog.getAsJsonArray("effectiveSharedIds");
            check(shared.size() > 0 && shared.size() == PlannedCampus.sharedMachineCount(),
                    "Effective shared catalog excludes dedicated overrides and modules");
            for (var id : shared) check(plots.stream().noneMatch(plot -> plot.id().equals(id.getAsString())),
                    "Shared production capacity does not create independent plots: " + id);
            for (var id : catalog.getAsJsonObject("reservationPolicy").getAsJsonArray("excludedModuleIds")) {
                check(plots.stream().noneMatch(plot -> plot.id().equals(id.getAsString())),
                        "A host module never reserves an independent plot: " + id);
                check(!shared.contains(id), "A host module is not counted as an independent shared machine: " + id);
            }
        }
        String[] earlyIds = {"gtceu:bronze_large_boiler", "gtl_enhancedcore:industrial_steam_platform",
                "gtceu:electric_blast_furnace", "gtceu:large_greenhouse", "gtl_enhancedcore:basic_ore_processing_plant"};
        int[] earlyCopies = {4, 15, 12, 6, 10};
        for (int i = 0; i < earlyIds.length; i++) {
            String id = earlyIds[i];
            var matches = plots.stream().filter(plot -> plot.id().equals(id)).toList();
            check(matches.size() == earlyCopies[i], "Requested early production capacity: " + id);
            check(matches.stream().allMatch(plot -> plot.stage().equals("early")
                            && Math.max(Math.abs(plot.x() + plot.width() / 2), Math.abs(plot.z() + plot.depth() / 2)) < 400),
                    "Requested early machines are grouped inside the middle ring: " + id);
        }
        check(plots.stream().filter(plot -> plot.id().equals("gtceu:assembly_line")).count() == 1
                        && plots.stream().noneMatch(plot -> plot.id().equals("gtceu:advanced_assembly_line")),
                "Assembly line upgrades share exactly one reservation");
        check(plots.stream().filter(plot -> plot.id().equals("gtceu:nano_forge_1")).count() == 1
                        && plots.stream().noneMatch(plot -> plot.id().equals("gtceu:nano_forge_2") || plot.id().equals("gtceu:nano_forge_3")),
                "All three nano forge stages share exactly one reservation");
        check(PlannedCampus.planningTier("gtceu:large_greenhouse") == 5,
                "Early reserve priority does not relabel the greenhouse construction voltage");
        for (Plot plot : plots) {
            check(ids.add(plot.id() + "#" + plot.slotIndex()), "Every catalog machine/copy gets exactly one lot: " + plot.id());
            check(plot.controllerX() >= plot.x() && plot.controllerX() <= plot.maxX()
                    && plot.controllerZ() >= plot.z() && plot.controllerZ() <= plot.maxZ()
                    && plot.controllerY() >= 1 && plot.controllerY() <= plot.height(), "Controller stays in exact machine bounds");
            check("minecraft:sea_lantern".equals(PlannedCampus.blockAboveAt(null, plot.controllerX(), plot.controllerY(), plot.controllerZ())),
                    "Controller marker uses its actual bottom-up Y");
            for (int x = plot.x() - 1; x <= plot.maxX() + 1; x++) {
                border(defaults, framed, plot, x, plot.z() - 1);
                border(defaults, framed, plot, x, plot.maxZ() + 1);
            }
            for (int z = plot.z(); z <= plot.maxZ(); z++) {
                border(defaults, framed, plot, plot.x() - 1, z);
                border(defaults, framed, plot, plot.maxX() + 1, z);
            }
            check("minecraft:light_gray_concrete".equals(PlannedCampus.surfaceBlock(defaults, plot.signX(), plot.z() - 2))
                            && !"minecraft:smooth_quartz".equals(PlannedCampus.surfaceBlock(defaults, plot.x() - 2, plot.z()))
                            && !"minecraft:smooth_quartz".equals(PlannedCampus.surfaceBlock(defaults, plot.maxX() + 2, plot.z())),
                    "The W+2 by D+2 ground frame extends exactly one block beyond the machine");
            check(PlannedCampus.blockAboveAt(null, plot.signX(), 1, plot.signZ()) == null,
                    "North-facing entrance sign has clear space");
            for (int x : new int[] {plot.x() - 1, plot.maxX() + 1}) for (int z : new int[] {plot.z() - 1, plot.maxZ() + 1})
                check(PlannedCampus.hasTerrain(x, z), "Every exact machine frame is supported by its district island");
            for (int x = plot.x(); x <= plot.maxX(); x++)
                for (int z = plot.z(); z <= plot.maxZ(); z++) {
                    check("minecraft:light_gray_concrete".equals(PlannedCampus.surfaceBlock(defaults, x, z)),
                            "The complete machine footprint is flat paving");
                    int ceiling = x == plot.controllerX() && z == plot.controllerZ() ? plot.controllerY() : 0;
                    check(PlannedCampus.maxDecorationYAt(defaults, x, z) == ceiling
                                    && PlannedCampus.maxDecorationYAt(framed, x, z) == ceiling,
                            "Both frame modes leave every machine column clear except its controller marker: " + plot.id());
                    for (int y : new int[] {1, Math.max(1, plot.height() / 2), plot.height(), plot.height() + 1}) {
                        boolean marker = x == plot.controllerX() && z == plot.controllerZ() && y == plot.controllerY();
                        check(marker || PlannedCampus.blockAboveAt(defaults, x, y, z) == null
                                        && PlannedCampus.blockAboveAt(framed, x, y, z) == null,
                                "Frame and trees never consume the usable W x H x D interior: " + plot.id());
                    }
                }
            check(!overlap(plot, -79, -79, 79, 79, 8), "AE's central 160 x 160 court stays free of machine lots");
            for (var workshop : PlannedCampus.workshops())
                check(!overlap(plot, workshop.x(), workshop.z(), workshop.x() + workshop.width() - 1,
                        workshop.z() + workshop.depth() - 1, 2), "Ordinary workshops never overlap megastructures");
        }
        for (int a = 0; a < plots.size(); a++) for (int b = a + 1; b < plots.size(); b++) {
            Plot first = plots.get(a), second = plots.get(b);
            check(!overlap(first, second.x(), second.z(), second.maxX(), second.maxZ(), 3),
                    "Independent machine frames and circulation gaps do not overlap: " + first.id() + " / " + second.id());
        }
        // Independent free-rectangle packing has no paired-copy contract. Adjacent same-id
        // slots (for example 7/8 of ten copies) may share a row without mirroring each other.
        // Every copy's unmirrored controller anchor was checked against its reservation above.
        // Read declared tree centers rather than estimating them from a visual sample: every indexed canopy is covered.
        var holder = Class.forName(PlannedCampus.class.getName() + "$Holder");
        var planField = holder.getDeclaredField("PLAN"); planField.setAccessible(true);
        Object plan = planField.get(null);
        var treeCenters = new HashSet<Object>();
        for (Object local : ((java.util.Map<?, ?>) field(plan, "index")).values())
            treeCenters.addAll((java.util.List<?>) field(local, "trees"));
        check(!treeCenters.isEmpty(), "The ceiling regression covers declared trees");
        for (Object tree : treeCenters) {
            int treeX = (int) field(tree, "x"), treeZ = (int) field(tree, "z");
            for (int x = treeX - 3; x <= treeX + 3; x++) for (int z = treeZ - 3; z <= treeZ + 3; z++) {
                int ceiling = PlannedCampus.maxDecorationYAt(defaults, x, z);
                for (int y = 1; y <= 12; y++) if (PlannedCampus.blockAboveAt(defaults, x, y, z) != null)
                    check(y <= ceiling, "Every declared canopy block fits the generated column ceiling at " + x + "," + y + "," + z);
            }
        }
        var bounds = PlannedCampus.bounds();
        check(bounds.minX() == -bounds.maxX() - 1 && bounds.minZ() == -bounds.maxZ() - 1,
                "The central AE reserve sits at the geometric world-plan center");
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++)
            check(PlannedCampus.maxDecorationYAt(x, z) == 0, "Arrival square has no above-ground collision");
        var core = PlannedCampus.aeCoreBounds();
        check(core.width() == 160 && core.depth() == 160, "The AE reserve remains exactly 160 by 160");
        for (int x = core.minX(); x <= core.maxX(); x++) for (int z = core.minZ(); z <= core.maxZ(); z++)
            check(PlannedCampus.hasTerrain(x, z) && PlannedCampus.maxDecorationYAt(defaults, x, z) == 0
                            && PlannedCampus.maxDecorationYAt(framed, x, z) == 0
                            && PlannedCampus.blockAboveAt(framed, x, 1, z) == null,
                    "The complete 160 x 160 AE reserve remains supported and open in both frame modes");
        check(PlannedCampus.workshops().size() == 4, "Four shared free-production courts remain available");
        for (var workshop : PlannedCampus.workshops()) for (int x = workshop.x(); x < workshop.x() + workshop.width(); x++)
            for (int z = workshop.z(); z < workshop.z() + workshop.depth(); z++)
                check(PlannedCampus.hasTerrain(x, z) && PlannedCampus.maxDecorationYAt(defaults, x, z) == 0
                                && PlannedCampus.maxDecorationYAt(framed, x, z) == 0
                                && PlannedCampus.blockAboveAt(framed, x, 1, z) == null,
                        "Planting never consumes shared production court space");
        for (var garden : PlannedCampus.aeGardenBounds()) {
            int x = (garden.minX() + garden.maxX()) / 2, z = (garden.minZ() + garden.maxZ()) / 2;
            check("minecraft:water".equals(PlannedCampus.surfaceBlock(defaults, x, z)), "Each AE garden has a real-water pond");
            for (int y = -1; y >= -3; y--)
                check("minecraft:water".equals(PlannedCampus.foundationBlockAt(x, y, z)), "Each AE pond retains four water layers including its surface");
            check("minecraft:clay".equals(PlannedCampus.foundationBlockAt(x, -4, z)), "Each pond center has a clay bed below four water layers");
            check("minecraft:smooth_quartz".equals(PlannedCampus.foundationBlockAt(garden.minX() + 24, -1, z)), "Each AE pond retains its quartz rim");
        }
        for (int x = -79; x <= 79; x += 3) for (int z = -79; z <= 79; z += 3)
            check(PlannedCampus.materials().contains(PlannedCampus.surfaceBlock(null, x, z)), "Central decoration only uses declared vanilla material palette");
        check(PlannedCampus.materials().stream().allMatch(id -> id.startsWith("minecraft:")), "The preset places decoration, no mod machinery");
        check(!PlannedCampus.hasTerrain(bounds.maxX() + 160, bounds.maxZ() + 160), "The exterior is finite void");
        check("minecraft:air".equals(PlannedCampus.surfaceBlock(null, bounds.maxX() + 160, bounds.maxZ() + 160)), "Void preview matches actual generation");
        var roadMarkings = java.util.Set.of("minecraft:black_concrete", "minecraft:white_concrete", "minecraft:yellow_concrete");
        for (int x = bounds.minX(); x <= -1; x += 8) for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 8) {
            int mirrorX = -1 - x;
            check(PlannedCampus.hasTerrain(x, z) == PlannedCampus.hasTerrain(mirrorX, z),
                    "Island and bridge geometry mirrors left/right at " + x + "," + z);
            String surface = PlannedCampus.surfaceBlock(defaults, x, z), mirror = PlannedCampus.surfaceBlock(defaults, mirrorX, z);
            if (roadMarkings.contains(surface) || roadMarkings.contains(mirror))
                check(surface.equals(mirror), "Road carriageways and painted markings mirror left/right at " + x + "," + z);
        }
        var reached = new HashSet<Long>(); var queue = new ArrayDeque<int[]>(); queue.add(new int[] {0, 0}); reached.add(key(0, 0));
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            for (int[] step : new int[][] {{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
                int x = cell[0] + step[0], z = cell[1] + step[1];
                if (bounds.contains(x, z) && PlannedCampus.hasTerrain(x, z) && reached.add(key(x, z))) queue.addLast(new int[] {x, z});
            }
        }
        for (Plot plot : plots) check(reached.contains(key(Math.floorDiv(plot.signX(), 8) * 8, Math.floorDiv(plot.signZ(), 8) * 8)),
                "Each machine's entrance connects to the central AE island without crossing void: " + plot.id());
        for (var garden : PlannedCampus.aeGardenBounds()) check(reached.contains(key(Math.floorDiv(garden.minX() + 8, 8) * 8,
                        Math.floorDiv(garden.minZ() + 8, 8) * 8)), "All four garden promenades connect to the core");
        return checks;
    }
    private static void border(PlotSettings defaults, PlotSettings framed, Plot plot, int x, int z) {
        check(PlannedCampus.hasTerrain(x, z) && "minecraft:smooth_quartz".equals(PlannedCampus.surfaceBlock(defaults, x, z))
                        && "minecraft:smooth_quartz".equals(PlannedCampus.surfaceBlock(framed, x, z)),
                "The W+2 by D+2 ground border is always supported and visible");
        check(PlannedCampus.maxDecorationYAt(defaults, x, z) == 0
                        && PlannedCampus.blockAboveAt(defaults, x, 1, z) == null
                        && PlannedCampus.blockAboveAt(defaults, x, plot.height() + 1, z) == null,
                "The default ground border has no elevated helper geometry");
        check(PlannedCampus.maxDecorationYAt(framed, x, z) == plot.height() + 1
                        && "minecraft:smooth_quartz".equals(PlannedCampus.blockAboveAt(framed, x, plot.height() + 1, z))
                        && PlannedCampus.blockAboveAt(framed, x, plot.height() + 2, z) == null,
                "Enabled helper frames mark the exact machine clearance height");
    }
    private static Object field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static long key(int x, int z) { return (long) x << 32 ^ (z & 0xffffffffL); }
    private static boolean overlap(Plot plot, int minX, int minZ, int maxX, int maxZ, int gap) {
        return plot.x() - gap <= maxX && plot.maxX() + gap >= minX && plot.z() - gap <= maxZ && plot.maxZ() + gap >= minZ;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) throws Exception { System.out.println("Campus geometry checks: " + run()); }
}
