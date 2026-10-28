package com.gtl.enhancedcore.mixin.extendedae.client;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import appeng.client.gui.me.patternaccess.PatternSlot;
import appeng.client.gui.style.ScreenStyle;
import appeng.util.inv.AppEngInternalInventory;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import com.glodblock.github.extendedae.container.ContainerExPatternTerminal;
import com.google.common.collect.HashMultimap;
import com.gtl.enhancedcore.client.pattern.PatternTerminalSlotProjection;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;

/** A display projection only: PatternSlot retains the original server inventory index. */
@Mixin(value = GuiExPatternTerminal.class, remap = false, priority = 100)
public abstract class ExPatternTerminalEmptySlotsMixin extends AEBaseScreen<ContainerExPatternTerminal> {
    @Shadow(remap = false) private boolean needsRefresh;

    @Unique private boolean gtlEnhancedcore$showEmptySlots;
    @Unique private final IdentityHashMap<AppEngInternalInventory, int[]> gtlEnhancedcore$slotIndices =
            new IdentityHashMap<>();

    protected ExPatternTerminalEmptySlotsMixin(ContainerExPatternTerminal menu, Inventory inventory,
                                               Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    // ExtendedAE's shipped override is already SRG-named; no refmap inference is needed.
    @Inject(method = "m_7856_()V", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private void gtlEnhancedcore$addEmptySlotToggle(CallbackInfo ci) {
        Button toggle = Button.builder(gtlEnhancedcore$toggleText(), button -> {
            gtlEnhancedcore$showEmptySlots = !gtlEnhancedcore$showEmptySlots;
            needsRefresh = true;
            button.setMessage(gtlEnhancedcore$toggleText());
            button.setTooltip(Tooltip.create(gtlEnhancedcore$toggleTooltip()));
        }).bounds(leftPos + imageWidth - 22, topPos + 2, 18, 18).build();
        toggle.setTooltip(Tooltip.create(gtlEnhancedcore$toggleTooltip()));
        addRenderableWidget(toggle);
    }

    @Unique
    private Component gtlEnhancedcore$toggleText() {
        return Component.translatable(gtlEnhancedcore$showEmptySlots
                ? "gtl_enhancedcore.pattern_terminal.hide_empty.short"
                : "gtl_enhancedcore.pattern_terminal.show_empty.short");
    }

    @Unique
    private Component gtlEnhancedcore$toggleTooltip() {
        return Component.translatable(gtlEnhancedcore$showEmptySlots
                ? "gtl_enhancedcore.pattern_terminal.hide_empty"
                : "gtl_enhancedcore.pattern_terminal.show_empty");
    }

    @Inject(method = "refreshList", at = @At("HEAD"), require = 1, allow = 1)
    private void gtlEnhancedcore$resetProjection(CallbackInfo ci) {
        gtlEnhancedcore$slotIndices.clear();
    }

    @Unique
    private int[] gtlEnhancedcore$indices(AppEngInternalInventory inventory) {
        return gtlEnhancedcore$slotIndices.computeIfAbsent(inventory,
                inv -> PatternTerminalSlotProjection.occupiedSlots(inv.size(),
                        slot -> !inv.getStackInSlot(slot).isEmpty()));
    }

    @Redirect(method = "refreshList", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/HashMultimap;put(Ljava/lang/Object;Ljava/lang/Object;)Z"),
            require = 1, allow = 1)
    private boolean gtlEnhancedcore$omitEmptyProviders(
            HashMultimap<PatternContainerGroup, PatternContainerRecord> groups,
            Object group, Object provider) {
        PatternContainerRecord record = (PatternContainerRecord) provider;
        if (!gtlEnhancedcore$showEmptySlots && gtlEnhancedcore$indices(record.getInventory()).length == 0) {
            return false;
        }
        return groups.put((PatternContainerGroup) group, record);
    }

    @Redirect(method = "refreshList", at = @At(value = "INVOKE",
            target = "Lappeng/util/inv/AppEngInternalInventory;size()I"), require = 3, allow = 3)
    private int gtlEnhancedcore$visibleSize(AppEngInternalInventory inventory) {
        return gtlEnhancedcore$showEmptySlots ? inventory.size() : gtlEnhancedcore$indices(inventory).length;
    }

    @Redirect(method = "drawFG", at = @At(value = "NEW",
            target = "(Lappeng/client/gui/me/patternaccess/PatternContainerRecord;III)"
                    + "Lappeng/client/gui/me/patternaccess/PatternSlot;"), require = 1, allow = 1)
    private PatternSlot gtlEnhancedcore$originalSlot(PatternContainerRecord record, int displaySlot, int x, int y) {
        int slot = gtlEnhancedcore$showEmptySlots ? displaySlot
                : gtlEnhancedcore$indices(record.getInventory())[displaySlot];
        return new PatternSlot(record, slot, x, y);
    }

    // Upstream incrementals update stacks without rebuilding rows; occupancy can change in either direction.
    @Inject(method = "postIncrementalUpdate", at = @At("RETURN"), require = 1, allow = 2)
    private void gtlEnhancedcore$refreshChangedSlots(CallbackInfo ci) {
        if (!gtlEnhancedcore$showEmptySlots) needsRefresh = true;
    }
}
