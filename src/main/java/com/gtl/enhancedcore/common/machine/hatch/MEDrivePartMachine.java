package com.gtl.enhancedcore.common.machine.hatch;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;
import com.gtl.enhancedcore.common.gui.PagedInventoryWidget;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * ME 元件集成仓 —— 从 GTLsupb 搬运适配，功能不变。
 *
 * 提供 10 页存储元件槽位（每页 9×7=63 格，共 630 格），挂载到 AE2 网络，
 * 支持自动补货与驱动器优先级。渲染使用 MAX 级机器外壳叠加独立正面材质。
 *
 * 规则（见 API标准.md）：读取物品 NBT 一律用 getTag()+判空，禁止 getOrCreateTag()。
 */
public class MEDrivePartMachine
extends TieredIOPartMachine
implements IGridConnectedMachine,
IMachineLife,
IStorageProvider {
    @NotNull
    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(MEDrivePartMachine.class, TieredIOPartMachine.MANAGED_FIELD_HOLDER);
    private static final int COLS = 9;
    private static final int ROWS_PER_PAGE = 7;
    private static final int SLOTS_PER_PAGE = COLS * ROWS_PER_PAGE;
    private static final int DEFAULT_MAX_PAGES = 10;
    private static final int DEFAULT_HOLDER_TIER = 14;
    private static final int SLOT_COUNT = SLOTS_PER_PAGE * DEFAULT_MAX_PAGES;
    @Persisted
    private final NotifiableItemStackHandler machineStorage;
    private final Map<Integer, StorageCell> cellCache;
    @Persisted
    private final GridNodeHolder nodeHolder;
    @DescSynced
    private boolean isOnline;
    @Persisted
    @DescSynced
    public boolean autoFillCells;
    private final IActionSource actionSource;
    private TickableSubscription cellPullSubs;
    private boolean inAutoPull;
    @Persisted
    @DescSynced
    public int drivePriority;
    @Persisted
    private UUID placerId = null;
    private int pullCounter;

    public MEDrivePartMachine(@NotNull IMachineBlockEntity holder) {
        super(holder, DEFAULT_HOLDER_TIER, IO.BOTH);
        this.machineStorage = new NotifiableItemStackHandler(this, SLOT_COUNT, IO.NONE, IO.BOTH) {
            @Override
            public int getSlotLimit(int slot) { return 1; }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate, boolean notifyChange) {
                if (stack.isEmpty() || !getStackInSlot(slot).isEmpty()) return stack;
                ItemStack single = stack.copy();
                single.setCount(1);
                if (!super.insertItem(slot, single, simulate, notifyChange).isEmpty()) return stack;
                ItemStack remainder = stack.copy();
                remainder.shrink(1);
                return remainder;
            }
        };
        this.machineStorage.setFilter(stack -> StorageCells.isCellHandled(stack)
                || isCellPack(stack));
        this.cellCache = new HashMap<Integer, StorageCell>();
        this.nodeHolder = new GridNodeHolder((IGridConnectedMachine)this);
        this.isOnline = false;
        this.autoFillCells = false;
        this.actionSource = IActionSource.ofMachine(() -> this.nodeHolder.getMainNode().getNode());
        this.cellPullSubs = null;
        this.inAutoPull = false;
        this.drivePriority = 0;
        this.pullCounter = 0;
        this.getMainNode().addService(IStorageProvider.class, this);
        this.machineStorage.addChangedListener(this::onMachineStorageContentsChanged);
    }

    public StorageCell getCellInventory(int index) {
        ItemStack stack = this.machineStorage.getStackInSlot(index);
        if (stack.isEmpty()) {
            return null;
        }
        return this.cellCache.computeIfAbsent(index, ignored -> this.createCellInventory(stack));
    }

    private StorageCell createCellInventory(ItemStack stack) {
        StorageCell[] cell = new StorageCell[1];
        cell[0] = StorageCells.getCellInventory((ItemStack)stack, () -> {
            if (cell[0] != null) {
                cell[0].persist();
            }
            this.markDirty();
        });
        return cell[0];
    }

    private StorageCell createInnerCellInventory(ItemStack innerStack, ItemStack packStack, int cellIndex) {
        StorageCell[] cell = new StorageCell[1];
        cell[0] = StorageCells.getCellInventory((ItemStack)innerStack, () -> {
            CompoundTag tag;
            ListTag keysList;
            if (cell[0] != null) {
                cell[0].persist();
            }
            // 用 getTag() + 判空，绝不用 getOrCreateTag()：后者会给无 NBT 的物品创建空标签，影响堆叠判定。
            if ((tag = packStack.getTag()) != null
                    && cellIndex < (keysList = tag.getList("keys", 10)).size()) {
                keysList.set(cellIndex, (Tag)innerStack.save(new CompoundTag()));
            }
            this.markDirty();
        });
        return cell[0];
    }

    private void onMachineStorageContentsChanged() {
        this.invalidateCellCache();
        this.markDirty();
        if (!this.inAutoPull) {
            this.onChanged();
            IStorageProvider.requestUpdate((IManagedGridNode)this.getMainNode());
        }
    }

    private void invalidateCellCache() {
        this.cellCache.values().forEach(StorageCell::persist);
        this.cellCache.clear();
    }

    public void mountInventories(IStorageMounts mounts) {
        if (!this.getMainNode().isOnline()) {
            return;
        }
        for (int slot = 0; slot < this.machineStorage.getSlots(); slot++) {
            ItemStack stack = this.machineStorage.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            List<StorageCell> innerCells = this.getInnerCellsFromPack(stack);
            if (innerCells != null) {
                for (StorageCell innerCell : innerCells) {
                    mounts.mount((MEStorage)innerCell, this.drivePriority);
                }
                continue;
            }
            StorageCell cell = this.cellCache.computeIfAbsent(slot, k -> this.createCellInventory(stack));
            if (cell == null) continue;
            mounts.mount((MEStorage)cell, this.drivePriority);
        }
    }

    private List<StorageCell> getInnerCellsFromPack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("keys")) {
            return null;
        }
        ListTag keysList = tag.getList("keys", 10);
        if (keysList.isEmpty()) {
            return null;
        }
        CompoundTag firstKey = keysList.getCompound(0);
        if (!"expatternprovider:infinity_cell".equals(firstKey.getString("id"))) {
            return null;
        }
        ArrayList<StorageCell> innerCells = new ArrayList<StorageCell>();
        for (int i = 0; i < keysList.size(); ++i) {
            StorageCell innerCell;
            ItemStack innerStack;
            CompoundTag keyTag = keysList.getCompound(i).copy();
            if (!keyTag.contains("Count")) {
                keyTag.putByte("Count", (byte)1);
            }
            if ((innerStack = ItemStack.of((CompoundTag)keyTag)).isEmpty() || (innerCell = this.createInnerCellInventory(innerStack, stack, i)) == null) continue;
            innerCells.add(innerCell);
        }
        return innerCells.isEmpty() ? null : innerCells;
    }

    private static boolean isCellPack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        ListTag keys = tag.getList("keys", Tag.TAG_COMPOUND);
        return !keys.isEmpty() && "expatternprovider:infinity_cell".equals(keys.getCompound(0).getString("id"));
    }

    public IManagedGridNode getMainNode() {
        return this.nodeHolder.getMainNode();
    }

    public boolean isOnline() {
        return this.isOnline;
    }

    public void setOnline(boolean online) {
        this.isOnline = online;
    }

    public boolean shouldSyncME() {
        return false;
    }

    public boolean updateMEStatus() {
        boolean online = this.getMainNode().isActive();
        if (this.isOnline != online) {
            this.isOnline = online;
        }
        return this.isOnline;
    }

    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        boolean wasOnline = this.isOnline;
        this.isOnline = this.getMainNode().isActive();
        if (wasOnline != this.isOnline) {
            IStorageProvider.requestUpdate((IManagedGridNode)this.getMainNode());
        }
    }

    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        this.getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    public void onLoad() {
        super.onLoad();
        this.getMainNode().setExposedOnSides(EnumSet.allOf(Direction.class));
        if (this.isRemote()) return;
        this.cellPullSubs = this.subscribeServerTick(this.cellPullSubs, () -> {
            ++this.pullCounter;
            if (this.pullCounter >= 20) {
                this.pullCounter = 0;
                if (this.isOnline && this.getMainNode().isReady()) {
                    if (this.autoFillCells) {
                        this.tryPullCells();
                    }
                }
            }
        });
    }

    @Override
    public void onMachinePlaced(LivingEntity entity, ItemStack stack) {
        if (entity instanceof Player player) {
            // 持久化放置者 UUID（重进存档后网格注册不依赖 AE2 节点 NBT）
            this.placerId = player.getUUID();
            // 同时把网格节点 owner 设为放置者
            this.getMainNode().setOwningPlayer(player);
        }
    }


    public void onMachineRemoved() {
        if (this.isRemote()) return;
        this.cellCache.values().forEach(StorageCell::persist);
        this.clearInventory(this.machineStorage.storage);
        this.invalidateCellCache();
        IStorageProvider.requestUpdate(this.getMainNode());
    }

    public Widget createUIWidget() {
        return new PagedInventoryWidget(this.machineStorage, COLS, ROWS_PER_PAGE,
                () -> this.isOnline ? "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline");
    }


    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        super.attachConfigurators(configuratorPanel);
        configuratorPanel.attachConfigurators(new IFancyConfigurator[]{new IFancyConfiguratorButton.Toggle((IGuiTexture)GuiTextures.BUTTON_POWER.getSubTexture(0.0, 0.0, 1.0, 0.5), (IGuiTexture)GuiTextures.BUTTON_POWER.getSubTexture(0.0, 0.5, 1.0, 0.5), () -> this.autoFillCells, (a, b) -> {
            this.autoFillCells = !this.autoFillCells;
            this.markDirty();
        }).setTooltipsSupplier(showAlternate -> Collections.singletonList(Component.translatable((String)"tooltip.gtl_enhancedcore.me_drive.auto_fill")))});
        configuratorPanel.attachConfigurators(new IFancyConfigurator[]{new IFancyConfigurator(){

            public Component getTitle() {
                return Component.translatable((String)"gui.gtl_enhancedcore.me_drive.priority");
            }

            public IGuiTexture getIcon() {
                return new ResourceTexture("gtceu:textures/item/portable_debug_scanner.png");
            }

            public Widget createConfigurator() {
                return new WidgetGroup(0, 0, 100, 20).addWidget((Widget)new IntInputWidget(() -> MEDrivePartMachine.this.drivePriority, value -> {
                    MEDrivePartMachine.this.drivePriority = value != null ? value : 0;
                    MEDrivePartMachine.this.markDirty();
                    IStorageProvider.requestUpdate((IManagedGridNode)MEDrivePartMachine.this.getMainNode());
                }).setMin(Integer.MIN_VALUE).setMax(Integer.MAX_VALUE));
            }
        }});
    }

    private void tryPullCells() {
        if (!this.autoFillCells) {
            return;
        }
        int targetSlot = CellSlotScan.nextEmpty(this.machineStorage.getSlots(), 0,
                slot -> this.machineStorage.getStackInSlot(slot).isEmpty());
        if (targetSlot == -1) return;
        IManagedGridNode mainNode = this.getMainNode();
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return;
        }
        IStorageService storageService = grid.getStorageService();
        KeyCounter cachedInv = storageService.getCachedInventory();
        MEStorage networkStorage = storageService.getInventory();
        this.inAutoPull = true;
        boolean pulled = false;
        try {
            // Snapshot keys before extraction mutates the network's live cached inventory.
            List<AEKey> candidates = new ArrayList<>();
            for (Object2LongMap.Entry<AEKey> entry : cachedInv) {
                if (entry.getLongValue() > 0L) candidates.add(entry.getKey());
            }
            for (AEKey key : candidates) {
                if (!(key instanceof AEItemKey)) continue;
                AEItemKey itemKey = (AEItemKey)key;
                if (!StorageCells.isCellHandled((ItemStack)itemKey.toStack())) continue;
                long extracted = networkStorage.extract(key, 1L, Actionable.SIMULATE, this.actionSource);
                if (extracted <= 0L) continue;
                if (networkStorage.extract(key, 1L, Actionable.MODULATE, this.actionSource) != 1L) continue;
                ItemStack stack = itemKey.toStack();
                stack.setCount(1);
                this.machineStorage.setStackInSlot(targetSlot, stack);
                pulled = true;
                targetSlot = CellSlotScan.nextEmpty(this.machineStorage.getSlots(), targetSlot + 1,
                        slot -> this.machineStorage.getStackInSlot(slot).isEmpty());
                if (targetSlot == -1) break;
            }
        }
        finally {
            this.inAutoPull = false;
            if (pulled) {
                this.onChanged();
                IStorageProvider.requestUpdate((IManagedGridNode)this.getMainNode());
            }
        }
    }

    @Override
    public void onUnload() {
        if (this.cellPullSubs != null) {
            this.cellPullSubs.unsubscribe();
            this.cellPullSubs = null;
        }
        this.invalidateCellCache();
        super.onUnload();
    }

    @NotNull
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
