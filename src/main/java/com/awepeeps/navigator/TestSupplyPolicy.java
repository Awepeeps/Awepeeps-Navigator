package com.awepeeps.navigator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Fail-closed feature gate for temporary test supplies. The configured date is inclusive. */
final class TestSupplyPolicy {
  private TestSupplyPolicy() {}

  static boolean isActive(boolean enabled, String expiryDate, String timezone, Instant now) {
    if (!enabled || expiryDate == null || timezone == null) return false;
    try {
      LocalDate expiry = LocalDate.parse(expiryDate.trim());
      ZoneId zone = ZoneId.of(timezone.trim());
      Instant expiresAt = expiry.plusDays(1).atStartOfDay(zone).toInstant();
      return now.isBefore(expiresAt);
    } catch (java.time.DateTimeException | ArithmeticException error) {
      return false;
    }
  }

  static boolean isSafePlacement(
      boolean solidFloor, boolean liquidFloor, boolean airAtChest, boolean airAbove, boolean nextToChest) {
    return solidFloor && !liquidFloor && airAtChest && airAbove && !nextToChest;
  }
}
