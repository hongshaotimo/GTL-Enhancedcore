package com.gtl.enhancedcore.mixin.ftbquests.client;

import com.gtl.enhancedcore.integration.ftbquests.QuestVisibilityCycleGuard;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Quest.class, remap = false)
public abstract class QuestVisibilityCycleGuardMixin {
    @WrapMethod(method = "isVisible(Ldev/ftb/mods/ftbquests/quest/TeamData;)Z", remap = false)
    private boolean enhanced$guardVisibility(TeamData teamData, Operation<Boolean> original) {
        return QuestVisibilityCycleGuard.evaluate(this, () -> original.call(teamData));
    }
}
