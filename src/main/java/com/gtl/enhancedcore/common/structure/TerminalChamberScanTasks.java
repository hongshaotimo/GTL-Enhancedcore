package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.gtl.enhancedcore.mixin.gtlcore.TerminalDismantlePatternAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern;

/** Bounded read-only server jobs: no world matching, chunk loading, building or inventory access. */
@Mod.EventBusSubscriber(modid=GTLEnhancedcore.MOD_ID)
public final class TerminalChamberScanTasks {
    private static final Map<UUID,Job> JOBS=new LinkedHashMap<>();
    private TerminalChamberScanTasks(){}
    public static void enqueue(IMultiController controller,ServerPlayer player){
        if(JOBS.size()>=4&&!JOBS.containsKey(player.getUUID())||!controller.getPatternLock().tryLock()){
            player.displayClientMessage(Component.translatable("gtl_enhancedcore.chambers.busy"),true);return;
        }
        try{if(controller.getPattern()!=null){JOBS.put(player.getUUID(),new Job(controller,player));
            player.displayClientMessage(Component.translatable("gtl_enhancedcore.chambers.scanning"),true);}}
        finally{controller.getPatternLock().unlock();}
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){JOBS.clear();}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        long deadline=System.nanoTime()+2_000_000L;int budget=65536;
        var iterator=JOBS.values().iterator();
        while(iterator.hasNext()&&budget>0&&System.nanoTime()<deadline){
            var job=iterator.next();
            if(!job.valid()){iterator.remove();continue;}
            if(!job.controller.getPatternLock().tryLock())continue;
            try{while(budget-->0&&System.nanoTime()<deadline&&!job.step()){}
                if(job.finished){iterator.remove();if(!job.overflow)com.gtl.enhancedcore.network.ChamberPackets.send(
                    job.player,job.origin,job.positions,job.minimum);}}
            catch(RuntimeException failure){iterator.remove();GTLEnhancedcore.LOGGER.warn("Chamber scan failed at {}",job.origin,failure);
                job.player.displayClientMessage(Component.translatable("gtl_enhancedcore.chambers.failed"),false);}
            finally{job.controller.getPatternLock().unlock();}
        }
    }
    private static final class Job{
        final IMultiController controller;final ServerPlayer player;final BlockPos origin;
        final Direction facing,up;final boolean flipped,minimum;
        final com.gregtechceu.gtceu.api.pattern.BlockPattern pattern;
        final com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate[][][] matches;
        final BlockPatternAccessor base;final TerminalDismantlePatternAccessor advanced;
        final int[] center,repeats;final ChamberPositions classifier=new ChamberPositions();
        final ArrayList<BlockPos> positions=new ArrayList<>();
        final ChamberScanCursor cursor;boolean finished,overflow;long examined;
        Job(IMultiController controller,ServerPlayer player){
            this.controller=controller;this.player=player;pattern=controller.getPattern();origin=controller.self().getPos().immutable();
            facing=controller.self().getFrontFacing();up=controller.self().getUpwardsFacing();
            base=(BlockPatternAccessor)pattern;advanced=pattern instanceof AdvancedBlockPattern?(TerminalDismantlePatternAccessor)pattern:null;
            matches=advanced==null?base.gtlEnhancedcore$getBlockMatches():advanced.enhancedcore$matches();
            center=(advanced==null?base.enhanced$getCenterOffset():advanced.enhancedcore$center()).clone();
            var limits=advanced==null?pattern.aisleRepetitions:advanced.enhancedcore$repetitions();
            int[] matched=controller.isFormed()?controller.getMultiblockState().getMatchContext().get("gtlcore:matched_repetitions"):null;
            minimum=matched==null;repeats=new int[limits.length];
            for(int i=0;i<repeats.length;i++)repeats[i]=matched!=null&&i<matched.length?Math.max(limits[i][0],Math.min(limits[i][1],matched[i])):limits[i][0];
            int firstZ=minimum?-center[4]:controller.getMultiblockState().getMatchContext().getInt("gtlcore:matched_min_z");
            int[] widths=new int[matches.length],heights=new int[matches.length];
            for(int i=0;i<matches.length;i++){heights[i]=matches[i].length;widths[i]=matches[i][0].length;}
            cursor=new ChamberScanCursor(widths,heights,repeats,firstZ);
            flipped=controller.isFormed()&&controller.getMultiblockState().isNeededFlip();
        }
        boolean valid(){return !player.hasDisconnected()&&player.level()==controller.self().getLevel()
            &&player.level().hasChunkAt(origin)&&MetaMachine.getMachine(player.level(),origin)==controller.self()
            &&controller.getPattern()==pattern&&controller.self().getFrontFacing()==facing&&controller.self().getUpwardsFacing()==up;}
        boolean step(){
            var cell=cursor.next();if(cell==null){finished=true;return true;}
            if(++examined>45_000_000L)throw new IllegalArgumentException("oversized structure");
            if(classifier.isChamber(matches[cell.sourceAisle()][cell.y()][cell.x()])){
                if(positions.size()>=com.gtl.enhancedcore.network.ChamberPackets.MAX_POSITIONS){
                    player.displayClientMessage(Component.translatable("gtl_enhancedcore.chambers.too_many"),false);overflow=finished=true;return true;}
                var offset=advanced==null?base.enhanced$chamberOffset(cell.x()-center[0],cell.y()-center[1],cell.z(),facing,up,flipped)
                    :advanced.enhancedcore$offset(cell.x()-center[0],cell.y()-center[1],cell.z(),facing,up,flipped);
                positions.add(origin.offset(offset));
            }
            return false;
        }
    }
}
