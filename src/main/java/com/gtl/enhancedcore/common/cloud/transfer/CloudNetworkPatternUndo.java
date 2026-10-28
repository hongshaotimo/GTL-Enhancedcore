package com.gtl.enhancedcore.common.cloud.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import static com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkImportState.*;

/** Return only originals whose current ownership, destination and catalyst state are proved. */
final class CloudNetworkPatternUndo {
    private CloudNetworkPatternUndo() {}
    static void begin(Task t){
        if(t.undoActor!=null)t.actor=t.undoActor;
        t.grid=t.main.serviceGrid();t.requestUndo=false;t.undoRunning=true;t.undoFinished=false;t.undoCursor=0;t.returned=0;
        t.pending=(int)t.batches.stream().flatMap(b->b.rows.stream()).filter(r->r.getString("status").equals("COMMITTED")&&!r.getString("returnStatus").equals("RETURNED")).count();
    }
    private static Source resolve(Task t,Batch b){
        CompoundTag row=b.rows.get(0);
        if(row.getString("kind").equals("STORAGE"))return b.source!=null&&b.source.storage&&b.source.storageGrid==t.grid?b.source:null;
        for(IGridNode node:CloudNetworkPatternImport.index(t.grid)){
            Source s=CloudNetworkPatternImport.discover(node);if(s==null)continue;
            if(s.holder.getBlockPos().asLong()!=row.getLong("position")||!s.holder.getLevel().dimension().location().toString().equals(row.getString("dimension"))||!s.facet.equals(row.getString("facet")))continue;
            if(!String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(s.holder.getType())).equals(row.getString("holderType")))return null;
            UUID id=s.buffer instanceof CloudImportSourceAccess access?access.cloud$sourceIdentity():s.holder.getPersistentData().hasUUID(IDENTITY)?s.holder.getPersistentData().getUUID(IDENTITY):null;
            if(!row.getUUID("identity").equals(id))return null;s.identity=id;return s;
        }return null;
    }
    private static boolean idleEntry(Task t,CompoundTag row){
        var store=CloudPatternStore.get(t.main.serverLevel());var entry=store.library(t.library).entry(row.getUUID("entry"));
        if(entry.status()!=CloudLedger.Status.AVAILABLE&&entry.status()!=CloudLedger.Status.LEASED&&entry.status()!=CloudLedger.Status.ARCHIVED)return false;
        if(!entry.catalysts().equals(row.getCompound("catalysts")))return false;
        if(entry.lease()==null)return true;
        var node=store.node(entry.lease().nodeId());var level=t.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,node.dimension()));
        BlockPos pos=BlockPos.of(node.position());var chunk=level==null?null:level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||!(MetaMachine.getMachine(level,pos) instanceof CloudPatternBufferMachine child)||!node.id().equals(child.cloudNodeId()))return false;
        return child.iv$getState().jobs.stream().noneMatch(job->entry.id().equals(job.cloudPattern));
    }
    private static boolean canReturn(Task t,Batch b,ServerPlayer player){
        Source s=b.source;if(s==null||!CloudNetworkPatternImport.permitted(t,player)||!CloudNetworkPatternImport.sourceReason(t,player,s).isEmpty())return false;
        if(s.storage){
            for(CompoundTag r:b.rows)if(r.getString("status").equals("COMMITTED")&&t.grid.getStorageService().getInventory().insert(AEItemKey.of(ItemStack.of(r.getCompound("expected"))),1,Actionable.SIMULATE,IActionSource.ofPlayer(player))!=1)return false;
            return true;
        }
        for(CompoundTag row:b.rows){
            if(row.getString("status").equals("ABORTED")||row.getString("returnStatus").equals("RETURNED"))continue;
            int slot=row.getInt("slot");if(slot<0||slot>=s.size()||!ItemStack.matches(s.get(slot),ItemStack.of(row.getCompound("after"))))return false;
            if(!s.insert(slot,ItemStack.of(row.getCompound("expected")),true).isEmpty())return false;
            if(s.buffer!=null&&(!CloudSlotConfiguration.capture(s.buffer,slot).equals(row.getCompound("sourceConfiguration"))||!CloudImportedCatalysts.canRestorePattern(s.buffer,slot,row.getCompound("catalysts"))))return false;
        }
        return s.buffer==null||!CloudImportedCatalysts.hasContents(b.shared)||CloudImportedCatalysts.canRestoreShared(s.buffer,b.shared);
    }
    static void next(Task t,ServerPlayer player){
        if(!CloudNetworkPatternImport.permitted(t,player)){t.undoRunning=false;t.undoFinished=true;t.stopReason="disconnected";persist(t);return;}
        if(t.undoCursor>=t.batches.size()){t.undoRunning=false;t.undoFinished=true;persist(t);return;}
        Batch b=t.batches.get(t.undoCursor++);if(b.returned()||!b.phase.equals("COMMITTED"))return;
        b.source=resolve(t,b);if(b.source==null||b.rows.stream().filter(r->r.getString("status").equals("COMMITTED")).anyMatch(r->!idleEntry(t,r))||!canReturn(t,b,player))return;
        var library=CloudPatternStore.get(t.main.serverLevel()).library(t.library);
        if(b.source.buffer!=null&&CloudImportedCatalysts.hasContents(b.shared)&&!library.getImportedCatalysts(t.actor,b.id).equals(b.shared))return;
        for(CompoundTag r:b.rows)if(r.getString("status").equals("COMMITTED")){
            if(!r.hasUUID("returnOperation"))r.putUUID("returnOperation",UUID.randomUUID());r.putUUID("returnActor",t.actor);r.putString("returnStatus","PREPARED");
        }
        t.active=b;CloudNetworkPatternImport.lock(t,b);b.phase="RETURN_PREPARED";persist(t);
    }
    static void advance(Task t,Batch b,ServerPlayer player){
        Source s=b.source;var store=CloudPatternStore.get(t.main.serverLevel());var library=store.library(t.library);
        switch(b.phase){
            case "RETURN_PREPARED" -> {
                if(!canReturn(t,b,player)||b.rows.stream().filter(r->r.getString("status").equals("COMMITTED")).anyMatch(r->!idleEntry(t,r))){b.phase="COMMITTED";persist(t);CloudNetworkPatternImport.unlock(b);t.active=null;return;}
                library.beginImportBatch(t.actor);
                try{
                    for(CompoundTag r:b.rows)if(r.getString("status").equals("COMMITTED")){
                        ItemStack actual=store.extractFromCore(t.library,t.actor,r.getUUID("entry"),r.getUUID("returnOperation"));
                        if(!ItemStack.matches(actual,ItemStack.of(r.getCompound("expected"))))throw new IllegalStateException("Undo original differs from imported original");
                        r.put("returnEscrow",actual.save(new CompoundTag()));if(s.buffer!=null)library.entry(r.getUUID("entry")).setCatalysts(t.actor,new CompoundTag());
                    }
                    if(s.buffer!=null&&CloudImportedCatalysts.hasContents(b.shared))library.setImportedCatalysts(t.actor,b.id,new CompoundTag());
                }finally{library.endImportBatch();}
                b.phase="RETURN_EXPORT_CHECKPOINT";t.io=store.checkpointAsync();
            }
            case "RETURN_EXPORT_CHECKPOINT" -> {b.phase="RETURN_EXPORTED";persist(t);}
            case "RETURN_EXPORTED" -> {
                if(!canReturn(t,b,player)){rollback(t,b);return;}
                for(CompoundTag r:b.rows)if(r.getString("status").equals("COMMITTED")){
                    ItemStack actual=ItemStack.of(r.getCompound("returnEscrow"));
                    if(s.storage){long n=t.grid.getStorageService().getInventory().insert(AEItemKey.of(actual),1,Actionable.MODULATE,IActionSource.ofPlayer(player));if(n!=1)throw new IllegalStateException("ME storage cannot receive undo original");}
                    else{
                        if(!ItemStack.matches(s.get(r.getInt("slot")),ItemStack.of(r.getCompound("after"))))throw new IllegalStateException("Undo destination changed");
                        if(!s.insert(r.getInt("slot"),actual,false).isEmpty())throw new IllegalStateException("Undo destination refused original");r.putBoolean("originalReturned",true);
                        if(s.buffer!=null)try{CloudImportedCatalysts.restorePattern(s.buffer,r.getInt("slot"),r.getCompound("catalysts"));}catch(CloudImportedCatalysts.TransferFailure f){r.put("returnCatalystRemaining",f.remaining());throw f;}
                    }
                    s.mark(r.getUUID("returnOperation"));r.putString("returnStatus","RETURNED");r.remove("returnEscrow");
                }
                if(s.buffer!=null&&CloudImportedCatalysts.hasContents(b.shared))try{CloudImportedCatalysts.restoreShared(s.buffer,b.shared);}catch(CloudImportedCatalysts.TransferFailure f){b.partialShared=f.remaining();throw f;}
                s.changed(b.rows);b.phase="RETURN_DELIVERED";persist(t);
            }
            case "RETURN_DELIVERED" -> {
                for(CompoundTag r:b.rows)if(r.hasUUID("returnOperation")&&r.getString("returnStatus").equals("RETURNED"))store.acknowledge(t.library,t.actor,r.getUUID("returnOperation"));
                b.phase="RETURN_ACKNOWLEDGED";t.io=store.checkpointAsync();
            }
            case "RETURN_ACKNOWLEDGED" -> {int count=(int)b.rows.stream().filter(r->r.getString("returnStatus").equals("RETURNED")).count();t.returned=add(t.returned,count);t.pending=Math.max(0,t.pending-count);b.phase="RETURNED";persist(t);}
            case "RETURNED" -> {CloudNetworkPatternImport.unlock(b);t.active=null;}
            case "RETURN_ROLLBACK" -> {b.phase="COMMITTED";persist(t);CloudNetworkPatternImport.unlock(b);t.active=null;}
        }
    }
    private static void rollback(Task t,Batch b){
        var store=CloudPatternStore.get(t.main.serverLevel());var library=store.library(t.library);library.beginImportBatch(t.actor);
        try{
            for(CompoundTag r:b.rows)if(r.hasUUID("returnOperation")){
                store.cancelUndeliveredExport(t.library,t.actor,r.getUUID("returnOperation"));
                if(b.source.buffer!=null)library.entry(r.getUUID("entry")).setCatalysts(t.actor,r.getCompound("catalysts"));
                r.remove("returnOperation");r.remove("returnActor");r.remove("returnEscrow");r.remove("returnStatus");
            }
            if(b.source.buffer!=null&&CloudImportedCatalysts.hasContents(b.shared))library.setImportedCatalysts(t.actor,b.id,b.shared);
        }finally{library.endImportBatch();}
        b.phase="RETURN_ROLLBACK";t.io=store.checkpointAsync();
    }
    /** The old import recovery must never debit a returned physical original a second time. */
    static boolean reconcileReturn(MetaMachine source,CompoundTag row){
        if(!(source.getLevel() instanceof ServerLevel level)||!(source instanceof CloudImportSourceAccess access))return false;
        var store=CloudPatternStore.get(level);var library=store.library(row.getUUID("library"));var receipt=library.receipt(row.getUUID("returnOperation"));
        if(receipt==null||receipt.status().equals("ROLLED_BACK"))return false;
        if(!receipt.kind().equals("EXPORT")||!receipt.subject().equals(row.getUUID("entry")))throw new IllegalStateException("Undo receipt differs from source evidence");
        if(!access.cloud$processed(row.getUUID("returnOperation"))&&!row.getString("returnStatus").equals("RETURNED"))throw new IllegalStateException("Undo delivery requires review; original retained by return escrow");
        store.acknowledge(row.getUUID("library"),row.hasUUID("returnActor")?row.getUUID("returnActor"):row.getUUID("actor"),row.getUUID("returnOperation"));return true;
    }
}
