package com.gtl.enhancedcore.common.world.personalplot;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.item.PlotWorldControllerItem;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Paginated owner management. Targets and destructive confirmations are authorized on the server. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class PersonalPlotManagement {
    public static final int PAGE_SIZE = 4;
    private static final String PREFIX = "gtl_enhancedcore.personal_plot.";
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();
    private record Confirmation(UUID owner, UUID token, PlotSettings settings, long expires) {}
    private static final class Session {
        final Map<UUID, Confirmation> confirmations = new HashMap<>();
        final Map<UUID, Long> requests = new HashMap<>();
    }
    private PersonalPlotManagement() {}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
    public static void invalidate(MinecraftServer server, UUID owner) {
        Session session = SESSIONS.get(server);
        if (session != null) session.confirmations.values().removeIf(c -> c.owner.equals(owner));
    }
    public static PersonalPlotNetwork.ManagementSnapshot handle(ServerPlayer actor, PersonalPlotNetwork.ManagementAction action,
                                                               UUID owner, UUID token, int page) {
        if (!heldController(actor)) return empty(PREFIX + "controller_required");
        boolean all = actor.server.isDedicatedServer() && actor.hasPermissions(2);
        boolean own = actor.server.isDedicatedServer() || actor.server.isSingleplayerOwner(actor.getGameProfile());
        if (!own) return empty(PREFIX + "management_denied");
        Session session = SESSIONS.computeIfAbsent(actor.server, ignored -> new Session());
        long now = actor.server.getTickCount();
        session.confirmations.values().removeIf(c -> now > c.expires);
        Long last = session.requests.put(actor.getUUID(), now);
        if (last != null && now - last < 5) return snapshot(actor, all, page, null, null, PREFIX + "cooldown");
        try {
            var data = PersonalPlotWorlds.data(actor.server);
            if (action == PersonalPlotNetwork.ManagementAction.LIST) return snapshot(actor, all, page, null, null, "");
            if (owner == null || !owner.equals(actor.getUUID()) && !all)
                return snapshot(actor, all, page, null, null, PREFIX + "management_denied");
            if (!knownOwners(actor, all, data).containsKey(owner))
                return snapshot(actor, all, page, null, null, PREFIX + "invalid_target");
            String notice;
            switch (action) {
                case PREPARE_DELETE -> {
                    if (!data.worlds().containsKey(owner)) notice = PREFIX + "not_created";
                    else if (PersonalPlotWorlds.ownerState(actor.server, owner) == PersonalPlotWorlds.State.DELETING)
                        notice = PREFIX + "deleting";
                    else {
                        UUID challenge = UUID.randomUUID();
                        session.confirmations.put(actor.getUUID(), new Confirmation(owner, challenge, data.worlds().get(owner), now + 1200));
                        return snapshot(actor, all, page, owner, challenge, "");
                    }
                }
                case CONFIRM_DELETE -> {
                    Confirmation confirmation = session.confirmations.remove(actor.getUUID());
                    if (confirmation == null || !confirmation.owner.equals(owner) || !Objects.equals(confirmation.token, token)
                            || now > confirmation.expires || !Objects.equals(data.worlds().get(owner), confirmation.settings))
                        notice = PREFIX + "confirmation_expired";
                    else notice = PersonalPlotWorlds.deleteFor(actor, owner);
                }
                case CREATE_FOR -> {
                    session.confirmations.remove(actor.getUUID());
                    notice = PersonalPlotWorlds.createFor(actor, owner);
                }
                default -> throw new IllegalArgumentException("Unknown personal plot management action");
            }
            return snapshot(actor, all, page, null, null, notice);
        } catch (IOException | RuntimeException failure) {
            GTLEnhancedcore.LOGGER.error("Personal plot management {} failed for {}", action, actor.getUUID(), failure);
            return empty(PREFIX + "storage_error");
        }
    }
    private static boolean heldController(ServerPlayer actor) {
        return actor.getMainHandItem().getItem() instanceof PlotWorldControllerItem
                || actor.getOffhandItem().getItem() instanceof PlotWorldControllerItem;
    }
    private static PersonalPlotNetwork.ManagementSnapshot empty(String notice) {
        return new PersonalPlotNetwork.ManagementSnapshot(false, List.of(), 0, 1, null, null, notice);
    }
    private static Map<UUID, String> knownOwners(ServerPlayer actor, boolean all, PersonalPlotStore.Data data) throws IOException {
        Map<UUID, String> owners = new LinkedHashMap<>();
        owners.put(actor.getUUID(), actor.getGameProfile().getName());
        if (!all) return owners;
        for (UUID owner : data.worlds().keySet()) owners.put(owner, name(actor.server, owner));
        for (ServerPlayer player : actor.server.getPlayerList().getPlayers()) owners.put(player.getUUID(), player.getGameProfile().getName());
        Path players = actor.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (Files.isDirectory(players)) try (var files = Files.newDirectoryStream(players, "*.dat")) {
            for (Path path : files) {
                String file = path.getFileName().toString();
                if (!file.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.dat")) continue;
                UUID owner = UUID.fromString(file.substring(0, file.length() - 4));
                owners.putIfAbsent(owner, name(actor.server, owner));
            }
        }
        return owners;
    }
    private static String name(MinecraftServer server, UUID owner) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) return player.getGameProfile().getName();
        var cache = server.getProfileCache();
        return cache == null ? owner.toString() : cache.get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(owner.toString());
    }
    private static PersonalPlotNetwork.ManagementSnapshot snapshot(ServerPlayer actor, boolean all, int page,
                                                                  UUID deleteOwner, UUID deleteToken, String notice) {
        try {
            var data = PersonalPlotWorlds.data(actor.server);
            var owners = new ArrayList<>(knownOwners(actor, all, data).entrySet());
            owners.sort(Comparator.<Map.Entry<UUID, String>, String>comparing(Map.Entry::getValue, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.getKey().toString()));
            int pages = Math.max(1, (owners.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int selected = Math.max(0, Math.min(page, pages - 1));
            List<PersonalPlotNetwork.Entry> entries = new ArrayList<>();
            for (int i = selected * PAGE_SIZE; i < Math.min(owners.size(), (selected + 1) * PAGE_SIZE); i++) {
                var entry = owners.get(i); UUID owner = entry.getKey();
                var state = PersonalPlotWorlds.ownerState(actor.server, owner);
                boolean hasWorld = data.worlds().containsKey(owner), deleting = state == PersonalPlotWorlds.State.DELETING;
                boolean canCreate = actor.server.isDedicatedServer() && !hasWorld && !deleting
                        && PersonalPlotWorlds.snapshot(actor, "").templateReady() && data.worlds().size() < PersonalPlotStore.MAX_WORLDS;
                String shownName = entry.getValue();
                if (shownName.length() > 64) shownName = shownName.substring(0, 64);
                entries.add(new PersonalPlotNetwork.Entry(owner, shownName, state, canCreate, hasWorld && !deleting));
            }
            return new PersonalPlotNetwork.ManagementSnapshot(all, entries, selected, pages, deleteOwner, deleteToken, notice);
        } catch (IOException failure) { return empty(PREFIX + "storage_error"); }
    }
}
