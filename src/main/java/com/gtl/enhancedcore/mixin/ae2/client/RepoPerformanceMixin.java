package com.gtl.enhancedcore.mixin.ae2.client;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.menu.me.common.GridInventoryEntry;
import com.gtl.enhancedcore.client.performance.PreciseAmountSortCache;
import com.gtl.enhancedcore.client.performance.PreciseRepoAmountAccess;
import com.gtl.enhancedcore.client.performance.RepoRebuildControl;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs after GTLCore fix4 adds its public exact-amount setter. */
@Mixin(value = Repo.class, remap = false, priority = 900)
public abstract class RepoPerformanceMixin implements RepoRebuildControl {
    @Shadow public abstract Set<GridInventoryEntry> getAllEntries();
    @Shadow public abstract void updateView();
    @Unique private final PreciseAmountSortCache<AEKey> enhancedcore$sortKeys = new PreciseAmountSortCache<>();
    @Unique private int enhancedcore$batchDepth;
    @Unique private boolean enhancedcore$pendingView;

    @Unique private boolean enhancedcore$currentRepo() {
        var screen = Minecraft.getInstance().screen;
        return screen instanceof MEStorageScreen<?> storage && storage.getMenu().getClientRepo() == (Object) this;
    }

    @WrapMethod(method = "handleUpdate(ZLjava/util/List;)V")
    private void enhancedcore$batchInventory(boolean full, List<GridInventoryEntry> entries, Operation<Void> original) {
        boolean batch = enhancedcore$currentRepo();
        if (batch) enhancedcore$batchDepth++;
        try { original.call(full, entries); }
        finally { if (batch) enhancedcore$batchDepth--; }
    }

    @Dynamic("GTLCore fix4 public PreciseRepoAmounts implementation")
    @WrapMethod(method = "gtlcore$setPreciseAmounts(Ljava/util/Map;)V")
    private void enhancedcore$batchExact(Map<AEKey, BigInteger> amounts, Operation<Void> original) {
        boolean batch = enhancedcore$currentRepo();
        if (batch) enhancedcore$batchDepth++;
        try { original.call(amounts); }
        finally { if (batch) enhancedcore$batchDepth--; }
    }

    @Inject(method = "updateView", at = @At("HEAD"), cancellable = true)
    private void enhancedcore$deferView(CallbackInfo ci) {
        if (enhancedcore$batchDepth != 0) {
            enhancedcore$pendingView = true;
            ci.cancel();
        } else {
            // An explicit filter/settings/pin update also incorporates queued inventory changes.
            enhancedcore$pendingView = false;
        }
    }

    @Override public void enhancedcore$flushView() {
        if (enhancedcore$pendingView && enhancedcore$batchDepth == 0) {
            enhancedcore$pendingView = false;
            updateView();
        }
    }

    // A click, JEI query or scrollbar read between packet processing and rendering must see the
    // same complete view that native synchronous updates would have produced.
    @Inject(method = {"get(I)Lappeng/menu/me/common/GridInventoryEntry;", "size()I",
            "getPinnedEntries()Ljava/util/List;", "hasPinnedRow()Z"}, at = @At("HEAD"))
    private void enhancedcore$flushRead(CallbackInfoReturnable<?> cir) { enhancedcore$flushView(); }

    @WrapMethod(method = "getComparator")
    private Comparator<? super GridInventoryEntry> enhancedcore$preciseKeys(SortOrder order, SortDir direction,
            Operation<Comparator<? super GridInventoryEntry>> original) {
        if (order != SortOrder.AMOUNT) {
            enhancedcore$sortKeys.clear();
            return original.call(order, direction);
        }
        var exact = PreciseRepoAmountAccess.get((Repo) (Object) this);
        if (exact == null || exact.isEmpty()) {
            enhancedcore$sortKeys.clear();
            return original.call(order, direction);
        }
        return enhancedcore$sortKeys.comparator(getAllEntries(), exact,
                GridInventoryEntry::getWhat, GridInventoryEntry::getStoredAmount, AEKey::getAmountPerUnit,
                direction == SortDir.ASCENDING);
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void enhancedcore$releaseView(CallbackInfo ci) {
        enhancedcore$pendingView = false;
        enhancedcore$sortKeys.clear();
    }
}
