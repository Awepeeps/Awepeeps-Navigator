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
  private final Plugin plugin;
  private final AtomicLong generation = new AtomicLong();
  private final Object lifecycleLock = new Object();
  private final MarkerSetOwnership ownedSets = new MarkerSetOwnership();
  private volatile List<Point> points = List.of();
  private volatile BlueMapAPI activeApi;
  private boolean closed;
  private final Consumer<BlueMapAPI> enabled = this::onEnabled;
  private final Consumer<BlueMapAPI> disabled = this::onDisabled;

  public BlueMapBridge(Plugin plugin) {
    this.plugin = plugin;
  }

  public void start() {
    if (!plugin.getConfig().getBoolean("bluemap-markers", true)) return;
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
      points = List.of();
    }
    BlueMapAPI.unregisterListener(enabled);
    BlueMapAPI.unregisterListener(disabled);
  }

  public void update(List<OptionalMapBridge.Point> snapshot) {
    points = snapshot.stream().map(p -> new Point(p.id(), p.worldId(), p.name(), p.group(), p.x(), p.y(), p.z())).toList();
    BlueMapAPI api = activeApi;
    if (api != null && plugin.isEnabled()) synchronized (lifecycleLock) {
      if (!closed && activeApi == api) safely(() -> reconcile(api, points));
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
            safely(() -> reconcile(api, points));
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
      // BlueMap says the API is unusable after this callback returns. Its API is thread-safe,
      // so remove our owned markers in this callback without touching Bukkit/configuration.
      safely(() -> removeOwnedSets(api));
    }
  }

  private void reconcile(BlueMapAPI api, List<Point> snapshot) {
    for (BlueMapWorld world : api.getWorlds()) {
      for (BlueMapMap map : world.getMaps()) {
        MarkerSet current = map.getMarkerSets().get(SET_ID);
        MarkerSet owned = (MarkerSet) ownedSets.get(map);
        // A matching key or label is not ownership. Each map is considered independently.
        if (current == null) {
          MarkerSet candidate = owned != null ? owned : MarkerSet.builder().label(SET_LABEL).build();
          MarkerSet raced = map.getMarkerSets().putIfAbsent(SET_ID, candidate);
          if (raced != null && raced != candidate) continue;
          if (map.getMarkerSets().get(SET_ID) != candidate) continue;
          owned = candidate;
          ownedSets.claim(map, owned);
        } else {
          if (!ownedSets.mayReconcile(map, current)) continue;
          owned = current;
        }
        Set<String> desired = new HashSet<>();
        for (Point point : snapshot) {
          if (!api.getWorld(point.worldId()).filter(world::equals).isPresent()) continue;
          String id = "poi-" + point.id();
          desired.add(id);
          String label = "[" + html(point.group()) + "] " + html(point.name());
          POIMarker marker = POIMarker.builder()
              .label(label)
              .position(new Vector3d(point.x(), point.y(), point.z()))
              .detail("<b>" + html(point.group()) + "</b><br>" + html(point.name()))
              .build();
          owned.getMarkers().put(id, marker);
        }
        MarkerSet finalOwned = owned;
        finalOwned.getMarkers().keySet().removeIf(id -> id.startsWith("poi-") && !desired.contains(id));
      }
    }
  }

  private void removeOwnedSets(BlueMapAPI api) {
    for (BlueMapWorld world : api.getWorlds()) {
      for (BlueMapMap map : world.getMaps()) {
        MarkerSet set = map.getMarkerSets().get(SET_ID);
        if (set != null && ownedSets.owns(map, set)) {
          map.getMarkerSets().computeIfPresent(SET_ID, (key, current) -> current == set ? null : current);
        }
      }
    }
    ownedSets.clear();
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
}
