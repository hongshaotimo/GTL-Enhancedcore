package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.item.PatternGeneratorSettings;
import com.gtl.enhancedcore.common.item.PatternGeneratorTransfer;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.PlayerTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** One server-thread call in a fresh audit world; no asynchronous setup is required. */
public final class MachinePacketPermissionChecks {
    private static final BlockPos MACHINE = new BlockPos(738, 96, 2), BARREL = new BlockPos(740, 96, 2);
    private static int checks;
    private MachinePacketPermissionChecks() {}

    public static int run(MinecraftServer server) throws Exception {
        checks = 0;
        check(server.isSameThread(), "Machine packet checks require the server thread");
        var level = server.overworld();
        check(level.getBlockState(MACHINE).isAir() && level.getBlockState(BARREL).isAir(), "Fixture positions must be empty");
        check(!level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(46, 0)), "Fixture chunk must not already be forced");
        var actor = new RecordingPlayer(server);
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        PlayerTeam personal = null;
        PartyTeam party = null;
        ClaimedChunk claim = null;
        Method access = Class.forName("com.gtl.enhancedcore.network.MachinePacketAccess")
                .getDeclaredMethod("canUse", ServerPlayer.class, BlockPos.class);
        access.setAccessible(true);
        level.setChunkForced(46, 0, true);
        ClaimReplacementChecks.onlinePlayers(server).put(actor.getUUID(), actor);
        try {
            personal = ClaimReplacementChecks.personalTeam(teams, actor);
            party = new PartyTeam(teams, UUID.randomUUID());
            teams.getTeamMap().put(party.getId(), party);
            party.addMember(UUID.randomUUID(), TeamRank.OWNER);
            party.addMember(actor.getUUID(), TeamRank.MEMBER);
            party.setProperty(FTBChunksProperties.BLOCK_INTERACT_MODE, PrivacyMode.PRIVATE);
            personal.setEffectiveTeam(party);
            var claims = (ChunkTeamDataImpl) FTBChunksAPI.api().getManager().getOrCreateData(party);
            var quota = ChunkTeamDataImpl.class.getDeclaredField("maxClaimChunks");
            quota.setAccessible(true); quota.setInt(claims, 10);
            var claimed = claims.claim(server.createCommandSourceStack(), new ChunkDimPos(level.dimension(), 46, 0), false);
            check(claimed instanceof ClaimedChunk, "Cannot create real FTB permission claim");
            claim = (ClaimedChunk) claimed;
            actor.setPos(MACHINE.getX() + .5, MACHINE.getY(), MACHINE.getZ() + .5);
            for (String id : List.of("circuit_encoder_hatch", "claim_replacement_terminal")) {
                var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtl_enhancedcore", id));
                check(block != null && level.setBlock(MACHINE, block.defaultBlockState(), 3), "Cannot place " + id);
                var machine = MetaMachine.getMachine(level, MACHINE);
                check(machine instanceof IUIMachine, "Fixture machine has no native UI");
                actor.containerMenu = new ModularUIContainer(((IUIMachine) machine).createUI(actor), 51);
                check((boolean) access.invoke(null, actor, MACHINE), "Authorized member UI was rejected: " + id);
                party.removeMember(actor.getUUID()); personal.setEffectiveTeam(personal);
                check(level.mayInteract(actor, MACHINE) && FTBChunksAPI.api().getManager()
                        .shouldPreventInteraction(actor, InteractionHand.MAIN_HAND, MACHINE, Protection.INTERACT_BLOCK, null),
                        "Revoked fixture must pass vanilla permissions and fail real FTB permissions");
                check(!(boolean) access.invoke(null, actor, MACHINE), "Revoked open machine UI was accepted: " + id);
                FTBChunksAPI.api().getManager().setBypassProtection(actor.getUUID(), true);
                check((boolean) access.invoke(null, actor, MACHINE), "FTB bypass was rejected: " + id);
                FTBChunksAPI.api().getManager().setBypassProtection(actor.getUUID(), false);
                party.setProperty(FTBChunksProperties.BLOCK_INTERACT_MODE, PrivacyMode.ALLIES);
                party.addAlly(server.createCommandSourceStack(), List.of(actor.getGameProfile()));
                check((boolean) access.invoke(null, actor, MACHINE), "Native FTB ally was rejected: " + id);
                party.removeAlly(server.createCommandSourceStack(), List.of(actor.getGameProfile()));
                party.setProperty(FTBChunksProperties.BLOCK_INTERACT_MODE, PrivacyMode.PRIVATE);
                party.addMember(actor.getUUID(), TeamRank.MEMBER); personal.setEffectiveTeam(party);
                check((boolean) access.invoke(null, actor, MACHINE), "Restored member UI was rejected: " + id);
                actor.containerMenu = actor.inventoryMenu;
                level.setBlock(MACHINE, Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
            var barrel = (BarrelBlockEntity) level.getBlockEntity(BARREL);
            var settings = new PatternGeneratorSettings();
            settings.generatorId = UUID.randomUUID(); settings.transferArmed = true;
            var tool = new ItemStack(GTLEnhancedcoreItems.PATTERN_GENERATOR.get());
            tool.getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write());
            actor.setItemInHand(InteractionHand.MAIN_HAND, tool);
            ItemStack source = new ItemStack(Items.PAPER, 17);
            PatternGeneratorTransfer.mark(source, settings);
            actor.getInventory().setItem(1, source);
            var context = new UseOnContext(actor, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(BARREL), Direction.UP, BARREL, false));
            PatternGeneratorTransfer.begin(actor, context);
            check(barrel.getItem(0).getCount() == 8 && source.getCount() == 9, "Authorized first transfer batch failed");
            party.removeMember(actor.getUUID()); personal.setEffectiveTeam(personal);
            // Advance this session's guard only, leaving the world's clock untouched.
            var transfersField = PatternGeneratorTransfer.class.getDeclaredField("TRANSFERS"); transfersField.setAccessible(true);
            @SuppressWarnings("unchecked") var transfers = (Map<ServerPlayer, ?>) transfersField.get(null);
            Object transfer = transfers.get(actor);
            check(transfer != null, "Transfer did not retain its unfinished batch");
            var lastTick = transfer.getClass().getDeclaredField("lastTick"); lastTick.setAccessible(true); lastTick.setLong(transfer, -1);
            PatternGeneratorTransfer.tick(actor, tool);
            check(barrel.getItem(0).getCount() == 8 && source.getCount() == 9 && !transfers.containsKey(actor),
                    "Revoked continuous transfer changed inventory or remained active");
            PatternGeneratorTransfer.begin(actor, context);
            check(barrel.getItem(0).getCount() == 8 && source.getCount() == 9, "Revoked transfer begin was accepted");
            party.addMember(actor.getUUID(), TeamRank.MEMBER); personal.setEffectiveTeam(party);
            PatternGeneratorTransfer.begin(actor, context);
            check(barrel.getItem(0).getCount() == 16 && source.getCount() == 1, "Restored transfer did not resume");
            PatternGeneratorTransfer.cancel(actor);
            party.removeMember(actor.getUUID()); personal.setEffectiveTeam(personal);
            FTBChunksAPI.api().getManager().setBypassProtection(actor.getUUID(), true);
            PatternGeneratorTransfer.begin(actor, context);
            check(barrel.getItem(0).getCount() == 17 && source.isEmpty(), "Native FTB bypass did not finish transfer");
            return checks;
        } finally {
            PatternGeneratorTransfer.cancel(actor);
            FTBChunksAPI.api().getManager().setBypassProtection(actor.getUUID(), false);
            actor.containerMenu = actor.inventoryMenu;
            actor.getInventory().clearContent();
            if (level.getBlockEntity(BARREL) instanceof BarrelBlockEntity barrel) barrel.clearContent();
            level.setBlock(MACHINE, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(BARREL, Blocks.AIR.defaultBlockState(), 3);
            if (claim != null) claim.unclaim(server.createCommandSourceStack(), false);
            ClaimReplacementChecks.onlinePlayers(server).remove(actor.getUUID());
            if (personal != null) personal.setEffectiveTeam(personal);
            if (party != null) teams.getTeamMap().remove(party.getId());
            level.setChunkForced(46, 0, false);
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); checks++; }
    private static final class RecordingPlayer extends ServerPlayer {
        RecordingPlayer(MinecraftServer server) {
            super(server, server.overworld(), new GameProfile(UUID.randomUUID(), "packet_permission"));
            connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), this);
            getAbilities().mayBuild = true;
        }
        @Override public void sendSystemMessage(Component message) {}
        @Override public void displayClientMessage(Component message, boolean overlay) {}
    }
}
