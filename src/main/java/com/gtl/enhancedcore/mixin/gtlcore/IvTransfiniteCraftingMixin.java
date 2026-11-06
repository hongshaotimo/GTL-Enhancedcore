package com.gtl.enhancedcore.mixin.gtlcore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.config.Actionable;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import com.gtl.enhancedcore.common.recipe.iv.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import net.minecraft.nbt.CompoundTag;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternAutoExpand;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingLogic;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingCPU;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Preserve private IV dispatch and durable final-output ownership on the upstream CPU. */
@Mixin(value=TransfiniteCraftingLogic.class,remap=false)
public abstract class IvTransfiniteCraftingMixin {
    @Unique private boolean iv$requiresProducedOutput;
    @Shadow @Final private TransfiniteCraftingCPU cpu;
    @Shadow @Final private ListCraftingInventory inventory;
    @Inject(method="trySubmitJob",at=@At("RETURN"))
    private void iv$submitted(IGrid grid,ICraftingPlan plan,IActionSource source,ICraftingRequester requester,CallbackInfoReturnable<ICraftingSubmitResult> cir){
        if(cir.getReturnValue().successful()){
            iv$requiresProducedOutput=plan.patternTimes().keySet().stream().anyMatch(IvTransfiniteCraftingMixin::iv$routed);
            if(iv$requiresProducedOutput)IvTaskLog.event(null,null,"CPU_SUBMIT","AWAIT_REAL_OUTPUT","cpu",cpu.getId(),"output",String.valueOf(plan.finalOutput()),"patterns",plan.patternTimes().size());
        }
    }
    @Unique private static boolean iv$routed(IPatternDetails pattern){
        var tag=pattern.getDefinition().getTag();return tag!=null && tag.contains(IvBuffers.ROUTE_KEY);
    }
    /** A standalone link returns zero even when the original logic credits the full amount.
     * Retain those products in the CPU's existing persistent inventory until the order finishes.
     * Returning the owned amount also keeps the host's waiting index valid between batches.
     * Any requester overflow is owned by the same durable CPU inventory, never re-credited. */
    @Redirect(method="insert",at=@At(value="INVOKE",target="Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J",ordinal=0))
    private long iv$receiveFinal(ListCraftingInventory waiting,AEKey key,long amount,Actionable simulate,
                                AEKey offeredKey,long offered,Actionable mode){
        long accepted=waiting.extract(key,amount,simulate);
        var logic=(TransfiniteCraftingLogic)(Object)this;
        if(!iv$requiresProducedOutput || accepted<=0 || !key.matches(logic.getFinalJobOutput()))return accepted;
        var link=(CraftingLink)logic.getLastLink();
        long delivered=link.isStandalone()?0:link.insert(key,accepted,mode);
        if(delivered<0 || delivered>accepted)throw new IllegalStateException("Invalid crafting requester acceptance");
        if(mode==Actionable.MODULATE){
            if(delivered<accepted)inventory.insert(key,accepted-delivered,mode);
            IvTaskLog.event(null,null,"CPU_RECEIVE","FINAL_OUTPUT","cpu",cpu.getId(),"key",key.toTagGeneric().toString(),"offered",offered,"accepted",accepted,"requesterAccepted",delivered,"cpuRetained",accepted-delivered,"standalone",link.isStandalone());
        }
        return accepted;
    }
    @Redirect(method="insert",at=@At(value="INVOKE",target="Lappeng/crafting/CraftingLink;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
    private long iv$alreadyReceived(CraftingLink link,AEKey key,long amount,Actionable mode){
        return iv$requiresProducedOutput ? amount : link.insert(key,amount,mode);
    }
    @Inject(method="finishJob",at=@At("HEAD"))
    private void iv$finished(boolean success,CallbackInfo ci){
        if(iv$requiresProducedOutput)IvTaskLog.event(null,null,"CPU_FINISH",success?"COMPLETE":"CANCELLED","cpu",cpu.getId());
    }
    @Inject(method="writeToNbt",at=@At("TAIL"))
    private void iv$save(CompoundTag tag,CallbackInfo ci){if(iv$requiresProducedOutput)tag.putBoolean("gtlEnhancedcoreIvAwaitProduction",true);}
    @Inject(method="readFromNbt",at=@At("TAIL"))
    private void iv$load(CompoundTag tag,CallbackInfo ci){
        iv$requiresProducedOutput=tag.getBoolean("gtlEnhancedcoreIvAwaitProduction");
        // Recover the scope from old persisted plans that have not yet been dispatched.
        var tasks=tag.getCompound("job").getList("tasks",10);
        for(int i=0;i<tasks.size();i++){
            var key=AEItemKey.fromTag(tasks.getCompound(i));
            if(key!=null && key.getTag()!=null && key.getTag().contains(IvBuffers.ROUTE_KEY))iv$requiresProducedOutput=true;
        }
    }
    @Redirect(method="executeCrafting",at=@At(value="INVOKE",target="Lorg/gtlcore/gtlcore/integration/ae2/crafting/CraftingPatternAutoExpand;getOperations(ZLappeng/api/networking/crafting/ICraftingProvider;Lappeng/api/crafting/IPatternDetails;J)J"))
    private long iv$remember(boolean processing,ICraftingProvider provider,IPatternDetails pattern,long requested){
        IvDispatchContext.clear();long operations=CraftingPatternAutoExpand.getOperations(processing,provider,pattern,requested);
        if(provider instanceof MEPatternBufferPartMachine buffer && IvBuffers.isolated(buffer)){
            iv$requiresProducedOutput=true;IvDispatchContext.set(provider,pattern,operations);
        }
        return operations;
    }
    @Redirect(method="executeCrafting",at=@At(value="INVOKE",target="Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean iv$dispatch(ICraftingProvider provider,IPatternDetails pattern,KeyCounter[] input){
        try{
            boolean pushed=provider.pushPattern(pattern,input);
            if(provider instanceof MEPatternBufferPartMachine buffer && IvBuffers.isolated(buffer))
                IvTaskLog.event(buffer,null,"CPU_DISPATCH",pushed?"ACCEPTED":"REJECTED","cpu",cpu.getId(),"operations",IvDispatchContext.operations(provider,pattern));
            return pushed;
        }finally{IvDispatchContext.clear();}
    }
}
