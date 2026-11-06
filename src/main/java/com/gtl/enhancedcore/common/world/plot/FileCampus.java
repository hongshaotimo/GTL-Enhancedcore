package com.gtl.enhancedcore.common.world.plot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

/** Shared file-backed terrain engine; every preset owns separate immutable geometry and material state. */
public final class FileCampus {
    private static final Map<String, FileCampus> CACHE = new ConcurrentHashMap<>();
    private final CampusPresetRegistry.Definition definition;
    private final FactoryArchitecture.Renderer renderer;
    private final CampusScene scene;
    private final boolean physicalSigns;
    private final String QUARTZ,DARK,FLOOR,ROAD,LIGHT,YELLOW,GREEN,LEAVES,SLAB,IRON,COLUMN,ACCENT;
    private final PlannedCampus.Bounds BOUNDS,AE;
    private final Set<String> MATERIALS;
    private final Plan plan;
    private final List<PlannedCampus.Bounds> gardens;
    public static FileCampus of(String id) {
        return CACHE.computeIfAbsent(id, key -> new FileCampus(CampusPresetRegistry.get(key)));
    }
    public FileCampus(CampusPresetRegistry.Definition definition) {
        this.definition=definition;
        if(!Set.of("quartz_factory", "file_scene").contains(definition.engine()))
            throw new IllegalArgumentException("Unknown campus engine: "+definition.engine());
        JsonObject layout=read(definition.layoutResource());
        JsonObject architecture=read(definition.architectureResource());
        physicalSigns=!architecture.has("physicalSigns") || architecture.get("physicalSigns").getAsBoolean();
        renderer=definition.engine().equals("quartz_factory")
                ? FactoryArchitecture.forResource(definition.architectureResource()) : null;
        scene=definition.engine().equals("file_scene") ? new CampusScene(architecture) : null;
        var m=definition.materials();
        QUARTZ=m.get("quartz");DARK=m.get("dark");FLOOR=m.get("floor");ROAD=m.get("road");
        LIGHT=m.get("light");YELLOW=m.get("yellow");GREEN=m.get("green");LEAVES=m.get("leaves");
        SLAB=m.get("slab");IRON=m.get("iron");COLUMN=m.get("column");ACCENT=m.get("accent");
        Set<String> palette=new LinkedHashSet<>();
        if(renderer!=null) palette.addAll(renderer.materials());
        if(scene!=null) palette.addAll(scene.materials());
        palette.addAll(m.values());MATERIALS=Set.copyOf(palette);
        BOUNDS=readBounds(layout,"bounds");AE=readBounds(layout,"aeBounds");
        plan=build(read(definition.catalogResource()),layout);
        gardens=renderer==null?List.of():buildGardens();
    }
    private static PlannedCampus.Bounds readBounds(JsonObject layout,String key) {
        return readBounds(layout.getAsJsonArray(key));
    }
    private static PlannedCampus.Bounds readBounds(com.google.gson.JsonArray b) {
        return new PlannedCampus.Bounds(b.get(0).getAsInt(),b.get(1).getAsInt(),b.get(2).getAsInt(),b.get(3).getAsInt());
    }
    private int dimension(String key) { return definition.dimensions().get(key); }
    public String id() { return definition.id(); }
    public String catalogResource() { return definition.catalogResource(); }
    public CampusPresetRegistry.Definition definition() { return definition; }
    public boolean physicalSigns() { return physicalSigns; }
    public String resourceIdentity() {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            for(String value:List.of(definition.id(),definition.engine(),new TreeMap<>(definition.materials()).toString(),
                    new TreeMap<>(definition.dimensions()).toString(),new TreeMap<>(definition.previewColors()).toString()))
                digest.update(value.getBytes(StandardCharsets.UTF_8));
            for(String resource:List.of(definition.catalogResource(),definition.layoutResource(),definition.architectureResource()))
                try(var stream=FileCampus.class.getResourceAsStream(resource)) { digest.update(stream.readAllBytes()); }
            return HexFormat.of().formatHex(digest.digest());
        } catch(IOException | NoSuchAlgorithmException error) { throw new IllegalStateException("Cannot fingerprint preset resources",error); }
    }
    private record Plan(List<PlannedCampus.Plot> plots, List<FactoryArchitecture.Hall> halls,
                        List<PlannedCampus.Workshop> workshops, Map<Long,List<FactoryArchitecture.Hall>> tiles,
                        Map<String,Integer> tiers, int shared, int height) {}
    public Set<String> materials() { return MATERIALS; }
    public List<PlannedCampus.Plot> plots() { return plan.plots; }
    public List<FactoryArchitecture.Hall> halls() { return plan.halls; }
    public List<FactoryArchitecture.Hall> coveredHalls() { return halls().stream().filter(h->!h.outdoor()).toList(); }
    public List<FactoryArchitecture.Hall> outdoorSites() { return halls().stream().filter(FactoryArchitecture.Hall::outdoor).toList(); }
    public List<PlannedCampus.Workshop> workshops() { return plan.workshops; }
    public PlannedCampus.Bounds bounds() { return BOUNDS; }
    public PlannedCampus.Bounds aeCoreBounds() { return AE; }
    public PlannedCampus.Bounds previewBounds(boolean overview) {
        return overview ? BOUNDS : new PlannedCampus.Bounds(-320,-320,319,319);
    }
    public int sharedMachineCount() { return plan.shared; }
    public int planningTier(String id) { return plan.tiers.get(id); }
    public int maxHeight() { return scene==null?plan.height:Math.max(plan.height,scene.maxHeight()); }
    public boolean fitsHeight(PlotSettings settings) { return settings.surfaceY()+maxHeight()<=319; }
    public boolean hasTerrain(int x, int z) { return BOUNDS.contains(x,z); }
    private long key(int x,int z) { return (long)x << 32 ^ z & 0xffffffffL; }
    private List<FactoryArchitecture.Hall> nearby(int x,int z) {
        return plan.tiles.getOrDefault(key(Math.floorDiv(x,64),Math.floorDiv(z,64)),List.of());
    }
    public FactoryArchitecture.Hall hallAt(int x,int z) {
        for (var hall : nearby(x,z)) if (hall.contains(x,z)) return hall;
        return null;
    }
    public PlannedCampus.Plot plotAt(int x,int z) {
        for (var plot : plots()) if (plot.contains(x,z)) return plot;
        return null;
    }
    private int fold(int x) { return x<0 ? -x-1 : x; }
    private List<PlannedCampus.Bounds> buildGardens() {
        List<PlannedCampus.Bounds> result=new ArrayList<>();
        int period=renderer.landscapePeriod(),road=dimension("mainRoadHalfWidth");
        for(int x=BOUNDS.minX()+64;x<BOUNDS.maxX()-32;x+=period)
            for(int z=BOUNDS.minZ()+64;z<BOUNDS.maxZ()-32;z+=period) {
                if(fold(x)<road+16 || fold(z)<road+16 || AE.contains(x,z)) continue;
                boolean clear=true;
                for(var hall:halls()) if(x+23>=hall.x() && x-23<=hall.maxX() && z+23>=hall.z() && z-23<=hall.maxZ()) {clear=false;break;}
                if(clear) result.add(new PlannedCampus.Bounds(x-11,z-11,x+11,z+11));
            }
        return List.copyOf(result);
    }
    private PlannedCampus.Bounds gardenAt(int x,int z) {
        for(var garden:gardens) if(garden.contains(x,z)) return garden;
        return null;
    }
    private int siteDistance(FactoryArchitecture.Hall hall,int x,int z) {
        return Math.max(Math.max(hall.x()-x,x-hall.maxX()),Math.max(hall.z()-z,z-hall.maxZ()));
    }
    public String surfaceBlock(PlotSettings settings,int x,int z) {
        if (!hasTerrain(x,z)) return "minecraft:air";
        if(scene!=null) {
            String authored=scene.blockAt(x,0,z);
            if(authored!=null) return authored;
            if(AE.contains(x,z)) return QUARTZ;
            var hall=hallAt(x,z);
            if(hall!=null) return FLOOR;
            int edgeX=Math.min(x-BOUNDS.minX(),BOUNDS.maxX()-x),edgeZ=Math.min(z-BOUNDS.minZ(),BOUNDS.maxZ()-z);
            int walk=dimension("boundaryWalkWidth"),inset=dimension("outerRoadInset"),halfRoad=dimension("mainRoadHalfWidth");
            if(edgeX<walk||edgeZ<walk) return QUARTZ;
            if(Math.abs(x)<halfRoad||Math.abs(z)<halfRoad||edgeX<inset||edgeZ<inset) return ROAD;
            for(var candidate:nearby(x,z)) if(siteDistance(candidate,x,z)<dimension("serviceRingWidth")) return ROAD;
            return GREEN;
        }
        if (AE.contains(x,z)) return coreFloor(x,z);
        var hall = hallAt(x,z);
        if (hall != null) return renderer.surface(hall,x,z);
        var garden=gardenAt(x,z);
        if(garden!=null) return renderer.gardenSurface(x-garden.minX()-11,z-garden.minZ()-11);
        int ax=fold(x),az=fold(z);
        int edgeX=Math.min(x-BOUNDS.minX(),BOUNDS.maxX()-x),edgeZ=Math.min(z-BOUNDS.minZ(),BOUNDS.maxZ()-z);
        int walk=dimension("boundaryWalkWidth"),inset=dimension("outerRoadInset"),halfRoad=dimension("mainRoadHalfWidth");
        if (edgeX<walk || edgeZ<walk) return edgeX==walk-1 || edgeZ==walk-1 ? DARK : QUARTZ;
        if (ax<halfRoad || az<halfRoad || edgeX<inset || edgeZ<inset) {
            int across=Math.min(ax,az),along= ax<az ? z : x;
            if (edgeX<inset || edgeZ<inset) across=Math.min(Math.abs(edgeX-(inset-walk)),Math.abs(edgeZ-(inset-walk)));
            if (across==0 && Math.floorMod(along,12)<7) return YELLOW;
            if (across==halfRoad-2 || across==halfRoad-1) return QUARTZ;
            return ROAD;
        }
        for (var candidate:nearby(x,z)) {
            int distance=siteDistance(candidate,x,z);
            if (distance<=3) return FLOOR;
            if (distance<dimension("serviceRingWidth")) return distance==dimension("serviceRingWidth")-1 ? DARK : ROAD;
        }
        // Large paved service courts, with restrained planted drainage strips.
        if ((ax<96 || az<96) && ax<276 && az<276) return FLOOR;
        int gx=Math.floorMod(x,dimension("gridPeriod")),gz=Math.floorMod(z,dimension("gridPeriod"));
        if (gx<3 || gz<3) return QUARTZ;
        boolean roadsidePlanting = gx>=7 && gx<=10 && gz>=12 && gz<=20
                || gz>=7 && gz<=10 && gx>=12 && gx<=20;
        boolean boundaryPlanting = (edgeX>=walk+3 && edgeX<=walk+6 || edgeZ>=walk+3 && edgeZ<=walk+6)
                && Math.floorMod(x+z,128)<40;
        return roadsidePlanting || boundaryPlanting ? GREEN : FLOOR;
    }
    private String coreFloor(int x,int z) {
        int ax=fold(x),az=fold(z),r=Math.max(ax,az);
        if(Math.abs(ax-44)<=11 && Math.abs(az-44)<=11) return renderer.gardenSurface(ax-44,az-44);
        if (r>=76) return QUARTZ;
        if (ax<8 || az<8) return ax==7 || az==7 ? DARK : FLOOR;
        if (r<18) return r==17 ? ACCENT : QUARTZ;
        if (ax==24 || az==24 || ax==64 || az==64) return DARK;
        if (ax>65 || az>65) return FLOOR;
        return QUARTZ;
    }
    public String foundationBlockAt(int x,int y,int z) {
        if (y>=0 || !hasTerrain(x,z)) return null;
        if(scene!=null) return scene.blockAt(x,y,z);
        // Two walkable service galleries beneath the dispatch hub, with real floors and ceilings.
        if (Math.abs(x)<=4 || Math.abs(z)<=4) {
            if (y==-7) return FLOOR;
            if (y>=-6 && y<=-3) return Math.abs(x)==4 && Math.abs(z)>4
                    || Math.abs(z)==4 && Math.abs(x)>4 ? DARK : "minecraft:air";
            if (y==-2) return Math.floorMod(x+z,16)==0 ? LIGHT : IRON;
        }
        if (x-BOUNDS.minX()<2 || BOUNDS.maxX()-x<2 || z-BOUNDS.minZ()<2 || BOUNDS.maxZ()-z<2) return y==-1 || y==-7 ? QUARTZ : DARK;
        return null;
    }
    public String blockAboveAt(PlotSettings settings,int x,int y,int z) {
        if (!hasTerrain(x,z) || y<1) return null;
        if(scene!=null) return scene.blockAt(x,y,z);
        var hall=hallAt(x,z);
        if (hall!=null) return renderer.above(hall,x,y,z);
        if (AE.contains(x,z)) return coreAbove(x,y,z);
        var garden=gardenAt(x,z);
        if(garden!=null) return renderer.gardenAbove(x-garden.minX()-11,y,z-garden.minZ()-11);
        int ax=fold(x),az=fold(z);
        if (x-BOUNDS.minX()<3 || BOUNDS.maxX()-x<3 || z-BOUNDS.minZ()<3 || BOUNDS.maxZ()-z<3) return y==1 ? SLAB : null;
        // Twin-post modern street lamps remain outside every machine and building envelope.
        if ((ax==18 && Math.floorMod(z,48)==24) || (az==18 && Math.floorMod(x,48)==24))
            return y<=5 ? COLUMN : y==6 ? LIGHT : y==7 ? SLAB : null;
        for (var candidate:nearby(x,z)) {
            int distance=siteDistance(candidate,x,z);
            if (distance<=10) return null;
        }
        int gx=Math.floorMod(x,dimension("gridPeriod")),gz=Math.floorMod(z,dimension("gridPeriod"));
        if (surfaceBlock(settings,x,z).equals(GREEN)
                && (gx==8 && gz>=8 && gz<=14 || gz==8 && gx>=8 && gx<=14))
            return y==1 ? LEAVES : null;
        return null;
    }
    private String coreAbove(int x,int y,int z) {
        int ax=fold(x),az=fold(z);
        if(Math.abs(ax-44)<=11 && Math.abs(az-44)<=11) return renderer.gardenAbove(ax-44,y,az-44);
        // An open cruciform dispatch plaza, framed by four monumental corner pylons.
        if (ax>=68 && ax<=72 && az>=68 && az<=72) {
            if (y<=22) return ax==68 || ax==72 || az==68 || az==72 ? QUARTZ : IRON;
            if (y==23) return LIGHT;
            if (y==24) return SLAB;
        }
        if (ax>=66 && az>=66 && y==25) return SLAB;
        if (ax>=28 && ax<=60 && az>=68 && az<=70) return y==1 ? QUARTZ : y==2 ? LEAVES : null;
        if (az>=28 && az<=60 && ax>=68 && ax<=70) return y==1 ? QUARTZ : y==2 ? LEAVES : null;
        return null;
    }
    public int maxDecorationYAt(PlotSettings settings,int x,int z) {
        if (!hasTerrain(x,z)) return 0;
        if(scene!=null) return scene.topAt(x,z);
        var hall=hallAt(x,z);
        if (hall!=null) return renderer.top(hall,x,z);
        if (AE.contains(x,z)) return 25;
        return 7;
    }
    public int previewColorAt(int x,int z) {
        if (!hasTerrain(x,z)) return preview("background");
        if(scene!=null) {
            int top=scene.topAt(x,z);
            for(int y=top;y>=0;y--) {
                String block=scene.blockAt(x,y,z);
                if(block!=null&&!block.equals("minecraft:air")) return preview(sceneColor(block));
            }
            return preview(previewColorForSurface(surfaceBlock(null,x,z)));
        }
        var hall=hallAt(x,z);
        if (hall!=null) {
            int u=x-hall.x(),v=z-hall.z();
            if (u<5 || v<14 || u>=hall.width()-5 || v>=hall.depth()-5) return preview("facade");
            return preview(definition.previewColors().containsKey(hall.department()) ? hall.department() : "precision");
        }
        String surface=surfaceBlock(null,x,z);
        if(surface.equals(ROAD)) return preview("road");
        if(surface.equals(DARK)) return preview("dark");
        if(surface.equals(GREEN)) return preview("green");
        if(surface.equals(FLOOR)) return preview("floor");
        if(surface.equals(YELLOW)) return preview("yellow");
        return preview("quartz");
    }
    private int preview(String name) { return 0xff000000 | definition.previewColors().get(name); }

    private String sceneColor(String block) {
        for(var entry:definition.materials().entrySet()) if(entry.getValue().equals(block)) {
            String key=entry.getKey();
            return switch(key) {
                case "quartz", "column", "slab" -> "quartz";
                case "dark", "iron", "accent" -> "dark";
                case "floor" -> "floor";
                case "road" -> "road";
                case "green", "leaves" -> "green";
                case "light", "yellow" -> "yellow";
                default -> "facade";
            };
        }
        if(block.contains("cherry_leaves")||block.contains("azalea_leaves")||block.contains("grass")||block.contains("flower")) return "green";
        if(block.contains("water")) return "background";
        return "facade";
    }

    private String previewColorForSurface(String block) {
        if(block.equals(ROAD)) return "road";
        if(block.equals(GREEN)) return "green";
        if(block.equals(FLOOR)) return "floor";
        if(block.equals(DARK)) return "dark";
        return "quartz";
    }

    private JsonObject read(String resource) {
        var stream=FileCampus.class.getResourceAsStream(resource);
        if (stream==null) throw new IllegalStateException("Missing factory resource "+resource);
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch(IOException error) { throw new IllegalStateException("Cannot read factory resource",error); }
    }
    private Plan build(JsonObject catalog,JsonObject layout) {
        Map<String,JsonObject> machines=new HashMap<>(); Map<String,Integer> tiers=new HashMap<>();
        for(var element:catalog.getAsJsonArray("reservations")) {
            var machine=element.getAsJsonObject(); String id=machine.get("id").getAsString();
            machines.put(id,machine); tiers.put(id,machine.get("planningTier").getAsInt());
        }
        List<FactoryArchitecture.Hall> halls=new ArrayList<>();
        List<PlannedCampus.Workshop> workshops=new ArrayList<>();
        Map<Long,List<FactoryArchitecture.Hall>> tiles=new HashMap<>(); int height=renderer==null?0:25;
        for(var element:layout.getAsJsonArray("halls")) {
            var h=element.getAsJsonObject();
            var hall=new FactoryArchitecture.Hall(h.get("id").getAsString(),h.get("department").getAsString(),
                    h.get("stage").getAsString(),h.get("x").getAsInt(),h.get("z").getAsInt(),
                    h.get("width").getAsInt(),h.get("depth").getAsInt(),h.get("clearHeight").getAsInt(),h.get("variant").getAsInt(),
                    h.get("outdoor").getAsBoolean(),h.has("entranceFacing")?h.get("entranceFacing").getAsString():"north");
            halls.add(hall); height=Math.max(height, renderer!=null ? renderer.maxHeight(hall) : hall.clearHeight());
            if(h.get("shared").getAsBoolean()) {
                var floor=h.has("productionBounds")
                        ? readBounds(h.getAsJsonArray("productionBounds")) : hall.productionBounds();
                workshops.add(new PlannedCampus.Workshop(hall.stage(),floor.minX(),floor.minZ(),floor.width(),floor.depth()));
            }
            for(int tx=Math.floorDiv(hall.x()-10,64);tx<=Math.floorDiv(hall.maxX()+10,64);tx++)
                for(int tz=Math.floorDiv(hall.z()-10,64);tz<=Math.floorDiv(hall.maxZ()+10,64);tz++)
                    tiles.computeIfAbsent(key(tx,tz),unused->new ArrayList<>()).add(hall);
        }
        List<PlannedCampus.Plot> plots=new ArrayList<>();
        for(var element:layout.getAsJsonArray("plots")) {
            var p=element.getAsJsonObject();String id=p.get("id").getAsString();var m=machines.get(id);
            var size=m.getAsJsonArray("size");var anchor=m.getAsJsonArray("groundOffset");
            int x=p.get("x").getAsInt(),z=p.get("z").getAsInt();
            plots.add(new PlannedCampus.Plot(id,p.get("stage").getAsString(),x,z,
                    size.get(0).getAsInt(),size.get(1).getAsInt(),size.get(2).getAsInt(),
                    x+anchor.get(0).getAsInt(),1+anchor.get(1).getAsInt(),z+anchor.get(2).getAsInt(),
                    m.get("patternResource").getAsString(),p.get("slotIndex").getAsInt(),m.get("theme").getAsString(),
                    m.has("displayNameKey")?m.get("displayNameKey").getAsString():""));
        }
        tiles.replaceAll((key,value)->List.copyOf(value));
        return new Plan(List.copyOf(plots),List.copyOf(halls),List.copyOf(workshops),Map.copyOf(tiles),Map.copyOf(tiers),
                catalog.getAsJsonArray("effectiveSharedIds").size(),height);
    }
}
