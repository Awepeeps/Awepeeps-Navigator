package com.awepeeps.navigator;

/** Pure validation and use-counter helpers shared by the plugin and tests. */
public final class NavigationMath {
  private NavigationMath() {}

  public static boolean isFiniteCoordinate(Object raw) {
    return raw instanceof Number number && Double.isFinite(number.doubleValue());
  }

  /** Remaining PDC uses after success, or -1 when the compass was already empty. */
  public static int afterSuccessfulUse(int remaining) {
    return remaining > 0 ? remaining - 1 : -1;
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
}
