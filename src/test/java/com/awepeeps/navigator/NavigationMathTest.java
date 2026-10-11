package com.awepeeps.navigator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NavigationMathTest {
  @Test
  void distanceIsHorizontalAndStable() {
    assertEquals(5.0, NavigationMath.horizontalDistance(3, 4), 1e-9);
    assertEquals(0.0, NavigationMath.horizontalDistance(0, 0), 1e-9);
  }

  @Test
  void bearingUsesMinecraftYawAndWrapsShortestWay() {
    assertEquals(0.0, NavigationMath.relativeBearing(0, 0, 1), 1e-9);
    assertEquals(90.0, NavigationMath.relativeBearing(0, -1, 0), 1e-9);
    assertEquals(-90.0, NavigationMath.relativeBearing(0, 1, 0), 1e-9);
    assertEquals(1.0, NavigationMath.relativeBearing(179, 0, -1), 1e-9);
  }

  @Test
  void directionsAreEightWay() {
    assertEquals("S", NavigationMath.direction(0));
    assertEquals("W", NavigationMath.direction(90));
    assertEquals("N", NavigationMath.direction(180));
    assertEquals("E", NavigationMath.direction(-90));
    assertEquals("E", NavigationMath.direction(NavigationMath.relativeBearing(0, 100, 0)));
    assertEquals("N", NavigationMath.direction(NavigationMath.relativeBearing(0, 0, -100)));
  }

  @Test
  void migratedYamlAcceptsIntegersAndDecimalsButRejectsInvalidCoordinates() throws Exception {
    var yaml = new org.bukkit.configuration.file.YamlConfiguration();
    yaml.loadFromString("x: 100\ny: 64.5\nz: -200\nbad: nope\n");
    assertTrue(NavigationMath.isFiniteCoordinate(yaml.get("x")));
    assertTrue(NavigationMath.isFiniteCoordinate(yaml.get("y")));
    assertTrue(NavigationMath.isFiniteCoordinate(yaml.get("z")));
    assertFalse(NavigationMath.isFiniteCoordinate(yaml.get("bad")));
    assertFalse(NavigationMath.isFiniteCoordinate(null));
    assertFalse(NavigationMath.isFiniteCoordinate(Double.NaN));
    assertFalse(NavigationMath.isFiniteCoordinate(Double.POSITIVE_INFINITY));
  }

  @Test
  void linkRadiusUsesInclusiveThreeDimensionalDistance() {
    assertTrue(NavigationMath.withinLinkRadius(3, 4, 12, 13));
    assertFalse(NavigationMath.withinLinkRadius(3, 4, 12.01, 13));
    assertFalse(NavigationMath.withinLinkRadius(Double.NaN, 0, 0, 16));
    assertFalse(NavigationMath.withinLinkRadius(0, 0, 0, Double.POSITIVE_INFINITY));
  }

  @Test
  void successfulUseDecrementsAndNeverRefills() {
    assertEquals(63, NavigationMath.afterSuccessfulUse(64));
    assertEquals(0, NavigationMath.afterSuccessfulUse(1));
    assertEquals(-1, NavigationMath.afterSuccessfulUse(0));
  }

  @Test
  void sameTargetAndFailedWorldOrRadiusNeverQualifyForBinding() {
    assertFalse(NavigationMath.shouldChargeBinding("id-a", "id-a"));
    assertTrue(NavigationMath.shouldChargeBinding("id-a", "id-b"));
    assertFalse(NavigationMath.canBind(false, 0, 0, 0, 16));
    assertFalse(NavigationMath.canBind(true, 16.01, 0, 0, 16));
    assertTrue(NavigationMath.canBind(true, 3, 4, 12, 13));
  }

  @Test
  void itemCapacityAndZeroWearDoNotFollowChangedServerDefaults() {
    assertEquals(64, NavigationMath.safeCapacity(64, 100));
    assertEquals(0, NavigationMath.safeRemaining(true, 0, NavigationMath.safeCapacity(64, 100)));
    assertEquals(17, NavigationMath.safeRemaining(true, 17, NavigationMath.safeCapacity(64, 8)));
    assertEquals(
        8, NavigationMath.safeRemaining(false, null, NavigationMath.safeCapacity(null, 8)));
    assertEquals(0, NavigationMath.safeRemaining(true, null, 64));
  }
}
