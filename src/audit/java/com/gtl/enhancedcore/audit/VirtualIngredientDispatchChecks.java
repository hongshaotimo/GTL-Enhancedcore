package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.recipe.iv.IvPatternAdmission;
import com.gtl.enhancedcore.common.recipe.iv.LongAllocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphDispatchContext;

/** Focused actual admission and GRAPH-context checks; no Minecraft registration or saved world. */
public final class VirtualIngredientDispatchChecks {
    private static final Predicate<String> CIRCUIT = key -> key.startsWith("circuit:");
    private static final Predicate<String> TOKEN = key -> key.startsWith("virtual:");
    private VirtualIngredientDispatchChecks() {}

    public static void main(String[] args) {
        var checks = new ArrayList<String>();
        run(checks);
        System.out.println("PASS: " + checks.size() + " focused GRAPH virtual-ingredient checks");
        checks.forEach(System.out::println);
    }

    public static void run(List<String> checks) {
        require(!GraphDispatchContext.active(), "GRAPH context is initially inactive", checks);
        String token = "virtual:mold";
        long configured = 2;
        Map<String, Long> unit = Map.of("iron", 2L, token, configured);
        Map<String, Long> offered = Map.of("iron", 14L, token, configured);
        GraphDispatchContext.call(Map.of(), 7, () -> {
            Predicate<String> fixed = key -> GraphDispatchContext.active() && TOKEN.test(key);
            require(GraphDispatchContext.active() && GraphDispatchContext.operations() == 7
                            && GraphDispatchContext.selectedInputs() != null,
                    "GRAPH context exposes seven operations and selected-input scope", checks);
            require(IvPatternAdmission.dispatchMultiplier(unit, offered, GraphDispatchContext.operations(),
                    CIRCUIT, fixed) == 7,
                    "Seven physical operations accept one configuration unit of two mold wrappers", checks);
            require(IvPatternAdmission.dispatchMultiplier(Map.of(token, configured), Map.of(token, configured),
                    GraphDispatchContext.operations(), CIRCUIT, fixed) == 7,
                    "Virtual-only order retains seven GRAPH operations", checks);
            require(rejected(() -> IvPatternAdmission.dispatchMultiplier(unit, Map.of("iron", 12L, token, configured),
                    GraphDispatchContext.operations(), CIRCUIT, fixed)),
                    "Insufficient physical material rejects despite available configuration", checks);
            require(rejected(() -> IvPatternAdmission.dispatchMultiplier(unit, Map.of("iron", 14L),
                    GraphDispatchContext.operations(), CIRCUIT, fixed)),
                    "Missing virtual configuration rejects", checks);
            require(rejected(() -> IvPatternAdmission.dispatchMultiplier(unit, Map.of("iron", 14L, token, configured + 1),
                    GraphDispatchContext.operations(), CIRCUIT, fixed)),
                    "Wrong configuration quantity rejects", checks);
            return null;
        });
        require(!GraphDispatchContext.active() && GraphDispatchContext.selectedInputs() == null,
                "GRAPH scope exits after dispatch", checks);
        require(rejected(() -> IvPatternAdmission.dispatchMultiplier(unit, offered, 7, CIRCUIT, key -> false)),
                "Legacy CPU does not accept a single configuration unit for seven multiplied inputs", checks);
        require(IvPatternAdmission.dispatchMultiplier(unit, Map.of("iron", 14L, token, configured * 7), 7,
                CIRCUIT, key -> false) == 7,
                "Legacy CPU retains its fully multiplied wrapper quantity rule", checks);
        var stock = List.of(new LongAllocation.Supply<>("iron", 14L, true),
                new LongAllocation.Supply<>("mold", Long.MAX_VALUE, false));
        require(Map.of("iron", 14L).equals(LongAllocation.plan(stock,
                        List.of(new LongAllocation.Need<String>("iron"::equals, 14, true),
                                new LongAllocation.Need<String>("mold"::equals, 1, false)))),
                "Virtual catalyst satisfies a non-consumed condition while only physical iron is charged", checks);
        require(LongAllocation.plan(List.of(new LongAllocation.Supply<>("iron", Long.MAX_VALUE, false)),
                List.of(new LongAllocation.Need<String>("iron"::equals, 1, true))) == null,
                "Virtual stock cannot substitute for a consumed physical material", checks);
    }

    private static boolean rejected(Runnable action) {
        try { action.run(); return false; }
        catch (IllegalArgumentException expected) { return true; }
    }
    private static void require(boolean valid, String description, List<String> checks) {
        if (!valid) throw new AssertionError(description);
        checks.add(description);
    }
}
