package com.gtl.enhancedcore.common.structure;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class StructureExportCommand {
    private StructureExportCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gtlenhancedcore")
                .then(Commands.literal("export_structures").requires(source -> source.hasPermission(2))
                        .executes(context -> export(context, false))
                        .then(Commands.literal("missing").executes(context -> export(context, true)))));
    }

    private static int export(CommandContext<CommandSourceStack> context, boolean missingOnly) {
        try {
            var result = missingOnly ? StructureSchematicExporter.exportMissingToDesktop()
                    : StructureSchematicExporter.exportToDesktop();
            context.getSource().sendSuccess(() -> Component.literal("Exported "
                    + result.exported() + " structures; skipped " + result.skipped()
                    + ". Output: " + result.directory()), false);
            return result.exported();
        } catch (IOException | RuntimeException failure) {
            GTLEnhancedcore.LOGGER.error("Failed to export registered multiblock schematics", failure);
            context.getSource().sendFailure(Component.literal("Structure export failed: "
                    + String.valueOf(failure.getMessage())));
            return 0;
        }
    }
}
