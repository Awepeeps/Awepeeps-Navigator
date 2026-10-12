package com.awepeeps.navigator;

import java.util.Objects;

/** Remembers only successfully reconciled desired state for one BlueMap API generation. */
final class MarkerApplyState<T> {
  private long generation = Long.MIN_VALUE;
  private T desired;

  boolean needsApply(long currentGeneration, T currentDesired) {
    return generation != currentGeneration || !Objects.equals(desired, currentDesired);
  }

  void markApplied(long currentGeneration, T currentDesired) {
    generation = currentGeneration;
    desired = currentDesired;
  }

  void reset() {
    generation = Long.MIN_VALUE;
    desired = null;
  }
}
