package com.gtl.enhancedcore.common.cloud.transfer;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.helpers.patternprovider.*;
import appeng.parts.AEBasePart;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import static com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkImportState.*;

/** One explicit import session per library. World/inventory access is confined to the server thread. */
public final class CloudNetworkPatternImport {
    private static final Map<MinecraftServer,Map<UUID,Task>> TASKS=new WeakHashMap<>();
    private static final Map<Object,Task> LOCKS=new IdentityHashMap<>();
    private CloudNetworkPatternImport() {}
    public static boolean sourceLocked(Object source){return LOCKS.containsKey(source);}
    public static boolean entryLocked(UUID library,UUID entry){
        for(var tasks:TASKS.values()){
            Task t=tasks.get(library);if(t==null||t.active==null)continue;
            if(t.active.rows.stream().anyMatch(r->r.hasUUID("entry")&&r.getUUID("entry").equals(entry)))return true;
        }return false;
    }
    public static boolean reservedForOtherOperation(UUID library,ItemStack pattern,UUID operation){
        for(var tasks:TASKS.values()){
            Task t=tasks.get(library);if(t==null||t.active==null||t.failed)continue;
            for(CompoundTag row:t.active.rows)if(!row.getString("status").equals("ABORTED")&&ItemStack.isSameItemSameTags(pattern,ItemStack.of(row.getCompound("expected"))))
                return operation==null||!operation.equals(row.getUUID("operation"));
        }return false;
    }
    static void lock(Task t,Batch b){LOCKS.put(b.source.owner,t);}
    static void unlock(Batch b){if(b!=null&&b.source!=null){LOCKS.remove(b.source.owner);b.source.changed(b.rows);}}
    private static Task task(CloudPatternLibraryMachine main){
        Task t=TASKS.computeIfAbsent(main.serverLevel().getServer(),s->new HashMap<>()).computeIfAbsent(main.libraryId(),id->new Task(main));
        if(!t.running&&!t.undoRunning&&t.active==null)t.main=main;return t;
    }
    public static void start(ServerPlayer player,CloudPatternLibraryMachine main){authorize(player,main);Task t=task(main);if(t.running||t.undoRunning||t.active!=null)return;t.actor=player.getUUID();t.requestStart=true;}
    public static void undo(ServerPlayer player,CloudPatternLibraryMachine main){authorize(player,main);Task t=task(main);t.undoActor=player.getUUID();if(!t.running)t.actor=player.getUUID();t.requestUndo=true;t.requestStart=false;if(t.running){t.stop=true;t.stopReason="cancelled";}}
    public static boolean running(CloudPatternLibraryMachine main){return task(main).running;}
    public static int moved(CloudPatternLibraryMachine main){return task(main).moved;}
    public static int skipped(CloudPatternLibraryMachine main){return task(main).skipped;}
    /** Lifecycle callbacks also run on the client; only the owning server may stop its import task. */
    public static void forget(CloudPatternLibraryMachine main){
        if(!(main.getLevel() instanceof ServerLevel level)||!level.getServer().isSameThread()||main.libraryId()==null)return;
        var tasks=TASKS.get(level.getServer());Task t=tasks==null?null:tasks.get(main.libraryId());
        if(t!=null&&t.main==main){t.stop=true;t.stopReason="disconnected";}
    }
    public static void snapshot(CloudPatternLibraryMachine main,CompoundTag tag){
        if(main.libraryId()==null)return;Task t=task(main);tag.putBoolean("networkImportRunning",t.running||t.requestStart);tag.putBoolean("networkImportFinished",t.finished);
        tag.putInt("networkImportMoved",t.moved);tag.putInt("networkImportSkipped",t.skipped);tag.putString("networkImportStopReason",t.stopReason);
        CompoundTag reasons=new CompoundTag();t.reasons.forEach(reasons::putInt);tag.put("networkImportReasons",reasons);
        tag.putBoolean("networkUndoAvailable",t.batches.stream().anyMatch(b->!b.returned()&&b.rows.stream().anyMatch(r->r.getString("status").equals("COMMITTED"))));
        tag.putBoolean("networkUndoRunning",t.undoRunning||t.requestUndo);tag.putBoolean("networkUndoFinished",t.undoFinished);
        tag.putInt("networkUndoReturned",t.returned);tag.putInt("networkUndoPending",t.pending);
    }
    public static void tickAll(MinecraftServer server){var tasks=TASKS.get(server);if(tasks!=null)for(Task t:List.copyOf(tasks.values()))tick(t);}
    public static void stopped(MinecraftServer server){var tasks=TASKS.remove(server);if(tasks!=null)tasks.values().forEach(t->{if(t.active!=null&&t.active.source!=null)LOCKS.remove(t.active.source.owner);});}
    public static void tick(CloudPatternLibraryMachine main){var tasks=TASKS.get(main.serverLevel().getServer());Task t=tasks==null?null:tasks.get(main.libraryId());if(t!=null)tick(t);}
    private static void tick(Task t){
        if(t.failed||t.lastTick==t.server.getTickCount())return;t.lastTick=t.server.getTickCount();
        try{
            if(t.loading!=null){if(!t.loading.isDone())return;UUID actor=t.actor;boolean requested=t.requestStart||t.requestUndo;t.load(t.loading.join());t.loading=null;refreshHistory(t);if(requested)t.actor=actor;}
            if(t.io!=null){if(!t.io.isDone())return;t.io.join();t.io=null;}
            if(t.active!=null){advance(t);return;}
            ServerPlayer player=t.player();
            if(t.requestUndo&&!t.running){authorize(player,t.main);CloudNetworkPatternUndo.begin(t);}
            if(t.requestStart){authorize(player,t.main);begin(t);}
            if(t.undoRunning){CloudNetworkPatternUndo.next(t,player);return;}
            if(!t.running)return;
            if(!permitted(t,player)){t.stop=true;t.stopReason="disconnected";}
            if(t.stop){finish(t);return;}
            long deadline=System.nanoTime()+3_000_000;
            for(int step=0;step<32&&System.nanoTime()<deadline;step++){
                if(t.planning!=null){plan(t,player);if(t.active!=null)return;continue;}
                if(t.nodeCursor<t.nodes.size()){
                    Source s=discover(t.nodes.get(t.nodeCursor++));if(s==null||!t.seen.add(s.buffer!=null?s.buffer.getPatternInventory():s.inventory))continue;
                    beginSource(t,player,s);continue;
                }
                if(!t.storageStarted){t.storageStarted=true;t.stored=t.grid.getStorageService().getInventory().getAvailableStacks().iterator();}
                if(!t.stored.hasNext()){finish(t);return;}
                var row=t.stored.next();if(!(row.getKey() instanceof AEItemKey key)||row.getLongValue()<1)continue;
                ItemStack pattern=key.toStack();String reason=patternReason(t,pattern);if(reason.equals("ignore"))continue;
                if(!reason.isEmpty()){t.skip(reason,(int)Math.min(Integer.MAX_VALUE,row.getLongValue()));continue;}
                Source s=new Source();s.owner=t.main;s.holder=t.main.getHolder().getSelf();s.storage=true;s.storageGrid=t.grid;s.identity=t.library;s.facet="storage";
                Batch b=new Batch(UUID.randomUUID());b.source=s;CompoundTag r=record(t,b,s,0,pattern,new CompoundTag());r.putLong("visibleCount",row.getLongValue());b.rows.add(r);prepare(t,b);return;
            }
        }catch(RuntimeException failure){
            t.failed=true;t.running=false;t.undoRunning=false;t.requestStart=false;t.requestUndo=false;t.finished=true;t.stopReason="review";
            if(t.active!=null&&t.active.source!=null&&t.active.source.buffer instanceof CloudImportSourceAccess source)source.cloud$quarantineImport("Cloud batch requires review: "+t.session);
            if(t.loading==null&&t.io==null){try{persist(t);}catch(RuntimeException suppressed){failure.addSuppressed(suppressed);}}
            if(t.active!=null&&t.active.source!=null)LOCKS.remove(t.active.source.owner);
            org.slf4j.LoggerFactory.getLogger(CloudNetworkPatternImport.class).warn("Pattern batch stopped; ownership evidence retained",failure);
        }
    }
    public static void sourceReconciled(ServerLevel level){
        var tasks=TASKS.get(level.getServer());if(tasks==null)return;
        for(Task t:tasks.values())if(!t.running&&!t.undoRunning&&t.active==null&&t.loading==null&&t.io==null&&refreshHistory(t))persist(t);
    }
    private static boolean refreshHistory(Task t){
        boolean changed=false;var library=CloudPatternStore.get(t.main.serverLevel()).library(t.library);
        for(Batch batch:t.batches){
            boolean committed=true,returned=true;
            for(CompoundTag row:batch.rows){
                if(row.getString("status").equals("ABORTED"))continue;
                var receipt=library.receipt(row.getUUID("operation"));
                if(receipt!=null&&receipt.kind().equals("UPLOAD")&&receipt.status().equals("ACKNOWLEDGED")){
                    var entry=library.entry(receipt.subject());
                    if(!ItemStack.matches(entry.original(),ItemStack.of(row.getCompound("expected")))||!entry.configuration().equals(row.getCompound("configuration")))throw new IllegalStateException("Import history payload differs from authority");
                    if(!row.getString("status").equals("COMMITTED")){row.putString("status","COMMITTED");row.putUUID("entry",entry.id());changed=true;}
                }else committed=false;
                if(row.hasUUID("returnOperation")){
                    var export=library.receipt(row.getUUID("returnOperation"));
                    if(export!=null&&export.kind().equals("EXPORT")&&export.subject().equals(row.getUUID("entry"))&&export.status().equals("ACKNOWLEDGED")&&row.getString("returnStatus").equals("RETURNED"))continue;
                }
                returned=false;
            }
            String phase=returned?"RETURNED":committed&&!batch.phase.startsWith("RETURN_")?"COMMITTED":batch.phase;
            if(!phase.equals(batch.phase)){batch.phase=phase;changed=true;}
        }
        if(changed)t.moved=(int)t.batches.stream().flatMap(b->b.rows.stream()).filter(r->r.getString("status").equals("COMMITTED")).count();
        return changed;
    }
    private static void begin(Task t){
        refreshHistory(t);
        if(t.batches.stream().anyMatch(b->!Set.of("COMMITTED","ABORTED","RETURNED").contains(b.phase))){t.requestStart=false;t.finished=true;t.stopReason="review";return;}
        t.requestStart=false;t.session=UUID.randomUUID();t.grid=t.main.serviceGrid();t.nodes=index(t.grid);t.nodeCursor=0;
        t.newSession=true;t.seen.clear();t.reasons.clear();t.moved=0;t.skipped=0;t.returned=0;t.pending=0;t.undoFinished=false;
        t.running=true;t.finished=false;t.stop=false;t.stopReason="";t.storageStarted=false;t.stored=null;t.planning=null;
    }
    static List<IGridNode> index(IGrid grid){
        var seen=Collections.newSetFromMap(new IdentityHashMap<IGridNode,Boolean>());List<IGridNode> result=new ArrayList<>();
        // The class index used by AE2/ExtendedAE PatternAccessTermMenu; no scan of unrelated cable/storage nodes.
        for(Class<?> type:grid.getMachineClasses())if(PatternContainer.class.isAssignableFrom(type)||IPatternTerminalLogicHost.class.isAssignableFrom(type)||MEPatternBufferPartMachine.class.isAssignableFrom(type))
            for(IGridNode node:grid.getMachineNodes(type))if(seen.add(node))result.add(node);
        return result;
    }
    static Source discover(IGridNode node){
        Object owner=node.getOwner();if(owner==null||owner instanceof CloudPatternBufferMachine||owner instanceof CloudPatternLibraryMachine)return null;
        Source s=new Source();s.owner=owner;s.node=node;
        if(owner instanceof MEPatternBufferPartMachine buffer){s.buffer=buffer;s.holder=buffer.getHolder().getSelf();}
        else{
            if(owner instanceof IPatternTerminalLogicHost terminal)s.inventory=terminal.getLogic().getEncodedPatternInv();
            else if(owner instanceof PatternContainer container)s.inventory=container.getTerminalPatternInventory();
            if(owner instanceof AEBasePart part)s.holder=part.getBlockEntity();
            else if(owner instanceof BlockEntity be)s.holder=be;
            else if(owner instanceof MetaMachine machine)s.holder=machine.getHolder().getSelf();
        }
        if(s.holder==null||s.buffer==null&&s.inventory==null)return null;
        s.facet=owner.getClass().getName()+(owner instanceof AEBasePart part?"/"+part.getSide():"");return s;
    }
    private static void beginSource(Task t,ServerPlayer player,Source s){
        Batch b=new Batch(UUID.randomUUID());b.source=s;t.planning=b;t.planSlot=0;t.planningRejected=false;t.sourceReason=sourceReason(t,player,s);
        if(t.sourceReason.isEmpty()){s.identity=identity(s);if(s.buffer!=null)b.shared=CloudImportedCatalysts.captureShared(s.buffer);}
    }
    private static String patternReason(Task t,ItemStack pattern){
        if(pattern.isEmpty())return "ignore";
        var details=PatternDetailsHelper.decodePattern(pattern.copyWithCount(1),t.main.getLevel());if(details==null)return "ignore";
        if(details instanceof appeng.crafting.pattern.AECraftingPattern)return "crafting";
        return CloudPatternStore.get(t.main.serverLevel()).library(t.library).containsOriginal(pattern.copyWithCount(1))?"duplicate":"";
    }
    private static void plan(Task t,ServerPlayer player){
        Batch b=t.planning;Source s=b.source;
        if(t.planSlot<s.size()){
            int slot=t.planSlot++;ItemStack item=s.get(slot);String reason=patternReason(t,item);if(reason.equals("ignore")){if(!item.isEmpty())t.planningRejected=true;return;}
            CompoundTag original=item.copyWithCount(1).save(new CompoundTag());
            if(reason.isEmpty()&&!b.originals.add(original))reason="duplicate";
            if(!reason.isEmpty()||!t.sourceReason.isEmpty()){t.skip(!reason.isEmpty()?reason:t.sourceReason,item.getCount());t.planningRejected=true;return;}
            CompoundTag config=s.buffer==null?new CompoundTag():CloudSlotConfiguration.capture(s.buffer,slot);
            if(s.buffer!=null&&CloudImportedCatalysts.hasContents(b.shared))config.putUUID("importCatalystGroup",b.id);
            CompoundTag row=record(t,b,s,slot,item,config);if(item.getCount()>1){t.planningRejected=true;row.putInt("duplicatesCounted",item.getCount()-1);t.skip("duplicate",item.getCount()-1);}
            if(s.buffer!=null)row.put("catalysts",CloudImportedCatalysts.capturePattern(s.buffer,slot));b.rows.add(row);return;
        }
        t.planning=null;if(b.rows.isEmpty())return;
        if(s.buffer!=null&&CloudImportedCatalysts.hasContents(b.shared)){
            if(t.planningRejected){t.skip("shared_dependency",b.rows.size());return;}
            b.rows.get(0).put("sharedCatalysts",b.shared.copy());b.rows.get(0).putUUID("sharedGroup",b.id);
        }
        if(!sourceReason(t,player,s).isEmpty()){t.skip("changed",b.rows.size());return;}
        prepare(t,b);
    }
    private static CompoundTag record(Task t,Batch b,Source s,int slot,ItemStack before,CompoundTag config){
        CompoundTag r=new CompoundTag();r.putInt("schema",1);r.putUUID("operation",UUID.randomUUID());r.putUUID("library",t.library);r.putUUID("actor",t.actor);
        r.putString("kind",s.storage?"STORAGE":s.buffer!=null?"GTL":"INVENTORY");r.putString("dimension",s.holder.getLevel().dimension().location().toString());
        r.putLong("position",s.holder.getBlockPos().asLong());r.putUUID("identity",s.identity);r.putString("facet",s.facet);
        r.putString("holderType",String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(s.holder.getType())));r.putInt("slot",slot);
        r.put("before",before.save(new CompoundTag()));r.put("expected",before.copyWithCount(1).save(new CompoundTag()));
        ItemStack after=before.copy();after.shrink(1);r.put("after",after.save(new CompoundTag()));r.put("configuration",config);r.putString("status","PREPARED");return r;
    }
    private static UUID identity(Source s){
        if(s.buffer instanceof CloudImportSourceAccess access)return access.cloud$sourceIdentity();
        CompoundTag data=s.holder.getPersistentData();if(!data.hasUUID(IDENTITY)){data.putUUID(IDENTITY,UUID.randomUUID());s.holder.setChanged();}return data.getUUID(IDENTITY);
    }
    private static void prepare(Task t,Batch b){if(t.newSession){t.batches.clear();t.newSession=false;}if(b.source.buffer instanceof CloudImportSourceAccess access)CloudImportInventoryGuard.register(b.source.buffer.getPatternInventory(),access);t.active=b;t.batches.add(b);lock(t,b);b.phase="PREPARED";persist(t);}
    private static void advance(Task t){
        Batch b=t.active;Source s=b.source;ServerPlayer player=t.player();var store=CloudPatternStore.get(t.main.serverLevel());var library=store.library(t.library);
        switch(b.phase){
            case "PREPARED" -> {
                if(b.cursor==0&&(t.stop||!permitted(t,player)||!sourceReason(t,player,s).isEmpty())){abort(t,b,"changed");return;}
                if(!permitted(t,player)||!sourceReason(t,player,s).isEmpty())throw new IllegalStateException("Source disconnected during debit; exact escrow retained");
                if(b.cursor==0&&s.buffer!=null&&CloudImportedCatalysts.hasContents(b.shared)){
                    try{b.shared=CloudImportedCatalysts.drainShared(s.buffer,b.shared);s.mark(b.id);}
                    catch(CloudImportedCatalysts.TransferFailure failure){b.partialShared=failure.escrow();throw failure;}
                }
                int end=Math.min(b.rows.size(),b.cursor+16);
                for(;b.cursor<end;b.cursor++){
                    CompoundTag r=b.rows.get(b.cursor);ItemStack expected=ItemStack.of(r.getCompound("expected"));
                    if(library.containsOriginal(expected))throw new IllegalStateException("Original was uploaded concurrently after source preflight");
                    if(!s.storage&&!ItemStack.matches(s.get(r.getInt("slot")),ItemStack.of(r.getCompound("before"))))throw new IllegalStateException("Source changed after durable intent");
                    ItemStack actual;
                    if(s.storage){long n=t.grid.getStorageService().getInventory().extract(AEItemKey.of(expected),1,Actionable.MODULATE,IActionSource.ofPlayer(player));if(n==0){r.putString("status","ABORTED");t.skip("unextractable",1);continue;}if(n!=1)throw new IllegalStateException("Invalid ME extraction amount");actual=expected.copy();}
                    else actual=s.extract(r.getInt("slot"));
                    r.put("escrow",actual.save(new CompoundTag()));if(!ItemStack.matches(actual,expected))throw new IllegalStateException("Unexpected actual pattern debit");
                    if(s.buffer!=null){
                        try{r.put("catalysts",CloudImportedCatalysts.drainPattern(s.buffer,r.getInt("slot"),r.getCompound("catalysts")));}
                        catch(CloudImportedCatalysts.TransferFailure failure){r.put("catalystEscrow",failure.escrow());throw failure;}
                    }
                    s.mark(r.getUUID("operation"));r.putString("status","DEBITED");
                }
                if(b.cursor<b.rows.size())return;
                s.changed(b.rows);
                if(s.buffer!=null)for(CompoundTag row:b.rows)row.put("sourceConfiguration",CloudSlotConfiguration.capture(s.buffer,row.getInt("slot")));
                b.cursor=0;b.phase="DEBITED";persist(t);
            }
            case "DEBITED" -> {
                library.beginImportBatch(t.actor);
                try{
                    if(s.buffer!=null&&CloudImportedCatalysts.hasContents(b.shared))library.setImportedCatalysts(t.actor,b.id,b.shared);
                    for(CompoundTag r:b.rows)if(r.getString("status").equals("DEBITED")){
                        var entry=store.upload(t.library,t.actor,ItemStack.of(r.getCompound("escrow")),r.getCompound("configuration"),r.getUUID("operation"));
                        CloudLibraryService.identify(entry,t.actor);if(s.buffer!=null)entry.initializeImportedCatalysts(t.actor,r.getUUID("operation"),r.getCompound("catalysts"));r.putUUID("entry",entry.id());
                    }
                }finally{library.endImportBatch();}
                b.phase="UPLOADED";t.io=store.checkpointAsync();
            }
            case "UPLOADED" -> {
                library.beginImportBatch(t.actor);try{for(CompoundTag r:b.rows)if(r.hasUUID("entry"))store.acknowledge(t.library,t.actor,r.getUUID("operation"));}finally{library.endImportBatch();}
                b.phase="ACKNOWLEDGED";t.io=store.checkpointAsync();
            }
            case "ACKNOWLEDGED" -> {
                for(CompoundTag r:b.rows)if(r.hasUUID("entry")){r.putString("status","COMMITTED");r.remove("escrow");t.moved=add(t.moved,1);long left=s.storage?r.getLong("visibleCount")-1:ItemStack.of(r.getCompound("before")).getCount()-1-r.getInt("duplicatesCounted");if(left>0)t.skip("duplicate",(int)Math.min(Integer.MAX_VALUE,left));}
                b.phase="COMMITTED";persist(t);
            }
            case "COMMITTED","ABORTED" -> {unlock(b);t.active=null;if(t.stop)finish(t);}
            case "RETURN_PREPARED","RETURN_EXPORT_CHECKPOINT","RETURN_EXPORTED","RETURN_DELIVERED","RETURN_ACKNOWLEDGED","RETURN_ROLLBACK","RETURNED" -> CloudNetworkPatternUndo.advance(t,b,player);
            default -> throw new IllegalStateException("Unknown import phase "+b.phase);
        }
    }
    private static void abort(Task t,Batch b,String reason){
        if(b.cursor!=0||b.rows.stream().anyMatch(r->r.getString("status").equals("DEBITED")))throw new IllegalStateException("A partially debited batch cannot be discarded");
        b.rows.forEach(r->r.putString("status","ABORTED"));t.skip(reason,b.rows.size());b.phase="ABORTED";persist(t);
    }
    static String sourceReason(Task t,ServerPlayer player,Source s){
        if(player==null)return "permission";if(s.storage)return t.main.serviceGrid()==t.grid?"":"disconnected";
        if(s.node==null||s.node.getGrid()!=t.grid||!s.node.isActive())return "disconnected";
        if(s.holder.isRemoved()||s.holder.getLevel()==null)return "not_loaded";
        if(!allowed(player,s.node,s.holder,t.grid))return "permission";
        if(s.buffer!=null)return s.access(()->{var result=CloudBufferImport.inspectNetworkWithCatalysts(player,s.buffer,(p,b)->allowed(p,s.node,s.holder,t.grid));return result.allowed()?"":result.reason().name().toLowerCase(Locale.ROOT);});
        if(s.owner instanceof IPatternTerminalLogicHost)return "";
        if(s.owner instanceof PatternProviderLogicHost provider)return provider.getLogic().isBusy()?"busy":"";
        try{return Boolean.FALSE.equals(s.owner.getClass().getMethod("isBusy").invoke(s.owner))?"":"busy";}catch(ReflectiveOperationException|LinkageError unavailable){return "unsupported";}
    }
    static boolean allowed(ServerPlayer player,IGridNode node,BlockEntity be,IGrid grid){
        if(node==null||node.getGrid()!=grid||!node.isActive()||!(be.getLevel() instanceof ServerLevel level)||be.isRemoved())return false;
        var pos=be.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        return chunk!=null&&chunk.getBlockEntity(pos)==be&&level.mayInteract(player,pos)&&CloudLibraryService.claimAllows(player,level,pos);
    }
    private static void authorize(ServerPlayer player,CloudPatternLibraryMachine main){if(player==null||!main.menuAuthorized(player)||!main.networkAvailable(main.serviceGrid())||!CloudLibraryService.authorized(player,main.libraryId(),main))throw new SecurityException("ME network unavailable or library denied");}
    static boolean permitted(Task t,ServerPlayer player){return player!=null&&t.main.serviceGrid()==t.grid&&t.main.networkAvailable(t.grid)&&CloudLibraryService.authorized(player,t.library,t.main);}
    static void finish(Task t){t.running=false;t.finished=true;t.planning=null;persist(t);}
    public static boolean reconcileReturn(MetaMachine source,CompoundTag row){return CloudNetworkPatternUndo.reconcileReturn(source,row);}
}
