package com.gtl.enhancedcore.audit;

import appeng.api.config.Settings;
import appeng.api.config.ShowPatternProviders;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AETextField;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import com.glodblock.github.extendedae.container.ContainerExPatternTerminal;
import com.gtl.enhancedcore.common.cloud.CloudPatternAccessMenu;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Real transformed ExtendedAE/Plus screen; synthetic packet data, never a player world. */
@Mod("enhancedcore_audit")
public final class ExtendedPatternTerminalUiChecks {
    private final List<String> checks=new ArrayList<>();
    private Path directory;
    private StellarIvGuiChecks.Fixture fixture;
    private NativeScreen screen;
    private Wrapper wrapper;
    private int phase,ticks;
    private boolean done;
    public ExtendedPatternTerminalUiChecks(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("gtl.enhancedcore.extendedPatternUiAudit"))return;
        var mc=Minecraft.getInstance();
        try{
            if(phase==0){
                if(mc.getOverlay()!=null||!(mc.screen instanceof TitleScreen))return;
                directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/request-fixes-20261027"))
                        &&Files.isRegularFile(directory.resolve("extended-pattern-ui.marker"))
                        &&mc.level==null&&mc.player==null&&mc.getSingleplayerServer()==null,
                        "Explicit isolated title screen; no saved or player world");
                fixture=StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line",true,false,false);
                var host=new Host();host.setLevel(fixture.player().level());
                var menu=new ProbeMenu(fixture.player().getInventory(),host);
                fixture.player().containerMenu=menu;
                screen=new NativeScreen(menu,fixture.player().getInventory());
                wrapper=new Wrapper(screen);mc.setScreen(wrapper);
                screen.updateBeforeRender();
                var stacks=new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ItemStack>();
                for(int i=0;i<74;i++)stacks.put(i,appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                        new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(AEItemKey.of(Items.IRON_INGOT),i+1)},
                        new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(AEItemKey.of(i%2==0?Items.GOLD_INGOT:Items.DIAMOND),i+1)}));
                screen.postFullUpdate(Long.MIN_VALUE,Long.MIN_VALUE,new PatternContainerGroup(AEItemKey.of(Items.CHEST),
                        Component.translatable("block.gtl_enhancedcore.cloud_pattern_library",
                                "00000000-0000-0000-0000-000000000074","","",String.valueOf(field(screen,"cloud$request"))),
                        List.of(Component.translatable("gtl_enhancedcore.cloud.terminal.hint"))),74,stacks);
                phase=1;
            }else if(++ticks>=8&&wrapper.frames>=8){
                capture();
                if(wrapper.failure!=null)throw new AssertionError("Real ExtendedAE render failed",wrapper.failure);
                verify();
                check(Files.size(directory.resolve("extended-pattern-terminal.png"))>1024,
                        "Actual framebuffer screenshot written");
                finish(null);
            }
        }catch(Throwable error){error.printStackTrace();finish(error);}
    }
    private void verify()throws Exception{
        check(screen.getClass().getSuperclass()==GuiExPatternTerminal.class,
                "Actual ExtendedAE screen superclass, including loaded Plus and cloud mixins");
        var byId=(Map<?,?>)field(screen,"byId");
        var record=(PatternContainerRecord)byId.get(Long.MIN_VALUE);
        check(byId.size()==1&&record!=null&&record.getInventory().size()==74,
                "Native full-update receiver retains all74 originals in one catalogue record");
        int originals=0;for(var stack:record.getInventory())if(!stack.isEmpty())originals++;
        check(originals==74,"All74 catalogue originals contain real encoded processing patterns");
        var input=(AETextField)field(screen,"searchInField");var output=(AETextField)field(screen,"searchOutField");
        check(input!=output&&((AbstractWidget)input).visible&&((AbstractWidget)output).visible,"Original native input and output search fields remain visible");
        check(screen.children().stream().noneMatch(widget->widget.getClass().getSimpleName().contains("PageButton")),
                "Cloud catalogue has no page-navigation controls");
        for(var widget:List.of(input,output))
            check(widget.getX()>=0&&widget.getY()>=0&&widget.getX()+widget.getWidth()<=((Screen)screen).width
                    &&widget.getY()+widget.getHeight()<=((Screen)screen).height,
                    "Visible widget stays within actual viewport: "+widget.getClass().getSimpleName());
        check(screen.getMenu().slots.stream().filter(slot->slot instanceof appeng.client.gui.me.patternaccess.PatternSlot).count()>0,
                "Real drawFG emits native pattern slots for received central originals");
        check(screen.getMenu().searchPayload!=null,"Actual transformed render sends native input/output query payload");
        verifyCompleteSearchAndReopen(input,output,record);
    }
    private void verifyCompleteSearchAndReopen(AETextField input,AETextField output,PatternContainerRecord cloud)throws Exception {
        String in="virtual-fluid-does-not-decode",out="拼音目录搜索";
        input.setValue(in);output.setValue(out);
        var stacks=new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ItemStack>();
        for(int i=0;i<74;i++)stacks.put(i,cloud.getInventory().getStackInSlot(i).copy());
        screen.postFullUpdate(17,17,new PatternContainerGroup(AEItemKey.of(Items.CHEST),Component.literal("Ordinary provider"),List.of()),74,stacks);
        invoke(screen,"refreshList");
        var groups=(com.google.common.collect.HashMultimap<?,?>)field(screen,"byGroup");
        check(groups.values().stream().anyMatch(value->((PatternContainerRecord)value).getServerId()==Long.MIN_VALUE),
                "Real ExtendedAE refresh retains server-matched cloud originals despite unsupported native IO query");
        check(groups.values().stream().noneMatch(value->((PatternContainerRecord)value).getServerId()==17),
                "Ordinary provider still obeys its native IO filter");
        screen.updateBeforeRender();
        set(screen,"cloud$searchDue",System.nanoTime()-1);screen.updateBeforeRender();
        String library="00000000-0000-0000-0000-000000000074";
        screen.postFullUpdate(Long.MIN_VALUE+9,Long.MIN_VALUE,new PatternContainerGroup(AEItemKey.of(Items.CHEST),
                Component.translatable("block.gtl_enhancedcore.cloud_pattern_library",
                        "00000000-0000-0000-0000-000000000075",in,out,"2"),List.of()),74,stacks);
        check(!((Map<?,?>)field(screen,"byId")).containsKey(Long.MIN_VALUE+9),
                "Late snapshot from a previous request cannot replace the current query or library");
        screen.postFullUpdate(Long.MIN_VALUE+10,Long.MIN_VALUE,new PatternContainerGroup(AEItemKey.of(Items.CHEST),
                Component.translatable("block.gtl_enhancedcore.cloud_pattern_library",library,in,out,
                        String.valueOf(field(screen,"cloud$request"))),List.of()),74,stacks);
        screen.removed();
        var reopened=new NativeScreen(screen.getMenu(),fixture.player().getInventory());
        reopened.initialize(Minecraft.getInstance());
        check(((AETextField)field(reopened,"searchInField")).getValue().equals(in)
                        &&((AETextField)field(reopened,"searchOutField")).getValue().equals(out),
                "Actual closed/reopened screen restores both IO queries from the client file");
        reopened.updateBeforeRender();
        check(reopened.getMenu().searchPayload.split("\n",-1)[0].equals(in)
                        &&reopened.getMenu().searchPayload.split("\n",-1)[1].equals(out),
                "Actual reopened screen requests the full catalogue with saved IO queries");
        check(Files.size(directory.resolve("config/GTL-Enhancedcore/cloud-terminal-views.json"))<262144,
                "Client view persistence remains a bounded small preference file");
    }
    private void finish(Throwable failure){
        done=true;var report=new LinkedHashMap<String,Object>();
        report.put("status",failure==null?"PASS":"FAIL");report.put("checks",checks);
        report.put("worldOpened",false);report.put("screenshot","extended-pattern-terminal.png");
        report.put("scope","Actual transformed ExtendedAE/Plus renderer with74 encoded packet originals in one catalogue record, search widgets and native scrolling; synthetic client host and intercepted outbound messages only; no server extraction claim");
        if(failure!=null)report.put("error",failure.toString());
        if(screen!=null){
            try{capture();}catch(Throwable error){report.put("captureError",error.toString());}
            try{
                report.put("viewport",List.of(((Screen)screen).width,((Screen)screen).height));
                report.put("guiOrigin",List.of(screen.getGuiLeft(),screen.getGuiTop()));
                report.put("widgets",screen.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).map(widget->Map.of(
                                "class",widget.getClass().getName(),"rect",List.of(widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight()),
                                "visible",widget.visible,"active",widget.active)).toList());
                report.put("activeSlots",screen.getMenu().slots.stream().filter(net.minecraft.world.inventory.Slot::isActive)
                        .map(slot->Map.of("class",slot.getClass().getName(),"index",slot.index,
                                "rect",List.of(screen.getGuiLeft()+slot.x,screen.getGuiTop()+slot.y,16,16))).toList());
            }catch(Throwable error){report.put("coordinateError",error.toString());}
        }
        try{Files.writeString(directory.resolve("extended-pattern-ui.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception error){error.printStackTrace();}
        if(fixture!=null)for(var holder:fixture.holders())holder.setLevel(null);
        Minecraft.getInstance().stop();
    }
    private void capture()throws Exception{
        try(var picture=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
            picture.writeToFile(directory.resolve("extended-pattern-terminal.png"));
        }
    }
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks.add(label);}
    private static Object field(Object object,String name)throws Exception{
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
        catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
    }
    private static void invoke(Object object,String name)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {
            var method=type.getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);return;
        }catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);
    }
    private static void set(Object object,String name,Object value)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {
            var member=type.getDeclaredField(name);member.setAccessible(true);member.set(object,value);return;
        }catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
    }
    private static final class ProbeMenu extends ContainerExPatternTerminal implements CloudPatternAccessMenu{
        String searchPayload;
        ProbeMenu(Inventory inventory,appeng.api.util.IConfigurableObject host){super(83,inventory,host);}
        @Override public void cloud$accessSearch(String payload){searchPayload=payload;}
    }
    /** AEBaseMenu accepts block entity, part or item-menu hosts, not an arbitrary config lambda. */
    private static final class Host extends net.minecraft.world.level.block.entity.BlockEntity
            implements appeng.api.util.IConfigurableObject {
        final appeng.util.ConfigManager config=new appeng.util.ConfigManager((Runnable)()->{});
        Host(){
            super(net.minecraft.world.level.block.entity.BlockEntityType.CHEST,net.minecraft.core.BlockPos.ZERO,
                    net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            config.registerSetting(Settings.TERMINAL_SHOW_PATTERN_PROVIDERS,ShowPatternProviders.VISIBLE);
        }
        @Override public appeng.api.util.IConfigManager getConfigManager(){return config;}
    }
    private static final class NativeScreen extends GuiExPatternTerminal<ProbeMenu>{
        NativeScreen(ProbeMenu menu,Inventory inventory){super(menu,inventory,Component.literal("ExtendedAE Central Pattern Library"),
                StyleManager.loadStyleDoc("/screens/ex_pattern_access_terminal.json"));}
        void initialize(Minecraft mc){minecraft=mc;font=mc.font;width=mc.getWindow().getGuiScaledWidth();height=mc.getWindow().getGuiScaledHeight();super.init();}
    }
    private static final class Wrapper extends Screen{
        final NativeScreen screen;int frames;Throwable failure;
        Wrapper(NativeScreen screen){super(Component.literal("ExtendedAE terminal audit"));this.screen=screen;}
        @Override protected void init(){screen.initialize(minecraft);}
        @Override public void tick(){}
        @Override public void render(GuiGraphics graphics,int x,int y,float partialTick){try{screen.render(graphics,x,y,partialTick);}catch(Throwable error){failure=error;}frames++;}
    }
}
