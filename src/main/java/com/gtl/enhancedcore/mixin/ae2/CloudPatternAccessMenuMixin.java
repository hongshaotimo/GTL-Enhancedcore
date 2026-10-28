package com.gtl.enhancedcore.mixin.ae2;

import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.api.networking.IGrid;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import appeng.core.sync.packets.PatternAccessTerminalPacket;
import appeng.helpers.InventoryAction;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport;
import java.util.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Projects the complete authorized library catalogue into one AE provider record. */
@Mixin(value = PatternAccessTermMenu.class, remap = false)
public abstract class CloudPatternAccessMenuMixin extends AEBaseMenu implements CloudPatternAccessMenu {
    @Shadow private IGrid getGrid() { throw new AssertionError(); }
    @Unique private static long cloud$nextId = Long.MIN_VALUE;
    @Unique private long cloud$id = cloud$nextId++;
    @Unique private String cloud$input = "", cloud$output = "";
    @Unique private BitSet cloud$inputItems=new BitSet(),cloud$inputFluids=new BitSet(),cloud$outputItems=new BitSet(),cloud$outputFluids=new BitSet();
    @Unique private List<CloudPatternAccessRow> cloud$rows = List.of();
    @Unique private UUID cloud$library;
    @Unique private long cloud$revision = Long.MIN_VALUE;
    @Unique private long cloud$locationRevision=Long.MIN_VALUE;
    @Unique private boolean cloud$projectionDirty = true;
    @Unique private List<UUID> cloud$matches=List.of();
    @Unique private boolean cloud$viewReady;
    @Unique private long cloud$request;
    @Unique private final CloudPatternAccessHandles cloud$handles=new CloudPatternAccessHandles();
    protected CloudPatternAccessMenuMixin(MenuType<?> type, int id, Inventory inventory, Object host) { super(type,id,inventory,host); }
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/api/util/IConfigurableObject;Z)V", at=@At("TAIL"))
    private void cloud$actions(CallbackInfo ci) {
        registerClientAction("cloudPatternSearch", String.class, value -> {
            if(value.length()>65536)return;
            String[] parts=value.split("\n",-1);
            if(parts.length!=7)return;
            long request;
            try { request=Long.parseLong(parts[6]); }
            catch(NumberFormatException malformed){return;}
            if(request<cloud$request)return;
            BitSet inItems=new BitSet(),inFluids=new BitSet(),outItems=new BitSet(),outFluids=new BitSet();
            try {
                inItems=cloud$bits(parts[2],net.minecraft.core.registries.BuiltInRegistries.ITEM.size());
                inFluids=cloud$bits(parts[3],net.minecraft.core.registries.BuiltInRegistries.FLUID.size());
                outItems=cloud$bits(parts[4],net.minecraft.core.registries.BuiltInRegistries.ITEM.size());
                outFluids=cloud$bits(parts[5],net.minecraft.core.registries.BuiltInRegistries.FLUID.size());
            } catch(IllegalArgumentException malformed) { return; }
            cloud$input=parts[0].substring(0,Math.min(96,parts[0].length())).toLowerCase(Locale.ROOT);
            cloud$output=parts[1].substring(0,Math.min(96,parts[1].length())).toLowerCase(Locale.ROOT);
            cloud$inputItems=inItems;cloud$inputFluids=inFluids;cloud$outputItems=outItems;cloud$outputFluids=outFluids;
            cloud$request=request;cloud$revision=Long.MIN_VALUE;cloud$viewReady=true;
        });
    }
    @Override public void cloud$accessSearch(String payload) { sendClientAction("cloudPatternSearch",payload); }
    @Unique private static BitSet cloud$bits(String text,int maximum){
        if(text.length()>((maximum+7)/8+2)*4/3+4)throw new IllegalArgumentException("Cloud search alias too large");
        var bits=BitSet.valueOf(Base64.getDecoder().decode(text));
        if(bits.length()>maximum)throw new IllegalArgumentException("Cloud search alias out of registry");return bits;
    }
    @Unique private CloudLibraryNetwork.Binding cloud$binding(ServerPlayer player) {
        boolean extended=false;for(Class<?> type=getClass();type!=null;type=type.getSuperclass())
            if(type.getName().equals("com.glodblock.github.extendedae.container.ContainerExPatternTerminal")){extended=true;break;}
        if(!extended)return CloudLibraryNetwork.resolve(null,player);
        if(player.containerMenu!=this || !isValidMenu() || !stillValid(player)) return CloudLibraryNetwork.resolve(null,player);
        var block=getBlockEntity();
        if(block!=null && (!(block.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || !CloudLibraryService.claimAllows(player,level,block.getBlockPos()))) return CloudLibraryNetwork.resolve(null,player);
        return CloudLibraryNetwork.resolve(getGrid(),player);
    }
    @Inject(method={"broadcastChanges()V","m_38946_()V"},at=@At("TAIL"),require=1)
    private void cloud$sync(CallbackInfo ci) {
        if(!(getPlayer() instanceof ServerPlayer player)) return;
        if(!cloud$viewReady)return;
        var binding=cloud$binding(player);
        if(!binding.available()) { cloud$clear(); return; }
        var library=CloudPatternStore.get(player.serverLevel()).library(binding.libraryId());
        if(!Objects.equals(cloud$library,binding.libraryId())||cloud$revision!=library.catalogRevision()){
            cloud$hideProjection();
            cloud$matches=CloudLibraryService.terminalCatalogue(library,player.serverLevel(),cloud$input,cloud$output,
                    cloud$inputItems,cloud$inputFluids,cloud$outputItems,cloud$outputFluids);
            cloud$rows=List.of();
            cloud$library=binding.libraryId();cloud$revision=library.catalogRevision();cloud$projectionDirty=true;
        }
        // Lease churn refreshes the currently projected library inventory.
        if(cloud$locationRevision!=library.locationRevision()&&!cloud$projectionDirty){
            var currentRows=new ArrayList<CloudPatternAccessRow>(cloud$rows.size());
            for(var row:cloud$rows){
                var entry=library.entry(row.id());
                currentRows.add(new CloudPatternAccessRow(row.id(),entry.revision(),row.original()));
            }
            cloud$rows=List.copyOf(currentRows);cloud$locationRevision=library.locationRevision();
        }
        if(!cloud$projectionDirty)return;
        cloud$locationRevision=library.locationRevision();cloud$projectionDirty=false;
        if(cloud$library!=null)sendPacketToClient(PatternAccessTerminalPacket.fullUpdate(cloud$id,0,Long.MIN_VALUE,PatternContainerGroup.nothing(),new Int2ObjectOpenHashMap<>()));
        var rows=new ArrayList<CloudPatternAccessRow>();
        for(UUID id:cloud$matches){
            var entry=library.entry(id);
            if(entry.status()==CloudLedger.Status.AVAILABLE||entry.status()==CloudLedger.Status.LEASED||entry.status()==CloudLedger.Status.ARCHIVED)
                rows.add(new CloudPatternAccessRow(id,entry.revision(),entry.original()));
        }
        cloud$rows=List.copyOf(rows);
        cloud$library=binding.libraryId();cloud$revision=library.catalogRevision();cloud$id=cloud$nextId++;
        cloud$handles.issue(cloud$id);
        var group=new PatternContainerGroup(AEItemKey.of(binding.main().getDefinition().asStack()),
                Component.translatable("block.gtl_enhancedcore.cloud_pattern_library",cloud$library.toString(),cloud$input,cloud$output,Long.toString(cloud$request))
                        .append(Component.literal(" · Σ"+cloud$rows.size())),
                List.of(Component.translatable("gtl_enhancedcore.cloud.terminal.hint")));
        sendPacketToClient(PatternAccessTerminalPacket.fullUpdate(cloud$id,cloud$rows.size(),Long.MIN_VALUE,group,new Int2ObjectOpenHashMap<>()));
        for(int offset=0;offset<cloud$rows.size();offset+=36){
            var stacks=new Int2ObjectOpenHashMap<ItemStack>();
            for(int slot=offset;slot<Math.min(cloud$rows.size(),offset+36);slot++)stacks.put(slot,cloud$rows.get(slot).original().copy());
            sendPacketToClient(PatternAccessTerminalPacket.incrementalUpdate(cloud$id,stacks));
        }
    }
    @Inject(method="sendFullUpdate",at=@At("TAIL"))
    private void cloud$upstreamCleared(IGrid grid,CallbackInfo ci) { cloud$projectionDirty=true; }
    @Unique private void cloud$hideProjection() {
        if(cloud$library!=null)sendPacketToClient(PatternAccessTerminalPacket.fullUpdate(cloud$id,0,Long.MIN_VALUE,PatternContainerGroup.nothing(),new Int2ObjectOpenHashMap<>()));
    }
    @Unique private void cloud$clear() {
        cloud$hideProjection();
        cloud$library=null;cloud$rows=List.of();cloud$revision=Long.MIN_VALUE;
    }
    @Inject(method="doAction",at=@At("HEAD"),cancellable=true)
    private void cloud$take(ServerPlayer player,InventoryAction action,int slot,long id,CallbackInfo ci) {
        if(!cloud$handles.owns(id))return;ci.cancel();
        if(id!=cloud$id||slot<0||slot>=cloud$rows.size()
                ||(action!=InventoryAction.PICKUP_OR_SET_DOWN&&action!=InventoryAction.SHIFT_CLICK&&action!=InventoryAction.PICKUP_SINGLE))return;
        var binding=cloud$binding(player);if(!binding.available()||!Objects.equals(binding.libraryId(),cloud$library))return;
        if(!player.containerMenu.getCarried().isEmpty()){
            if(action==InventoryAction.PICKUP_OR_SET_DOWN && binding.main().acceptTerminalOriginal(player,getGrid()))cloud$revision=Long.MIN_VALUE;
            return;
        }
        var row=cloud$rows.get(slot);var store=CloudPatternStore.get(player.serverLevel());var entry=store.library(cloud$library).entry(row.id());
        if(entry.revision()!=row.revision()||!ItemStack.matches(entry.original(),row.original())){cloud$revision=Long.MIN_VALUE;return;}
        if(CloudNetworkPatternImport.entryLocked(cloud$library,row.id()))return;
        if(action==InventoryAction.SHIFT_CLICK&&player.getInventory().getFreeSlot()<0)return;
        UUID operation=UUID.randomUUID();var output=store.extractFromCore(cloud$library,player.getUUID(),row.id(),operation);store.checkpointLibrary(cloud$library);
        if(output.isEmpty())return;
        if(action==InventoryAction.SHIFT_CLICK&&!player.getInventory().add(output)){store.needsReview(cloud$library,player.getUUID(),operation);store.checkpointLibrary(cloud$library);return;}
        if(action!=InventoryAction.SHIFT_CLICK)player.containerMenu.setCarried(output);
        store.acknowledge(cloud$library,player.getUUID(),operation);store.checkpointLibrary(cloud$library);cloud$revision=Long.MIN_VALUE;
    }
}
