package com.gtl.enhancedcore.common.cloud.ui;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.renderer.machine.TieredHullMachineRenderer;
import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** UV GT casing, with the access/lease connection state shown independently of recipe activity. */
public final class CloudPatternMachineRenderer extends TieredHullMachineRenderer {
    private final IModelRenderer idle, connected;
    public CloudPatternMachineRenderer(int tier, ResourceLocation idle, ResourceLocation connected) {
        super(tier, com.gregtechceu.gtceu.GTCEu.id("block/machine/hull_machine"));
        this.idle = new IModelRenderer(idle);
        this.connected = new IModelRenderer(connected);
    }
    public CloudPatternMachineRenderer(ResourceLocation casing, ResourceLocation idle, ResourceLocation connected) {
        super(com.gregtechceu.gtceu.api.GTValues.UV, com.gregtechceu.gtceu.GTCEu.id("block/cube/all"));
        setTextureOverride(java.util.Map.of("all", casing, "side", casing));
        this.idle = new IModelRenderer(idle);
        this.connected = new IModelRenderer(connected);
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @Override public void renderMachine(List<BakedQuad> quads, MachineDefinition definition, MetaMachine machine,
                                        Direction front, Direction side, RandomSource random, Direction upwards,
                                        ModelState state) {
        super.renderMachine(quads, definition, machine, front, side, random, upwards, state);
        IModelRenderer overlay = machine instanceof CloudPatternUiHost host && host.cloudUiConnected() ? connected : idle;
        quads.addAll(overlay.getRotatedModel(front).getQuads(definition.defaultBlockState(), side, random));
    }
}
