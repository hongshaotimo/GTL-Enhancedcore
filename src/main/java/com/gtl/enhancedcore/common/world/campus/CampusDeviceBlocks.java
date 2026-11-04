package com.gtl.enhancedcore.common.world.campus;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** Campus-only planning devices, registered on this mod's normal Registrate lifecycle. */
public final class CampusDeviceBlocks {
    public static BlockEntry<VirtualMarkerBlock> VIRTUAL_MARKER;
    public static BlockEntry<CentralTeleporterBlock> CENTRAL_TELEPORTER;
    public static BlockEntityEntry<VirtualMarkerBlockEntity> VIRTUAL_MARKER_BLOCK_ENTITY;
    public static BlockEntityEntry<CentralTeleporterBlockEntity> CENTRAL_TELEPORTER_BLOCK_ENTITY;

    private CampusDeviceBlocks() {}

    /** Call after REGISTRATE.registerEventListeners, before Forge's registry events. */
    public static void init() {
        if (VIRTUAL_MARKER != null) return;
        VIRTUAL_MARKER = GTLEnhancedcore.REGISTRATE.block("virtual_marker", VirtualMarkerBlock::new)
                .properties(properties -> properties.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.0F)
                        .sound(SoundType.GLASS).noCollission().noOcclusion().replaceable())
                .blockstate(NonNullBiConsumer.noop())
                .loot(NonNullBiConsumer.noop())
                .item().model(NonNullBiConsumer.noop()).build()
                .register();
        CENTRAL_TELEPORTER = GTLEnhancedcore.REGISTRATE.block("central_teleporter", CentralTeleporterBlock::new)
                .properties(properties -> properties.mapColor(MapColor.COLOR_CYAN).strength(1.5F, 6.0F)
                        .sound(SoundType.METAL).noOcclusion())
                .blockstate(NonNullBiConsumer.noop())
                .loot(NonNullBiConsumer.noop())
                .item().model(NonNullBiConsumer.noop()).build()
                .register();
        VIRTUAL_MARKER_BLOCK_ENTITY = GTLEnhancedcore.REGISTRATE
                .blockEntity("virtual_marker", VirtualMarkerBlockEntity::new)
                .validBlocks(VIRTUAL_MARKER).register();
        CENTRAL_TELEPORTER_BLOCK_ENTITY = GTLEnhancedcore.REGISTRATE
                .blockEntity("central_teleporter", CentralTeleporterBlockEntity::new)
                .validBlocks(CENTRAL_TELEPORTER).register();
    }
}
