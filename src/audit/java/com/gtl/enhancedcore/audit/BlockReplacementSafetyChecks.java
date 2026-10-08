package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.util.BlockReplacementTransaction;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = "enhancedcore_audit", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BlockReplacementSafetyChecks {
    private static final BlockPos POSITION = new BlockPos(544, 90, 0);
    private static final BlockPos NEIGHBOR = POSITION.east();
    private static final BlockPos REMOTE = POSITION.offset(8, 0, 0);
    private static final AABB AREA = new AABB(POSITION).inflate(2.0D);
    private static final AABB REMOTE_AREA = new AABB(REMOTE).inflate(1.0D);
    private static Block initializationFailure;
    private static Block commitFailure;
    private static Block multiPlacement;

    private BlockReplacementSafetyChecks() {}

    @SubscribeEvent
    public static void registerFixtures(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, registry -> {
            initializationFailure = new Block(BlockBehaviour.Properties.copy(Blocks.GOLD_BLOCK)) {
                @Override
                public void setPlacedBy(Level world, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
                    throw new IllegalStateException("EXPECTED_AUDIT_INITIALIZATION_FAILURE");
                }
            };
            commitFailure = new Block(BlockBehaviour.Properties.copy(Blocks.GOLD_BLOCK)) {
                @Override
                public void onPlace(BlockState state, Level world, BlockPos position, BlockState previous, boolean moved) {
                    throw new IllegalStateException("EXPECTED_AUDIT_COMMIT_FAILURE");
                }
            };
            multiPlacement = new Block(BlockBehaviour.Properties.copy(Blocks.GOLD_BLOCK)) {
                @Override
                public void setPlacedBy(Level world, BlockPos position, BlockState state, LivingEntity placer, ItemStack stack) {
                    world.setBlock(NEIGHBOR, Blocks.GLASS.defaultBlockState(), 3);
                }
            };
            registry.register(new ResourceLocation("enhancedcore_audit", "placement_initialization_failure"), initializationFailure);
            registry.register(new ResourceLocation("enhancedcore_audit", "placement_commit_failure"), commitFailure);
            registry.register(new ResourceLocation("enhancedcore_audit", "placement_multi"), multiPlacement);
        });
    }

    public static int run(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        Checks checks = new Checks();
        checks.check(server.isSameThread(), "Replacement checks must run on the server thread");
        checks.check(initializationFailure != null && commitFailure != null && multiPlacement != null,
                "Fault-injection blocks must be registered before Forge freezes the registry");
        level.getChunkAt(POSITION);
        checks.check(level.getBlockState(POSITION).isAir() && level.getBlockState(NEIGHBOR).isAir(),
                "Replacement fixture requires empty owned cells");
        checks.check(level.getEntitiesOfClass(ItemEntity.class, AREA).isEmpty()
                && level.getEntitiesOfClass(ItemEntity.class, REMOTE_AREA).isEmpty(), "Replacement fixture must have no existing drops");
        checks.check(!level.captureBlockSnapshots && !level.restoringBlockSnapshots, "Fixture starts outside snapshot capture");
        int snapshotCount = level.capturedBlockSnapshots.size();
        BlockSnapshot sentinel = BlockSnapshot.create(level.dimension(), level, POSITION, 3);
        level.capturedBlockSnapshots.add(sentinel);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "replace_audit"));
        player.getAbilities().mayBuild = true;
        player.getAbilities().instabuild = false;
        player.setPos(POSITION.getX() + 0.5D, POSITION.getY() + 1.0D, POSITION.getZ() - 2.0D);
        try {
            verifyCanceled(level, player, checks);
            verifyInsufficient(level, player, checks);
            verifyEventFailure(level, player, checks);
            verifyInitializationFailure(level, player, checks);
            verifyCommitWarning(level, player, checks);
            verifyMultiCancel(level, player, checks);
            verifyWitherSafety(level, player, checks);
            verifySpongeWarning(level, player, checks);
            verifyUnrelatedDrop(level, player, checks);
            verifyDropVeto(level, player, checks);
            verifyDropExceptionFallback(level, player, checks);
            verifyDropUncancelRollback(level, player, checks);
            verifyDropUncancelAllowed(level, player, checks);
            verifyDropUncancelHiddenRollback(level, player, checks);
            verifyDropUncancelHiddenAllowed(level, player, checks);
            verifyAllowed(level, player, checks);
            level.captureBlockSnapshots = true;
            checks.check(replace(level, player, new Payment(player), Blocks.GOLD_BLOCK).equals(BlockReplacementTransaction.Result.FAILED),
                    "External capture rejects replacement without changing the guard");
            checks.check(level.captureBlockSnapshots, "External capture guard remains owned by its caller");
            level.captureBlockSnapshots = false;
            level.restoringBlockSnapshots = true;
            checks.check(replace(level, player, new Payment(player), Blocks.GOLD_BLOCK).equals(BlockReplacementTransaction.Result.FAILED),
                    "External rollback rejects replacement");
            checks.check(level.restoringBlockSnapshots, "External rollback guard remains owned by its caller");
            level.restoringBlockSnapshots = false;
            checks.check(level.capturedBlockSnapshots.size() == snapshotCount + 1
                            && level.capturedBlockSnapshots.get(snapshotCount) == sentinel,
                    "Transactions preserve pre-existing snapshots and their identity");
            verifyClean(level, checks);
        } finally {
            level.captureBlockSnapshots = false;
            level.restoringBlockSnapshots = false;
            clear(level);
            level.capturedBlockSnapshots.remove(sentinel);
            player.getInventory().clearContent();
        }
        GTLEnhancedcore.LOGGER.info("[BLOCK_REPLACEMENT_AUDIT] checks={} position={} cases=18", checks.total, POSITION);
        return checks.total;
    }

    private static void verifyCanceled(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        CompoundTag original = barrel(level, player);
        Payment payment = new Payment(player);
        PlaceProbe probe = new PlaceProbe(level, player, true, false, false);
        BlockReplacementTransaction.Result result = withPlaceProbe(probe, () -> replace(level, player, payment, Blocks.GOLD_BLOCK));
        checks.check(result == BlockReplacementTransaction.Result.CANCELED, "Placement protection veto is honored");
        checks.check(probe.calls == 1 && probe.realPlacedState, "Protection sees the actual replacement state once");
        checks.check(probe.nested == BlockReplacementTransaction.Result.FAILED, "Nested replacement is rejected without disturbing the outer transaction");
        unchanged(level, player, original, payment, checks);
    }

    private static void verifyInsufficient(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        CompoundTag original = barrel(level, player);
        Payment payment = new Payment(player);
        payment.available = false;
        checks.check(replace(level, player, payment, Blocks.GOLD_BLOCK) == BlockReplacementTransaction.Result.INSUFFICIENT,
                "Insufficient material is rejected after protection without committing");
        unchanged(level, player, original, payment, checks);
    }

    private static void verifyEventFailure(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        CompoundTag original = barrel(level, player);
        Payment payment = new Payment(player);
        PlaceProbe probe = new PlaceProbe(level, player, false, true, false);
        checks.check(withPlaceProbe(probe, () -> replace(level, player, payment, Blocks.GOLD_BLOCK)) == BlockReplacementTransaction.Result.FAILED,
                "Throwing placement listener rolls back rather than escaping the tick");
        unchanged(level, player, original, payment, checks);
    }

    private static void verifyInitializationFailure(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        CompoundTag original = barrel(level, player);
        Payment payment = new Payment(player);
        Block failing = initializationFailure;
        checks.check(replace(level, player, payment, failing) == BlockReplacementTransaction.Result.FAILED,
                "Initialization exception restores the original block entity without payment");
        unchanged(level, player, original, payment, checks);
    }

    private static void verifyCommitWarning(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        barrel(level, player);
        Payment payment = new Payment(player);
        Block failing = commitFailure;
        checks.check(replace(level, player, payment, failing) == BlockReplacementTransaction.Result.PLACED_WITH_WARNING,
                "Irreversible commit callback failure returns a committed warning");
        checks.check(level.getBlockState(POSITION).getBlock() == failing && level.getBlockEntity(POSITION) == null,
                "Committed callback failure does not pretend to restore the original container");
        checks.check(payment.charged == 1 && player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2,
                "Committed callback failure retains exactly one replacement payment");
        checks.check(dropCount(level, AREA, Items.DIAMOND) == 37 && dropCount(level, AREA, Items.PAPER) == 23,
                "Committed callback failure still delivers old container contents once");
        verifyClean(level, checks);
        clear(level);
    }

    private static void verifyMultiCancel(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        CompoundTag original = barrel(level, player);
        Payment payment = new Payment(player);
        Block multi = multiPlacement;
        PlaceProbe probe = new PlaceProbe(level, player, true, false, false);
        checks.check(withPlaceProbe(probe, () -> replace(level, player, payment, multi)) == BlockReplacementTransaction.Result.CANCELED,
                "Multi-position initialization obeys placement protection");
        checks.check(probe.multi && level.getBlockState(NEIGHBOR).isAir(), "Multi-place cancellation restores neighboring snapshots");
        unchanged(level, player, original, payment, checks);
    }

    private static void verifyUnrelatedDrop(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        CompoundTag original = barrel(level, player);
        Payment payment = new Payment(player);
        PlaceProbe probe = new PlaceProbe(level, player, true, false, true);
        checks.check(withPlaceProbe(probe, () -> replace(level, player, payment, Blocks.GOLD_BLOCK)) == BlockReplacementTransaction.Result.CANCELED,
                "A rejected replacement still returns the protected result");
        checks.check(dropCount(level, REMOTE_AREA, Items.DIAMOND) == 1, "Rollback does not swallow unrelated world drops");
        unchanged(level, player, original, payment, checks);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, REMOTE_AREA)) item.discard();
    }

    private static void verifyWitherSafety(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        clear(level);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WITHER_SKELETON_SKULL, 3));
        AABB bossArea = new AABB(POSITION).inflate(4.0D);
        BlockPos[] bases = {POSITION.below(), POSITION.west().below(), POSITION.east().below(), POSITION.below(2)};
        checks.check(level.getEntitiesOfClass(WitherBoss.class, bossArea).isEmpty(), "Summoning fixture has no pre-existing wither");
        try {
            for (BlockPos base : bases) level.setBlock(base, Blocks.SOUL_SAND.defaultBlockState(), 3);
            level.setBlock(POSITION.west(), Blocks.WITHER_SKELETON_SKULL.defaultBlockState(), 3);
            level.setBlock(POSITION.east(), Blocks.WITHER_SKELETON_SKULL.defaultBlockState(), 3);
            level.setBlock(POSITION, Blocks.STONE.defaultBlockState(), 3);
            Payment canceledPayment = new Payment(player);
            PlaceProbe probe = new PlaceProbe(level, player, true, false, false);
            checks.check(withPlaceProbe(probe, () -> BlockReplacementTransaction.replace(level, player, POSITION,
                    Blocks.WITHER_SKELETON_SKULL.defaultBlockState(), new ItemStack(Items.WITHER_SKELETON_SKULL),
                    canceledPayment::charge)) == BlockReplacementTransaction.Result.CANCELED,
                    "Canceled summoning skull obeys placement protection before creating an entity");
            checks.check(level.getEntitiesOfClass(WitherBoss.class, bossArea).isEmpty(), "Canceled replacement does not leave a free wither");
            checks.check(level.getBlockState(POSITION).is(Blocks.STONE) && canceledPayment.charged == 0,
                    "Canceled skull restores the target and never pays");
            for (BlockPos base : bases) checks.check(level.getBlockState(base).is(Blocks.SOUL_SAND), "Canceled skull preserves the summoning structure");
            Payment insufficientPayment = new Payment(player);
            insufficientPayment.available = false;
            checks.check(BlockReplacementTransaction.replace(level, player, POSITION,
                    Blocks.WITHER_SKELETON_SKULL.defaultBlockState(), new ItemStack(Items.WITHER_SKELETON_SKULL),
                    insufficientPayment::charge) == BlockReplacementTransaction.Result.INSUFFICIENT,
                    "Unpaid skull is rolled back before entity-spawning initialization");
            checks.check(level.getEntitiesOfClass(WitherBoss.class, bossArea).isEmpty()
                    && level.getBlockState(POSITION).is(Blocks.STONE), "Material shortage never creates a free wither");
            Payment allowedPayment = new Payment(player);
            checks.check(BlockReplacementTransaction.replace(level, player, POSITION,
                    Blocks.WITHER_SKELETON_SKULL.defaultBlockState(), new ItemStack(Items.WITHER_SKELETON_SKULL),
                    allowedPayment::charge) == BlockReplacementTransaction.Result.PLACED,
                    "Allowed paid skull retains the terminal's existing summoning behavior");
            checks.check(level.getEntitiesOfClass(WitherBoss.class, bossArea).size() == 1,
                    "The actual complete soul-sand fixture creates exactly one paid wither");
            checks.check(allowedPayment.charged == 1 && player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2,
                    "Summoning commits exactly one material payment without a false rollback");
            checks.check(level.getBlockState(POSITION).isAir(), "Successful summoning is not incorrectly restored by the placement transaction");
            verifyClean(level, checks);
        } finally {
            for (WitherBoss boss : level.getEntitiesOfClass(WitherBoss.class, bossArea)) boss.discard();
            level.setBlock(POSITION.west(), Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos base : bases) level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
            clear(level);
        }
    }

    private static void verifySpongeWarning(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        clear(level);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_BLOCK, 3));
        level.setBlock(POSITION, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(NEIGHBOR, Blocks.WATER.defaultBlockState(), 3);
        Payment payment = new Payment(player);
        boolean[] injected = {false};
        Consumer<BlockEvent.NeighborNotifyEvent> listener = event -> {
            if (!injected[0] && event.getPos().equals(NEIGHBOR)
                    && !level.captureBlockSnapshots && !level.restoringBlockSnapshots) {
                injected[0] = true;
                throw new IllegalStateException("EXPECTED_AUDIT_SPONGE_NOTIFICATION_FAILURE");
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, BlockEvent.NeighborNotifyEvent.class, listener);
        try {
            checks.check(replace(level, player, payment, Blocks.SPONGE) == BlockReplacementTransaction.Result.PLACED_WITH_WARNING,
                    "Sponge side-effect failure is a committed warning, not a false rollback and refund");
            checks.check(injected[0] && level.getBlockState(NEIGHBOR).isAir(), "Real sponge absorption executes before the injected notification error");
            checks.check(!level.getBlockState(POSITION).is(Blocks.STONE) && payment.charged == 1
                    && player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2,
                    "Irreversible sponge side effects retain their paid replacement rather than creating unpaid changes");
            verifyClean(level, checks);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            clear(level);
        }
    }

    private static void verifyDropVeto(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        barrel(level, player);
        Payment payment = new Payment(player);
        JoinProbe probe = new JoinProbe(level, true, false);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, EntityJoinLevelEvent.class, probe);
        try {
            checks.check(replace(level, player, payment, Blocks.GOLD_BLOCK) == BlockReplacementTransaction.Result.PLACED,
                    "An entity drop veto does not veto allowed block placement");
            checks.check(!probe.repeated && !probe.seen.isEmpty(), "Previously vetoed container drops are never replayed");
            checks.check(level.getEntitiesOfClass(ItemEntity.class, AREA).isEmpty(), "Earlier entity vetoes remain effective");
            checks.check(payment.charged == 1, "Successful placement pays once despite a drop veto");
            verifyClean(level, checks);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            clear(level);
        }
    }

    private static void verifyDropExceptionFallback(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        barrel(level, player);
        Payment payment = new Payment(player);
        JoinProbe probe = new JoinProbe(level, false, true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, EntityJoinLevelEvent.class, probe);
        try {
            checks.check(replace(level, player, payment, Blocks.GOLD_BLOCK) == BlockReplacementTransaction.Result.PLACED,
                    "A throwing drop replay does not turn a committed block into a failed transaction");
            checks.check(probe.repeated, "Drop replay exception fixture actually executes");
            checks.check(inventoryCount(player, Items.DIAMOND) == 37 && inventoryCount(player, Items.PAPER) == 23,
                    "Failed container drops fall back to the player's inventory with conserved counts");
            checks.check(level.getEntitiesOfClass(ItemEntity.class, AREA).isEmpty(), "Drop fallback does not duplicate world items");
            checks.check(payment.charged == 1, "Drop fallback does not duplicate replacement payment");
            verifyClean(level, checks);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            clear(level);
        }
    }

    private static void verifyDropUncancelRollback(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        CompoundTag original = barrel(level, player);
        ItemStack originalMaterial = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
        Payment payment = new Payment(player);
        UncancelJoinProbe joinProbe = new UncancelJoinProbe(level);
        UncancelPlaceProbe placeProbe = new UncancelPlaceProbe(level, player, true);
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, joinProbe);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, BlockEvent.EntityPlaceEvent.class, placeProbe);
            checks.check(replace(level, player, payment, Blocks.GOLD_BLOCK) == BlockReplacementTransaction.Result.CANCELED,
                    "Placement cancellation survives a later LOWEST listener uncanceling captured item joins");
            verifyUncancelBeforePlace(joinProbe, placeProbe, checks);
            unchanged(level, player, original, payment, checks);
            checks.check(ItemStack.matches(originalMaterial, player.getItemInHand(InteractionHand.MAIN_HAND)),
                    "Canceled placement preserves the complete original material stack");
            checks.check(inventoryCount(player, Items.DIAMOND) == 0 && inventoryCount(player, Items.PAPER) == 0,
                    "Canceled uncanceled drops never fall back into the player's inventory");
            for (ItemEntity item : joinProbe.entities) {
                checks.check(level.getEntity(item.getUUID()) == null && item.isRemoved(),
                        "Rollback removes each already-joined captured entity from the world");
            }
            GTLEnhancedcore.LOGGER.info(
                    "[BLOCK_REPLACEMENT_UNCANCEL_AUDIT] canceled=true joins={} uniqueUuids={} prePlaceDiamonds={} prePlacePaper={} worldDrops={} charged={}",
                    joinProbe.calls, joinProbe.seen.size(), placeProbe.diamonds, placeProbe.papers,
                    level.getEntitiesOfClass(ItemEntity.class, AREA).size(), payment.charged);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(placeProbe);
            MinecraftForge.EVENT_BUS.unregister(joinProbe);
            clear(level);
        }
    }

    private static void verifyDropUncancelAllowed(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        barrel(level, player);
        ItemStack expectedMaterial = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
        expectedMaterial.shrink(1);
        Payment payment = new Payment(player);
        UncancelJoinProbe joinProbe = new UncancelJoinProbe(level);
        UncancelPlaceProbe placeProbe = new UncancelPlaceProbe(level, player, false);
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, joinProbe);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, BlockEvent.EntityPlaceEvent.class, placeProbe);
            checks.check(replace(level, player, payment, Blocks.GOLD_BLOCK) == BlockReplacementTransaction.Result.PLACED,
                    "Allowed placement commits after a later LOWEST listener uncancels captured item joins");
            verifyUncancelBeforePlace(joinProbe, placeProbe, checks);
            checks.check(level.getBlockState(POSITION).is(Blocks.GOLD_BLOCK) && level.getBlockEntity(POSITION) == null,
                    "Allowed uncanceled placement replaces the original barrel exactly once");
            checks.check(dropCount(level, AREA, Items.DIAMOND) == 37 && dropCount(level, AREA, Items.PAPER) == 23,
                    "Already-joined container drops remain exactly 37 diamonds and 23 paper");
            Set<UUID> worldUuids = new HashSet<>();
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, AREA)) {
                checks.check(worldUuids.add(item.getUUID()), "World container drops have unique UUIDs");
                checks.check(item.getItem().is(Items.DIAMOND) || item.getItem().is(Items.PAPER),
                        "Allowed uncanceled placement produces no unrelated world items");
                if (item.getItem().is(Items.PAPER)) {
                    checks.check(item.getItem().getTag() != null
                                    && "preserve-slot-and-tag".equals(item.getItem().getTag().getString("AuditPayload")),
                            "Already-joined paper drops retain their original item NBT");
                }
            }
            checks.check(worldUuids.equals(joinProbe.seen), "World UUIDs match exactly the initially uncanceled joins");
            for (ItemEntity item : joinProbe.entities) {
                checks.check(level.getEntity(item.getUUID()) == item && !item.isRemoved(),
                        "Allowed placement retains the already-joined entity instead of replaying or replacing it");
            }
            checks.check(payment.charged == 1 && ItemStack.matches(expectedMaterial, player.getItemInHand(InteractionHand.MAIN_HAND)),
                    "Allowed uncanceled placement charges exactly one material without other stack changes");
            checks.check(inventoryCount(player, Items.DIAMOND) == 0 && inventoryCount(player, Items.PAPER) == 0,
                    "Already-joined drops are not duplicated by an inventory fallback");
            verifyClean(level, checks);
            GTLEnhancedcore.LOGGER.info(
                    "[BLOCK_REPLACEMENT_UNCANCEL_AUDIT] canceled=false joins={} uniqueUuids={} diamonds={} paper={} charged={}",
                    joinProbe.calls, joinProbe.seen.size(), dropCount(level, AREA, Items.DIAMOND),
                    dropCount(level, AREA, Items.PAPER), payment.charged);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(placeProbe);
            MinecraftForge.EVENT_BUS.unregister(joinProbe);
            clear(level);
        }
    }

    private static void verifyUncancelBeforePlace(UncancelJoinProbe joinProbe, UncancelPlaceProbe placeProbe, Checks checks) {
        checks.check(placeProbe.calls == 1, "The real Forge placement event runs exactly once");
        checks.check(!joinProbe.seen.isEmpty() && joinProbe.allInitiallyCanceled,
                "The later LOWEST receiveCanceled listener observes transaction-canceled item joins before uncanceling them");
        checks.check(!joinProbe.repeated && joinProbe.calls == joinProbe.seen.size(),
                "Each captured UUID enters EntityJoinLevelEvent exactly once without replay");
        checks.check(placeProbe.diamonds == 37 && placeProbe.papers == 23 && placeProbe.onlyInventoryItems,
                "All original inventory drops are already in the world before the placement decision");
        checks.check(placeProbe.allEntitiesInWorld && placeProbe.seen.equals(joinProbe.seen),
                "Every initially uncanceled captured UUID is truly joined before the placement event");
    }

    private static void verifyDropUncancelHiddenRollback(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        verifyDropUncancelHidden(level, player, true, checks);
    }

    private static void verifyDropUncancelHiddenAllowed(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        verifyDropUncancelHidden(level, player, false, checks);
    }

    private static void verifyDropUncancelHidden(ServerLevel level, FakePlayer player, boolean cancel, Checks checks) throws Exception {
        PersistentEntitySectionManager<?> manager = entityManager(level, checks);
        ChunkPos fixtureChunk = new ChunkPos(POSITION);
        checks.check(fixtureChunk.equals(new ChunkPos(34, 0)), "Hidden visibility changes are bounded to the owned fixture chunk");
        checks.check(manager.canPositionTick(fixtureChunk), "The owned fixture chunk must originally be TICKING");
        CompoundTag original = barrel(level, player);
        ItemStack originalMaterial = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
        Payment payment = new Payment(player);
        UncancelJoinProbe joinProbe = new UncancelJoinProbe(level);
        HiddenUncancelPlaceProbe placeProbe = new HiddenUncancelPlaceProbe(level, player, manager, fixtureChunk, joinProbe, cancel);
        boolean visibilityChanged = false;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, joinProbe);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, BlockEvent.EntityPlaceEvent.class, placeProbe);
            visibilityChanged = true;
            manager.updateChunkStatus(fixtureChunk, Visibility.HIDDEN);
            checks.check(!manager.canPositionTick(fixtureChunk), "The real fixture entity manager enters HIDDEN visibility");
            checks.check(replace(level, player, payment, Blocks.GOLD_BLOCK)
                            == (cancel ? BlockReplacementTransaction.Result.CANCELED : BlockReplacementTransaction.Result.PLACED),
                    "Hidden uncanceled item joins do not change the real placement decision");
            checks.check(placeProbe.calls == 1 && placeProbe.seen.equals(joinProbe.seen),
                    "The placement decision observes exactly the initially uncanceled captured UUIDs");
            checks.check(!joinProbe.seen.isEmpty() && joinProbe.allInitiallyCanceled,
                    "The later LOWEST receiveCanceled listener really uncancels transaction-captured hidden joins");
            checks.check(!joinProbe.repeated && joinProbe.calls == joinProbe.seen.size(),
                    "Each hidden captured UUID reaches EntityJoinLevelEvent once without replay");
            checks.check(placeProbe.loadedBeforePlace.equals(joinProbe.seen) && placeProbe.allAddedAtPlace
                            && placeProbe.allInvisibleAtPlace && placeProbe.allInFixtureChunk,
                    "Before the placement decision every hidden UUID is manager-loaded and added to the world but invisible to level.getEntity");
            checks.check(placeProbe.diamonds == 37 && placeProbe.papers == 23
                            && placeProbe.onlyInventoryItems && placeProbe.paperNbtIntact,
                    "The real hidden joins retain exactly 37 diamonds and 23 paper with original item NBT");
            checks.check(inventoryCount(player, Items.DIAMOND) == 0 && inventoryCount(player, Items.PAPER) == 0,
                    "Hidden captured drops never duplicate into an inventory fallback");
            if (cancel) {
                unchanged(level, player, original, payment, checks);
                checks.check(ItemStack.matches(originalMaterial, player.getItemInHand(InteractionHand.MAIN_HAND)),
                        "Canceled hidden placement preserves the complete original material stack");
                for (ItemEntity item : joinProbe.entities) {
                    checks.check(!manager.isLoaded(item.getUUID()) && item.isRemoved(),
                            "Hidden rollback removes every captured entity and its manager UUID without visible lookup");
                }
            } else {
                ItemStack expectedMaterial = originalMaterial.copy();
                expectedMaterial.shrink(1);
                checks.check(level.getBlockState(POSITION).is(Blocks.GOLD_BLOCK) && level.getBlockEntity(POSITION) == null,
                        "Allowed hidden placement commits the replacement block");
                checks.check(payment.charged == 1
                                && ItemStack.matches(expectedMaterial, player.getItemInHand(InteractionHand.MAIN_HAND)),
                        "Allowed hidden placement charges exactly one material");
                for (ItemEntity item : joinProbe.entities) {
                    checks.check(manager.isLoaded(item.getUUID()) && item.isAddedToWorld() && !item.isRemoved()
                                    && level.getEntity(item.getUUID()) == null,
                            "Allowed placement retains each already-added hidden entity without replay or fallback");
                }
            }
            manager.updateChunkStatus(fixtureChunk, Visibility.TICKING);
            checks.check(manager.canPositionTick(fixtureChunk), "The owned fixture chunk returns to TICKING");
            if (cancel) {
                checks.check(level.getEntitiesOfClass(ItemEntity.class, AREA).isEmpty(),
                        "Restoring visibility after hidden rollback reveals no duplicated world drops");
                for (ItemEntity item : joinProbe.entities) {
                    checks.check(!manager.isLoaded(item.getUUID()) && item.isRemoved() && level.getEntity(item.getUUID()) == null,
                            "Removed hidden UUIDs do not reappear when their chunk becomes TICKING");
                }
            } else {
                checks.check(dropCount(level, AREA, Items.DIAMOND) == 37 && dropCount(level, AREA, Items.PAPER) == 23,
                        "Restored TICKING visibility exposes exactly 37 diamonds and 23 paper");
                Set<UUID> worldUuids = new HashSet<>();
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, AREA)) {
                    checks.check(worldUuids.add(item.getUUID()), "Restored hidden drops have unique world UUIDs");
                    checks.check(item.getItem().is(Items.DIAMOND) || item.getItem().is(Items.PAPER),
                            "Restored hidden drops contain only the previous barrel inventory");
                    if (item.getItem().is(Items.PAPER)) {
                        checks.check(item.getItem().getTag() != null
                                        && "preserve-slot-and-tag".equals(item.getItem().getTag().getString("AuditPayload")),
                                "Restored hidden paper drops preserve their original item NBT");
                    }
                }
                checks.check(worldUuids.equals(joinProbe.seen)
                                && level.getEntitiesOfClass(ItemEntity.class, AREA).size() == joinProbe.seen.size(),
                        "Visible world entity count and UUIDs exactly match the original hidden joins");
                for (ItemEntity item : joinProbe.entities) {
                    checks.check(manager.isLoaded(item.getUUID()) && level.getEntity(item.getUUID()) == item
                                    && item.isAddedToWorld() && !item.isRemoved(),
                            "Restoring TICKING makes the same already-added entity visible without replacement");
                }
            }
            checks.check(!joinProbe.repeated && joinProbe.calls == joinProbe.seen.size(),
                    "Visibility restoration does not replay any captured UUID into EntityJoinLevelEvent");
            verifyClean(level, checks);
            GTLEnhancedcore.LOGGER.info(
                    "[BLOCK_REPLACEMENT_HIDDEN_UNCANCEL_AUDIT] canceled={} chunk={} loadedAtPlace={} addedAtPlace={} hiddenAtPlace={} joins={} uniqueUuids={} visibleDrops={} diamonds={} paper={} charged={} uuids={}",
                    cancel, fixtureChunk, placeProbe.loadedBeforePlace.size(), placeProbe.allAddedAtPlace,
                    placeProbe.allInvisibleAtPlace, joinProbe.calls, joinProbe.seen.size(),
                    level.getEntitiesOfClass(ItemEntity.class, AREA).size(), dropCount(level, AREA, Items.DIAMOND),
                    dropCount(level, AREA, Items.PAPER), payment.charged, joinProbe.seen);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(placeProbe);
            MinecraftForge.EVENT_BUS.unregister(joinProbe);
            try {
                if (visibilityChanged) manager.updateChunkStatus(fixtureChunk, Visibility.TICKING);
            } finally {
                for (ItemEntity item : joinProbe.entities) {
                    if (!item.isRemoved()) item.discard();
                }
                clear(level);
            }
        }
    }

    private static PersistentEntitySectionManager<?> entityManager(ServerLevel level, Checks checks) throws IllegalAccessException {
        Field managerField = null;
        int managerFields = 0;
        for (Field candidate : ServerLevel.class.getDeclaredFields()) {
            if (candidate.getType() != PersistentEntitySectionManager.class) continue;
            managerFields++;
            managerField = candidate;
        }
        checks.check(managerFields == 1, "ServerLevel must have exactly one PersistentEntitySectionManager field by type");
        managerField.setAccessible(true);
        Object manager = managerField.get(level);
        checks.check(manager instanceof PersistentEntitySectionManager<?>, "The reflected server entity manager must be present");
        return (PersistentEntitySectionManager<?>) manager;
    }

    private static void verifyAllowed(ServerLevel level, FakePlayer player, Checks checks) throws Exception {
        barrel(level, player);
        Payment payment = new Payment(player);
        checks.check(replace(level, player, payment, Blocks.GOLD_BLOCK) == BlockReplacementTransaction.Result.PLACED,
                "An allowed replacement still works after all rejection paths");
        checks.check(level.getBlockState(POSITION).is(Blocks.GOLD_BLOCK) && level.getBlockEntity(POSITION) == null,
                "Allowed placement commits the requested state");
        checks.check(dropCount(level, AREA, Items.DIAMOND) == 37 && dropCount(level, AREA, Items.PAPER) == 23,
                "Allowed container removal drops exactly the previous contents");
        checks.check(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2
                && payment.charged == 1, "Allowed replacement consumes exactly one material");
        verifyClean(level, checks);
        clear(level);
    }

    private static BlockReplacementTransaction.Result replace(ServerLevel level, FakePlayer player, Payment payment, Block block) {
        return BlockReplacementTransaction.replace(level, player, POSITION, block.defaultBlockState(),
                new ItemStack(Items.GOLD_BLOCK), payment::charge);
    }

    private static BlockReplacementTransaction.Result withPlaceProbe(PlaceProbe probe,
            java.util.function.Supplier<BlockReplacementTransaction.Result> operation) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, BlockEvent.EntityPlaceEvent.class, probe);
        try {
            return operation.get();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }
    }

    private static CompoundTag barrel(ServerLevel level, FakePlayer player) {
        clear(level);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_BLOCK, 3));
        level.setBlock(POSITION, Blocks.BARREL.defaultBlockState(), 3);
        BarrelBlockEntity barrel = (BarrelBlockEntity) level.getBlockEntity(POSITION);
        barrel.setCustomName(Component.literal("audit_complete_nbt"));
        barrel.setItem(0, new ItemStack(Items.DIAMOND, 37));
        ItemStack paper = new ItemStack(Items.PAPER, 23);
        paper.getOrCreateTag().putString("AuditPayload", "preserve-slot-and-tag");
        barrel.setItem(18, paper);
        barrel.setChanged();
        return barrel.saveWithFullMetadata();
    }

    private static void unchanged(ServerLevel level, FakePlayer player, CompoundTag original,
            Payment payment, Checks checks) throws Exception {
        checks.check(level.getBlockState(POSITION).is(Blocks.BARREL), "Rejected replacement retains the original block");
        checks.check(level.getBlockEntity(POSITION) instanceof BarrelBlockEntity barrel
                && original.equals(barrel.saveWithFullMetadata()), "Rollback retains full container metadata and item NBT");
        checks.check(level.getEntitiesOfClass(ItemEntity.class, AREA).isEmpty(), "Rollback produces no duplicated container drops");
        checks.check(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 3, "Rejected replacement retains or refunds its material");
        checks.check(payment.charged == 0, "Rejected and reversible failure paths never charge material");
        verifyClean(level, checks);
    }

    private static void verifyClean(ServerLevel level, Checks checks) throws Exception {
        Field active = BlockReplacementTransaction.class.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        checks.check(!level.captureBlockSnapshots && !level.restoringBlockSnapshots, "Transaction clears both world flags");
        checks.check(((ThreadLocal<?>) active.get(null)).get() == null, "Transaction clears its thread-local capture");
    }

    private static int dropCount(ServerLevel level, AABB area, net.minecraft.world.item.Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static int inventoryCount(FakePlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void clear(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, AREA)) item.discard();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, REMOTE_AREA)) item.discard();
        if (level.getBlockEntity(POSITION) instanceof BarrelBlockEntity barrel) barrel.clearContent();
        level.setBlock(POSITION, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(NEIGHBOR, Blocks.AIR.defaultBlockState(), 3);
    }

    private static final class Payment {
        private final FakePlayer player;
        private boolean available = true;
        private int charged;

        private Payment(FakePlayer player) {
            this.player = player;
        }

        private boolean charge() {
            if (!available) return false;
            player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
            charged++;
            return true;
        }

    }

    private static final class PlaceProbe implements Consumer<BlockEvent.EntityPlaceEvent> {
        private final ServerLevel level;
        private final FakePlayer player;
        private final boolean cancel;
        private final boolean fail;
        private final boolean remoteDrop;
        private int calls;
        private boolean realPlacedState;
        private boolean multi;
        private BlockReplacementTransaction.Result nested;

        private PlaceProbe(ServerLevel level, FakePlayer player, boolean cancel, boolean fail, boolean remoteDrop) {
            this.level = level;
            this.player = player;
            this.cancel = cancel;
            this.fail = fail;
            this.remoteDrop = remoteDrop;
        }

        @Override
        public void accept(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity() != player || !event.getPos().equals(POSITION)) return;
            calls++;
            realPlacedState = event.getPlacedBlock().equals(level.getBlockState(POSITION));
            multi = event instanceof BlockEvent.EntityMultiPlaceEvent;
            nested = replace(level, player, new Payment(player), Blocks.EMERALD_BLOCK);
            if (remoteDrop) level.addFreshEntity(new ItemEntity(level, REMOTE.getX() + 0.5D, REMOTE.getY(),
                    REMOTE.getZ() + 0.5D, new ItemStack(Items.DIAMOND)));
            if (fail) throw new IllegalStateException("EXPECTED_AUDIT_PLACEMENT_EVENT_FAILURE");
            if (cancel) event.setCanceled(true);
        }
    }

    private static final class JoinProbe implements Consumer<EntityJoinLevelEvent> {
        private final ServerLevel level;
        private final boolean cancel;
        private final boolean failRepeated;
        private final Set<UUID> seen = new HashSet<>();
        private boolean repeated;

        private JoinProbe(ServerLevel level, boolean cancel, boolean failRepeated) {
            this.level = level;
            this.cancel = cancel;
            this.failRepeated = failRepeated;
        }

        @Override
        public void accept(EntityJoinLevelEvent event) {
            if (event.getLevel() != level || !(event.getEntity() instanceof ItemEntity item)
                    || !AREA.contains(item.position())) return;
            if (!seen.add(item.getUUID())) {
                repeated = true;
                if (failRepeated) throw new IllegalStateException("EXPECTED_AUDIT_DROP_REPLAY_FAILURE");
            }
            if (cancel) event.setCanceled(true);
        }
    }

    private static final class UncancelJoinProbe implements Consumer<EntityJoinLevelEvent> {
        private final ServerLevel level;
        private final Set<UUID> seen = new HashSet<>();
        private final Set<ItemEntity> entities = new HashSet<>();
        private int calls;
        private boolean repeated;
        private boolean allInitiallyCanceled = true;

        private UncancelJoinProbe(ServerLevel level) {
            this.level = level;
        }

        @Override
        public void accept(EntityJoinLevelEvent event) {
            if (event.getLevel() != level || !(event.getEntity() instanceof ItemEntity item)
                    || !AREA.contains(item.position())) return;
            calls++;
            allInitiallyCanceled &= event.isCanceled();
            if (!seen.add(item.getUUID())) repeated = true;
            entities.add(item);
            event.setCanceled(false);
        }
    }

    private static final class UncancelPlaceProbe implements Consumer<BlockEvent.EntityPlaceEvent> {
        private final ServerLevel level;
        private final FakePlayer player;
        private final boolean cancel;
        private final Set<UUID> seen = new HashSet<>();
        private int calls;
        private int diamonds;
        private int papers;
        private boolean onlyInventoryItems = true;
        private boolean allEntitiesInWorld = true;

        private UncancelPlaceProbe(ServerLevel level, FakePlayer player, boolean cancel) {
            this.level = level;
            this.player = player;
            this.cancel = cancel;
        }

        @Override
        public void accept(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity() != player || !event.getPos().equals(POSITION)) return;
            calls++;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, AREA)) {
                seen.add(item.getUUID());
                allEntitiesInWorld &= level.getEntity(item.getUUID()) == item;
                if (item.getItem().is(Items.DIAMOND)) diamonds += item.getItem().getCount();
                else if (item.getItem().is(Items.PAPER)) papers += item.getItem().getCount();
                else onlyInventoryItems = false;
            }
            if (cancel) event.setCanceled(true);
        }
    }

    private static final class HiddenUncancelPlaceProbe implements Consumer<BlockEvent.EntityPlaceEvent> {
        private final ServerLevel level;
        private final FakePlayer player;
        private final PersistentEntitySectionManager<?> manager;
        private final ChunkPos fixtureChunk;
        private final UncancelJoinProbe joinProbe;
        private final boolean cancel;
        private final Set<UUID> seen = new HashSet<>();
        private final Set<UUID> loadedBeforePlace = new HashSet<>();
        private int calls;
        private int diamonds;
        private int papers;
        private boolean allAddedAtPlace = true;
        private boolean allInvisibleAtPlace = true;
        private boolean allInFixtureChunk = true;
        private boolean onlyInventoryItems = true;
        private boolean paperNbtIntact = true;

        private HiddenUncancelPlaceProbe(ServerLevel level, FakePlayer player, PersistentEntitySectionManager<?> manager,
                ChunkPos fixtureChunk, UncancelJoinProbe joinProbe, boolean cancel) {
            this.level = level;
            this.player = player;
            this.manager = manager;
            this.fixtureChunk = fixtureChunk;
            this.joinProbe = joinProbe;
            this.cancel = cancel;
        }

        @Override
        public void accept(BlockEvent.EntityPlaceEvent event) {
            if (event.getEntity() != player || !event.getPos().equals(POSITION)) return;
            calls++;
            for (ItemEntity item : joinProbe.entities) {
                seen.add(item.getUUID());
                if (manager.isLoaded(item.getUUID())) loadedBeforePlace.add(item.getUUID());
                allAddedAtPlace &= item.isAddedToWorld();
                allInvisibleAtPlace &= level.getEntity(item.getUUID()) == null;
                allInFixtureChunk &= fixtureChunk.equals(new ChunkPos(item.blockPosition()));
                if (item.getItem().is(Items.DIAMOND)) {
                    diamonds += item.getItem().getCount();
                } else if (item.getItem().is(Items.PAPER)) {
                    papers += item.getItem().getCount();
                    paperNbtIntact &= item.getItem().getTag() != null
                            && "preserve-slot-and-tag".equals(item.getItem().getTag().getString("AuditPayload"));
                } else {
                    onlyInventoryItems = false;
                }
            }
            if (cancel) event.setCanceled(true);
        }
    }

    private static final class Checks {
        private int total;

        private void check(boolean condition, String message) {
            if (!condition) throw new IllegalStateException("[BLOCK_REPLACEMENT_AUDIT] " + message);
            total++;
        }
    }
}
