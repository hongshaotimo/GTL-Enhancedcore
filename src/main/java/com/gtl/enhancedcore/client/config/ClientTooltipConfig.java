package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.config.EditableConfigFile;
import com.gtl.enhancedcore.common.config.TooltipOverrides;
import com.gtl.enhancedcore.common.config.TooltipMarkup;
import com.gtl.enhancedcore.common.config.TooltipOverrides.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class ClientTooltipConfig {
    private static TooltipOverrides config = new TooltipOverrides(Map.of());
    private static EditableConfigFile file;
    private static String error = "";
    private static final ThreadLocal<Boolean> ORIGINAL = ThreadLocal.withInitial(() -> false);

    private ClientTooltipConfig() {}
    public static List<String> itemIds() {
        return ForgeRegistries.ITEMS.getKeys().stream().filter(id -> id.getNamespace().equals(GTLEnhancedcore.MOD_ID))
                .map(Object::toString).sorted().toList();
    }
    public static Entry entry(String id) { return config.entry(id); }
    public static String error() { return error; }
    public static void reload() throws IOException {
        var candidate = new EditableConfigFile(FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore/item_tooltips.json"));
        var parsed = candidate.exists() ? TooltipOverrides.parse(candidate.text()) : new TooltipOverrides(Map.of());
        validate(parsed);
        var complete = parsed.include(itemIds());
        if (!candidate.exists() || !parsed.items().keySet().equals(complete.items().keySet())) candidate.save(complete.serialize());
        config = complete; file = candidate; error = "";
        PARSED.clear();
    }
    public static void initialize() {
        try { reload(); }
        catch (IOException | RuntimeException ex) {
            error = ex.getMessage();
            GTLEnhancedcore.LOGGER.error("Cannot load item_tooltips.json; retaining last valid tooltips", ex);
        }
    }
    public static void save(String id, Entry entry) throws IOException {
        if (file == null || !error.isEmpty()) reload();
        var updated = TooltipOverrides.parse(config.with(id, entry).serialize());
        validate(updated);
        file.save(updated.serialize());
        config = updated;
        PARSED.clear();
    }
    private static void validate(TooltipOverrides candidate) {
        for (Entry entry : candidate.items().values()) for (Line line : entry.lines()) {
            if (Component.Serializer.fromJson(line.component()) == null) throw new IllegalArgumentException("Invalid component");
        }
    }
    public static List<Component> original(ItemStack stack) {
        boolean previous = ORIGINAL.get(); ORIGINAL.set(true);
        try { return stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL); }
        finally { ORIGINAL.set(previous); }
    }
    /**
     * Rows this module renders with a live rainbow. Used when snapshotting a built-in tip so an
     * animated row does not silently become static text in the custom entry.
     */
    public static boolean isAnimated(Component component) {
        return containsTranslatable(component, com.gtl.enhancedcore.common.util.TooltipPolicy.CROSS_RECIPE_KEY)
                || containsTranslatable(component, com.gtl.enhancedcore.common.util.TooltipPolicy.STAR_CROSS_RECIPE_KEY)
                || containsTranslatable(component, com.gtl.enhancedcore.common.util.TooltipPolicy.SOURCE_KEY)
                || containsTranslatable(component, com.gtl.enhancedcore.common.util.TooltipPolicy.CREDIT_KEY)
                || containsTranslatable(component, com.gtl.enhancedcore.common.util.TooltipPolicy.GALAXY_ALLOWED_KEY);
    }
    private static boolean containsTranslatable(Component component, String key) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents) {
            if (contents.getKey().equals(key)) return true;
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component nested && containsTranslatable(nested, key)) return true;
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> containsTranslatable(sibling, key));
    }
    /**
     * The tooltip rows this module would add for {@code stack}, with the user's saved overrides
     * suppressed. The preview needs the untouched built-in rows; {@link #original} alone returns the
     * already-overridden list because the item tooltip event runs on the way out.
     */
    public static List<Component> builtIn(ItemStack stack) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return original(stack);
        var saved = config;
        config = new TooltipOverrides(Map.of());
        try { return original(stack); }
        finally { config = saved; }
    }
    /**
     * 悬停时提示框每帧都会重建；行 JSON 只在配置变化时改变，因此按原文缓存解析结果。
     * 配置重载/保存时整体清空，避免旧条目长期驻留。
     */
    private static final Map<String, Optional<Component>> PARSED = new HashMap<>();

    private static Component parsed(String json) {
        return PARSED.computeIfAbsent(json, key -> Optional.ofNullable(Component.Serializer.fromJson(key))).orElse(null);
    }

    public static List<Component> render(Entry entry, boolean shift) {
        var result = new ArrayList<Component>();
        for (Line line : entry.lines()) {
            if (!line.visible(shift)) continue;
            Component component = parsed(line.component().toString());
            if (component != null) result.add(styledText(component, line.rainbow()));
        }
        return result;
    }
    private static Component styledText(Component input, boolean defaultRainbow) {
        var output = Component.empty();
        int[] index = {0};
        float phase = (System.currentTimeMillis() % 6000) / 6000.0f;
        input.visit((style, text) -> {
            for (var span : TooltipMarkup.parse(text)) {
                Style local = spanStyle(style, span);
                if (span.isRainbow(defaultRainbow)) {
                    span.text().codePoints().forEach(point -> {
                        int color = net.minecraft.util.Mth.hsvToRgb((phase + index[0]++ * 0.035f) % 1.0f, 0.75f, 1.0f);
                        output.append(Component.literal(new String(Character.toChars(point))).setStyle(local.withColor(color)));
                    });
                } else {
                    output.append(Component.literal(span.text()).setStyle(local));
                    index[0] += span.text().codePointCount(0, span.text().length());
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return output;
    }
    private static Style spanStyle(Style inherited, TooltipMarkup.Span span) {
        Style style = inherited;
        if (span.color() != null) style = style.withColor(span.color());
        if (span.font() != null) style = style.withFont(new net.minecraft.resources.ResourceLocation(span.font()));
        for (var entry : span.styles().entrySet()) {
            style = switch (entry.getKey()) {
                case "bold" -> style.withBold(entry.getValue());
                case "italic" -> style.withItalic(entry.getValue());
                case "underlined" -> style.withUnderlined(entry.getValue());
                case "strikethrough" -> style.withStrikethrough(entry.getValue());
                case "obfuscated" -> style.withObfuscated(entry.getValue());
                default -> style;
            };
        }
        return style;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event) {
        if (ORIGINAL.get()) return;
        var id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null || !id.getNamespace().equals(GTLEnhancedcore.MOD_ID)) return;
        Entry entry = config.entry(id.toString());
        if (entry.mode() == Mode.DEFAULT) return;
        var lines = event.getToolTip();
        var replacement = TooltipOverrides.compose(lines, render(entry, Screen.hasShiftDown()), entry.mode());
        lines.clear(); lines.addAll(replacement);
    }
}
