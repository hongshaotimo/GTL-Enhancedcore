package com.gtl.enhancedcore.common.cloud.transfer;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.*;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferMethods;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Runtime handles never enter the WAL. IO receives detached tags and paths only. */
final class CloudNetworkImportState {
    static final String IDENTITY="gtl_cloud_batch_source_identity", MARKERS="gtl_cloud_batch_processed";
    private CloudNetworkImportState() {}
    static int add(int a,int b) {return (int)Math.min(Integer.MAX_VALUE,(long)a+b);}
    static final class Source {
        Object owner; IGridNode node; BlockEntity holder; InternalInventory inventory;
        MEPatternBufferPartMachine buffer; boolean storage; UUID identity; String facet; IGrid storageGrid;
        int size(){return storage?1:buffer!=null?buffer.getPatternInventory().getSlots():inventory.size();}
        <T> T access(java.util.function.Supplier<T> body){return buffer==null?body.get():CloudImportInventoryGuard.recover(buffer.getPatternInventory(),body);}
        ItemStack get(int slot){return access(()->buffer==null?inventory.getStackInSlot(slot).copy():buffer.getPatternInventory().getStackInSlot(slot).copy());}
        ItemStack extract(int slot){return access(()->buffer==null?inventory.extractItem(slot,1,false):buffer.getPatternInventory().extractItem(slot,1,false,true));}
        ItemStack insert(int slot,ItemStack stack,boolean simulate){return access(()->buffer==null?inventory.insertItem(slot,stack,simulate):buffer.getPatternInventory().insertItem(slot,stack,simulate,!simulate));}
        void mark(UUID operation){
            if(buffer instanceof CloudImportSourceAccess access)access.cloud$markProcessed(operation);
            else {CompoundTag marks=holder.getPersistentData().getCompound(MARKERS);marks.putBoolean(operation.toString(),true);holder.getPersistentData().put(MARKERS,marks);}
            holder.setChanged();
        }
        boolean processed(UUID operation){return buffer instanceof CloudImportSourceAccess access?access.cloud$processed(operation):holder.getPersistentData().getCompound(MARKERS).getBoolean(operation.toString());}
        void changed(List<CompoundTag> rows){
            holder.setChanged();
            if(buffer!=null){
                for(CompoundTag row:rows)((IvBufferMethods)buffer).iv$patternChanged(row.getInt("slot"));
                ((IvBufferMethods)buffer).iv$refreshPatterns();
                appeng.api.networking.crafting.ICraftingProvider.requestUpdate(buffer.getMainNode());buffer.markDirty();
            }else if(owner instanceof PatternProviderLogicHost host)host.getLogic().updatePatterns();
        }
    }
    static final class Batch {
        final UUID id;final List<CompoundTag> rows=new ArrayList<>();
        CompoundTag shared=new CompoundTag(),partialShared=new CompoundTag();Source source;int cursor;String phase="PREPARED";
        final Set<CompoundTag> originals=new HashSet<>();
        Batch(UUID id){this.id=id;}
        CompoundTag save(){
            CompoundTag tag=new CompoundTag();tag.putUUID("id",id);tag.putString("phase",phase);tag.put("shared",shared.copy());tag.put("partialShared",partialShared.copy());
            ListTag entries=new ListTag();rows.forEach(r->entries.add(r.copy()));tag.put("rows",entries);return tag;
        }
        static Batch load(CompoundTag tag){Batch b=new Batch(tag.getUUID("id"));b.phase=tag.getString("phase");b.shared=tag.getCompound("shared").copy();for(Tag r:tag.getList("rows",Tag.TAG_COMPOUND))b.rows.add((CompoundTag)r);return b;}
        boolean returned(){return rows.stream().allMatch(r->r.getString("returnStatus").equals("RETURNED")||r.getString("status").equals("ABORTED"));}
    }
    static final class Task {
        CloudPatternLibraryMachine main;final MinecraftServer server;final UUID library;
        UUID actor,undoActor,session=UUID.randomUUID();IGrid grid;List<IGridNode> nodes=List.of();int nodeCursor;
        final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Batch> batches=new ArrayList<>();final Map<String,Integer> reasons=new LinkedHashMap<>();
        CompletableFuture<?> io;CompletableFuture<CompoundTag> loading;
        Batch planning,active;int planSlot,undoCursor,returned,pending;String sourceReason="",stopReason="";
        boolean running,finished,stop,requestStart,requestUndo,undoRunning,undoFinished,storageStarted,planningRejected,failed,newSession;
        int moved,skipped,lastTick=-1;
        Iterator<it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<appeng.api.stacks.AEKey>> stored;
        Task(CloudPatternLibraryMachine main){this.main=main;server=main.serverLevel().getServer();library=main.libraryId();Path file=path(this);loading=CloudImportIo.submit(()->read(file));}
        ServerPlayer player(){return actor==null?null:server.getPlayerList().getPlayer(actor);}
        void skip(String reason,int count){if(count<=0)return;skipped=add(skipped,count);reasons.merge(reason,count,CloudNetworkImportState::add);}
        CompoundTag save(){
            CompoundTag tag=new CompoundTag();tag.putInt("schema",1);tag.putUUID("library",library);tag.putUUID("session",session);
            if(actor!=null)tag.putUUID("actor",actor);
            tag.putInt("moved",moved);tag.putInt("skipped",skipped);tag.putInt("returned",returned);tag.putInt("pending",pending);
            tag.putBoolean("undoFinished",undoFinished);tag.putString("stopReason",stopReason);
            CompoundTag rs=new CompoundTag();reasons.forEach(rs::putInt);tag.put("reasons",rs);
            ListTag list=new ListTag();batches.forEach(b->list.add(b.save()));tag.put("batches",list);return tag;
        }
        void load(CompoundTag tag){
            if(tag.isEmpty())return;
            if(tag.getInt("schema")!=1||!tag.hasUUID("library")||!tag.getUUID("library").equals(library))throw new IllegalStateException("Import history identity mismatch");
            session=tag.getUUID("session");actor=tag.hasUUID("actor")?tag.getUUID("actor"):null;
            moved=tag.getInt("moved");skipped=tag.getInt("skipped");returned=tag.getInt("returned");pending=tag.getInt("pending");
            undoFinished=tag.getBoolean("undoFinished");stopReason=tag.getString("stopReason");finished=true;
            CompoundTag rs=tag.getCompound("reasons");rs.getAllKeys().forEach(k->reasons.put(k,rs.getInt(k)));
            for(Tag b:tag.getList("batches",Tag.TAG_COMPOUND))batches.add(Batch.load((CompoundTag)b));
        }
    }
    static Path path(Task t){return t.server.getWorldPath(LevelResource.ROOT).resolve("data/gtl_cloud_import_sessions").resolve(t.library+".dat");}
    static void persist(Task t){
        CompoundTag snapshot=t.save();Path target=path(t);CompletableFuture<Void> journal=CompletableFuture.completedFuture(null);Batch b=t.active;
        if(b!=null&&b.source!=null&&b.source.buffer!=null)journal=CloudImportJournal.persistBatchAsync(t.main.serverLevel(),b.id,b.rows);
        t.io=journal.thenCompose(nothing->CloudImportIo.run(()->write(target,snapshot)));
    }
    static CompoundTag read(Path path){
        if(!Files.exists(path))return new CompoundTag();
        try {CompoundTag wrapper=NbtIo.readCompressed(path.toFile()),data=wrapper.getCompound("payload");if(!wrapper.getString("sha256").equals(CloudPatternStore.checksum(data)))throw new IllegalStateException("Import history checksum mismatch");
            // A source WAL can be one durable step ahead of the session pointer after shutdown.
            for(Tag raw:data.getList("batches",Tag.TAG_COMPOUND)){
                CompoundTag batch=(CompoundTag)raw;ListTag rows=batch.getList("rows",Tag.TAG_COMPOUND);
                if(rows.isEmpty()||Set.of("COMMITTED","ABORTED","RETURNED").contains(batch.getString("phase"))||!rows.getCompound(0).getString("kind").equals("GTL"))continue;
                CompoundTag first=rows.getCompound(0);String dimension=first.getString("dimension");
                Path source=path.getParent().getParent().resolve("gtl_cloud_import").resolve(UUID.nameUUIDFromBytes(dimension.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"_"+first.getLong("position")).resolve(batch.getUUID("id")+".dat");
                if(!Files.exists(source))continue;
                List<CompoundTag> durable=CloudImportJournal.records(NbtIo.readCompressed(source.toFile()));
                Map<UUID,CompoundTag> byId=new HashMap<>();durable.forEach(r->byId.put(r.getUUID("operation"),r));
                boolean aborted=true;
                for(int i=0;i<rows.size();i++){
                    CompoundTag old=rows.getCompound(i),next=byId.get(old.getUUID("operation"));
                    if(next==null||!next.getUUID("identity").equals(old.getUUID("identity"))||!next.getCompound("expected").equals(old.getCompound("expected")))throw new IllegalStateException("Source/session import evidence differs");
                    rows.set(i,next.copy());if(!next.getString("status").equals("ABORTED"))aborted=false;
                }
                if(aborted)batch.putString("phase","ABORTED");
            }
            return data;}
        catch(IOException failure){throw new IllegalStateException("Cannot read import history",failure);}
    }
    private static void write(Path target,CompoundTag data){
        Path temporary=target.resolveSibling(target.getFileName()+".pending");
        try {Files.createDirectories(target.getParent());CompoundTag wrapper=new CompoundTag();wrapper.put("payload",data);wrapper.putString("sha256",CloudPatternStore.checksum(data));
            NbtIo.writeCompressed(wrapper,temporary.toFile());try(FileChannel c=FileChannel.open(temporary,StandardOpenOption.WRITE)){c.force(true);}
            if(!NbtIo.readCompressed(temporary.toFile()).equals(wrapper))throw new IOException("Import history verification failed");
            if(Files.exists(target))Files.copy(target,target.resolveSibling(target.getFileName()+".previous"),StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException failure){throw new IllegalStateException("Import history could not be persisted",failure);}
    }
}
