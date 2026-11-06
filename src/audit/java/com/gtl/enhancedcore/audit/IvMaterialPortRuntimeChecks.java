package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferRegistry;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.mixin.gtm.api.machine.IMultiblockStateInvoker;

/** Executes the final registered production material rules in Forge without opening any world. */
@Mod("enhancedcore_audit")
public final class IvMaterialPortRuntimeChecks {
    private static final String FACTORY = "gtl_enhancedcore:universal_joint_factory";
    private static final String HYDROTHERMAL = "gtl_enhancedcore:hydrothermal_leaching_complex";
    private static final List<String> OWN_IV = List.of("plasma_machine_tool", "hadron_catalytic_refinery",
            "quantum_mass_spectrum_array", "superconducting_fusion_assembler");
    private final List<String> checks = new ArrayList<>();
    private final List<Map<String, Object>> machines = new ArrayList<>();
    private final Map<String, String> ordinaryIds = new LinkedHashMap<>();
    private final int expectedJointLimit = Integer.getInteger("gtl.enhancedcore.ivMaterialPortsExpectedMax", 30);
    private List<List<Port>> finalLayouts = List.of();
    private boolean done;
    private int scenarios;

    public IvMaterialPortRuntimeChecks() {
        if (Boolean.getBoolean("gtl.enhancedcore.ivMaterialPortsAudit"))
            MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || minecraft.screen == null) return;
        done = true;
        var report = new LinkedHashMap<String, Object>();
        Path directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            Path root = Path.of(System.getProperty("gtl.enhancedcore.auditRoot")).toRealPath();
            directory = directory.toRealPath();
            check(directory.startsWith(root) && !directory.equals(root)
                    && Files.isRegularFile(directory.resolve("iv-material-ports.marker"))
                    && minecraft.level == null && minecraft.player == null && minecraft.getSingleplayerServer() == null,
                    "Explicitly marked isolated startup screen; no player world opened");
            run();
            report.put("status", "PASS");
        } catch (Throwable failure) {
            failure.printStackTrace();
            report.put("status", "FAIL");
            report.put("error", failure.toString());
        }
        report.put("scope", "Both original material SimplePredicate identities and complete final production TraceabilityPredicates "
                + "at distinct actual pattern grid positions, including both factory X/Q zones; real registered hatch block states, "
                + "transformed TraceabilityPredicate.test and MultiblockState clean/update; "
                + "no complete geometry formation, machine entity lifecycle, recipes or player world");
        report.put("checks", checks);
        report.put("scenarioCount", scenarios);
        report.put("expectedJointLimit", expectedJointLimit);
        report.put("machineCount", machines.size());
        report.put("ordinaryMaterialBlocks", ordinaryIds);
        report.put("machines", machines);
        try {
            Files.writeString(directory.resolve("iv-material-ports-checks.json"),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report),
                    StandardCharsets.UTF_8);
        } catch (Exception failure) { failure.printStackTrace(); }
        minecraft.stop();
    }

    private void run() throws Exception {
        check(expectedJointLimit > 0 && expectedJointLimit < 142,
                "Explicit expected joint-factory endpoint limit fits its real 142-position panel");
        Block superBuffer = block("gtladditions:me_super_pattern_buffer");
        check(IvBufferRegistry.contains(ForgeRegistries.BLOCKS.getKey(superBuffer)),
                "Actual ADD super buffer is a registered isolation endpoint");
        List<Block> ordinary = new ArrayList<>();
        for (var ability : List.of(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS,
                PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS)) {
            Block selected = ability.getAllBlocks().stream()
                    .filter(IvNativeHatches::materials)
                    .filter(candidate -> !IvBufferRegistry.contains(ForgeRegistries.BLOCKS.getKey(candidate)))
                    .sorted(java.util.Comparator.comparing(candidate -> ForgeRegistries.BLOCKS.getKey(candidate).toString()))
                    .findFirst().orElseThrow();
            ordinary.add(selected);
            ordinaryIds.put(ability.toString(), ForgeRegistries.BLOCKS.getKey(selected).toString());
        }
        List<String> targets = new ArrayList<>();
        targets.add(FACTORY);
        OWN_IV.forEach(id -> targets.add("gtl_enhancedcore:" + id));
        targets.add(HYDROTHERMAL);
        var controllersField = IvBuffers.class.getDeclaredField("CONTROLLERS");
        controllersField.setAccessible(true);
        Object controllers = controllersField.get(null);
        check(controllers instanceof Set<?> ownControllers && ownControllers.equals(Set.copyOf(targets)),
                "Enumerated six own isolation machines exactly equal production IvBuffers.CONTROLLERS");
        IvMachineScope.NATIVE_IDS.stream().sorted().forEach(id -> targets.add("gtceu:" + id));
        IvMachineScope.FOREIGN_NATIVE_IDS.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue().stream().sorted()
                        .forEach(id -> targets.add(entry.getKey() + ":" + id)));
        check(targets.size() == 25, "All six own machines and all nineteen native isolation targets enumerated");
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var world = (AuditWorld) unsafe.allocateInstance(AuditWorld.class);
        for (String name : List.of("renderedBlocks", "blockEntities", "proxyWorld")) {
            var field = TrackedDummyWorld.class.getField(name);
            field.setAccessible(true);
            field.set(world, name.equals("proxyWorld") ? new java.lang.ref.WeakReference<Level>(null)
                    : new java.util.HashMap<>());
        }
        var state = new MultiblockState(world, BlockPos.ZERO);
        try {
            for (String id : targets) testMachine(id, world, state, superBuffer, ordinary);
        } finally {
            world.renderedBlocks.clear();
            world.blockEntities.clear();
        }
    }

    private void testMachine(String id, AuditWorld world, MultiblockState state,
                             Block superBuffer, List<Block> ordinary) throws Exception {
        var raw = GTRegistries.MACHINES.get(new ResourceLocation(id));
        check(raw instanceof MultiblockMachineDefinition, id + " resolves to a registered multiblock");
        var definition = (MultiblockMachineDefinition) raw;
        var pattern = definition.getPatternFactory().get();
        var grid = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        Set<TraceabilityPredicate> seenRules = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<SimplePredicate> materials = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Port> ports = new ArrayList<>();
        Map<BlockPos, Character> factorySymbols = FACTORY.equals(id) ? factorySymbols() : Map.of();
        int portPositions = 0;
        Map<TraceabilityPredicate, Boolean> materialRules = new IdentityHashMap<>();
        for (int z = 0; z < grid.length; z++) for (int y = 0; y < grid[z].length; y++)
            for (int x = 0; x < grid[z][y].length; x++) {
            var rule = grid[z][y][x];
            if (seenRules.add(rule)) {
                boolean containsMaterials = false;
                for (var list : List.of(rule.common, rule.limited)) for (var simple : list) {
                    if (simple.candidates == null) continue;
                    boolean acceptsSuper = false;
                    for (var candidate : simple.candidates.get()) {
                        if (candidate == null) continue;
                        Block candidateBlock = candidate.getBlockState().getBlock();
                        acceptsSuper |= candidateBlock == superBuffer;
                    }
                    if (acceptsSuper) { materials.add(simple); containsMaterials = true; }
                }
                materialRules.put(rule, containsMaterials);
            }
            if (Boolean.TRUE.equals(materialRules.get(rule))) {
                portPositions++;
                var sourcePosition = new BlockPos(x, y, z);
                ports.add(new Port(sourcePosition, rule, factorySymbols.getOrDefault(sourcePosition, '?')));
            }
        }
        boolean factory = FACTORY.equals(id);
        int count = factory ? expectedJointLimit : id.startsWith("gtl_enhancedcore:") && !HYDROTHERMAL.equals(id) ? 32 : 3;
        int endpointLimit = factory ? expectedJointLimit : Integer.MAX_VALUE;
        check(!materials.isEmpty() && portPositions >= (factory ? expectedJointLimit + 1 : count),
                id + " final pattern has real shared material rules and enough distinct hatch positions");
        int before = scenarios;
        for (SimplePredicate material : materials) {
            var matchedPorts = ports.stream().filter(port -> port.rule.common.stream().anyMatch(part -> part == material)
                    || port.rule.limited.stream().anyMatch(part -> part == material)).toList();
            finalLayouts = layouts(matchedPorts, factory);
            check(!finalLayouts.isEmpty() && finalLayouts.stream().allMatch(layout -> layout.size() == matchedPorts.size()),
                    id + " final complete rules retain the exact production material identity at each distinct grid position");
            var rule = new TraceabilityPredicate(material);
            check(material.minCount == 1 && material.maxCount == -1
                            && material.minLayerCount == -1 && material.maxLayerCount == -1,
                    id + " production material minimum has no accidental global/layer maximum");
            if (factory) {
                for (int amount : new int[]{1, 2, 3, 30}) {
                    expect(id, "super-" + amount, world, state, rule, repeated(superBuffer, amount), amount <= expectedJointLimit);
                }
                expect(id, "super-at-limit", world, state, rule, repeated(superBuffer, expectedJointLimit), true);
                expect(id, "super-over-limit", world, state, rule, repeated(superBuffer, expectedJointLimit + 1), false);
            } else {
                expect(id, "multiple-super-" + count, world, state, rule, repeated(superBuffer, count), true);
            }
            expect(id, "all-four-ordinary-io", world, state, rule, ordinary, true);
            for (Block input : ordinary) {
                String name = ForgeRegistries.BLOCKS.getKey(input).toString();
                expect(id, "super-before-ordinary-" + name, world, state, rule, List.of(superBuffer, input), false);
                expect(id, "ordinary-before-super-" + name, world, state, rule, List.of(input, superBuffer), false);
            }
            // Each expect reuses exactly the same state and invokes the actual upstream cleanState().
            expect(id, "retry-after-exclusion-ordinary", world, state, rule, List.of(ordinary.getFirst()), true);
            expect(id, "retry-after-ordinary-super", world, state, rule, repeated(superBuffer, Math.min(3, endpointLimit)), true);
        }
        machines.add(Map.of("id", id, "materialRuleIdentities", materials.size(), "portPositions", portPositions,
                "finalRuleIdentities", ports.stream().map(Port::rule).distinct().count(),
                "factoryXPositions", ports.stream().filter(port -> port.symbol == 'X').count(),
                "factoryQPositions", ports.stream().filter(port -> port.symbol == 'Q').count(),
                "multipleBufferCount", count, "scenarios", scenarios - before));
    }

    private void expect(String id, String scenario, AuditWorld world, MultiblockState state,
                        TraceabilityPredicate rule, List<Block> blocks, boolean expected) {
        expectLayer(id, scenario + " isolated-material", world, state, rule, null, blocks, expected);
        for (int index = 0; index < finalLayouts.size(); index++)
            expectLayer(id, scenario + " final-layout-" + index, world, state, rule, finalLayouts.get(index), blocks, expected);
    }

    private void expectLayer(String id, String scenario, AuditWorld world, MultiblockState state,
                             TraceabilityPredicate materialRule, List<Port> layout, List<Block> blocks, boolean expected) {
        var invoker = (IMultiblockStateInvoker) state;
        invoker.cleanState();
        check(state.getMatchContext().get("gtlEnhancedcoreMaterialPortSelection") == null
                        && state.getGlobalCount().isEmpty() && state.getLayerCount().isEmpty(),
                id + " " + scenario + " begins with upstream-reset selection and counters");
        world.renderedBlocks.clear();
        boolean accepted = true;
        int tested = 0;
        if (layout != null) check(layout.size() >= blocks.size(), id + " " + scenario + " uses distinct real hatch positions");
        for (Block block : blocks) {
            Port port = layout == null ? null : layout.get(tested);
            BlockPos pos = port == null ? new BlockPos(tested, 64, 0) : port.position;
            TraceabilityPredicate rule = port == null ? materialRule : port.rule;
            tested++;
            world.renderedBlocks.put(pos, new BlockInfo(block.defaultBlockState()));
            if (!invoker.updateState(pos, rule) || !rule.test(state)) { accepted = false; break; }
        }
        check(accepted == expected, id + " " + scenario + " expected=" + expected + " actual=" + accepted
                + " tested=" + tested + "/" + blocks.size() + " error=" + state.error);
        scenarios++;
    }

    private List<List<Port>> layouts(List<Port> ports, boolean factory) {
        if (factory) {
            var xs = ports.stream().filter(port -> port.symbol == 'X').toList();
            var qs = ports.stream().filter(port -> port.symbol == 'Q').toList();
            check(xs.size() == 24 && qs.size() == 118, "Factory final rules cover all 24 X and 118 Q positions");
            return List.of(interleave(xs, qs), interleave(qs, xs));
        }
        Set<TraceabilityPredicate> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Port> representatives = ports.stream().filter(port -> seen.add(port.rule)).toList();
        List<List<Port>> result = new ArrayList<>();
        for (Port anchor : representatives) {
            List<Port> order = new ArrayList<>(); order.add(anchor);
            representatives.stream().filter(port -> port != anchor).forEach(order::add);
            ports.stream().filter(port -> !order.contains(port)).forEach(order::add);
            result.add(order);
        }
        return result;
    }

    private static List<Port> interleave(List<Port> first, List<Port> second) {
        List<Port> result = new ArrayList<>();
        for (int index = 0; index < Math.max(first.size(), second.size()); index++) {
            if (index < first.size()) result.add(first.get(index));
            if (index < second.size()) result.add(second.get(index));
        }
        return result;
    }

    private static Map<BlockPos, Character> factorySymbols() throws Exception {
        var result = new LinkedHashMap<BlockPos, Character>();
        try (var input = StructureData.class.getResourceAsStream(
                "/data/gtl_enhancedcore/structures/integrated_universal_factory.pattern.gz")) {
            var structure = StructureData.read(input);
            int[] depth = {0};
            structure.forEachAisle(rows -> {
                int z = depth[0]++;
                for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) {
                    char symbol = rows[y].charAt(x);
                    if (symbol == 'X' || symbol == 'Q') result.put(new BlockPos(x, y, z), symbol);
                }
            });
        }
        return result;
    }

    private record Port(BlockPos position, TraceabilityPredicate rule, char symbol) {}

    private static List<Block> repeated(Block block, int amount) {
        return new ArrayList<>(Collections.nCopies(amount, block));
    }

    private static Block block(String id) {
        Block result = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (result == null || result == Blocks.AIR) throw new AssertionError("Missing actual registered block " + id);
        return result;
    }

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks.add(message);
    }

    /** Block-state fixture only: material predicates must not need hatch entities or a server world. */
    private static final class AuditWorld extends TrackedDummyWorld {
        private AuditWorld() { super(null); }
        @Override public Level getLevel() { return this; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    }
}
