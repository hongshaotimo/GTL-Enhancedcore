package com.gtl.enhancedcore.common.world.plot;

/** Low industrial landscape around megastructures; their complete production volume stays open. */
public final class FactoryOutdoor {
    private FactoryOutdoor() {}

    public static String surface(FactoryArchitecture.Hall hall, int x, int z) {
        return surface(FactoryArchitecture.forResource(FactoryArchitecture.RESOURCE), hall, x, z);
    }
    static String surface(FactoryArchitecture.Renderer renderer, FactoryArchitecture.Hall hall, int x, int z) {
        int u = x - hall.x(), v = z - hall.z(), side = Math.min(u, hall.width() - 1 - u);
        if (side == 0 || v == 0 || v == hall.depth() - 1) return renderer.block("dark");
        if ((side == 3 && Math.floorMod(v, 8) < 2) || (v == 3 && Math.floorMod(u, 8) < 2))
            return renderer.block("yellow");
        if (side <= 2 || v <= 2 || v >= hall.depth() - 3) return renderer.block("quartz");
        if (side == 11 || v == 21 || v == hall.depth() - 12) return renderer.accent(hall);
        if (side <= 10 || v <= 20 || v >= hall.depth() - 11) return renderer.block("quartz");
        if (u % 32 == 0 || v % 32 == 0) return renderer.block("quartz");
        return renderer.block("floor");
    }

    public static String above(FactoryArchitecture.Hall hall, int x, int y, int z) {
        return above(FactoryArchitecture.forResource(FactoryArchitecture.RESOURCE), hall, x, y, z);
    }
    static String above(FactoryArchitecture.Renderer renderer, FactoryArchitecture.Hall hall, int x, int y, int z) {
        if (y < 1 || y > 8) return null;
        int u = x - hall.x(), v = z - hall.z(), side = Math.min(u, hall.width() - 1 - u);
        // This guard covers both the complete machine and the air above it, at every height.
        if (u >= 12 && u <= hall.width() - 13 && v >= 22 && v <= hall.depth() - 13) return null;
        int center = hall.width() / 2;
        if (v <= 21 && Math.abs(u - center) <= 6) return null;
        int along = Math.floorMod(v, 32);
        if (side == 10 && along >= 4 && along <= 27) {
            if (y == 2 && along % 8 != 0) return renderer.block("barsZ");
            if (y == 1 && along % 8 == 0) return renderer.block("steel");
            if (y == 3 && along == 16) return renderer.block("light");
        }
        // Four cut-back planted plinths frame the court without becoming enclosing pillars.
        int fromEnd = Math.min(v, hall.depth() - 1 - v);
        if (side >= 3 && side <= 9 && fromEnd >= 3 && fromEnd <= 9 && side + fromEnd >= 9) {
            boolean rim = side == 3 || side == 9 || fromEnd == 3 || fromEnd == 9;
            if (y == 1) return renderer.block("quartz");
            if (y == 2) return rim ? renderer.block("slab") : renderer.block("hedge");
            return null;
        }
        // The observation decks rise by walkable stairs in the outer eleven-block service band.
        int deckStart = hall.depth() / 2 - 13, deckEnd = deckStart + 25;
        if (side >= 3 && side <= 9 && v >= deckStart && v <= deckEnd) {
            int step = Math.min(5, v - deckStart);
            if (v < deckStart + 5 && side >= 5 && side <= 7 && y == step + 1) return renderer.block("accessStair");
            if (v >= deckStart + 5) {
                if (y == 5) return renderer.block("floorSlab");
                if ((side == 3 || side == 9 || v == deckEnd) && y == 6) return side == 3 || side == 9 ? renderer.block("barsZ") : renderer.block("barsX");
                if ((side == 3 || side == 9) && (v == deckStart + 5 || v == deckEnd) && y <= 4) return renderer.block("column");
                if (side == 6 && v == deckEnd - 2 && y == 6) return renderer.block("light");
            }
        }
        // Short, low utility galleries have deliberate gaps; they never bridge the machine court.
        int servicePhase = Math.floorMod(v - 24, 64);
        if (side == 2 && v >= 24 && v <= hall.depth() - 24 && servicePhase <= 20) {
            if ((servicePhase == 0 || servicePhase == 20) && y <= 6) return renderer.block("column");
            if (y == 6) return renderer.block("pipe");
            if (y == 7 && servicePhase % 5 == 0) return renderer.block("darkSlab");
        }
        if (side >= 4 && side <= 8 && v >= hall.depth() - 10 && v <= hall.depth() - 4) {
            if (y <= 2) return renderer.block("base");
            if (y <= 4 && (side == 4 || side == 8)) return renderer.block("quartz");
            if (y == 3) return renderer.block("louvreEast");
            if (y == 4) return renderer.block("topSlab");
        }
        // Front benches and inset lighting leave a broad direct route from the road.
        if (v == 13 && side >= 15 && side <= 28) return y == 1 ? renderer.block("topSlab") : null;
        if (v == 15 && side >= 15 && side <= 28) return y == 1 ? renderer.block("quartz") : y == 2 ? renderer.block("slab") : null;
        if ((side == 3 && Math.floorMod(v, 48) == 18) || (v == 3 && side == 15)) {
            if (y <= 3) return renderer.block("column");
            if (y == 4) return renderer.block("light");
            if (y == 5) return renderer.block("slab");
        }
        return null;
    }

    public static int top(FactoryArchitecture.Hall hall, int x, int z) {
        return top(FactoryArchitecture.forResource(FactoryArchitecture.RESOURCE), hall, x, z);
    }
    static int top(FactoryArchitecture.Renderer renderer, FactoryArchitecture.Hall hall, int x, int z) {
        for (int y = 8; y >= 1; y--) if (above(renderer, hall, x, y, z) != null) return y;
        return 0;
    }
}
