package com.awepeeps.navigator;

/** Bounded placement policy for Bukkit's square spawn-protection region. */
final class SpawnProtectionPolicy {
  static final int MAX_SEARCH_RADIUS = 64;
  static final int MAX_VERTICAL_SEARCH = 16;

  private SpawnProtectionPolicy() {}

  static boolean outsideProtection(int deltaX, int deltaZ, int protectionRadius) {
    if (protectionRadius <= 0) return true;
    return Math.max(Math.abs(deltaX), Math.abs(deltaZ)) > protectionRadius;
  }

  /** Returns -1 when the configured protection is too large for the bounded placement search. */
  static int searchRadius(int protectionRadius, int preferredRadius) {
    if (protectionRadius >= MAX_SEARCH_RADIUS) return -1;
    return Math.min(MAX_SEARCH_RADIUS, Math.max(preferredRadius, Math.max(0, protectionRadius) + 2));
  }

  static Integer surfacePlacementY(int surfaceBlockY, int spawnBlockY, int minHeight, int maxHeight) {
    int placementY = surfaceBlockY + 1;
    if (Math.abs((long) placementY - spawnBlockY) > MAX_VERTICAL_SEARCH
        || placementY <= minHeight || placementY + 1 >= maxHeight) return null;
    return placementY;
  }

  static boolean safeFloor(boolean solid, boolean liquid, boolean leaves, boolean hazardous) {
    return solid && !liquid && !leaves && !hazardous;
  }
}
