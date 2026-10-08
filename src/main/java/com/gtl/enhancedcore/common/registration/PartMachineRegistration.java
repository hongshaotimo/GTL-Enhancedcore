package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer;
import com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredActiveMachineRenderer;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.hatch.MEDrivePartMachine;
import com.gtl.enhancedcore.common.machine.hatch.QuantumDataAccessHatchMachine;
import com.gtl.enhancedcore.common.machine.hatch.CircuitEncoderHatchMachine;
import com.gtl.enhancedcore.common.machine.hatch.CreativeComputationReceiverHatchMachine;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.Map;

/** Machine builders, invoked only during the GTCEu registration event. */
public final class PartMachineRegistration {
    private PartMachineRegistration() {}

    public static MachineDefinition registerCircuitEncoderHatch() {
        return GTLEnhancedcore.REGISTRATE
                .machine("circuit_encoder_hatch", CircuitEncoderHatchMachine::new)
                .langValue("电路编码仓")
                .rotationState(RotationState.ALL)
                .tier(0)
                .abilities(PartAbility.IMPORT_ITEMS)
                .renderer(() -> new OverlayTieredActiveMachineRenderer(0,
                        GTLEnhancedcore.id("block/machine/part/circuit_encoder_hatch"),
                        GTLEnhancedcore.id("block/machine/part/circuit_encoder_hatch_active")))
                .tooltipBuilder(MachineTooltips.create("circuit_encoder_hatch"))
                .register();
    }

    public static MachineDefinition registerMEDrive() {
        return GTLEnhancedcore.REGISTRATE
                .machine("me_drive", holder -> new MEDrivePartMachine(holder))
                .langValue("ME元件集成仓")
                .rotationState(RotationState.ALL)
                .tier(GTValues.MAX)
                .renderer(() -> new OverlayTieredMachineRenderer(GTValues.MAX, GTLEnhancedcore.id("block/machine/part/me_drive")))
                .tooltipBuilder(MachineTooltips.create("me_drive"))
                .register();
    }

    public static MachineDefinition registerClaimReplacementTerminal() {
        return GTLEnhancedcore.REGISTRATE
                .machine("claim_replacement_terminal", com.gtl.enhancedcore.common.machine.ClaimReplacementTerminalMachine::new)
                .langValue("领地置换终端")
                .rotationState(RotationState.ALL)
                .tier(GTValues.MAX)
                .renderer(() -> new com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer(GTValues.MAX, GTLEnhancedcore.id("block/machine/claim_replacement_terminal")))
                .tooltipBuilder(MachineTooltips.create("claim_replacement"))
                .register();
    }

    public static MachineDefinition registerQuantumDataAccessHatch() {
        return GTLEnhancedcore.REGISTRATE
                .machine("quantum_data_access_hatch",
                        holder -> new QuantumDataAccessHatchMachine(holder, GTValues.MAX, false))
                .langValue("Quantum Data Access Hatch")
                .rotationState(RotationState.ALL)
                .tier(GTValues.MAX)
                .abilities(PartAbility.DATA_ACCESS)
                .renderer(() -> new com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer(GTValues.MAX, GTLEnhancedcore.id("block/machine/part/quantum_data_access_hatch")))
                .tooltipBuilder(MachineTooltips.create("quantum_data_access_hatch", Map.of(
                        "tooltip.gtl_enhancedcore.quantum_data_access_hatch.1", new Object[]{810})))
                .register();
    }

    public static MachineDefinition registerCreativeComputationReceiverHatch() {
        return GTLEnhancedcore.REGISTRATE
                .machine("creative_computation_receiver_hatch", CreativeComputationReceiverHatchMachine::new)
                .langValue("创造算力数据靶仓")
                .rotationState(RotationState.ALL)
                .tier(GTValues.MAX)
                .abilities(PartAbility.COMPUTATION_DATA_RECEPTION)
                .renderer(() -> new OverlayTieredMachineRenderer(GTValues.MAX,
                        GTCEu.id("block/machine/part/computation_data_hatch")))
                .tooltipBuilder(MachineTooltips.create("creative_computation_receiver_hatch"))
                .register();
    }
}
