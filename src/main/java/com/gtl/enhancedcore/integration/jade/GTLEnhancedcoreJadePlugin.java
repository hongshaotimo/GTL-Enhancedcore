package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * GTLEnhancedcore 的 Jade 插件。
 *
 * 依赖策略（见 API标准.md）：Jade 为 compileOnly 依赖 + mods.toml 可选依赖；
 * 本类由 Jade 的注解扫描发现并仅在 Jade 已安装时类加载，
 * 除 com.gtl.enhancedcore.integration.jade 包外禁止任何 GTLEnhancedcore 代码 import snownee.jade.*。
 */
@WailaPlugin
public class GTLEnhancedcoreJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new MachineDiagnosticProvider(), MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(new SteamPlatformInfoProvider(), MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(new FusionParallelInfoProvider(), MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(new AssemblyLineParallelProvider(), MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(new SingleRecipeParallelProvider(), MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(new IvOrderInfoProvider(), MetaMachineBlockEntity.class);
        registration.registerBlockDataProvider(new IvBufferInfoProvider(), MetaMachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new MachineDiagnosticProvider(), MetaMachineBlock.class);
        registration.registerBlockComponent(new SteamPlatformInfoProvider(), MetaMachineBlock.class);
        registration.registerBlockComponent(new FusionParallelInfoProvider(), MetaMachineBlock.class);
        registration.registerBlockComponent(new AssemblyLineParallelProvider(), MetaMachineBlock.class);
        registration.registerBlockComponent(new SingleRecipeParallelProvider(), MetaMachineBlock.class);
        registration.registerBlockComponent(new IvOrderInfoProvider(), MetaMachineBlock.class);
        registration.registerBlockComponent(new IvBufferInfoProvider(), MetaMachineBlock.class);
    }
}
