package com.gtl.enhancedcore.audit;

import appeng.api.networking.GridHelper;
import appeng.core.definitions.AEBlocks;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.assembly.AssemblyMirrorNetwork;
import com.gtl.enhancedcore.common.assembly.AssemblyMirrorService;
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
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;

/** Real FTB permissions and real AE sources; prepare, wait 20 ticks, connect, wait 20 ticks, run, cleanup. */
public final class AssemblyMirrorPermissionChecks {
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private static final BlockPos ANCHOR = new BlockPos(641, 96, 0), SOURCE = new BlockPos(640, 96, 0),
            PUBLIC_SOURCE = new BlockPos(672, 96, 0), MIRROR = new BlockPos(658, 96, 3),
            PUBLIC_MIRROR = new BlockPos(676, 96, 3), OLD_SOURCE_MIRROR = new BlockPos(677, 96, 3);
    private static final BlockPos[] POSITIONS = {ANCHOR, SOURCE, PUBLIC_SOURCE, MIRROR, PUBLIC_MIRROR, OLD_SOURCE_MIRROR};
    private static final List<ClaimedChunk> CLAIMS = new ArrayList<>();
    private static MinecraftServer server;
    private static RecordingPlayer actor;
    private static PlayerTeam personal;
    private static PartyTeam party;
    private static int checks;

    private AssemblyMirrorPermissionChecks() {}

    public static void prepare(MinecraftServer value) throws Exception {
        server = value; checks = 0;
        check(server.isSameThread(), "Mirror fixture requires the server thread");
        var level = server.overworld();
        for (BlockPos pos : POSITIONS) check(level.getBlockState(pos).isAir(), "Mirror fixture requires empty owned positions");
        for (int chunk = 40; chunk <= 42; chunk++) level.setChunkForced(chunk, 0, true);
        actor = new RecordingPlayer(server);
        ClaimReplacementChecks.onlinePlayers(server).put(actor.getUUID(), actor);
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
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
        for (int chunk = 40; chunk <= 41; chunk++) {
            var result = claims.claim(server.createCommandSourceStack(), new ChunkDimPos(level.dimension(), chunk, 0), false);
            check(result instanceof ClaimedChunk, "Cannot create real mirror permission claim");
            CLAIMS.add((ClaimedChunk) result);
        }
        level.setBlock(ANCHOR, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        place(SOURCE, "me_super_pattern_buffer"); place(PUBLIC_SOURCE, "me_super_pattern_buffer");
        place(MIRROR, "me_super_pattern_buffer_proxy"); place(PUBLIC_MIRROR, "me_super_pattern_buffer_proxy");
        place(OLD_SOURCE_MIRROR, "me_super_pattern_buffer_proxy");
        actor.setItemInHand(HAND, new ItemStack(ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("gtl_enhancedcore", "assembly_manager"))));
    }

    public static void connect(MinecraftServer value) {
        check(value == server && server.isSameThread(), "Mirror connection requires its prepared server");
        var anchor = GridHelper.getExposedNode(server.overworld(), ANCHOR, Direction.UP);
        check(anchor != null && source(SOURCE).getMainNode().getNode() != null
                && source(PUBLIC_SOURCE).getMainNode().getNode() != null, "Real AE nodes must finish loading");
        // SOURCE is adjacent to the energy cell and already joins its grid through AE's normal placement path.
        GridHelper.createConnection(anchor, source(PUBLIC_SOURCE).getMainNode().getNode());
    }

    public static int run(MinecraftServer value) {
        check(value == server && server.isSameThread(), "Mirror checks require their prepared server");
        check(source(SOURCE).getMainNode().isActive() && source(PUBLIC_SOURCE).getMainNode().isActive(), "Mirror sources must be powered");
        move(ANCHOR);
        result(AssemblyMirrorService.bindNetwork(actor, HAND, ANCHOR, Direction.UP), "network_selected");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.SELECT_SOURCE, PUBLIC_SOURCE), "source_selected");
        move(MIRROR); result(AssemblyMirrorService.bindMirror(actor, HAND, MIRROR, false), "bound");
        move(PUBLIC_MIRROR); result(AssemblyMirrorService.bindMirror(actor, HAND, PUBLIC_MIRROR, false), "bound");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.SELECT_SOURCE, SOURCE), "source_selected");
        move(OLD_SOURCE_MIRROR); result(AssemblyMirrorService.bindMirror(actor, HAND, OLD_SOURCE_MIRROR, false), "bound");
        party.removeMember(actor.getUUID()); personal.setEffectiveTeam(personal);
        check(actor.serverLevel().mayInteract(actor, MIRROR)
                && FTBChunksAPI.api().getManager().shouldPreventInteraction(actor, HAND, MIRROR, Protection.INTERACT_BLOCK, null),
                "Revoked actor must pass vanilla permissions but fail actual FTB interaction permission");
        check(actor.distanceToSqr(MIRROR.getCenter()) > 64, "GUI target must remain beyond direct interaction range");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.UNBIND, MIRROR), "blocked");
        check(PUBLIC_SOURCE.equals(proxy(MIRROR).getBufferPos()), "Revoked target permission allowed unbinding a public source");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.REBIND, MIRROR), "blocked");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.REBIND, PUBLIC_MIRROR), "blocked");
        check(PUBLIC_SOURCE.equals(proxy(PUBLIC_MIRROR).getBufferPos()), "Revoked new-source permission allowed rebinding a public mirror");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.SELECT_SOURCE, SOURCE), "blocked");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.SELECT_SOURCE, PUBLIC_SOURCE), "source_selected");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.REBIND, OLD_SOURCE_MIRROR), "blocked");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.UNBIND, OLD_SOURCE_MIRROR), "blocked");
        check(SOURCE.equals(proxy(OLD_SOURCE_MIRROR).getBufferPos()), "Revoked old-source permission allowed removing its public mirror");
        FTBChunksAPI.api().getManager().setBypassProtection(actor.getUUID(), true);
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.REBIND, OLD_SOURCE_MIRROR), "bound");
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.UNBIND, MIRROR), "unbound");
        check(proxy(MIRROR).getBufferPos() == null, "Native FTB bypass must still authorize remote unbinding");
        FTBChunksAPI.api().getManager().setBypassProtection(actor.getUUID(), false);
        party.addMember(actor.getUUID(), TeamRank.MEMBER); personal.setEffectiveTeam(party);
        move(ANCHOR);
        result(AssemblyMirrorService.action(actor, HAND, AssemblyMirrorNetwork.UNBIND, OLD_SOURCE_MIRROR), "unbound");
        check(proxy(OLD_SOURCE_MIRROR).getBufferPos() == null, "Authorized member must retain remote mirror management");
        return checks;
    }

    public static void cleanup() throws Exception {
        if (server == null) return;
        if (actor != null) {
            FTBChunksAPI.api().getManager().setBypassProtection(actor.getUUID(), false);
            ClaimReplacementChecks.onlinePlayers(server).remove(actor.getUUID());
        }
        for (ClaimedChunk claim : CLAIMS) claim.unclaim(server.createCommandSourceStack(), false);
        CLAIMS.clear();
        for (BlockPos pos : POSITIONS) server.overworld().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        for (int chunk = 40; chunk <= 42; chunk++) server.overworld().setChunkForced(chunk, 0, false);
        server = null;
    }

    private static void move(BlockPos pos) { actor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); }
    private static void place(BlockPos pos, String id) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtladditions", id));
        check(definition != null && server.overworld().setBlock(pos, definition.defaultBlockState(), 3), "Cannot place real mirror fixture " + id);
    }
    private static MEPatternBufferPartMachine source(BlockPos pos) {
        return (MEPatternBufferPartMachine) MetaMachine.getMachine(server.overworld(), pos);
    }
    private static MEPatternBufferProxyPartMachine proxy(BlockPos pos) {
        return (MEPatternBufferProxyPartMachine) MetaMachine.getMachine(server.overworld(), pos);
    }
    private static void result(Component message, String key) {
        check(Component.Serializer.toJson(message).contains("gtl_enhancedcore.assembly.mirror." + key), "Unexpected mirror result: " + message);
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); checks++; }

    private static final class RecordingPlayer extends ServerPlayer {
        RecordingPlayer(MinecraftServer server) {
            super(server, server.overworld(), new GameProfile(UUID.randomUUID(), "mirror_permission"));
            connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), this);
            getAbilities().mayBuild = true;
        }
        @Override public void sendSystemMessage(Component message) {}
        @Override public void displayClientMessage(Component message, boolean overlay) {}
    }
}
