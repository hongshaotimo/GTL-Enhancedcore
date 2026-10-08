package com.gtl.enhancedcore.mixin.ae2.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 MEStorageScreen 的 repo（置顶后刷新置顶行用）。
 */
@Mixin(value = MEStorageScreen.class, remap = false)
public interface MEStorageScreenAccessor {

    @Accessor("repo")
    Repo getRepo();
}
