package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.GTRecipeWidget;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeTypeCategory;
import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.library.focus.FocusGroup;
import com.gtl.enhancedcore.client.gui.GtRecipeGrid;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gtl.enhancedcore.client.gui.RecipeViewport;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.jei.ModularUIRecipeCategory;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeBookmarkButton;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeTransferButton;
import mezz.jei.library.ingredients.TypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;

/** Real GT recipe wrapper rendering plus a deterministic JEI slot probe, through the transformed create path. */
public final class JeiStyleChecks {
    private JeiStyleChecks() {}
    public static List<GuiStyleClientChecks.Scene> scenes() {
        var scenes=new ArrayList<GuiStyleClientChecks.Scene>();
        scenes.addAll(List.of(scene("jei-eye-first", true, false), scene("jei-eye-last", true, true),
                scene("jei-wide-last", false, true), scene("jei-normal", false, false)));
        scenes.add(new GuiStyleClientChecks.Scene("jei-runtime-eye-first", ()->new RealRecipeScreen(false), screen->((RealRecipeScreen)screen).verify()));
        scenes.add(new GuiStyleClientChecks.Scene("jei-runtime-eye-last", ()->new RealRecipeScreen(true), screen->((RealRecipeScreen)screen).verify()));
        scenes.add(new GuiStyleClientChecks.Scene("jei-runtime-mixed-short-first", ()->new RealRecipeScreen(true,true,false), screen->((RealRecipeScreen)screen).verify()));
        scenes.add(new GuiStyleClientChecks.Scene("jei-runtime-mixed-long-first", ()->new RealRecipeScreen(true,true,true), screen->((RealRecipeScreen)screen).verify()));
        scenes.add(new GuiStyleClientChecks.Scene("jei-stock-grid-normal-tick",JeiStyleChecks::stockGrid,
                screen->verifyStock((RecipeScreen)screen)));
        scenes.add(new GuiStyleClientChecks.Scene("jei-stock-small-oc",JeiStyleChecks::smallStock,
                screen->verifyOc((RecipeScreen)screen,false)));
        scenes.add(new GuiStyleClientChecks.Scene("config-long-error-hover",JeiStyleChecks::longError,
                JeiStyleChecks::verifyLongError));
        return scenes;
    }
    private static boolean worldRequested, runtimeReady;
    private static LocalPlayer baselinePlayer;
    /** Called asynchronously by the main audit before creating each JEI scene. */
    public static boolean prepareScene(String id) throws Exception {
        if (!id.startsWith("jei-")) return true;
        var mc=Minecraft.getInstance();
        if (Internal.getOptionalJeiRuntime().isPresent() && com.lowdragmc.lowdraglib.jei.JEIPlugin.jeiHelpers != null
                && mc.player != null && mc.level != null) {
            if (!runtimeReady) {
                baselinePlayer=mc.player; runtimeReady=true;
                require(true,"Actual JEI runtime and LDLib category helpers are initialized through the normal lifecycle");
            }
            return true;
        }
        if (!worldRequested) {
            var directory=mc.gameDirectory.toPath().toAbsolutePath().normalize();
            var root=java.nio.file.Path.of("C:/IDEA/GTL-Enhancedcore/_work/gui-unification-20261102").toAbsolutePath().normalize();
            var rollbackRoot=java.nio.file.Path.of("C:/IDEA/GTL-Enhancedcore/_work/jei-team-20261104").toAbsolutePath().normalize();
            require(Boolean.getBoolean("gtl.enhancedcore.guiStyleJeiWorld"),"Runner explicitly allows a fresh isolated JEI lifecycle world");
            require(((directory.startsWith(root) && !directory.equals(root))
                    || (directory.startsWith(rollbackRoot) && !directory.equals(rollbackRoot)))
                    && Files.isRegularFile(directory.resolve("gui-style.marker")),
                    "Fresh JEI world is confined to the marked disposable audit instance");
            require(mc.level==null && mc.getSingleplayerServer()==null,"No existing world is open before JEI lifecycle preparation");
            require(!Files.exists(directory.resolve("saves/gui-style-jei")),"JEI lifecycle world is new and never copied from player saves");
            worldRequested=true; System.setProperty("gtl.enhancedcore.guiStyleCreatedWorld","gui-style-jei");
            mc.options.pauseOnLostFocus=false;
            mc.createWorldOpenFlows().createFreshLevel("gui-style-jei",new LevelSettings("GUI Style JEI Audit",GameType.CREATIVE,
                    false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261102L,false,false),
                    registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        return false;
    }
    private static GuiStyleClientChecks.Scene scene(String name, boolean eye, boolean end) {
        return new GuiStyleClientChecks.Scene(name, () -> open(eye, end), screen -> ((RecipeScreen)screen).verify());
    }
    private static void require(boolean value, String description) { GuiStyleClientChecks.require(value, description); }
    private static RecipeScreen open(boolean eye, boolean end) throws Exception {
        if (!eye) return new RecipeScreen(new Adapter(end ? 950 : 160, end ? 100 : 70, null, null), end);
        require(baselinePlayer != null && Minecraft.getInstance().player == baselinePlayer,
                "Eye rendering starts with the legitimate isolated-world lifecycle player");
        require(baselinePlayer.getCooldowns() != null, "Lifecycle player has fully initialized item-rendering state");
        var substitute = baselinePlayer;
        GTRecipe recipe = eyeRecipe();
        GTRecipeWrapper wrapper = withPlayer(substitute, () -> new GTRecipeWrapper(recipe));
        require(wrapper.getWidget() instanceof GTRecipeWidget, "Eye uses the real GTRecipeWidget inside GTRecipeWrapper");
        var size = wrapper.getWidget().getSize();
        return new RecipeScreen(new Adapter(size.width, size.height, wrapper, substitute), end);
    }
    private static RecipeScreen stockGrid() throws Exception {
        var id=new ResourceLocation("gtl_enhancedcore","audit_unregistered_huge_grid");
        require(com.gregtechceu.gtceu.api.registry.GTRegistries.RECIPE_TYPES.get(id)==null,
                "Dedicated stock-grid fixture type is absent from the global GT registry");
        var type=new com.gregtechceu.gtceu.api.recipe.GTRecipeType(id,"audit").setMaxIOSize(1,120,1,18);
        require(!type.getRecipeUI().hasCustomUI(),"Dedicated unregistered type has no rtui resource");
        var recipe=eyeRecipe(type);
        recipe.tickInputs.put(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP,
                new ArrayList<>(List.of(new com.gregtechceu.gtceu.api.recipe.content.Content(32L,10000,10000,0,null,null))));
        // Move authored content objects into the tick table without copying or changing their chance/tag metadata.
        var regularItems=new ArrayList<>(recipe.getOutputContents(ItemRecipeCapability.CAP));
        var regularFluids=new ArrayList<>(recipe.getOutputContents(FluidRecipeCapability.CAP));
        recipe.outputs.put(ItemRecipeCapability.CAP,new ArrayList<>(regularItems.subList(0,117)));
        recipe.outputs.put(FluidRecipeCapability.CAP,new ArrayList<>(regularFluids.subList(0,22)));
        recipe.tickOutputs.put(ItemRecipeCapability.CAP,new ArrayList<>(regularItems.subList(117,120)));
        recipe.tickOutputs.put(FluidRecipeCapability.CAP,new ArrayList<>(regularFluids.subList(22,24)));
        regularItems.get(0).chance=6500;regularItems.get(0).tierChanceBoost=125;
        regularItems.get(119).chance=2500;regularItems.get(119).tierChanceBoost=75;
        regularFluids.get(23).chance=7500;
        var tagged=ItemRecipeCapability.CAP.of(regularItems.get(118).content).getItems()[0].copy();
        tagged.getOrCreateTag().putString("audit_native_tag","preserve");
        regularItems.get(118).content=com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient.create(tagged);
        var wrapper=withPlayer(baselinePlayer,()->new GTRecipeWrapper(recipe));
        var size=wrapper.getWidget().getSize();
        require(com.gregtechceu.gtceu.api.registry.GTRegistries.RECIPE_TYPES.get(id)==null,
                "Constructing and rendering a fixture recipe type never registers it globally");
        return new RecipeScreen(new Adapter(size.width,size.height,wrapper,baselinePlayer),true);
    }
    private static void verifyStock(RecipeScreen screen) throws Exception {
        var recipe=screen.content.wrapper.recipe;
        var widget=(WidgetGroup)screen.content.wrapper.getWidget();
        var grids=widget.getWidgetsByType(GtRecipeGrid.class);
        require(grids.size()==1,"Stock-template scene actually executes the production compact grid");
        var grid=grids.getFirst();var bindings=grid.bindings();
        require(bindings.stream().filter(binding->binding.io()==IO.OUT).count()==144,"Stock grid binds all 120 item and 24 fluid outputs");
        require(bindings.stream().filter(binding->binding.io()==IO.OUT && binding.capability()==ItemRecipeCapability.CAP && binding.perTick()).count()==3,
                "Stock item normal/tick boundary preserves the final three tick contents");
        require(bindings.stream().filter(binding->binding.io()==IO.OUT && binding.capability()==FluidRecipeCapability.CAP && binding.perTick()).count()==2,
                "Stock fluid normal/tick boundary preserves the final two tick contents");
        var originalWidgets=bindings.stream().map(GtRecipeGrid.Binding::widget).toList();
        grid.rebind();
        for(var binding:bindings) {
            var regular=binding.io()==IO.IN?recipe.getInputContents(binding.capability()):recipe.getOutputContents(binding.capability());
            var tick=(binding.io()==IO.IN?recipe.tickInputs:recipe.tickOutputs).getOrDefault(binding.capability(),List.of());
            var expected=binding.index()<regular.size()?regular.get(binding.index()):tick.get(binding.index()-regular.size());
            require(binding.content()==expected,"Grid binding retains exact authored Content identity across normal/tick tables");
            require(binding.widget().getId().equals(binding.capability().slotName(binding.io(),binding.index())),"Grid retains native capability slot ids");
            require(binding.widget().getOverlay()!=null,"Grid restores native content overlays after GT addSlots/rebind");
            if(binding.widget() instanceof SlotWidget item) {
                require(item.getHandler().getSlotIndex()==binding.index(),"Native item handler retains the combined normal/tick index");
                require(Math.abs(item.getXEIChance()-binding.content().chance/(float)binding.content().maxChance)<0.0001,
                        "Native item chance is restored from its actual Content");
                if(binding.io()==IO.OUT && binding.index()==118) {
                    var nativeStack=item.getHandler().getItem();
                    require(nativeStack.hasTag() && "preserve".equals(nativeStack.getTag().getString("audit_native_tag")),
                            "Native tick-output handler preserves the authored item NBT after rebind");
                    require(nativeStack.getCount()==131072,"Native tick-output handler preserves the authored oversized stack count");
                }
            }
            if(binding.widget() instanceof TankWidget fluid) require(Math.abs(fluid.getXEIChance()-binding.content().chance/(float)binding.content().maxChance)<0.0001,
                    "Native fluid chance is restored from its actual Content");
        }
        require(grid.bindings().stream().map(GtRecipeGrid.Binding::widget).toList().equals(originalWidgets),"Rebind preserves all native widget identities");
        require(widget.getWidgetsByType(SlotWidget.class).stream().filter(slot->slot.getIngredientIO()==IngredientIO.OUTPUT).count()==120,
                "Stock grid renders every actual item output through native slots");
        require(widget.getWidgetsByType(TankWidget.class).stream().filter(slot->slot.getIngredientIO()==IngredientIO.OUTPUT).count()==24,
                "Stock grid renders every actual fluid output through native tanks");
        require(screen.content.registrations.stream().filter(slot->slot.role==RecipeIngredientRole.OUTPUT).count()==144,
                "Real LDLib category sees every combined normal/tick output slot");
        require(screen.viewport.scrollY()==screen.viewport.maxScrollY(),"Stock grid viewport reaches the exact last row");
        verifyThumbs(screen.viewport);
        verifyOc(screen,true);
    }
    private static RecipeScreen smallStock() {
        var type=new com.gregtechceu.gtceu.api.recipe.GTRecipeType(
                new ResourceLocation("gtl_enhancedcore","audit_unregistered_small_oc"),"audit").setMaxIOSize(1,1,0,0);
        var recipe=type.recipeBuilder(new ResourceLocation("gtl_enhancedcore","audit_small_oc"))
                .inputItems(new ItemStack(Items.IRON_INGOT)).outputItems(new ItemStack(Items.GOLD_INGOT))
                .EUt(32).duration(1200).buildRawRecipe();
        var wrapper=withPlayer(baselinePlayer,()->new GTRecipeWrapper(recipe));
        var size=wrapper.getWidget().getSize();
        return new RecipeScreen(new Adapter(size.width,size.height,wrapper,baselinePlayer),true);
    }
    private static void verifyOc(RecipeScreen screen,boolean compact) throws Exception {
        var widget=(GTRecipeWidget)screen.content.wrapper.getWidget();
        require(com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(screen.content.wrapper.recipe)==32,
                "OC fixture has real positive 32 EU/t tick input");
        require(widget.getWidgetsByType(GtRecipeGrid.class).size()==(compact?1:0),"OC fixture executes the intended stock template path");
        var voltage=(com.lowdragmc.lowdraglib.gui.widget.LabelWidget)GuiStyleClientChecks.field(GTRecipeWidget.class,"voltageTextWidget").get(widget);
        require(voltage!=null,"Positive EU/t constructs the real native voltage label");
        var oc=widget.getWidgetsByType(com.lowdragmc.lowdraglib.gui.widget.ButtonWidget.class).stream()
                .filter(button->button.getPositionX()==voltage.getPositionX() && button.getPositionY()==voltage.getPositionY()
                        && button.getSizeWidth()==voltage.getSizeWidth() && button.getSizeHeight()==voltage.getSizeHeight()).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var parameters=(List<com.lowdragmc.lowdraglib.gui.widget.LabelWidget>)GuiStyleClientChecks.field(GTRecipeWidget.class,"recipeParaTexts").get(widget);
        require(parameters.size()>=3,"Actual native recipe parameter list contains duration, total EU and EU/t");
        var footer=new ArrayList<com.lowdragmc.lowdraglib.gui.widget.Widget>(parameters);footer.add(voltage);footer.add(oc);
        screen.viewport.getInputHandler().handleMouseScrolled(screen.viewport.getRect().getX()+5,screen.viewport.getRect().getY()+5,-10000);
        require(screen.viewport.scrollY()==screen.viewport.maxScrollY(),"OC and parameter reachability uses actual scroll-to-bottom");
        for(var child:footer) {
            int x=child.getPositionX()-screen.content.x,y=child.getPositionY()-screen.content.y;
            require(x>=0 && y>=0 && x+child.getSizeWidth()<=screen.content.naturalWidth && y+child.getSizeHeight()<=screen.content.naturalHeight,
                    "Native OC/footer widget fits real natural dimensions: "+child.getClass().getSimpleName()+" "+x+","+y+" "+child.getSize());
            require(child.getPositionY()>=screen.viewport.getRect().getY()
                            && child.getPositionY()+child.getSizeHeight()<=screen.viewport.getRect().getY()+screen.viewport.contentHeight(),
                    "Native OC/footer widget is visibly reachable at the actual final scroll position");
        }
        int oldTier=GuiStyleClientChecks.field(GTRecipeWidget.class,"tier").getInt(widget);
        var oldParameters=parameters.stream().map(label->{try{return ((Component)GuiStyleClientChecks.field(label.getClass(),"component").get(label)).getString();}catch(Exception ex){throw new RuntimeException(ex);}}).toList();
        int mx=oc.getPositionX()+Math.max(1,oc.getSizeWidth()/2),my=oc.getPositionY()+Math.max(1,oc.getSizeHeight()/2);
        require(screen.viewport.getInputHandler().handleInput(mx,my,LEFT),"Visible native OC button accepts input through RecipeViewport");
        require(GuiStyleClientChecks.field(GTRecipeWidget.class,"tier").getInt(widget)==oldTier+1,"Real OC button increases the actual widget tier");
        var newParameters=parameters.stream().map(label->{try{return ((Component)GuiStyleClientChecks.field(label.getClass(),"component").get(label)).getString();}catch(Exception ex){throw new RuntimeException(ex);}}).toList();
        require(!newParameters.equals(oldParameters),"Native OC button updates the actual duration and energy parameter text");
        widget.setRecipeOC(2,false);
    }
    private static final Component LONG_ERROR=Component.literal(("配置保存失败 / Configuration validation failed: this long error must wrap, keep its error color, and remain fully readable on hover. ").repeat(5)+"END_OF_FULL_ERROR");
    private static Screen longError() throws Exception {
        var screen=GuiStyleClientChecks.construct("EnhancedConfigScreen",new Class<?>[]{Screen.class},(Object)null);
        var method=screen.getClass().getSuperclass().getDeclaredMethod("persistentError",Component.class);method.setAccessible(true);method.invoke(screen,LONG_ERROR);
        return screen;
    }
    private static void verifyLongError(Screen screen) throws Exception {
        require(GuiStyleClientChecks.field(screen.getClass(),"errorStatus").getBoolean(screen),"Long banner uses the actual ConfigPage error state");
        int left=GuiStyleClientChecks.field(screen.getClass(),"left").getInt(screen),body=GuiStyleClientChecks.field(screen.getClass(),"bodyWidth").getInt(screen);
        var mc=Minecraft.getInstance();
        require(mc.font.split(LONG_ERROR,body-12-mc.font.width("…")).size()>3,"Long error fixture exceeds the three-row banner");
        var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
        screen.render(graphics,left+8,screen.height-45,0);
        var pending=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Screen.class,"f_262730_").get(screen);
        require(pending!=null,"Hovering the real ConfigPage banner schedules a complete tooltip");
        @SuppressWarnings("unchecked") var lines=(List<net.minecraft.util.FormattedCharSequence>)
                net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(pending.getClass(),"f_262736_").get(pending);
        var full=new StringBuilder();for(var line:lines)line.accept((index,style,codepoint)->{full.appendCodePoint(codepoint);return true;});
        require(full.toString().replace(" ","").equals(LONG_ERROR.getString().replace(" ","")),"Hover tooltip retains the complete error including its final sentinel");
        screen.renderWithTooltip(graphics,left+8,screen.height-45,0);graphics.flush();
    }
    private static GTRecipe eyeRecipe() throws Exception {return eyeRecipe(GTLRecipeTypes.COSMOS_SIMULATION_RECIPES);}
    private static GTRecipe eyeRecipe(com.gregtechceu.gtceu.api.recipe.GTRecipeType type) throws Exception {
        var source = Minecraft.getInstance().gameDirectory.toPath().resolve("kubejs/server_scripts/space_equipment.js");
        String script = Files.readString(source);
        int start = script.indexOf("gtr.cosmos_simulation(\"cosmos_simulation1\")");
        require(start >= 0, "Copied instance contains the real cosmos_simulation1 recipe");
        String recipeText = script.substring(start, script.indexOf("});", start));
        var builder = type.recipeBuilder(new ResourceLocation("gtl_enhancedcore", "audit_eye"));
        var items = Pattern.compile("\"([0-9]+)x ([a-z0-9_:]+)\"").matcher(recipeText);
        int count = 0;
        while (items.find()) {
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(items.group(2)));
            require(item != null && item != Items.AIR, "Registered Eye output " + items.group(2));
            builder.outputItems(new ItemStack(item, Integer.parseInt(items.group(1)))); count++;
        }
        require(count == 120, "Eye fixture preserves all 120 actual item outputs");
        int fluidStart = recipeText.indexOf(".outputFluids(");
        int fluidEnd = recipeText.indexOf(")", fluidStart);
        var fluids = Pattern.compile("\"([a-z0-9_:]+) ([0-9]+)\"").matcher(recipeText.substring(fluidStart, fluidEnd));
        count = 0;
        while (fluids.find()) {
            var fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(fluids.group(1)));
            require(fluid != null, "Registered Eye fluid " + fluids.group(1));
            builder.outputFluids(FluidStack.create(fluid, Long.parseLong(fluids.group(2)))); count++;
        }
        require(count == 24, "Actual script has 24 fluid outputs; UI capacity is checked separately");
        builder.inputItems(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("kubejs", "quantum_chromodynamic_charge"))));
        builder.inputFluids(FluidStack.create(ForgeRegistries.FLUIDS.getValue(new ResourceLocation("gtceu", "cosmic_element")), 1_024_000));
        return builder.duration(1200).buildRawRecipe();
    }
    private static <T> T withPlayer(LocalPlayer player, Supplier<T> action) {
        var mc = Minecraft.getInstance(); var previous = mc.player;
        try { mc.player = player; return action.get(); }
        finally { mc.player = previous; }
    }
    private static final IJeiUserInput LEFT = new IJeiUserInput() {
        public InputConstants.Key getKey() { return InputConstants.Type.MOUSE.getOrCreate(0); }
        public int getModifiers() { return 0; }
        public boolean isSimulate() { return false; }
        public boolean is(net.minecraft.client.KeyMapping key) { return false; }
        public boolean is(mezz.jei.api.runtime.IJeiKeyMapping key) { return false; }
    };
    private static final class RecipeScreen extends Screen {
        final Adapter content;
        final boolean end;
        IRecipeLayoutWithButtons<GTRecipeWrapper> buttons;
        RecipeViewport<GTRecipeWrapper> viewport;
        RecipeTransferButton transfer;
        RecipeScreen(Adapter content, boolean end) {
            super(Component.literal(content.wrapper == null ? "JEI viewport" : "鸿蒙之眼 / Eye of Harmony"));
            this.content = content; this.end = end;
        }
        @Override protected void init() {
            transfer = RecipeTransferButton.create(content, () -> {});
            var bookmark = RecipeBookmarkButton.create(content, (BookmarkList)null, (RecipeBookmark<?, ?>)null);
            buttons = RecipeLayoutWithButtons.create(content, transfer, bookmark, List.of());
            require(buttons.getRecipeLayout() instanceof RecipeViewport<?>, "Real RecipeLayoutWithButtons.create applies the production Mixin");
            @SuppressWarnings("unchecked") var cast = (RecipeViewport<GTRecipeWrapper>)buttons.getRecipeLayout(); viewport = cast;
            buttons.updateBounds(30, 48);
            if (end) {
                if (viewport.maxScrollY() > 0) viewport.getInputHandler().handleMouseScrolled(viewport.getRect().getX()+8, viewport.getRect().getY()+8, -10000);
                if (viewport.maxScrollX() > 0) {
                    var r = viewport.getRect();
                    viewport.getInputHandler().handleInput(r.getX()+viewport.contentWidth()-1, r.getY()+viewport.contentHeight()+2, LEFT);
                    viewport.getInputHandler().handleMouseDragged(r.getX()+viewport.contentWidth(),r.getY()+viewport.contentHeight()+2,
                            LEFT.getKey(), 1000, 0);
                }
            }
        }
        @Override public void tick() { content.tick(); }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            renderBackground(g);
            g.fill(15, 27, width-15, height-20, 0xff333333);
            g.drawCenteredString(font, title, width/2, 12, 0xffe5e7eb);
            buttons.draw(g, -1000, -1000, partial);
            var r = viewport.getRect();
            g.drawString(font, "Recipe " + content.naturalWidth + " x " + content.naturalHeight + "  |  scroll "
                    + viewport.scrollX() + ", " + viewport.scrollY(), 20, height-15, 0xffb8c0cc, false);
            if (end) {
                int probeX = content.x + content.probeX + 5, probeY = content.y + content.probeY + 5;
                viewport.drawOverlays(g, probeX, probeY);
            }
        }
        void verify() throws Exception {
            var r = viewport.getRect();
            require(viewport.getRecipe() == content.getRecipe(), "Recipe identity survives viewport wrapping");
            require(viewport.getRecipeSlotsView() == content.slots, "Original JEI slot view identity is retained");
            require(r.getX()>=0 && r.getY()>=0 && r.getX()+r.getWidth()<=width && r.getY()+r.getHeight()<=height,
                    "Viewport fits the actual GUI dimensions");
            var area = transferArea(transfer);
            require(area.getX()>=r.getX()+r.getWidth(), "Real transfer button is placed to the right of the visible viewport");
            require(area.getX()+area.getWidth()<=width && area.getY()>=0 && area.getY()+area.getHeight()<=height,
                    "Transfer control stays inside the screen");
            require(!viewport.getInputHandler().handleMouseScrolled(-10,-10,-1), "Outside scrolling is not consumed");
            require(viewport.getSlotUnderMouse(r.getX()+r.getWidth()-1,r.getY()+r.getHeight()-1).isEmpty(),
                    "Scrollbars do not expose clipped recipe ingredients");
            int oldX=viewport.scrollX(),oldY=viewport.scrollY();
            viewport.getInputHandler().handleMouseScrolled(r.getX()+5,r.getY()+5,0);
            require(viewport.scrollX()==oldX && viewport.scrollY()==oldY, "Zero-delta scrolling does not move content");
            if (end) {
                require(viewport.scrollX()==viewport.maxScrollX() && viewport.scrollY()==viewport.maxScrollY(),
                        "Scroll wheel and scrollbar dragging reach both final boundaries");
                require(content.x==r.getX()-viewport.scrollX() && content.y==r.getY()-viewport.scrollY(),
                        "Original recipe coordinates move by exactly the scroll offsets");
                int px=content.x+content.probeX+5, py=content.y+content.probeY+5;
                require(viewport.getSlotUnderMouse(px,py).map(RecipeSlotUnderMouse::slot).orElse(null)==content.probe,
                        "Last JEI slot hover maps to the original drawable after scrolling");
                require(viewport.getIngredientUnderMouse(px,py,VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY).is(Items.DIAMOND),
                        "Ingredient query uses the same original scrolled JEI slot");
                require(content.overlayX==px && content.overlayY==py, "Tooltip rendering receives unchanged absolute mouse coordinates");
                verifyThumbs(viewport);
            } else if (content.wrapper == null) {
                require(viewport.maxScrollX()==0 && viewport.maxScrollY()==0, "Ordinary recipes keep their full dimensions without scrollbars");
                require(r.getWidth()==content.naturalWidth && r.getHeight()==content.naturalHeight, "Ordinary recipe dimensions are unchanged");
                require(viewport.getInputHandler().handleMouseScrolled(r.getX()+5,r.getY()+5,1), "Ordinary recipe scroll handling delegates to the original input");
            }
            if (content.wrapper != null) {
                var widget=(WidgetGroup)content.wrapper.getWidget();
                require(widget.getWidgetsByType(SlotWidget.class).stream().filter(slot->slot.getIngredientIO()==IngredientIO.OUTPUT).count()==120,
                        "Real Eye UI exposes all 120 native item output slots");
                require(widget.getWidgetsByType(TankWidget.class).stream().filter(slot->slot.getIngredientIO()==IngredientIO.OUTPUT).count()==24,
                        "Real Eye UI exposes all 24 native fluid output slots, including outputs beyond the old capacity");
                require(content.registrations.stream().filter(slot->slot.role==RecipeIngredientRole.OUTPUT
                        && slot.ingredients.stream().anyMatch(ItemStack.class::isInstance)).count()==120,
                        "Real LDLib category registers every item output with JEI");
                require(content.registrations.stream().filter(slot->slot.role==RecipeIngredientRole.OUTPUT
                        && !slot.ingredients.isEmpty() && slot.ingredients.stream().noneMatch(ItemStack.class::isInstance)).count()==24,
                        "Real LDLib category registers every fluid output with JEI");
                require(content.registrations.stream().allMatch(slot->slot.name!=null), "Every JEI registration retains its original slot name");
                require(content.registrations.stream().allMatch(slot->slot.x>=0 && slot.y>=0
                        && slot.x+18<=content.naturalWidth && slot.y+18<=content.naturalHeight),
                        "Every actual JEI slot registration remains inside the recomputed natural recipe dimensions");
                require(content.wrapper.recipe.getOutputContents(ItemRecipeCapability.CAP).size()==120, "Recipe keeps all actual item outputs");
                require(content.wrapper.recipe.getOutputContents(FluidRecipeCapability.CAP).size()==24, "Recipe keeps all 24 actual fluid outputs");
                require(viewport.maxScrollY()>0, "Real Eye recipe is taller than the available viewport");
                var grids=widget.getWidgetsByType(GtRecipeGrid.class);
                var typeUI=content.wrapper.recipe.recipeType.getRecipeUI();
                String diagnostic="gridCount="+grids.size()+", widget="+widget.getSizeWidth()+"x"+widget.getSizeHeight()
                        +", hasCustomUI="+typeUI.hasCustomUI()+", originalWidth="+typeUI.getOriginalWidth()
                        +", jeiSize="+typeUI.getJEISize().width+"x"+typeUI.getJEISize().height
                        +", children="+widget.widgets.stream().map(child->child.getClass().getSimpleName()+"["+child.getId()+"]@"
                            +child.getSelfPositionX()+","+child.getSelfPositionY()+":"+child.getSizeWidth()+"x"+child.getSizeHeight()).toList();
                if(typeUI.hasCustomUI()) {
                    require(grids.isEmpty(),"Custom Eye template intentionally keeps its native type-specific UI: "+diagnostic);
                } else {
                    require(grids.size()==1,"Stock oversized Eye template uses exactly one compact native grid: "+diagnostic);
                    var grid=grids.getFirst();
                    require(grid.columns()>=4 && grid.columns()<=16,"Compact recipe grid adapts its columns to GUI width");
                    require(grid.bindings().stream().filter(binding->binding.io()==IO.OUT).count()==144,"All 144 output bindings retain their native capability metadata");
                }

            }
            require(Minecraft.getInstance().player==baselinePlayer, "Scoped lifecycle player is restored after GT wrapper construction and rendering");
        }
    }
    private static GuiIconButton transferAreaButton(RecipeTransferButton transfer) throws Exception {
        var field=GuiStyleClientChecks.field(transfer.getClass(),"button"); return (GuiIconButton)field.get(transfer);
    }
    private static mezz.jei.common.util.ImmutableRect2i transferArea(RecipeTransferButton transfer) throws Exception {
        return transferAreaButton(transfer).getArea();
    }
    /** Draws the real RecipesGui and its real RecipeLayout/RecipeSlot objects, after the fresh-world lifecycle, with scoped player fixtures. */
    private static final class RealRecipeScreen extends Screen {
        final RecipeScreen setup;
        final boolean end, mixed, longFirst;
        List<IRecipeLayoutWithButtons<?>> visibleLayouts=List.of();
        final RecipesGui real;
        final IRecipeLayoutDrawable<GTRecipeWrapper> independentlyCreated;
        RecipeViewport<?> viewport;
        List<IRecipeSlotDrawable> actualOutputs;
        RealRecipeScreen(boolean end) throws Exception {this(end,false,false);}
        RealRecipeScreen(boolean end,boolean mixed,boolean longFirst) throws Exception {
            super(Component.literal("Real JEI RecipesGui Eye audit")); this.end=end;this.mixed=mixed;this.longFirst=longFirst;
            setup=open(true,false);
            var runtime=Internal.getOptionalJeiRuntime().orElseThrow();
            require(runtime.getIngredientManager()!=null,"Real JEI IngredientManager is available after the fresh-world lifecycle");
            var category=new GTRecipeTypeCategory(runtime.getJeiHelpers(),GTLRecipeTypes.COSMOS_SIMULATION_RECIPES);
            independentlyCreated=withPlayer(setup.content.player,()->runtime.getRecipeManager()
                    .createRecipeLayoutDrawable(category,setup.content.wrapper,FocusGroup.EMPTY).orElseThrow(
                            ()->new AssertionError("Actual JEI recipe manager rejected the Eye layout")));
            require(independentlyCreated.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).size()==144,
                    "Real RecipeManager layout contains all 144 output RecipeSlot views");
            List<GTRecipeWrapper> shown;
            if(mixed) {
                var shortRecipe=GTLRecipeTypes.COSMOS_SIMULATION_RECIPES.recipeBuilder(new ResourceLocation("gtl_enhancedcore","audit_short_eye"))
                        .inputItems(Items.IRON_INGOT).outputItems(Items.DIAMOND).duration(20).buildRawRecipe();
                var shortWrapper=withPlayer(setup.content.player,()->new GTRecipeWrapper(shortRecipe));
                shown=longFirst?List.of(setup.content.wrapper,shortWrapper):List.of(shortWrapper,setup.content.wrapper);
            } else shown=List.of(setup.content.wrapper);
            var mc=Minecraft.getInstance(); var previous=mc.screen;
            try {
                withPlayer(setup.content.player,()->{runtime.getRecipesGui().showRecipes(category,shown,List.of());return null;});
                require(mc.screen instanceof RecipesGui,"Real IRecipesGui.showRecipes opens the production RecipesGui");
                real=(RecipesGui)mc.screen;
                // The audit renders two wrappers around JEI's singleton; a previous wrapper must not become its own background.
                GuiStyleClientChecks.field(real.getClass(),"parentScreen").set(real,null);
            } finally {mc.screen=previous;}
        }
        private <T> T scoped(Supplier<T> action) {
            var mc=Minecraft.getInstance();var previous=mc.screen;
            try {mc.screen=real;return withPlayer(setup.content.player,action);}
            finally {mc.screen=previous;}
        }
        @Override protected void init() {
            scoped(()->{real.init(minecraft,width,height);return null;});
            try {
                Object layouts=GuiStyleClientChecks.field(real.getClass(),"layouts").get(real);
                @SuppressWarnings("unchecked") var children=(List<IRecipeLayoutWithButtons<?>>)GuiStyleClientChecks
                        .field(layouts.getClass(),"recipeLayoutsWithButtons").get(layouts);
                require(!children.isEmpty(),"Real RecipesGui builds a visible recipe layout");
                visibleLayouts=children;
                if(children.stream().noneMatch(child->child.getRecipeLayout().getRecipe()==setup.content.wrapper)) {
                    var logic=(mezz.jei.gui.recipes.IRecipeGuiLogic)GuiStyleClientChecks.field(real.getClass(),"logic").get(real);
                    require(mixed && logic.getRecipesPerPage()==1,"Long mixed recipe uses the existing native second page when only one card fits");
                    scoped(()->{logic.nextPage();return null;});
                    @SuppressWarnings("unchecked") var next=(List<IRecipeLayoutWithButtons<?>>)GuiStyleClientChecks
                            .field(layouts.getClass(),"recipeLayoutsWithButtons").get(layouts);
                    visibleLayouts=next;
                }
                var longLayout=visibleLayouts.stream().filter(child->child.getRecipeLayout().getRecipe()==setup.content.wrapper).findFirst().orElseThrow();
                require(longLayout.getRecipeLayout() instanceof RecipeViewport<?>,"Visible RecipesGui layout uses the production RecipeViewport Mixin");
                viewport=(RecipeViewport<?>)longLayout.getRecipeLayout();
                actualOutputs=viewport.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                        .map(IRecipeSlotDrawable.class::cast).toList();
                if(end) {
                    var r=viewport.getRect();
                    scoped(()->{real.mouseScrolled(r.getX()+8,r.getY()+8,-10000);return null;});
                    if(viewport.maxScrollX()>0) {
                        viewport.getInputHandler().handleInput(r.getX()+viewport.contentWidth()-1,r.getY()+viewport.contentHeight()+2,LEFT);
                        viewport.getInputHandler().handleMouseDragged(r.getX()+viewport.contentWidth(),r.getY()+viewport.contentHeight()+2,LEFT.getKey(),1000,0);
                    }
                }
            } catch (ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
        }
        @Override public void tick() {scoped(()->{real.tick();return null;});}
        private IRecipeSlotDrawable finalSlot() {
            return actualOutputs.stream().max(java.util.Comparator.comparingInt((IRecipeSlotDrawable slot)->slot.getRect().getY())
                    .thenComparingInt(slot->slot.getRect().getX())).orElseThrow();
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            int mx=-1000,my=-1000;
            if(end && viewport!=null) {
                var slot=finalSlot().getRect(); var r=viewport.getRect();
                mx=r.getX()+slot.getX()-viewport.scrollX()+5;my=r.getY()+slot.getY()-viewport.scrollY()+5;
            }
            final int pointerX=mx,pointerY=my;
            scoped(()->{real.render(g,pointerX,pointerY,partial);return null;});
        }
        @Override public boolean mouseScrolled(double x,double y,double delta) {return scoped(()->real.mouseScrolled(x,y,delta));}
        @Override public void removed() {scoped(()->{real.removed();return null;});setup.removed();}
        void verify() {
            var area=real.getArea();
            require(area.getX()>=0 && area.getY()>=0 && area.getX()+area.getWidth()<=width && area.getY()+area.getHeight()<=height,
                    "Actual RecipesGui outer area fits the screen");
            var r=viewport.getRect();
            require(r.getX()>=area.getX() && r.getY()>=area.getY() && r.getX()+r.getWidth()<=area.getX()+area.getWidth()
                    && r.getY()+r.getHeight()<=area.getY()+area.getHeight(),"Visible real JEI recipe layout fits inside its actual outer area");
            require(actualOutputs.size()==144,"Visible real JEI recipe has every output slot");
            if(mixed) {
                var first=visibleLayouts.getFirst().getRecipeLayout().getRectWithBorder();
                for(var child:visibleLayouts) {
                    var card=child.getRecipeLayout().getRectWithBorder();
                    require(card.getWidth()==first.getWidth() && card.getHeight()==first.getHeight(),"Mixed-height cards reserve identical visible outer dimensions");
                    require(card.getX()>=area.getX() && card.getY()>=area.getY() && card.getX()+card.getWidth()<=area.getX()+area.getWidth()
                            && card.getY()+card.getHeight()<=area.getY()+area.getHeight(),"Mixed card including border fits inside the actual JEI panel");
                }
                for(int i=0;i<visibleLayouts.size();i++)for(int j=i+1;j<visibleLayouts.size();j++) {
                    var a=visibleLayouts.get(i).getRecipeLayout().getRectWithBorder();var b=visibleLayouts.get(j).getRecipeLayout().getRectWithBorder();
                    require(a.getX()+a.getWidth()<=b.getX() || b.getX()+b.getWidth()<=a.getX()
                            || a.getY()+a.getHeight()<=b.getY() || b.getY()+b.getHeight()<=a.getY(),"Actual mixed JEI cards do not overlap");
                }
                require(true,"Mixed scene "+(longFirst?"long-first":"short-first")+" visible card count="+visibleLayouts.size());
            }
            require(actualOutputs.stream().filter(slot->slot.getDisplayedItemStack().isPresent()).count()==120,
                    "Actual JEI IngredientManager resolves all 120 item output slots");
            require(actualOutputs.stream().filter(slot->slot.getDisplayedItemStack().isEmpty() && slot.getDisplayedIngredient().isPresent()).count()==24,
                    "Actual JEI IngredientManager resolves all 24 fluid output slots");
            if(end) {
                require(viewport.scrollY()==viewport.maxScrollY(),"Real RecipesGui mouse scrolling reaches the final output rows");
                var slot=finalSlot();var rect=slot.getRect();
                int px=r.getX()+rect.getX()-viewport.scrollX()+5,py=r.getY()+rect.getY()-viewport.scrollY()+5;
                require(viewport.getSlotUnderMouse(px,py).map(RecipeSlotUnderMouse::slot).orElse(null)==slot,
                        "Actual final JEI RecipeSlot hover survives scrolling");
                require(!slot.getTooltip().isEmpty(),"Actual final fluid slot produces its normal ingredient tooltip");
                verifyThumbs(viewport);
            }
            require(Minecraft.getInstance().player==baselinePlayer,"Real RecipesGui restores the lifecycle player after scoped draw/tick/input");
        }
    }
    private static void verifyThumbs(RecipeViewport<?> viewport) {
        for(boolean horizontal:new boolean[]{false,true}) {
            int maximum=horizontal?viewport.maxScrollX():viewport.maxScrollY();
            if(maximum==0)continue;
            var thumb=viewport.getScrollThumb(horizontal);
            double mx=thumb.getX()+thumb.getWidth()/2.0,my=thumb.getY()+thumb.getHeight()/2.0;
            int before=horizontal?viewport.scrollX():viewport.scrollY();
            require(viewport.getInputHandler().handleInput(mx,my,LEFT),"Real scrollbar thumb accepts left click");
            require((horizontal?viewport.scrollX():viewport.scrollY())==before,"Grabbing the thumb at its center does not jump");
            require(viewport.getInputHandler().handleMouseDragged(mx,my,LEFT.getKey(),0,0),"Real scrollbar consumes a zero-distance drag");
            require((horizontal?viewport.scrollX():viewport.scrollY())==before,"A zero-distance thumb drag preserves the scroll position");
            var r=viewport.getRect();
            double startX=horizontal?r.getX()+thumb.getWidth()/2.0:mx;
            double startY=horizontal?my:r.getY()+thumb.getHeight()/2.0;
            viewport.getInputHandler().handleMouseDragged(startX,startY,LEFT.getKey(),-10000,-10000);
            require((horizontal?viewport.scrollX():viewport.scrollY())==0,"Thumb drag reaches the exact leading boundary");
            double endX=horizontal?r.getX()+viewport.contentWidth()-thumb.getWidth()/2.0:mx;
            double endY=horizontal?my:r.getY()+viewport.contentHeight()-thumb.getHeight()/2.0;
            viewport.getInputHandler().handleMouseDragged(endX,endY,LEFT.getKey(),10000,10000);
            require((horizontal?viewport.scrollX():viewport.scrollY())==maximum,"Thumb drag reaches the exact trailing boundary");
        }
    }
    private static final class Registration {
        final RecipeIngredientRole role;
        final int x,y;
        final List<Object> ingredients=new ArrayList<>();
        String name;
        Registration(RecipeIngredientRole role,int x,int y) {this.role=role;this.x=x;this.y=y;}
    }
    private static final class RegistrationCategory extends ModularUIRecipeCategory<GTRecipeWrapper> {
        public mezz.jei.api.recipe.RecipeType<GTRecipeWrapper> getRecipeType() {
            return mezz.jei.api.recipe.RecipeType.create("gtl_enhancedcore","audit_eye",GTRecipeWrapper.class);
        }
        public Component getTitle() { return Component.literal("Eye JEI slot registration"); }
        public mezz.jei.api.gui.drawable.IDrawable getIcon() {return null;}
    }
    private static final class Adapter implements IRecipeLayoutDrawable<GTRecipeWrapper> {
        final int naturalWidth,naturalHeight,probeX,probeY;
        final GTRecipeWrapper wrapper;
        final LocalPlayer player;
        final IRecipeSlotDrawable probe;
        final IRecipeSlotsView slots;
        final IRecipeCategory<GTRecipeWrapper> category;
        final List<Registration> registrations = new ArrayList<>();
        int x,y,overlayX=Integer.MIN_VALUE,overlayY=Integer.MIN_VALUE;
        Adapter(int width,int height,GTRecipeWrapper wrapper,LocalPlayer player) {
            naturalWidth=width; naturalHeight=height; this.wrapper=wrapper; this.player=player;
            if (wrapper != null) {
                var builder=(IRecipeLayoutBuilder)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IRecipeLayoutBuilder.class},
                        (proxy, method, args)-> {
                            if (method.getName().equals("addSlot")) {
                                var entry=new Registration((RecipeIngredientRole)args[0], args.length>1?(int)args[1]:0, args.length>2?(int)args[2]:0);
                                registrations.add(entry);
                                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IRecipeSlotBuilder.class},(slot, operation, values)-> {
                                    if (operation.getName().equals("addIngredients")) entry.ingredients.addAll((List<?>)values[1]);
                                    if (operation.getName().equals("setSlotName")) entry.name=(String)values[0];
                                    return operation.getReturnType().isInstance(slot)?slot:null;
                                });
                            }
                            return null;
                        });
                withPlayer(player,()->{ new RegistrationCategory().setRecipe(builder,wrapper,null);return null; });
            }
            probeX=width-22; probeY=height-22;
            var typed=TypedIngredient.createUnvalidated(VanillaTypes.ITEM_STACK,new ItemStack(Items.DIAMOND));
            probe=(IRecipeSlotDrawable)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IRecipeSlotDrawable.class},(proxy,method,args)-> {
                return switch(method.getName()) {
                    case "getRole" -> RecipeIngredientRole.OUTPUT;
                    case "getSlotName" -> Optional.of("audit-final-slot");
                    case "getDisplayedIngredient" -> args==null || args.length==0 ? Optional.of(typed) : Optional.of(typed.getIngredient());
                    case "getAllIngredientsList" -> List.of(typed);
                    case "getAllIngredients" -> java.util.stream.Stream.of(typed);
                    case "getRect", "getAreaIncludingBackground" -> new Rect2i(probeX,probeY,18,18);
                    case "getTooltip" -> method.getReturnType()==List.class ? List.of(Component.literal("JEI final-slot coordinate probe")) : null;
                    case "isMouseOver" -> ((Number)args[0]).doubleValue()>=probeX && ((Number)args[0]).doubleValue()<probeX+18
                            && ((Number)args[1]).doubleValue()>=probeY && ((Number)args[1]).doubleValue()<probeY+18;
                    case "equals" -> proxy==args[0]; case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Audit final JEI slot";
                    default -> null;
                };
            });
            slots=()->List.of(probe);
            @SuppressWarnings("unchecked") var c=(IRecipeCategory<GTRecipeWrapper>)Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IRecipeCategory.class},(proxy,method,args)->null); category=c;
        }
        public void setPosition(int x,int y) { this.x=x; this.y=y; if(wrapper!=null) withPlayer(player,()->{wrapper.setRecipeWidget(x,y);return null;}); }
        public void drawRecipe(GuiGraphics g,int mx,int my) {
            if(wrapper!=null) withPlayer(player,()->{wrapper.draw(g,mx,my,0);return null;});
            else {
                g.fill(x,y,x+naturalWidth,y+naturalHeight,0xff494949);
                for(int col=0;col<naturalWidth/22;col++) for(int row=0;row<naturalHeight/22;row++) {
                    g.fill(x+col*22+3,y+row*22+3,x+col*22+21,y+row*22+21,0xff252525);
                    g.renderItem(new ItemStack((col+row)%2==0?Items.DIAMOND:Items.IRON_INGOT),x+col*22+4,y+row*22+4);
                }
            }
        }
        public void drawOverlays(GuiGraphics g,int mx,int my) {
            overlayX=mx;overlayY=my;
            if(getSlotUnderMouse(mx,my).isPresent()) g.renderTooltip(Minecraft.getInstance().font,Component.literal("JEI final-slot coordinate probe"),mx,my);
        }
        public boolean isMouseOver(double mx,double my) { return mx>=x && mx<x+naturalWidth && my>=y && my<y+naturalHeight; }
        public <T> Optional<T> getIngredientUnderMouse(int mx,int my,IIngredientType<T> type) { return getRecipeSlotUnderMouse(mx,my).flatMap(slot->slot.getDisplayedIngredient(type)); }
        public Optional<IRecipeSlotDrawable> getRecipeSlotUnderMouse(double mx,double my) { return getSlotUnderMouse(mx,my).map(RecipeSlotUnderMouse::slot); }
        public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mx,double my) {
            return probe.isMouseOver(mx-x,my-y)?Optional.of(new RecipeSlotUnderMouse(probe,x,y)):Optional.empty();
        }
        public Rect2i getRect() { return new Rect2i(x,y,naturalWidth,naturalHeight); }
        public Rect2i getRectWithBorder() { return new Rect2i(x-4,y-4,naturalWidth+8,naturalHeight+8); }
        public Rect2i getRecipeTransferButtonArea() { return new Rect2i(naturalWidth+6,naturalHeight-13,13,13); }
        public Rect2i getRecipeBookmarkButtonArea() { return new Rect2i(naturalWidth+6,naturalHeight-28,13,13); }
        public IRecipeSlotsView getRecipeSlotsView() { return slots; }
        public IRecipeCategory<GTRecipeWrapper> getRecipeCategory() { return category; }
        public GTRecipeWrapper getRecipe() { return wrapper; }
        public IJeiInputHandler getInputHandler() { return new IJeiInputHandler() {
            public ScreenRectangle getArea() { return new ScreenRectangle(x,y,naturalWidth,naturalHeight); }
            public boolean handleMouseScrolled(double mx,double my,double delta) { return true; }
            public boolean handleInput(double mx,double my,IJeiUserInput input) {
                if (wrapper == null || input.isSimulate()) return false;
                // LDLib ClickData reads MouseHandler.activeButton rather than mouseClicked's argument.
                // Native GLFW dispatch sets this before Screen input; reproduce that scoped state in this adapter.
                var mouse = Minecraft.getInstance().mouseHandler;
                try {
                    // 1.20.1 official activeButton = SRG f_91510_; Forge resolves both dev and reobfuscated runtime.
                    var activeButton = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(
                            net.minecraft.client.MouseHandler.class, "f_91510_");
                    int previous = activeButton.getInt(mouse);
                    try {
                        activeButton.setInt(mouse, input.getKey().getValue());
                        return withPlayer(player,()->wrapper.getWidget().mouseClicked(mx,my,input.getKey().getValue()));
                    } finally { activeButton.setInt(mouse, previous); }
                } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
            }
        }; }
        public void tick() { if(wrapper!=null) withPlayer(player,()->{wrapper.updateScreen();return null;}); }
    }
}
