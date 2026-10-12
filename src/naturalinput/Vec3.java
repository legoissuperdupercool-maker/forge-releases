package naturalinput;

/** Immutable 3D vector. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public double distanceTo(Vec3 o) { return sub(o).length(); }
}
