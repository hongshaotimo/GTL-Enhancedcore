package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.plot.CampusPlotSearch;
import com.gtl.enhancedcore.client.search.PhoneticSearchMatcher;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

final class CampusPlotSearchRegression {
    private static final String DICTIONARY = "me/towdium/pinin/data.txt";

    static int run() throws Exception {
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
        var aliases = new ArrayList<>(List.of("Assembly Line", "gtceu:assembly_line"));
        var prepared = CampusPlotSearch.prepare(aliases); aliases.clear();
        check(CampusPlotSearch.compile("  ASSEMBLY\tLINE  ").matches(prepared),
                "Compiled multiword queries retain prepared immutable alias handles after the caller changes its collection"); checks++;
        check(CampusPlotSearch.compile("\u3000\t\n").matches(prepared)
                        && !CampusPlotSearch.compile("gtceu:component_assembly_line").matches(prepared),
                "Blank queries and qualified IDs keep their literal behavior through the compiled API"); checks++;
        checks += pinyinDictionary();
        return checks;
    }

    private static int pinyinDictionary() throws Exception {
        int checks = 0;
        var code = new LinkedHashSet<URL>();
        code.add(CampusPlotSearch.class.getProtectionDomain().getCodeSource().getLocation());
        code.add(PhoneticSearchMatcher.class.getProtectionDomain().getCodeSource().getLocation());
        try (var loader = new URLClassLoader(code.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            var search = Class.forName(CampusPlotSearch.class.getName(), true, loader);
            Method matches = search.getMethod("matches", String.class, Collection.class);
            check(loader.getResource(DICTIONARY) == null, "The literal fallback fixture does not inherit a PinIn dictionary"); checks++;
            check(match(matches, "\u7535\u529b", List.of("\u7535\u529b\u9ad8\u7089"))
                            && match(matches, "ELECTRIC furnace", List.of("Electric Blast Furnace", "gtceu:electric_blast_furnace"))
                            && match(matches, "gtceu:electric_blast_furnace", List.of("gtceu:electric_blast_furnace"))
                            && !match(matches, "dianligaolu", List.of("\u7535\u529b\u9ad8\u7089")),
                    "Without the optional dictionary, Chinese, English and ID matching work while pinyin declines cleanly"); checks++;
        }

        Path source = dictionaryJar(); byte[] dictionary;
        try (var archive = new ZipFile(source.toFile())) { dictionary = archive.getInputStream(archive.getEntry(DICTIONARY)).readAllBytes(); }
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dictionary));
        check(dictionary.length > 100_000 && new String(dictionary, StandardCharsets.UTF_8).lines().count() > 20_000,
                "The pinyin fixture loads a real complete JECharacters dictionary from the current instance"); checks++;
        code.add(source.toUri().toURL());
        try (var loader = new URLClassLoader(code.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            var search = Class.forName(CampusPlotSearch.class.getName(), true, loader);
            Method matches = search.getMethod("matches", String.class, Collection.class);
            check(loader.getResource(DICTIONARY) != null, "The isolated campus matcher can see the actual JECharacters PinIn resource"); checks++;
            var cases = List.of(
                    List.of("\u7535\u529b\u9ad8\u7089", "dianligaolu", "dlgl", "dianlgl", "DIANLIGAOLU", "\u3000\tDLGL  ", "\u7535\u529b gaolu"),
                    List.of("\u5927\u578b\u9752\u94dc\u9505\u7089", "daxingqingtongguolu", "dxqtgl", "daxqtgl", "DAXINGQINGTONGGUOLU", "\u5927\u578b qtgl"),
                    List.of("\u88c5\u914d\u7ebf", "zhuangpeixian", "zpx", "zhuangpx", "ZPX", "\u88c5\u914d xian"));
            for (var row : cases) for (String query : row.subList(1, row.size())) {
                check(match(matches, query, List.of(row.getFirst())), "Real PinIn data matches full, initial or mixed-syllable campus query: " + query); checks++;
            }
            check(match(matches, "\uff24\uff2c\uff27\uff2c", List.of("\u7535\u529b\u9ad8\u7089"))
                            && match(matches, "dianli\u3000\tgaolu\n", List.of("\u7535\u529b\u9ad8\u7089")),
                    "NFKC case and whitespace normalization precede one shared compiled query"); checks++;
            check(match(matches, "dlgl furnace", List.of("\u7535\u529b\u9ad8\u7089", "Electric Blast Furnace", "gtceu:electric_blast_furnace"))
                            && !match(matches, "dlgl boiler", List.of("\u7535\u529b\u9ad8\u7089", "Electric Blast Furnace", "gtceu:electric_blast_furnace")),
                    "Pinyin and English terms retain AND semantics across a destination's aliases"); checks++;
            check(match(matches, "jinjiezpx", List.of("\u88c5\u914d\u7ebf / \u8fdb\u9636\u88c5\u914d\u7ebf", "Assembly Line / Advanced Assembly Line", "gtceu:assembly_line", "gtceu:advanced_assembly_line"))
                            && match(matches, "nano_forge_2", List.of("\u7eb3\u7c73\u953b\u7089 I-III", "gtceu:nano_forge_1", "gtceu:nano_forge_2")),
                    "Pinyin upgrade display aliases and punctuation-normalized member IDs both remain searchable"); checks++;
            check(match(matches, "zhongxingjiqi", List.of("\u91cd\u578b\u673a\u5668"))
                            && match(matches, "chongxingjiqi", List.of("\u91cd\u578b\u673a\u5668")),
                    "Real dictionary alternatives retain both polyphonic readings without selecting a single pronunciation"); checks++;
            check(!match(matches, "dianligaolu", List.of("Electric Blast Furnace", "gtceu:electric_blast_furnace"))
                            && !match(matches, "gtceu:assembly_line", List.of("\u90e8\u4ef6\u88c5\u914d\u7ebf", "gtceu:component_assembly_line"))
                            && !match(matches, "dlgl furnace", List.of("\u5927\u578b\u9752\u94dc\u9505\u7089", "Large Bronze Boiler")),
                    "Pinyin never fabricates missing Chinese aliases or weakens qualified-ID and term boundaries"); checks++;

            var prepared = search.getMethod("prepare", Collection.class).invoke(null, List.of("\u7535\u529b\u9ad8\u7089", "Electric Blast Furnace"));
            var query = search.getMethod("compile", String.class).invoke(null, "dlgl furnace");
            Method preparedMatch = query.getClass().getMethod("matches", prepared.getClass());
            check((boolean) preparedMatch.invoke(query, prepared), "Prepared names warm the actual lazy PinIn syllables"); checks++;
            var cache = Class.forName(PhoneticSearchMatcher.class.getName(), true, loader).getDeclaredField("TEXTS"); cache.setAccessible(true);
            ((Map<?, ?>) cache.get(null)).clear();
            boolean stable = true;
            for (int index = 0; index < 256; index++) stable &= (boolean) preparedMatch.invoke(query, prepared);
            check(stable && ((Map<?, ?>) cache.get(null)).isEmpty(),
                    "Repeated destination matching reuses one compiled query and retained SearchText handles without preparing names again"); checks++;
        }
        System.out.println("Campus pinyin dictionary: " + source + "; bytes=" + dictionary.length + "; sha256=" + sha256);
        return checks;
    }

    private static boolean match(Method method, String query, List<String> aliases) throws Exception {
        return (boolean) method.invoke(null, query, aliases);
    }
    private static Path dictionaryJar() throws Exception {
        String override = System.getProperty("gtl.enhancedcore.pinyinDictionaryJar", "");
        if (!override.isBlank()) return verifiedDictionary(Path.of(override));
        String configured = System.getenv("SHCORE_GAME_DIR");
        Path mods = Path.of(configured == null || configured.isBlank()
                ? "C:/Minecraft/.minecraft/versions/GregTech Leisure-Enhanced" : configured).resolve("mods");
        if (Files.isDirectory(mods)) try (var paths = Files.list(mods)) {
            for (var path : paths.filter(file -> file.getFileName().toString().startsWith("jecharacters-")
                    && file.toString().endsWith(".jar")).sorted(Comparator.comparing(Path::toString)).toList())
                return verifiedDictionary(path);
        }
        throw new AssertionError("A real current-instance JECharacters dictionary is required; set -Dgtl.enhancedcore.pinyinDictionaryJar=<JECharacters.jar>");
    }
    private static Path verifiedDictionary(Path path) throws Exception {
        check(Files.isRegularFile(path), "Configured JECharacters dictionary JAR exists: " + path);
        try (var archive = new ZipFile(path.toFile())) {
            var metadata = archive.getEntry("META-INF/mods.toml");
            check(archive.getEntry(DICTIONARY) != null && metadata != null
                            && new String(archive.getInputStream(metadata).readAllBytes(), StandardCharsets.UTF_8).contains("\"jecharacters\""),
                    "Pinyin regression uses a real JECharacters JAR and its embedded PinIn dictionary");
        }
        return path.toAbsolutePath().normalize();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Campus plot search checks: " + run());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
