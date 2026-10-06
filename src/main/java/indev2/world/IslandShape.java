package indev2.world;

/** Seeded coastline envelope shared by terrain, surface estimates, and structure placement. */
public record IslandShape(int radius, long seed, int seaLevel) {
    public static final int MIN_RADIUS = 128;
    public static final int MAX_RADIUS = 8192;
    public static final int MIN_MARGIN = 32;
    public static final int MAX_MARGIN = 4096;
    public static final int DEFAULT_RADIUS = 128;
    public static final int DEFAULT_MARGIN = 32;

    public double landFactor(int x, int z) {
        double angle = Math.atan2(z, x);
        double phase = (double) (seed & 0xffff) / 65536.0 * Math.PI * 2;
        // Maximum coastline radius is the configured radius; never encroach on the ocean margin.
        double coast = radius * (0.94 + 0.035 * Math.sin(angle * 5 + phase)
                + 0.025 * Math.sin(angle * 9 - phase * 2));
        double shoreWidth = Math.min(64, Math.max(24, radius * 0.15));
        double t = Math.clamp((coast - Math.hypot(x, z)) / shoreWidth, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public double floor(int x, int z) {
        return seaLevel - 28 + 36 * landFactor(x, z);
    }

    public double ceiling(int x, int z) {
        return seaLevel - 24 + 134 * landFactor(x, z);
    }

    public static int worldWidth(int radius, int margin) {
        return 2 * (radius + margin);
    }
}
