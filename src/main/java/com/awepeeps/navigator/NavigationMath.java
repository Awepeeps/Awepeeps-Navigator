package com.awepeeps.navigator;

/** Pure helpers used for navigation feedback. Minecraft yaw is zero at south. */
public final class NavigationMath {
  private NavigationMath() {}

  public static boolean isFiniteCoordinate(Object raw) {
    return raw instanceof Number number && Double.isFinite(number.doubleValue());
  }

  public static double horizontalDistance(double dx, double dz) {
    return Math.hypot(dx, dz);
  }

  /** Link radius is inclusive and three-dimensional. */
  public static boolean withinLinkRadius(double dx, double dy, double dz, double radius) {
    return Double.isFinite(dx)
        && Double.isFinite(dy)
        && Double.isFinite(dz)
        && Double.isFinite(radius)
        && radius >= 0
        && Math.hypot(Math.hypot(dx, dy), dz) <= radius;
  }

  /** Remaining PDC uses after success, or -1 when the compass was already empty. */
  public static int afterSuccessfulUse(int remaining) {
    return remaining > 0 ? remaining - 1 : -1;
  }

  public static boolean shouldChargeBinding(String currentId, String targetId) {
    return targetId != null && !targetId.equals(currentId);
  }

  public static boolean canBind(boolean sameWorld, double dx, double dy, double dz, double radius) {
    return sameWorld && withinLinkRadius(dx, dy, dz, radius);
  }

  public static int safeCapacity(Integer storedCapacity, int configuredCapacity) {
    return storedCapacity != null && storedCapacity > 0
        ? Math.min(storedCapacity, 1_000_000)
        : Math.max(1, configuredCapacity);
  }

  public static int safeRemaining(boolean managed, Integer storedRemaining, int itemCapacity) {
    if (storedRemaining == null) return managed ? 0 : itemCapacity;
    return Math.max(0, Math.min(itemCapacity, storedRemaining));
  }

  /** Signed shortest rotation in degrees from the player's facing yaw to the target bearing. */
  public static double relativeBearing(double playerYaw, double dx, double dz) {
    double bearing = Math.toDegrees(Math.atan2(-dx, dz));
    double delta = (bearing - playerYaw) % 360.0;
    if (delta >= 180.0) delta -= 360.0;
    if (delta < -180.0) delta += 360.0;
    return delta;
  }

  public static String direction(double degrees) {
    String[] points = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    int index = Math.floorMod((int) Math.round(degrees / 45.0), 8);
    return points[index];
  }
}
