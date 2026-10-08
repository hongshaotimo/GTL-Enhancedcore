package com.gtl.enhancedcore.common.data.machines.multiblock.PlasmaMachineToolStructure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

/**
 * 磁流体约束等离子机床占位结构：3×3×3，控制器位于正面中心。
 * C=主方块；A=钨钢机器外壳/功能仓；中心严格空气。
 * 后续替换正式结构时按 API标准 用 SchemTool 生成，禁止人工挪层。
 */
public final class PlasmaMachineToolStructure {
    private PlasmaMachineToolStructure() {}

    private static final String[] AISLE_001 = {
        "AAA",
        "AAA",
        "AAA",
    };

    private static final String[] AISLE_002 = {
        "AAA",
        "A A",
        "AAA",
    };

    private static final String[] AISLE_003 = {
        "AAA",
        "ACA",
        "AAA",
    };

    public static FactoryBlockPattern create() {
        return FactoryBlockPattern.start()
                .aisle(AISLE_001)
                .aisle(AISLE_002)
                .aisle(AISLE_003)
                ;
    }
}
