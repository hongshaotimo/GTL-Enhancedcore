package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.tterrag.registrate.builders.Builder;
import com.tterrag.registrate.builders.BuilderCallback;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateItemModelProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegistryObject;

/** Removes the old registration, retaining only a lazy Java-field alias and save migration. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class RetiredDebugPatternTool {
    public static final ResourceLocation OLD_ID = new ResourceLocation("gtlcore", "debug_pattern_test");
    private static final ResourceLocation REPLACEMENT = new ResourceLocation("gtl_enhancedcore", "pattern_generator");

    private RetiredDebugPatternTool() {}

    public static ItemBuilder<ComponentItem, GTRegistrate> replacementBuilder(GTRegistrate owner, String name) {
        if (!owner.getModid().equals(OLD_ID.getNamespace()) || !name.equals(OLD_ID.getPath())) {
            throw new IllegalArgumentException("Unexpected retired item registration: " + owner.getModid() + ":" + name);
        }
        return new AliasBuilder(owner, name);
    }

    @SubscribeEvent
    public static void migrate(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(Registries.ITEM, OLD_ID.getNamespace())) {
            if (mapping.getKey().equals(OLD_ID)) mapping.remap(GTLEnhancedcoreItems.getPatternGenerator());
        }
    }

    private static final class AliasBuilder extends ItemBuilder<ComponentItem, GTRegistrate> {
        AliasBuilder(GTRegistrate owner, String name) {
            super(owner, owner, name, new BuilderCallback() {
                @Override
                public <R, T extends R> RegistryEntry<T> accept(String ignored, ResourceKey<? extends Registry<R>> key,
                        Builder<R, T, ?, ?> builder, NonNullSupplier<? extends T> creator,
                        NonNullFunction<RegistryObject<T>, ? extends RegistryEntry<T>> wrapper) {
                    throw new IllegalStateException("Retired debug tool must not be registered");
                }
            }, ComponentItem::create);
        }

        @Override
        public ItemBuilder<ComponentItem, GTRegistrate> onRegister(NonNullConsumer<? super ComponentItem> callback) {
            // Also intercepts ItemBuilder's constructor callback; no dangling Registrate callbacks remain.
            return this;
        }

        @Override
        public ItemBuilder<ComponentItem, GTRegistrate> model(
                NonNullBiConsumer<DataGenContext<Item, ComponentItem>, RegistrateItemModelProvider> callback) {
            return this;
        }

        @Override
        public ItemEntry<ComponentItem> register() {
            // Never resolve the replacement during GTLItems class initialization.
            return new ItemEntry<>(getOwner(), RegistryObject.create(REPLACEMENT, ForgeRegistries.ITEMS));
        }
    }
}
