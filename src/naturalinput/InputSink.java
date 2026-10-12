package naturalinput;

/**
 * Output boundary. Implement this against whatever the competition harness
 * exposes (a game API, a test client, a recorder). The framework never
 * touches devices or processes on its own.
 */
public interface InputSink {
    /** Called every tick with an orientation and velocity that were computed together. */
    void applyMotion(Rotation rotation, Vec3 velocity);

    void press();

    void release();
}
