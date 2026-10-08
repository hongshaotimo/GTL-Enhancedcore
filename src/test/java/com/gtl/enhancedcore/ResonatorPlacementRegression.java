package com.gtl.enhancedcore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ResonatorPlacementRegression {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/gtl/enhancedcore/common/event/ResonatorPlacementEvents.java");
    private static final String SPAWN_GUARD = "if (!level.addFreshEntity(crystal)) return;";

    private ResonatorPlacementRegression() {}

    public static int run() throws IOException {
        return verifySourceContracts() + verifyFailureAndSuccessOrdering();
    }

    public static int verifySourceContracts() throws IOException {
        String source = readCallback();
        Checks checks = new Checks();
        for (String guard : List.of(
                "level.isClientSide()",
                "stack.isEmpty() || !stack.is(Items.END_CRYSTAL)",
                "event.getFace() != Direction.UP",
                "instanceof IMachineBlockEntity machineBE",
                "machineBE.getMetaMachine() instanceof EndCrystalResonatorMachine",
                "level.mayInteract(event.getEntity(), abovePos)",
                "event.getEntity().mayUseItemAt(abovePos, Direction.UP, stack)",
                "!aboveState.isAir() && !replaceable",
                "if (occupied)")) {
            checks.check(source.contains(guard) && source.indexOf(guard) < source.indexOf(SPAWN_GUARD),
                    "Qualification remains ahead of entity insertion: " + guard);
        }
        checks.check(source.contains("BlockPos abovePos = clickedPos.above();"), "Only the top cell is targeted");
        checks.check(source.contains("new AABB(abovePos)"), "Occupancy uses the exact top-cell box");
        checks.check(source.contains("crystal.isAlive() && crystal.blockPosition().equals(abovePos)"),
                "Adjacent crystal boxes do not count as an occupied top cell");
        checks.check(source.contains("abovePos.getX() + 0.5D, abovePos.getY(), abovePos.getZ() + 0.5D"),
                "Crystal spawn coordinates are unchanged");
        checks.check(source.contains("crystal.setShowBottom(false);"), "Crystal base stays hidden");
        checks.check(source.contains(SPAWN_GUARD), "Rejected entity insertion returns immediately");
        checks.check(occurrences(source, "level.addFreshEntity(") == 1, "Exactly one insertion attempt");
        checks.check(occurrences(source, "level.destroyBlock(") == 1, "Exactly one top-block destruction site");
        checks.check(occurrences(source, "stack.shrink(") == 1, "Exactly one item-debit site");
        checks.check(source.contains("if (replaceable && !aboveState.isAir())"),
                "Only a replaceable non-air top block is destroyed");
        checks.check(source.contains("if (!player.getAbilities().instabuild)"),
                "Creative players remain exempt from item consumption");
        checks.check(source.indexOf(SPAWN_GUARD) < source.indexOf("level.destroyBlock(abovePos, true);"),
                "Canceled insertion cannot destroy the top block or spawn its drops");
        checks.check(source.indexOf("level.destroyBlock(abovePos, true);") < source.indexOf("stack.shrink(1);"),
                "Successful top-block cleanup remains before item debit");
        checks.check(source.indexOf("stack.shrink(1);") < source.indexOf("event.setCanceled(true);"),
                "Successful interaction is claimed only after item accounting");
        checks.check(source.indexOf("event.setCanceled(true);")
                        < source.indexOf("event.setCancellationResult(InteractionResult.SUCCESS);"),
                "The successful interaction result is unchanged");
        return checks.total;
    }

    public static int verifyFailureAndSuccessOrdering() throws IOException {
        String source = readCallback();
        List<Step> order = new ArrayList<>(List.of(Step.values()));
        Checks checks = new Checks();
        for (Step step : order) {
            checks.check(source.contains(step.token), "Production effect site exists: " + step);
        }
        checks.check(source.contains(SPAWN_GUARD), "Pure model is tied to the production failure return");
        order.sort(Comparator.comparingInt(step -> source.indexOf(step.token)));
        for (boolean creative : new boolean[]{false, true}) {
            for (boolean replaceableTop : new boolean[]{false, true}) {
                Fixture canceled = new Fixture(false, replaceableTop, creative);
                canceled.apply(order);
                checks.check(canceled.effects.equals(List.of(Step.ADD_CRYSTAL)),
                        "Canceled insertion has no subsequent effects");
                checks.check(canceled.topPresent == replaceableTop && canceled.dropCalls == 0
                                && canceled.held == 4 && !canceled.claimed && !canceled.success,
                        "Canceled insertion conserves the block, drops, stack and interaction");
                Fixture accepted = new Fixture(true, replaceableTop, creative);
                accepted.apply(order);
                checks.check(accepted.crystals == 1 && !accepted.topPresent,
                        "Accepted insertion creates one crystal and clears only a replaceable top");
                checks.check(accepted.dropCalls == (replaceableTop ? 1 : 0)
                                && accepted.held == (creative ? 4 : 3),
                        "Successful cleanup and survival/creative debit occur once");
                checks.check(accepted.claimed && accepted.success,
                        "Accepted insertion claims the interaction with success");
            }
        }
        Fixture originalFailure = new Fixture(false, true, false);
        originalFailure.apply(List.of(Step.DESTROY_TOP, Step.ADD_CRYSTAL, Step.CONSUME_ITEM,
                Step.CANCEL_INTERACTION, Step.SUCCESS_RESULT));
        checks.check(originalFailure.dropCalls == 1 && !originalFailure.topPresent
                        && originalFailure.crystals == 0 && originalFailure.held == 4,
                "Counterexample reproduces the original rejected-spawn side effect");
        return checks.total;
    }

    public static void main(String[] args) throws IOException {
        System.out.println("ResonatorPlacementRegression passed: " + run()
                + " assertions (source-shape + pure model; Forge runtime not exercised)");
    }

    private static String readCallback() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        int callback = source.indexOf("public static void onRightClickBlock(");
        if (callback < 0) throw new AssertionError("Production placement callback is missing");
        return source.substring(callback);
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        for (int position = source.indexOf(token); position >= 0;
                position = source.indexOf(token, position + token.length())) count++;
        return count;
    }

    private enum Step {
        ADD_CRYSTAL("level.addFreshEntity(crystal)"),
        DESTROY_TOP("level.destroyBlock(abovePos, true)"),
        CONSUME_ITEM("stack.shrink(1)"),
        CANCEL_INTERACTION("event.setCanceled(true)"),
        SUCCESS_RESULT("event.setCancellationResult(InteractionResult.SUCCESS)");

        private final String token;

        Step(String token) {
            this.token = token;
        }
    }

    private static final class Fixture {
        private final boolean spawnAccepted;
        private final boolean creative;
        private final List<Step> effects = new ArrayList<>();
        private boolean topPresent;
        private int held = 4;
        private int crystals;
        private int dropCalls;
        private boolean claimed;
        private boolean success;

        private Fixture(boolean spawnAccepted, boolean topPresent, boolean creative) {
            this.spawnAccepted = spawnAccepted;
            this.topPresent = topPresent;
            this.creative = creative;
        }

        private void apply(List<Step> order) {
            for (Step step : order) {
                if (step == Step.DESTROY_TOP && !topPresent) continue;
                if (step == Step.CONSUME_ITEM && creative) continue;
                effects.add(step);
                switch (step) {
                    case ADD_CRYSTAL -> {
                        if (!spawnAccepted) return;
                        crystals++;
                    }
                    case DESTROY_TOP -> {
                        topPresent = false;
                        dropCalls++;
                    }
                    case CONSUME_ITEM -> held--;
                    case CANCEL_INTERACTION -> claimed = true;
                    case SUCCESS_RESULT -> success = true;
                }
            }
        }
    }

    private static final class Checks {
        private int total;

        private void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
            total++;
        }
    }
}
