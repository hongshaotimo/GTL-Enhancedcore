package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNameAccess;
import com.gtl.enhancedcore.integration.jade.SuperBufferJadeNames;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

public final class SuperBufferJadeNameChecks {
    private static final String DATA_KEY = "gtlEnhancedcoreIvBuffer";
    private static final String NAME_KEY = "gtlEnhancedcoreSuperBufferName";
    private static int assertions;

    private SuperBufferJadeNameChecks() {}

    public static int run() throws ReflectiveOperationException {
        int previousAssertions = assertions;
        Language previousLanguage = Language.getInstance();
        try {
            var automatic = SuperBufferJadeNames.select("压缩机", "压缩机", false, "gtceu.compressor", "压缩机");
            var encoded = new CompoundTag();
            automatic.write(encoded);
            var received = SuperBufferJadeNames.read(encoded.copy());
            check(automatic.equals(received), "Name fields did not round-trip");
            for (var locale : Map.of("en_us", "Compressor", "zh_cn", "压缩机").entrySet()) {
                Language.inject(language(Map.of("gtceu.compressor", locale.getValue())));
                check(received.display().getString().equals(locale.getValue()), "Wrong automatic language: " + locale.getKey());
                check(Component.Serializer.toJson(received.display()).contains("\"translate\":\"gtceu.compressor\""),
                        "The server response solidified a translated name");
                check(encoded.getString("name").equals("压缩机"), "Client rendering changed the saved fallback");
                var legacy = SuperBufferJadeNames.select("gtceu.compressor", "gtceu.compressor", false, "", "");
                check(legacy.display().getString().equals(locale.getValue()), "Proven legacy key did not translate");
                for (String manual : new String[]{"我的总成", "Compressor", "gtceu.compressor", "", " ", "{broken"}) {
                    var literal = SuperBufferJadeNames.select(manual, manual, true, "gtceu.compressor", "压缩机");
                    check(literal.display().getString().equals(manual), "Manual or empty name changed");
                    check(!Component.Serializer.toJson(literal.display()).contains("\"translate\""),
                            "A manual name became a translation key");
                }
                var unowned = SuperBufferJadeNames.select("gtceu.compressor", "", false, "gtceu.compressor", "压缩机");
                check(unowned.display().getString().equals("gtceu.compressor"), "Missing ownership was inferred from old text");
            }
            Language.inject(language(Map.of()));
            check(received.display().getString().equals("压缩机"), "Missing resources must keep the actual saved fallback");
            check(SuperBufferJadeNames.read(new CompoundTag()) == null, "Legacy job-only data synthesized a name");
            var invalid = new CompoundTag();
            invalid.putInt("name", 7);
            check(SuperBufferJadeNames.read(invalid) == null, "A non-string name reached the title");
            invalid.putString("name", "我的总成");
            invalid.putInt("nameKey", 7);
            check(SuperBufferJadeNames.read(invalid).display().getString().equals("我的总成"),
                    "An invalid key must not erase a valid literal name");
            invalid.putString("nameKey", "gtceu:compressor");
            check(SuperBufferJadeNames.read(invalid).translationKey().isEmpty(), "A registry ID was accepted as a language key");
            invalid.putString("name", "");
            invalid.putString("nameKey", "gtceu.compressor");
            check(SuperBufferJadeNames.read(invalid).display().getString().isEmpty(), "Bad metadata filled an explicit empty name");
            invalid.putString("name", "长".repeat(SuperBufferJadeNames.MAX_NAME_LENGTH + 1));
            check(SuperBufferJadeNames.read(invalid).stored().isEmpty(), "Oversized metadata must not replace the normal title");
        } finally {
            Language.inject(previousLanguage);
        }
        legacyResponses();
        return assertions - previousAssertions;
    }

    public static int inspectAutomatic(Object candidate, String expectedKey) throws ReflectiveOperationException {
        if (!(candidate instanceof MESuperPatternBufferPartMachine buffer)) {
            throw new IllegalArgumentException("A prepared super pattern buffer fixture is required");
        }
        int previousAssertions = assertions;
        check(expectedKey != null && !expectedKey.isEmpty(), "An expected recipe-type key is required");
        check(expectedKey.equals(SuperBufferJadeNames.current(buffer).translationKey()),
                "The actual automatic name did not retain its recipe-type translation key");
        inspect(buffer);
        return assertions - previousAssertions;
    }

    public static int inspect(Object candidate) throws ReflectiveOperationException {
        if (!(candidate instanceof MESuperPatternBufferPartMachine buffer)) {
            throw new IllegalArgumentException("A prepared super pattern buffer fixture is required");
        }
        int previousAssertions = assertions;
        var names = (Object) buffer instanceof SuperBufferNameAccess access ? access : null;
        String storedBefore = buffer.getCustomName();
        String automaticBefore = names == null ? null : names.enhanced$getAutomaticName();
        boolean manualBefore = names != null && names.enhanced$isManualName();
        var state = IvBuffers.state(buffer);
        var ordersBefore = state == null ? null : state.save();
        String messageBefore = state == null ? null : state.message;
        boolean refreshBefore = state != null && state.refreshNeeded;
        boolean isolated = IvBuffers.isolated(buffer);
        var expected = SuperBufferJadeNames.current(buffer);
        var data = new CompoundTag();
        data.putString(DATA_KEY, "stale");
        data.putString(NAME_KEY, "stale");
        Class<?> accessorType = Class.forName("snownee.jade.api.BlockAccessor");
        Class<?> providerType = Class.forName("com.gtl.enhancedcore.integration.jade.IvBufferInfoProvider");
        Object provider = providerType.getConstructor().newInstance();
        var append = providerType.getMethod("appendServerData", CompoundTag.class, accessorType);
        Object accessor = accessor(accessorType, buffer, data, false);
        for (int readIndex = 0; readIndex < 8; readIndex++) {
            append.invoke(provider, data, accessor);
            check(data.contains(NAME_KEY, Tag.TAG_COMPOUND), "A super buffer has no Jade name response outside isolation");
            check(expected.equals(SuperBufferJadeNames.read(data.getCompound(NAME_KEY))),
                    "The real provider sent a different name snapshot");
            check(data.contains(DATA_KEY, Tag.TAG_COMPOUND) == isolated,
                    "The legacy status key escaped its isolation scope");
            if (isolated) {
                var tag = data.getCompound(DATA_KEY);
                check(tag.contains("jobs", Tag.TAG_INT) && tag.contains("accepting", Tag.TAG_BYTE),
                        "The existing order fields changed NBT types");
                check(tag.getInt("jobs") == state.jobs.size(), "A Jade read changed the displayed order count");
                check(tag.getBoolean("accepting") == state.accepting, "A Jade read changed the accepting state");
            }
        }
        if (manualBefore || storedBefore == null || storedBefore.isEmpty()) {
            check(expected.translationKey().isEmpty(), "Manual or empty names received an automatic key");
        }
        check(Objects.equals(storedBefore, buffer.getCustomName()), "Jade renamed the buffer");
        check(names == null || Objects.equals(automaticBefore, names.enhanced$getAutomaticName()), "Jade changed automatic ownership");
        check(names == null || manualBefore == names.enhanced$isManualName(), "Jade changed manual ownership");
        check(state == null || ordersBefore.equals(state.save()), "Jade changed persisted orders or accepting status");
        check(state == null || Objects.equals(messageBefore, state.message), "Jade changed the order rejection reason");
        check(state == null || refreshBefore == state.refreshNeeded, "Jade changed the order refresh state");
        check((int) providerType.getMethod("getDefaultPriority").invoke(provider) == 10000, "Wrong title provider priority");
        append.invoke(provider, data, accessor(accessorType, buffer, data, true));
        check(!data.contains(DATA_KEY) && !data.contains(NAME_KEY), "A reused response retained the previous buffer data");
        return assertions - previousAssertions;
    }

    private static void legacyResponses() throws ReflectiveOperationException {
        Class<?> accessorType = Class.forName("snownee.jade.api.BlockAccessor");
        Class<?> tooltipType = Class.forName("snownee.jade.api.ITooltip");
        Class<?> configType = Class.forName("snownee.jade.api.config.IPluginConfig");
        Class<?> providerType = Class.forName("com.gtl.enhancedcore.integration.jade.IvBufferInfoProvider");
        Object provider = providerType.getConstructor().newInstance();
        var append = providerType.getMethod("appendTooltip", tooltipType, accessorType, configType);
        var data = new CompoundTag();
        var received = new ArrayList<Component>();
        var removedTitle = new AtomicBoolean();
        Object accessor = Proxy.newProxyInstance(accessorType.getClassLoader(), new Class<?>[]{accessorType},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getServerData")) return data;
                    throw new IllegalStateException("Unexpected Jade accessor method: " + method.getName());
                });
        Object tooltip = Proxy.newProxyInstance(tooltipType.getClassLoader(), new Class<?>[]{tooltipType},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "add" -> {
                        if (arguments.length != 1 || !(arguments[0] instanceof Component component)) {
                            throw new IllegalStateException("Unexpected Jade add overload");
                        }
                        received.add(component);
                        yield null;
                    }
                    case "get" -> List.of();
                    case "remove" -> {
                        removedTitle.set(true);
                        yield null;
                    }
                    case "size" -> received.size();
                    case "isEmpty" -> received.isEmpty();
                    default -> throw new IllegalStateException("Unexpected Jade tooltip method: " + method.getName());
                });
        var legacy = new CompoundTag();
        legacy.putInt("jobs", 2);
        legacy.putBoolean("accepting", true);
        data.put(DATA_KEY, legacy);
        append.invoke(provider, tooltip, accessor, null);
        check(received.size() == 3, "Legacy job-only responses lost the existing status lines");
        check(Component.Serializer.toJson(received.getFirst()).contains("gtl_enhancedcore.gui.iv_buffer_count"),
                "Legacy order-count translation changed");
        check(!removedTitle.get(), "Legacy data removed the normal object title");
        var invalidName = new CompoundTag();
        invalidName.putInt("name", 7);
        data.put(NAME_KEY, invalidName);
        received.clear();
        append.invoke(provider, tooltip, accessor, null);
        check(received.size() == 3 && !removedTitle.get(), "An invalid name damaged valid legacy order information");
        data.remove(NAME_KEY);
        legacy.putInt("jobs", -1);
        received.clear();
        append.invoke(provider, tooltip, accessor, null);
        check(received.isEmpty(), "Negative order counts escaped into Jade");
        legacy.putString("jobs", "2");
        append.invoke(provider, tooltip, accessor, null);
        check(received.isEmpty(), "Wrong order-count types escaped into Jade");
        legacy.putInt("jobs", 2);
        for (int badBoolean : new int[]{-1, 2}) {
            legacy.putByte("accepting", (byte) badBoolean);
            append.invoke(provider, tooltip, accessor, null);
            check(received.isEmpty(), "An invalid accepting byte escaped into Jade");
        }
        legacy.putInt("accepting", 1);
        append.invoke(provider, tooltip, accessor, null);
        check(received.isEmpty(), "A non-byte accepting flag escaped into Jade");
        var empty = new CompoundTag();
        empty.putString("name", "");
        empty.putString("nameKey", "gtceu.compressor");
        data.remove(DATA_KEY);
        data.put(NAME_KEY, empty);
        append.invoke(provider, tooltip, accessor, null);
        check(received.isEmpty() && !removedTitle.get(), "An empty name created a fake title or isolation status");
        empty.putString("name", "压缩机");
        append.invoke(provider, tooltip, accessor, null);
        check(received.isEmpty() && !removedTitle.get(), "A name re-enabled a disabled Jade object title");
        data.putString(DATA_KEY, "invalid");
        data.putString(NAME_KEY, "invalid");
        append.invoke(provider, tooltip, accessor, null);
        check(received.isEmpty(), "A non-compound root response reached Jade");
        data.remove(DATA_KEY);
        data.remove(NAME_KEY);
        append.invoke(provider, tooltip, accessor, null);
        check(received.isEmpty(), "Missing server data synthesized an overlay");
    }

    private static Object accessor(Class<?> accessorType, MESuperPatternBufferPartMachine buffer,
                                   CompoundTag data, boolean emptyBlock) {
        return Proxy.newProxyInstance(accessorType.getClassLoader(), new Class<?>[]{accessorType},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getBlockEntity" -> emptyBlock ? null : buffer.getLevel().getBlockEntity(buffer.getPos());
                    case "getServerData" -> data;
                    case "getLevel" -> buffer.getLevel();
                    case "getPosition" -> buffer.getPos();
                    case "toString" -> "SuperBufferJadeNameAuditAccessor";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new IllegalStateException("Unexpected Jade accessor method: " + method.getName());
                });
    }

    private static Language language(Map<String, String> translations) {
        return new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return translations.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return translations.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.EMPTY;
            }
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
