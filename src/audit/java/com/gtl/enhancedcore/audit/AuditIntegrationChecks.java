package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.CausalityTerminalMachine;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.hatch.QuantumDataAccessHatchMachine;
import com.gtl.enhancedcore.common.machine.hatch.CircuitEncoderHatchMachine;
import com.gtl.enhancedcore.integration.jade.MachineDiagnosticProvider;
import com.gtl.enhancedcore.mixin.gtceu.MultiblockMachineDefinitionAccessor;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.fml.common.Mod;
import snownee.jade.impl.BlockAccessorImpl;

/** Only packaged by the explicit auditRuntimeJar task, never shipped to players. */
@Mod("enhancedcore_audit")
public final class AuditIntegrationChecks {
    private static int checks;

    public static int run(MinecraftServer server) throws Exception {
        checks = 0;
        var world = server.overworld();
        world.setChunkForced(0, 0, true);
        var hatchPos = new BlockPos(3, 80, 3);
        var controllerPos = new BlockPos(8, 80, 3);
        world.setBlock(hatchPos, ((Block) GTLEnhancedcoreMachines.getCircuitEncoderHatch().get()).defaultBlockState(), 3);
        world.setBlock(controllerPos, GTLEnhancedcoreMachines.getIntegratedUniversalFactory().defaultBlockState(), 3);
        var hatch = (CircuitEncoderHatchMachine) MetaMachine.getMachine(world, hatchPos);
        var controller = (MultiblockControllerMachine) MetaMachine.getMachine(world, controllerPos);
        var first = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "audit0"));
        var second = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "audit1"));
        first.setPos(3.5, 80.5, 4.5);
        second.setPos(3.5, 80.5, 4.5);
        check(!canUse(first, hatchPos), "Packet accepted without machine UI");
        first.containerMenu = new ModularUIContainer(hatch.createUI(first), 21);
        check(canUse(first, hatchPos), "Legitimate circuit UI packet rejected");
        check(!canUse(second, hatchPos), "Second player borrowed first player UI");
        second.containerMenu = new ModularUIContainer(hatch.createUI(second), 22);
        check(canUse(second, hatchPos), "Second independent UI rejected");
        check(!canUse(first, controllerPos), "UI allowed a different target");
        first.setPos(50, 80, 50);
        check(!canUse(first, hatchPos), "Distant packet accepted");
        log("two_player_menu_isolation_and_distance=OK");

        var data = new CompoundTag();
        var accessor = new BlockAccessorImpl.Builder().level(world).player(second).serverData(data)
                .serverConnected(true).showDetails(true).blockState(world.getBlockState(controllerPos))
                .blockEntity(() -> world.getBlockEntity(controllerPos))
                .hit(new BlockHitResult(Vec3.atCenterOf(controllerPos), Direction.NORTH, controllerPos, false)).build();
        new MachineDiagnosticProvider().appendServerData(data, accessor);
        var encoded = data.getString("enhancedMachineDiagnostic");
        check(encoded.contains("translate") && encoded.contains("gtceu.multiblock.invalid_structure"),
                "Jade did not preserve localized reason");
        check(Component.Serializer.fromJson(encoded) != null, "Invalid Jade component JSON");
        log("jade_server_component_serialization=OK");

        var dataPos = new BlockPos(10, 80, 3);
        world.setBlock(dataPos, ((Block) GTLEnhancedcoreMachines.getQuantumDataAccessHatch().get()).defaultBlockState(), 3);
        var dataHatch = (QuantumDataAccessHatchMachine) MetaMachine.getMachine(world, dataPos);
        var recipe = world.getRecipeManager().getAllRecipesFor(GTRecipeTypes.ASSEMBLY_LINE_RECIPES).stream()
                .filter(candidate -> candidate.conditions.stream().anyMatch(ResearchCondition.class::isInstance))
                .findFirst().orElseThrow();
        check(!dataHatch.isRecipeAvailable(recipe, new HashSet<>()), "Empty data hatch bypassed research");
        var recipesField = QuantumDataAccessHatchMachine.class.getDeclaredField("quantumRecipes");
        recipesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var staleCache = (Set<com.gregtechceu.gtceu.api.recipe.GTRecipe>) recipesField.get(dataHatch);
        staleCache.add(recipe);
        check(dataHatch.isRecipeAvailable(recipe, new HashSet<>()), "Stale cache fixture was not installed");
        MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(), null));
        check(!dataHatch.isRecipeAvailable(recipe, new HashSet<>()), "Datapack reload retained stale research cache");
        log("research_cache_reload_invalidation=OK");

        var terminalPos = new BlockPos(12, 80, 3);
        world.setBlock(terminalPos, ((Block) GTLEnhancedcoreMachines.getCausalityTerminal().get()).defaultBlockState(), 3);
        var terminal = (CausalityTerminalMachine) MetaMachine.getMachine(world, terminalPos);
        var pending = CausalityTerminalMachine.class.getDeclaredField("pendingOutput");
        var progress = CausalityTerminalMachine.class.getDeclaredField("progress");
        var tick = CausalityTerminalMachine.class.getDeclaredMethod("runTick");
        pending.setAccessible(true);
        progress.setAccessible(true);
        tick.setAccessible(true);
        pending.set(terminal, new ItemStack(Items.DIAMOND));
        progress.setInt(terminal, 42);
        tick.invoke(terminal);
        check(!((ItemStack) pending.get(terminal)).isEmpty() && progress.getInt(terminal) == 42,
                "Loading canceled pending causality output");
        var formed = MultiblockControllerMachine.class.getDeclaredField("isFormed");
        formed.setAccessible(true);
        formed.setBoolean(terminal, true);
        terminal.getMultiblockState().setError(null);
        check(terminal.isRecipeLogicAvailable(), "Saved formed-flag fixture did not reproduce loading state");
        tick.invoke(terminal);
        check(!((ItemStack) pending.get(terminal)).isEmpty() && progress.getInt(terminal) == 42,
                "Saved formed flag canceled work before energy parts were bound");
        formed.setBoolean(terminal, false);
        terminal.onPartUnload();
        tick.invoke(terminal);
        check(!((ItemStack) pending.get(terminal)).isEmpty(), "Unloaded part canceled pending causality output");
        terminal.onStructureInvalid();
        check(((ItemStack) pending.get(terminal)).isEmpty() && progress.getInt(terminal) == 0,
                "Actual invalidation no longer cancels causality work");
        log("causality_load_vs_invalidation=OK");

        var definition = controller.getDefinition();
        var original = definition.getPatternFactory();
        var lock = (ReentrantLock) controller.getPatternLock();
        lock.lock();
        try {
            ((MultiblockMachineDefinitionAccessor) definition).gtlEnhancedcore$setPatternFactory(() -> {
                throw new IllegalStateException("intentional audit predicate failure");
            });
            for (boolean tryLock : new boolean[]{false, true}) {
                boolean thrown = false;
                try {
                    if (tryLock) controller.checkPatternWithTryLock();
                    else controller.checkPatternWithLock();
                } catch (IllegalStateException expected) {
                    thrown = expected.getMessage().equals("intentional audit predicate failure");
                }
                check(thrown, "Fault injection did not reach concrete pattern check");
                check(lock.getHoldCount() == 1, "Concrete pattern check leaked a lock: tryLock=" + tryLock
                        + ", holds=" + lock.getHoldCount());
            }
        } finally {
            ((MultiblockMachineDefinitionAccessor) definition).gtlEnhancedcore$setPatternFactory(original);
            while (lock.isHeldByCurrentThread()) lock.unlock();
        }
        log("concrete_pattern_check_exception_unlock_both_paths=OK");
        checks += SuperBufferNameChecks.run(server);
        log("super_buffer_name_wire_zh_en_manual_and_widget=OK");
        return checks;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
        checks++;
    }

    private static boolean canUse(ServerPlayer player, BlockPos pos) throws Exception {
        var method = Class.forName("com.gtl.enhancedcore.network.MachinePacketAccess")
                .getDeclaredMethod("canUse", ServerPlayer.class, BlockPos.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, player, pos);
    }

    private static void log(String message) {
        GTLEnhancedcore.LOGGER.info("[INTEGRATION_AUDIT] {}", message);
    }
}
