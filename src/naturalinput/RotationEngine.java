package naturalinput;

/**
 * Component 3: rotation interpolation with velocity resync.
 *
 * Each tick moves the current rotation toward the goal by a fraction taken
 * from a Gaussian bell velocity profile (fast in the middle of the move,
 * slow at start/end, like a minimum-jerk reach). After the rotation changes,
 * the world-space velocity is rebuilt from the movement input and the new
 * yaw, so orientation and motion always agree within the same tick.
 */
public final class RotationEngine {
    private final GaussianSource gauss;
    private final JitterOverlay jitter;
    private final double maxDegPerTick;
    private final double settleDeg;

    private Rotation current;
    private Rotation finalTarget;
    private Rotation activeGoal;  // either finalTarget or an overshoot point
    private boolean correcting;
    private double moveStartMag;

    public RotationEngine(GaussianSource gauss, JitterOverlay jitter, Rotation initial,
                          double maxDegPerTick, double settleDeg) {
        this.gauss = gauss;
        this.jitter = jitter;
        this.current = initial;
        this.maxDegPerTick = maxDegPerTick;
        this.settleDeg = settleDeg;
    }

    public Rotation current() { return current; }

    /** Set (or update) where we want to look. Starts a new move if it changed significantly. */
    public void setTarget(Rotation target) {
        boolean newMove = finalTarget == null
                || finalTarget.deltaTo(target).magnitude() > Math.max(2.0, settleDeg * 4);
        finalTarget = target;
        if (newMove) {
            activeGoal = jitter.planOvershoot(current, target);
            correcting = activeGoal != target;
            moveStartMag = Math.max(1e-6, current.deltaTo(activeGoal).magnitude());
        } else if (!correcting) {
            activeGoal = target; // track small target drift directly
        }
    }

    public void clearTarget() { finalTarget = null; activeGoal = null; correcting = false; }

    /** Bell-shaped speed factor over normalized progress p in [0,1]. */
    private double bell(double p) {
        double sigma = 0.28;
        double g = Math.exp(-Math.pow(p - 0.5, 2) / (2 * sigma * sigma));
        return 0.08 + 0.92 * g; // floor keeps it from stalling at the ends
    }

    /** Advance one tick. Returns the new rotation (jitter included). */
    public Rotation tick() {
        if (activeGoal != null) {
            Rotation d = current.deltaTo(activeGoal);
            double mag = d.magnitude();
            if (mag <= settleDeg) {
                if (correcting) {
                    // reached overshoot point -> correct back to the real target
                    correcting = false;
                    activeGoal = finalTarget;
                    moveStartMag = Math.max(1e-6, current.deltaTo(activeGoal).magnitude());
                }
            } else {
                double progress = 1.0 - Math.min(1.0, mag / moveStartMag);
                double speed = maxDegPerTick * bell(progress)
                        * Math.max(0.6, gauss.next(1.0, 0.08)); // per-tick speed variation
                double step = Math.min(mag, speed);
                current = current.plus(d.yaw() / mag * step, d.pitch() / mag * step);
            }
        }
        double[] j = jitter.nextJitter();
        current = current.plus(j[0], j[1]);
        return current;
    }

    /**
     * Rebuilds horizontal velocity from local input axes and the current yaw.
     * forward/strafe are in [-1,1]; speed in units/tick.
     */
    public static Vec3 velocityFor(Rotation rot, double forward, double strafe, double speed, double vy) {
        double len = Math.hypot(forward, strafe);
        if (len < 1e-9) return new Vec3(0, vy, 0);
        if (len > 1) { forward /= len; strafe /= len; }
        double rad = Math.toRadians(rot.yaw());
        double sin = Math.sin(rad), cos = Math.cos(rad);
        // forward = (-sin, cos), right/strafe = (cos, sin) for yaw convention in Rotation
        double vx = (-sin * forward + cos * strafe) * speed;
        double vz = (cos * forward + sin * strafe) * speed;
        return new Vec3(vx, vy, vz);
    }
}
