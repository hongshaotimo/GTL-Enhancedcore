package com.gtl.enhancedcore.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/** Stable round-robin selection; permanently stocked recipes cannot starve later matches. */
public final class FairRecipeSelector<T> {
    private String lastSelected;

    public Set<T> select(Collection<T> candidates, int requested, Function<T, String> id) {
        if (candidates == null || candidates.isEmpty()) return Set.of();
        var sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(id));
        int start = 0;
        if (lastSelected != null) {
            while (start < sorted.size() && id.apply(sorted.get(start)).compareTo(lastSelected) <= 0) start++;
            if (start == sorted.size()) start = 0;
        }
        int count = Math.min(sorted.size(), Math.max(1, requested));
        Set<T> selected = new LinkedHashSet<>(count);
        for (int offset = 0; offset < count; offset++) {
            T candidate = sorted.get((start + offset) % sorted.size());
            selected.add(candidate);
            lastSelected = id.apply(candidate);
        }
        return selected;
    }
}
