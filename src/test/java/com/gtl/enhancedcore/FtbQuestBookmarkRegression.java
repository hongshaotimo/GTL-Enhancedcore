package com.gtl.enhancedcore;

import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Version-sensitive contracts behind the FTB quest item/JEI input bridge, without a game client. */
final class FtbQuestBookmarkRegression {
    private static final String LIB = "libs/ftb-library-forge-2001.2.13.jar";
    private static final String QUESTS = "libs/ftb-quests-forge-2001.4.22.jar";
    private static final String JEI = "libs/jei-1.20.1-forge-15.49.0.191.jar";
    private static final String UI = "dev/ftb/mods/ftblibrary/ui/";
    private static final String INGREDIENT = "(L" + UI + "Widget;)Ljava/util/Optional;";
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        ClassNode base = read(LIB, UI + "BaseScreen");
        check(base.fields.stream().anyMatch(f -> f.name.equals("modalPanels")
                && f.desc.equals("Ljava/util/Deque;") && (f.access & Opcodes.ACC_PRIVATE) != 0),
                "FTB modal accessor still targets the private Deque");
        check(calls(method(base, "pushModalPanel"), "java/util/Deque", "addFirst") == 1,
                "First modal entry remains the topmost visible modal");
        check(calls(method(base, "updateMouseOver"), UI + "BaseScreen", "setOffset") == 2,
                "FTB hover query restores root scrolling offsets");
        ClassNode panel = read(LIB, UI + "Panel");
        check(calls(method(panel, "updateMouseOver"), UI + "Widget", "updateMouseOver") == 2,
                "Panel hover state is refreshed for itself and children");
        ClassNode positioned = read(LIB, "dev/ftb/mods/ftblibrary/util/client/PositionedIngredient");
        MethodNode of = positioned.methods.stream().filter(m -> m.name.equals("of")
                && m.desc.contains("Widget;Z)")).findFirst().orElseThrow();
        check(calls(of, UI + "Widget", "getPosX") == 1
                        && calls(of, UI + "Widget", "getPosY") == 1,
                "PositionedIngredient still supplies local rather than absolute screen coordinates");
        ClassNode taskButton = read(QUESTS, "dev/ftb/mods/ftbquests/client/gui/quests/TaskButton");
        check(calls(method(taskButton, "getIngredientUnderMouse"),
                "dev/ftb/mods/ftbquests/quest/task/Task", "getIngredient") == 1,
                "Task button uses the task's public ingredient API");
        for (String type : new String[]{"reward/Reward", "reward/ItemReward", "task/Task"}) {
            check(read(QUESTS, "dev/ftb/mods/ftbquests/quest/" + type).methods.stream().anyMatch(
                    m -> m.name.equals("getIngredient") && m.desc.equals(INGREDIENT)
                            && (m.access & Opcodes.ACC_PUBLIC) != 0),
                    "FTB " + type + " exposes Optional<PositionedIngredient> via Widget");
        }
        ClassNode screenHelper = read(JEI, "mezz/jei/library/gui/helpers/ScreenHelper");
        check(screenHelper.methods.stream().anyMatch(m -> calls(m,
                "mezz/jei/api/gui/handlers/IGlobalGuiHandler", "getClickableIngredientUnderMouse") == 1),
                "JEI queries global GUI ingredients through the standard factory API");
        ClassNode bookmarks = read(JEI, "mezz/jei/gui/input/handlers/BookmarkInputHandler");
        check(calls(method(bookmarks, "handleIngredientBookmark"),
                "mezz/jei/gui/input/CombinedRecipeFocusSource", "getIngredientUnderMouse") == 1,
                "JEI bookmarks resolve the same registered GUI focus source");
        ClassNode input = read(JEI, "mezz/jei/gui/input/ClientInputHandler");
        for (String phase : new String[]{"onKeyboardKeyPressedPre", "onKeyboardKeyPressedPost"}) {
            check(calls(method(input, phase), "mezz/jei/api/runtime/IScreenHelper", "getGuiProperties") == 1,
                    "JEI " + phase + " requires screen properties before routing bookmark input");
        }
        return assertions;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("FTB quest bookmark contracts: " + run());
    }

    private static ClassNode read(String path, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (JarFile jar = new JarFile(path); var input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static int calls(MethodNode method, String owner, String name) {
        int count = 0;
        for (var insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
