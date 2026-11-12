package com.gtl.enhancedcore.common.patterncut;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative name matching; ambiguity is left to the caller. */
public final class PatternCutNames {
    private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\p{Z}\\p{P}\\p{S}]+");
    private static final Pattern TIER_PREFIX = Pattern.compile(
            "^[\\s\\p{Z}\\p{P}\\p{S}]*(?:超大型|超级|大型|高级|终极|巨型|(?:super|large|advanced|ultimate)(?=[\\s\\p{Z}\\p{P}\\p{S}]|$))[\\s\\p{Z}\\p{P}\\p{S}]*");
    private static final Set<String> CHINESE_SMELTERS = Set.of("合金冶炼炉", "超级冶炼炉", "冶炼炉", "合金炉");
    private static final Set<String> ENGLISH_SMELTERS = Set.of("alloysmelter", "supersmelter");

    private PatternCutNames() {}

    public static String groupKey(String name) { return normalize(clean(name)); }

    public static int score(String source, String target) {
        String sourceText = clean(source);
        String targetText = clean(target);
        String sourceName = normalize(sourceText);
        String targetName = normalize(targetText);
        if (sourceName.isEmpty() || targetName.isEmpty()) return 0;
        if (sourceName.equals(targetName)) return 100;

        String sourceBase = normalize(withoutTier(sourceText));
        String targetBase = normalize(withoutTier(targetText));
        if (!sourceBase.isEmpty() && sourceBase.equals(targetBase)) return 80;
        if (sameAlias(CHINESE_SMELTERS, sourceName, sourceBase, targetName, targetBase)
                || sameAlias(ENGLISH_SMELTERS, sourceName, sourceBase, targetName, targetBase)) return 70;
        return 0;
    }

    /** Returns every target with the highest positive score, preserving target order. */
    public static List<Integer> best(String source, List<String> targets) {
        List<Integer> matches = new ArrayList<>();
        int highest = 0;
        for (int i = 0; i < targets.size(); i++) {
            int score = score(source, targets.get(i));
            if (score > highest) {
                highest = score;
                matches.clear();
            }
            if (score > 0 && score == highest) matches.add(i);
        }
        return List.copyOf(matches);
    }

    private static String clean(String name) {
        return name == null ? "" : FORMATTING.matcher(name).replaceAll("").toLowerCase(Locale.ROOT).strip();
    }

    private static String normalize(String name) {
        return SEPARATORS.matcher(name).replaceAll("");
    }

    private static String withoutTier(String name) {
        String remainder = name;
        while (true) {
            var prefix = TIER_PREFIX.matcher(remainder);
            if (!prefix.find()) return remainder;
            remainder = remainder.substring(prefix.end());
        }
    }

    private static boolean sameAlias(Set<String> aliases, String source, String sourceBase,
                                     String target, String targetBase) {
        return (aliases.contains(source) || aliases.contains(sourceBase))
                && (aliases.contains(target) || aliases.contains(targetBase));
    }
}
