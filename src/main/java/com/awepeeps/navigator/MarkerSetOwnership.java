package com.awepeeps.navigator;

import java.util.IdentityHashMap;

/** Tracks marker sets by object identity; names and labels do not confer ownership. */
final class MarkerSetOwnership {
  private final IdentityHashMap<Object, Object> sets = new IdentityHashMap<>();

  boolean mayReconcile(Object map, Object currentSet) {
    return currentSet == null || sets.get(map) == currentSet;
  }

  Object get(Object map) {
    return sets.get(map);
  }

  void claim(Object map, Object set) {
    sets.put(map, set);
  }

  boolean owns(Object map, Object set) {
    return sets.get(map) == set;
  }

  void clear() {
    sets.clear();
  }

  void clear(Object map) {
    sets.remove(map);
  }
}
