package com.gtl.enhancedcore.common.cloud;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import appeng.api.stacks.AEKey;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** 山海：partial shared indexes must never hide an unindexed matching recipe. */
public final class CloudPerformanceRegression {
    public static void main(String[] args) throws Exception {
        int checks = run();
        if (Arrays.asList(args).contains("--related")) {
            for (String name : List.of("CloudLedgerRegression", "CloudStoreNbtRegression", "CloudEndpointRegression",
                    "CloudMeTargetRegression", "CloudOrderCapacityRegression", "CloudPatternAccessRegression", "CloudUploadSequenceRegression")) {
                var method = Class.forName("com.gtl.enhancedcore." + name).getDeclaredMethod("run"); method.setAccessible(true);
                int count = (int)method.invoke(null); checks += count;
                System.out.println(name + ": " + count);
            }
            checks += com.gtl.enhancedcore.common.cloud.transfer.CloudTransferRegression.run();
        }
        System.out.println("Cloud performance and related regression checks: " + checks);
    }
    @SuppressWarnings("unchecked")
    public static int run() throws Exception {
        Class<?> type;
        try { type = Class.forName(CloudPatternMatching.class.getName() + "$RecipeIndex"); }
        catch (ClassNotFoundException original) { type = Class.forName(CloudPatternAllocator.class.getName() + "$RecipeIndex"); }
        var constructor = type.getDeclaredConstructor(List.class); constructor.setAccessible(true);
        var first = recipe("first"); var second = recipe("second");
        var all = List.of(first, second); Object index = constructor.newInstance(all);
        var outputField = type.getDeclaredField("byOutput"); outputField.setAccessible(true);
        var outputs = (Map<AEKey, List<GTRecipe>>)outputField.get(index);
        // A null sentinel exercises bucket selection without bootstrapping Minecraft registries.
        outputs.put(null, List.of(first));
        Map<AEKey, Long> requested = new HashMap<>(); requested.put(null, 1L);
        var candidateMethod = type.getDeclaredMethod("candidates", CloudPatternMatching.Probe.class);
        candidateMethod.setAccessible(true);
        var probe = new CloudPatternMatching.Probe(Map.of(), Map.of(), requested);
        check(candidateMethod.invoke(index, probe) == all, "Unfinished index omitted unindexed recipes");
        var step = type.getDeclaredMethod("buildStep"); step.setAccessible(true);
        check((boolean)step.invoke(index), "First bounded build step missing");
        check(candidateMethod.invoke(index, probe) == all, "Half-built index published an incomplete bucket");
        check((boolean)step.invoke(index) && !(boolean)step.invoke(index), "Index must finish after exactly two recipes");
        check(candidateMethod.invoke(index, probe).equals(List.of(first)), "Finished index failed to narrow candidates");
        outputs.clear();
        check(((List<?>)candidateMethod.invoke(index, probe)).isEmpty(), "Unknown output must have no candidates");
        check(candidateMethod.invoke(index, new CloudPatternMatching.Probe(Map.of(), Map.of(), Map.of())) == all,
                "Empty output query changed full candidate fallback");
        var cacheField = CloudPatternMatching.class.getDeclaredField("INDEXES"); cacheField.setAccessible(true);
        var cache = (Map<Object, Object>)cacheField.get(null);
        cache.put(null, new HashMap<>());
        var clear = CloudPatternMatching.class.getDeclaredMethod("clear", net.minecraft.server.MinecraftServer.class);
        clear.setAccessible(true); clear.invoke(null, new Object[]{null});
        check(!cache.containsKey(null), "Recipe reload/stop invalidation retained an index");
        return 8 + catalogueBudget();
    }
    @SuppressWarnings("unchecked")
    private static int catalogueBudget() throws Exception {
        var service = CloudLibraryService.class;
        var libraryType = com.gtl.enhancedcore.common.cloud.store.CloudPatternStore.Library.class;
        var libraryConstructor = libraryType.getDeclaredConstructors()[0]; libraryConstructor.setAccessible(true);
        var library = libraryConstructor.newInstance(null, new UUID(1, 1), new UUID(2, 2));
        var catalogType = Class.forName(service.getName() + "$Catalog");
        var catalogConstructor = catalogType.getDeclaredConstructor(); catalogConstructor.setAccessible(true);
        Object catalog = catalogConstructor.newInstance();
        var buildType = Class.forName(service.getName() + "$CatalogBuild");
        var buildConstructor = buildType.getDeclaredConstructors()[0]; buildConstructor.setAccessible(true);
        Object build = buildConstructor.newInstance(library, null, catalog);
        var rowsField = buildType.getDeclaredField("rows"); rowsField.setAccessible(true);
        var rows = (Map<UUID, Object>)rowsField.get(build);
        var sideType = Class.forName(service.getName() + "$TerminalSide");
        var side = sideType.getDeclaredConstructors()[0]; side.setAccessible(true);
        var empty = side.newInstance("", new int[0], new int[0]);
        var rowType = Class.forName(service.getName() + "$CatalogRow");
        var row = rowType.getDeclaredConstructors()[0]; row.setAccessible(true);
        for (int i = 0; i < 2664; i++) {
            var id = new UUID(0, i + 1);
            rows.put(id, row.newInstance(id, "", Set.of("test"), new int[]{7}, new int[]{9}, false, true, empty, empty));
        }
        var step = buildType.getDeclaredMethod("step"); step.setAccessible(true);
        var visible = catalogType.getDeclaredField("visible"); visible.setAccessible(true);
        check((boolean)step.invoke(build), "Large catalogue completed in one unbounded step");
        check(((List<?>)visible.get(catalog)).isEmpty(), "Partial catalogue was published as complete");
        int steps = 1;
        while ((boolean)step.invoke(build)) { if (++steps > 2700) throw new AssertionError("Catalogue failed to finish"); }
        check(steps >= 2664 && ((List<?>)visible.get(catalog)).size() == 2664, "Budgeted catalogue lost rows");
        var itemBits = catalogType.getDeclaredField("searchItems"); itemBits.setAccessible(true);
        var fluidBits = catalogType.getDeclaredField("searchFluids"); fluidBits.setAccessible(true);
        check(BitSet.valueOf((long[])itemBits.get(catalog)).get(7) && BitSet.valueOf((long[])fluidBits.get(catalog)).get(9),
                "Atomic catalogue publication lost search scopes");
        var version = libraryType.getDeclaredField("catalogRevision"); version.setAccessible(true);
        var revision = catalogType.getDeclaredField("revision"); revision.setAccessible(true);
        Object stale = buildConstructor.newInstance(library, null, catalog);
        version.setLong(library, 1);
        check(!(boolean)step.invoke(stale) && revision.getLong(catalog) == 0, "Stale build published a different authority revision");
        var pending = service.getDeclaredMethod("pendingPage", libraryType, catalogType, int.class); pending.setAccessible(true);
        var response = (CompoundTag)pending.invoke(null, library, catalog, 5);
        check(response.getBoolean("indexing") && response.getInt("page") == 5 && response.getInt("pages") >= 6,
                "Indexing reset the viewer's requested page");
        check(response.getList("types", net.minecraft.nbt.Tag.TAG_STRING).size() == 1
                && BitSet.valueOf(response.getLongArray("searchItems")).get(7)
                && BitSet.valueOf(response.getLongArray("searchFluids")).get(9), "Indexing discarded the published search scope");
        var failed = catalogType.getDeclaredField("failureRevision"); failed.setAccessible(true); failed.setLong(catalog, 1);
        try { pending.invoke(null, library, catalog, 5); throw new AssertionError("Indexing failure remained a loading screen"); }
        catch (InvocationTargetException expected) { check(expected.getCause() instanceof IllegalStateException, "Failure diagnostic was lost"); }
        return 8;
    }
    private static GTRecipe recipe(String id) {
        return new GTRecipe(null, new ResourceLocation("gtl_enhancedcore", "perf_" + id),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), List.of(), new CompoundTag(), 20, false);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
