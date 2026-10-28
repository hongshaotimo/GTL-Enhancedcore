package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.client.preview.ChamberPreviewOverlay;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Actual tiny PatternPreviewWidget and native button callbacks, without opening a world. */
public final class ChamberPreviewUiChecks {
    private ChamberPreviewUiChecks() {}
    public static void run(BiConsumer<Boolean,String> check) throws Exception {
        var fixture=StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line",true,false,false);
        var levelField=PatternPreviewWidget.class.getDeclaredField("LEVEL");levelField.setAccessible(true);
        Object before=levelField.get(null);
        PatternPreviewWidget widget=null;
        try {
            var world=(TrackedDummyWorld)fixture.player().level();seedWorld(world);
            levelField.set(null,world);
            var definition=(MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtceu:wooden_multiblock_tank"));
            if(definition==null)throw new AssertionError("Missing tiny native wooden tank");
            widget=PatternPreviewWidget.getPatternWidget(definition);
            var scene=(SceneWidget)field(widget,"sceneWidget");
            var all=widget.widgets.stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(button->button.getSelfPositionY()==50).findFirst().orElseThrow();
            var toggle=widget.widgets.stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(button->button.getSelfPositionY()==70).findFirst().orElseThrow();
            check.accept(!ChamberPreviewOverlay.enabled(scene),"Actual tiny preview initially disables chamber overlay");
            aligned(widget, all, toggle, check, "Embedded");
            var controls=(org.gtlcore.gtlcore.client.preview.PreviewControls)field(widget,"previewControls");
            for (int[] size : new int[][]{{320,180},{960,540},{480,270}}) {
                controls.enter(size[0],size[1]);
                aligned(widget,all,toggle,check,"Fullscreen "+size[0]+"x"+size[1]);
                controls.layout();
                aligned(widget,all,toggle,check,"Repeated fullscreen layout "+size[0]);
            }
            controls.exit();
            aligned(widget,all,toggle,check,"Restored embedded");
            check.accept(toggle.getSelfPositionX()==138&&toggle.getSelfPositionY()==70,
                    "Fullscreen restore retains the original narrow embedded chamber geometry");
            for(String language:List.of("zh_cn","en_us")) {
                var resource=new ResourceLocation("gtl_enhancedcore","lang/"+language+".json");
                try(var reader=net.minecraft.client.Minecraft.getInstance().getResourceManager().openAsReader(resource)) {
                    var translations=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    for(String state:List.of("on","off")) check.accept(net.minecraft.client.Minecraft.getInstance().font
                            .width(translations.get("gtl_enhancedcore.chambers.preview_"+state).getAsString())<=toggle.getSizeWidth(),
                            language+" chamber "+state+" label fits the native button width");
                }
            }
            check.accept(toggle.getBackgroundTexture().getClass()==all.getBackgroundTexture().getClass()
                    &&field(toggle,"hoverTexture").getClass()==field(all,"hoverTexture").getClass(),
                    "Actual chamber button retains native gray texture-group and hover-border classes");
            press(toggle);
            check.accept(ChamberPreviewOverlay.enabled(scene),"Actual chamber button callback enables the scene overlay");
            widget.setPage(0,null);
            check.accept(ChamberPreviewOverlay.enabled(scene),"Native setPage rebuild retains the enabled preview state");
            var layer=PatternPreviewWidget.class.getDeclaredMethod("updateLayer",ClickData.class);layer.setAccessible(true);
            layer.invoke(widget,new ClickData());
            check.accept(ChamberPreviewOverlay.enabled(scene),"Native layer switch rebuild retains the enabled preview state");
            press(toggle);
            check.accept(!ChamberPreviewOverlay.contains(scene),"Actual second click immediately disables overlay drawing");
            press(toggle);scene.releaseCacheBuffer();
            check.accept(!ChamberPreviewOverlay.contains(scene),"Native scene release clears retained chamber state");
            widget.setPage(0,null);
            check.accept(ChamberPreviewOverlay.enabled(scene),"Live preview state restores its overlay after a released scene is rebuilt");
            var next=PatternPreviewWidget.getPatternWidget(definition);
            var nextScene=(SceneWidget)field(next,"sceneWidget");
            check.accept(!ChamberPreviewOverlay.enabled(nextScene),"New actual preview instance independently defaults to off");
            nextScene.releaseCacheBuffer();
        } finally {
            if(widget!=null)((SceneWidget)field(widget,"sceneWidget")).releaseCacheBuffer();
            PatternPreviewWidget.clearSharedPreviewState();levelField.set(null,before);
            fixture.holders().forEach(holder->holder.setLevel(null));
        }
    }
    static void aligned(PatternPreviewWidget widget,ButtonWidget all,ButtonWidget toggle,
            BiConsumer<Boolean,String> check,String phase) {
        check.accept(toggle.getSelfPositionX()==all.getSelfPositionX()
                &&toggle.getSizeWidth()==all.getSizeWidth()&&toggle.getSizeHeight()==all.getSizeHeight(),
                phase+": chamber button has identical left/right edges and size to ALL");
        check.accept(toggle.getSelfPositionY()==all.getSelfPositionY()+all.getSizeHeight()+2,
                phase+": chamber button follows ALL with the native two-pixel gap");
        check.accept(toggle.getSelfPositionX()>=0&&toggle.getSelfPositionY()>=0
                &&toggle.getSelfPositionX()+toggle.getSizeWidth()<=widget.getSizeWidth()
                &&toggle.getSelfPositionY()+toggle.getSizeHeight()<=widget.getSizeHeight(),
                phase+": complete button stays inside preview bounds");
    }
    @SuppressWarnings("unchecked")
    private static void press(ButtonWidget button)throws Exception {
        ((java.util.function.Consumer<ClickData>)field(button,"onPressCallback")).accept(new ClickData());
    }
    private static Object field(Object object,String name)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {
            Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);
        }catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
    }
    private static void set(Object object,String name,Object value)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {
            Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);return;
        }catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
    }
    /** Fill the detached title fixture's vanilla constructor inputs; never installs it in Minecraft. */
    static void seedWorld(TrackedDummyWorld world)throws Exception {
        set(world,"entities",new HashMap<Integer,net.minecraft.world.entity.Entity>());
        var damage=new MappedRegistry<net.minecraft.world.damagesource.DamageType>(Registries.DAMAGE_TYPE,com.mojang.serialization.Lifecycle.stable());
        for(Field field:net.minecraft.world.damagesource.DamageTypes.class.getFields())
            if(field.get(null) instanceof net.minecraft.resources.ResourceKey<?> key && key.registry().equals(Registries.DAMAGE_TYPE.location())) {
                @SuppressWarnings("unchecked") var typed=(net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType>)key;
                Registry.register(damage,typed,new net.minecraft.world.damagesource.DamageType("audit",0.1F));
            }
        damage.freeze();
        var biome=new MappedRegistry<net.minecraft.world.level.biome.Biome>(Registries.BIOME,com.mojang.serialization.Lifecycle.stable());
        var plain=new net.minecraft.world.level.biome.Biome.BiomeBuilder().hasPrecipitation(false).temperature(.8F).downfall(0)
                .specialEffects(new net.minecraft.world.level.biome.BiomeSpecialEffects.Builder().fogColor(0xC0D8FF).waterColor(0x3F76E4)
                        .waterFogColor(0x50533).skyColor(0x78A7FF).build())
                .mobSpawnSettings(net.minecraft.world.level.biome.MobSpawnSettings.EMPTY)
                .generationSettings(net.minecraft.world.level.biome.BiomeGenerationSettings.EMPTY).build();
        Registry.register(biome,net.minecraft.world.level.biome.Biomes.PLAINS,plain);biome.freeze();
        var registries=new ArrayList<Registry<?>>();BuiltInRegistries.REGISTRY.forEach(registries::add);registries.add(damage);registries.add(biome);
        var dimension=new net.minecraft.world.level.dimension.DimensionType(OptionalLong.empty(),true,false,false,true,1D,true,false,
                -64,384,384,net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD,new ResourceLocation("minecraft:overworld"),0F,
                new net.minecraft.world.level.dimension.DimensionType.MonsterSettings(false,true,net.minecraft.util.valueproviders.ConstantInt.of(0),0));
        var dimensions=new MappedRegistry<net.minecraft.world.level.dimension.DimensionType>(Registries.DIMENSION_TYPE,com.mojang.serialization.Lifecycle.stable());
        Registry.register(dimensions,net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD,dimension);dimensions.freeze();
        registries.add(dimensions);
        var access=new RegistryAccess.ImmutableRegistryAccess(registries);
        set(world,"auditRegistryAccess",access);
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(Level.class,world,Level.OVERWORLD,"f_46421_");
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(Level.class,world,
                dimensions.getHolderOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD),"f_204147_");
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(Level.class,world,
                net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD,"f_220349_");
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(Level.class,world,access,"f_268710_");
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(Level.class,world,
                new net.minecraft.client.multiplayer.ClientLevel.ClientLevelData(net.minecraft.world.Difficulty.NORMAL,false,false),"f_46442_");
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(Level.class,world,
                (java.util.function.Supplier<net.minecraft.util.profiling.ProfilerFiller>)()->net.minecraft.util.profiling.InactiveProfiler.INSTANCE,"f_46446_");
        set(world,"minPos",new org.joml.Vector3f(Integer.MAX_VALUE));set(world,"maxPos",new org.joml.Vector3f(Integer.MIN_VALUE));
    }
}
