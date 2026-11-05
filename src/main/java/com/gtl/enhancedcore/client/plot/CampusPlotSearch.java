package com.gtl.enhancedcore.client.plot;

import com.gtl.enhancedcore.client.cloud.CloudSearchMatcher;
import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Local name/ID/pinyin matching. Each whitespace-separated query term must match an alias. */
public final class CampusPlotSearch {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private CampusPlotSearch() {}

    /** Rebuild only when localized names or upgrade aliases change, then retain the handles. */
    public static Aliases prepare(Collection<String> aliases) {
        var identifiers = new LinkedHashSet<String>();
        var names = new LinkedHashSet<String>();
        for (String alias : aliases) {
            String literal = canonical(alias);
            if (literal.indexOf(':') > 0 && literal.indexOf(' ') < 0) identifiers.add(literal);
            names.add(normalize(literal));
        }
        return new Aliases(List.copyOf(identifiers), names.stream().map(CloudSearchMatcher::prepare).toList());
    }

    /** One query compilation per input update, shared by every indexed destination. */
    public static Query compile(String query) {
        String literal = canonical(query).strip();
        if (literal.contains(":")) return new Query(literal, List.of());
        var terms = WHITESPACE.splitAsStream(normalize(literal)).filter(term -> !term.isEmpty())
                .map(CloudSearchMatcher::compile).toList();
        return new Query(null, terms);
    }

    /** Convenience for small one-off callers; screen indexes use compile/prepare directly. */
    public static boolean matches(String query, Collection<String> aliases) {
        return compile(query).matches(prepare(aliases));
    }

    public static final class Aliases {
        private final List<String> identifiers;
        private final List<CloudSearchMatcher.SearchText> names;
        private Aliases(List<String> identifiers, List<CloudSearchMatcher.SearchText> names) {
            this.identifiers = identifiers; this.names = names;
        }
    }

    public static final class Query {
        private final String identifier;
        private final List<CloudSearchMatcher> terms;
        private Query(String identifier, List<CloudSearchMatcher> terms) {
            this.identifier = identifier; this.terms = terms;
        }
        public boolean matches(Aliases aliases) {
            if (identifier != null) {
                for (String alias : aliases.identifiers) if (alias.contains(identifier)) return true;
                return false;
            }
            for (var term : terms) {
                boolean matched = false;
                for (var name : aliases.names) if (term.matches(name)) { matched = true; break; }
                if (!matched) return false;
            }
            return true;
        }
    }
    private static String canonical(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
    private static String normalize(String value) {
        return value.replace('_', ' ').replace(':', ' ').replace('-', ' ').strip();
    }
    public static int cycle(int index, int delta, int count) {
        return count == 0 ? -1 : Math.floorMod(index + delta, count);
    }
}
