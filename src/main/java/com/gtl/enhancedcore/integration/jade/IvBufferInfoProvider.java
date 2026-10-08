package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

public final class IvBufferInfoProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final String KEY = "gtlEnhancedcoreIvBuffer";
    private static final String NAME_KEY = "gtlEnhancedcoreSuperBufferName";

    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation("gtl_enhancedcore", "iv_buffer");
    }

    @Override
    public int getDefaultPriority() {
        return 10000;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        data.remove(KEY);
        data.remove(NAME_KEY);
        if (accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity
                && entity.getMetaMachine() instanceof MEPatternBufferPartMachine buffer && IvBuffers.compatible(buffer)) {
            var nameTag = new CompoundTag();
            SuperBufferJadeNames.current(buffer).write(nameTag);
            data.put(NAME_KEY, nameTag);
            if (IvBuffers.isolated(buffer)) {
                var state = IvBuffers.state(buffer);
                if (state != null) {
                    var tag = new CompoundTag();
                    tag.putInt("jobs", state.jobs.size());
                    tag.putBoolean("accepting", state.accepting);
                    data.put(KEY, tag);
                }
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (data.contains(NAME_KEY, Tag.TAG_COMPOUND)) {
            var name = SuperBufferJadeNames.read(data.getCompound(NAME_KEY));
            if (name != null && !name.stored().isEmpty() && !tooltip.get(Identifiers.CORE_OBJECT_NAME).isEmpty()) {
                tooltip.remove(Identifiers.CORE_OBJECT_NAME);
                tooltip.add(0, IThemeHelper.get().title(name.display()), Identifiers.CORE_OBJECT_NAME);
            }
        }
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return;
        var tag = data.getCompound(KEY);
        if (!tag.contains("jobs", Tag.TAG_INT) || !tag.contains("accepting", Tag.TAG_BYTE)
                || tag.getInt("jobs") < 0 || tag.getByte("accepting") < 0 || tag.getByte("accepting") > 1) return;
        tooltip.add(Component.translatable("gtl_enhancedcore.gui.iv_buffer_count", tag.getInt("jobs"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(tag.getBoolean("accepting")
                ? "gtl_enhancedcore.gui.iv_accepting" : "gtl_enhancedcore.gui.iv_not_accepting"));
        tooltip.add(Component.translatable("gtl_enhancedcore.gui.iv_break_help").withStyle(ChatFormatting.YELLOW));
    }
}
