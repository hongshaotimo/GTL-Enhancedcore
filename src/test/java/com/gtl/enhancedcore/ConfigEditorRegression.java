package com.gtl.enhancedcore;

import com.google.gson.*;
import com.gtl.enhancedcore.common.config.*;
import com.gtl.enhancedcore.common.config.TooltipOverrides.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Round-trip authoring, future registry entries, tooltip ordering and edit conflict protection. */
public final class ConfigEditorRegression {
    private static int assertions;
    public static int run() throws Exception {
        assertions = 0;
        inlineMarkup();
        toolbarSelections();
        lineDrafts();
        paletteAndLayout();
        var component = JsonParser.parseString("""
                {"text":"反应芯 ","color":"#12ABef","font":"minecraft:uniform","bold":true,
                 "extra":[{"translate":"block.gtl_enhancedcore.extreme_reactor","italic":true,"underlined":true}]}
                """);
        var line = new Line(component, Visibility.SHIFT, true);
        var custom = new Entry(Mode.APPEND, List.of(line));
        var original = new TooltipOverrides(Map.of("gtl_enhancedcore:extreme_reactor", custom));
        var restored = TooltipOverrides.parse(original.serialize());
        check(restored.items().equals(original.items()), "Rich text, font and visibility survive save/reload");
        var expanded = restored.include(List.of("gtl_enhancedcore:extreme_reactor", "gtl_enhancedcore:future_machine"));
        check(expanded.entry("gtl_enhancedcore:extreme_reactor").equals(custom), "New registry discovery preserves authored entries");
        check(expanded.items().containsKey("gtl_enhancedcore:future_machine")
                && expanded.entry("gtl_enhancedcore:future_machine").mode() == Mode.DEFAULT, "New machines are automatically editable with original tips");
        check(expanded.include(expanded.items().keySet()).serialize().equals(expanded.serialize()), "Catalog discovery is idempotent");
        check(!line.visible(false) && line.visible(true), "Shift-only tips");
        check(new Line(component, Visibility.NO_SHIFT, false).visible(false)
                && !new Line(component, Visibility.NO_SHIFT, false).visible(true), "Non-Shift tips");
        for (boolean shift : List.of(true, false)) check(Line.text("always").visible(shift), "Always-visible tips");
        var base = List.of("NAME", "voltage", "warning", "same", "same");
        var extra = List.of("new", "new");
        check(TooltipOverrides.compose(base, extra, Mode.DEFAULT).equals(base), "Default retains all lines and duplicates");
        check(TooltipOverrides.compose(base, extra, Mode.REPLACE).equals(List.of("NAME", "new", "new")), "Replace retains only item name and custom tips");
        check(TooltipOverrides.compose(base, extra, Mode.PREPEND).equals(List.of("NAME", "new", "new", "voltage", "warning", "same", "same")), "Prepend goes below item name");
        check(TooltipOverrides.compose(base, extra, Mode.APPEND).equals(List.of("NAME", "voltage", "warning", "same", "same", "new", "new")), "Append preserves original order");
        check(base.equals(List.of("NAME", "voltage", "warning", "same", "same")), "Composition never mutates its input");
        for (String bad : List.of("{", original.serialize().replace("#12ABef", "#GGGGGG"),
                original.serialize().replace("minecraft:uniform", "bad font"), original.serialize().replace("\"bold\": true", "\"bold\": \"true\""),
                original.serialize().replace("gtl_enhancedcore:extreme_reactor", "other:item"),
                original.serialize().replace("\"schema\": 1", "\"schema\": 2"),
                original.serialize().replace("\"shift\"", "\"unknown\""))) {
            fails(() -> TooltipOverrides.parse(bad), "Malformed or invalid customization rejected");
        }
        var tooMany = new Entry(Mode.REPLACE, Collections.nCopies(257, Line.text("x")));
        fails(() -> TooltipOverrides.parseEntry(TooltipOverrides.serialize(tooMany)), "Line limit enforced before write");
        fileEdits();
        String menu = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/client/config/EnhancedConfigScreen.java"));
        var buttons = java.util.regex.Pattern.compile("(?:button|row)\\(\"([^\"]+)\"").matcher(menu);
        var actions = new ArrayList<String>();
        while (buttons.find()) actions.add(buttons.group(1));
        // 2026-10-12 用户要求「复制 ID / 复制配方」按键功能可在配置里开关，因此
        // 配置主页新增 copy_keys 入口（进入独立子界面）。主页仍不含机器数值设置，
        // 决策 10「配置页只保留 Tips、删除机器数值配置」继续成立。
        check(actions.equals(List.of("tips", "reload_tips", "tips_file", "copy_keys", "all_files", "open_folder", "back")),
                "The configuration entry exposes item Tips actions plus the requested copy-key toggles");
        String copyScreen = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/client/config/CopyKeysScreen.java"));
        check(copyScreen.contains("CopyKeysConfig.save("),
                "The copy-key switches write their configuration file");
        return assertions;
    }
    private static void inlineMarkup() {
        String input = "[白色]支持模式：[动态彩虹]组装机";
        var spans = TooltipMarkup.parse(input);
        check(spans.size() == 2, "User example becomes exactly two local spans");
        check(spans.get(0).text().equals("支持模式：") && spans.get(0).color() == 0xffffff,
                "Prefix is explicitly white");
        check(!spans.get(0).isRainbow(false) && !spans.get(0).isRainbow(true), "Prefix stays white even if default rainbow is enabled");
        check(spans.get(1).text().equals("组装机") && spans.get(1).isRainbow(false), "Only machine name enables the animated rainbow");
        var suffix = TooltipMarkup.parse(input + "[白色]；其他");
        check(suffix.size() == 3 && !suffix.get(2).isRainbow(true), "A later color ends the rainbow locally");
        var styles = TooltipMarkup.parse("[粗体][#12ABef][字体=minecraft:uniform]甲[斜体]乙[/粗体]丙[重置]丁");
        check(styles.get(0).styles().get("bold") && styles.get(0).color() == 0x12abef
                && styles.get(0).font().equals("minecraft:uniform"), "Font, custom color and bold compose locally");
        check(styles.get(1).styles().get("bold") && styles.get(1).styles().get("italic"), "Styles accumulate until changed");
        check(!styles.get(2).styles().get("bold") && styles.get(2).styles().get("italic"), "Closing one style keeps another style active");
        var reset = styles.get(3);
        check(reset.styles().isEmpty() && reset.font() == null && reset.color() == null
                && reset.isRainbow(true) && !reset.isRainbow(false), "Reset inherits original line defaults including rainbow");
        check(TooltipMarkup.parse("[[白色]原样").getFirst().text().equals("[白色]原样"), "Escaped marker stays literal");
        for (String literal : List.of("原始文字", "[未知]正文", "[动态彩", "[#GGGGGG]", "[字体=bad id]"))
            check(TooltipMarkup.parse(literal).stream().map(TooltipMarkup.Span::text).reduce("", String::concat).equals(literal),
                    "Unknown and incomplete tags retain all text");
        var english = TooltipMarkup.parse("[white]A[rainbow]B[reset]C");
        check(english.get(0).color() == 0xffffff && english.get(1).isRainbow(false) && !english.get(2).isRainbow(false), "English aliases follow the same rules");
        check(TooltipMarkup.parse("[白色][动态彩虹]🪐机").getFirst().text().equals("🪐机"), "Adjacent tags and supplementary Unicode are preserved");
        var source = new TooltipOverrides(Map.of("gtl_enhancedcore:extreme_reactor", new Entry(Mode.APPEND, List.of(Line.text(input)))));
        var restored = TooltipOverrides.parse(source.serialize()).entry("gtl_enhancedcore:extreme_reactor").lines().getFirst();
        check(restored.component().getAsJsonObject().get("text").getAsString().equals(input), "Save/reopen retains editable marker source");
        check(TooltipMarkup.parse(restored.component().getAsJsonObject().get("text").getAsString()).equals(spans), "Reload retains local effect boundaries");
        check(TooltipMarkup.parse("[红色][粗体]").isEmpty(), "Markers alone do not leak visible brackets or empty spans");
    }
    private static String visible(String text) {
        return TooltipMarkup.parse(text).stream().map(TooltipMarkup.Span::text).reduce("", String::concat);
    }
    private static TooltipMarkup.Span containing(String text, String word) {
        return TooltipMarkup.parse(text).stream().filter(span -> span.text().contains(word)).findFirst().orElseThrow();
    }
    private static void toolbarSelections() {
        var repeated = "组装机和组装机；结束";
        var selection = new TooltipTextEditor.Selection(7, 4);
        var edited = TooltipTextEditor.apply(repeated, selection, "[动态彩虹]");
        check(visible(edited.text()).equals(repeated), "Coloring the second repeated word preserves all visible text");
        var spans = TooltipMarkup.parse(edited.text());
        check(spans.getFirst().text().equals("组装机和") && !spans.getFirst().isRainbow(false), "The first occurrence is not selected by string search");
        check(spans.get(1).text().equals("组装机") && spans.get(1).isRainbow(false), "Only the actual second occurrence becomes rainbow");
        check(!spans.getLast().isRainbow(false), "Rainbow does not leak into the unselected suffix");
        var backwards = TooltipTextEditor.apply(repeated, new TooltipTextEditor.Selection(4, 7), "[动态彩虹]");
        check(backwards.text().equals(edited.text()) && backwards.selection().cursor() < backwards.selection().anchor(), "Backwards selection uses the same words and retains its direction");
        check(edited.text().substring(edited.selection().from(), edited.selection().to()).equals("组装机"), "Selection is retained for another toolbar action");

        String original = "[金色][粗体][字体=minecraft:uniform]前机后";
        int position = original.indexOf('机');
        var colored = TooltipTextEditor.apply(original, new TooltipTextEditor.Selection(position + 1, position), "[#1275EF]");
        var suffix = containing(colored.text(), "后");
        check(containing(colored.text(), "机").color() == 0x1275ef && suffix.color() == 0xffaa00, "Custom selection color restores the preceding gold color after the selection");
        check(suffix.styles().get("bold") && suffix.font().equals("minecraft:uniform"), "Trailing bold and font survive local color changes");
        var rainbowBase = "[动态彩虹]前机后";
        position = rainbowBase.indexOf('机');
        var white = TooltipTextEditor.apply(rainbowBase, new TooltipTextEditor.Selection(position + 1, position), "[白色]");
        check(!containing(white.text(), "机").isRainbow(true) && containing(white.text(), "后").isRainbow(false), "A fixed selection color restores an existing rainbow afterwards");

        String mixed = "前[白色]甲[绿色]乙[重置]丙后";
        var unified = TooltipTextEditor.apply(mixed, new TooltipTextEditor.Selection(mixed.length() - 1, 1), "[红色]");
        check(visible(unified.text()).equals("前甲乙丙后"), "Existing formatting markers are rewritten without deleting selected words");
        for (String word : List.of("甲", "乙", "丙")) check(containing(unified.text(), word).color() == 0xff5555, "New color covers all selected spans, including old colors and resets");
        check(containing(unified.text(), "后").color() == null, "The untouched suffix inherits its original color");

        var bold = TooltipTextEditor.apply("甲机乙", new TooltipTextEditor.Selection(2, 1), "[粗体]");
        check(containing(bold.text(), "机").styles().get("bold"), "A selected word can be made bold");
        var off = TooltipTextEditor.apply(bold.text(), bold.selection(), TooltipMarkup.styleTag("bold",
                !TooltipMarkup.formatAt(bold.text(), bold.selection().from()).style("bold", false)));
        check(!containing(off.text(), "机").styles().get("bold") && !containing(off.text(), "乙").styles().containsKey("bold"), "Pressing the style button again disables it without changing the suffix");
        var insertion = TooltipTextEditor.apply("甲乙", new TooltipTextEditor.Selection(1, 1), "[动态彩虹]");
        check(insertion.text().equals("甲[动态彩虹]乙") && !insertion.selection().selected(), "No selection inserts a marker at the actual caret");
        var emoji = TooltipTextEditor.apply("🪐机", new TooltipTextEditor.Selection(1, 1), "[动态彩虹]");
        check(emoji.text().startsWith("🪐[动态彩虹]") && visible(emoji.text()).equals("🪐机"), "Toolbar insertion never splits a surrogate pair");
        var insideTag = TooltipTextEditor.apply("[白色]机", new TooltipTextEditor.Selection(2, 2), "[斜体]");
        check(insideTag.text().equals("[白色][斜体]机"), "A caret inside an existing tag moves to its boundary before insertion");

        String legacy = "§6§l前§f机§6§l后";
        int begin = legacy.indexOf("§f");
        var legacyColor = TooltipTextEditor.apply(legacy, new TooltipTextEditor.Selection(legacy.indexOf("§6", begin + 2), begin), "[动态彩虹]");
        check(visible(legacyColor.text()).equals("前机后") && containing(legacyColor.text(), "机").isRainbow(false), "Imported section-sign colors do not override a toolbar-applied rainbow");
        var legacySuffix = containing(legacyColor.text(), "后");
        check(legacySuffix.color() == 0xffaa00 && legacySuffix.styles().get("bold"), "Legacy colors and bold remain correct beyond the selection");
        check(TooltipMarkup.parse("§x§1§2§a§b§E§F字").getFirst().color() == 0x12abef, "Legacy full RGB color is a single recognized color token");
        check(TooltipMarkup.parse("[字体=minecraft:uniform][默认字体][红色][默认颜色]字").getFirst().font() == null,
                "Individual font and color inheritance markers restore defaults");
        String longestFont = "pack:" + "f".repeat(TooltipMarkup.INLINE_FONT_LIMIT - 5);
        var fontBoundary = TooltipTextEditor.apply("机", new TooltipTextEditor.Selection(1, 0), "[字体=" + longestFont + "]");
        check(containing(fontBoundary.text(), "机").font().equals(longestFont), "The font picker maximum fits the inline parser boundary exactly");
        fails(() -> TooltipTextEditor.apply("机", new TooltipTextEditor.Selection(1, 0), "[字体=" + longestFont + "f]"), "Longer font markers cannot be silently accepted as plain text");
        fails(() -> TooltipTextEditor.apply("字".repeat(8192), new TooltipTextEditor.Selection(1, 0), "[动态彩虹]"), "Oversize formatting is rejected before vanilla can truncate the draft");
        check(visible(TooltipTextEditor.apply("前[[白色][未知]机后", new TooltipTextEditor.Selection(12, 11), "[斜体]").text())
                .equals("前[白色][未知]机后"), "Literal bracket escapes and unknown markers remain visible after editing");
    }
    private static void lineDrafts() {
        var translated = JsonParser.parseString("""
                {"translate":"tooltip.key","fallback":"Fallback %s","with":[{"text":"参数","italic":true}],
                 "extra":[{"text":"附注"}],"color":"aqua"}
                """);
        var source = new Line(translated, Visibility.SHIFT, false);
        var baseline = source.component().deepCopy();
        var draft = new TooltipLineDraft(source, "Fallback 参数附注");
        check(!draft.dirty() && draft.line().equals(source), "Opening a translated rich line is not an edit");
        draft.text(draft.text());
        check(draft.line().component().equals(baseline), "Initial responder values preserve translate, with, fallback and extra");
        draft.color("red");
        check(draft.dirty() && draft.line().component().getAsJsonObject().has("translate"), "Only changing color counts as a draft and retains translation data");
        draft.color("aqua"); check(!draft.dirty(), "Restoring the initial color clears dirty state");
        draft.font("minecraft:uniform"); check(draft.dirty(), "Font-only changes are tracked");
        draft.font(""); draft.rainbow(true); check(draft.dirty(), "Rainbow-only changes are tracked");
        draft.rainbow(false); draft.visibility(Visibility.ALWAYS); check(draft.dirty(), "Visibility-only changes are tracked");
        draft.visibility(Visibility.SHIFT); draft.style("bold", true); draft.style("bold", !draft.style("bold"));
        check(!draft.dirty(), "Whole-line styles can be toggled on and off repeatedly");
        draft.text("重新编辑");
        var flattened = draft.line().component().getAsJsonObject();
        check(flattened.get("text").getAsString().equals("重新编辑") && !flattened.has("translate") && !flattened.has("extra"), "Editing flattened translation text does not duplicate its former suffix");
        draft.text("Fallback 参数附注");
        check(!draft.dirty() && draft.line().equals(source), "Reverting visible text restores the original rich component");
        draft.text("取消内容"); draft.color("#654321"); draft.rainbow(true);
        check(source.component().equals(baseline) && !source.rainbow() && source.visibility() == Visibility.SHIFT, "Discarding a modified draft leaves the original entry untouched");
        var primitive = new Line(new JsonPrimitive("原文"), Visibility.ALWAYS, false);
        var simple = new TooltipLineDraft(primitive, "原文");
        check(simple.line().equals(primitive), "Unedited primitive components retain their exact shape");
        simple.text("新文"); check(simple.line().component().getAsJsonObject().get("text").getAsString().equals("新文"), "Explicit primitive text edits do not retain a duplicate old extra");
    }
    private static void paletteAndLayout() {
        check(TooltipMarkup.palette().size() == 16, "All sixteen Minecraft color presets are selectable");
        for (var preset : TooltipMarkup.palette()) {
            check(TooltipMarkup.color(preset.id()) == preset.rgb() && TooltipMarkup.color(preset.tag()) == preset.rgb(), "Chinese labels and existing English colors address the same palette");
            check(TooltipColorInput.hex(TooltipMarkup.hex(preset.rgb())).orElseThrow() == preset.rgb(), "Every swatch round-trips through the custom color input");
        }
        check(TooltipColorInput.hex("12aBeF").orElseThrow() == 0x12abef && TooltipColorInput.hex("#000000").orElseThrow() == 0, "HEX accepts either case and black without a required hash");
        check(TooltipColorInput.rgb("0", "0", "0").orElseThrow() == 0
                && TooltipColorInput.rgb("255", "255", "255").orElseThrow() == 0xffffff, "RGB picker supports both endpoints of every channel");
        for (String bad : List.of("", "#", "#FFF", "#GGGGGG", "#FFFFFFF", "white"))
            check(TooltipColorInput.hex(bad).isEmpty(), "Partial/invalid HEX remains an unapplied draft");
        for (String bad : List.of("", "-1", "256", "1.5", "x", "1000"))
            check(TooltipColorInput.rgb("1", bad, "2").isEmpty(), "RGB cannot silently wrap or coerce an invalid channel");
        for (int w : List.of(296, 384, 408, 500, 640)) for (int h : List.of(240, 260, 360, 720)) {
            var layout = TooltipEditorLayout.create(w, h);
            for (var rect : layout.toolbar()) check(rect.x() >= 0 && rect.x() + rect.width() <= w
                    && rect.y() + rect.height() < layout.textY(), "Toolbar wraps within the available width");
            check(layout.textY() + layout.textHeight() + 13 <= layout.controlsY(), "Text and character count stay clear of lower controls");
            check(layout.controlsY() + 20 < layout.previewY() && layout.previewY() + 19 < h - 43
                    && layout.saveY() + 20 <= h, "Preview, status and save controls remain separate at all GUI sizes");
        }
    }
    private static void fileEdits() throws Exception {
        Path dir = Files.createTempDirectory("enhancedcore-config-regression-");
        Path target = dir.resolve("tips.json");
        try {
            var session = new EditableConfigFile(target);
            check(!Files.exists(target), "Opening an absent document does not create it");
            session.save("中文 / original");
            var competing = new EditableConfigFile(target);
            session.save("new value");
            try { competing.save("stale edit"); throw new AssertionError("Expected conflict"); }
            catch (IOException expected) { assertions++; }
            check(Files.readString(target).equals("new value"), "External change survives stale editor save");
            competing.reload(); competing.save("updated");
            check(Files.readString(target).equals("updated"), "Explicit reload permits a fresh edit");
            try (var files = Files.list(dir)) { check(files.count() == 1, "Atomic save leaves no temporary files"); }
        } finally { Files.deleteIfExists(target); Files.deleteIfExists(dir); }
    }
    private static void fails(Runnable action, String message) {
        try { action.run(); } catch (RuntimeException expected) { assertions++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
