package com.gtl.enhancedcore.common.item;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvTimeAcceleration;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned, non-persistent targets; one player can accelerate only one machine. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class WirelessTimeAcceleration {
    private static final Map<UUID, Target> TARGETS = new HashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new HashMap<>();
    private record Target(ResourceKey<Level> dimension, BlockPos pos, WeakReference<RecipeLogic> logic) {}

    private WirelessTimeAcceleration() {}

    /** Called by the existing KubeJS item event after its five-block ray trace. */
    public static void toggle(ServerPlayer player, RecipeLogic logic) {
        if (player instanceof FakePlayer || !player.isSteppingCarefully()) return;
        long tick = player.server.overworld().getGameTime();
        if (Long.valueOf(tick).equals(LAST_CLICK.put(player.getUUID(), tick))) return;
        Target previous = TARGETS.remove(player.getUUID());
        if (previous != null && (logic == null || previous.logic.get() == logic)) {
            tell(player, "stopped");
            return;
        }
        if (logic == null || !logic.isWorking() || logic.getMachine().getLevel() != player.level()) {
            if (previous != null) tell(player, "stopped");
            return;
        }
        TARGETS.put(player.getUUID(), new Target(player.level().dimension(),
                logic.getMachine().getPos().immutable(), new WeakReference<>(logic)));
        tell(player, "started");
        accelerate(player, logic);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Target target = TARGETS.get(player.getUUID());
        if (target == null) return;
        ServerLevel level = player.serverLevel();
        RecipeLogic logic = target.logic.get();
        if (!player.isAlive() || player.isRemoved() || level.dimension() != target.dimension
                || logic == null || level.getChunkSource().getChunkNow(target.pos.getX() >> 4,
                        target.pos.getZ() >> 4) == null
                || GTCapabilityHelper.getRecipeLogic(level, target.pos, null) != logic) {
            TARGETS.remove(player.getUUID());
            return;
        }
        // Vanilla held right-click repeats every four game ticks. Keep that original cadence.
        long tick = player.server.overworld().getGameTime();
        long clicked = LAST_CLICK.getOrDefault(player.getUUID(), tick);
        if (tick > clicked && (tick - clicked) % 4 == 0) accelerate(player, logic);
    }

    private static void accelerate(ServerPlayer player, RecipeLogic logic) {
        if (IvMachineScope.crossRecipeEnabled(logic.getMachine())) {
            IvTimeAcceleration.accelerate(logic, player.getUUID());
            return;
        }
        if (!logic.isWorking() || logic.getLastRecipe() == null) return;
        int remaining = logic.getDuration() - logic.getProgress();
        long eut = RecipeHelper.getInputEUt(logic.getLastRecipe());
        if (remaining <= 0 || eut <= 0) return;
        // Exactly the original 8 * (remaining * 0.5) * EU/t, without JS double overflow.
        BigInteger eu = BigInteger.valueOf(remaining).multiply(BigInteger.valueOf(eut)).shiftLeft(2);
        if (WirelessEnergyManager.addEUToGlobalEnergyMap(player.getUUID(), eu.negate(), logic.getMachine())) {
            double reduced = remaining * 0.5;
            logic.setProgress((int) (logic.getProgress() + reduced));
            player.displayClientMessage(Component.translatable(
                    "message.gtl_enhancedcore.time_acceleration.consumed",
                    FormattingUtil.formatNumbers(eu), reduced), true);
        }
    }

    private static void tell(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.gtl_enhancedcore.time_acceleration." + key), true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        TARGETS.remove(event.getEntity().getUUID());
        LAST_CLICK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        TARGETS.clear();
        LAST_CLICK.clear();
    }
}
