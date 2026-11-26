package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.structure.StructureData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** User-selected blueprints, deletion accounting and protected functional cells. */
final class RequestedStructureBatchRegression {
    private static int checks;
    static int run() throws Exception {
        Path root = Path.of("src/main/resources/data/gtl_enhancedcore/structures");
        var specs = new HashMap<String,JsonObject>();
        for (String file : new String[]{"upgrades.json", "super_particle_collider.json", "upgrades_20261102.json"}) {
            var entries = JsonParser.parseString(Files.readString(root.resolve("gtl/" + file))).getAsJsonObject();
            for (var entry : entries.entrySet()) specs.put(entry.getKey(), entry.getValue().getAsJsonObject());
        }
        String[] own = {"stellar_confinement_fusion_reactor", "stellar_vortex_space_station", "lanthanide_refining_hub"};
        for (String name : own) specs.put(name, JsonParser.parseString(Files.readString(root.resolve(name + ".json"))).getAsJsonObject());
        String[] rows = {
                "stellar_confinement_fusion_reactor 167 95 167 40 gtlcore:iridium_casing",
                "advanced_sps_crafting 73 77 73 68 gtceu:fusion_casing_mk2",
                "super_particle_collider 131 55 139 174 gtlcore:lafium_mechanical_casing",
                "huge_incubator 59 68 59 394 gtceu:inert_machine_casing",
                "eye_of_harmony 191 191 187 220 gtlcore:dimensionally_transcendent_casing",
                "stellar_vortex_space_station 200 216 200 298 gtlcore:dimensionally_transcendent_casing",
                "lanthanide_refining_hub 57 45 71 64 gtceu:computer_casing"};
        var replacementIds = new java.util.HashSet<String>();
        for (String file : new String[]{"manifest.json", "upgrades.json", "upgrades_20260927.json",
                "super_particle_collider.json", "upgrades_20261013.json", "upgrades_20261102.json"}) {
            var entries = JsonParser.parseString(Files.readString(root.resolve("gtl/" + file))).getAsJsonObject();
            for (var entry : entries.entrySet()) {
                var spec = entry.getValue().getAsJsonObject();
                String id = (spec.has("namespace") ? spec.get("namespace").getAsString() : "gtceu") + ":" + entry.getKey();
                String controller = spec.has("controller") ? spec.get("controller").getAsString() : "@";
                for (var bindingEntry : spec.getAsJsonObject("bindings").entrySet()) {
                    if (bindingEntry.getKey().equals(controller)) continue;
                    var binding = bindingEntry.getValue().getAsJsonObject();
                    for (String candidate : new String[]{"block", "fallback", "preview"})
                        check(!binding.has(candidate) || !binding.get(candidate).getAsString().equals(id),
                                id + " non-controller cell cannot use its own controller: " + bindingEntry.getKey());
                }
                check(replacementIds.add(entry.getKey()) && id.startsWith("gtceu:"),
                        id + " is a unique base GTCEu replacement");
            }
        }
        var hollow = JsonParser.parseString(Files.readString(root.resolve("hollow/manifest.json")))
                .getAsJsonObject().getAsJsonObject("resources");
        for (String name : new String[]{
                "antientropy_condensation_center", "apocalyptic_torsion_quantum_matrix", "arcanic_astrograph",
                "dimensionally_transcendent_chemical_plant", "fuxi_bagua_heaven_forging_furnace",
                "heart_of_the_universe", "lucid_etchdreamer", "macro_atomic_resonant_fragment_stripper",
                "quantum_syphon_matrix", "skeleton_shift_rift_engine", "space_infinity_integrated_ore_processor",
                "suprachronal_assembly_line", "titan_crip_earthbore", "weather_control", "research_station"}) {
            String pattern = "gtl/" + name + ".pattern.gz";
            check(!replacementIds.contains(name) && !Files.exists(root.resolve(pattern))
                            && !Files.exists(root.resolve("hollow/" + pattern))
                            && !hollow.has("/data/gtl_enhancedcore/structures/" + pattern),
                    name + " keeps ADD geometry without a replacement or hollow mask");
        }
        long volume = 0, removed = 0;
        for (String row : rows) {
            String[] columns = row.split(" "); String name = columns[0]; var spec = specs.get(name);
            check(spec != null && spec.get("sourceRevision").getAsString().equals("user-request-20261102"), name + " current source revision");
            long cells = 1;
            for (int i=0;i<3;i++) { int size=Integer.parseInt(columns[i+1]); cells*=size; check(spec.getAsJsonArray("size").get(i).getAsInt()==size,name+" source dimensions"); }
            volume += cells;
            String casing = columns[5], controller = spec.get("controller").getAsString(), hatch = spec.getAsJsonArray("hatches").get(0).getAsString();
            int ports = Integer.parseInt(columns[4]);
            check(spec.get("diamondOreMarkers").getAsInt()==ports && spec.get("hatchFallback").getAsString().equals(casing),name+" requested hatch fallback");
            String resource = switch(name) {
                case "stellar_confinement_fusion_reactor" -> "stellarconfinementfusionreactorstructure";
                case "stellar_vortex_space_station", "lanthanide_refining_hub" -> name;
                default -> "gtl/"+name;
            };
            Path pattern=root.resolve(resource+".pattern.gz"); byte[] payload;
            try(var input=new GZIPInputStream(Files.newInputStream(pattern))) { payload=input.readAllBytes(); }
            check(hash(payload).equals(spec.get("sha256").getAsString()),name+" final resource digest");
            check(hash(Files.readAllBytes(Path.of(spec.get("sourceSchematic").getAsString()))).equals(spec.get("sourceSha256").getAsString()),name+" original schematic retained");
            var counts=new HashMap<String,Integer>();
            StructureData.read(Files.newInputStream(pattern)).forEachAisle(aisle->{for(String line:aisle)for(char symbol:line.toCharArray())counts.merge(String.valueOf(symbol),1,Integer::sum);});
            check(counts.keySet().equals(spec.getAsJsonObject("bindings").keySet()),name+" exact live palette");
            check(counts.get(controller)==1 && counts.get(hatch)==ports,name+" unique controller and all marked ports");
            var hidden=spec.getAsJsonObject("hiddenRemoval"); var oldCounts=spec.getAsJsonObject("sourceCounts"); int deleted=hidden.get("removed").getAsInt(); removed+=deleted;
            int removedSum=0;
            for(var entry:counts.entrySet())check(spec.getAsJsonObject("counts").get(entry.getKey()).getAsInt()==entry.getValue(),name+" final count "+entry.getKey());
            for(var entry:oldCounts.entrySet()) {
                String symbol=entry.getKey(); int before=entry.getValue().getAsInt(), after=counts.getOrDefault(symbol,0);
                int difference=symbol.equals(" ")?after-before:before-after;
                check(difference>=0,name+" deletion-only geometry");
                if(symbol.equals(" "))check(difference==deleted,name+" deleted cells become any");
                else {
                    removedSum+=difference;
                    int recorded=hidden.getAsJsonObject("removedBySymbol").has(symbol)?hidden.getAsJsonObject("removedBySymbol").get(symbol).getAsInt():0;
                    check(difference==recorded,name+" audited removal count");
                    var binding=spec.getAsJsonObject("bindings").getAsJsonObject(symbol);
                    if(binding!=null && (!binding.get("mode").getAsString().equals("literal")
                            || binding.get("block").getAsString().contains("coil") || binding.getAsJsonObject("properties").size()>0))
                        check(before==after,name+" functional/stateful cells retained");
                }
            }
            check(removedSum==deleted,name+" removal total");
        }
        check(volume == 20_786_040L && removed == 128_607L, "retained geometry and removal totals");
        check(specs.get("advanced_sps_crafting").get("minimumCasing").getAsInt()==162,"SPS minimum fixed casings retained");
        String preview=Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/structure/StellarVortexStructure.java"));
        check(preview.contains("width-1-x") && preview.contains("depth-1-z") && !preview.contains("new BlockInfo[199]"),"station preview follows actual imported dimensions");
        return checks;
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void check(boolean okay,String message) { if(!okay)throw new AssertionError(message);checks++; }
}
