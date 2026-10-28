package com.gtl.enhancedcore.common.cloud.ui;

import appeng.api.stacks.AEKey;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Render the actual output key, including fluids, instead of a generic encoded-pattern item. */
final class CloudPatternProductTexture implements IGuiTexture {
    private final AEKey key;
    private final IGuiTexture item;
    private final boolean leased;
    CloudPatternProductTexture(CompoundTag entry) {
        key = entry.contains("iconKey") ? AEKey.fromTagGeneric(entry.getCompound("iconKey")) : null;
        leased = entry.getBoolean("leased");
        item = new ItemStackTexture(ItemStack.of(entry.getCompound("icon"))).setColor(leased ? 0xff777777 : 0xffffffff);
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    @Override public void draw(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
        if (key == null) { item.draw(graphics, mouseX, mouseY, x, y, width, height); return; }
        appeng.api.client.AEKeyRendering.drawInGui(net.minecraft.client.Minecraft.getInstance(), graphics, (int) x, (int) y, key);
        if (leased) graphics.fill((int) x, (int) y, (int) x + width, (int) y + height, 0x90777777);
    }
}
