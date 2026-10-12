package naturalinput;

import java.util.SplittableRandom;

/**
 * Box-Muller standard-normal generator. Produces values in pairs and caches the
 * second one. Not thread-safe: give each thread its own instance.
 */
public final class GaussianSource {
    private final SplittableRandom rng;
    private double spare;
    private boolean hasSpare;

    public GaussianSource(long seed) { this.rng = new SplittableRandom(seed); }
    public GaussianSource() { this(System.nanoTime()); }

    /** Standard normal N(0,1) via the basic Box-Muller transform. */
    public double nextStandard() {
        if (hasSpare) {
            hasSpare = false;
            return spare;
        }
        double u1;
        do { u1 = rng.nextDouble(); } while (u1 <= Double.MIN_VALUE); // avoid log(0)
        double u2 = rng.nextDouble();
        double r = Math.sqrt(-2.0 * Math.log(u1));
        double theta = 2.0 * Math.PI * u2;
        spare = r * Math.sin(theta);
        hasSpare = true;
        return r * Math.cos(theta);
    }

    public double next(double mean, double stdDev) { return mean + stdDev * nextStandard(); }

    /** Uniform in [lo, hi). */
    public double uniform(double lo, double hi) { return lo + (hi - lo) * rng.nextDouble(); }

    public boolean chance(double p) { return rng.nextDouble() < p; }
}
