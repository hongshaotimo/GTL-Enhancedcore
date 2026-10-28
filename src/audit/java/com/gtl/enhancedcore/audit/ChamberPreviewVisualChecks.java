package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.lowdragmc.lowdraglib.gui.modular.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.gtlcore.gtlcore.client.preview.*;

/** Real native widget/fullscreen renderer in an isolated title client, never a player world. */
@Mod("enhancedcore_audit")
public final class ChamberPreviewVisualChecks {
    private static final Path ROOT=Path.of("C:/IDEA/GTL-Enhancedcore/_audit/chamber-order-fix-20261027");
    private final List<String> checks=new ArrayList<>();
    private final List<Map<String,Object>> layouts=new ArrayList<>();
    private Path directory;
    private StellarIvGuiChecks.Fixture fixture;
    private Field levelField;
    private Object before;
    private PatternPreviewWidget widget;
    private PreviewControls controls;
    private SceneWidget scene;
    private Screen embedded;
    private ButtonWidget all,toggle;
    private int phase,elapsed;
    private boolean done;
    public ChamberPreviewVisualChecks(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("gtl.enhancedcore.chamberVisualAudit"))return;
        var mc=Minecraft.getInstance();
        if(phase==0&&(mc.getOverlay()!=null||!(mc.screen instanceof TitleScreen)))return;
        try{
            directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
            require(directory.startsWith(ROOT)&&!directory.equals(ROOT)&&Files.isRegularFile(directory.resolve("chamber-visual.marker"))
                    &&mc.level==null&&mc.player==null&&mc.getSingleplayerServer()==null,"Isolated title client without player world");
            if(phase==0){
                layouts.add(GalaxyConfigurationChecks.run(this::require));
                ChamberPreviewUiChecks.run(this::require);
                setup();phase=1;return;
            }
            if(++elapsed<8||scene.getRenderer().isCompiling())return;
            elapsed=0;
            String label=switch(phase){case 1->"embedded-off";case 2->"fullscreen-off";case 3->"fullscreen-on";default->"embedded-restored-on";};
            ChamberPreviewUiChecks.aligned(widget,all,toggle,this::require,label);
            layouts.add(Map.of("phase",label,"root",List.of(widget.getSizeWidth(),widget.getSizeHeight()),
                    "all",List.of(all.getSelfPositionX(),all.getSelfPositionY(),all.getSizeWidth(),all.getSizeHeight()),
                    "chambers",List.of(toggle.getSelfPositionX(),toggle.getSelfPositionY(),toggle.getSizeWidth(),toggle.getSizeHeight())));
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(directory.resolve(label+".png"));}
            if(phase==1)mc.setScreen(new FullscreenPreviewScreen(embedded,controls));
            else if(phase==2){
                var mouse=toggle.getPosition();
                mc.screen.mouseClicked(mouse.x+9,mouse.y+9,0);
                require(com.gtl.enhancedcore.client.preview.ChamberPreviewOverlay.enabled(scene),"Native fullscreen mouse callback enables chambers");
            }else if(phase==3){controls.exit();mc.setScreen(embedded);}
            else{finish(null);return;}
            phase++;
        }catch(Throwable error){error.printStackTrace();finish(error);}
    }
    private void setup()throws Exception{
        var mc=Minecraft.getInstance();
        fixture=StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line",true,false,false);
        var world=(TrackedDummyWorld)fixture.player().level();ChamberPreviewUiChecks.seedWorld(world);
        levelField=PatternPreviewWidget.class.getDeclaredField("LEVEL");levelField.setAccessible(true);before=levelField.get(null);levelField.set(null,world);
        var definition=(MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtceu:wooden_multiblock_tank"));
        widget=PatternPreviewWidget.getPatternWidget(definition);controls=widget.getPreviewControls();scene=controls.scene();
        all=buttonAt(50);toggle=buttonAt(70);
        var ui=fixture.machine().createUI(fixture.player());ui.initWidgets();
        ui.mainGroup.clearAllWidgets();ui.mainGroup.addWidget(widget);widget.setSelfPosition(35,40);
        var gui=new AuditGui(ui);fixture.player().containerMenu=ui.getModularUIContainer();
        embedded=new Screen(Component.literal("Chamber preview layout audit")){
            @Override protected void init(){gui.initialize(minecraft);}
            @Override public void render(GuiGraphics graphics,int x,int y,float partial){
                graphics.fill(0,0,width,height,0xff30343a);gui.render(graphics,-100,-100,partial);
            }
        };
        mc.setScreen(embedded);
    }
    private ButtonWidget buttonAt(int y){return widget.widgets.stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
            .filter(button->button.getSelfPositionY()==y).findFirst().orElseThrow();}
    private void require(boolean value,String label){if(!value)throw new AssertionError(label);checks.add(label);}
    private void finish(Throwable error){
        done=true;var report=new LinkedHashMap<String,Object>();report.put("status",error==null?"PASS":"FAIL");
        if(error!=null)report.put("error",error.toString());report.put("checks",checks);report.put("layouts",layouts);
        report.put("scope","Real mixed-in native widget, resize/restore, labels and rendered embedded/fullscreen frames; detached tiny structure fixture, no player world");
        try{Minecraft.getInstance().setScreen(new TitleScreen());if(controls!=null)controls.exit();if(scene!=null)scene.releaseCacheBuffer();PatternPreviewWidget.clearSharedPreviewState();
            if(levelField!=null)levelField.set(null,before);if(fixture!=null)fixture.holders().forEach(holder->holder.setLevel(null));
            Files.writeString(directory.resolve("chamber-visual.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception failure){failure.printStackTrace();}Minecraft.getInstance().stop();
    }
    private static final class AuditGui extends ModularUIGuiContainer{
        AuditGui(ModularUI ui){super(ui,1);}
        void initialize(Minecraft mc){minecraft=mc;font=mc.font;width=mc.getWindow().getGuiScaledWidth();height=mc.getWindow().getGuiScaledHeight();super.init();}
        @Override protected void containerTick(){}
        @Override public void removed(){}
        @Override public void onClose(){}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){super.render(graphics,-100,-100,partial);}
    }
}
