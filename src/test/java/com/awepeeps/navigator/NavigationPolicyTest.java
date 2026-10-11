package com.awepeeps.navigator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NavigationPolicyTest {
  @Test
  void finiteCoordinatesAreAcceptedAndInvalidValuesRejected() {
    assertTrue(NavigationMath.isFiniteCoordinate(100));
    assertTrue(NavigationMath.isFiniteCoordinate(64.5));
    assertFalse(NavigationMath.isFiniteCoordinate("nope"));
    assertFalse(NavigationMath.isFiniteCoordinate(null));
    assertFalse(NavigationMath.isFiniteCoordinate(Double.NaN));
    assertFalse(NavigationMath.isFiniteCoordinate(Double.POSITIVE_INFINITY));
  }

  @Test
  void successfulCreationConsumesOneUseIncludingFinalUse() {
    assertEquals(63, NavigatorPolicy.remainingAfterCreation(true, 64));
    assertEquals(0, NavigatorPolicy.remainingAfterCreation(true, 1));
    assertEquals(-1, NavigatorPolicy.remainingAfterCreation(true, 0));
  }

  @Test
  void failedCreationAndBrowsingDoNotConsumeUses() {
    assertEquals(64, NavigatorPolicy.remainingAfterCreation(false, 64));
    assertTrue(NavigatorPolicy.canUseCompass(true, 1));
    assertFalse(NavigatorPolicy.canUseCompass(true, 2));
    assertFalse(NavigatorPolicy.canUseCompass(false, 1));
  }

  @Test
  void targetlessTrackerWithTrackedFalseStillCountsAsLodestoneCompass() {
    assertTrue(NavigatorPolicy.isLodestoneCompass(true, false));
    assertTrue(NavigatorPolicy.isLodestoneCompass(false, true));
    assertFalse(NavigatorPolicy.isLodestoneCompass(false, false));
  }

  @Test
  void lodestoneInteractionsPassThroughAndOffhandFallbackIsSuppressed() {
    assertTrue(NavigatorPolicy.shouldPassRightClick(true, false, false));
    assertTrue(NavigatorPolicy.shouldPassRightClick(false, true, false));
    assertTrue(NavigatorPolicy.shouldPassRightClick(false, false, true));
    assertFalse(NavigatorPolicy.shouldPassRightClick(false, false, false));
  }

  @Test
  void existingCapacityAndUsesRemainBounded() {
    assertEquals(64, NavigationMath.safeCapacity(64, 100));
    assertEquals(0, NavigationMath.safeRemaining(true, 0, 64));
    assertEquals(17, NavigationMath.safeRemaining(true, 17, 64));
    assertEquals(8, NavigationMath.safeRemaining(false, null, 8));
    assertEquals(0, NavigationMath.safeRemaining(true, null, 64));
  }
}

