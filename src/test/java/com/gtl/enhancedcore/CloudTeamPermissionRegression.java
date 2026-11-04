package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.CloudLibraryService;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Exercises the real library ACL against changing UUID-based FTB membership, without a player world. */
public final class CloudTeamPermissionRegression {
    private CloudTeamPermissionRegression() {}
    public static void main(String[] args) { System.out.println("Cloud team permission assertions: " + run()); }

    @SuppressWarnings("unchecked")
    public static int run() {
        var originalApi = FTBTeamsAPI.api();
        try {
            var apiField = FTBTeamsAPI.class.getDeclaredField("instance"); apiField.setAccessible(true);
            UUID owner = new UUID(1, 1), member = new UUID(2, 2), captain = new UUID(3, 3);
            UUID outsider = new UUID(4, 4), unknown = new UUID(5, 5), partyId = new UUID(6, 6);
            Map<UUID, Team> memberships = new HashMap<>();
            Team party = (Team)Proxy.newProxyInstance(Team.class.getClassLoader(), new Class<?>[]{Team.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getId")) return partyId;
                        throw new AssertionError("Authorization must not ask for rank, online members or ownership: " + method);
                    });
            memberships.put(owner, party); memberships.put(member, party); memberships.put(captain, party);
            TeamManager manager = (TeamManager)Proxy.newProxyInstance(TeamManager.class.getClassLoader(), new Class<?>[]{TeamManager.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getTeamForPlayerID")) return Optional.ofNullable(memberships.get(args[0]));
                        throw new AssertionError("Authorization must use persisted player IDs, including offline owners: " + method);
                    });
            FTBTeamsAPI.API api = (FTBTeamsAPI.API)Proxy.newProxyInstance(FTBTeamsAPI.API.class.getClassLoader(),
                    new Class<?>[]{FTBTeamsAPI.API.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getManager")) return manager;
                        throw new AssertionError("Unexpected FTB API dependency: " + method);
                    });
            apiField.set(null, api);
            var constructor = CloudPatternStore.Library.class.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            var library = (CloudPatternStore.Library)constructor.newInstance(null, UUID.randomUUID(), owner);
            int checks = 0;
            check(library.authorized(owner), "Library owner retains access"); checks++;
            check(library.authorized(member), "Ordinary teammate needs no explicit delegate grant"); checks++;
            check(library.authorized(captain), "Captain and ordinary member have equal library access"); checks++;
            check(!library.authorized(outsider) && !library.authorized(unknown), "Other or unknown teams are denied"); checks++;
            check(CloudLibraryService.sameTeam(member, owner), "Team equality is symmetric"); checks++;
            check(!library.authorized(null) && !CloudLibraryService.sameTeam(null, member), "Missing identity never authorizes"); checks++;
            var delegatesField = CloudPatternStore.Library.class.getDeclaredField("delegates"); delegatesField.setAccessible(true);
            var delegates = (Set<UUID>)delegatesField.get(library); delegates.add(outsider);
            check(!library.authorized(outsider), "Stale explicit delegate cannot authorize an outsider"); checks++;
            delegates.add(member); memberships.remove(member);
            check(!library.authorized(member), "Leaving immediately revokes access even with a saved delegate"); checks++;
            memberships.put(member, party); delegates.clear();
            check(library.authorized(member), "Rejoining restores equal access without changing saved ACL"); checks++;
            memberships.remove(owner);
            check(!library.authorized(member) && library.authorized(owner), "Unknown owner team fails closed while owner identity remains valid"); checks++;
            memberships.put(owner, party);
            check(library.authorized(member), "Restored offline owner membership needs no online-player lookup"); checks++;
            memberships.put(outsider, party);
            check(library.authorized(outsider), "New teammates inherit permissions dynamically"); checks++;
            memberships.remove(outsider);
            check(!library.authorized(outsider), "Departing newly joined member is denied without cache delay"); checks++;
            return checks;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cloud team permission fixture failed", failure);
        } finally {
            try {
                var field = FTBTeamsAPI.class.getDeclaredField("instance"); field.setAccessible(true); field.set(null, originalApi);
            } catch (ReflectiveOperationException failure) { throw new AssertionError("FTB API restore failed", failure); }
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
