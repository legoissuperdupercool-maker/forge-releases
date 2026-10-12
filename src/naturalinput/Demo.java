package naturalinput;

import java.util.Optional;

/** Headless demo: a moving target and a sink that logs output. */
public final class Demo {
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        WorldView world = new WorldView() {
            public Vec3 eyePosition() { return new Vec3(0, 1.62, 0); }
            public Optional<TargetProfile> activeTarget() {
                double t = (System.currentTimeMillis() - start) / 1000.0;
                double x = Math.sin(t) * 1.5, z = 2.9;
                return Optional.of(new TargetProfile("dummy",
                        new Vec3(x - 0.3, 0, z - 0.3), new Vec3(x + 0.3, 1.8, z + 0.3)));
            }
            public double forwardInput() { return 1; }
            public double strafeInput() { return 0; }
            public double moveSpeed() { return 0.2; }
            public double verticalVelocity() { return 0; }
            public double latencyMs() { return 80; }
        };
        int[] clicks = {0};
        InputSink sink = new InputSink() {
            public void applyMotion(Rotation r, Vec3 v) {}
            public void press() { clicks[0]++; }
            public void release() {}
        };

        try (InteractionLoop loop = InteractionLoop.create(world, sink, new Rotation(90, 0))) {
            loop.start();
            loop.setClicking(true);
            for (int i = 0; i < 6; i++) {
                Thread.sleep(500);
                InteractionLoop.MotionFrame f = loop.latestFrame();
                System.out.printf("tick=%d yaw=%.3f pitch=%.3f vel=(%.3f, %.3f) inRange=%b clicks=%d%n",
                        f.tick(), f.rotation().yaw(), f.rotation().pitch(),
                        f.velocity().x(), f.velocity().z(), f.targetInRange(), clicks[0]);
            }
        }

        // timing distribution check
        ClickTimingEngine t = ClickTimingEngine.defaults(new GaussianSource(42));
        double sum = 0, sq = 0, min = 1e9, max = 0;
        int n = 10_000;
        for (int i = 0; i < n; i++) {
            double v = t.nextIntervalMs();
            sum += v; sq += v * v; min = Math.min(min, v); max = Math.max(max, v);
        }
        double mean = sum / n;
        System.out.printf("intervals: mean=%.2f sd=%.2f min=%.2f max=%.2f%n",
                mean, Math.sqrt(sq / n - mean * mean), min, max);
        System.out.printf("reach threshold @80ms: %.4f%n", ProximityFilter.defaults().currentThreshold());
    }
}
