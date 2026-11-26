package com.gtl.enhancedcore.common.structure;

import com.google.common.base.Suppliers;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
import com.gtl.enhancedcore.common.registration.MachineRegistrationSupport;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit panel materials, independent of which blocks are allowed in empty hatch slots. */
public final class StructurePanelAppearance {
    private static final Map<ResourceLocation, Supplier<BlockState>> PANELS = load();

    private static final Map<ResourceLocation, Supplier<BlockState>> HATCHES = GtlMegastructurePatterns.hatchCasings();

    private StructurePanelAppearance() {}

    private static Map<ResourceLocation, Supplier<BlockState>> load() {
        String path = "/data/gtl_enhancedcore/structures/panel_appearance.json";
        try (var input = StructurePanelAppearance.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing structure panel palette: " + path);
            var entries = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var panels = new HashMap<ResourceLocation, Supplier<BlockState>>();
            for (var entry : entries.entrySet()) {
                String block = entry.getValue().getAsString();
                panels.put(new ResourceLocation(entry.getKey()),
                        Suppliers.memoize(() -> MachineRegistrationSupport.requiredBlockBlock(block).defaultBlockState()));
            }
            return Map.copyOf(panels);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static Supplier<BlockState> panel(ResourceLocation id) {
        if (AddStructureOwnership.owns(id)) return null;
        if (!id.getNamespace().equals("gtl_enhancedcore")
                && !MachineOverrideConfig.current().replaceOriginalStructures()) return null;
        return PANELS.get(id);
    }

    public static Supplier<BlockState> hatchPanel(ResourceLocation id) {
        if (id.getNamespace().equals("gtl_enhancedcore")) return PANELS.get(id);
        return MachineOverrideConfig.current().replaceOriginalStructures() ? HATCHES.get(id) : null;
    }

    /** Run after the builder has installed both its appearance and optional part-appearance function. */
    public static void configure(MultiblockMachineDefinition definition) {
        var panel = panel(definition.getId());
        if (panel != null) definition.setAppearance(panel);
        var hatch = hatchPanel(definition.getId());
        if (hatch != null) definition.setPartAppearance((controller, part, side) -> hatch.get());
    }
}
