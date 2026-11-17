package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.integration.jade.FusionParallelInfoProvider;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import snownee.jade.api.ITooltip;
import snownee.jade.impl.BlockAccessorImpl;

/** Registered fusion supplier and production GUI/Jade paths; no blocks or inventories are changed. */
public final class FusionDisplayChecks {
    private static final String KEY = "gui.gtl_enhancedcore.fusion_reactor.parallel";
    private FusionDisplayChecks() {}

    public static Map<String, Object> run(MinecraftServer server) throws Exception {
        require(server.isSameThread(), "Fusion display check requires the server thread");
        var fixture = fixture();
        try {
            var machine = (FusionReactorMachine) fixture.machine();
            var gui = new ArrayList<Component>(); machine.addDisplayText(gui);
            Component guiLine = parallelLine(gui);
            require(argument(guiLine).equals("1"), "Real fusion GUI replaced one parallel with its maximum: " + guiLine);
            var data = new CompoundTag(); var pos = machine.getPos();
            var accessor = new BlockAccessorImpl.Builder().level(machine.getLevel()).player(fixture.player())
                    .serverData(data).serverConnected(true).showDetails(true)
                    .blockState(fixture.holders().getFirst().getBlockState())
                    .blockEntity(() -> fixture.holders().getFirst())
                    .hit(new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)).build();
            var provider = new FusionParallelInfoProvider(); provider.appendServerData(data, accessor);
            require(data.getInt("gtlEnhancedcoreFusionParallel") == 1, "Jade payload replaced one parallel with maximum");
            var lines = new ArrayList<Component>();
            var tooltip = (ITooltip) Proxy.newProxyInstance(ITooltip.class.getClassLoader(), new Class<?>[]{ITooltip.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("add") && arguments != null && arguments[0] instanceof Component line) lines.add(line);
                        return null;
                    });
            provider.appendTooltip(tooltip, new BlockAccessorImpl.Builder().from(accessor).serverData(data).build(), null);
            Component jadeLine = parallelLine(lines);
            require(argument(jadeLine).equals("1"), "Real Jade tooltip hid or changed one parallel");
            return Map.of("machine", machine.getDefinition().getId().toString(), "actualParallel", 1,
                    "gui", Component.Serializer.toJson(guiLine), "jadeData", data.toString(),
                    "jade", Component.Serializer.toJson(jadeLine));
        } finally { release(fixture); }
    }

    /** Client thread: setScreen(prepareUi()), wait for rendered frames, then use the caller's screenshot stage. */
    public static Screen prepareUi() throws Exception {
        require(Minecraft.getInstance().isSameThread(), "Fusion visual preparation requires client thread");
        return new FusionScreen(fixture());
    }

    private static StellarIvGuiChecks.Fixture fixture() throws Exception {
        var f = StellarIvGuiChecks.fixture("gtceu:uv_fusion_reactor", false, false, -1);
        var machine = f.machine();
        set(machine, "parts", new ArrayList<>());
        set(machine, "partPositions", new net.minecraft.core.BlockPos[0]);
        set(machine, "inputEnergyContainers", new com.gregtechceu.gtceu.api.misc.EnergyContainerList(List.of()));
        var recipe = GTRecipeBuilder.of(new ResourceLocation("enhancedcore_audit", "fusion_one_parallel"),
                machine.getRecipeType()).duration(200).EUt(512).buildRawRecipe();
        recipe.parallels = 1;
        set(machine.getRecipeLogic(), "lastRecipe", recipe);
        set(machine.getRecipeLogic(), "duration", 200);
        machine.getRecipeLogic().setStatus(RecipeLogic.Status.WORKING);
        return f;
    }
    private static Component parallelLine(List<Component> lines) {
        return lines.stream().filter(line -> line.getContents() instanceof TranslatableContents t && KEY.equals(t.getKey()))
                .findFirst().orElseThrow(() -> new AssertionError("Production fusion parallel line missing"));
    }
    private static String argument(Component line) {
        Object value = ((TranslatableContents) line.getContents()).getArgs()[0];
        return value instanceof Component component ? component.getString() : String.valueOf(value);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void release(StellarIvGuiChecks.Fixture fixture) { fixture.holders().forEach(holder -> holder.setLevel(null)); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Menu extends ModularUIGuiContainer {
        Menu(ModularUI ui) { super(ui, 1); }
        void initialize(Minecraft mc) { minecraft = mc; font = mc.font;
            width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight(); super.init(); }
        void advance() { super.containerTick(); }
    }
    private static final class FusionScreen extends Screen {
        private final StellarIvGuiChecks.Fixture fixture;
        private final ModularUI ui;
        private final Menu menu;
        FusionScreen(StellarIvGuiChecks.Fixture fixture) {
            super(Component.literal("Fusion production UI audit")); this.fixture = fixture;
            ui = fixture.machine().createUI(fixture.player()); ui.initWidgets();
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { ui.mainGroup.writeInitialData(buffer); ui.mainGroup.readInitialData(buffer);
                require(buffer.readableBytes() == 0, "Fusion UI initial synchronization left unread bytes"); }
            finally { buffer.release(); }
            menu = new Menu(ui); fixture.player().containerMenu = menu.getMenu();
        }
        @Override protected void init() {
            menu.initialize(minecraft);
            ui.mainGroup.setUiAccess(new WidgetUIAccess() {
                public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
                public void writeUpdateInfo(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
                public void writeClientAction(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
            });
        }
        @Override public void tick() { menu.advance(); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            menu.renderWithTooltip(graphics, -100, -100, partial);
        }
        @Override public void removed() { release(fixture); }
    }
}
