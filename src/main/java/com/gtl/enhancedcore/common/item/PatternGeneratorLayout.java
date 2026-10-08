package com.gtl.enhancedcore.common.item;

/** Page geometry shared by the menu, saved preferences and regression checks. */
public final class PatternGeneratorLayout {
    public static final int ROWS = 15, DEFAULT_COLUMNS = 3, MAX_COLUMNS = 10;
    private PatternGeneratorLayout() {}
    public static int columns(int value) { return Math.max(1, Math.min(MAX_COLUMNS, value)); }
    public static int capacity(int columns) { return ROWS * columns(columns); }
    public static int pages(int count, int columns) { return Math.max(1, (Math.max(0, count) + capacity(columns) - 1) / capacity(columns)); }
    public static int page(int value, int count, int columns) { return Math.max(0, Math.min(value, pages(count, columns) - 1)); }
    public static int column(int index) { return index / ROWS; }
    public static int row(int index) { return index % ROWS; }
}
