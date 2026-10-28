package com.gtl.enhancedcore.client.cloud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

/** Client search only. Reuses optional PinIn dictionary data without loading its runtime classes. */
public final class CloudSearchMatcher {
    private static final System.Logger LOGGER = System.getLogger(CloudSearchMatcher.class.getName());
    private static final int MAX_CACHED_TEXTS = 4096;
    private static final int MAX_CACHED_CHARACTERS = 8192;
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_DICTIONARY_ENTRIES = 65536;
    private static final Map<String, SearchText> TEXTS = lru(MAX_CACHED_TEXTS);
    private static final Map<Integer, String[]> CHARACTERS = lru(MAX_CACHED_CHARACTERS);
    private static PinyinBackend backend;
    private static boolean backendResolved;
    private final String query;
    private final boolean phonetic;

    private CloudSearchMatcher(String query) {
        this.query = query;
        // Chinese / English / resource-ID queries always keep literal substring semantics.
        this.phonetic = !query.isEmpty() && query.chars().allMatch(c -> c >= 'a' && c <= 'z');
    }

    public static CloudSearchMatcher compile(String query) {
        return new CloudSearchMatcher(query == null ? "" : query.strip().toLowerCase(Locale.ROOT));
    }

    public boolean matches(String text) {
        return matches(prepare(text));
    }

    public boolean matches(SearchText text) {
        if (query.isEmpty() || text.literal.contains(query)) return true;
        if (!phonetic || !text.hasHan || text.literal.length() > MAX_TEXT_LENGTH) return false;
        String[][] tokens = text.syllables();
        if (tokens.length == 0) return false;
        // Active query offsets retain all polyphonic paths without Cartesian expansion or recursion.
        boolean[] active = new boolean[query.length() + 1];
        boolean[] next = new boolean[active.length];
        for (String[] alternatives : tokens) {
            java.util.Arrays.fill(next, false);
            active[0] = true; // A substring may begin at any displayed character.
            for (int offset = 0; offset < query.length(); offset++) {
                if (!active[offset]) continue;
                for (String syllable : alternatives) {
                    int remaining = query.length() - offset;
                    int matched = Math.min(remaining, syllable.length());
                    if (!query.regionMatches(offset, syllable, 0, matched)) continue;
                    if (remaining <= syllable.length()) return true;
                    next[offset + syllable.length()] = true;
                }
            }
            boolean[] swap = active; active = next; next = swap;
        }
        return false;
    }

    /** Retain this immutable handle in a name index to avoid global-cache churn for large registries. */
    public static synchronized SearchText prepare(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_TEXT_LENGTH) return new SearchText(normalized);
        return TEXTS.computeIfAbsent(normalized, SearchText::new);
    }

    /** Call on language/resource reload; retained SearchText handles should also be discarded. */
    public static synchronized void clearCaches() {
        TEXTS.clear();
        CHARACTERS.clear();
        backend = null;
        backendResolved = false;
    }

    public static final class SearchText {
        private final String literal;
        private final boolean hasHan;
        private volatile String[][] syllables;

        private SearchText(String literal) {
            this.literal = literal;
            this.hasHan = literal.codePoints().anyMatch(CloudSearchMatcher::isHan);
        }

        private String[][] syllables() {
            String[][] value = syllables;
            if (value != null) return value;
            synchronized (this) {
                if (syllables != null) return syllables;
                var result = new ArrayList<String[]>();
                literal.codePoints().forEach(cp -> result.add(character(cp)));
                return syllables = result.toArray(String[][]::new);
            }
        }
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private static synchronized String[] character(int codePoint) {
        String literal = new String(Character.toChars(codePoint));
        if (!isHan(codePoint)) return new String[]{literal};
        String[] cached = CHARACTERS.get(codePoint);
        if (cached != null) return cached;
        var forms = new LinkedHashSet<String>();
        forms.add(literal);
        var provider = backend();
        if (provider != null) {
            String rawForms = provider.dictionary.get(codePoint);
            if (rawForms != null) {
                int start = 0;
                while (start < rawForms.length()) {
                    int comma = rawForms.indexOf(',', start);
                    int limit = comma < 0 ? rawForms.length() : comma;
                    String raw = rawForms.substring(start, limit).strip().toLowerCase(Locale.ROOT);
                    int end = raw.length();
                    while (end > 0 && Character.isDigit(raw.charAt(end - 1))) end--;
                    String syllable = raw.substring(0, end);
                    if (!syllable.isEmpty() && syllable.chars().allMatch(c -> c >= 'a' && c <= 'z')) {
                        forms.add(syllable);
                        forms.add(syllable.substring(0, 1));
                    }
                    start = limit + 1;
                }
            }
        }
        String[] value = forms.toArray(String[]::new);
        CHARACTERS.put(codePoint, value);
        return value;
    }

    private static PinyinBackend backend() {
        if (!backendResolved) {
            backendResolved = true;
            // PinIn's constructor links fastutil, although its dictionary is self-contained.
            // Read that documented local resource directly: this also works in standalone tools
            // and never initializes JECharacters' client mod / JEI classes on a dedicated server.
            try (var stream = CloudSearchMatcher.class.getClassLoader()
                    .getResourceAsStream("me/towdium/pinin/data.txt")) {
                if (stream == null) return null; // JECharacters is optional.
                var dictionary = new HashMap<Integer, String>();
                try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) continue;
                        int separator = line.indexOf(':');
                        if (separator <= 0 || line.length() > 256)
                            throw new IOException("Unexpected PinIn dictionary entry");
                        if (dictionary.size() >= MAX_DICTIONARY_ENTRIES)
                            throw new IOException("PinIn dictionary exceeds bounded capacity");
                        String readings = line.substring(separator + 1).strip();
                        if (!readings.isEmpty()) dictionary.put(line.codePointAt(0), readings);
                    }
                }
                if (dictionary.isEmpty()) throw new IOException("PinIn dictionary is empty");
                backend = new PinyinBackend(Map.copyOf(dictionary));
            } catch (IOException | RuntimeException error) {
                backend = null;
                LOGGER.log(System.Logger.Level.WARNING,
                        "Cannot read optional PinIn dictionary; cloud search uses literal matching", error);
            }
        }
        return backend;
    }

    private record PinyinBackend(Map<Integer, String> dictionary) {}

    private static <K, V> Map<K, V> lru(int maximum) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> entry) {
                return size() > maximum;
            }
        };
    }
}
