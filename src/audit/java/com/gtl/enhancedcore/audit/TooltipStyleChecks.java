package com.gtl.enhancedcore.audit;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** The real Forge gather -> Modern UI Pre path with original styled text and a bundle image. */
public final class TooltipStyleChecks {
    private TooltipStyleChecks() {}
    public static List<GuiStyleClientChecks.Scene> scenes() {
        return List.of(scene("tooltip-wide-styled", false, false), scene("tooltip-tall-top", true, false),
                scene("tooltip-tall-native-scroll", true, true));
    }
    private static GuiStyleClientChecks.Scene scene(String name, boolean tall, boolean scroll) {
        return new GuiStyleClientChecks.Scene(name, () -> new TooltipScreen(tall, scroll),
                screen -> ((TooltipScreen)screen).verify());
    }
    public static final class TooltipScreen extends Screen {
        private final boolean tall, scroll;
        private final List<Component> lines = new ArrayList<>();
        private final ItemStack stack = new ItemStack(Items.BUNDLE);
        private final BundleTooltip image = new BundleTooltip(net.minecraft.core.NonNullList.of(ItemStack.EMPTY,
                new ItemStack(Items.DIAMOND, 16), new ItemStack(Items.IRON_INGOT, 32)),48);
        private final List<String> originals = new ArrayList<>();
        private int ticks, gathers, preCalls, measuredWidth, measuredHeight;
        private boolean imagePreserved;
        private Object modernRenderer;
        TooltipScreen(boolean tall, boolean scroll) {
            super(Component.literal("Tooltip viewport audit")); this.tall=tall; this.scroll=scroll;
            lines.add(Component.literal("Styled item tooltip / \u4fdd\u7559\u989c\u8272\u4e0e\u7ec4\u4ef6").withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal("Long recipe catalog: ").withStyle(ChatFormatting.GRAY).append(
                    Component.literal("chemical_reactor centrifuge assembler electrolyzer ".repeat(8)).withStyle(ChatFormatting.GOLD)));
            for (int i=0;i<(tall?80:3);i++) lines.add(Component.literal(String.format("%02d  ",i))
                    .withStyle(ChatFormatting.GRAY).append(Component.literal("Original recipe and capacity details / \u539f\u59cb\u914d\u65b9\u4e0e\u5bb9\u91cf\u8bf4\u660e")
                            .withStyle(i%2==0?ChatFormatting.WHITE:ChatFormatting.GREEN)));
            lines.forEach(line->originals.add(Component.Serializer.toJson(line)));
        }
        @Override protected void init() { MinecraftForge.EVENT_BUS.register(this); }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void gathered(RenderTooltipEvent.GatherComponents event) {
            if (Minecraft.getInstance().screen==this && event.getItemStack()==stack) gathers++;
        }
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void pre(RenderTooltipEvent.Pre event) {
            if (Minecraft.getInstance().screen!=this || event.getItemStack()!=stack) return;
            preCalls++; measuredWidth=0; measuredHeight=0;
            for (var component:event.getComponents()) {
                measuredWidth=Math.max(measuredWidth,component.getWidth(event.getFont()));
                measuredHeight+=component.getHeight();
                if (!(component instanceof ClientTextTooltip)) imagePreserved=true;
            }
        }
        @Override public void tick() {
            if (++ticks==3 && scroll) {
                try {
                    Class<?> manager=Class.forName("icyllis.modernui.mc.UIManager");
                    Object instance=manager.getMethod("getInstance").invoke(null);
                    var field=manager.getDeclaredField("mTooltipRenderer"); field.setAccessible(true); modernRenderer=field.get(instance);
                    var input=manager.getDeclaredMethod("onPreKeyInput",int.class,int.class,int.class,int.class); input.setAccessible(true);
                    for (int i=0;i<12;i++) input.invoke(instance,264,0,1,0);
                } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
            }
        }
        @Override public void render(GuiGraphics graphics,int x,int y,float partial) {
            graphics.fill(0,0,width,height,0xff30343a);
            graphics.renderTooltip(font,lines,Optional.of(image),stack,width/2,height/2);
        }
        @Override public void removed() { MinecraftForge.EVENT_BUS.unregister(this); }
        void verify() throws Exception {
            GuiStyleClientChecks.require(gathers>0 && preCalls>0,"Real tooltip traverses Forge GatherComponents and Pre, including Modern UI");
            GuiStyleClientChecks.require(measuredWidth>0,"Native tooltip retains measurable original content: "+measuredWidth);
            GuiStyleClientChecks.require(imagePreserved,"Original bundle/image tooltip component survives gathering");
            for (int i=0;i<lines.size();i++) GuiStyleClientChecks.require(originals.get(i).equals(Component.Serializer.toJson(lines.get(i))),"Source text/style preserved at line "+i);
            GuiStyleClientChecks.require(!mouseScrolled(width/2.0,height/2.0,1),"Tooltip view does not capture ordinary mouse wheel input");
            if (tall) GuiStyleClientChecks.require(measuredHeight>height,"Tall original content is retained for native scrolling: "+measuredHeight);
            if (scroll) {
                Field amount=modernRenderer.getClass().getDeclaredField("mScroll"); amount.setAccessible(true);
                GuiStyleClientChecks.require(amount.getFloat(modernRenderer)>0,"Modern UI native down-arrow input scrolls retained full-height tooltip: "+amount.getFloat(modernRenderer));
            }
        }
    }
}
