package com.gtl.enhancedcore.client.plot;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

/** Local name/ID matching. Every whitespace-separated query term must occur in an alias. */
public final class CampusPlotSearch {
    private CampusPlotSearch() {}
    public static boolean matches(String query, Collection<String> aliases) {
        String identifier = Normalizer.normalize(query, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
        if (identifier.contains(":")) return aliases.stream().filter(alias -> alias.indexOf(':') > 0
                        && alias.indexOf(' ') < 0)
                .map(alias -> Normalizer.normalize(alias, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT))
                .anyMatch(alias -> alias.contains(identifier));
        String[] terms = normalize(query).split("\\s+");
        var names = aliases.stream().map(CampusPlotSearch::normalize).toList();
        for (String term : terms) if (!term.isEmpty() && names.stream().noneMatch(name -> name.contains(term))) return false;
        return true;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace(':', ' ').replace('-', ' ').strip();
    }
    public static int cycle(int index, int delta, int count) {
        return count == 0 ? -1 : Math.floorMod(index + delta, count);
    }
}
