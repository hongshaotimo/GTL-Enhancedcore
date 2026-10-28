package com.gtl.enhancedcore.mixin.extendedae.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal;
import com.glodblock.github.extendedae.container.ContainerExPatternTerminal;
import com.gtl.enhancedcore.common.cloud.CloudPatternAccessMenu;
import com.gtl.enhancedcore.client.cloud.CloudTerminalViewState;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Existing input/output fields filter the server catalogue before its complete provider projection. */
@Mixin(value=GuiExPatternTerminal.class,remap=false)
public abstract class CloudPatternAccessScreenMixin extends AEBaseScreen<ContainerExPatternTerminal> {
    @Shadow @Final private AETextField searchInField;
    @Shadow @Final private AETextField searchOutField;
    @Unique private String cloud$lastInput="",cloud$lastOutput="";
    @Unique private long cloud$searchDue;
    @Unique private long cloud$request;
    @Unique private String cloud$library="",cloud$scope;
    @Unique private boolean cloud$restored,cloud$serverMatched;
    @Unique private CloudTerminalViewState cloud$preferences;
    @Unique private final java.util.Set<Long> cloud$records=new java.util.HashSet<>();
    @Shadow @Final private java.util.HashMap<Long,appeng.client.gui.me.patternaccess.PatternContainerRecord> byId;
    @Shadow private boolean needsRefresh;
    @Shadow @Final private java.util.HashMap<Long,?> infoMap;
    @Shadow @Final private java.util.Map<String,java.util.Set<Object>> cachedSearches;
    protected CloudPatternAccessScreenMixin(ContainerExPatternTerminal menu,Inventory inventory,Component title,ScreenStyle style){super(menu,inventory,title,style);}
    @Inject(method="postFullUpdate",at=@At("HEAD"),cancellable=true)
    private void cloud$replaceProjection(long id,long order,appeng.api.implementations.blockentities.PatternContainerGroup group,int size,
            it.unimi.dsi.fastutil.ints.Int2ObjectMap<net.minecraft.world.item.ItemStack> stacks,CallbackInfo ci){
        if(size==0 && cloud$records.remove(id)){byId.remove(id);infoMap.remove(id);cachedSearches.clear();needsRefresh=true;ci.cancel();return;}
        if(id<0 && group.name().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title
                && title.getKey().equals("block.gtl_enhancedcore.cloud_pattern_library")) {
            if(cloud$searchDue!=0){ci.cancel();return;}
            Object[] args=title.getArgs();
            if(args.length!=4 || Long.parseLong(String.valueOf(args[3]))!=cloud$request){ci.cancel();return;}
            String library=String.valueOf(args[0]);
            if(cloud$restored) {
                if(!cloud$library.isEmpty() && !cloud$library.equals(library)) {
                    var saved=cloud$preferences.loadForLibrary(cloud$scope,library);
                    cloud$library=library;
                    searchInField.setValue(saved.input());searchOutField.setValue(saved.output());
                    cloud$lastInput=saved.input();cloud$lastOutput=saved.output();cloud$searchDue=System.nanoTime();
                    ci.cancel();return;
                }
                if(String.valueOf(args[1]).equals(searchInField.getValue().toLowerCase(java.util.Locale.ROOT))
                        && String.valueOf(args[2]).equals(searchOutField.getValue().toLowerCase(java.util.Locale.ROOT))) {
                    cloud$library=library;cloud$save();
                } else { ci.cancel();return; }
            }
            cloud$records.add(id);cachedSearches.clear();
        }
    }
    @Inject(method="m_7856_()V",at=@At("RETURN"),require=1)
    private void cloud$restoreSearch(CallbackInfo ci){
        searchInField.setMaxLength(96);searchOutField.setMaxLength(96);
        if(!cloud$restored) {
            var mc=net.minecraft.client.Minecraft.getInstance();
            String server=mc.getCurrentServer()==null ? (mc.getSingleplayerServer()==null?"local":
                    mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().toString()) : mc.getCurrentServer().ip;
            var endpoint=menu.getBlockEntity();
            cloud$scope=server+"\n"+mc.getUser().getGameProfile().getId()+"\n"+
                    (endpoint==null?"wireless":endpoint.getLevel().dimension().location()+"/"+endpoint.getBlockPos().asLong());
            cloud$preferences=new CloudTerminalViewState(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("GTL-Enhancedcore/cloud-terminal-views.json"));
            var saved=cloud$preferences.load(cloud$scope);cloud$library=saved.library();
            searchInField.setValue(saved.input());searchOutField.setValue(saved.output());
            cloud$lastInput=saved.input();cloud$lastOutput=saved.output();cloud$restored=true;
        }
        cloud$searchDue=System.nanoTime();
    }
    @Unique private void cloud$save() {
        if(cloud$restored)cloud$preferences.save(cloud$scope,new CloudTerminalViewState.View(cloud$library,
                searchInField.getValue(),searchOutField.getValue()));
    }
    @Inject(method="updateBeforeRender",at=@At("TAIL"),require=1)
    private void cloud$search(CallbackInfo ci){
        String input=searchInField.getValue(),output=searchOutField.getValue();
        if(!input.equals(cloud$lastInput)||!output.equals(cloud$lastOutput)){
            cloud$lastInput=input;cloud$lastOutput=output;cloud$searchDue=System.nanoTime()+120_000_000L;
        }
        if(cloud$searchDue!=0&&System.nanoTime()>=cloud$searchDue){
            cloud$searchDue=0;cloud$save();var in=com.gtl.enhancedcore.client.cloud.CloudPatternNameSearch.find(input);
            var out=com.gtl.enhancedcore.client.cloud.CloudPatternNameSearch.find(output);var encoder=java.util.Base64.getEncoder();
            ((CloudPatternAccessMenu)menu).cloud$accessSearch(input.substring(0,Math.min(96,input.length()))+"\n"+output.substring(0,Math.min(96,output.length()))
                    +"\n"+encoder.encodeToString(in.items().toByteArray())+"\n"+encoder.encodeToString(in.fluids().toByteArray())
                    +"\n"+encoder.encodeToString(out.items().toByteArray())+"\n"+encoder.encodeToString(out.fluids().toByteArray())
                    +"\n"+(++cloud$request));
        }
    }
    @Override public void removed() { cloud$save();super.removed(); }
    @Inject(method="clear",at=@At("HEAD"))
    private void cloud$clearRecords(CallbackInfo ci) { cloud$records.clear(); }
    // ExtendedAE cannot decode every GT virtual input or reproduce the localized server query.
    // Only tagged server-authorized cloud records bypass its second filter; ordinary providers retain it.
    @Inject(method="refreshList",at=@At("HEAD"))
    private void cloud$beginFilter(CallbackInfo ci) {
        cloud$serverMatched=false;if(!cloud$records.isEmpty())cachedSearches.clear();
    }
    @Inject(method="refreshList",at=@At("RETURN"))
    private void cloud$endFilter(CallbackInfo ci) { cloud$serverMatched=false; }
    @WrapOperation(method="refreshList",at=@At(value="INVOKE",target="Lappeng/client/gui/me/patternaccess/PatternContainerRecord;getInventory()Lappeng/util/inv/AppEngInternalInventory;"))
    private appeng.util.inv.AppEngInternalInventory cloud$selectRecord(appeng.client.gui.me.patternaccess.PatternContainerRecord record,
            Operation<appeng.util.inv.AppEngInternalInventory> original) {
        cloud$serverMatched=cloud$records.contains(record.getServerId());return original.call(record);
    }
    @WrapOperation(method="refreshList",at=@At(value="INVOKE",target="Lappeng/client/gui/me/patternaccess/PatternContainerRecord;getSearchName()Ljava/lang/String;"))
    private String cloud$selectName(appeng.client.gui.me.patternaccess.PatternContainerRecord record,Operation<String> original) {
        cloud$serverMatched=cloud$records.contains(record.getServerId());return original.call(record);
    }
    @Inject(method="itemStackMatchesSearchTerm",at=@At("HEAD"),cancellable=true)
    private void cloud$keepServerStack(net.minecraft.world.item.ItemStack stack,java.util.List<String> terms,boolean output,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if(cloud$serverMatched)cir.setReturnValue(true);
    }
    @WrapOperation(method="refreshList",at=@At(value="INVOKE",target="Lcom/glodblock/github/extendedae/util/FCUtil;compareTokens(Ljava/util/List;Ljava/util/List;)Z"))
    private boolean cloud$keepServerRecord(java.util.List<String> query,java.util.List<String> name,Operation<Boolean> original) {
        return cloud$serverMatched || original.call(query,name);
    }
}
