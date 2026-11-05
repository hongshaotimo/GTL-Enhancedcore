package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.CloudChemicalModes;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.recipe.iv.IvPatternAdmission;
import java.util.*;

/** Fixed chemical aliases share one original while retaining independent mode claims and leases. */
public final class CloudChemicalModesRegression {
    public static int run() {
        int checks = 0;
        String small = "gtceu:chemical_reactor", large = "gtceu:large_chemical_reactor";
        for (String source : List.of(small, large)) for (String target : List.of(small, large)) {
            check(CloudChemicalModes.equivalent(source, target), "both chemical modes are equivalent"); checks++;
            check(CloudChemicalModes.allows(Set.of(source), target), "tagged original is admitted in either chemical mode"); checks++;
        }
        for (String other : List.of("gtceu:chemical_bath", "gtceu:mixer", "other:chemical_reactor", "chemical_reactor")) {
            check(!CloudChemicalModes.equivalent(small, other) && !CloudChemicalModes.allows(Set.of(other), large),
                    "no guessed names or unrelated recipe groups"); checks++;
            check(CloudChemicalModes.queryModes(other).equals(List.of(other)), "other maps remain exact"); checks++;
        }
        check(CloudChemicalModes.queryModes(small).equals(List.of(small, large))
                && CloudChemicalModes.queryModes(large).equals(List.of(large, small)), "native map precedes alias fallback"); checks++;
        check(CloudChemicalModes.catalogueTypes(Set.of(small)).equals(Set.of(small, large))
                && CloudChemicalModes.catalogueTypes(Set.of(large)).equals(Set.of(small, large)), "both catalogue filters find either chemical type"); checks++;
        check(CloudChemicalModes.allows(Set.of(), large) && !IvPatternAdmission.allowsType(Set.of(small), large),
                "untagged cloud semantics and strict noncloud IV semantics are preserved"); checks++;
        UUID owner = UUID.randomUUID(), library = UUID.randomUUID(), entry = UUID.randomUUID();
        UUID smallNode = UUID.randomUUID(), largeNode = UUID.randomUUID();
        var ledger = new CloudLedger(); ledger.addEntry(entry);
        ledger.addNode(smallNode, owner, library); ledger.addNode(largeNode, owner, library);
        ledger.claimMode(smallNode, UUID.randomUUID(), List.of(small));
        ledger.claimMode(largeNode, UUID.randomUUID(), List.of(large));
        var first = ledger.lease(smallNode, entry, 2, UUID.randomUUID());
        var second = ledger.lease(largeNode, entry, 9, UUID.randomUUID());
        check(ledger.entry(entry).leases().size() == 2 && !first.id().equals(second.id()), "one original has two independent leases"); checks++;
        check(ledger.node(smallNode).mode().equals(small) && ledger.node(largeNode).mode().equals(large)
                && ledger.node(smallNode).slots().get(2).equals(entry) && ledger.node(largeNode).slots().get(9).equals(entry),
                "aliases never rewrite native mode claims or stable slots"); checks++;
        ledger.exportFromCore(entry, UUID.randomUUID());
        check(ledger.entry(entry).leases().isEmpty() && ledger.node(smallNode).slots().isEmpty()
                && ledger.node(largeNode).slots().isEmpty(), "normal export still revokes both mode routes exactly once"); checks++;
        return checks;
    }
    public static void main(String[] args) { System.out.println("Cloud chemical mode checks: " + run()); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
