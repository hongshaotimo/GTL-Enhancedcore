package com.gtl.enhancedcore.mixin.mc.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.PinnedKeys;
import appeng.client.gui.me.common.RepoSlot;
import appeng.menu.me.common.GridInventoryEntry;
import com.gtl.enhancedcore.client.PinnedKeysTracker;
import com.gtl.enhancedcore.client.AEPinnedKeysStore;
import com.gtl.enhancedcore.mixin.ae2.client.MEStorageScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 终端物品置顶：Alt+左键点击终端物品 = 置顶到列表第一行（再次点击取消），可置顶多个（上限 9，AE2 原版 PinnedKeys 机制）。
 * 注入点选在 AbstractContainerScreen（hoveredSlot/mouseClicked 的声明类），仅对 MEStorageScreen 生效。
 * 手动置顶同步登记到 PinnedKeysTracker，使 PinnedKeys.prune 在关闭终端时只清理原版自动置顶、保留手动置顶。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void gtlEnhancedcore$handlePinClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !Screen.hasAltDown()) {
            return;
        }
        // AEBaseScreen.mouseClicked 在 btn==1（右键）时会以 button=0 转发给 super（反编译实证），
        // 不校验真实按键会让 Alt+右键也误触发置顶切换。直接问 GLFW 当前左键是否按下。
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            return;
        }
        if (!(this.hoveredSlot instanceof RepoSlot repoSlot)) {
            return;
        }
        if (!((Object) this instanceof MEStorageScreen meScreen)) {
            return;
        }
        GridInventoryEntry entry = repoSlot.getEntry();
        if (entry == null) {
            return;
        }
        AEKey what = entry.getWhat();
        if (PinnedKeys.isPinned(what)) {
            PinnedKeys.unpin(what);
            PinnedKeysTracker.unmark(what);
        } else {
            PinnedKeys.pinKey(what, PinnedKeys.PinReason.CRAFTING);
            PinnedKeysTracker.mark(what);
        }
        // 手动置顶变更后立即持久化，保证重启游戏后置顶保留
        AEPinnedKeysStore.save(PinnedKeysTracker.snapshot());
        // 刷新置顶行（AE2 Repo.updateView 会重建 pinnedRow）
        ((MEStorageScreenAccessor) meScreen).getRepo().updateView();
        cir.setReturnValue(true);
    }

}
