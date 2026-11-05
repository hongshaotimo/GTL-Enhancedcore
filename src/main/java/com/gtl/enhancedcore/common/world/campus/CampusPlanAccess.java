package com.gtl.enhancedcore.common.world.campus;

import com.gtl.enhancedcore.common.world.plot.CompactPlannedCampus;
import com.gtl.enhancedcore.common.world.plot.DensePlannedCampus;
import com.gtl.enhancedcore.common.world.plot.FactoryCampus;
import com.gtl.enhancedcore.common.world.plot.FileCampus;
import com.gtl.enhancedcore.common.world.plot.LegacyPlannedCampus;
import com.gtl.enhancedcore.common.world.plot.ParkPlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.ToIntFunction;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Explicit campus identities, adapted without changing the authored order or geometry of published layouts. */
public final class CampusPlanAccess {
    public static final List<String> PLANNING_TIERS = List.of(
            "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX");
    public enum Kind { MACHINE, AE_CORE, WORKSHOP }
    /** The index is the only client-supplied destination selector accepted by the server. */
    public record Target(int index, Kind kind, PlannedCampus.Bounds bounds, PlannedCampus.Plot plot,
                         String stage, int ordinal, int arrivalX, int arrivalZ) {}
    /** tiers is aligned with plots; the original campus has no tier metadata and uses -1. */
    public record Plan(List<PlannedCampus.Plot> plots, List<PlannedCampus.Workshop> workshops,
                       PlannedCampus.Bounds bounds, PlannedCampus.Bounds aeBounds,
                       List<Integer> tiers, List<Target> targets) {
        public int planningTier(PlannedCampus.Plot plot) { return tiers.get(plots.indexOf(plot)); }
        public Target targetAt(int x, int z) {
            for (Target target : targets) if (target.bounds.contains(x, z)) return target;
            return null;
        }
    }
    private static final Map<String, Plan> PLANS = new HashMap<>();
    private static final Map<String, String> IDENTITIES = new HashMap<>();
    private CampusPlanAccess() {}

    public static synchronized Plan of(PlotSettings settings) {
        if (!settings.isCampus()) throw new IllegalArgumentException("Not a campus preset");
        return PLANS.computeIfAbsent(settings.campusKey(), key -> {
            if (settings.roadStyle() != PlotSettings.RoadStyle.CAMPUS_FILE) return build(settings.roadStyle());
            var campus=FileCampus.of(settings.campusPresetId());
            return filePlan(campus);
        });
    }
    /** One cached identity per explicit preset; never translated or rebuilt for travel requests. */
    public static synchronized String layoutIdentity(PlotSettings settings) {
        Plan plan = of(settings);
        return IDENTITIES.computeIfAbsent(settings.campusKey(), key -> settings.roadStyle() == PlotSettings.RoadStyle.CAMPUS_FILE
                ? "campus-file-v1:" + settings.campusPresetId() + ":" + FileCampus.of(settings.campusPresetId()).resourceIdentity()
                    + ":" + fingerprint(settings.roadStyle(),plan)
                : fingerprint(settings.roadStyle(), plan));
    }
    /** Stable binary geometry contract, also used by the frozen-preset regression fixtures. */
    public static String fingerprint(PlotSettings.RoadStyle style, Plan plan) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeUTF("campus-plan-v1"); out.writeUTF(style.name());
                writeBounds(out, plan.bounds); writeBounds(out, plan.aeBounds);
                out.writeInt(plan.plots.size()); for (var plot : plan.plots) writePlot(out, plot);
                out.writeInt(plan.workshops.size());
                for (var workshop : plan.workshops) {
                    out.writeUTF(workshop.stage()); out.writeInt(workshop.x()); out.writeInt(workshop.z());
                    out.writeInt(workshop.width()); out.writeInt(workshop.depth());
                }
                out.writeInt(plan.tiers.size()); for (int tier : plan.tiers) out.writeInt(tier);
                out.writeInt(plan.targets.size());
                for (var target : plan.targets) {
                    out.writeInt(target.index); out.writeUTF(target.kind.name()); writeBounds(out, target.bounds);
                    out.writeBoolean(target.plot != null); if (target.plot != null) writePlot(out, target.plot);
                    out.writeUTF(target.stage); out.writeInt(target.ordinal); out.writeInt(target.arrivalX); out.writeInt(target.arrivalZ);
                }
            }
            return "campus-plan-v1:" + style.name() + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException("Cannot fingerprint campus layout", impossible); }
    }
    private static void writeBounds(DataOutputStream out, PlannedCampus.Bounds bounds) throws IOException {
        out.writeInt(bounds.minX()); out.writeInt(bounds.minZ()); out.writeInt(bounds.maxX()); out.writeInt(bounds.maxZ());
    }
    private static void writePlot(DataOutputStream out, PlannedCampus.Plot plot) throws IOException {
        out.writeUTF(plot.id()); out.writeUTF(plot.stage()); out.writeInt(plot.x()); out.writeInt(plot.z());
        out.writeInt(plot.width()); out.writeInt(plot.height()); out.writeInt(plot.depth());
        out.writeInt(plot.controllerX()); out.writeInt(plot.controllerY()); out.writeInt(plot.controllerZ());
        out.writeUTF(plot.patternResource()); out.writeInt(plot.slotIndex()); out.writeUTF(plot.theme()); out.writeUTF(plot.displayNameKey());
    }
    private static Plan build(PlotSettings.RoadStyle style) {
        return switch (style) {
            case CAMPUS -> {
                var b = LegacyPlannedCampus.bounds(); var ae = LegacyPlannedCampus.aeCoreBounds();
                yield plan(LegacyPlannedCampus.plots().stream().map(p -> new PlannedCampus.Plot(
                        p.id(), p.stage(), p.x(), p.z(), p.width(), p.height(), p.depth(), p.controllerX(), p.controllerY(),
                        p.controllerZ(), p.patternResource(), p.slotIndex(), p.theme())).toList(),
                        LegacyPlannedCampus.workshops().stream().map(w -> new PlannedCampus.Workshop(w.stage(), w.x(), w.z(), w.width(), w.depth())).toList(),
                        new PlannedCampus.Bounds(b.minX(), b.minZ(), b.maxX(), b.maxZ()),
                        new PlannedCampus.Bounds(ae.minX(), ae.minZ(), ae.maxX(), ae.maxZ()), id -> -1);
            }
            case CAMPUS_PARK -> {
                var b = ParkPlannedCampus.bounds(); var ae = ParkPlannedCampus.aeCoreBounds();
                yield plan(ParkPlannedCampus.plots().stream().map(p -> new PlannedCampus.Plot(
                        p.id(), p.stage(), p.x(), p.z(), p.width(), p.height(), p.depth(), p.controllerX(), p.controllerY(),
                        p.controllerZ(), p.patternResource(), p.slotIndex(), p.theme())).toList(),
                        ParkPlannedCampus.workshops().stream().map(w -> new PlannedCampus.Workshop(w.stage(), w.x(), w.z(), w.width(), w.depth())).toList(),
                        new PlannedCampus.Bounds(b.minX(), b.minZ(), b.maxX(), b.maxZ()),
                        new PlannedCampus.Bounds(ae.minX(), ae.minZ(), ae.maxX(), ae.maxZ()), ParkPlannedCampus::planningTier);
            }
            case CAMPUS_COMPACT -> {
                var b = CompactPlannedCampus.bounds(); var ae = CompactPlannedCampus.aeCoreBounds();
                yield plan(CompactPlannedCampus.plots().stream().map(p -> new PlannedCampus.Plot(
                        p.id(), p.stage(), p.x(), p.z(), p.width(), p.height(), p.depth(), p.controllerX(), p.controllerY(),
                        p.controllerZ(), p.patternResource(), p.slotIndex(), p.theme())).toList(),
                        CompactPlannedCampus.workshops().stream().map(w -> new PlannedCampus.Workshop(w.stage(), w.x(), w.z(), w.width(), w.depth())).toList(),
                        new PlannedCampus.Bounds(b.minX(), b.minZ(), b.maxX(), b.maxZ()),
                        new PlannedCampus.Bounds(ae.minX(), ae.minZ(), ae.maxX(), ae.maxZ()), CompactPlannedCampus::planningTier);
            }
            case CAMPUS_DENSE -> {
                var b = DensePlannedCampus.bounds(); var ae = DensePlannedCampus.aeCoreBounds();
                yield plan(DensePlannedCampus.plots().stream().map(p -> new PlannedCampus.Plot(
                        p.id(), p.stage(), p.x(), p.z(), p.width(), p.height(), p.depth(), p.controllerX(), p.controllerY(),
                        p.controllerZ(), p.patternResource(), p.slotIndex(), p.theme())).toList(),
                        DensePlannedCampus.workshops().stream().map(w -> new PlannedCampus.Workshop(w.stage(), w.x(), w.z(), w.width(), w.depth())).toList(),
                        new PlannedCampus.Bounds(b.minX(), b.minZ(), b.maxX(), b.maxZ()),
                        new PlannedCampus.Bounds(ae.minX(), ae.minZ(), ae.maxX(), ae.maxZ()), DensePlannedCampus::planningTier);
            }
            case CAMPUS_WOODLAND -> plan(PlannedCampus.plots(), PlannedCampus.workshops(), PlannedCampus.bounds(),
                    PlannedCampus.aeCoreBounds(), PlannedCampus::planningTier);
            case CAMPUS_FACTORY -> filePlan(FileCampus.of("quartz_works"));
            default -> throw new IllegalArgumentException("Not a campus preset");
        };
    }
    private static Plan filePlan(FileCampus campus) {
        Plan base = plan(campus.plots(), campus.workshops(), campus.bounds(), campus.aeCoreBounds(), campus::planningTier);
        List<Target> targets = new ArrayList<>(base.targets());
        for (Target target : base.targets()) {
            if (target.kind() != Kind.WORKSHOP) continue;
            var bounds = target.bounds();
            var hall = campus.hallAt((bounds.minX() + bounds.maxX()) / 2, (bounds.minZ() + bounds.maxZ()) / 2);
            targets.set(target.index(), new Target(target.index(), target.kind(), target.bounds(), null,
                    target.stage(), target.ordinal(), (int) Math.floor(hall.arrivalX()), (int) Math.floor(hall.arrivalZ())));
        }
        return new Plan(base.plots(), base.workshops(), base.bounds(), base.aeBounds(), base.tiers(), List.copyOf(targets));
    }
    private static Plan plan(List<PlannedCampus.Plot> plots, List<PlannedCampus.Workshop> workshops,
                             PlannedCampus.Bounds bounds, PlannedCampus.Bounds aeBounds, ToIntFunction<String> tier) {
        List<Target> targets = new ArrayList<>();
        for (var plot : plots) targets.add(new Target(targets.size(), Kind.MACHINE, plot.bounds(), plot,
                plot.stage(), plot.slotIndex() + 1, plot.signX(), plot.signZ()));
        targets.add(new Target(targets.size(), Kind.AE_CORE, aeBounds, null, "ae", 0,
                (aeBounds.minX() + aeBounds.maxX()) / 2, (aeBounds.minZ() + aeBounds.maxZ()) / 2));
        for (int i = 0; i < workshops.size(); i++) {
            var w = workshops.get(i);
            targets.add(new Target(targets.size(), Kind.WORKSHOP,
                    new PlannedCampus.Bounds(w.x(), w.z(), w.x() + w.width() - 1, w.z() + w.depth() - 1),
                    null, w.stage(), i + 1, w.x() + w.width() / 2, w.z() - 3));
        }
        return new Plan(List.copyOf(plots), List.copyOf(workshops), bounds, aeBounds,
                plots.stream().map(p -> tier.applyAsInt(p.id())).toList(), List.copyOf(targets));
    }
}
