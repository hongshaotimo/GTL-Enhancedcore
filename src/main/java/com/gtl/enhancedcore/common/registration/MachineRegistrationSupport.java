package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/** Machine builders, invoked only during the GTCEu registration event. */
public final class MachineRegistrationSupport {
    private MachineRegistrationSupport() {}

    public static TraceabilityPredicate requiredBlock(String id) {
        return Predicates.blocks(new Block[]{requiredBlockBlock(id)});
    }

    public static Block requiredBlockBlock(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Objects.requireNonNull(location, "Invalid block id: " + id);
        Block block = ForgeRegistries.BLOCKS.getValue(location);
        if (block == null || block == Blocks.AIR) {
            throw new IllegalStateException("Required block is not registered: " + id);
        }
        return block;
    }

}
