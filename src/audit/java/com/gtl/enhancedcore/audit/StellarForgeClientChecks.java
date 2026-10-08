package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.renderer.StellarForgeRenderer;
import com.gtl.enhancedcore.client.renderer.machine.StellarForgeMachineRenderer;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class StellarForgeClientChecks {
    public static long draws;
    private static boolean connecting, done;
    private static int phase, ticks;
    private static long lastDraws;
    private static CompletableFuture<Void> reload;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.stellarAudit") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            if (!connecting && mc.screen instanceof TitleScreen) {
                connecting = true;
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString("127.0.0.1:25654"),
                        new ServerData("Stellar audit", "127.0.0.1:25654", false), false);
            }
            if (mc.level == null || mc.player == null) return;
            mc.options.hideGui = true;
            var base = MetaMachine.getMachine(mc.level, StellarForgeFixture.CORE);
            if (!(base instanceof WorkableElectricMultiblockMachine machine) || !machine.isFormed()) {
                if (++ticks > 2400) throw new AssertionError("Client full structure never formed");
                return;
            }
            if (!(machine.getDefinition().getRenderer() instanceof StellarForgeMachineRenderer))
                throw new AssertionError("Forge renderer was not installed");
            if (mc.getBlockEntityRenderDispatcher().getRenderer(mc.level.getBlockEntity(StellarForgeFixture.CORE)) == null)
                throw new AssertionError("Forge block entity dispatcher was not registered");
            if (++ticks < 100 || mc.screen != null || mc.getOverlay() != null) return;
            if (phase == 0) {
                camera(50, 148, 180, 0.5, 130.5, 106.5);
            } else if (phase == 1 || phase == 2 || phase == 3 || phase == 6 || phase == 8 || phase == 10) {
                require(StellarForgeRenderer.ready() && draws > lastDraws + 10, "Active field was not drawn phase=" + phase);
                screenshot("stellar-" + phase);
                if (phase == 1) { /* Same camera, separated animation frames. */ }
                if (phase == 2) camera(-55, 150, 170, 0.5, 130.5, 106.5);
                if (phase == 3) mc.player.connection.sendChat("stellar-audit:pause");
                if (phase == 6) reload = mc.reloadResourcePacks();
                if (phase == 8) {
                    mc.player.connection.sendChat("stellar-audit:mirror");
                    camera(-180, 148, -50, -105.5, 130.5, 0.5);
                }
                if (phase == 10) {
                    log("COMPLETE full_structure active/animation/views/stop/resume/reload/mirror");
                    done = true;
                    mc.stop();
                }
            } else if (phase == 4) {
                require(!machine.getRecipeLogic().isWorking(), "Server pause was not synchronized");
            } else if (phase == 5) {
                require(draws == lastDraws, "Paused forge still draws the field");
                screenshot("stellar-stopped");
                mc.player.connection.sendChat("stellar-audit:resume");
            } else if (phase == 7) {
                if (!reload.isDone()) return;
                reload.join();
            } else if (phase == 9) {
                require(machine.getFrontFacing() == net.minecraft.core.Direction.EAST && machine.isFlipped(),
                        "Mirrored controller orientation not synchronized");
                camera(-180, 148, -50, -105.5, 130.5, 0.5);
            }
            log("PHASE " + phase + " draws=" + draws + " working=" + machine.getRecipeLogic().isWorking());
            lastDraws = draws;
            ticks = 0;
            phase++;
        } catch (Throwable error) {
            GTLEnhancedcore.LOGGER.error("[STELLAR_CLIENT] FAIL", error);
            done = true;
            mc.stop();
        }
    }

    private static void camera(double x, double y, double z, double tx, double ty, double tz) {
        var mc = Minecraft.getInstance();
        double dx = tx - x, dy = ty - (y + mc.player.getEyeHeight()), dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        mc.player.connection.sendCommand("tp @s " + x + " " + y + " " + z + " " + yaw + " " + pitch);
    }
    private static void screenshot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[STELLAR_CLIENT] {}", message); }
}
