package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.client.model.WorkableOverlayModel;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableCasingMachineRenderer;
import com.gtl.enhancedcore.client.renderer.machine.StellarVortexMachineRenderer;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.galaxy.StellarVortexSpaceStationMachine;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Registration and actual BER checks with a synthetic client level; never opens a world. */
public final class StellarRenderChecks {
    private StellarRenderChecks() {}

    public static void run(Path directory, List<String> checks, Map<String,Object> result) throws Exception {
        var mc = Minecraft.getInstance();
        var definition = GTLEnhancedcoreMachines.getStellarVortexStation();
        var entity = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        require(entity instanceof MetaMachineBlockEntity, "Registered controller creates its real machine BE", checks);
        var holder = (MetaMachineBlockEntity) entity;
        var station = (StellarVortexSpaceStationMachine) holder.getMetaMachine();
        require(definition.getRenderer() instanceof StellarVortexMachineRenderer, "Registered controller uses Stellar Vortex renderer", checks);
        var renderer = (StellarVortexMachineRenderer) definition.getRenderer();
        var overlayField = WorkableCasingMachineRenderer.class.getDeclaredField("overlayModel");
        overlayField.setAccessible(true);
        var overlay = (WorkableOverlayModel) overlayField.get(renderer);
        var front = overlay.sprites.get(WorkableOverlayModel.OverlayFace.FRONT);
        require(front != null, "Texture stitching registered a FRONT overlay", checks);
        for (boolean[] state : List.of(new boolean[]{false,true}, new boolean[]{true,true}, new boolean[]{true,false})) {
            var sprite = front.getSprite(state[0], state[1]);
            var emissive = front.getEmissiveSprite(state[0], state[1]);
            String label = !state[0] ? "idle" : state[1] ? "active" : "paused";
            require(sprite != null && !sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()), label + " FRONT sprite is stitched", checks);
            require(emissive != null && !emissive.contents().name().equals(MissingTextureAtlasSprite.getLocation()), label + " FRONT emissive sprite is stitched", checks);
        }

        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var world = (AuditLevel) unsafe.allocateInstance(AuditLevel.class);
        world.state = definition.defaultBlockState();
        world.entity = entity;
        ObfuscationReflectionHelper.findField(Level.class, "f_46443_").setBoolean(world, true);
        entity.setLevel(world);
        var ber = mc.getBlockEntityRenderDispatcher().getRenderer(entity);
        require(ber != null, "Real BlockEntityRenderDispatcher has a renderer for the registered controller type", checks);
        require(ber.shouldRenderOffScreen(entity), "Real BER preserves global rendering for the distant field", checks);
        require(ber.shouldRender(entity, net.minecraft.world.phys.Vec3.ZERO), "Real BER accepts controller within view distance", checks);

        var active = StellarVortexSpaceStationMachine.class.getDeclaredField("fieldActive");
        active.setAccessible(true);
        active.setBoolean(station, true);
        var start = StellarVortexSpaceStationMachine.class.getDeclaredField("fieldStart");
        start.setAccessible(true);
        start.setLong(station, 9910L);
        require(station.isRemote() && !station.isFormed() && station.isFieldActive(), "Client trusts synchronized active field without server-only formation/error state", checks);

        var offset = StellarVortexMachineRenderer.class.getDeclaredMethod("offset", StellarVortexSpaceStationMachine.class, double.class, double.class, double.class);
        offset.setAccessible(true);
        var center = (double[]) offset.invoke(null, station, -.5, 49.5, -38.5);
        var targetPoint = new Vector3f((float) center[0] + .5F, (float) center[1] + .5F, (float) center[2] + .5F);
        var view = new Matrix4f().lookAt(new Vector3f(targetPoint).add(410,235,410), targetPoint, new Vector3f(0,1,0));
        var pose = new PoseStack();
        pose.last().pose().set(view);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var target = new TextureTarget(1280,960,true,Minecraft.ON_OSX);
        try {
            target.setClearColor(.003F,.006F,.015F,1);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(52),1280F/960F,.1F,2000F),VertexSorting.DISTANCE_TO_ORIGIN);
            var buffers = mc.renderBuffers().bufferSource();
            ber.render(entity,0,pose,buffers,0xF000F0,0);
            // Fade uses real elapsed time. A second real BER call exercises the normal ramp-up.
            Thread.sleep(350);
            ber.render(entity,0,pose,buffers,0xF000F0,0);
            buffers.endBatch();
            try (var picture = Screenshot.takeScreenshot(target)) {
                picture.writeToFile(directory.resolve("galaxy-ber.png"));
                int lit = 0;
                for (int y=0;y<picture.getHeight();y+=4) for(int x=0;x<picture.getWidth();x+=4) {
                    int color=picture.getPixelRGBA(x,y);
                    if ((color&255)>25 || (color>>8&255)>25 || (color>>16&255)>25) lit++;
                }
                result.put("berLitSamples",lit);
                require(lit>200,"Registered real BER produces visible field geometry from synchronized active state",checks);
            }
        } finally {
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
            entity.setLevel(null);
        }
    }

    private static void require(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label);
        checks.add(label);
    }

    /** Allocated without a constructor: only the bounded read methods used by BER are provided. */
    private static final class AuditLevel extends DummyWorld {
        BlockState state;
        BlockEntity entity;
        private AuditLevel() { super(null); }
        @Override public long getGameTime() { return 10_000; }
        @Override public BlockState getBlockState(BlockPos pos) { return state; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return entity; }
    }
}
