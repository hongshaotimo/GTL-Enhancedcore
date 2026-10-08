package com.gtl.enhancedcore.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.PinnedKeys;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.config.ConfigFiles;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * AE 终端手动置顶的跨重启持久化：置顶列表保存到
 * {@code config/GTL-Enhancedcore/ae_pins.json}（客户端本地），进档/换维度时恢复。
 * 键用 AE2 通用序列化（AEKey.toTagGeneric / fromTagGeneric，覆盖物品/流体等全部键类型），
 * 经 NbtOps ↔ JsonOps 转 JSON 存储。
 */
public final class AEPinnedKeysStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("GTL-Enhancedcore").resolve("ae_pins.json");

    private AEPinnedKeysStore() {
    }

    /** 保存当前手动置顶列表（用户每次置顶/取消时调用）。 */
    public static void save(List<AEKey> keys) {
        try {
            JsonArray array = new JsonArray();
            for (AEKey key : keys) {
                if (key == null) {
                    continue;
                }
                CompoundTag tag = key.toTagGeneric();
                array.add(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag));
            }
            Files.createDirectories(FILE.getParent());
            ConfigFiles.writeAtomically(FILE, GSON.toJson(array));
        } catch (IOException | RuntimeException ex) {
            GTLEnhancedcore.LOGGER.debug("Unable to save AE pinned keys", ex);
        }
    }

    /** 读取持久化的手动置顶列表（损坏条目跳过）。 */
    public static List<AEKey> load() {
        List<AEKey> keys = new ArrayList<>();
        try {
            if (!Files.exists(FILE)) {
                return keys;
            }
            JsonArray array = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement element : array) {
                try {
                    CompoundTag tag = (CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, element);
                    AEKey key = AEKey.fromTagGeneric(tag);
                    if (key != null && !keys.contains(key)) keys.add(key);
                } catch (RuntimeException ex) {
                    GTLEnhancedcore.LOGGER.debug("Skipping unreadable AE pinned key entry", ex);
                }
            }
        } catch (IOException | RuntimeException ex) {
            GTLEnhancedcore.LOGGER.debug("Unable to load AE pinned keys", ex);
        }
        return keys;
    }

    /** 进档时恢复手动置顶（超出上限由 PinnedKeys.pinKey 淘汰逻辑处理，随后回写修正文件）。 */
    public static void restore() {
        List<AEKey> keys = load();
        if (keys.isEmpty()) {
            return;
        }
        for (AEKey key : keys) {
            PinnedKeys.pinKey(key, PinnedKeys.PinReason.CRAFTING);
            PinnedKeysTracker.mark(key);
        }
        save(PinnedKeysTracker.snapshot());
    }
}
