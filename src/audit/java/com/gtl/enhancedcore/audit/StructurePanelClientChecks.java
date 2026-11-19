package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
import com.gtl.enhancedcore.common.structure.HollowStructureResources;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.state.properties.Property;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.client.renderer.machine.MachineRenderer;
import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import com.lowdragmc.lowdraglib.client.renderer.IBlockRendererProvider;
import com.lowdragmc.lowdraglib.client.scene.ImmediateWorldSceneRenderer;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

/** Real controller/hatch CTM queries, casing quads and detached panel screenshots, without a saved world. */
@Mod("enhancedcore_audit")
public final class StructurePanelClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/structure-panel-appearance-20261102");
    private final List<String> checks = new ArrayList<>();
    private final List<Map<String,Object>> results = new ArrayList<>();
    private final List<Panel> panels = new ArrayList<>();
    private final boolean baseline = Boolean.getBoolean("gtl.enhancedcore.panelBaseline");
    private Path directory;
    private record Materials(String id, String controller, String hatch) {}
    private static final String ORE = "gtladditions:space_infinity_integrated_ore_processor";
    private static final String DATA = "/data/gtl_enhancedcore/structures/";
    private JsonObject oreSpec;
    private List<Materials> palette;
    private int next, page, frames;
    private boolean done;

    public StructurePanelClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.panelAudit")) return;
        var mc = Minecraft.getInstance();
        if (next == 0 && (mc.getOverlay() != null || mc.screen == null)) return;
        try {
            if (palette == null) {
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.startsWith(ROOT) && Files.exists(directory.resolve("panel.marker"))
                        && mc.level == null && mc.player == null && mc.getSingleplayerServer() == null, "Isolated title-screen client");
                var selected = System.getProperty("gtl.enhancedcore.panelMachine");
                if (ORE.equals(selected)) {
                    require(MachineOverrideConfig.current().replaceOriginalStructures(), "Replacement structure selected");
                    var path = new ResourceLocation(ORE).getPath();
                    oreSpec = packagedJson("gtl/upgrades_20261102.json").getAsJsonObject(path);
                    require(ORE.equals(oreSpec.get("namespace").getAsString() + ":" + path), "Packaged machine ID");
                    String slot = oreSpec.getAsJsonArray("hatches").get(0).getAsString();
                    palette = List.of(new Materials(ORE, packagedJson("panel_appearance.json").get(ORE).getAsString(),
                            oreSpec.getAsJsonObject("bindings").getAsJsonObject(slot).get("fallback").getAsString()));
                } else {
                    var structures = ROOT.resolve("../../src/main/resources/data/gtl_enhancedcore/structures").normalize();
                    var entries = JsonParser.parseString(Files.readString(structures.resolve("panel_appearance.json"))).getAsJsonObject();
                    var hatches = new HashMap<String,String>();
                    try (var files = Files.list(structures.resolve("gtl"))) {
                        for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                            var specs = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                            for (var specEntry : specs.entrySet()) {
                                var spec = specEntry.getValue().getAsJsonObject();
                                var namespace = spec.has("namespace") ? spec.get("namespace").getAsString() : "gtceu";
                                for (var slot : spec.getAsJsonObject("bindings").entrySet()) {
                                    var rule = slot.getValue().getAsJsonObject();
                                    if ("hatch".equals(rule.get("mode").getAsString()))
                                        hatches.put(namespace + ":" + specEntry.getKey(), rule.get("fallback").getAsString());
                                }
                            }
                        }
                    }
                    palette = entries.entrySet().stream().map(e -> {
                        var id = e.getKey();
                        var controller = e.getValue().getAsString();
                        var hatch = id.startsWith("gtl_enhancedcore:") ? controller :
                                Objects.requireNonNull(hatches.get(id), "Missing third-party hatch binding: " + id);
                        return new Materials(id, controller, hatch);
                    }).toList();
                    if (selected != null) {
                        palette = palette.stream().filter(m -> m.id().equals(selected)).toList();
                        require(palette.size() == 1, "Single reviewed machine: " + selected);
                    }
                }
            }
            if (next < palette.size()) {
                var entry = palette.get(next++);
                panels.add(verify(entry.id(), entry.controller(), entry.hatch()));
                return;
            }
            if (!(mc.screen instanceof PanelScreen)) { mc.setScreen(new PanelScreen()); return; }
            if (++frames < 10) return;
            frames = 0;
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(directory.resolve("panels-" + page + ".png"));
            }
            checks.add("Rendered panel comparison page " + page);
            if (++page * 5 >= panels.size()) finish(null);
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }

    private Panel verify(String id, String casing, String hatchCasing) throws Exception {
        // Reuse only the detached world fixture: Harmony and other controllers are not
        // subclasses of WorkableElectricMultiblockMachine.
        var f = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false);
        var world = (TrackedDummyWorld) f.player().level();
        ChamberPreviewUiChecks.seedWorld(world);
        for (var input : net.minecraft.world.level.Level.class.getDeclaredFields()) {
            if (input.getType() == RandomSource.class && !java.lang.reflect.Modifier.isStatic(input.getModifiers())) {
                input.setAccessible(true);
                input.set(world, RandomSource.create(42));
            }
        }
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
        var origin = new BlockPos(0,80,0);
        var state = definition.defaultBlockState();
        var facing = ((MetaMachineBlock) definition.getBlock()).rotationState.property;
        if (state.hasProperty(facing)) state = state.setValue(facing, Direction.NORTH);
        var entity = definition.getBlockEntityType().create(origin,state);
        entity.setLevel(world);
        var controller = (MultiblockControllerMachine)((IMachineBlockEntity)entity).getMetaMachine();
        var panel = Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(casing))).defaultBlockState();
        var hatch = Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(hatchCasing))).defaultBlockState();
        var controllerNeighbor = origin.offset(-1,0,0);
        var hatchNeighbor = origin.offset(0,2,0);
        world.renderedBlocks.clear();
        world.blockEntities.clear();
        var observed = new LinkedHashMap<String,Object>();
        BlockPos[] positions;
        String[] partIds;
        if (oreSpec != null) {
            positions = sampleOreFace(world, origin, observed);
            partIds = positions.length == 0 ? new String[0] : new String[]{"gtceu:ev_input_bus"};
        } else {
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++)
                world.renderedBlocks.put(origin.offset(x,y,0), BlockInfo.fromBlockState(hatch));
            world.renderedBlocks.put(controllerNeighbor, BlockInfo.fromBlockState(panel));
            positions = new BlockPos[]{origin.offset(-1,-1,0),origin.offset(0,1,0),origin.offset(1,-1,0)};
            partIds = new String[]{"gtceu:ev_input_bus", "gtceu:ev_output_hatch", "gtceu:maintenance_hatch"};
        }
        world.renderedBlocks.put(origin, BlockInfo.fromBlockState(state));
        world.blockEntities.put(origin, entity);
        var parts = new ArrayList<IMultiPart>();
        for (int i = 0; i < partIds.length; i++) {
            var pd = GTRegistries.MACHINES.get(new ResourceLocation(partIds[i]));
            var ps = pd.defaultBlockState().setValue(((MetaMachineBlock)pd.getBlock()).rotationState.property,Direction.NORTH);
            var holder = pd.getBlockEntityType().create(positions[i],ps);
            holder.setLevel(world);
            world.renderedBlocks.put(positions[i],BlockInfo.fromBlockState(ps));
            world.blockEntities.put(positions[i],holder);
            var part = (MultiblockPartMachine)((IMachineBlockEntity)holder).getMetaMachine();
            @SuppressWarnings("unchecked") var controllers = (Set<BlockPos>) field(part,"controllerPositions").get(part);
            controllers.add(origin);
            parts.add(part);
        }
        field(controller,"parts").set(controller,parts);
        field(controller,"partPositions").set(controller,positions);
        var formed = field(controller,"isFormed");
        formed.setBoolean(controller,true);
        var renderer = (MachineRenderer) definition.getRenderer();
        observed.put("id",id);
        observed.put("panel",casing);
        observed.put("hatchCasing",hatchCasing);
        observed.put("appearance",String.valueOf(ForgeRegistries.BLOCKS.getKey(definition.getAppearance().get().getBlock())));
        observed.put("renderer",renderer.getClass().getName());
        var failures = new ArrayList<String>();
        test(definition.getAppearance().get() == panel,id + " controller appearance matches panel",failures);
        for (var side : Direction.values()) {
            var expected = casingQuads(world,origin,panel,side);
            require(!expected.isEmpty(),id + " real casing quads " + side);
            var sprites = sprites(expected);
            var base = new ArrayList<BakedQuad>();
            renderer.renderBaseModel(base,definition,controller,Direction.NORTH,side,RandomSource.create(42));
            test(!base.isEmpty() && sprites(base).equals(sprites),id + " controller casing sprites " + side,failures);
            var itemBase = new ArrayList<BakedQuad>();
            renderer.renderBaseModel(itemBase,definition,null,Direction.NORTH,side,RandomSource.create(42));
            test(!itemBase.isEmpty() && sprites(itemBase).equals(sprites),id + " item casing sprites " + side,failures);
            var item = new ArrayList<BakedQuad>();
            renderer.renderMachine(item,definition,null,Direction.NORTH,side,RandomSource.create(42),
                    ModelFactory.modelFacing(side,Direction.NORTH),ModelFactory.getRotation(Direction.NORTH));
            test(!item.isEmpty() && sprites(item).containsAll(sprites),id + " full item renderer " + side,failures);
            if (oreSpec != null) {
                for (var offset : List.of(Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN)) {
                    var neighbor = origin.relative(offset);
                    var actual = world.getBlockState(neighbor);
                    if (actual == panel) test(renderer.isConnected(world,state,origin,actual,neighbor,side),
                            id + " controller CTM with actual " + offset + " casing on " + side,failures);
                }
            } else test(renderer.isConnected(world,state,origin,panel,controllerNeighbor,side),
                    id + " controller-to-shell CTM " + side,failures);
            for (var part : parts) {
                var pm = part.self(); var pd = pm.getDefinition(); var pr = (MachineRenderer)pd.getRenderer();
                var neighborPos = oreSpec == null ? hatchNeighbor : pm.getPos().relative(side);
                var neighborState = world.getBlockState(neighborPos);
                test(part.getFormedAppearance(neighborState,neighborPos,side) == hatch,
                        id + " " + pd.getId() + " formed appearance " + side,failures);
                if (neighborState == hatch) test(pr.isConnected(world,pm.getBlockState(),pm.getPos(),neighborState,neighborPos,side),
                        id + " " + pd.getId() + " hatch-to-shell CTM " + side,failures);
                var hatchQuads = casingQuads(world,pm.getPos(),hatch,side);
                require(!hatchQuads.isEmpty(), id + " real hatch casing quads " + side);
                var hatchSprites = sprites(hatchQuads);
                var quads = new ArrayList<BakedQuad>();
                pr.renderMachine(quads,pd,pm,Direction.NORTH,side,RandomSource.create(42),
                        ModelFactory.modelFacing(side,Direction.NORTH),ModelFactory.getRotation(Direction.NORTH));
                test(sprites(quads).containsAll(hatchSprites),id + " " + pd.getId() + " casing sprites " + side,failures);
                if (side == Direction.NORTH) require(quads.stream().anyMatch(q -> !hatchSprites.contains(q.getSprite().contents().name().toString())),
                        id + " " + pd.getId() + " original front overlay retained");
            }
        }
        // Original renderers still append their controller front overlay after the casing layer.
        var complete = new ArrayList<BakedQuad>();
        renderer.renderMachine(complete,definition,controller,Direction.NORTH,Direction.NORTH,RandomSource.create(42),
                Direction.NORTH,ModelFactory.getRotation(Direction.NORTH));
        var unculledItem = new ArrayList<BakedQuad>();
        renderer.renderMachine(unculledItem,definition,null,Direction.NORTH,null,RandomSource.create(42),
                null,ModelFactory.getRotation(Direction.NORTH));
        checks.add(id + " unculled item renderer accepts null machine and null side");
        var controllerBase = new ArrayList<BakedQuad>();
        renderer.renderBaseModel(controllerBase,definition,controller,Direction.NORTH,Direction.NORTH,RandomSource.create(42));
        var controllerOverlay = sprites(complete);
        controllerOverlay.removeAll(sprites(controllerBase));
        // Some native controllers deliberately have no north-face overlay. Compare the exact
        // remaining sprites against the old artifact instead of inventing an overlay requirement.
        observed.put("controllerOverlaySprites",controllerOverlay);
        formed.setBoolean(controller,false);
        if (!parts.isEmpty()) require(controller.getPartAppearance(parts.getFirst(),Direction.NORTH,hatch,hatchNeighbor) == null,
                id + " unformed controller does not provide a formed appearance");
        formed.setBoolean(controller,true);
        observed.put("mismatches",failures);
        observed.put("mismatchCount",failures.size());
        results.add(observed);
        if (!baseline) require(failures.isEmpty(),id + " no panel mismatch: " + failures);
        var scene = new ImmediateWorldSceneRenderer(world);
        scene.setParticleManager(new com.lowdragmc.lowdraglib.client.scene.ParticleManager());
        scene.useCacheBuffer(false);
        scene.setClearColor(0xff182331);
        // About 20 blocks of vertical field for a 17-block-high crop; a slight angle reveals the backing.
        var eye = oreSpec == null ? new Vector3f(1.8F,81.2F,-9) : new Vector3f(3F,82F,-27);
        scene.setCameraLookAt(eye,new Vector3f(.5F,80.5F,.5F),new Vector3f(0,1,0));
        scene.setFov(40);
        scene.addRenderedBlocks(world.renderedBlocks.keySet(),null);
        return new Panel(id,casing,scene,world,f.holders());
    }

    private static JsonObject packagedJson(String path) throws Exception {
        try (var input = Objects.requireNonNull(StructureData.class.getResourceAsStream(DATA + path), path);
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** Crop the actual selected resource in pattern coordinates (LEFT / UP / FRONT, north-facing).
     * Keep the 17 x 13 face, two blocks of framing on each edge, and two depth slices on either side. */
    private BlockPos[] sampleOreFace(TrackedDummyWorld world, BlockPos origin, Map<String,Object> observed) throws Exception {
        var path = HollowStructureResources.resolve(DATA + "gtl/" + new ResourceLocation(ORE).getPath() + ".pattern.gz");
        var pattern = new ArrayList<String[]>();
        StructureData.read(StructureData.class.getResourceAsStream(path)).forEachAisle(pattern::add);
        var anchor = oreSpec.getAsJsonArray("controllerPosition");
        int cx = anchor.get(0).getAsInt(), cy = anchor.get(1).getAsInt(), cz = anchor.get(2).getAsInt();
        char marker = oreSpec.get("controller").getAsString().charAt(0);
        require(pattern.get(cz)[cy].charAt(cx) == marker, "Controller at packaged anchor in " + path);
        var cells = new LinkedHashMap<String,String>();
        var slots = new ArrayList<BlockPos>();
        var bindings = oreSpec.getAsJsonObject("bindings");
        for (int dz = -2; dz <= 2; dz++) for (int dy = -8; dy <= 8; dy++) for (int dx = -10; dx <= 10; dx++) {
            int z = cz+dz, y = cy+dy, x = cx+dx;
            if (z < 0 || z >= pattern.size() || y < 0 || y >= pattern.get(z).length) continue;
            String row = pattern.get(z)[y];
            if (x < 0 || x >= row.length()) continue;
            char symbol = row.charAt(x);
            if (dx == 0 && dy == 0 && dz == 0) continue;
            var rule = bindings.getAsJsonObject(String.valueOf(symbol));
            String mode = rule.get("mode").getAsString();
            if (mode.equals("any")) continue;
            var pos = origin.relative(RelativeDirection.LEFT.getActualFacing(Direction.NORTH), dx).above(dy).north(dz);
            String block = switch (mode) {
                case "hatch" -> rule.get("fallback").getAsString();
                case "literal", "casing" -> rule.get("block").getAsString();
                default -> throw new IllegalStateException("Cannot represent sampled " + symbol + ": " + mode);
            };
            var key = new ResourceLocation(block);
            require(ForgeRegistries.BLOCKS.containsKey(key), "Sampled material " + key);
            var state = ForgeRegistries.BLOCKS.getValue(key).defaultBlockState();
            if (rule.has("properties")) for (var prop : rule.getAsJsonObject("properties").entrySet()) {
                var property = Objects.requireNonNull(state.getBlock().getStateDefinition().getProperty(prop.getKey()));
                state = applyProperty(state, property, prop.getValue().getAsString());
            }
            world.renderedBlocks.put(pos, BlockInfo.fromBlockState(state));
            cells.put(x + "," + y + "," + z, symbol + " " + state);
            if (mode.equals("hatch") && dz == 0 && Math.max(Math.abs(dx),Math.abs(dy)) > 1) slots.add(pos);
        }
        // Leave the controller's immediate material ring intact. Never invent or expose a slot.
        var shown = slots.stream().filter(p -> !world.renderedBlocks.containsKey(p.north())
                && !world.renderedBlocks.containsKey(p.north(2)))
                .min(Comparator.comparingDouble(origin::distSqr));
        observed.put("patternResource", path);
        observed.put("patternAnchor", List.of(cx,cy,cz));
        observed.put("sampledCellsXYZ", cells);
        observed.put("displayedBusOffset", shown.map(p -> p.subtract(origin).toShortString()).orElse("none"));
        return shown.stream().toArray(BlockPos[]::new);
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> prop, String value) {
        return state.setValue(prop, prop.getValue(value).orElseThrow());
    }

    private static List<BakedQuad> casingQuads(TrackedDummyWorld world, BlockPos pos, BlockState state, Direction side) {
        if (state.getBlock() instanceof IBlockRendererProvider provider)
            return provider.getRenderer(state).renderModel(world,pos,state,side,RandomSource.create(42));
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getQuads(state,side,RandomSource.create(42));
    }

    private static Set<String> sprites(List<BakedQuad> quads) {
        var names = new HashSet<String>();
        for (var q : quads) names.add(q.getSprite().contents().name().toString());
        return names;
    }

    private void test(boolean valid,String label,List<String> failures) {
        if (!valid) failures.add(label);
        checks.add((valid ? "PASS " : "MISMATCH ") + label);
    }
    private void require(boolean valid,String label) {
        if (!valid) throw new AssertionError(label);
        checks.add(label);
    }
    private static Field field(Object target,String name) throws Exception {
        for (var type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException missing) { /* search the actual declaring superclass */ }
        }
        throw new NoSuchFieldException(name);
    }

    private void finish(Throwable failure) {
        done = true;
        var result = new LinkedHashMap<String,Object>();
        result.put("status",failure == null ? "PASS" : "FAIL");
        result.put("baseline",baseline);
        result.put("checks",checks);
        result.put("machines",results);
        result.put("scope",oreSpec != null ? "Packaged ore processor controller neighbourhood; detached render, not full formation" : "Real registrations, formed controller/part appearance and CTM/render paths in synthetic detached five-by-five panels; no player world, no full megastructure formation or production claim");
        if (failure != null) result.put("error",failure.toString());
        try {
            Files.writeString(directory.resolve("panel-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
            Minecraft.getInstance().setScreen(new TitleScreen());
            for (var p : panels) { p.scene.deleteCacheBuffer(); p.world.blockEntities.values().forEach(h -> h.setLevel(null)); p.holders.forEach(h -> h.setLevel(null)); }
        } catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }

    private record Panel(String id,String casing,ImmediateWorldSceneRenderer scene,TrackedDummyWorld world,List<? extends BlockEntity> holders) {}

    private final class PanelScreen extends Screen {
        PanelScreen() { super(Component.literal("Structure panel comparison")); }
        @Override public void render(GuiGraphics g,int x,int y,float partial) {
            try { renderPanels(g); }
            catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
        }
        private void renderPanels(GuiGraphics g) {
            g.fill(0,0,width,height,0xff101820);
            g.drawString(font,(baseline ? "BEFORE" : "AFTER") + " - formed panels " + (page+1) + "/" + ((panels.size()+4)/5),12,12,0xffffff);
            int cell = width / Math.min(5, panels.size());
            for (int col = 0; col < 5 && page*5+col < panels.size(); col++) {
                var p = panels.get(page*5+col);
                p.scene.render(g.pose(),col*cell+3,45,cell-6,height-105,-100,-100);
                String name = p.id.substring(p.id.indexOf(':')+1);
                var lines = font.split(Component.literal(name.replace('_',' ')),cell-10);
                for (int line=0;line<lines.size();line++) g.drawString(font,lines.get(line),col*cell+5,height-52+line*10,0xe7eff7);
            }
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
