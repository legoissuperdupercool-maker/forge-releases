package naturalinput;

/** Snapshot of a target: its bounding box and aim point. */
public record TargetProfile(String id, Vec3 boxMin, Vec3 boxMax) {
    public Vec3 center() {
        return boxMin.add(boxMax).scale(0.5);
    }
}
