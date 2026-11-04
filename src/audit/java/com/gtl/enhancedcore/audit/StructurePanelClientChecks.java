package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
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
    private List<Map.Entry<String,String>> palette;
    private int next, page, frames;
    private boolean done;

    public StructurePanelClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.panelAudit")) return;
        var mc = Minecraft.getInstance();
        if (next == 0 && (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen))) return;
        try {
            if (palette == null) {
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.startsWith(ROOT) && Files.exists(directory.resolve("panel.marker"))
                        && mc.level == null && mc.player == null && mc.getSingleplayerServer() == null, "Isolated title-screen client");
                var entries = JsonParser.parseString(Files.readString(ROOT.resolve(
                        "../../src/main/resources/data/gtl_enhancedcore/structures/panel_appearance.json").normalize())).getAsJsonObject();
                palette = entries.entrySet().stream().map(e -> Map.entry(e.getKey(),e.getValue().getAsString())).toList();
                require(palette.size() == 19, "Exact nineteen-machine review scope");
            }
            if (next < palette.size()) {
                var entry = palette.get(next++);
                panels.add(verify(entry.getKey(), entry.getValue()));
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

    private Panel verify(String id, String casing) throws Exception {
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
        world.renderedBlocks.clear();
        world.blockEntities.clear();
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++)
            world.renderedBlocks.put(origin.offset(x,y,0), BlockInfo.fromBlockState(panel));
        world.renderedBlocks.put(origin, BlockInfo.fromBlockState(state));
        world.blockEntities.put(origin, entity);
        var parts = new ArrayList<IMultiPart>();
        String[] partIds = {"gtceu:ev_input_bus", "gtceu:ev_output_hatch", "gtceu:maintenance_hatch"};
        BlockPos[] positions = {origin.offset(-1,-1,0),origin.offset(0,1,0),origin.offset(1,-1,0)};
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
        var observed = new LinkedHashMap<String,Object>();
        observed.put("id",id);
        observed.put("panel",casing);
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
            test(renderer.isConnected(world,state,origin,panel,origin.offset(2,0,0),side),
                    id + " controller-to-shell CTM " + side,failures);
            for (var part : parts) {
                var pm = part.self(); var pd = pm.getDefinition(); var pr = (MachineRenderer)pd.getRenderer();
                test(part.getFormedAppearance(panel,origin.offset(2,0,0),side) == panel,
                        id + " " + pd.getId() + " formed appearance " + side,failures);
                test(pr.isConnected(world,pm.getBlockState(),pm.getPos(),panel,origin.offset(2,0,0),side),
                        id + " " + pd.getId() + " hatch-to-shell CTM " + side,failures);
                var quads = new ArrayList<BakedQuad>();
                pr.renderMachine(quads,pd,pm,Direction.NORTH,side,RandomSource.create(42),
                        ModelFactory.modelFacing(side,Direction.NORTH),ModelFactory.getRotation(Direction.NORTH));
                test(sprites(quads).containsAll(sprites),id + " " + pd.getId() + " casing sprites " + side,failures);
                if (side == Direction.NORTH) require(quads.stream().anyMatch(q -> !sprites.contains(q.getSprite().contents().name().toString())),
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
        require(controller.getPartAppearance(parts.getFirst(),Direction.NORTH,panel,origin) == null,
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
        scene.setCameraLookAt(new Vector3f(1.8F,81.2F,-9),new Vector3f(.5F,80.5F,.5F),new Vector3f(0,1,0));
        scene.setFov(40);
        scene.addRenderedBlocks(world.renderedBlocks.keySet(),null);
        return new Panel(id,casing,scene,world,f.holders());
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
        result.put("scope","Real registrations, formed controller/part appearance and CTM/render paths in synthetic detached five-by-five panels; no player world, no full megastructure formation or production claim");
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
            g.drawString(font,(baseline ? "4.3.9 BEFORE" : "4.4.0 AFTER") + " - formed panels " + (page+1) + "/4",12,12,0xffffff);
            int cell = width / 5;
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
