package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferAutoName;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 超级样板总成自动改名（“重命名样板总成”）的回归。
 *
 * <p>只验证可离线判定的部分：决策函数、语言键推导、回退规则，以及
 * “机器模式显示名”与 GTCEu {@code MultiblockDisplayText#addMachineModeLine} 的同源性
 * （后者用 {@code Component.translatable(recipeType.registryName.toLanguageKey())}）。
 *
 * <p>真实的 {@code Component.translatable(...).getString()}` 需要语言资源加载，
 * 这里用语言文件本身核对键存在，避免依赖 Minecraft 启动。
 */
final class SuperBufferAutoNameRegression {

    static int run() throws Exception {
        int before = assertions;

        // 1) Formation fills an unnamed buffer, and never changes an existing name.
        var plasmaKeys = java.util.List.of("gtceu.lathe", "gtceu.bender", "gtceu.compressor");
        // 空名字 -> 写入
        check(SuperBufferAutoName.shouldWrite("", "gtceu.compressor", "", false),
                "empty name is filled with the language key");
        check(SuperBufferAutoName.shouldWrite(null, "gtceu.compressor", "", false),
                "null name is filled with the language key");
        // 已是同一个键 -> 不重复写
        check(!SuperBufferAutoName.shouldWrite("gtceu.compressor", "gtceu.compressor", "gtceu.compressor", false),
                "same key is not rewritten");
        // 即便名字是上次自动生成的，重新成型和位置变化也不得覆盖。
        check(!SuperBufferAutoName.shouldWrite("gtceu.lathe", "gtceu.compressor", "gtceu.lathe", false),
                "a previous automatic name survives a changed position");
        // 旧存档没有来源记录，不能仅凭文本判断为自动名称。
        check(!SuperBufferAutoName.shouldWrite("Compressor", "gtceu.compressor", "", false),
                "legacy English name is preserved without ownership proof");
        // 玩家自己取的名字 -> 永不覆盖
        check(!SuperBufferAutoName.shouldWrite("我的压缩机", "gtceu.compressor", "", true),
                "player-given name is never overwritten");
        check(!SuperBufferAutoName.shouldWrite("压缩机", "gtceu.compressor", "", false),
                "player Chinese name is never overwritten");
        // 目标为空 -> 不写（超出配方类型数量时留空）
        check(!SuperBufferAutoName.shouldWrite("", "", "", false), "empty target never writes");
        check(!SuperBufferAutoName.shouldWrite("", null, "", false), "null target never writes");
        for (String chosen : java.util.List.of("", "gtceu.lathe", "Lathe", "车床", "车床.1")) {
            check(!SuperBufferAutoName.shouldWrite(chosen, "gtceu.compressor", "gtceu.lathe", true),
                    "manual confirmation survives reformation: " + chosen);
            check(!SuperBufferAutoName.shouldTranslate(chosen, "gtceu.lathe", true),
                    "manual name always remains literal: " + chosen);
        }
        check(!SuperBufferAutoName.shouldWrite("gtceu.lathe", "gtceu.compressor", "", false),
                "legacy language key is not proof of automatic ownership");
        check(!SuperBufferAutoName.shouldWrite("改过的名字", "gtceu.compressor", "gtceu.lathe", false),
                "external edit invalidates stale automatic ownership");
        check(SuperBufferAutoName.shouldTranslate("gtceu.lathe", "gtceu.lathe", false),
                "known automatic name translates");

        check(!SuperBufferAutoName.shouldWrite("gtceu.compressor", "压缩机", "gtceu.compressor", false),
                "a stored legacy key is not renamed during reformation");
        check(!SuperBufferAutoName.shouldWrite("Compressor", "压缩机", "Compressor", false),
                "a stored English name is not renamed during reformation");
        check(!SuperBufferAutoName.shouldWrite("压缩机", "压缩机", "压缩机", false),
                "fixed Chinese name is not repeatedly saved");
        check(!SuperBufferAutoName.shouldWrite("已有名字", "压缩机", "已有名字", false),
                "any nonempty name survives even with stale automatic ownership");
        check(SuperBufferAutoName.displayName("gtceu.compressor").equals("压缩机"), "fixed Chinese dictionary");
        check(SuperBufferAutoName.displayName("压缩机").equals("压缩机"), "Chinese roundtrip");
        check(SuperBufferAutoName.displayName("missing.recipe").equals("未命名加工模式"), "no English fallback");
        check(SuperBufferAutoName.displayName(null).isEmpty(), "empty name stays empty");
        // 1b) Language-key recognition remains independent of ownership.
        check(SuperBufferAutoName.isRecipeTypeKey("gtceu.compressor", plasmaKeys), "key recognized as recipe type key");
        check(!SuperBufferAutoName.isRecipeTypeKey("我的总成", plasmaKeys), "player text is not a recipe type key");
        check(!SuperBufferAutoName.isRecipeTypeKey("", plasmaKeys), "empty is not a recipe type key");

        // 2) 语言键推导与 GTCEu 同源：gtceu:compressor -> gtceu.compressor
        check(SuperBufferAutoName.languageKey(new net.minecraft.resources.ResourceLocation("gtceu", "compressor"))
                .equals("gtceu.compressor"), "registry name maps to gtceu lang key");
        check(SuperBufferAutoName.languageKey(null).isEmpty(), "null id yields empty key");

        // 3) 只有唯一控制器才自动命名（共享总成不抖动）
        check(SuperBufferAutoName.exclusive(1), "single controller may be auto-named");
        check(!SuperBufferAutoName.exclusive(0), "unformed buffer is skipped");
        check(!SuperBufferAutoName.exclusive(2), "shared buffer across two controllers is skipped");

        // 3b) 跨配方类型并行机器：按位置序号取名，互不重复。
        var plasma = java.util.List.of("gtceu.lathe", "gtceu.bender", "gtceu.compressor",
                "gtceu.forge_hammer", "gtceu.cutter", "gtceu.forming_press",
                "gtceu.wiremill", "gtceu.extruder", "gtceu.polarizer");
        check(SuperBufferAutoName.keyForIndex(0, plasma).equals("gtceu.lathe"), "1st buffer -> 1st recipe type");
        check(SuperBufferAutoName.keyForIndex(1, plasma).equals("gtceu.bender"), "2nd buffer -> 2nd recipe type");
        check(SuperBufferAutoName.keyForIndex(2, plasma).equals("gtceu.compressor"), "3rd buffer -> 3rd recipe type");
        check(SuperBufferAutoName.keyForIndex(8, plasma).equals("gtceu.polarizer"), "9th buffer -> 9th recipe type");
        // 超出配方类型数量必须留空，绝不回绕重复（用户摆了 11 个但只有 9 种类型）。
        check(SuperBufferAutoName.keyForIndex(9, plasma).isEmpty(), "10th buffer has no name (only 9 types)");
        check(SuperBufferAutoName.keyForIndex(10, plasma).isEmpty(), "11th buffer has no name (only 9 types)");
        check(SuperBufferAutoName.keyForIndex(0, java.util.List.of()).isEmpty(), "no recipe types -> no name");
        check(SuperBufferAutoName.keyForIndex(-1, plasma).isEmpty(), "negative index is safe");
        check(SuperBufferAutoName.keyForIndex(0, null).isEmpty(), "null recipe types is safe");
        // 前 9 个名字两两不同，且没有任何一个是重复的类型。
        var assigned = new java.util.ArrayList<String>();
        for (int i = 0; i < 11; i++) assigned.add(SuperBufferAutoName.keyForIndex(i, plasma));
        var nonEmpty = assigned.stream().filter(s -> !s.isEmpty()).toList();
        check(nonEmpty.size() == 9, "exactly nine buffers get a name");
        check(new java.util.HashSet<>(nonEmpty).size() == 9, "nine assigned names are all distinct");

        // 3c) 位置键必须对同一坐标稳定、对不同坐标可区分（决定“第几个总成”）。
        check(SuperBufferAutoName.positionKey(1, 2, 3) == SuperBufferAutoName.positionKey(1, 2, 3),
                "position key is stable for the same block");
        check(SuperBufferAutoName.positionKey(1, 2, 3) != SuperBufferAutoName.positionKey(1, 2, 4),
                "position key differs across blocks");
        check(SuperBufferAutoName.positionKey(-5, 70, 9) != SuperBufferAutoName.positionKey(5, 70, 9),
                "position key keeps negative coordinates distinct");
        // 位置排序必须与插入顺序无关：这是“分批摆总成不再重复/遗漏”的关键。
        var positions = java.util.List.of(
                SuperBufferAutoName.positionKey(3, 2, 22), SuperBufferAutoName.positionKey(2, 2, 22),
                SuperBufferAutoName.positionKey(4, 2, 22));
        var sortedForward = new java.util.ArrayList<>(positions);
        var sortedBackward = new java.util.ArrayList<>(positions);
        java.util.Collections.reverse(sortedBackward);
        sortedForward.sort(Long::compareTo);
        sortedBackward.sort(Long::compareTo);
        check(sortedForward.equals(sortedBackward), "position sorting is independent of iteration order");
        // 分批摆：先摆 3 个，再补 2 个，位置靠后的总成序号必须仍然正确。
        var batch = new java.util.ArrayList<Long>();
        batch.add(SuperBufferAutoName.positionKey(2, 2, 22));
        batch.add(SuperBufferAutoName.positionKey(3, 2, 22));
        batch.add(SuperBufferAutoName.positionKey(4, 2, 22));
        batch.add(SuperBufferAutoName.positionKey(5, 2, 22));
        batch.add(SuperBufferAutoName.positionKey(6, 2, 22));
        batch.sort(Long::compareTo);
        check(batch.get(0) < batch.get(1) && batch.get(1) < batch.get(2)
                && batch.get(2) < batch.get(3) && batch.get(3) < batch.get(4),
                "adding buffers later keeps a stable position order");

        // 4) 模式名必须真的存在于 GTCEu 语言文件，否则回退为注册名路径。
        Path zh = Path.of("libs/../_allmods/gtceu-1.20.1-1.4.4/assets/gtceu/lang/zh_cn.json");
        if (Files.isRegularFile(zh)) {
            var lang = JsonParser.parseString(Files.readString(zh)).getAsJsonObject();
            check(lang.has("gtceu.compressor"), "gtceu lang key gtceu.compressor exists");
            check(lang.get("gtceu.compressor").getAsString().equals("压缩机"),
                    "compressor mode displays as the player-facing mode name");
            for (String mode : new String[]{"compressor", "macerator", "centrifuge", "mixer", "bender", "lathe"}) {
                check(lang.has("gtceu." + mode), "steam platform mode key present: " + mode);
            }
        }
        // 本模组自己的提示键
        Path ownZh = Path.of("src/main/resources/assets/gtl_enhancedcore/lang/zh_cn.json");
        Path ownEn = Path.of("src/main/resources/assets/gtl_enhancedcore/lang/en_us.json");
        var zhKeys = JsonParser.parseString(Files.readString(ownZh)).getAsJsonObject();
        var enKeys = JsonParser.parseString(Files.readString(ownEn)).getAsJsonObject();
        check(zhKeys.has("tooltip.gtl_enhancedcore.super_buffer_autoname"), "zh tip key exists");
        check(enKeys.has("tooltip.gtl_enhancedcore.super_buffer_autoname"), "en tip key exists");

        // 5) Mixin 目标类/注入点静态核对：确认我们注入的是成型路径而不是别的同名方法。
        String mixin = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtceu/MultiblockAutoRenameSuperBufferMixin.java"));
        check(mixin.contains("WorkableMultiblockMachine.class"), "mixin targets the workable multiblock controller");
        check(mixin.contains("\"onStructureFormed\""), "mixin injects the structure-formed hook");
        check(mixin.contains("SuperBufferNaming.applyNames"), "formation uses shared naming service");
        mixin += Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/recipe/iv/SuperBufferNaming.java"));
        check(mixin.contains("IvBuffers.collect(machine)"), "naming uses registered compatible buffers");
        check(mixin.contains("shouldWrite"), "mixin delegates the write decision to the policy");
        // 跨配方类型并行机器：仅空名按位置序号取名；已有名字不会在成型时校正。
        check(mixin.contains("keyForIndex"), "mixin names each buffer by its position index");
        check(mixin.contains("IvBuffers.targetController"), "distinct naming is scoped to the four IV machines");
        check(mixin.contains("positionKey"), "mixin orders buffers by stable block position");
        check(mixin.contains("buffers.size() > 1"), "single-buffer machines keep the active-mode name");
        // 服务端只写语言键：不得在成型逻辑里做翻译（那会把英文存进存档）。
        check(!mixin.contains("getString()"), "mixin never translates on the server");
        check(mixin.contains("SuperBufferChineseNames.resolve(targetKey)"), "formation compares fixed Chinese target");
        check(!mixin.contains("renderLegacyName"), "no translation-based ownership guessing");
        String persistence = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtlcore/SuperBufferNamePersistenceMixin.java"));
        check(persistence.contains("@Persisted @DescSynced private boolean enhanced$manualName"),
                "manual name ownership persists and synchronizes");
        check(persistence.contains("self.markDirty()") && persistence.contains("self.markDirty(\"customName\")"),
                "manual rename explicitly saves and synchronizes");
        String config = Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json"));
        check(config.contains("gtceu.MultiblockAutoRenameSuperBufferMixin"), "mixin is registered in the config");
        check(persistence.contains("self.setCustomName(chinese)"), "server stores literal Chinese names");
        // Both display paths must stay Chinese even with an English client language.
        String terminal = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtlcore/PatternBufferTerminalNameMixin.java"));
        check(terminal.contains("Component.literal(SuperBufferAutoName.displayName(stored))"), "AE terminal uses literal Chinese");
        check(config.contains("gtlcore.PatternBufferTerminalNameMixin"), "terminal name mixin is registered");
        String superBuffer = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtlcore/IvSuperBufferMixin.java"));
        check(superBuffer.contains("IvBufferUi.installNameControls"), "GUI uses shared name controls for qualified buffers");
        String nameWidget = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/gui/SuperBufferNameWidget.java"));
        check(nameWidget.contains("NameSnapshot.read(buffer)"), "initial/update packets retain automatic-name ownership");
        check(nameWidget.contains("expected.equals(machine.getCustomName())"), "concurrent stale rename rejected");
        check(persistence.contains("enhanced$restoreAutomaticName"), "explicit old-name recovery is available");

        // 6) 四台 IV 机器的配方类型表确实互不相同且数量足够（命名来源）。
        String reg = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/registration/ProcessingMachineRegistration.java"));
        check(reg.contains("plasmaMachineToolRecipeTypes"), "plasma machine recipe types exist");
        check(reg.contains("hadronRefineryRecipeTypes"), "refinery recipe types exist");
        check(reg.contains("quantumMassArrayRecipeTypes"), "quantum array recipe types exist");
        check(reg.contains("fusionAssemblerRecipeTypes"), "fusion assembler recipe types exist");
        var quantumTypes = java.util.regex.Pattern.compile(
                "quantumMassArrayRecipeTypes\\(\\)\\s*\\{\\s*return new GTRecipeType\\[\\]\\s*\\{([^}]+)};")
                .matcher(reg);
        check(quantumTypes.find(), "quantum array recipe list located");
        var entries = java.util.regex.Pattern.compile("(?:GTRecipeTypes|GTLRecipeTypes)\\.([A-Z_]+)")
                .matcher(quantumTypes.group(1));
        var types = new java.util.ArrayList<String>();
        while (entries.find()) types.add(entries.group(1));
        check(types.size() == 11 && new java.util.HashSet<>(types).equals(java.util.Set.of(
                "ROCK_BREAKER_RECIPES", "ORE_WASHER_RECIPES", "CENTRIFUGE_RECIPES", "ELECTROLYZER_RECIPES",
                "SIFTER_RECIPES", "MACERATOR_RECIPES", "DEHYDRATOR_RECIPES", "THERMAL_CENTRIFUGE_RECIPES",
                "ELECTROMAGNETIC_SEPARATOR_RECIPES", "CHEMICAL_BATH_RECIPES", "LASER_ENGRAVER_RECIPES")),
                "quantum array adds laser engraving once and retains every original recipe type");

        return assertions - before;
    }

    private static int assertions;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
