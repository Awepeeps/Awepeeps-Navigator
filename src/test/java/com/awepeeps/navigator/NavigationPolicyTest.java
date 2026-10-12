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

  @Test
  void markerSetsAreOwnedPerMapByIdentityOnly() {
    MarkerSetOwnership ownership = new MarkerSetOwnership();
    Object mapA = new Object();
    Object mapB = new Object();
    Object owned = new String("Navigator POIs");
    Object foreignSameLabel = new String("Navigator POIs");

    assertTrue(ownership.mayReconcile(mapA, null));
    ownership.claim(mapA, owned);
    assertTrue(ownership.mayReconcile(mapA, owned));
    assertFalse(ownership.mayReconcile(mapA, foreignSameLabel));
    assertFalse(ownership.mayReconcile(mapB, foreignSameLabel));
    assertTrue(ownership.mayReconcile(mapB, null));
    assertTrue(ownership.owns(mapA, owned));
    assertFalse(ownership.owns(mapB, owned));

    ownership.clear();
    assertFalse(ownership.owns(mapA, owned));
    assertFalse(ownership.mayReconcile(mapA, foreignSameLabel));
  }

  @Test
  void clearingOneMarkerLayerDoesNotChangeAnotherLayerOwnership() {
    MarkerSetOwnership poiSets = new MarkerSetOwnership();
    MarkerSetOwnership spawnSets = new MarkerSetOwnership();
    Object map = new Object();
    Object poiSet = new Object();
    Object spawnSet = new Object();
    poiSets.claim(map, poiSet);
    spawnSets.claim(map, spawnSet);

    spawnSets.clear(map);

    assertTrue(poiSets.owns(map, poiSet));
    assertFalse(spawnSets.owns(map, spawnSet));
  }

  @Test
  void primarySpawnSelectionSkipsNetherAndEndWorlds() {
    var worlds = java.util.List.of(
        org.bukkit.World.Environment.NETHER,
        org.bukkit.World.Environment.THE_END,
        org.bukkit.World.Environment.NORMAL,
        org.bukkit.World.Environment.NORMAL);

    assertEquals(org.bukkit.World.Environment.NORMAL,
        SpawnWorldSelection.primaryNormalWorld(worlds, environment -> environment));
    assertEquals(null, SpawnWorldSelection.primaryNormalWorld(
        worlds.subList(0, 2), environment -> environment));
  }

  @Test
  void markerReconciliationRetriesFailuresAndSkipsUnchangedSuccessfulState() {
    MarkerApplyState<String> applied = new MarkerApplyState<>();
    assertTrue(applied.needsApply(1, "snapshot"));

    // A failed attempt is deliberately not recorded as applied.
    assertTrue(applied.needsApply(1, "snapshot"));
    applied.markApplied(1, "snapshot");
    assertFalse(applied.needsApply(1, "snapshot"));
    assertTrue(applied.needsApply(1, "updated"));
    assertTrue(applied.needsApply(2, "snapshot"));
  }

  @Test
  void temporaryTestSuppliesFailClosedAndExpireAtLocalDayBoundary() {
    var beforeExpiry = java.time.Instant.parse("2042-05-18T23:59:59Z");
    var expiryBoundary = java.time.Instant.parse("2042-05-19T00:00:00Z");
    assertTrue(TestSupplyPolicy.isActive(true, "2042-05-18", "UTC", beforeExpiry));
    assertFalse(TestSupplyPolicy.isActive(true, "2042-05-18", "UTC", expiryBoundary));
    assertFalse(TestSupplyPolicy.isActive(false, "2042-05-18", "UTC", beforeExpiry));
    assertFalse(TestSupplyPolicy.isActive(true, "", "UTC", beforeExpiry));
    assertFalse(TestSupplyPolicy.isActive(true, "2042-05-18", "invalid-zone", beforeExpiry));
  }

  @Test
  void testChestPlacementRequiresSafeAirOverSolidDryUnoccupiedFloor() {
    assertTrue(TestSupplyPolicy.isSafePlacement(true, false, true, true, false));
    assertFalse(TestSupplyPolicy.isSafePlacement(false, false, true, true, false));
    assertFalse(TestSupplyPolicy.isSafePlacement(true, true, true, true, false));
    assertFalse(TestSupplyPolicy.isSafePlacement(true, false, false, true, false));
    assertFalse(TestSupplyPolicy.isSafePlacement(true, false, true, false, false));
    assertFalse(TestSupplyPolicy.isSafePlacement(true, false, true, true, true));
  }

  @Test
  void chestSearchStaysOutsideSpawnProtectionAndDefersOversizedAreas() {
    assertFalse(SpawnProtectionPolicy.outsideProtection(16, 0, 16));
    assertFalse(SpawnProtectionPolicy.outsideProtection(16, 16, 16));
    assertTrue(SpawnProtectionPolicy.outsideProtection(17, 0, 16));
    assertTrue(SpawnProtectionPolicy.outsideProtection(0, 17, 16));
    assertEquals(18, SpawnProtectionPolicy.searchRadius(16, 8));
    assertEquals(64, SpawnProtectionPolicy.searchRadius(62, 8));
    assertEquals(-1, SpawnProtectionPolicy.searchRadius(64, 8));
  }

  @Test
  void issuanceTombstonePreventsReplacementKitsInMemoryAndAfterRestart() {
    TestSupplyIssuance issuance = new TestSupplyIssuance();
    assertFalse(issuance.hasBeenIssued("world-id", false));
    assertTrue(issuance.mayIssueNewKit("world-id", false, false, false));
    assertFalse(issuance.mayIssueNewKit("world-id", false, true, false),
        "A saved location is historical issuance even if its chest is gone or unmarked");
    assertFalse(issuance.mayIssueNewKit("world-id", false, false, true));

    // This memory marker remains authoritative when writing the private file fails.
    issuance.markIssued("world-id");
    assertTrue(issuance.hasBeenIssued("world-id", false));
    assertFalse(issuance.mayIssueNewKit("world-id", false, false, false));
    assertTrue(issuance.hasBeenIssued("world-id", true));

    // A new manager instance recognizes the durable tombstone after restart.
    TestSupplyIssuance restarted = new TestSupplyIssuance();
    assertTrue(restarted.hasBeenIssued("world-id", true));
    assertFalse(restarted.hasBeenIssued("different-world", false));
  }

  @Test
  void testSupplyKitContainsRequestedStarterItems() {
    var materials = TestSupplyKit.stacks().stream().map(TestSupplyKit.Stack::material).collect(java.util.stream.Collectors.toSet());
    assertTrue(materials.containsAll(java.util.Set.of(
        org.bukkit.Material.LODESTONE, org.bukkit.Material.RED_BED, org.bukkit.Material.COMPASS,
        org.bukkit.Material.BRUSH, org.bukkit.Material.OAK_LOG, org.bukkit.Material.OAK_PLANKS,
        org.bukkit.Material.COBBLESTONE, org.bukkit.Material.CRAFTING_TABLE, org.bukkit.Material.STICK,
        org.bukkit.Material.TORCH, org.bukkit.Material.DIAMOND, org.bukkit.Material.IRON_INGOT)));
    int slots = TestSupplyKit.stacks().stream().mapToInt(stack ->
        (stack.amount() + stack.material().getMaxStackSize() - 1) / stack.material().getMaxStackSize()).sum();
    assertTrue(slots <= 27, "The entire test kit must fit in one chest, including unstackable beds and brushes");
  }

  @Test
  void configuredVisibilityAndCreationRequirementsStayIndependent() {
    assertTrue(NavigatorPolicy.mayOpenMenu(true, true));
    assertFalse(NavigatorPolicy.mayOpenMenu(false, true));
    assertTrue(NavigatorPolicy.mayOpenMenu(false, false));
    assertFalse(NavigatorPolicy.mayCreate(false, true, true, true));
    assertFalse(NavigatorPolicy.mayCreate(true, false, false, true));
    assertTrue(NavigatorPolicy.mayCreate(true, false, true, true));
    assertFalse(NavigatorPolicy.mayCreate(true, false, false, true));
  }

  @Test
  void durabilityDisabledNeverChargesEvenAnOldManagedItem() {
    assertFalse(NavigatorPolicy.shouldCharge(false, true));
    assertFalse(NavigatorPolicy.shouldCharge(false, false));
    assertTrue(NavigatorPolicy.shouldCharge(true, true));
    assertFalse(NavigatorPolicy.shouldCharge(true, false));
  }

  @Test
  void sneakRightClickCanBeConfiguredToPassThrough() {
    assertTrue(NavigatorPolicy.mayOpenInteractionMenu(true, true));
    assertFalse(NavigatorPolicy.mayOpenInteractionMenu(true, false));
    assertTrue(NavigatorPolicy.mayOpenInteractionMenu(false, false));
  }
}

