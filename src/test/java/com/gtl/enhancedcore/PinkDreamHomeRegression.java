package com.gtl.enhancedcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.plot.CampusPresetRegistry;
import com.gtl.enhancedcore.common.world.plot.CampusScene;
import com.gtl.enhancedcore.common.world.plot.FileCampus;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Identity, catalog, bounds and scenery boundary contract for the 1500-square pink preset. */
final class PinkDreamHomeRegression {
    private static int checks;
    private static final Set<String> FALLING_BLOCKS = Set.of(
            "minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
            "minecraft:suspicious_sand", "minecraft:suspicious_gravel",
            "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil",
            "minecraft:white_concrete_powder", "minecraft:orange_concrete_powder", "minecraft:magenta_concrete_powder",
            "minecraft:light_blue_concrete_powder", "minecraft:yellow_concrete_powder", "minecraft:lime_concrete_powder",
            "minecraft:pink_concrete_powder", "minecraft:gray_concrete_powder", "minecraft:light_gray_concrete_powder",
            "minecraft:cyan_concrete_powder", "minecraft:purple_concrete_powder", "minecraft:blue_concrete_powder",
            "minecraft:brown_concrete_powder", "minecraft:green_concrete_powder", "minecraft:red_concrete_powder",
            "minecraft:black_concrete_powder");
    static int run() throws Exception {
        checks=0;
        var definition=CampusPresetRegistry.get("pink_dream_home_v1");
        check(definition.engine().equals("file_scene")
                        && definition.nameKey().equals("gtl_enhancedcore.create_world_plot.pink_dream_home_v1"),
                "The pink dream preset has its own registered name and file-scene engine");
        check(!CampusPresetRegistry.contains("celestial_megastructure")
                        && !definition.id().equals("quartz_works"),
                "The new theme stays distinct from both the removed preset and frozen Quartz Works");
        var campus=FileCampus.of(definition.id());
        check(campus.bounds().width()==1500 && campus.bounds().depth()==1500
                        && campus.bounds().minX()==-750 && campus.bounds().minZ()==-750
                        && campus.bounds().maxX()==749 && campus.bounds().maxZ()==749,
                "The world boundary is exactly 1500 by 1500 blocks");
        check(campus.aeCoreBounds().width()==160 && campus.aeCoreBounds().depth()==160,
                "The central AE hub retains the documented 160-square footprint");
        check(campus.plots().size()==225 && campus.sharedMachineCount()==46,
                "All 225 full-envelope machine bays and 46 shared machine IDs are retained");
        check(campus.halls().size()==70 && campus.outdoorSites().size()==53
                        && campus.coveredHalls().size()==17 && campus.workshops().size()==4,
                "The replacement layout has 70 sites, 53 open-air machine areas and four shared workshops");
        check(campus.coveredHalls().stream().allMatch(h->h.width()<=114 && h.depth()<=114),
                "Covered homes stay within the smaller neighborhood scale");
        check(campus.maxHeight()<=372 && campus.fitsHeight(PlotSettings.fileCampus(definition.id())),
                "The complete authored scene fits below the overworld build ceiling at the default surface");

        JsonObject catalog=resource(definition.catalogResource());
        JsonObject factory=resource("/data/gtl_enhancedcore/worldgen/factory_campus_machines.json");
        check(catalog.toString().equals(factory.toString()),
                "The new theme snapshots every published machine/module fact without changing geometry metadata");
        Set<String> expected=new HashSet<>();
        for(var item:catalog.getAsJsonArray("reservations")) {
            var machine=item.getAsJsonObject();
            for(int slot=0;slot<machine.get("copies").getAsInt();slot++) expected.add(machine.get("id").getAsString()+"#"+slot);
        }
        Set<String> placed=new HashSet<>();
        for(var plot:resource(definition.layoutResource()).getAsJsonArray("plots")) {
            var row=plot.getAsJsonObject();
            check(placed.add(row.get("id").getAsString()+"#"+row.get("slotIndex").getAsInt()),
                    "A reservation slot is not placed twice");
        }
        check(placed.equals(expected), "The 1500-square layout places every catalog copy once and only once");

        JsonObject architecture=resource(definition.architectureResource());
        var scene=new CampusScene(architecture);
        JsonObject audit=architecture.getAsJsonObject("design").getAsJsonObject("validation");
        check(scene.shapeCount()>=1000 && scene.materials().contains("minecraft:air")
                        && scene.materials().contains("minecraft:cherry_leaves[persistent=true,distance=1]"),
                "The theme loads its reusable cherry/dessert scene and explicit air-cut palette");
        check(scene.materials().contains("minecraft:calcite")
                        && !scene.materials().contains("minecraft:white_concrete_powder"),
                "The garden's pale sand decoration uses stable calcite instead of falling concrete powder");
        check(scene.materials().contains("minecraft:pink_petals[flower_amount=4,facing=north]"),
                "Pink petals use the actual Minecraft 1.20.1 flower_amount state property");
        var counts=architecture.getAsJsonObject("design").getAsJsonObject("counts");
        check(counts.get("lollipop_landmarks").getAsInt()>=8
                        && counts.get("architectural_bows").getAsInt()>=4,
                "The scene has visible lollipop markers and bow decorations");
        check(counts.get("connected_hall_walks").getAsInt()==campus.coveredHalls().size()
                        && counts.get("shared_entry_walks_to_ae").getAsInt()==4,
                "Every covered entry has an approach and the four shared doors connect to the AE plaza");
        for (String material : scene.materials()) {
            String blockId = material.substring(0, material.indexOf('[') < 0 ? material.length() : material.indexOf('['));
            check(blockId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), "Every authored scene material has a valid block ID: " + material);
            check(!FALLING_BLOCKS.contains(blockId), "Every authored scene material is stable against gravity: " + material);
        }
        check(scene.maxHeight()<=372 && audit.get("sceneMachineCollisions").getAsInt()==0
                        && audit.get("teleporterLandingClear").getAsBoolean(),
                "Authored scenery remains below the ceiling and outside machine envelopes and the teleporter landing");
        String landing=scene.blockAt(0,1,0);
        check(landing==null||landing.equals("minecraft:air"), "The central teleporter block remains unobstructed");
        String centralFloor=campus.surfaceBlock(null,0,0);
        check(!centralFloor.equals("minecraft:air")&&!centralFloor.startsWith("minecraft:water["),
                "The central arrival device sits on a dry, generated surface");
        var ae=campus.aeCoreBounds();
        var shared=campus.halls().stream().filter(h->h.id().startsWith("shared-")).toList();
        check(shared.size()==4,"Four shared courts surround the AE core");
        for(var hall:shared) {
            double before=distanceTo(ae,hall.entranceX(),hall.entranceZ());
            double after=distanceTo(ae,hall.arrivalX(),hall.arrivalZ());
            check(after<before,"The shared door faces into the AE core: "+hall.id());
            int fixed,from,to;
            switch(hall.entranceFacing()) {
                case "south" -> { fixed=hall.x()+hall.width()/2; from=hall.maxZ()+1; to=0; }
                case "north" -> { fixed=hall.x()+hall.width()/2; from=hall.z()-1; to=0; }
                case "east" -> { fixed=hall.z()+hall.depth()/2; from=hall.maxX()+1; to=0; }
                case "west" -> { fixed=hall.z()+hall.depth()/2; from=hall.x()-1; to=0; }
                default -> throw new AssertionError("Unknown inward entry direction");
            }
            int step=from<=to?1:-1;
            for(int n=from;n!=to+step;n+=step) {
                int x=hall.entranceFacing().equals("south")||hall.entranceFacing().equals("north")?fixed:n;
                int z=hall.entranceFacing().equals("south")||hall.entranceFacing().equals("north")?n:fixed;
                check(campus.surfaceBlock(null,x,z).equals("minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                        "The inward shared entrance has a continuous walk toward the central axis: "+hall.id());
            }
            if(hall.entranceFacing().equals("north")||hall.entranceFacing().equals("south")) {
                for(int x=Math.min(fixed,0);x<=Math.max(fixed,0);x++)
                    check(campus.surfaceBlock(null,x,0).equals("minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                            "The shared path reaches the central player position: "+hall.id());
            } else {
                for(int z=Math.min(fixed,0);z<=Math.max(fixed,0);z++)
                    check(campus.surfaceBlock(null,0,z).equals("minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]"),
                            "The shared path reaches the central player position: "+hall.id());
            }
        }
        checkEntranceWalkClearance(campus,scene,architecture);
        checkLollipopVisibility(architecture,scene);
        check(!CampusPlanAccess.layoutIdentity(PlotSettings.fileCampus(definition.id()))
                        .equals(CampusPlanAccess.layoutIdentity(PlotSettings.fileCampus("quartz_works"))),
                "Map, search and teleport plans retain separate identities for the new theme");
        return checks;
    }
    /** Inspect the actual final scene, including later instance and direct-shape overrides. */
    private static void checkEntranceWalkClearance(FileCampus campus,CampusScene scene,JsonObject architecture) {
        var routes=new HashMap<String,JsonObject>();
        for(var entry:architecture.getAsJsonObject("design").getAsJsonObject("validation").getAsJsonArray("hallWalks")) {
            var route=entry.getAsJsonObject();
            check(routes.put(route.get("hall").getAsString(),route)==null,"Each covered entrance declares one approach route");
        }
        check(routes.size()==17,"All 17 actual approach routes are declared for independent scene sampling");
        int walks=0;
        for(var hall:campus.coveredHalls()) {
            var route=routes.get(hall.id());
            check(route!=null,"Every covered entrance has a declared approach route: "+hall.id());
            Set<Long> cells=new HashSet<>();
            int cx=hall.x()+hall.width()/2,cz=hall.z()+hall.depth()/2;
            String facing=hall.entranceFacing();
            boolean alongZ=facing.equals("north")||facing.equals("south");
            int edge=switch(facing) {
                case "north" -> hall.z();
                case "south" -> hall.maxZ();
                case "west" -> hall.x();
                case "east" -> hall.maxX();
                default -> throw new AssertionError("Unknown entrance direction: "+facing);
            };
            int doorCenter=alongZ?hall.x()+(hall.width()-1)/2:hall.z()+(hall.depth()-1)/2;
            addWalk(cells,alongZ?doorCenter:edge-3,alongZ?edge-3:doorCenter,
                    alongZ?doorCenter:edge+3,alongZ?edge+3:doorCenter,6);
            if(hall.id().startsWith("shared-")) {
                int from=edge+(facing.equals("north")||facing.equals("west")?-1:1);
                if(alongZ) {
                    addWalk(cells,cx,from,cx,0,2);
                    addWalk(cells,cx,0,0,0,0);
                } else {
                    addWalk(cells,from,cz,0,cz,2);
                    addWalk(cells,0,cz,0,0,0);
                }
            } else {
                var entry=route.getAsJsonArray("entry");
                int px=entry.get(0).getAsInt(),pz=entry.get(1).getAsInt();
                check(alongZ?px==cx:pz==cz,"The declared approach stays aligned with its actual doorway: "+hall.id());
                switch(facing) {
                    case "north" -> { check(pz<hall.z(),"North approach remains outside its doorway"); addWalk(cells,cx,pz,cx,hall.z()-1,2); }
                    case "south" -> { check(pz>hall.maxZ(),"South approach remains outside its doorway"); addWalk(cells,cx,hall.maxZ()+1,cx,pz,2); }
                    case "west" -> { check(px<hall.x(),"West approach remains outside its doorway"); addWalk(cells,px,cz,hall.x()-1,cz,2); }
                    case "east" -> { check(px>hall.maxX(),"East approach remains outside its doorway"); addWalk(cells,hall.maxX()+1,cz,px,cz,2); }
                }
                var crossing=route.getAsJsonArray("roadCrossing");
                int roadX=crossing.get(0).getAsInt();
                check((roadX==-8||roadX==7)&&crossing.get(1).getAsInt()==0,
                        "The approach meets the central road axis: "+hall.id());
                var waypoints=route.getAsJsonArray("waypoints");
                check(waypoints!=null&&waypoints.size()>=2,"A covered approach declares its continuous route: "+hall.id());
                var first=waypoints.get(0).getAsJsonArray();
                var last=waypoints.get(waypoints.size()-1).getAsJsonArray();
                check(first.size()==2&&first.get(0).getAsInt()==px&&first.get(1).getAsInt()==pz,
                        "The approach route begins at its doorway entry: "+hall.id());
                check(last.size()==2&&last.get(0).getAsInt()==roadX&&last.get(1).getAsInt()==0,
                        "The approach route ends at the declared central road crossing: "+hall.id());
                for(int i=1;i<waypoints.size();i++) {
                    var start=waypoints.get(i-1).getAsJsonArray();
                    var end=waypoints.get(i).getAsJsonArray();
                    check(start.size()==2&&end.size()==2,"Every approach waypoint has X and Z coordinates: "+hall.id());
                    addWalk(cells,start.get(0).getAsInt(),start.get(1).getAsInt(),
                            end.get(0).getAsInt(),end.get(1).getAsInt(),2);
                }
            }
            for(long cell:cells) {
                int x=(int)(cell>>32),z=(int)cell;
                String machine=null;
                for(var plot:campus.plots()) if(plot.contains(x,z)) {
                    machine=plot.id()+"#"+plot.slotIndex();break;
                }
                check(machine==null,"Entrance walk must avoid the entire reserved machine footprint: "+hall.id()
                        +" at "+x+","+z+" intersects "+machine);
                for(int y=1;y<=3;y++) {
                    String block=scene.blockAt(x,y,z);
                    check(block==null||block.equals("minecraft:air"),
                            "Entrance walk must remain clear above its floor: "+hall.id()+" at "+x+","+y+","+z+" contains "+block);
                }
            }
            walks++;
        }
        check(walks==17,"All 17 covered entrance walks receive final-scene clearance checks");
    }
    private static void addWalk(Set<Long> cells,int x1,int z1,int x2,int z2,int halfWidth) {
        check(x1==x2||z1==z2,"Walk segments must follow one cardinal axis");
        int minX=Math.min(x1,x2),maxX=Math.max(x1,x2),minZ=Math.min(z1,z2),maxZ=Math.max(z1,z2);
        if(x1==x2) { minX-=halfWidth;maxX+=halfWidth; }
        else { minZ-=halfWidth;maxZ+=halfWidth; }
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++)
            cells.add(((long)x<<32)^(z&0xffffffffL));
    }
    /** Compare each candy head with its isolated template so foliage cannot hide the theme. */
    private static void checkLollipopVisibility(JsonObject architecture,CampusScene scene) {
        var authored=architecture.getAsJsonObject("scene");
        int candies=0;
        for(var element:authored.getAsJsonArray("instances")) {
            var instance=element.getAsJsonObject();
            if(!instance.get("template").getAsString().equals("lollipop")) continue;
            JsonObject isolated=new JsonObject(),singleScene=new JsonObject();
            isolated.addProperty("schemaVersion",1);isolated.add("scene",singleScene);
            singleScene.add("palette",authored.get("palette").deepCopy());
            singleScene.add("templates",authored.get("templates").deepCopy());
            JsonArray oneInstance=new JsonArray();oneInstance.add(instance.deepCopy());
            singleScene.add("instances",oneInstance);
            CampusScene candy=new CampusScene(isolated);
            int ox=instance.get("x").getAsInt(),oz=instance.get("z").getAsInt();
            int oy=instance.get("y").getAsInt(),headVoxels=0,visibleVoxels=0;
            for(int x=ox-5;x<=ox+5;x++) for(int z=oz-5;z<=oz+5;z++) for(int y=oy+9;y<=oy+17;y++) {
                String expected=candy.blockAt(x,y,z);
                if(expected==null||expected.equals("minecraft:air")) continue;
                headVoxels++;
                if(expected.equals(scene.blockAt(x,y,z))) visibleVoxels++;
            }
            check(headVoxels>0&&visibleVoxels*10>=headVoxels*9,
                    "Each lollipop head must retain at least 90 percent of its actual template: "+ox+","+oz
                            +" retains "+visibleVoxels+"/"+headVoxels);
            candies++;
        }
        check(candies>=8,"Candy visibility is checked on actual lollipop instances");
    }
    private static JsonObject resource(String path) throws Exception {
        try(var reader=new InputStreamReader(PinkDreamHomeRegression.class.getResourceAsStream(path),StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static double distanceTo(com.gtl.enhancedcore.common.world.plot.PlannedCampus.Bounds bounds,double x,double z) {
        double dx=x<bounds.minX()?bounds.minX()-x:x>bounds.maxX()?x-bounds.maxX():0;
        double dz=z<bounds.minZ()?bounds.minZ()-z:z>bounds.maxZ()?z-bounds.maxZ():0;
        return Math.hypot(dx,dz);
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
}
