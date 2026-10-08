package com.gtl.enhancedcore.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class GTLConfig {

    private GTLConfig() {}

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.BooleanValue CLAIM_REPLACEMENT_ENABLED;
    public static final ForgeConfigSpec.IntValue CLAIM_REPLACEMENT_INTERVAL_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("领地置换终端设置");
        builder.push("claim_replacement_terminal");

        CLAIM_REPLACEMENT_ENABLED = builder
                .comment("领地置换终端总开关。关闭后机器提示“已禁用”，不做任何操作。")
                .define("enabled", true);

        CLAIM_REPLACEMENT_INTERVAL_TICKS = builder
                .comment("每次替换之间的 tick 间隔（默认 5 = 每 5 tick 替换 1 个方块）。")
                .defineInRange("replace_interval_ticks", 5, 1, 200);

        builder.pop();
        COMMON_SPEC = builder.build();
    }
}
