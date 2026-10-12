package naturalinput;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wires the four components together.
 *
 * Motion thread (fixed tick): reads WorldView, updates target -> RotationEngine,
 * recomputes velocity from the *new* rotation, publishes both atomically as
 * one MotionFrame, and hands it to the sink. Rotation and velocity are never
 * published separately, so a consumer can't see one without the other.
 *
 * Click thread: sleeps for the Gaussian interval, then clicks only if the
 * latest published frame says the target is within the proximity threshold.
 */
public final class InteractionLoop implements AutoCloseable {

    /** One coherent tick of output. */
    public record MotionFrame(long tick, Rotation rotation, Vec3 velocity, boolean targetInRange) {}

    private final WorldView world;
    private final InputSink sink;
    private final ProximityFilter proximity;
    private final ClickTimingEngine timing;
    private final RotationEngine rotation;
    private final long tickMillis;

    private final AtomicReference<MotionFrame> latest = new AtomicReference<>();
    private final AtomicBoolean clicking = new AtomicBoolean(false);
    private final ScheduledExecutorService motionExec =
            Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "motion-loop"));
    private Thread clickThread;
    private long tick;

    public InteractionLoop(WorldView world, InputSink sink, ProximityFilter proximity,
                           ClickTimingEngine timing, RotationEngine rotation, long tickMillis) {
        this.world = world;
        this.sink = sink;
        this.proximity = proximity;
        this.timing = timing;
        this.rotation = rotation;
        this.tickMillis = tickMillis;
    }

    public static InteractionLoop create(WorldView world, InputSink sink, Rotation initial) {
        GaussianSource motionGauss = new GaussianSource();
        GaussianSource clickGauss = new GaussianSource(); // separate instance: GaussianSource isn't thread-safe
        JitterOverlay jitter = JitterOverlay.defaults(motionGauss);
        RotationEngine rot = new RotationEngine(motionGauss, jitter, initial, 18.0, 0.6);
        return new InteractionLoop(world, sink, ProximityFilter.defaults(),
                ClickTimingEngine.defaults(clickGauss), rot, 50);
    }

    public void start() {
        motionExec.scheduleAtFixedRate(this::motionTick, 0, tickMillis, TimeUnit.MILLISECONDS);
    }

    public void setClicking(boolean on) {
        if (on && clicking.compareAndSet(false, true)) {
            clickThread = daemon(this::clickLoop, "click-loop");
            clickThread.start();
        } else if (!on && clicking.compareAndSet(true, false) && clickThread != null) {
            clickThread.interrupt();
        }
    }

    public MotionFrame latestFrame() { return latest.get(); }

    private void motionTick() {
        try {
            double lat = world.latencyMs();
            if (lat >= 0) proximity.reportLatency(lat);

            Vec3 eye = world.eyePosition();
            Optional<TargetProfile> target = world.activeTarget();
            boolean inRange = false;
            if (target.isPresent()) {
                rotation.setTarget(Rotation.lookingAt(eye, target.get().center()));
                inRange = proximity.inRange(eye, target.get());
            } else {
                rotation.clearTarget();
            }

            Rotation r = rotation.tick();
            Vec3 v = RotationEngine.velocityFor(r, world.forwardInput(), world.strafeInput(),
                    world.moveSpeed(), world.verticalVelocity());

            MotionFrame frame = new MotionFrame(tick++, r, v, inRange);
            latest.set(frame);
            sink.applyMotion(frame.rotation(), frame.velocity());
        } catch (RuntimeException e) {
            // keep the scheduler alive; a thrown exception would cancel future ticks
            System.err.println("motion tick failed: " + e);
        }
    }

    private void clickLoop() {
        try {
            while (clicking.get()) {
                sleepMs(timing.nextIntervalMs());
                MotionFrame f = latest.get();
                if (f == null || !f.targetInRange()) continue;
                sink.press();
                try {
                    sleepMs(timing.nextHoldMs());
                } finally {
                    sink.release();
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepMs(double ms) throws InterruptedException {
        long nanos = (long) (ms * 1e6);
        Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000));
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    @Override
    public void close() {
        setClicking(false);
        motionExec.shutdownNow();
    }
}
