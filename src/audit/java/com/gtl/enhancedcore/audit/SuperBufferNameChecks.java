package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.gui.SuperBufferNameWidget;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNameAccess;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferChineseNames;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import io.netty.buffer.Unpooled;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.registries.ForgeRegistries;

public final class SuperBufferNameChecks {
    private SuperBufferNameChecks() {}

    public static int run(MinecraftServer server) {
        int checks = 0;
        var world = server.overworld();
        var pos = new BlockPos(14, 80, 3);
        world.setBlock(pos, ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("gtladditions", "me_super_pattern_buffer")).defaultBlockState(), 3);
        var machine = (MESuperPatternBufferPartMachine) MetaMachine.getMachine(world, pos);
        var names = (SuperBufferNameAccess) (Object) machine;
        names.enhanced$setAutomaticName("gtceu.compressor");
        check(machine.getCustomName().equals("\u538b\u7f29\u673a"), "Server did not store fixed Chinese: " + machine.getCustomName());
        checks++;
        var automatic = new SuperBufferNameWidget.NameSnapshot(machine.getCustomName(), true);
        var wire = new FriendlyByteBuf(Unpooled.buffer());
        Language previous = Language.getInstance();
        try {
            automatic.write(wire);
            var received = SuperBufferNameWidget.NameSnapshot.read(wire);
            var terminal = machine.getTerminalGroup().name();
            check(!Component.Serializer.toJson(terminal).contains("\"translate\""),
                    "AE automatic name still depends on client language");
            checks++;
            wire.clear();
            wire.writeComponent(terminal);
            var receivedTerminal = wire.readComponent();
            for (var locale : Map.of("en_us", "Compressor", "zh_cn", "\u538b\u7f29\u673a").entrySet()) {
                Language.inject(language(locale.getValue()));
                check(received.display().getString().equals("\u538b\u7f29\u673a"), "GUI language " + locale.getKey());
                check(receivedTerminal.getString().equals("\u538b\u7f29\u673a"), "AE language " + locale.getKey());
                check(machine.getCustomName().equals("\u538b\u7f29\u673a"), "Display changed saved name");
                checks += 3;
            }
            check(new SuperBufferNameWidget.NameSnapshot("gtceu.compressor", true).display().getString()
                    .equals("\u538b\u7f29\u673a"), "Legacy automatic key did not display in Chinese");
            checks++;
            var missing = new java.util.ArrayList<String>();
            for (var type : com.gregtechceu.gtceu.api.registry.GTRegistries.RECIPE_TYPES.values()) {
                String key = type.registryName.toLanguageKey();
                if (key.equals("gtceu.dummy")) continue;
                if (!SuperBufferChineseNames.hasTranslation(key)) missing.add(key);
                checks++;
            }
            check(missing.isEmpty(), "Missing Chinese recipe types: " + missing);
            for (String manual : new String[]{"Compressor", "gtceu.compressor", "", "\u6211\u7684\u603b\u6210"}) {
                machine.setCustomName(manual);
                check(names.enhanced$isManualName(), "Manual ownership lost");
                var manualDisplay = new SuperBufferNameWidget.NameSnapshot(machine.getCustomName(), false).display();
                check(manualDisplay.getString().equals(manual)
                        && !Component.Serializer.toJson(manualDisplay).contains("\"translate\""),
                        "Manual GUI name was translated");
                if (!manual.isEmpty()) {
                    check(machine.getTerminalGroup().name().getString().equals(manual),
                            "Manual AE name was translated");
                    checks++;
                }
                names.enhanced$setAutomaticName("gtceu.lathe");
                check(machine.getCustomName().equals(manual), "Manual name overwritten");
                check(!names.enhanced$restoreAutomaticName(), "Unformed restore accepted");
                check(machine.getCustomName().equals(manual), "Rejected restore changed name");
                checks += 5;
            }
            var group = (WidgetGroup) machine.createUIWidget();
            check(group.getWidgetsByType(SuperBufferNameWidget.class).size() == 1, "Localized name widget missing");
            checks++;
            var first = new SuperBufferNameWidget(100, 2, 70, 10, machine);
            var second = new SuperBufferNameWidget(100, 2, 70, 10, machine);
            String oldName = machine.getCustomName();
            wire.clear();
            wire.writeUtf(oldName).writeUtf("first player's name", 256);
            first.handleClientAction(21, wire);
            check(machine.getCustomName().equals("first player's name"), "Current rename rejected");
            check(names.enhanced$isManualName(), "GUI rename did not persist manual ownership");
            wire.clear();
            wire.writeUtf(oldName).writeUtf("stale second name", 256);
            second.handleClientAction(21, wire);
            check(machine.getCustomName().equals("first player's name"), "Stale second menu overwrote first rename");
            wire.clear();
            wire.writeUtf(oldName);
            second.handleClientAction(22, wire);
            check(machine.getCustomName().equals("first player's name"), "Stale restore overwrote first rename");
            wire.clear();
            first.writeInitialData(wire);
            var initial = SuperBufferNameWidget.NameSnapshot.read(wire);
            check(initial.stored().equals("first player's name") && !initial.automatic() && !wire.isReadable(),
                    "Initial GUI packet lost manual ownership or changed widget ordering");
            checks += 5;
        } finally {
            Language.inject(previous);
            wire.release();
        }
        return checks;
    }

    private static Language language(String translated) {
        return new Language() {
            @Override public String getOrDefault(String key, String fallback) {
                return key.equals("gtceu.compressor") ? translated : fallback;
            }
            @Override public boolean has(String key) { return key.equals("gtceu.compressor"); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.EMPTY;
            }
        };
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }
}
