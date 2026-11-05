package com.gtl.enhancedcore.mixin.mc;

import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotDeletion;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Filters only tombstoned UUID private dimensions after level.dat/datapack merging and before saving. */
@Mixin(LevelStorageSource.LevelStorageAccess.class)
public abstract class PersonalPlotStorageMixin {
    // MixinExtras annotations do not populate this project's AP refmap. Match both actual namespaces explicitly.
    @ModifyReturnValue(method = {
            "getDataTag(Lcom/mojang/serialization/DynamicOps;Lnet/minecraft/world/level/WorldDataConfiguration;Lnet/minecraft/core/Registry;Lcom/mojang/serialization/Lifecycle;)Lcom/mojang/datafixers/util/Pair;",
            "m_246049_(Lcom/mojang/serialization/DynamicOps;Lnet/minecraft/world/level/WorldDataConfiguration;Lnet/minecraft/core/Registry;Lcom/mojang/serialization/Lifecycle;)Lcom/mojang/datafixers/util/Pair;"
    }, at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private Pair<WorldData, WorldDimensions.Complete> enhancedcore$filterDeletedDimensions(Pair<WorldData, WorldDimensions.Complete> loaded) {
        return PersonalPlotDeletion.filterLoaded(((LevelStorageSource.LevelStorageAccess) (Object) this).getLevelPath(LevelResource.ROOT), loaded);
    }
    @ModifyExpressionValue(method = {
            "saveDataTag(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/storage/WorldData;Lnet/minecraft/nbt/CompoundTag;)V",
            "m_78290_(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/storage/WorldData;Lnet/minecraft/nbt/CompoundTag;)V"
    }, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;createTag(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", remap = false),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;m_6626_(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", remap = false)
    }, remap = false, require = 1, allow = 1)
    private CompoundTag enhancedcore$omitDeletedDimensionDefinitions(CompoundTag data) {
        return PersonalPlotDeletion.filterSaved(((LevelStorageSource.LevelStorageAccess) (Object) this).getLevelPath(LevelResource.ROOT), data);
    }
}
