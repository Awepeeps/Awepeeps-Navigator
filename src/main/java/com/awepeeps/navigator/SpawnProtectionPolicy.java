package com.awepeeps.navigator;

/** Bounded placement policy for Bukkit's square spawn-protection region. */
final class SpawnProtectionPolicy {
  static final int MAX_SEARCH_RADIUS = 64;

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
}
