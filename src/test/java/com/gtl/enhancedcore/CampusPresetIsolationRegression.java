package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plot.PlotSettingsCodec;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Locale;

/** Layout identity is explicit and survives settings transport; changed target contracts never alias. */
final class CampusPresetIsolationRegression {
    static int run() {
        int checks = 0;
        var bounds = new PlannedCampus.Bounds(-100, -100, 99, 99);
        var first = new CampusPlanAccess.Target(0, CampusPlanAccess.Kind.AE_CORE, bounds, null, "ae", 0, 0, 0);
        var second = new CampusPlanAccess.Target(1, CampusPlanAccess.Kind.WORKSHOP, bounds, null, "early", 1, 18, 19);
        var original = new CampusPlanAccess.Plan(List.of(), List.of(), bounds, bounds, List.of(), List.of(first, second));
        String identity = CampusPlanAccess.fingerprint(PlotSettings.RoadStyle.CAMPUS_WOODLAND, original);
        check(!identity.equals(CampusPlanAccess.fingerprint(PlotSettings.RoadStyle.CAMPUS_DENSE, original)),
                "Identical geometry under a distinct named preset retains a distinct identity"); checks++;
        var moved = new CampusPlanAccess.Target(0, CampusPlanAccess.Kind.AE_CORE, bounds, null, "ae", 0, 1, 0);
        check(!identity.equals(CampusPlanAccess.fingerprint(PlotSettings.RoadStyle.CAMPUS_WOODLAND,
                        new CampusPlanAccess.Plan(List.of(), List.of(), bounds, bounds, List.of(), List.of(moved, second)))),
                "A changed teleport coordinate is rejected as another target contract"); checks++;
        check(!identity.equals(CampusPlanAccess.fingerprint(PlotSettings.RoadStyle.CAMPUS_WOODLAND,
                        new CampusPlanAccess.Plan(List.of(), List.of(), bounds, bounds, List.of(), List.of(second, first)))),
                "Target sequence changes cannot reuse the old numeric destination contract"); checks++;
        Locale oldLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            check(identity.equals(CampusPlanAccess.fingerprint(PlotSettings.RoadStyle.CAMPUS_WOODLAND, original)),
                    "Identity serialization is independent of the process locale"); checks++;
        } finally { Locale.setDefault(oldLocale); }
        for (var style : List.of(PlotSettings.RoadStyle.CAMPUS, PlotSettings.RoadStyle.CAMPUS_PARK,
                PlotSettings.RoadStyle.CAMPUS_COMPACT, PlotSettings.RoadStyle.CAMPUS_DENSE, PlotSettings.RoadStyle.CAMPUS_WOODLAND)) {
            var settings = PlotSettings.plannedCampus().withRoadStyle(style);
            String current = CampusPlanAccess.layoutIdentity(settings);
            check(current.startsWith("campus-plan-v1:" + style.name() + ":"), "Preset identity contains its explicit stable name"); checks++;
            check(current == CampusPlanAccess.layoutIdentity(settings), "Repeated opens reuse the cached layout identity"); checks++;
            var shared = PlotPresetShare.decode(PlotPresetShare.encode(settings));
            check(CampusPlanAccess.layoutIdentity(shared).equals(current), "Open settings share transport retains layout identity"); checks++;
            var encoded = PlotSettingsCodec.CODEC.encodeStart(JsonOps.INSTANCE, settings).result().orElseThrow();
            var decoded = PlotSettingsCodec.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
            check(CampusPlanAccess.layoutIdentity(decoded).equals(current), "Real dimension settings codec roundtrip retains layout identity"); checks++;
        }
        return checks;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
