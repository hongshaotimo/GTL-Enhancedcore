package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.GTLEnhancedcore;
import org.gtlcore.gtlcore.client.preview.PreviewRendererAccess;
import org.gtlcore.gtlcore.client.preview.PreviewRayIndex;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class PreviewPickingChecks {
    static void check(SceneWidget scene) throws Exception {
        var field = SceneWidget.class.getDeclaredField("core");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var core = (Set<BlockPos>) field.get(scene);
        var renderer = scene.getRenderer();
        var access = (PreviewRendererAccess) renderer;
        var center = new Vec3(renderer.getLookAt());
        var eye = new Vec3(renderer.getEyePos());
        var rayIndex = new PreviewRayIndex();
        for (var pos : core) rayIndex.add(renderer.world, pos, renderer.world.getBlockState(pos));
        long count = 0;
        int rays = 0;
        for (var offset : new Vec3[]{new Vec3(0, 0, 0), new Vec3(45, 0, 0), new Vec3(-45, 0, 0),
                new Vec3(0, 45, 0), new Vec3(0, -45, 0), new Vec3(0, 0, 45),
                new Vec3(0, 0, -45), new Vec3(1500, 1500, 1500)}) {
            var end = center.add(offset).scale(2).subtract(eye);
            var candidates = access.gtlcore$rayCandidates(eye, end);
            if (candidates == null) throw new AssertionError("Missing preview ray index");
            var positions = new java.util.ArrayList<BlockPos>();
            candidates.forEachRemaining(positions::add);
            count += positions.size();
            var expected = trace(renderer.world, core.iterator(), eye, end);
            var actual = trace(renderer.world, positions.iterator(), eye, end);
            if ((expected == null) != (actual == null) || expected != null
                    && (!expected.getBlockPos().equals(actual.getBlockPos()) || expected.getDirection() != actual.getDirection())) {
                throw new AssertionError("Indexed picking changed the selected block or face");
            }
            var full = renderer.world.clip(new net.minecraft.world.level.ClipContext(eye, end,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, null));
            var bounded = rayIndex.trace(renderer.world, eye, end, null);
            if (full.getType() != bounded.getType() || !full.getBlockPos().equals(bounded.getBlockPos())
                    || full.getDirection() != bounded.getDirection()) {
                throw new AssertionError("Bounded ray changed vanilla picking");
            }
            rays++;
        }
        GTLEnhancedcore.LOGGER.info("[PREVIEW_CLIENT] PICKING rays={} original_candidates={} indexed_candidates={}",
                rays, core.size() * (long) rays, count);
    }

    private static BlockHitResult trace(Level world, Iterator<BlockPos> positions, Vec3 eye, Vec3 end) {
        BlockHitResult selected = null;
        double nearest = Float.MAX_VALUE;
        while (positions.hasNext()) {
            var pos = positions.next();
            var state = world.getBlockState(pos);
            if (state.isAir()) continue;
            var hit = world.clipWithInteractionOverride(eye, end, pos, state.getShape(world, pos), state);
            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                double distance = eye.distanceToSqr(hit.getLocation());
                if (distance < nearest) {
                    nearest = distance;
                    selected = hit;
                }
            }
        }
        return selected;
    }
}
