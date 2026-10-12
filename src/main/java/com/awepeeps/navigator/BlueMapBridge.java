package com.awepeeps.navigator;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import com.flowpowered.math.vector.Vector3d;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

/** Optional BlueMap integration; this class is loaded only when BlueMap is present. */
public final class BlueMapBridge implements OptionalMapBridge {
  private static final String SET_ID = "awepeeps-navigator-pois";
  private static final String SET_LABEL = "Navigator POIs";
  private static final String SPAWN_SET_ID = "awepeeps-navigator-spawn";
  private static final String SPAWN_SET_LABEL = "World Spawn";
  private final Plugin plugin;
  private final AtomicLong generation = new AtomicLong();
  private final Object lifecycleLock = new Object();
  private final MarkerSetOwnership ownedSets = new MarkerSetOwnership();
  private final MarkerSetOwnership ownedSpawnSets = new MarkerSetOwnership();
  private final MarkerApplyState<Snapshot> applied = new MarkerApplyState<>();
  private volatile List<Point> points = List.of();
  private volatile List<Spawn> spawns = List.of();
  private volatile BlueMapAPI activeApi;
  private boolean closed;
  private final Consumer<BlueMapAPI> enabled = this::onEnabled;
  private final Consumer<BlueMapAPI> disabled = this::onDisabled;

  public BlueMapBridge(Plugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    // Consumer registration can call enabled immediately and asynchronously.
    BlueMapAPI.onDisable(disabled);
    BlueMapAPI.onEnable(enabled);
  }

  public void stop() {
    synchronized (lifecycleLock) {
      closed = true;
      generation.incrementAndGet();
      BlueMapAPI api = activeApi;
      activeApi = null;
      if (api != null) safely(() -> removeOwnedSets(api));
      applied.reset();
      points = List.of();
      spawns = List.of();
    }
    BlueMapAPI.unregisterListener(enabled);
    BlueMapAPI.unregisterListener(disabled);
  }

  public void update(List<OptionalMapBridge.Point> snapshot, List<OptionalMapBridge.Spawn> spawnSnapshot) {
    List<Point> newPoints = snapshot.stream().map(p -> new Point(p.id(), p.worldId(), p.name(), p.group(), p.x(), p.y(), p.z())).toList();
    List<Spawn> newSpawns = spawnSnapshot.stream().map(p -> new Spawn(p.worldId(), p.worldName(), p.x(), p.y(), p.z())).toList();
    points = newPoints;
    spawns = newSpawns;
    BlueMapAPI api = activeApi;
    if (api != null && plugin.isEnabled()) synchronized (lifecycleLock) {
      if (!closed && activeApi == api) applyIfNeeded(api, generation.get());
    }
  }

  private void onEnabled(BlueMapAPI api) {
    final long token;
    synchronized (lifecycleLock) {
      if (closed) return;
      token = generation.incrementAndGet();
      activeApi = api;
    }
    try {
      Bukkit.getScheduler().runTask(plugin, () -> {
        synchronized (lifecycleLock) {
          if (!closed && plugin.isEnabled() && generation.get() == token && activeApi == api) {
            applyIfNeeded(api, token);
          }
        }
      });
    } catch (RuntimeException | LinkageError error) {
      plugin.getLogger().warning("BlueMap POI marker scheduling failed: " + error.getMessage());
      synchronized (lifecycleLock) {
        if (generation.get() == token && activeApi == api) activeApi = null;
      }
    }
  }

  private void onDisabled(BlueMapAPI api) {
    synchronized (lifecycleLock) {
      // A delayed callback from an older API instance must not disturb the active epoch.
      if (activeApi != api) return;
      generation.incrementAndGet();
      activeApi = null;
      applied.reset();
      // BlueMap says the API is unusable after this callback returns. Its API is thread-safe,
      // so remove our owned markers in this callback without touching Bukkit/configuration.
      safely(() -> removeOwnedSets(api));
    }
  }

  private void applyIfNeeded(BlueMapAPI api, long token) {
    Snapshot desired = new Snapshot(points, spawns);
    if (!applied.needsApply(token, desired)) return;
    try {
      reconcile(api, desired.points(), desired.spawns());
      applied.markApplied(token, desired);
    } catch (Throwable error) {
      plugin.getLogger().warning("BlueMap POI marker update failed: " + error.getMessage());
    }
  }

  private void reconcile(BlueMapAPI api, List<Point> snapshot, List<Spawn> spawnSnapshot) {
    for (BlueMapWorld world : api.getWorlds()) {
      for (BlueMapMap map : world.getMaps()) {
        List<MarkerValue> poiMarkers = new java.util.ArrayList<>();
        if (plugin.getConfig().getBoolean("bluemap-markers", true)) {
          for (Point point : snapshot) if (api.getWorld(point.worldId()).filter(world::equals).isPresent()) {
            String id = "poi-" + point.id();
            String label = "[" + html(point.group()) + "] " + html(point.name());
            poiMarkers.add(new MarkerValue(id, POIMarker.builder().label(label)
                .position(new Vector3d(point.x(), point.y(), point.z()))
                .detail("<b>" + html(point.group()) + "</b><br>" + html(point.name())).build()));
          }
        }
        reconcileSet(map, SET_ID, SET_LABEL, poiMarkers, "poi-", ownedSets, false);

        List<MarkerValue> spawnMarkers = new java.util.ArrayList<>();
        for (Spawn spawn : spawnSnapshot) if (api.getWorld(spawn.worldId()).filter(world::equals).isPresent()) {
          String markerId = "spawn-" + spawn.worldId();
          String label = "Spawn: " + spawn.worldName();
          spawnMarkers.add(new MarkerValue(markerId, POIMarker.builder().label(label)
              .position(new Vector3d(spawn.x(), spawn.y(), spawn.z()))
              .detail("<b>" + html(label) + "</b>").build()));
        }
        reconcileSet(map, SPAWN_SET_ID, SPAWN_SET_LABEL, spawnMarkers, "spawn-", ownedSpawnSets, true);
      }
    }
  }

  private void reconcileSet(BlueMapMap map, String setId, String label, List<MarkerValue> markers,
      String markerPrefix, MarkerSetOwnership ownership, boolean removeSetWhenEmpty) {
    MarkerSet current = map.getMarkerSets().get(setId);
    MarkerSet owned = (MarkerSet) ownership.get(map);
    if (markers.isEmpty() && current == null) return;
    if (markers.isEmpty() && removeSetWhenEmpty && current != null && ownership.owns(map, current)) {
      map.getMarkerSets().computeIfPresent(setId, (key, value) -> value == current ? null : value);
      ownership.clear(map);
      return;
    }
    // A matching key or label is not ownership. Each map is considered independently.
    if (current == null) {
      MarkerSet candidate = owned != null ? owned : MarkerSet.builder().label(label).build();
      MarkerSet raced = map.getMarkerSets().putIfAbsent(setId, candidate);
      if (raced != null && raced != candidate) return;
      if (map.getMarkerSets().get(setId) != candidate) return;
      owned = candidate;
      ownership.claim(map, owned);
    } else {
      if (!ownership.mayReconcile(map, current)) return;
      owned = current;
    }
    Set<String> desired = new HashSet<>();
    for (MarkerValue marker : markers) {
      desired.add(marker.id());
      owned.getMarkers().put(marker.id(), marker.marker());
    }
    MarkerSet finalOwned = owned;
    finalOwned.getMarkers().keySet().removeIf(id -> id.startsWith(markerPrefix) && !desired.contains(id));
  }

  private void removeOwnedSets(BlueMapAPI api) {
    for (BlueMapWorld world : api.getWorlds()) {
      for (BlueMapMap map : world.getMaps()) {
        removeOwned(map, SET_ID, ownedSets);
        removeOwned(map, SPAWN_SET_ID, ownedSpawnSets);
      }
    }
    ownedSets.clear();
    ownedSpawnSets.clear();
  }

  private void removeOwned(BlueMapMap map, String id, MarkerSetOwnership ownership) {
    MarkerSet set = map.getMarkerSets().get(id);
    if (set != null && ownership.owns(map, set)) {
      map.getMarkerSets().computeIfPresent(id, (key, current) -> current == set ? null : current);
    }
    ownership.clear(map);
  }

  private void safely(Runnable action) {
    try {
      action.run();
    } catch (Throwable error) {
      plugin.getLogger().warning("BlueMap POI marker update failed: " + error.getMessage());
    }
  }

  private static String html(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }

  private record Point(String id, UUID worldId, String name, String group, double x, double y, double z) {}
  private record Spawn(UUID worldId, String worldName, double x, double y, double z) {}
  private record MarkerValue(String id, POIMarker marker) {}
  private record Snapshot(List<Point> points, List<Spawn> spawns) {}
}
