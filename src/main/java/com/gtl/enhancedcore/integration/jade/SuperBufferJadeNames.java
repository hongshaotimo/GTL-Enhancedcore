package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferAutoName;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferChineseNames;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNameAccess;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNaming;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public final class SuperBufferJadeNames {
    public static final int MAX_NAME_LENGTH = 4096;
    public static final int MAX_KEY_LENGTH = 256;

    private SuperBufferJadeNames() {}

    public record NameSnapshot(String stored, String translationKey) {
        public NameSnapshot {
            stored = stored == null || stored.length() > MAX_NAME_LENGTH ? "" : stored;
            translationKey = stored.isEmpty() || !isTranslationKey(translationKey) ? "" : translationKey;
        }

        public Component display() {
            return translationKey.isEmpty() ? Component.literal(stored)
                    : Component.translatableWithFallback(translationKey, stored);
        }

        public void write(CompoundTag data) {
            data.putString("name", stored);
            data.putString("nameKey", translationKey);
        }
    }

    public static NameSnapshot current(MEPatternBufferPartMachine buffer) {
        String stored = buffer.getCustomName();
        if (!((Object) buffer instanceof SuperBufferNameAccess names)) return new NameSnapshot(stored, "");
        String automatic = names.enhanced$getAutomaticName();
        boolean manual = names.enhanced$isManualName();
        if (stored == null || stored.length() > MAX_NAME_LENGTH
                || !SuperBufferAutoName.shouldTranslate(stored, automatic, manual) || isTranslationKey(stored)) {
            return select(stored, automatic, manual, "", "");
        }
        String key = SuperBufferNaming.automaticKey(buffer);
        if (!stored.equals(SuperBufferChineseNames.resolve(key))) key = uniqueStoredKey(stored);
        return select(stored, automatic, manual, key, SuperBufferChineseNames.resolve(key));
    }

    public static NameSnapshot select(String stored, String automatic, boolean manual,
                                      String candidateKey, String candidateName) {
        var literal = new NameSnapshot(stored, "");
        if (!SuperBufferAutoName.shouldTranslate(literal.stored(), automatic, manual)) return literal;
        if (isTranslationKey(literal.stored())) return new NameSnapshot(literal.stored(), literal.stored());
        return isTranslationKey(candidateKey) && literal.stored().equals(candidateName)
                ? new NameSnapshot(literal.stored(), candidateKey) : literal;
    }

    public static NameSnapshot read(CompoundTag data) {
        if (!data.contains("name", Tag.TAG_STRING)) return null;
        String key = data.contains("nameKey", Tag.TAG_STRING) ? data.getString("nameKey") : "";
        return new NameSnapshot(data.getString("name"), key);
    }

    private static String uniqueStoredKey(String stored) {
        String unique = "";
        for (var type : GTRegistries.RECIPE_TYPES.values()) {
            if (type == null || type.registryName == null) continue;
            String key = type.registryName.toLanguageKey();
            if (!isTranslationKey(key) || !SuperBufferChineseNames.hasTranslation(key)
                    || !stored.equals(SuperBufferChineseNames.resolve(key))) continue;
            if (!unique.isEmpty() && !unique.equals(key)) return "";
            unique = key;
        }
        return unique;
    }

    private static boolean isTranslationKey(String value) {
        return value != null && value.length() <= MAX_KEY_LENGTH
                && value.matches("[a-z0-9_.-]+\\.[a-z0-9_./-]+");
    }
}
