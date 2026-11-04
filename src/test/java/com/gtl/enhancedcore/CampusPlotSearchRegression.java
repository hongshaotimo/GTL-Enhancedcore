package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.plot.CampusPlotSearch;
import java.util.List;

final class CampusPlotSearchRegression {
    static int run() {
        int checks = 0;
        check(CampusPlotSearch.matches("assembly line", List.of("Assembly Line / Advanced Assembly Line", "gtceu:assembly_line")),
                "Multiple query terms match a localized display name"); checks++;
        check(CampusPlotSearch.matches("GTCEU:ASSEMBLY_LINE", List.of("gtceu:assembly_line")),
                "Machine IDs match case-insensitively"); checks++;
        check(!CampusPlotSearch.matches("gtceu:assembly_line", List.of("Component Assembly Line", "gtceu:component_assembly_line"))
                        && !CampusPlotSearch.matches("gtceu:assembly_line", List.of("Suprachronal Assembly Line", "gtceu:suprachronal_assembly_line")),
                "Qualified controller IDs do not match another controller with inserted words"); checks++;
        check(CampusPlotSearch.matches("advanced assembly", List.of("Assembly Line / Advanced Assembly Line", "gtceu:assembly_line")),
                "Upgrade member display names share the reserved plot alias"); checks++;
        check(CampusPlotSearch.matches("nano_forge_2", List.of("Nano Forge I-III", "gtceu:nano_forge_1", "gtceu:nano_forge_2")),
                "Upgrade member IDs match punctuation-normalized queries"); checks++;
        check(!CampusPlotSearch.matches("assembly furnace", List.of("Assembly Line", "gtceu:assembly_line")),
                "Every nonempty query term must match an alias"); checks++;
        check(!CampusPlotSearch.matches("missing", List.of("Assembly Line", "gtceu:assembly_line")),
                "Unmatched search reports an empty result set"); checks++;
        check(CampusPlotSearch.matches("\u88c5\u914d", List.of("\u88c5\u914d\u7ebf / \u8fdb\u9636\u88c5\u914d\u7ebf")),
                "Chinese name fragments match without word separators"); checks++;
        check(CampusPlotSearch.cycle(0, 1, 3) == 1 && CampusPlotSearch.cycle(2, 1, 3) == 0
                        && CampusPlotSearch.cycle(0, -1, 3) == 2 && CampusPlotSearch.cycle(-1, 1, 0) == -1,
                "Match navigation wraps in both directions and handles no results"); checks++;
        return checks;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
