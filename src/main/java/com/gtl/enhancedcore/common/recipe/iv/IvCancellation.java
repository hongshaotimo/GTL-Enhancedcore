package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageHelper;
import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.Map;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;

/** Abort all selected work immediately; drain recoverable stock even without a formed controller. */
public final class IvCancellation {
    private record Return(IvJob job, Map<AEKey, Long> stock, AEKey key) {}
    private IvCancellation() {}
    public static void cancel(MEPatternBufferPartMachineBase buffer,int slot) {
        if(buffer.isRemote() || !IvBuffers.isolated(buffer))return;
        var state=IvBuffers.state(buffer);
        if(!state.healthy()){state.message="gtl_enhancedcore.gui.iv_cancel_corrupt";return;}
        state.accepting=false;
        for(var job:state.jobs)if(slot<0 || job.slot==slot){
            long aborted=job.parallel;
            job.cancel();
            IvTaskLog.event(buffer,job,"CANCEL","ALL_UNFINISHED_WORK_CANCELLED","cancelledOperations",job.cancelledOperations,
                    "abortedRunningOperations",aborted,"refunds",IvTaskLog.stock(job.refunds),"completedOutput",IvTaskLog.stock(job.pending));
        }
        state.message="gtl_enhancedcore.gui.iv_cancel_message";
        buffer.markDirty(); ICraftingProvider.requestUpdate(buffer.getMainNode());
        returnStock(buffer);
    }
    public static void resume(MEPatternBufferPartMachineBase buffer){
        setAccepting(buffer,true);
    }
    public static void setAccepting(MEPatternBufferPartMachineBase buffer,boolean accepting){
        if(buffer.isRemote() || !IvBuffers.isolated(buffer))return;
        var state=IvBuffers.state(buffer); if(!state.healthy())return;
        state.accepting=accepting;state.message="";buffer.markDirty();ICraftingProvider.requestUpdate(buffer.getMainNode());
        IvTaskLog.event(buffer,null,"ADMISSION",accepting?"RESUMED":"PAUSED");
    }
    public static void returnStock(MEPatternBufferPartMachineBase buffer){
        var state=IvBuffers.state(buffer);
        if(buffer.isRemote() || state==null || !state.dedicated() || !state.healthy())return;
        var grid=buffer.getGrid();
        boolean online=grid!=null && buffer.getMainNode().isActive();
        var returns=new ArrayList<Return>();
        if(online)for(var job:state.jobs){
            job.refunds.keySet().forEach(key->returns.add(new Return(job,job.refunds,key)));
            if(job.cancelled)job.pending.keySet().forEach(key->returns.add(new Return(job,job.pending,key)));
        }
        int attempts=Math.min(64,returns.size());
        int start=returns.isEmpty()?0:Math.floorMod(state.returnCursor,returns.size());
        for(int i=0;i<attempts;i++){
            var item=returns.get((start+i)%returns.size());
            long offered=item.stock.get(item.key);
            long inserted=StorageHelper.poweredInsert(grid.getEnergyService(),grid.getStorageService().getInventory(),item.key,offered,IActionSource.ofMachine(buffer));
            if(inserted<0 || inserted>offered)throw new IllegalStateException("Invalid ME refund amount");
            if(inserted==0)continue;
            if(inserted==offered)item.stock.remove(item.key);else item.stock.put(item.key,offered-inserted);
            buffer.markDirty();IvTaskLog.event(buffer,item.job,item.stock==item.job.refunds?"REFUND":"OUTPUT","OK",
                    "key",item.key.toTagGeneric().toString(),"offered",offered,"inserted",inserted,"left",offered-inserted);
        }
        state.returnCursor=returns.isEmpty()?0:(start+attempts)%returns.size();
        for(var job:state.jobs){
            // Completed output must also drain after controller removal or while it is switched off.
            if(job.cancelled && job.pending.isEmpty() && job.pendingOperations>0){
                job.deliveredOperations=Math.addExact(job.deliveredOperations,job.pendingOperations);
                job.pendingOperations=0;job.validateAccounting();buffer.markDirty();
            }
        }
        var jobs=state.jobs.iterator();
        while(jobs.hasNext()){
            var job=jobs.next();
            if(job.cancelled && job.done()){
                IvTaskLog.event(buffer,job,"RETIRE","CANCELLED_AND_DRAINED","cancelledOperations",job.cancelledOperations,"deliveredOperations",job.deliveredOperations);
                jobs.remove();buffer.markDirty();
            }
        }
    }
}
