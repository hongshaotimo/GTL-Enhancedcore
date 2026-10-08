package com.gtl.enhancedcore.common.event;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Migrates the old joint-factory registration name while keeping the four Plus IDs alive. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class RetiredFactoryMappings {
    private RetiredFactoryMappings() {}

    @SubscribeEvent
    public static void remap(MissingMappingsEvent event) {
        var factory = GTLEnhancedcoreMachines.getIntegratedUniversalFactory();
        if (factory == null) return;
        for (var mapping : event.getMappings(Registries.ITEM, GTLEnhancedcore.MOD_ID)) {
            if (mapping.getKey().getPath().equals("integrated_universal_factory")) mapping.remap(factory.getItem());
        }
        for (var mapping : event.getMappings(Registries.BLOCK, GTLEnhancedcore.MOD_ID)) {
            if (mapping.getKey().getPath().equals("integrated_universal_factory")) mapping.remap(factory.getBlock());
        }
        for (var mapping : event.getMappings(Registries.BLOCK_ENTITY_TYPE, GTLEnhancedcore.MOD_ID)) {
            if (mapping.getKey().getPath().equals("integrated_universal_factory")) mapping.remap(factory.getBlockEntityType());
        }
    }
}
