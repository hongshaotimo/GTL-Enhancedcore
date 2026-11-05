package com.gtl.enhancedcore.common.world.plot;

import java.util.List;
import java.util.Set;

/** Stable published factory alias; all geometry and parameters load through the shared file engine. */
public final class FactoryCampus {
    public static final String CATALOG = "/data/gtl_enhancedcore/worldgen/factory_campus_machines.json";
    public static final String LAYOUT = "/data/gtl_enhancedcore/worldgen/factory_campus_layout.json";
    public static final String PROGRAM = "/data/gtl_enhancedcore/worldgen/factory_campus_program.json";
    private static final class Holder { static final FileCampus DATA = FileCampus.of("quartz_works"); }
    private FactoryCampus() {}
    public static Set<String> materials() { return Holder.DATA.materials(); }
    public static List<PlannedCampus.Plot> plots() { return Holder.DATA.plots(); }
    public static List<FactoryArchitecture.Hall> halls() { return Holder.DATA.halls(); }
    public static List<FactoryArchitecture.Hall> coveredHalls() { return Holder.DATA.coveredHalls(); }
    public static List<FactoryArchitecture.Hall> outdoorSites() { return Holder.DATA.outdoorSites(); }
    public static List<PlannedCampus.Workshop> workshops() { return Holder.DATA.workshops(); }
    public static PlannedCampus.Bounds bounds() { return Holder.DATA.bounds(); }
    public static PlannedCampus.Bounds aeCoreBounds() { return Holder.DATA.aeCoreBounds(); }
    public static PlannedCampus.Bounds previewBounds(boolean overview) { return Holder.DATA.previewBounds(overview); }
    public static int sharedMachineCount() { return Holder.DATA.sharedMachineCount(); }
    public static int planningTier(String id) { return Holder.DATA.planningTier(id); }
    public static int maxHeight() { return Holder.DATA.maxHeight(); }
    public static boolean fitsHeight(PlotSettings settings) { return Holder.DATA.fitsHeight(settings); }
    public static boolean hasTerrain(int x, int z) { return Holder.DATA.hasTerrain(x,z); }
    public static FactoryArchitecture.Hall hallAt(int x,int z) { return Holder.DATA.hallAt(x,z); }
    public static PlannedCampus.Plot plotAt(int x,int z) { return Holder.DATA.plotAt(x,z); }
    public static String surfaceBlock(PlotSettings settings,int x,int z) { return Holder.DATA.surfaceBlock(settings,x,z); }
    public static String foundationBlockAt(int x,int y,int z) { return Holder.DATA.foundationBlockAt(x,y,z); }
    public static String blockAboveAt(PlotSettings settings,int x,int y,int z) { return Holder.DATA.blockAboveAt(settings,x,y,z); }
    public static int maxDecorationYAt(PlotSettings settings,int x,int z) { return Holder.DATA.maxDecorationYAt(settings,x,z); }
    public static int previewColorAt(int x,int z) { return Holder.DATA.previewColorAt(x,z); }
}
