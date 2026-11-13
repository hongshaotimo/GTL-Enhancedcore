package com.gtl.enhancedcore.common.recipe.iv;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Selector identity belongs to the accepted pattern, not the buffer's fallback circuit. */
public final class IvPatternAdmission {
    private IvPatternAdmission() {}

    public static <T> boolean allowsType(Set<T> declared, T candidate) {
        return declared.isEmpty() || declared.contains(candidate);
    }

    public static <T> boolean compatibleTypes(Set<T> declared, Set<T> supported) {
        return declared.isEmpty() || declared.stream().anyMatch(supported::contains);
    }

    /** GRAPH supplies reusable configuration once; physical inputs still prove the exact batch. */
    public static <K> long dispatchMultiplier(Map<K, Long> unit, Map<K, Long> received, long operations,
                                              Predicate<K> circuit, Predicate<K> fixedVirtual) {
        long multiplier = operations;
        for (var input : unit.entrySet()) {
            if (circuit.test(input.getKey())) continue;
            long count = received.getOrDefault(input.getKey(), 0L);
            if (fixedVirtual.test(input.getKey())) {
                if (count != input.getValue()) throw new IllegalArgumentException("虚拟配置投料与样板不一致");
                continue;
            }
            if (count <= 0 || count % input.getValue() != 0) throw new IllegalArgumentException("投料与样板倍率不一致");
            long ratio = count / input.getValue();
            if (multiplier >= 0 && multiplier != ratio) throw new IllegalArgumentException("整笔物品/流体倍率不一致");
            multiplier = ratio;
        }
        if (multiplier < 1) throw new IllegalArgumentException("未获得无消耗样板的智能翻倍订单数量");
        for (K key : received.keySet()) if (!unit.containsKey(key)) throw new IllegalArgumentException("样板外的投料");
        return multiplier;
    }

    public static <K> Map<K, Long> circuits(Map<K, Long> available, Map<K, Long> declared,
                                           Predicate<K> circuit) {
        Map<K, Long> result = new LinkedHashMap<>();
        (declared.isEmpty() ? available : declared).forEach((key, amount) -> {
            if (amount > 0 && circuit.test(key)) result.put(key, 1L);
        });
        return result;
    }

    /** A virtual wrapper must carry exactly one item or fluid, and never a consumable supply. */
    public static <K> Map<K, Long> token(K item, K fluid, Predicate<K> circuit) {
        if ((item == null) == (fluid == null)) throw new IllegalArgumentException("虚拟原料必须只包含一种物品或流体");
        K payload = item == null ? fluid : item;
        return Map.of(payload, circuit.test(payload) ? 1L : Long.MAX_VALUE);
    }

    public static <K> Map<K, Long> virtualStock(Map<K, Long> available, Map<K, Long> accepted,
                                              Predicate<K> circuit) {
        Map<K, Long> result = new LinkedHashMap<>(available);
        if (accepted.keySet().stream().anyMatch(circuit)) result.keySet().removeIf(circuit);
        result.putAll(accepted);
        return result;
    }
}
