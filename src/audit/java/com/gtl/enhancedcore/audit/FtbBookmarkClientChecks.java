package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.integration.jei.FtbQuestItemGuiHandler;
import com.gtl.enhancedcore.mixin.ftblibrary.client.QuestModalPanelsAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ftb.mods.ftblibrary.ui.BlankPanel;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.client.gui.quests.RewardButton;
import dev.ftb.mods.ftbquests.client.gui.quests.TaskButton;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.ingredients.ITypedIngredientFactory;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.config.IBookmarkConfig;
import mezz.jei.gui.input.ClientInputHandler;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.GuiContainerWrapper;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.BookmarkInputHandler;
import mezz.jei.gui.input.handlers.ChatLinkInputHandler;
import mezz.jei.gui.input.handlers.DragRouter;
import mezz.jei.gui.input.handlers.UserInputRouter;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.library.ingredients.TypedIngredient;
import mezz.jei.library.load.registration.GuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Real JEI input/bookmark behavior against synthetic FTB widgets, without loading any world. */
@Mod("enhancedcore_audit")
public final class FtbBookmarkClientChecks {
    private static final Path TASK = Path.of("C:/IDEA/GTL-Enhancedcore/_work/ftb-bookmark-fix-20261022");
    private static final Path REUSED = Path.of("C:/IDEA/GTL-Enhancedcore/_work/terminal-time-20261022/interaction-client");
    private final List<String> checks = new ArrayList<>();
    private boolean done;

    public FtbBookmarkClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("gtl.enhancedcore.ftbBookmarkAudit")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) return;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        if ((!directory.equals(REUSED) && (!directory.startsWith(TASK) || directory.equals(TASK)))
                || !Files.isRegularFile(directory.resolve("ftb-bookmark.marker"))
                || mc.level != null || mc.getSingleplayerServer() != null) return;
        done = true;
        var report = new LinkedHashMap<String, Object>();
        Screen originalScreen = mc.screen;
        ClientQuestFile originalFile = ClientQuestFile.INSTANCE;
        try {
            check(true, "Marked isolated Forge client, title screen, no world/server");
            run(mc);
            report.put("status", "PASS");
        } catch (Throwable failure) {
            report.put("status", "FAIL");
            report.put("error", failure.toString());
            failure.printStackTrace();
        } finally {
            mc.screen = originalScreen;
            ClientQuestFile.INSTANCE = originalFile;
        }
        report.put("checks", checks);
        report.put("scope", "Real FTB widgets and registered JEI ScreenHelper; real ClientInputHandler -> "
                + "UserInputRouter -> BookmarkInputHandler -> BookmarkList; in-memory ingredient interfaces and "
                + "bookmark persistence. Closed RecipesGui and offscreen BookmarkOverlay are allocated without "
                + "constructors as inert auxiliary state; no world, actual rendering, packet or player inventory.");
        try {
            Files.writeString(directory.resolve("ftb-bookmark-checks.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }

    private void run(Minecraft mc) throws Exception {
        ClientQuestFile file = new ClientQuestFile();
        // TeamData explicitly supports a detached (null-file) snapshot: getOrCreatePlayerData
        // returns Optional.empty before touching Player. ChapterPanel's constructor asks
        // isChapterPinned(mc.player), even though the audit intentionally has no player/world.
        file.selfTeamData = new TeamData(new UUID(0L, 0x20261022L), null, "bookmark audit fixture");
        ClientQuestFile.INSTANCE = file;
        check(mc.player == null && !file.selfTeamData.isChapterPinned(null),
                "Detached real TeamData supports title-screen chapter preference reads without a player");
        Quest quest = new Quest(0x1002L, new Chapter(0x1001L, file, null));
        QuestScreen quests = new QuestScreen(file, null);
        ScreenWrapper wrapper = new ScreenWrapper(quests);
        wrapper.width = mc.getWindow().getGuiScaledWidth();
        wrapper.height = mc.getWindow().getGuiScaledHeight();
        quests.setSize(wrapper.width, wrapper.height);
        ViewQuestPanel view = quests.viewQuestPanel;
        setField(view, "quest", quest); // Avoid setViewedQuest's data/team UI rebuild; retain real getter and hover code.
        view.setPosAndSize(30, 30, 220, 140);
        view.clearWidgets();
        BlankPanel nested = new BlankPanel(view);
        nested.setPosAndSize(15, 20, 140, 80);
        nested.setOnlyInteractWithWidgetsInside(true);
        nested.setOnlyRenderWidgetsInside(true);
        view.add(nested);
        TaskButton task = new TaskButton(nested, new ItemTask(0x1003L, quest)
                .setStackAndCount(new ItemStack(Items.DIAMOND), 4));
        task.setPosAndSize(10, 12, 20, 20);
        nested.add(task);
        RewardButton reward = new RewardButton(nested,
                new ItemReward(0x1004L, quest, new ItemStack(Items.EMERALD), 3));
        reward.setPosAndSize(50, 12, 20, 20);
        nested.add(reward);
        var modals = ((QuestModalPanelsAccessor) quests).enhanced$getQuestModalPanels();
        modals.addFirst(view);
        mc.screen = wrapper; // No setScreen/init: those build unrelated team/player-dependent widgets.

        GuiHandlerRegistration registration = new GuiHandlerRegistration(proxy(IJeiHelpers.class));
        FtbQuestItemGuiHandler.register(registration);
        ITypedIngredientFactory factory = new ITypedIngredientFactory() {
            @Override public <T> Optional<ITypedIngredient<T>> createTypedIngredient(
                    IIngredientType<T> type, T ingredient, boolean normalize) {
                return Optional.of(TypedIngredient.createUnvalidated(type, ingredient));
            }
        };
        IScreenHelper helper = registration.createGuiScreenHelper(factory);
        check(helper.getGuiProperties(wrapper).isPresent(), "Registered FTB quest details have JEI GUI properties");
        int[] taskPoint = point(view, nested, task);
        int[] rewardPoint = point(view, nested, reward);
        check(hover(helper, wrapper, taskPoint).is(Items.DIAMOND), "Real FTB task hover resolves DIAMOND");
        check(hover(helper, wrapper, rewardPoint).is(Items.EMERALD), "Real FTB reward hover resolves EMERALD, not nested Optional");

        @SuppressWarnings("unchecked")
        IIngredientHelper<ItemStack> itemHelper = proxy(IIngredientHelper.class, (method, args) -> {
            Object value = args.length == 0 ? null : args[0];
            ItemStack stack = value instanceof ITypedIngredient<?> typed ? (ItemStack) typed.getIngredient()
                    : value instanceof ItemStack item ? item : ItemStack.EMPTY;
            return switch (method.getName()) {
                case "getUid", "getUniqueId", "getWildcardId", "getErrorInfo", "getDisplayName" ->
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                case "copyIngredient", "normalizeIngredient" -> stack.copy();
                case "isValidIngredient", "isIngredientOnServer" -> !stack.isEmpty();
                default -> defaultValue(method);
            };
        });
        IIngredientManager manager = proxy(IIngredientManager.class, (method, args) -> switch (method.getName()) {
            case "getIngredientHelper" -> itemHelper;
            case "normalizeTypedIngredient" -> args[0];
            default -> defaultValue(method);
        });
        IClientConfig config = proxy(IClientConfig.class);
        AtomicInteger saves = new AtomicInteger();
        IBookmarkConfig persistence = proxy(IBookmarkConfig.class, (method, args) -> {
            if (method.getName().equals("saveBookmarks")) saves.incrementAndGet();
            return defaultValue(method);
        });
        BookmarkList bookmarks = new BookmarkList(proxy(IRecipeManager.class), proxy(IFocusFactory.class),
                manager, RegistryAccess.EMPTY, persistence, config, proxy(IGuiHelper.class));
        // Auxiliary recipe GUI is closed (minecraft == null), so real getRecipeLayoutUnderMouse returns empty.
        RecipesGui recipes = allocate(RecipesGui.class);
        // Actual overlay hit-testing runs against an offscreen empty rectangle, never a fake bookmark function.
        BookmarkOverlay overlay = allocate(BookmarkOverlay.class);
        IngredientGridWithNavigation grid = allocate(IngredientGridWithNavigation.class);
        setField(grid, "backgroundArea", new ImmutableRect2i(-1000, -1000, 0, 0));
        setField(grid, "guiExclusionAreas", Set.of());
        setField(overlay, "contents", grid);
        AtomicInteger boundKey = new AtomicInteger(65);
        IJeiKeyMapping bookmarkKey = proxy(IJeiKeyMapping.class, (method, args) ->
                method.getName().equals("isActiveAndMatches")
                        ? ((InputConstants.Key) args[0]).getValue() == boundKey.get() : defaultValue(method));
        IJeiKeyMapping inactiveKey = proxy(IJeiKeyMapping.class);
        IInternalKeyMappings keys = proxy(IInternalKeyMappings.class, (method, args) ->
                method.getName().equals("getBookmark") ? bookmarkKey
                        : IJeiKeyMapping.class.isAssignableFrom(method.getReturnType()) ? inactiveKey : defaultValue(method));
        BookmarkInputHandler bookmarkHandler = new BookmarkInputHandler(
                new CombinedRecipeFocusSource(new GuiContainerWrapper(helper)), bookmarks, overlay, config, recipes);
        UserInputRouter router = new UserInputRouter("FTB bookmark behavior audit", bookmarkHandler);
        ClientInputHandler client = new ClientInputHandler(List.of(),
                new ChatLinkInputHandler(null, null, helper, bookmarks), router, new DragRouter(), keys, helper);
        GuiHandlerRegistration oldRegistration = new GuiHandlerRegistration(proxy(IJeiHelpers.class));
        Field handlersField = GuiHandlerRegistration.class.getDeclaredField("globalGuiHandlers");
        handlersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var globalHandlers = (List<mezz.jei.api.gui.handlers.IGlobalGuiHandler>) handlersField.get(registration);
        globalHandlers.forEach(oldRegistration::addGlobalGuiHandler);
        IScreenHelper oldHelper = oldRegistration.createGuiScreenHelper(factory);
        check(hover(oldHelper, wrapper, taskPoint).is(Items.DIAMOND)
                        && oldHelper.getGuiProperties(wrapper).isEmpty(),
                "Before-fix control: global handler finds task but ScreenHelper has no GUI properties");
        ClientInputHandler oldClient = new ClientInputHandler(List.of(),
                new ChatLinkInputHandler(null, null, oldHelper, bookmarks), router, new DragRouter(), keys, oldHelper);
        check(!oldClient.onKeyboardKeyPressedPre(wrapper, input(65, taskPoint)) && bookmarks.isEmpty(),
                "Before-fix control: real ClientInputHandler rejects A despite a valid hovered ingredient");
        check(client.onKeyboardKeyPressedPre(wrapper, input(65, taskPoint)), "A traverses real JEI client input and consumes task ingredient");
        check(bookmarks.getElements().size() == 1 && element(bookmarks, 0).is(Items.DIAMOND),
                "Actual JEI BookmarkList contains DIAMOND after A");
        check(!client.onKeyboardKeyPressedPre(wrapper, input(66, rewardPoint)) && bookmarks.getElements().size() == 1,
                "Unbound B leaves real bookmark list unchanged");
        boundKey.set(66);
        check(!client.onKeyboardKeyPressedPre(wrapper, input(65, rewardPoint)), "After rebind old A no longer bookmarks");
        check(client.onKeyboardKeyPressedPre(wrapper, input(66, rewardPoint)), "Rebound B consumes real reward ingredient");
        check(bookmarks.getElements().size() == 2 && element(bookmarks, 1).is(Items.EMERALD),
                "Actual JEI BookmarkList contains EMERALD after rebound B");
        check(!client.onKeyboardKeyPressedPre(wrapper, input(66, new int[]{view.getX() + 200, view.getY() + 115})),
                "Blank quest detail area does not consume bookmark input");
        ModalPanel above = new ModalPanel(quests) {
            @Override public void addWidgets() {}
            @Override public void alignWidgets() {}
        };
        above.setPosAndSize(0, 0, wrapper.width, wrapper.height);
        modals.addFirst(above);
        check(helper.getClickableIngredientUnderMouse(wrapper, taskPoint[0], taskPoint[1]).findAny().isEmpty(),
                "Upper modal blocks underlying task ingredient");
        check(!client.onKeyboardKeyPressedPre(wrapper, input(66, taskPoint)), "Upper modal blocks bookmark input");
        modals.removeFirst();
        nested.setScrollY(8);
        int[] scrolled = point(view, nested, task);
        check(scrolled[1] == taskPoint[1] - 8, "FTB scroll moves ingredient geometry by the actual eight-pixel offset");
        check(hover(helper, wrapper, scrolled).is(Items.DIAMOND), "Nested panel offset/scroll still resolves task ingredient");
        nested.setScrollY(60);
        int[] clipped = point(view, nested, task);
        check(helper.getClickableIngredientUnderMouse(wrapper, clipped[0], clipped[1]).findAny().isEmpty(),
                "Task scrolled outside ancestor clipping cannot expose a bookmark ingredient");
        setField(view, "quest", null);
        check(helper.getGuiProperties(wrapper).isEmpty(), "Non-detail quest screen has no added JEI GUI properties");
        check(!client.onKeyboardKeyPressedPre(wrapper, input(66, scrolled)), "ClientInputHandler rejects non-detail FTB screen");
        check(bookmarks.getElements().size() == 2 && saves.get() == 2,
                "Only two successful bookmark operations reached real list persistence");
    }

    private static ItemStack element(BookmarkList bookmarks, int index) {
        return (ItemStack) bookmarks.getElements().get(index).getTypedIngredient().getIngredient();
    }

    private static ItemStack hover(IScreenHelper helper, Screen screen, int[] point) {
        return (ItemStack) helper.getClickableIngredientUnderMouse(screen, point[0], point[1])
                .findFirst().orElseThrow().getTypedIngredient().getIngredient();
    }

    private static int[] point(ViewQuestPanel view, BlankPanel nested, dev.ftb.mods.ftblibrary.ui.Widget widget) {
        view.setOffset(true);
        nested.setOffset(true);
        try { return new int[]{widget.getX() + 5, widget.getY() + 5}; }
        finally { nested.setOffset(false); view.setOffset(false); }
    }

    private static UserInput input(int key, int[] point) {
        return new UserInput(InputConstants.Type.KEYSYM.getOrCreate(key), point[0], point[1], 0, InputType.IMMEDIATE);
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
    }

    private static void setField(Object instance, String name, Object value) throws Exception {
        Class<?> owner = instance.getClass();
        while (owner != null) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                field.set(instance, value);
                return;
            } catch (NoSuchFieldException ignored) { owner = owner.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    private interface Answer { Object invoke(Method method, Object[] args) throws Throwable; }

    private static <T> T proxy(Class<T> type) { return proxy(type, (method, args) -> defaultValue(method)); }

    private static <T> T proxy(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, arguments) -> {
            Object[] args = arguments == null ? new Object[0] : arguments;
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(instance);
                case "equals" -> instance == args[0];
                default -> "AuditProxy[" + type.getSimpleName() + "]";
            };
            return answer.invoke(method, args);
        }));
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == Optional.class) return Optional.empty();
        if (type == List.class || type == Collection.class) return List.of();
        if (type == Set.class) return Set.of();
        return null;
    }
}
