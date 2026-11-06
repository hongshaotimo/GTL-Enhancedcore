package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.search.PhoneticSearchMatcher;
import com.gtl.enhancedcore.client.gui.ChangelogScreen;
import com.gtl.enhancedcore.client.plot.CampusPlanScreen;
import com.gtl.enhancedcore.client.plot.CreateWorldPlotScreen;
import com.gtl.enhancedcore.client.plotinfo.PersonalPlotWorldScreen;
import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds;
import com.gtl.enhancedcore.common.world.plot.CampusPresetRegistry;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Real rendered search events with explicitly synthetic permission/session fixtures. */
@Mod("enhancedcore_audit")
public final class CampusSearchClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/campus-pinyin-remove-20261105").toAbsolutePath().normalize();
    private static final int[][] SIZES = {{960,600,2},{1280,720,3},{640,480,2}};
    private static final String KEY = "gtl_enhancedcore.create_world_plot.";
    private record Query(String category,String text,String expectedId,int count) {}
    private static final List<Query> QUERIES = List.of(
            new Query("full-pinyin","zhuangpeixian","gtceu:assembly_line",-1),
            new Query("initials","zpx","gtceu:assembly_line",-1),
            new Query("pinyin-uppercase","ZHUANGPEIXIAN","gtceu:assembly_line",-1),
            new Query("initials-uppercase","ZPX","gtceu:assembly_line",-1),
            new Query("pinyin-terms","zhuangpei xian","gtceu:assembly_line",-1),
            new Query("initials-terms","DL GL","gtceu:electric_blast_furnace",-1),
            new Query("chinese","装配线","gtceu:assembly_line",-1),
            new Query("resource-id","GTCEU:ASSEMBLY_LINE","gtceu:assembly_line",1),
            new Query("english-terms","advanced assembly","gtceu:assembly_line",-1),
            new Query("upgrade-id-alias","nano_forge_2","gtceu:nano_forge_1",1),
            new Query("upgrade-chinese-alias","2阶纳米锻炉","gtceu:nano_forge_1",1),
            new Query("upgrade-pinyin","namiduanlu","gtceu:nano_forge_1",1),
            new Query("repeated-slot","gtceu:electric_blast_furnace","gtceu:electric_blast_furnace",12),
            new Query("no-result","no-such-machine-827419","",0));
    private final List<String> checks=new ArrayList<>(),failures=new ArrayList<>();
    private final List<Map<String,Object>> screenshots=new ArrayList<>(),queries=new ArrayList<>(),creators=new ArrayList<>(),navigationEvents=new ArrayList<>();
    private final Map<String,Object> dictionary=new LinkedHashMap<>();
    private Path directory;
    // Screen is deliberate: inherited MC method owners must reobfuscate to Screen.
    private Screen screen,dashboard,editor;
    private EditBox search;
    private int sizeIndex,group,stage,frames,ticks,startupTicks,queryIndex,navigationStep,focusedSearchIndex;
    private boolean initialized,done,atlasObservedReady;

    public CampusSearchClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }
    private void render(TickEvent.RenderTickEvent event) {
        if(!done&&initialized&&event.phase==TickEvent.Phase.END&&Minecraft.getInstance().getOverlay()==null) frames++;
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("gtl.enhancedcore.campusSearchAudit")) return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(!initialized) {
                if(mc.getOverlay()!=null) return;
                Path proposed=mc.gameDirectory.toPath().toAbsolutePath().normalize();
                if(!proposed.startsWith(ROOT)||proposed.equals(ROOT)||!Files.isRegularFile(proposed.resolve("campus-search.marker")))
                    throw new IllegalStateException("Requires explicitly marked isolated search fixture");
                if(mc.screen instanceof ChangelogScreen) {mc.screen.onClose();return;}
                if(!(mc.screen instanceof TitleScreen)) {if(++startupTicks>1200) throw new AssertionError("Unexpected startup screen");return;}
                directory=proposed;
                require(mc.level==null&&mc.getSingleplayerServer()==null&&mc.getConnection()==null,"No world/server/network opened");
                require("zh_cn".equals(mc.options.languageCode),"Pinyin audit uses actual Chinese machine names");
                Files.createDirectories(directory.resolve("screenshots"));checkDictionary();initialized=true;openDashboard(mc);return;
            }
            if(++ticks>6000) throw new AssertionError("Targeted pinyin audit timed out");
            if(mc.getOverlay()!=null||frames<6) return;
            switch(stage) {
                case 0 -> {
                    click(dashboard,"screen.gtl_enhancedcore.personal_plot.edit_template");
                    require(mc.screen instanceof CreateWorldPlotScreen,"OP root click opens production preset editor");
                    editor=mc.screen;stage=1;frames=0;
                }
                case 1 -> {
                    var ids=CampusPresetRegistry.definitions().stream().map(CampusPresetRegistry.Definition::id).toList();
                    require(ids.contains("quartz_works")&&!ids.contains("celestial_megastructure"),"Registry retains factory and removes celestial");
                    var labels=editor.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).map(w->w.getMessage().getString()).toList();
                    require(labels.contains(Component.translatable(KEY+"campus_preset").getString()),"Natural woodland card remains");
                    require(labels.contains(Component.translatable(KEY+"factory_preset").getString()),"Factory card remains");
                    require(labels.stream().noneMatch(s->s.contains("神域")||s.contains("巨物神殿")||s.toLowerCase(java.util.Locale.ROOT).contains("celestial")),"Complete widget tree has no celestial card");
                    click(editor,KEY+"campus_preset");require(draft().equals(PlotSettings.plannedCampus()),"Natural card selects unchanged settings");
                    click(editor,KEY+"page_presets");scrollTo(KEY+"factory_preset");
                    creators.add(Map.of("framebuffer",sizeName(),"registryIds",ids,"cardLabels",labels,"opRootClicked",true,"naturalCardSelected",true,"celestialAbsent",true));
                    stage=2;frames=0;
                }
                case 2 -> {
                    capture("creator",editor);click(editor,KEY+"factory_preset");
                    require(draft().equals(PlotSettings.fileCampus("quartz_works")),"Factory card selects unchanged file settings");
                    scrollTo(KEY+"campus_open_plan");click(editor,KEY+"campus_open_plan");
                    require(mc.screen instanceof CampusPlanScreen,"Creator mouse path opens actual factory Atlas");
                    screen=mc.screen;group=0;prepareSearch();stage=3;frames=0;
                }
                case 3 -> {
                    if(group%2==0) {
                        if(!atlasReady()) {frames=0;return;}
                        if(!atlasObservedReady) {atlasObservedReady=true;frames=0;return;}
                    }
                    bounds();capture(groupName()+"-overview",screen);queryIndex=0;typeQuery(QUERIES.get(0));stage=4;frames=0;
                }
                case 4 -> {
                    Query query=QUERIES.get(queryIndex);verifyQuery(query);
                    if(queryIndex<2||queryIndex==QUERIES.size()-1) capture(groupName()+"-"+query.category(),screen);
                    if(++queryIndex<QUERIES.size()) {typeQuery(QUERIES.get(queryIndex));frames=0;}
                    else {typeQuery(new Query("navigation","gtceu:electric_blast_furnace","gtceu:electric_blast_furnace",12));navigationStep=0;stage=5;frames=0;}
                }
                case 5 -> {
                    if(!navigation(mc)) {frames=0;return;}
                    int groups=sizeIndex==0?4:2;
                    if(++group<groups) {openSearch(mc);stage=3;frames=0;}
                    else if(++sizeIndex<SIZES.length) openDashboard(mc);else finish(mc);
                }
                default -> throw new IllegalStateException("Unknown stage");
            }
        } catch(Throwable failure) {failures.add(failure.toString());failure.printStackTrace();finish(mc);}
    }
    private void checkDictionary() throws Exception {
        String resource="me/towdium/pinin/data.txt";
        try(var stream=PhoneticSearchMatcher.class.getClassLoader().getResourceAsStream(resource)) {
            require(stream!=null,"PinIn dictionary visible to production PhoneticSearchMatcher loader");
            byte[] bytes=stream.readAllBytes();require(bytes.length>1000,"PinIn dictionary is populated");
            dictionary.put("resource",resource);dictionary.put("bytes",bytes.length);dictionary.put("visible",true);
            dictionary.put("owner",PhoneticSearchMatcher.class.getName());dictionary.put("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }
    }
    private void openDashboard(Minecraft mc) throws Exception {
        int[] size=SIZES[sizeIndex];mc.options.guiScale().set(size[2]);
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),size[0],size[1]);mc.resizeDisplay();
        var snapshot=new PersonalPlotNetwork.Snapshot(true,true,false,false,false,false,null,null,PersonalPlotWorlds.State.NONE,null,false,false,"");
        var constructor=PersonalPlotWorldScreen.class.getDeclaredConstructor(PersonalPlotNetwork.Snapshot.class);
        constructor.setAccessible(true);dashboard=constructor.newInstance(snapshot);mc.setScreen(dashboard);stage=0;frames=0;
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),0,0);
    }
    private void openSearch(Minecraft mc) throws Exception {
        PlotSettings settings=group<2?PlotSettings.fileCampus("quartz_works"):PlotSettings.plannedCampus();Screen parent=new TitleScreen();
        if(group%2==0) screen=new CampusPlanScreen(parent,settings);
        else {
            Class<?> type=Class.forName(CampusPlanScreen.class.getName()+"$TeleportSession");
            var sessionConstructor=type.getDeclaredConstructor(BlockPos.class,ResourceLocation.class,UUID.class);sessionConstructor.setAccessible(true);
            Object session=sessionConstructor.newInstance(new BlockPos(0,settings.surfaceY()+1,0),new ResourceLocation("gtl_enhancedcore","audit_pinyin_synthetic"),UUID.randomUUID());
            var constructor=CampusPlanScreen.class.getDeclaredConstructor(Screen.class,PlotSettings.class,type);constructor.setAccessible(true);
            screen=constructor.newInstance(parent,settings,session);
        }
        mc.setScreen(screen);prepareSearch();
    }
    private void prepareSearch() throws Exception {search=(EditBox)field(CampusPlanScreen.class,"searchBox").get(screen);atlasObservedReady=false;}
    private void typeQuery(Query query) {
        search.setValue("");require(screen.mouseClicked(search.getX()+4,search.getY()+4,0),"Real mouse focuses search");screen.mouseReleased(search.getX()+4,search.getY()+4,0);
        for(char ch:query.text().toCharArray()) require(screen.charTyped(ch,0),"Real keyboard accepts query character");
        require(search.getValue().equals(query.text()),"Real EditBox contains complete typed query");
    }
    @SuppressWarnings("unchecked") private List<CampusPlanAccess.Target> matches() {return (List<CampusPlanAccess.Target>)get("searchMatches");}
    private void verifyQuery(Query query) {
        var matches=matches();var ids=matches.stream().filter(t->t.plot()!=null).map(t->t.plot().id()).toList();
        require(query.count()<0?!matches.isEmpty():matches.size()==query.count(),"Rendered query count: "+query.text()+" => "+matches.size());
        require(query.expectedId().isEmpty()||ids.contains(query.expectedId()),"Query includes expected canonical reservation: "+query.text());
        if(!matches.isEmpty()) require(((Number)get("searchIndex")).intValue()==0&&get("selected").equals(matches.getFirst()),"Typed query selects/focuses first result");
        require(!(boolean)get("pending"),"Search sends no teleport request");
        var result=new LinkedHashMap<String,Object>();result.put("framebuffer",sizeName());result.put("preset",group<2?"quartz_works":"natural_woodland");
        result.put("mode",group%2==0?"preview":"synthetic-teleporter");result.put("category",query.category());result.put("query",query.text());
        result.put("count",matches.size());result.put("targetIds",ids);result.put("targetIndices",matches.stream().map(CampusPlanAccess.Target::index).toList());
        result.put("expectedId",query.expectedId());result.put("typedInput",true);result.put("renderFramesAfterInput",frames);result.put("passed",true);queries.add(result);
    }
    private boolean navigation(Minecraft mc) throws Exception {
        var matches=matches();
        switch(navigationStep++) {
            case 0 -> {
                require(matches.size()==12,"Navigation has twelve repeated blast-furnace slots");
                var next=(AbstractWidget)get("nextMatch");require(screen.mouseClicked(next.getX()+4,next.getY()+4,0),"Next result actual click");
                screen.mouseReleased(next.getX()+4,next.getY()+4,0);navigationEvent("next-click");
            }
            case 1 -> {
                require(get("selected").equals(matches.get(1)),"Next advances slot");
                require(screen.mouseClicked(search.getX()+4,search.getY()+4,0),"Real search click restores keyboard focus");
                screen.mouseReleased(search.getX()+4,search.getY()+4,0);navigationEvent("search-focus-click");
            }
            case 2 -> {
                require(search.isFocused(),"Search field actually focused before Enter");
                focusedSearchIndex=((Number)get("searchIndex")).intValue();
                require(focusedSearchIndex>=0&&get("selected").equals(matches.get(focusedSearchIndex)),"Focus click leaves a valid selected search result");
                require(screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0),"Production screen handles actual Enter event");navigationEvent("enter");
            }
            case 3 -> {
                require(get("selected").equals(matches.get((focusedSearchIndex+1)%matches.size())),"Enter advances from the actual post-click selection");
                require(screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,GLFW.GLFW_MOD_SHIFT),"Production screen handles actual Shift Enter event");navigationEvent("shift-enter");
            }
            case 4 -> {
                require(get("selected").equals(matches.get(focusedSearchIndex)),"Shift Enter returns to actual focused selection");
                screen.resize(mc,screen.width,screen.height);prepareSearch();navigationEvent("resize");
            }
            case 5 -> {
                require(search.getValue().equals("gtceu:electric_blast_furnace")&&get("selected").equals(matches.get(focusedSearchIndex)),"Resize retains text and selection");
                if(group%2==0) return true;
                var clear=(AbstractWidget)get("clearSearch");require(screen.mouseClicked(clear.getX()+4,clear.getY()+4,0),"Clear actual click");
                screen.mouseReleased(clear.getX()+4,clear.getY()+4,0);navigationEvent("clear-click");
            }
            case 6 -> {
                require(search.getValue().isEmpty()&&search.isFocused(),"Clear restores empty focused search");
                require(screen.charTyped('e',0),"E accepted as text");screen.keyPressed(GLFW.GLFW_KEY_E,0,0);navigationEvent("focused-e");
            }
            case 7 -> {
                require(mc.screen==screen&&search.getValue().equals("e"),"Focused E does not close teleporter");
                int x=((Number)get("mapLeftEdge")).intValue()+5,y=((Number)get("mapBottom")).intValue()-5;
                screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);navigationEvent("map-focus-click");
            }
            case 8 -> {
                require(!search.isFocused(),"Map click clears search focus");
                require(screen.keyPressed(GLFW.GLFW_KEY_E,0,0)&&mc.screen!=screen,"Unfocused E closes teleporter");navigationEvent("close-e");return true;
            }
            default -> throw new IllegalStateException("Unknown navigation step");
        }
        return false;
    }
    private void navigationEvent(String event) {
        navigationEvents.add(Map.of("framebuffer",sizeName(),"group",groupName(),"event",event,
                "searchFocused",search.isFocused(),"searchIndex",((Number)get("searchIndex")).intValue(),
                "query",search.getValue(),"renderFramesBeforeEvent",frames));
    }
    private PlotSettings draft() throws Exception {return (PlotSettings)field(CreateWorldPlotScreen.class,"draft").get(editor);}
    private static AbstractWidget button(Screen owner,String key) {
        String label=Component.translatable(key).getString();return owner.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(w->w.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private void click(Screen owner,String key) {
        var widget=button(owner,key);double x=widget.getX()+widget.getWidth()/2.0,y=widget.getY()+widget.getHeight()/2.0;
        require(widget.active&&widget.visible&&owner.mouseClicked(x,y,0),"Production root/editor click: "+key);owner.mouseReleased(x,y,0);
    }
    @SuppressWarnings("unchecked") private void scrollTo(String key) throws Exception {
        var positions=(Map<AbstractWidget,Integer>)field(CreateWorldPlotScreen.class,"contentY").get(editor);
        var method=CreateWorldPlotScreen.class.getDeclaredMethod("updateScroll",int.class);method.setAccessible(true);method.invoke(editor,positions.get(button(editor,key)));
    }
    private void bounds() {
        for(var child:screen.children()) if(child instanceof AbstractWidget widget&&widget.visible)
            require(widget.getX()>=0&&widget.getY()>=0&&widget.getX()+widget.getWidth()<=screen.width&&widget.getY()+widget.getHeight()<=screen.height,"Visible search/map control fits viewport");
    }
    private boolean atlasReady() throws Exception {Object map=get("map");return (boolean)map.getClass().getMethod("ready").invoke(map);}
    private String sizeName() {return SIZES[sizeIndex][0]+"x"+SIZES[sizeIndex][1];}
    private String groupName() {return (group<2?"factory-":"woodland-")+(group%2==0?"preview":"session");}
    private void capture(String name,Screen owner) throws Exception {
        var mc=Minecraft.getInstance();int[] size=SIZES[sizeIndex];require(mc.getWindow().getWidth()==size[0]&&mc.getWindow().getHeight()==size[1],"Actual framebuffer dimensions match");
        if(sizeIndex==2) require(owner.width==320&&owner.height==240,"Actual logical minimum is 320x240");
        String file="screenshots/"+name+"-"+sizeName()+".png";
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(directory.resolve(file));}
        screenshots.add(Map.of("file",file,"frames",frames,"logicalWidth",owner.width,"logicalHeight",owner.height));
    }
    private Object get(String name) {try{return field(CampusPlanScreen.class,name).get(screen);}catch(Exception error){throw new IllegalStateException(error);}}
    private static Field field(Class<?> owner,String name) throws Exception {var field=owner.getDeclaredField(name);field.setAccessible(true);return field;}
    private void require(boolean value,String message) {if(!value) throw new AssertionError(message);checks.add(message);}
    private void finish(Minecraft mc) {
        done=true;if(directory==null) directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
        if(failures.isEmpty()&&(queries.size()!=8*QUERIES.size()||creators.size()!=3)) failures.add("Incomplete query or creator matrix");
        try {
            var result=new LinkedHashMap<String,Object>();result.put("schemaVersion",2);result.put("status",failures.isEmpty()?"PASS":"FAIL");
            result.put("checks",checks);result.put("failures",failures);result.put("screenshots",screenshots);result.put("pinyinQueries",queries);
            result.put("navigationEvents",navigationEvents);
            result.put("presetRegistryChecks",creators);result.put("creatorChecked",creators.size()==3);result.put("pinyinDictionary",dictionary);
            result.put("language",mc.options.languageCode);result.put("worldOpened",false);result.put("syntheticTeleporterSession",true);
            result.put("scope","Actual Forge UI, GL screenshots and real mouse/keyboard input. Factory preview/synthetic central-teleporter session at three sizes; woodland at 960x600. OP permissions and teleporter sessions are reflection-created fixtures; root/card/search clicks use production paths. No world, network, server Open packet, real device right click, teleport travel or FTB texture refresh tested.");
            Files.writeString(directory.resolve("campus-search-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch(Throwable error) {error.printStackTrace();}mc.stop();
    }
}
