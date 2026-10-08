package com.gtl.enhancedcore.common.event;

import com.gtl.enhancedcore.common.machine.EndCrystalResonatorMachine;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.gtl.enhancedcore.GTLEnhancedcore;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class ResonatorPlacementEvents {
    private ResonatorPlacementEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(Items.END_CRYSTAL)) {
            return;
        }
        if (event.getFace() != Direction.UP) {
            return;
        }
        BlockPos clickedPos = event.getPos();
        if (!(level.getBlockEntity(clickedPos) instanceof IMachineBlockEntity machineBE)) {
            return;
        }
        if (!(machineBE.getMetaMachine() instanceof EndCrystalResonatorMachine)) {
            return;
        }

        BlockPos abovePos = clickedPos.above();
        if (!level.mayInteract(event.getEntity(), abovePos)
                || !event.getEntity().mayUseItemAt(abovePos, Direction.UP, stack)) return;
        BlockState aboveState = level.getBlockState(abovePos);
        boolean replaceable = aboveState.canBeReplaced();
        if (!aboveState.isAir() && !replaceable) {
            return;
        }

        // 精确判定：只看正上方那一格里有没有活着的末影水晶，不用膨胀盒。
        AABB exactCell = new AABB(abovePos);
        boolean occupied = !level.getEntitiesOfClass(EndCrystal.class, exactCell,
                crystal -> crystal.isAlive() && crystal.blockPosition().equals(abovePos)).isEmpty();
        if (occupied) {
            return;
        }

        EndCrystal crystal = new EndCrystal(level,
                abovePos.getX() + 0.5D, abovePos.getY(), abovePos.getZ() + 0.5D);
        crystal.setShowBottom(false);
        if (!level.addFreshEntity(crystal)) return;

        if (replaceable && !aboveState.isAir()) {
            level.destroyBlock(abovePos, true);
        }

        Player player = event.getEntity();
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

}
