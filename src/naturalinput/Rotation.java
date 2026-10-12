package naturalinput;

/** Yaw/pitch in degrees. Yaw 0 = +Z, increasing clockwise (toward -X). */
public record Rotation(double yaw, double pitch) {

    public static Rotation lookingAt(Vec3 from, Vec3 to) {
        Vec3 d = to.sub(from);
        double horiz = Math.hypot(d.x(), d.z());
        double yaw = Math.toDegrees(Math.atan2(-d.x(), d.z()));
        double pitch = Math.toDegrees(-Math.atan2(d.y(), horiz));
        return new Rotation(yaw, clampPitch(pitch));
    }

    public static double wrap(double deg) {
        deg %= 360.0;
        if (deg >= 180.0) deg -= 360.0;
        if (deg < -180.0) deg += 360.0;
        return deg;
    }

    public static double clampPitch(double p) { return Math.max(-90.0, Math.min(90.0, p)); }

    /** Shortest signed angular delta from this rotation to {@code o}. */
    public Rotation deltaTo(Rotation o) {
        return new Rotation(wrap(o.yaw - yaw), o.pitch - pitch);
    }

    public Rotation plus(double dYaw, double dPitch) {
        return new Rotation(wrap(yaw + dYaw), clampPitch(pitch + dPitch));
    }

    public double magnitude() { return Math.hypot(yaw, pitch); }
}
