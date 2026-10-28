package com.gtl.enhancedcore.client.plot;

import com.gtl.enhancedcore.common.world.plot.PlotLayout;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A local recipe workbench. No terrain or creation state is changed before confirmation. */
public final class CreateWorldPlotScreen extends Screen {
    private static final String KEY = "gtl_enhancedcore.create_world_plot.";
    private static final String[] PAGES = {"page_layout", "page_roads", "page_materials", "page_height"};
    private static final String[] MATERIAL_KEYS = {"ground", "plot_edge_block", "road_block", "border_block", "line_block", "marker_block"};
    private static final int[] COLORS = {0xffd4d5ce,0xffe06101,0xffa9309f,0xff2489c7,
            0xfff0af15,0xff5ea819,0xffd5658f,0xff36393d,0xff7d7d73,0xff157788,
            0xff64209c,0xff2c2e8f,0xff603b20,0xff495b24,0xff8e2020,0xff101114};
    private static final int INK = 0xffe8e1d3, MUTED = 0xffa7a69f, GOLD = 0xffd6bc81;
    private final Screen parent;
    private final WorldCreationUiState state;
    private final Consumer<PlotSettings> accepted;
    private final Component confirmLabel;
    private final Map<String,String> inputs = new HashMap<>();
    private final PlotPreviewRenderer.Cached single = new PlotPreviewRenderer.Cached();
    private final PlotPreviewRenderer.Cached four = new PlotPreviewRenderer.Cached();
    private final Map<PlotSettings.RoadPreset,PlotPreviewRenderer.Cached> thumbnails = new EnumMap<>(PlotSettings.RoadPreset.class);
    private final List<Label> labels = new ArrayList<>();
    private final List<AbstractWidget> editorWidgets = new ArrayList<>(), fixedWidgets = new ArrayList<>();
    private final Map<AbstractWidget,Integer> contentY = new IdentityHashMap<>();
    private PlotSettings draft, validatedPreview;
    private int page, materialTarget, left, top, bodyWidth, bodyHeight;
    private int editorX, editorY, editorWidth, editorBottom, previewX, previewWidth, scroll, contentHeight, diagramY = -1;
    private final int[] pageScroll = new int[4];
    private Component error = CommonComponents.EMPTY;
    private FlatButton done, share;
    private boolean building, grid;
    private record Label(Component text, int x, int y, int maxWidth, boolean heading) {}

    public CreateWorldPlotScreen(CreateWorldScreen parent) {
        super(tr("title")); this.parent = parent; state = parent.getUiState();
        accepted = null; confirmLabel = tr("confirm");
        draft = CreateWorldPlotState.get(state); validatedPreview = draft; resetInputs();
    }
    /** Reuses the same live editor for personal dimensions and server templates. */
    public CreateWorldPlotScreen(Screen parent, PlotSettings initial, Component title,
                                 Component confirmLabel, Consumer<PlotSettings> accepted) {
        super(title);
        this.parent = Objects.requireNonNull(parent); this.state = null;
        this.accepted = Objects.requireNonNull(accepted); this.confirmLabel = Objects.requireNonNull(confirmLabel);
        draft = Objects.requireNonNull(initial); validatedPreview = draft; resetInputs();
    }
    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) { return Component.translatable(KEY + key, args); }
    private void resetInputs() {
        inputs.put("ground", draft.groundBlock()); inputs.put("road_block", draft.roadBlock());
        inputs.put("plot_edge_block", draft.plotEdgeBlock()); inputs.put("plot_edge_width", "" + draft.plotEdgeWidth());
        inputs.put("border_block", draft.borderBlock()); inputs.put("marker_block", draft.markerBlock());
        inputs.put("line_block", draft.lineBlock()); inputs.put("size", "" + draft.plotChunks());
        inputs.put("road_width", "" + draft.roadWidth()); inputs.put("border_width", "" + draft.borderWidth());
        inputs.put("marker_size", "" + draft.markerSize()); inputs.put("dash_length", "" + draft.dashLength());
        inputs.put("dash_gap", "" + draft.dashGap()); inputs.put("bedrock_y", "" + draft.bedrockY());
        inputs.put("surface_y", "" + draft.surfaceY());
    }
    @Override protected void init() {
        building = true; labels.clear(); editorWidgets.clear(); fixedWidgets.clear(); contentY.clear(); diagramY = -1;
        bodyWidth = Math.min(900, width - 12); bodyHeight = Math.min(500, height - 12);
        left = (width - bodyWidth) / 2; top = (height - bodyHeight) / 2;
        editorX = left + 12; editorY = top + 65; editorBottom = top + bodyHeight - 46;
        editorWidth = (bodyWidth - 42) * 56 / 100;
        previewX = editorX + editorWidth + 18; previewWidth = left + bodyWidth - 12 - previewX;
        int tw = (editorWidth + 4) / PAGES.length;
        for (int i = 0; i < PAGES.length; i++) {
            int selected = i;
            fixed(() -> tr(PAGES[selected]), editorX + i * tw, top + 37, tw - 3, 20,
                    () -> { pageScroll[page] = scroll; page = selected; rebuildWidgets(); }, () -> page == selected);
        }
        scroll = pageScroll[page];
        contentHeight = switch (page) { case 0 -> layoutPage(); case 1 -> roadsPage(); case 2 -> materialsPage(); default -> heightPage(); };
        int buttonWidth = Math.min(130, bodyWidth / 3);
        done = fixed(() -> confirmLabel, left + bodyWidth - buttonWidth - 12, top + bodyHeight - 27, buttonWidth, 20, this::commit, () -> true);
        fixed(() -> CommonComponents.GUI_CANCEL, left + 12, top + bodyHeight - 27, 58, 20, this::onClose, () -> false);
        share = fixed(() -> tr("share_presets"), left + 76, top + bodyHeight - 27, Math.min(100, bodyWidth - buttonWidth - 100), 20,
                this::openShare, () -> false);
        fixed(() -> tr(grid ? "grid_on" : "grid_off"), previewX, top + 37, previewWidth, 20, () -> grid = !grid, () -> grid);
        building = false; validate(); updateScroll(scroll);
    }
    private int layoutPage() {
        int y = heading("plot_dimensions", 0);
        int w = (editorWidth - 12) / 5;
        for (int i = 1; i <= 10; i++) {
            int size = i;
            edit(() -> Component.literal(size + "×" + size), editorX + ((i-1)%5)*(w+3), y + ((i-1)/5)*23,
                    w, 20, () -> { inputs.put("size", "" + size); validate(); rebuildWidgets(); }, () -> draft.plotChunks() == size);
        }
        y += 51; number("size", editorX, y, editorWidth, 1, 11, true); y += 42;
        y = heading("center_markers", y);
        int half = (editorWidth - 6) / 2;
        number("plot_edge_width", editorX, y, half, 0, 8, true);
        FlatButton edgeColor = edit(() -> tr("edit_plot_edge_color"), editorX + half + 6, y + 13, half, 20,
                () -> { pageScroll[page] = scroll; materialTarget = 1; page = 2; rebuildWidgets(); }, () -> false);
        edgeColor.indicator = () -> colorOf(inputs.get("plot_edge_block"));
        y += 42;
        options("marker_mode", List.of(PlotSettings.MarkerMode.values()), () -> draft.markerMode(),
                value -> draft = draft.withMarkerMode(value), y, 3, true); y += 28;
        number("marker_size", editorX, y, editorWidth, 0, 5, draft.markerMode() != PlotSettings.MarkerMode.NONE); y += 42;
        y = hint(tr("marker_auto_hint"), y);
        return y + 8;
    }
    private int roadsPage() {
        int y = heading("road_templates", 0);
        y = hint(tr("road_templates_hint"), y);
        int columns = editorWidth >= 380 ? 3 : 2, gap = 5, w = (editorWidth - (columns-1)*gap) / columns;
        var values = PlotSettings.RoadPreset.values();
        for (int i = 0; i < values.length; i++) {
            var preset = values[i];
            FlatButton card = edit(() -> tr("preset_" + preset.name().toLowerCase(Locale.ROOT)), editorX + (i%columns)*(w+gap),
                    y + (i/columns)*37, w, 32, () -> applyRoadPreset(preset), () -> matchesPreset(preset));
            card.preset = preset;
            card.setTooltip(Tooltip.create(tr("road_templates_hint")));
        }
        y += ((values.length + columns-1)/columns)*37 + 7;
        y = heading("road_layout", y);
        y = hint(tr("road_layout_hint"), y);
        options("road_layout", List.of(PlotSettings.RoadStyle.HIGHWAY, PlotSettings.RoadStyle.AVENUE,
                PlotSettings.RoadStyle.GARDEN, PlotSettings.RoadStyle.OUTLINE, PlotSettings.RoadStyle.NONE),
                () -> canonical(draft.roadStyle()), value -> draft = draft.withRoadStyle(value), y, 3, true); y += 50;
        int half = (editorWidth-6)/2;
        number("road_width", editorX, y, half, 1, 32, roadsEnabled());
        number("border_width", editorX+half+6, y, half, 0, 8, draft.roadStyle() != PlotSettings.RoadStyle.NONE); y += 45;
        y = heading("lane_markings", y);
        boolean lines = roadsEnabled();
        y = hint(tr(lines ? "lane_markings_hint" : "markings_unavailable"), y);
        options("line_style", List.of(PlotSettings.LineStyle.values()), () -> draft.lineStyle(), value -> {
            draft = draft.withLineStyle(value);
            if (value == PlotSettings.LineStyle.DOUBLE && parseOr("road_width", draft.roadWidth()) < 2) inputs.put("road_width", "2");
        }, y, 3, lines); y += 50;
        boolean dashed = lines && (draft.lineStyle() == PlotSettings.LineStyle.DASHED || draft.lineStyle() == PlotSettings.LineStyle.CROSSWALK);
        number("dash_length", editorX, y, half, 1, 32, dashed);
        number("dash_gap", editorX+half+6, y, half, 1, 32, dashed); y += 43;
        FlatButton palette = edit(() -> tr("edit_lane_color"), editorX, y, editorWidth, 21,
                () -> { pageScroll[page] = scroll; materialTarget = 3; page = 2; rebuildWidgets(); }, () -> false);
        palette.active = lines && draft.lineStyle() != PlotSettings.LineStyle.NONE;
        return y + 29;
    }
    private static PlotSettings.RoadStyle canonical(PlotSettings.RoadStyle style) {
        return style == PlotSettings.RoadStyle.RING || style == PlotSettings.RoadStyle.BOULEVARD ? PlotSettings.RoadStyle.HIGHWAY : style;
    }
    private boolean roadsEnabled() { return draft.roadStyle() != PlotSettings.RoadStyle.NONE && draft.roadStyle() != PlotSettings.RoadStyle.OUTLINE; }
    private boolean matchesPreset(PlotSettings.RoadPreset preset) {
        PlotSettings other = draft.withRoadPreset(preset);
        return draft.roadBlock().equals(other.roadBlock()) && draft.borderBlock().equals(other.borderBlock())
                && draft.lineBlock().equals(other.lineBlock()) && canonical(draft.roadStyle()) == canonical(other.roadStyle())
                && draft.roadWidth() == other.roadWidth() && draft.borderWidth() == other.borderWidth()
                && draft.lineStyle() == other.lineStyle() && draft.dashLength() == other.dashLength() && draft.dashGap() == other.dashGap();
    }
    private void applyRoadPreset(PlotSettings.RoadPreset preset) {
        draft = draft.withRoadPreset(preset);
        for (String key : List.of("road_block", "border_block", "line_block", "road_width", "border_width", "dash_length", "dash_gap"))
            inputs.put(key, switch (key) { case "road_block" -> draft.roadBlock(); case "border_block" -> draft.borderBlock();
                case "line_block" -> draft.lineBlock(); case "road_width" -> ""+draft.roadWidth(); case "border_width" -> ""+draft.borderWidth();
                case "dash_length" -> ""+draft.dashLength(); default -> ""+draft.dashGap(); });
        validate(); rebuildWidgets();
    }
    private int materialsPage() {
        int y = heading("material_surface", 0), columns = editorWidth >= 280 ? 3 : 2, gap = 4;
        int w = (editorWidth - (columns-1)*gap) / columns;
        for (int i=0;i<MATERIAL_KEYS.length;i++) {
            int target = i;
            FlatButton button = edit(() -> tr(MATERIAL_KEYS[target]), editorX+(i%columns)*(w+gap), y+(i/columns)*24,
                    w, 21, () -> { materialTarget = target; rebuildWidgets(); }, () -> materialTarget == target);
            button.indicator = () -> colorOf(inputs.get(MATERIAL_KEYS[target]));
        }
        y += ((MATERIAL_KEYS.length+columns-1)/columns)*24 + 10;
        String slot = MATERIAL_KEYS[materialTarget];
        y = heading("concrete_palette", y);
        y = hint(blockName(inputs.get(slot)), y);
        int cols = editorWidth >= 240 ? 8 : 4, chip = Math.min(30, (editorWidth - (cols-1)*5)/cols);
        for (int i=0;i<16;i++) {
            String id = "minecraft:" + PlotSettings.CONCRETE_COLORS.get(i) + "_concrete";
            FlatButton swatch = edit(() -> blockName(id), editorX+(i%cols)*(chip+5), y+(i/cols)*(chip+5), chip, chip,
                    () -> { inputs.put(slot,id); validate(); rebuildWidgets(); }, () -> id.equals(inputs.get(slot)));
            swatch.swatch = COLORS[i]; swatch.setTooltip(Tooltip.create(blockName(id)));
        }
        y += ((16+cols-1)/cols)*(chip+5)+8;
        if (slot.equals("marker_block")) {
            y = heading("marker_lighting", y);
            for (int i=0;i<2;i++) {
                String id = i==0 ? "minecraft:sea_lantern" : "minecraft:glowstone";
                edit(() -> blockName(id), editorX+i*((editorWidth-4)/2+4), y, (editorWidth-4)/2, 22,
                        () -> { inputs.put(slot,id); validate(); rebuildWidgets(); }, () -> id.equals(inputs.get(slot)));
            }
            y += 30;
        }
        return hint(tr("palette_hint"),y)+8;
    }
    private int heightPage() {
        int y = heading("world_layers",0);
        y = hint(tr("height_custom_hint"),y);
        number("surface_y",editorX,y,editorWidth,-63,319,true); y += 44;
        number("bedrock_y",editorX,y,editorWidth,-64,318,true); y += 44;
        y = hint(tr("height_range"),y);
        edit(() -> tr("height_defaults"),editorX,y+4,editorWidth,21,() -> {
            inputs.put("bedrock_y","-64"); inputs.put("surface_y","-53"); validate(); rebuildWidgets(); },() -> false);
        y += 38;
        y = heading("layer_section",y);
        diagramY = y;
        return y+73;
    }
    private int heading(String key,int y) { labels.add(new Label(tr(key),editorX,y,editorWidth,true)); return y+18; }
    private int hint(Component text,int y) {
        for (var line : font.split(text,editorWidth-2)) {
            // Keep components, including inherited translations, available to narration and tooltips.
            StringBuilder shown = new StringBuilder();
            line.accept((index, style, codePoint) -> { shown.appendCodePoint(codePoint); return true; });
            labels.add(new Label(Component.literal(shown.toString()),editorX,y,editorWidth,false)); y+=12;
        }
        return y+5;
    }
    private void number(String key,int x,int y,int w,int min,int max,boolean enabled) {
        labels.add(new Label(tr(key),x,y,w,false));
        FlatButton minus = edit(() -> Component.literal("−"),x,y+13,18,20,() -> step(key,-1,min,max),() -> false);
        EditBox box = new EditBox(font,x+24,editorY+y+18,Math.max(12,w-48),12,tr(key));
        box.setMaxLength(5); box.setBordered(false); box.setTextColor(INK); box.setTextColorUneditable(0xff777974);
        box.setValue(inputs.get(key)); box.active=enabled; box.setEditable(enabled);
        box.setTooltip(Tooltip.create(tr(key.equals("marker_size") ? "marker_auto_hint" : key.equals("road_width") || key.equals("border_width") ? "width_hint" : key)));
        box.setResponder(value -> { inputs.put(key,value); validate(); });
        registerEditor(box,y+18);
        FlatButton plus = edit(() -> Component.literal("+"),x+w-18,y+13,18,20,() -> step(key,1,min,max),() -> false);
        minus.active=enabled; plus.active=enabled;
    }
    private void step(String key,int delta,int min,int max) {
        inputs.put(key,""+Math.max(min,Math.min(max,parseOr(key,min)+delta))); validate(); rebuildWidgets();
    }
    private int parseOr(String key,int fallback) { try { return Integer.parseInt(inputs.get(key)); } catch (RuntimeException e) { return fallback; } }
    private <T> void options(String key,List<T> values,Supplier<T> getter,Consumer<T> setter,int y,int columns,boolean enabled) {
        int gap=3,w=(editorWidth-(columns-1)*gap)/columns;
        for(int i=0;i<values.size();i++) {
            T value=values.get(i);
            FlatButton option=edit(() -> display(value),editorX+(i%columns)*(w+gap),y+(i/columns)*24,w,21,
                    () -> { setter.accept(value); validate(); rebuildWidgets(); },() -> getter.get()==value);
            option.active=enabled; option.setTooltip(Tooltip.create(enabled ? tr(key).append(": ").append(display(value)) : tr("markings_unavailable")));
        }
    }
    private Component display(Object value) {
        if(value==PlotSettings.MarkerMode.NONE) return tr("marker_none");
        return tr(((Enum<?>)value).name().toLowerCase(Locale.ROOT));
    }
    private static Component blockName(String id) {
        ResourceLocation key=ResourceLocation.tryParse(id);
        return key!=null && BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.get(key).getName() : Component.literal(id);
    }
    private static int colorOf(String id) {
        for(int i=0;i<16;i++) if(id.equals("minecraft:"+PlotSettings.CONCRETE_COLORS.get(i)+"_concrete")) return COLORS[i];
        return 0xffe0d6a9;
    }
    private int number(String key) { return Integer.parseInt(inputs.get(key)); }
    private PlotSettings candidate() {
        return new PlotSettings(number("size"),inputs.get("ground"),inputs.get("road_block"),inputs.get("border_block"),
                inputs.get("marker_block"),draft.roadStyle(),number("road_width"),draft.markerMode(),number("marker_size"),
                number("bedrock_y"),number("surface_y"),inputs.get("line_block"),draft.lineStyle(),number("dash_length"),number("dash_gap"),number("border_width"),
                inputs.get("plot_edge_block"),number("plot_edge_width"));
    }
    private void validate() {
        if(building) return; error=CommonComponents.EMPTY;
        try { draft=candidate(); validatedPreview=draft; }
        catch(RuntimeException invalid) { error=tr("invalid_settings"); }
        boolean valid=error.equals(CommonComponents.EMPTY);
        if(done!=null) done.active=valid && (state == null || CreateWorldPlotState.isSkyblock(state));
        if(share!=null) share.active=valid;
    }
    private void commit() {
        validate(); if(!done.active) return;
        if (state != null) CreateWorldPlotState.put(state,draft);
        minecraft.setScreen(parent);
        if (accepted != null) accepted.accept(draft);
    }
    private void openShare() {
        validate(); if(!share.active) return;
        minecraft.setScreen(new PlotPresetShareScreen(this,draft,settings -> { draft=settings; validatedPreview=settings; resetInputs(); }));
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() { single.close(); four.close(); thumbnails.values().forEach(PlotPreviewRenderer.Cached::close); }
    @Override public void tick() { for(var child:editorWidgets) if(child instanceof EditBox box) box.tick(); }
    private void updateScroll(int value) {
        scroll=Math.max(0,Math.min(value,Math.max(0,contentHeight-(editorBottom-editorY)))); pageScroll[page]=scroll;
        for(var widget:editorWidgets) {
            widget.setY(editorY+contentY.get(widget)-scroll);
            widget.visible=widget.getY()+widget.getHeight()>editorY && widget.getY()<editorBottom;
            if(!widget.visible && getFocused()==widget) setFocused(null);
        }
    }
    @Override public boolean mouseScrolled(double mx,double my,double amount) {
        if(inEditor(mx,my)) { updateScroll(scroll-(int)Math.round(amount*24)); return true; }
        return super.mouseScrolled(mx,my,amount);
    }
    private boolean inEditor(double mx,double my) { return mx>=editorX && mx<editorX+editorWidth && my>=editorY && my<editorBottom; }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        List<AbstractWidget> widgets=inEditor(mx,my) ? editorWidgets : fixedWidgets;
        for(var widget:widgets) if(widget.visible && widget.active && widget.mouseClicked(mx,my,button)) {
            // A button may have rebuilt the screen. Never restore focus to its discarded instance.
            if(editorWidgets.contains(widget) || fixedWidgets.contains(widget)) setFocused(widget);
            if(button==0) setDragging(true); return true;
        }
        setFocused(null); return false;
    }
    @Override public boolean mouseReleased(double mx,double my,int button) {
        boolean handled = false;
        List<AbstractWidget> widgets = new ArrayList<>(editorWidgets); widgets.addAll(fixedWidgets);
        for (var widget : widgets) {
            if (widget.visible) handled |= widget.mouseReleased(mx, my, button);
        }
        setDragging(false);
        return handled;
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) {
        if(editorWidgets.contains(getFocused()) && !inEditor(mx,my)) return false;
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==256) { onClose(); return true; }
        if(key==258) {
            List<AbstractWidget> order=new ArrayList<>(fixedWidgets.subList(0,PAGES.length)); order.addAll(editorWidgets);
            order.addAll(fixedWidgets.subList(PAGES.length,fixedWidgets.size())); order.removeIf(widget -> !widget.active);
            if(order.isEmpty()) return true;
            int index=order.indexOf(getFocused()), step=hasShiftDown() ? -1 : 1;
            AbstractWidget next=order.get(Math.floorMod(index+step,order.size()));
            if(editorWidgets.contains(next)) {
                int y=contentY.get(next);
                if(y<scroll) updateScroll(y-3);
                else if(y+next.getHeight()>scroll+editorBottom-editorY) updateScroll(y+next.getHeight()-(editorBottom-editorY)+3);
            }
            setFocused(next); next.setFocused(true); return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderDirtBackground(g);
        g.fillGradient(0,0,width,height,0xb0161719,0xe00c0d0f);
        panel(g,left,top,bodyWidth,bodyHeight,0xff252a30);
        g.fill(left+1,top+1,left+bodyWidth-1,top+29,0xff30363d);
        g.fill(left+1,top+29,left+bodyWidth-1,top+30,0xff111519);
        g.drawString(font,title,left+12,top+10,INK,false);
        if(bodyWidth>510) g.drawString(font,tr("subtitle"),left+12+font.width(title)+16,top+10,MUTED,false);
        panel(g,editorX-4,editorY-4,editorWidth+8,editorBottom-editorY+8,0xff20252a);
        panel(g,previewX-4,editorY-4,previewWidth+8,editorBottom-editorY+8,0xff181d22);
        g.enableScissor(editorX,editorY,editorX+editorWidth,editorBottom);
        for(Label label:labels) {
            int y=editorY+label.y-scroll;
            if(y+12<editorY || y>=editorBottom) continue;
            if(label.heading) {
                g.drawString(font,font.plainSubstrByWidth(label.text.getString(),label.maxWidth),label.x,y,GOLD,false);
                int start=label.x+font.width(label.text)+6;
                if(start<label.x+label.maxWidth) g.fill(start,y+5,label.x+label.maxWidth,y+6,0xff454744);
            } else g.drawString(font,font.plainSubstrByWidth(label.text.getString(),label.maxWidth),label.x,y,MUTED,false);
        }
        if(diagramY >= 0) {
            int y = editorY + diagramY - scroll;
            g.fill(editorX,y,editorX+editorWidth,y+14,0xff333f4b);
            g.fill(editorX,y+15,editorX+editorWidth,y+45,colorOf(validatedPreview.groundBlock()));
            g.fill(editorX,y+46,editorX+editorWidth,y+59,0xff525355);
            g.renderOutline(editorX,y+46,editorWidth,13,0xff28292b);
            g.drawString(font,tr("surface_level",validatedPreview.surfaceY()),editorX+4,y+3,INK,false);
            g.drawString(font,tr("layers",validatedPreview.concreteLayers()),editorX+4,y+25,0xff101316,false);
            g.drawString(font,tr("foundation_level",validatedPreview.bedrockY()),editorX+4,y+48,INK,false);
        }
        for(var child:editorWidgets) if(child.visible) {
            if(child instanceof EditBox box) {
                panel(g,box.getX()-3,box.getY()-4,box.getWidth()+6,20,0xff151a1e);
                if(box.isFocused()) g.renderOutline(box.getX()-3,box.getY()-4,box.getWidth()+6,20,GOLD);
            }
            child.render(g,inEditor(mx,my)?mx:-1000,inEditor(mx,my)?my:-1000,partial);
        }
        g.disableScissor();
        if(contentHeight>editorBottom-editorY) {
            int vh=editorBottom-editorY, thumb=Math.max(10,vh*vh/contentHeight);
            int sy=editorY+scroll*(vh-thumb)/Math.max(1,contentHeight-vh);
            g.fill(editorX+editorWidth+5,editorY,editorX+editorWidth+7,editorBottom,0xff13171b);
            g.fill(editorX+editorWidth+5,sy,editorX+editorWidth+7,sy+thumb,0xff929080);
        }
        drawPreviews(g);
        int statusY=top+bodyHeight-40;
        Component status=error.equals(CommonComponents.EMPTY) ? tr("live_status",validatedPreview.plotBlocks(),validatedPreview.surfaceY()) : error;
        g.drawString(font,font.plainSubstrByWidth(status.getString(),bodyWidth-24),left+12,statusY,error.equals(CommonComponents.EMPTY)?MUTED:0xffffa69c,false);
        for(var widget:fixedWidgets) widget.render(g,mx,my,partial);
        if(!error.equals(CommonComponents.EMPTY) && my>=statusY && my<statusY+10)
            g.renderTooltip(font,font.split(error,Math.min(280,bodyWidth-24)),mx,my);
    }
    private void drawPreviews(GuiGraphics g) {
        int y=editorY+3, available=editorBottom-y;
        boolean beside=previewWidth>=250;
        int pixels=beside ? Math.min((previewWidth-14)/2,available-120) : Math.min(previewWidth-8,(available-145)/2);
        pixels=Math.max(12,pixels);
        int x=previewX+(previewWidth-(beside?pixels*2+10:pixels))/2;
        preview(g,single,x,y,pixels,1);
        preview(g,four,beside?x+pixels+10:x,beside?y:y+pixels+17,pixels,2);
        int sy=beside ? y+pixels+24 : y+pixels*2+41;
        g.drawString(font,tr("preview_dimensions",validatedPreview.plotChunks(),validatedPreview.plotChunks(),validatedPreview.plotBlocks()),previewX+3,sy,INK,false);
        g.drawString(font,tr("layers",validatedPreview.concreteLayers()),previewX+3,sy+14,MUTED,false);
        g.drawString(font,tr("effective_width",PlotLayout.effectiveRoadWidth(validatedPreview)),previewX+3,sy+28,MUTED,false);
        g.drawString(font,tr("effective_curb",PlotLayout.effectiveBorderWidth(validatedPreview)),previewX+3,sy+42,MUTED,false);
        String[] legend={"ground","plot_edge_block","road_block","border_block","line_block","marker_block"};
        String[] materials={validatedPreview.groundBlock(),validatedPreview.plotEdgeBlock(),validatedPreview.roadBlock(),
                validatedPreview.borderBlock(),validatedPreview.lineBlock(),validatedPreview.markerBlock()};
        int gap=8, columnWidth=(previewWidth-gap-6)/2;
        for(int i=0;i<legend.length;i++) {
            int column=i%2, row=i/2, lx=previewX+3+column*(columnWidth+gap), ly=sy+58+row*14;
            g.fill(lx,ly+1,lx+10,ly+11,colorOf(materials[i]));
            g.renderOutline(lx,ly+1,10,10,0xffd4d9dc);
            String text=tr(legend[i]).getString()+" · "+blockName(materials[i]).getString();
            g.drawString(font,font.plainSubstrByWidth(text,Math.max(12,columnWidth-15)),lx+14,ly+2,INK,false);
        }
    }
    private void preview(GuiGraphics g,PlotPreviewRenderer.Cached cache,int x,int y,int size,int plots) {
        cache.draw(g,validatedPreview,x,y,size,plots,grid);
        String text=font.plainSubstrByWidth(tr(plots==1?"preview_single":"preview_four").getString(),size+4);
        g.drawString(font,text,x+(size-font.width(text))/2,y+size+4,MUTED,false);
    }
    private static void panel(GuiGraphics g,int x,int y,int w,int h,int fill) {
        g.fill(x,y,x+w,y+h,0xff101316); g.fill(x+1,y+1,x+w-1,y+h-1,fill);
        g.fill(x+1,y+1,x+w-1,y+2,0xff485057); g.fill(x+1,y+2,x+2,y+h-1,0xff3d444a);
    }
    private FlatButton fixed(Supplier<Component> text,int x,int y,int w,int h,Runnable action,BooleanSupplier selected) {
        FlatButton button=addRenderableWidget(new FlatButton(text,x,y,w,h,action,selected)); fixedWidgets.add(button); return button;
    }
    private FlatButton edit(Supplier<Component> text,int x,int y,int w,int h,Runnable action,BooleanSupplier selected) {
        FlatButton button=new FlatButton(text,x,editorY+y,w,h,action,selected); registerEditor(button,y); return button;
    }
    private void registerEditor(AbstractWidget widget,int y) { addRenderableWidget(widget); editorWidgets.add(widget); contentY.put(widget,y); }
    private final class FlatButton extends AbstractButton {
        private final Supplier<Component> text; private final Runnable action; private final BooleanSupplier selected;
        private int swatch; private IntSupplier indicator; private PlotSettings.RoadPreset preset;
        FlatButton(Supplier<Component> text,int x,int y,int w,int h,Runnable action,BooleanSupplier selected) {
            super(x,y,w,h,text.get()); this.text=text; this.action=action; this.selected=selected;
        }
        @Override public void onPress() { action.run(); }
        @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) { setMessage(text.get()); defaultButtonNarrationText(output); }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
            Component message=text.get(); setMessage(message); boolean chosen=selected.getAsBoolean(), hover=isHoveredOrFocused();
            panel(g,getX(),getY(),width,height,!active?0xff272c30:chosen?0xff555345:hover?0xff474e53:0xff343b40);
            if(chosen) { g.fill(getX()+1,getY()+2,getX()+3,getY()+height-2,GOLD); g.fill(getX()+2,getY()+height-2,getX()+width-1,getY()+height-1,0xff9f8f67); }
            if(hover && active) g.renderOutline(getX(),getY(),width,height,0xffb4ac98);
            if(swatch!=0) {
                g.fill(getX()+3,getY()+3,getX()+width-3,getY()+height-3,swatch);
                if(chosen) { g.renderOutline(getX()+1,getY()+1,width-2,height-2,INK); g.fill(getX()+width-8,getY()+3,getX()+width-3,getY()+8,0xff161a1d); g.drawString(font,Component.literal("+"),getX()+width-8,getY()+2,INK,false); }
                return;
            }
            int tx=getX()+5, remaining=width-10;
            if(preset!=null) {
                int side=Math.min(width<100?16:24,height-8);
                PlotSettings thumbnailSettings = draft.withRoadPreset(preset).withPlotChunks(3).withMarkerMode(PlotSettings.MarkerMode.NONE);
                thumbnails.computeIfAbsent(preset,p -> new PlotPreviewRenderer.Cached()).draw(g,thumbnailSettings,tx,getY()+4,side,1,false);
                tx+=side+5; remaining-=side+5;
            } else if(indicator!=null) { g.fill(tx,getY()+6,tx+7,getY()+height-6,indicator.getAsInt()); tx+=12; remaining-=12; }
            String shown=font.plainSubstrByWidth(message.getString(),Math.max(1,remaining));
            int textX=preset!=null || indicator!=null ? tx : getX()+(width-font.width(shown))/2;
            g.drawString(font,shown,textX,getY()+(height-8)/2,!active?0xff777d7f:chosen?0xfff2e5c3:INK,false);
            if(!shown.equals(message.getString())) setTooltip(Tooltip.create(message));
        }
    }
}
