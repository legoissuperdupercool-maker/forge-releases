package naturalinput;

/**
 * Component 4: micro-jitter and overshoot.
 *
 * Jitter: a smoothed (first-order low-pass) noise process whose per-step
 * amplitude magnitude is drawn from [0.03, 0.05] degrees, so it reads as
 * continuous tremor rather than white noise.
 *
 * Overshoot: when a new large movement starts, a goal past the target is
 * chosen (proportional to distance, Gaussian-scattered); once reached, the
 * goal snaps back to the real target, producing the correction.
 */
public final class JitterOverlay {
    private final GaussianSource gauss;
    private final double minAmp, maxAmp;
    private final double smoothing; // 0..1, higher = smoother tremor
    private double noiseYaw, noisePitch;

    private final double overshootFraction;   // mean overshoot as fraction of move
    private final double overshootProbability;
    private final double overshootTriggerDeg; // only overshoot moves larger than this

    public JitterOverlay(GaussianSource gauss, double minAmp, double maxAmp, double smoothing,
                         double overshootFraction, double overshootProbability, double overshootTriggerDeg) {
        this.gauss = gauss;
        this.minAmp = minAmp;
        this.maxAmp = maxAmp;
        this.smoothing = smoothing;
        this.overshootFraction = overshootFraction;
        this.overshootProbability = overshootProbability;
        this.overshootTriggerDeg = overshootTriggerDeg;
    }

    public static JitterOverlay defaults(GaussianSource g) {
        return new JitterOverlay(g, 0.03, 0.05, 0.6, 0.06, 0.7, 8.0);
    }

    /** One step of tremor, each axis bounded to ±[minAmp, maxAmp]. */
    public double[] nextJitter() {
        noiseYaw = smoothing * noiseYaw + (1 - smoothing) * gauss.nextStandard();
        noisePitch = smoothing * noisePitch + (1 - smoothing) * gauss.nextStandard();
        return new double[] { shape(noiseYaw), shape(noisePitch) };
    }

    private double shape(double n) {
        double amp = gauss.uniform(minAmp, maxAmp);
        double sign = n >= 0 ? 1 : -1;
        // magnitude modulated by noise strength, but floored at minAmp
        double mag = Math.min(maxAmp, Math.max(minAmp, amp * Math.min(1.0, Math.abs(n) * 2.5)));
        return sign * mag;
    }

    /**
     * Plans the intermediate goal for a move from {@code from} to {@code target}.
     * Returns the target itself when no overshoot is applied.
     */
    public Rotation planOvershoot(Rotation from, Rotation target) {
        Rotation d = from.deltaTo(target);
        double mag = d.magnitude();
        if (mag < overshootTriggerDeg || !gauss.chance(overshootProbability)) return target;
        double frac = Math.max(0.01, gauss.next(overshootFraction, overshootFraction * 0.35));
        // slight off-axis component: overshoot isn't perfectly collinear
        double perp = gauss.next(0, mag * 0.01);
        double ux = d.yaw() / mag, uy = d.pitch() / mag;
        return target.plus(ux * mag * frac - uy * perp, uy * mag * frac + ux * perp);
    }
}
