package com.gtl.enhancedcore.client;

import com.gtl.enhancedcore.common.item.PatternGeneratorPresetFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

/** Accessed only by client menu actions; server packets never choose filesystem paths. */
public final class PatternGeneratorPresets {
    private PatternGeneratorPresets() {}
    public static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/GTL-Enhancedcore/pattern-generator-presets");
    }
    public static PatternGeneratorPresetFiles.Listing list() throws IOException {
        return PatternGeneratorPresetFiles.list(directory());
    }
    public static void save(PatternGeneratorPresetFiles.Preset preset) throws IOException {
        PatternGeneratorPresetFiles.save(directory(), preset);
    }
    public static void openDirectory() throws IOException {
        Files.createDirectories(directory());
        Util.getPlatform().openFile(directory().toFile());
    }
}
