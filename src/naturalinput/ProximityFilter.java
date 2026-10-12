package naturalinput;

/**
 * Component 1: proximity boundary filter.
 * The interaction threshold scales with measured latency but is always
 * clamped into [minReach, maxReach] (defaults 3.10 .. 3.15).
 */
public final class ProximityFilter {
    private final double minReach;
    private final double maxReach;
    /** Latency (ms) at which the threshold reaches maxReach. */
    private final double latencyCeilingMs;
    private volatile double smoothedLatencyMs;

    public ProximityFilter(double minReach, double maxReach, double latencyCeilingMs) {
        if (minReach <= 0 || maxReach < minReach) throw new IllegalArgumentException("bad reach bounds");
        this.minReach = minReach;
        this.maxReach = maxReach;
        this.latencyCeilingMs = latencyCeilingMs;
    }

    public static ProximityFilter defaults() { return new ProximityFilter(3.10, 3.15, 250.0); }

    /** Feed a latency sample; exponentially smoothed to ignore spikes. */
    public void reportLatency(double ms) {
        double prev = smoothedLatencyMs;
        smoothedLatencyMs = prev == 0 ? ms : prev * 0.85 + ms * 0.15;
    }

    public double currentThreshold() {
        double t = Math.min(1.0, Math.max(0.0, smoothedLatencyMs / latencyCeilingMs));
        return minReach + (maxReach - minReach) * t;
    }

    /** Distance from point to the closest point of an axis-aligned box. */
    public static double distanceToBox(Vec3 p, Vec3 boxMin, Vec3 boxMax) {
        double cx = Math.max(boxMin.x(), Math.min(p.x(), boxMax.x()));
        double cy = Math.max(boxMin.y(), Math.min(p.y(), boxMax.y()));
        double cz = Math.max(boxMin.z(), Math.min(p.z(), boxMax.z()));
        return p.distanceTo(new Vec3(cx, cy, cz));
    }

    public boolean inRange(Vec3 origin, TargetProfile target) {
        return distanceToBox(origin, target.boxMin(), target.boxMax()) <= currentThreshold();
    }
}
