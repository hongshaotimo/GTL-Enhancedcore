package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.event.ResonatorPlacementEvents;
import com.gtl.enhancedcore.common.machine.EndCrystalResonatorMachine;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;

public final class ResonatorPlacementSafetyChecks {
    private ResonatorPlacementSafetyChecks() {}

    public static int run(MinecraftServer server, BlockPos resonatorPos) {
        return run(server.overworld(), resonatorPos);
    }

    public static int run(ServerLevel level, BlockPos resonatorPos) {
        Checks checks = new Checks();
        checks.check(level.getServer().isSameThread(), "Run placement checks on the server thread");
        checks.check(level.getBlockEntity(resonatorPos) instanceof IMachineBlockEntity machineBE
                        && machineBE.getMetaMachine() instanceof EndCrystalResonatorMachine,
                "Caller must supply an actual placed end-crystal resonator");
        BlockPos abovePos = resonatorPos.above();
        BlockState originalTop = level.getBlockState(abovePos);
        AABB area = new AABB(abovePos).inflate(2.0D);
        checks.check(originalTop.isAir() && level.getBlockEntity(abovePos) == null,
                "Fixture must start with an empty top cell");
        checks.check(level.getEntitiesOfClass(EndCrystal.class, area).isEmpty(),
                "Fixture requires no pre-existing crystals within the cleanup area");
        checks.check(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                "Fixture requires no pre-existing item entities within the cleanup area");
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "resonator_audit"));
        ItemStack originalHand = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
        boolean originalCreative = player.getAbilities().instabuild;
        boolean originalMayBuild = player.getAbilities().mayBuild;
        try {
            player.setPos(resonatorPos.getX() + 0.5D, resonatorPos.getY() + 1.0D,
                    resonatorPos.getZ() - 2.5D);
            player.getAbilities().mayBuild = true;
            checks.check(level.mayInteract(player, abovePos)
                            && player.mayUseItemAt(abovePos, Direction.UP, new ItemStack(Items.END_CRYSTAL)),
                    "Use a fixture position permitted by the world's protection rules");
            runCase(level, resonatorPos, player, Blocks.GRASS.defaultBlockState(), true, false, checks);
            runCase(level, resonatorPos, player, Blocks.SNOW.defaultBlockState(), true, false, checks);
            runCase(level, resonatorPos, player, Blocks.GRASS.defaultBlockState(), false, false, checks);
            runCase(level, resonatorPos, player, Blocks.SNOW.defaultBlockState(), false, false, checks);
            runCase(level, resonatorPos, player, Blocks.AIR.defaultBlockState(), false, false, checks);
            runCase(level, resonatorPos, player, Blocks.SNOW.defaultBlockState(), false, true, checks);
        } finally {
            cleanup(level, abovePos, area, originalTop);
            player.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
            player.getAbilities().instabuild = originalCreative;
            player.getAbilities().mayBuild = originalMayBuild;
        }
        GTLEnhancedcore.LOGGER.info("[RESONATOR_PLACEMENT_AUDIT] checks={} position={}",
                checks.total, resonatorPos);
        return checks.total;
    }

    private static void runCase(ServerLevel level, BlockPos resonatorPos, FakePlayer player,
            BlockState topState, boolean rejectSpawn, boolean creative, Checks checks) {
        BlockPos abovePos = resonatorPos.above();
        AABB area = new AABB(abovePos).inflate(2.0D);
        JoinProbe probe = new JoinProbe(level, abovePos, area, topState, player, rejectSpawn);
        Consumer<EntityJoinLevelEvent> finalJoin = event -> {
            if (event.getLevel() != level || event.getEntity() != probe.lastCrystal) return;
            probe.finalJoinSeen = true;
            probe.finalJoinCanceled = event.isCanceled();
        };
        PlayerInteractEvent.RightClickBlock interaction = null;
        boolean listenerRegistered = false;
        boolean finalListenerRegistered = false;
        try {
            checks.check(level.setBlock(abovePos, topState, 3)
                            || level.getBlockState(abovePos).equals(topState),
                    "Fixture top state must be installed");
            checks.check(topState.isAir() || topState.canBeReplaced(),
                    "Grass/snow fixtures must be replaceable according to the live API");
            checks.check(level.getBlockState(abovePos).equals(topState),
                    "Fixture top state must survive setup before the interaction");
            ItemStack before = new ItemStack(Items.END_CRYSTAL, 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, before.copy());
            player.getAbilities().instabuild = creative;
            interaction = new PlayerInteractEvent.RightClickBlock(
                    player, InteractionHand.MAIN_HAND, resonatorPos,
                    new BlockHitResult(Vec3.atCenterOf(resonatorPos), Direction.UP, resonatorPos, false));
            InteractionResult initialResult = interaction.getCancellationResult();
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true,
                    EntityJoinLevelEvent.class, probe);
            listenerRegistered = true;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                    EntityJoinLevelEvent.class, finalJoin);
            finalListenerRegistered = true;
            ResonatorPlacementEvents.onRightClickBlock(interaction);
            checks.check(probe.joins == 1, "Production callback must attempt one real Forge entity insertion");
            checks.check(probe.topIntactAtJoin && probe.stackIntactAtJoin && probe.noDropsAtJoin,
                    "Top state, held crystals and drops must stay untouched until insertion is accepted");
            var crystals = level.getEntitiesOfClass(EndCrystal.class, new AABB(abovePos),
                    crystal -> crystal.isAlive() && crystal.blockPosition().equals(abovePos));
            ItemStack after = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (rejectSpawn) {
                checks.check(level.getBlockState(abovePos).equals(topState),
                        "Canceled Forge insertion must preserve the replaceable top block");
                checks.check(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                        "Canceled Forge insertion must not create block-drop entities");
                checks.check(ItemStack.matches(before, after),
                        "Canceled Forge insertion must preserve the complete held stack");
                checks.check(crystals.isEmpty(), "Canceled Forge insertion must not leave a crystal");
                checks.check(!interaction.isCanceled() && interaction.getCancellationResult() == initialResult,
                        "Canceled insertion must not claim successful placement");
            } else {
                checks.check(crystals.size() == 1, "Normal insertion must leave exactly one live crystal");
                EndCrystal crystal = crystals.get(0);
                checks.check(crystal.getX() == abovePos.getX() + 0.5D
                                && crystal.getY() == abovePos.getY()
                                && crystal.getZ() == abovePos.getZ() + 0.5D && !crystal.showsBottom(),
                        "Successful crystal coordinates and hidden-base behavior remain unchanged");
                checks.check(level.getBlockState(abovePos).isAir(),
                        "Accepted insertion must clear a replaceable non-air top block");
                checks.check(after.is(Items.END_CRYSTAL) && after.getCount() == (creative ? 4 : 3),
                        "Normal placement debits one crystal only outside creative mode");
                checks.check(interaction.isCanceled()
                                && interaction.getCancellationResult() == InteractionResult.SUCCESS,
                        "Normal placement must retain its successful interaction result");
            }
            GTLEnhancedcore.LOGGER.info(
                    "[RESONATOR_PLACEMENT_AUDIT] top={} rejectSpawn={} creative={} joins={} crystals={} drops={} held={} context={}",
                    topState, rejectSpawn, creative, probe.joins, crystals.size(),
                    level.getEntitiesOfClass(ItemEntity.class, area).size(), after.getCount(),
                    diagnosticContext(level, abovePos, area, player, probe, interaction));
        } catch (RuntimeException | Error failure) {
            if (!rejectSpawn) {
                try {
                    GTLEnhancedcore.LOGGER.error(
                            "[RESONATOR_PLACEMENT_AUDIT] normalFailure={} top={} rejectSpawn={} creative={} joins={} context={}",
                            failure.toString(), topState, rejectSpawn, creative, probe.joins,
                            diagnosticContext(level, abovePos, area, player, probe, interaction));
                } catch (RuntimeException | Error diagnosticFailure) {
                    failure.addSuppressed(diagnosticFailure);
                }
            }
            throw failure;
        } finally {
            if (finalListenerRegistered) MinecraftForge.EVENT_BUS.unregister(finalJoin);
            if (listenerRegistered) MinecraftForge.EVENT_BUS.unregister(probe);
            cleanup(level, abovePos, area, Blocks.AIR.defaultBlockState());
        }
    }

    private static String diagnosticContext(ServerLevel level, BlockPos abovePos, AABB area, FakePlayer player,
            JoinProbe probe, PlayerInteractEvent.RightClickBlock interaction) {
        EndCrystal crystal = probe.lastCrystal;
        AABB exactArea = new AABB(abovePos);
        return "lastCrystalUuid=" + (crystal == null ? "none" : crystal.getUUID())
                + " lastCrystalPos=" + (crystal == null ? "none" : crystal.position())
                + " lastCrystalBlockPos=" + (crystal == null ? "none" : crystal.blockPosition())
                + " lastCrystalAlive=" + (crystal != null && crystal.isAlive())
                + " lastCrystalRemoved=" + (crystal != null && crystal.isRemoved())
                + " uuidLookupSame=" + (crystal != null && level.getEntity(crystal.getUUID()) == crystal)
                + " joinSeenAtLowest=" + probe.finalJoinSeen
                + " joinCanceledAtLowest=" + (probe.finalJoinSeen ? Boolean.toString(probe.finalJoinCanceled) : "unseen")
                + " interactionCanceled=" + (interaction == null ? "uncreated" : Boolean.toString(interaction.isCanceled()))
                + " cancellationResult=" + (interaction == null ? "uncreated" : interaction.getCancellationResult())
                + " held=" + player.getItemInHand(InteractionHand.MAIN_HAND).getCount()
                + " topPos=" + abovePos + " liveTop=" + level.getBlockState(abovePos)
                + " broadArea=" + area + " broadCrystals=" + level.getEntitiesOfClass(EndCrystal.class, area).size()
                + " exactArea=" + exactArea + " exactCrystals=" + level.getEntitiesOfClass(EndCrystal.class, exactArea).size()
                + " exactLiveAtTop=" + level.getEntitiesOfClass(EndCrystal.class, exactArea,
                        candidate -> candidate.isAlive() && candidate.blockPosition().equals(abovePos)).size();
    }

    private static void cleanup(ServerLevel level, BlockPos abovePos, AABB area, BlockState restoredTop) {
        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, area)) crystal.discard();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) item.discard();
        level.setBlock(abovePos, restoredTop, 3);
    }

    private static final class JoinProbe implements Consumer<EntityJoinLevelEvent> {
        private final ServerLevel level;
        private final BlockPos abovePos;
        private final AABB area;
        private final BlockState topState;
        private final FakePlayer player;
        private final boolean rejectSpawn;
        private int joins;
        private EndCrystal lastCrystal;
        private boolean finalJoinSeen;
        private boolean finalJoinCanceled;
        private boolean topIntactAtJoin = true;
        private boolean stackIntactAtJoin = true;
        private boolean noDropsAtJoin = true;

        private JoinProbe(ServerLevel level, BlockPos abovePos, AABB area, BlockState topState,
                FakePlayer player, boolean rejectSpawn) {
            this.level = level;
            this.abovePos = abovePos;
            this.area = area;
            this.topState = topState;
            this.player = player;
            this.rejectSpawn = rejectSpawn;
        }

        @Override
        public void accept(EntityJoinLevelEvent event) {
            if (event.getLevel() != level || !(event.getEntity() instanceof EndCrystal crystal)
                    || !crystal.blockPosition().equals(abovePos)) return;
            joins++;
            lastCrystal = crystal;
            finalJoinSeen = false;
            topIntactAtJoin &= level.getBlockState(abovePos).equals(topState);
            stackIntactAtJoin &= player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 4;
            noDropsAtJoin &= level.getEntitiesOfClass(ItemEntity.class, area).isEmpty();
            if (rejectSpawn) event.setCanceled(true);
        }
    }

    private static final class Checks {
        private int total;

        private void check(boolean condition, String message) {
            if (!condition) throw new IllegalStateException("[RESONATOR_PLACEMENT_AUDIT] " + message);
            total++;
        }
    }
}
