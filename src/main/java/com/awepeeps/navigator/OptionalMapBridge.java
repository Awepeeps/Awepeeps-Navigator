package com.awepeeps.navigator;

import java.util.List;
import java.util.UUID;

/** Core-only contract so optional integrations do not leak API classes into the plugin loader. */
interface OptionalMapBridge {
  void start();
  void stop();
  void update(List<Point> points, List<Spawn> spawns);

  record Point(String id, UUID worldId, String name, String group, double x, double y, double z) {}
  record Spawn(UUID worldId, String worldName, double x, double y, double z) {}
}
