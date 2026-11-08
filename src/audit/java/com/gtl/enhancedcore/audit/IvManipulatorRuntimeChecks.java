package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.gtlcore.gtlcore.common.item.manipulator.ManipulatorSnapshot;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Real transformed detached buffer fixture; no world, inventory, or player's machine is accessed. */
public final class IvManipulatorRuntimeChecks {
    private IvManipulatorRuntimeChecks() {}

    public static List<String> run() {
        List<String> checks = new ArrayList<>();
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtladditions", "me_super_pattern_buffer"));
        var holder = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        var buffer = (MEPatternBufferPartMachine) ((IMachineBlockEntity) holder).getMetaMachine();
        var state = IvBuffers.state(buffer);
        state.owner = "audit:detached_iv_owner";
        var identity = state.identity;
        var key = AEItemKey.of(new ItemStack(Items.DIAMOND));
        buffer.getBuffer().put(key, 17L);
        Map<AEKey, Long> costs = new LinkedHashMap<>();
        costs.put(AEItemKey.of(new ItemStack(Items.APPLE)), 3L);
        Map<AEKey, Long> before = Map.copyOf(costs);
        boolean rejected = false;
        try { buffer.captureManipulatorContents(costs); }
        catch (ManipulatorSnapshot.UnsupportedBlockEntityException expected) { rejected = true; }
        check(rejected, "Busy IV manipulator capture throws the upstream pre-removal skip exception", checks);
        check(buffer.getBuffer().getLong(key) == 17 && costs.equals(before),
                "Rejected manipulator capture leaves physical output and material costs untouched", checks);
        check(state.identity.equals(identity) && state.owner.equals("audit:detached_iv_owner") && state.jobs.isEmpty(),
                "Rejected manipulator capture retains IV identity and ownership", checks);
        buffer.getBuffer().clear();
        costs.clear();
        var captured = buffer.captureManipulatorContents(costs);
        check(captured.contains("Slots") && costs.isEmpty(), "Healthy drained IV buffer can still be captured", checks);
        check(!captured.contains(IvBuffers.SAVE_KEY) && state.identity.equals(identity),
                "Idle capture carries configuration and leaves the source ledger identity intact", checks);
        return checks;
    }

    private static void check(boolean condition, String description, List<String> checks) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }
}
