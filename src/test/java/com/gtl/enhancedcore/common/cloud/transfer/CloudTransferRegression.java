package com.gtl.enhancedcore.common.cloud.transfer;

import java.util.UUID;

/** Network changes must never silently redirect outstanding outputs or refunds. */
public final class CloudTransferRegression {
    private CloudTransferRegression() {}
    public static int run() {
        int checks = 0;
        var guard = new CloudAeOriginGuard();
        Object original = new Object(), replacement = new Object();
        var endpoint = new CloudAeOriginGuard.Origin(UUID.randomUUID(), "minecraft:overworld", 42, 1);
        require(!guard.canUse(original, true), "unbound old orders must pause"); checks++;
        require(!guard.captureOnAccept((Object) null, endpoint, false), "offline admission"); checks++;
        require(guard.captureOnAccept(original, endpoint, false), "first admission"); checks++;
        require(guard.canUse(original, true), "same network"); checks++;
        require(!guard.canUse((Object)null, true), "temporary outage pauses work"); checks++;
        require(guard.canUse(original, true), "same original grid automatically recovers after outage"); checks++;
        require(!guard.canUse(replacement, true), "network switch pauses"); checks++;
        require(!guard.canUse(original, true), "reconnecting the old object does not revoke suspension"); checks++;
        require(!guard.authorizeReconnect(original, endpoint, false), "requires explicit authorization"); checks++;
        var replacedEndpoint = new CloudAeOriginGuard.Origin(endpoint.nodeId(), endpoint.dimension(), endpoint.position(), 2);
        require(!guard.authorizeReconnect(original, replacedEndpoint, true), "replacement generation is different"); checks++;
        require(guard.authorizeReconnect(replacement, endpoint, true), "explicit approved reconnect"); checks++;
        require(guard.canUse(replacement, true), "approved grid"); checks++;
        require(!guard.captureOnAccept(original, endpoint, true), "new dispatch cannot change old origin"); checks++;
        require(!guard.canUse(replacement, true), "failed old-order admission remains paused"); checks++;
        try { guard.clearWhenDrained(true); throw new AssertionError("cleared outstanding origin"); }
        catch (IllegalStateException expected) { checks++; }
        guard.clearWhenDrained(false);
        require(guard.origin() == null && guard.suspended(), "drained clear"); checks++;
        require(guard.captureOnAccept(original, replacedEndpoint, false), "new empty lifecycle can bind replacement"); checks++;
        var restored = new CloudAeOriginGuard();
        restored.load(guard.save());
        require(replacedEndpoint.equals(restored.origin()), "persistent endpoint identity"); checks++;
        require(!restored.canUse(original, true), "restart never auto-authorizes network"); checks++;
        require(restored.authorizeReconnect(original, replacedEndpoint, true), "restart requires explicit reconnect"); checks++;
        return checks;
    }
    public static void main(String[] args) { System.out.println("Cloud transfer checks: " + run()); }
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
