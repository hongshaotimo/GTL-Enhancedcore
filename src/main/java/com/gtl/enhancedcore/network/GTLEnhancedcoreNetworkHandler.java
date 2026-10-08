package com.gtl.enhancedcore.network;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import java.util.Optional;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * GTL-Enhancedcore 网络处理器（FTB Quests AE 集成已于 2026-09-09 移除，
 * 由第三方 mod ae2_ftbquest_detector 接管；仅保留领地置换与电路编码包）。
 */
public final class GTLEnhancedcoreNetworkHandler {
    public static final GTLEnhancedcoreNetworkHandler INSTANCE = new GTLEnhancedcoreNetworkHandler();
    public static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLEnhancedcore.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            v -> v.equals(PROTOCOL_VERSION),
            v -> v.equals(PROTOCOL_VERSION));
    private static boolean initialized;

    private GTLEnhancedcoreNetworkHandler() {
    }

    public static synchronized void init() {
        if (initialized) return;
        CHANNEL.registerMessage(0, C2SClaimReplacementPacket.class,
                C2SClaimReplacementPacket::encode, C2SClaimReplacementPacket::decode, C2SClaimReplacementPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, C2SCircuitEncoderPacket.class,
                C2SCircuitEncoderPacket::encode, C2SCircuitEncoderPacket::decode, C2SCircuitEncoderPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        initialized = true;
        GTLEnhancedcore.LOGGER.debug("GTL-Enhancedcore network handler initialized");
    }
}
