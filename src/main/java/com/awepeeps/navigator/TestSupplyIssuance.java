package com.awepeeps.navigator;

import java.util.HashSet;
import java.util.Set;

/** Tracks worlds issued a test kit even when the private state file cannot be saved. */
final class TestSupplyIssuance {
  private final Set<String> issuedThisRun = new HashSet<>();

  boolean hasBeenIssued(String worldId, boolean persistedTombstone) {
    return persistedTombstone || issuedThisRun.contains(worldId);
  }

  boolean mayIssueNewKit(
      String worldId, boolean persistedTombstone, boolean hasSavedPosition, boolean hasOwnedChestMarker) {
    return !hasBeenIssued(worldId, persistedTombstone) && !hasSavedPosition && !hasOwnedChestMarker;
  }

  void markIssued(String worldId) {
    issuedThisRun.add(worldId);
  }
}
