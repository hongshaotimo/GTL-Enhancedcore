package com.gtl.enhancedcore.common.world.personalplot;

/** Pure permission/state rules shared by authoritative handlers and regression checks. */
public final class PersonalPlotPolicy {
    private PersonalPlotPolicy() {}
    public static boolean customize(boolean dedicated, boolean singleplayerOwner) { return !dedicated && singleplayerOwner; }
    public static boolean editTemplate(boolean dedicated, boolean operator) { return dedicated && operator; }
    public static boolean create(boolean dedicated, boolean owner, boolean activeTemplate, boolean alreadyOwned) {
        return !alreadyOwned && (dedicated ? activeTemplate : owner);
    }
    public static boolean manageOwner(boolean dedicated, boolean singleplayerOwner, boolean operator, boolean self) {
        return dedicated ? self || operator : singleplayerOwner && self;
    }
}
