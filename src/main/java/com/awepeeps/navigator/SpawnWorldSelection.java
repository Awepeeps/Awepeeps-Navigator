package com.awepeeps.navigator;

import java.util.function.Function;
import org.bukkit.World;

/** Selects the first normal world in Bukkit's stable world order as the server's primary spawn. */
final class SpawnWorldSelection {
  private SpawnWorldSelection() {}

  static <T> T primaryNormalWorld(Iterable<T> worlds, Function<T, World.Environment> environment) {
    for (T world : worlds) {
      if (environment.apply(world) == World.Environment.NORMAL) return world;
    }
    return null;
  }
}
