package com.gtl.enhancedcore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BlockReplacementTransactionRegression {
    private static int checks;

    private BlockReplacementTransactionRegression() {}

    public static int run() throws IOException {
        checks = 0;
        String transaction = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/util/BlockReplacementTransaction.java"));
        String terminal = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/machine/ClaimReplacementTerminalMachine.java"));
        check(transaction.contains("!level.getServer().isSameThread()"), "Replacement stays on the server thread");
        check(transaction.contains("level.captureBlockSnapshots || level.restoringBlockSnapshots || ACTIVE.get() != null"),
                "Nested and externally captured replacements are rejected");
        check(transaction.contains("BlockSnapshot.create(level.dimension(), level, position, 3)"),
                "Original block entity data is captured before mutation");
        check(transaction.contains("replacement.getBlock().setPlacedBy("), "Replacement block initialization is retained");
        check(transaction.contains("ForgeEventFactory.onBlockPlace(player, original, Direction.UP)"),
                "Single replacement publishes a real Forge placement event");
        check(transaction.contains("ForgeEventFactory.onMultiBlockPlace(player, snapshots, Direction.UP)"),
                "Multi-position block initialization also publishes placement protection");
        before(transaction, "if (canceled) return Result.CANCELED;", "if (!charge.getAsBoolean())",
                "Canceled placement never pays");
        before(transaction, "if (!charge.getAsBoolean())", "committed = true;", "Block commit only follows successful payment");
        before(transaction, "committed = true;", "current.onPlace(", "Irreversible callbacks belong to the paid commit phase");
        check(transaction.contains("replacement.getBlock() instanceof WitherSkullBlock")
                        && transaction.contains("if (!deferredInitializer) replacement.getBlock().setPlacedBy("),
                "Entity-spawning skull initialization is deferred until placement protection and payment");
        check(transaction.contains("return warned ? Result.PLACED_WITH_WARNING : Result.PLACED;"),
                "Post-commit callback failures report a committed warning rather than a false rollback");
        check(transaction.contains("level.markAndNotifyBlock("), "Accepted replacement sends Forge block notifications");
        check(transaction.contains("restore(true, false)"), "Rejected and failed replacement restores the original snapshot");
        check(transaction.contains("int index = snapshots.size() - 1; index >= 0; index--"),
                "Multi-position rollback follows reverse mutation order");
        check(transaction.contains("level.captureBlockSnapshots = false;")
                        && transaction.contains("level.restoringBlockSnapshots = false;")
                        && transaction.contains("ACTIVE.remove();"), "All capture state has a cleanup path");
        check(!transaction.contains("refund.run();"), "Irreversible post-commit effects never refund an already placed block");
        check(transaction.contains("EventPriority.LOWEST, receiveCanceled = true"),
                "Pending drops observe earlier entity insertion vetoes");
        check(transaction.contains("!event.isCanceled()") && transaction.contains("capture.owns(item.blockPosition())"),
                "Pending drops exclude vetoed and unrelated world entities");
        check(transaction.contains("!drop.isAddedToWorld() && !drop.isRemoved()")
                        && transaction.contains("level.getEntity(drop.getUUID()) == null"),
                "Drop exception fallback does not duplicate hidden or visible inserted entities");
        check(transaction.contains("player.getInventory().add(remaining)"),
                "A failed drop callback has an inventory delivery fallback");
        check(transaction.contains("if (!drop.isRemoved()) drop.discard();"),
                "Rollback discards owned staged entities without relying on chunk visibility");
        check(transaction.contains("if (drop.isAddedToWorld() || drop.isRemoved()) continue;"),
                "Commit neither replays inserted hidden entities nor revives removed entities");
        check(!transaction.contains("level.getEntity(drop.getUUID()) == drop"),
                "Hidden entity ownership is not inferred from the visible entity lookup");
        check(terminal.contains("BlockReplacementTransaction.replace("), "Terminal uses the protected replacement transaction");
        check(terminal.contains("case CANCELED -> ReplacementResult.PROTECTED;"),
                "Placement veto contributes to the protected result rather than a generic failure");
        before(terminal, "new net.minecraftforge.event.level.BlockEvent.BreakEvent", "BlockReplacementTransaction.replace(",
                "Break protection stays before placement protection");
        check(terminal.contains("ItemStack material = repl.copyWithCount(1);"), "Material identity is frozen for payment and refund");
        check(terminal.contains("boolean useAe = this.aeMode;"), "The inventory source cannot change during the transaction");
        check(terminal.contains("gtl_enhancedcore.claim_replacement.placement_warning"),
                "Committed callback failures are also visible to the initiating player");
        return checks;
    }

    private static void before(String source, String earlier, String later, String message) {
        int earlierIndex = source.indexOf(earlier);
        int laterIndex = source.indexOf(later);
        check(earlierIndex >= 0 && laterIndex > earlierIndex, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
