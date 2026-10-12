package naturalinput;

import java.util.Optional;

/** Input boundary: supplies local state and the current target. */
public interface WorldView {
    Vec3 eyePosition();

    Optional<TargetProfile> activeTarget();

    /** Movement input axes in [-1,1]. */
    double forwardInput();

    double strafeInput();

    double moveSpeed();

    double verticalVelocity();

    /** Latest round-trip latency sample in ms, or a negative number if unknown. */
    double latencyMs();
}
