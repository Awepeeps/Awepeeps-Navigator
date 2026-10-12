package com.awepeeps.navigator;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

/** Creates one one-time, expiring test supply chest near the primary normal world's current spawn. */
final class TestSupplyChestManager {
  private static final int SEARCH_RADIUS = 8;
  private static final int DISCOVERY_Y = 8;
  private static final Set<Material> UNSAFE_FLOORS = Set.of(
      Material.CACTUS,
      Material.MAGMA_BLOCK,
      Material.CAMPFIRE,
      Material.SOUL_CAMPFIRE,
      Material.FIRE,
      Material.SOUL_FIRE,
      Material.POWDER_SNOW,
      Material.SWEET_BERRY_BUSH,
      Material.POINTED_DRIPSTONE,
      Material.SLIME_BLOCK,
      Material.HONEY_BLOCK);
  private final JavaPlugin plugin;
  private final NamespacedKey ownedKey;
  private final File storeFile;
  private final YamlConfiguration store;
  private final Map<String, Long> retryAfterNanos = new HashMap<>();
  private final Map<String, Long> nextNoCandidateLogNanos = new HashMap<>();
  private final TestSupplyIssuance issuance = new TestSupplyIssuance();
  private boolean invalidConfigLogged;

  TestSupplyChestManager(JavaPlugin plugin) {
    this.plugin = plugin;
    this.ownedKey = new NamespacedKey(plugin, "test_supply_chest");
    this.storeFile = new File(plugin.getDataFolder(), "test-supplies.yml");
    this.store = YamlConfiguration.loadConfiguration(storeFile);
  }

  void tick() {
    if (!TestSupplyPolicy.isActive(
        plugin.getConfig().getBoolean("test-supplies.enabled", false),
        plugin.getConfig().getString("test-supplies.expires-on", ""),
        plugin.getConfig().getString("test-supplies.timezone", ""), Instant.now())) {
      if (plugin.getConfig().getBoolean("test-supplies.enabled", false) && !invalidConfigLogged) {
        invalidConfigLogged = true;
        plugin.getLogger().warning("Test supplies are disabled: configure a valid expiry date and timezone, and keep the expiry date current.");
      }
      return;
    }
    invalidConfigLogged = false;
    World world = SpawnWorldSelection.primaryNormalWorld(Bukkit.getWorlds(), World::getEnvironment);
    if (world != null) ensureChest(world);
  }

  private void ensureChest(World world) {
    String worldId = world.getUID().toString();
    long now = System.nanoTime();
    if (now < retryAfterNanos.getOrDefault(worldId, 0L)) return;
    String worldPath = "worlds." + world.getUID();
    boolean persistedTombstone = store.getBoolean(worldPath + ".issued", false);
    boolean hasSavedPosition = store.contains(worldPath + ".x")
        && store.contains(worldPath + ".y") && store.contains(worldPath + ".z");
    if (!issuance.mayIssueNewKit(worldId, persistedTombstone, hasSavedPosition, false)) {
      if (!persistedTombstone && !issuance.hasBeenIssued(worldId, false)) reserveIssuance(worldPath, worldId);
      return;
    }

    int spawnRadius = Bukkit.getSpawnRadius();
    int discoveryRadius = SpawnProtectionPolicy.MAX_SEARCH_RADIUS;
    Block discovered = findOwnedNearSpawn(world, discoveryRadius);
    if (discovered != null) {
      reserveIssuance(worldPath, worldId);
      remember(world, discovered);
      return;
    }

    int permittedSearchRadius = SpawnProtectionPolicy.searchRadius(spawnRadius, SEARCH_RADIUS);
    if (permittedSearchRadius < 0) {
      retryAfterNanos.put(worldId, now + java.util.concurrent.TimeUnit.SECONDS.toNanos(30));
      return;
    }
    SearchResult search = findSafeCandidate(world, permittedSearchRadius, spawnRadius);
    if (search.block() == null) {
      retryAfterNanos.put(worldId, now + java.util.concurrent.TimeUnit.SECONDS.toNanos(30));
      if (now >= nextNoCandidateLogNanos.getOrDefault(worldId, 0L)) {
        plugin.getLogger().info("Test supplies placement deferred: no safe loaded surface in the bounded spawn search "
            + "(columns=" + search.columns() + ", loaded=" + search.loadedColumns()
            + ", surfaces=" + search.surfacesInRange() + ", safeFloors=" + search.safeFloors()
            + ", clearSpots=" + search.clearSpots() + ").");
        nextNoCandidateLogNanos.put(worldId, now + java.util.concurrent.TimeUnit.MINUTES.toNanos(5));
      }
      return;
    }
    Block candidate = search.block();
    // Persist the at-most-once reservation before changing the world. If storage is unavailable,
    // leave the world untouched; a crash after reservation may forfeit a kit, never duplicate it.
    if (!reserveIssuance(worldPath, worldId)) return;
    candidate.setType(Material.CHEST, false);
    BlockState state = candidate.getState();
    if (!(state instanceof Chest chest)) {
      candidate.setType(Material.AIR, false);
      return;
    }
    chest.getPersistentDataContainer().set(ownedKey, PersistentDataType.BYTE, (byte) 1);
    if (!chest.update(true, false)) {
      if (candidate.getType() == Material.CHEST) candidate.setType(Material.AIR, false);
      return;
    }
    remember(world, candidate);
    BlockState placedState = candidate.getState();
    if (placedState instanceof Chest placedChest) seedOnce(placedChest);
    retryAfterNanos.remove(worldId);
    nextNoCandidateLogNanos.remove(worldId);
  }

  private Block findOwnedNearSpawn(World world, int radius) {
    var spawn = world.getSpawnLocation();
    int centerX = spawn.getBlockX();
    int centerY = spawn.getBlockY();
    int centerZ = spawn.getBlockZ();
    int minChunkX = (centerX - radius) >> 4;
    int maxChunkX = (centerX + radius) >> 4;
    int minChunkZ = (centerZ - radius) >> 4;
    int maxChunkZ = (centerZ + radius) >> 4;
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
        for (var state : world.getChunkAt(chunkX, chunkZ).getTileEntities()) {
          if (!(state instanceof Chest chest)) continue;
          Block block = chest.getBlock();
          if (Math.abs(block.getX() - centerX) <= radius
              && Math.abs(block.getZ() - centerZ) <= radius
              && Math.abs(block.getY() - centerY) <= DISCOVERY_Y
              && isOwned(block)) return block;
        }
      }
    }
    return null;
  }

  private SearchResult findSafeCandidate(World world, int radius, int protectionRadius) {
    var spawn = world.getSpawnLocation();
    int centerX = spawn.getBlockX();
    int centerY = spawn.getBlockY();
    int centerZ = spawn.getBlockZ();
    List<int[]> offsets = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        if (dx * dx + dz * dz <= radius * radius
            && SpawnProtectionPolicy.outsideProtection(dx, dz, protectionRadius)) {
          offsets.add(new int[] {dx, dz});
        }
      }
    }
    offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));
    int loadedColumns = 0;
    int surfacesInRange = 0;
    int safeFloors = 0;
    int clearSpots = 0;
    for (int[] offset : offsets) {
      int x = centerX + offset[0];
      int z = centerZ + offset[1];
      if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
      loadedColumns++;
      int surfaceBlockY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
      Integer y = SpawnProtectionPolicy.surfacePlacementY(
          surfaceBlockY, centerY, world.getMinHeight(), world.getMaxHeight());
      if (y == null) continue;
      surfacesInRange++;
      Block floor = world.getBlockAt(x, y - 1, z);
      Material floorType = floor.getType();
      boolean leaves = floorType.name().equals("LEAVES") || floorType.name().endsWith("_LEAVES");
      boolean hazardous = UNSAFE_FLOORS.contains(floorType);
      if (!SpawnProtectionPolicy.safeFloor(floorType.isSolid(), floor.isLiquid(), leaves, hazardous)) continue;
      safeFloors++;
      Block spot = world.getBlockAt(x, y, z);
      Block headroom = world.getBlockAt(x, y + 1, z);
      if (!world.getWorldBorder().isInside(spot.getLocation())) continue;
      if (!TestSupplyPolicy.isSafePlacement(true, false,
          spot.getType().isAir(), headroom.getType().isAir(), nextToChest(spot))) continue;
      clearSpots++;
      return new SearchResult(spot, offsets.size(), loadedColumns, surfacesInRange, safeFloors, clearSpots);
    }
    return new SearchResult(null, offsets.size(), loadedColumns, surfacesInRange, safeFloors, clearSpots);
  }

  private boolean nextToChest(Block block) {
    for (Block adjacent : List.of(block.getRelative(1, 0, 0), block.getRelative(-1, 0, 0),
        block.getRelative(0, 0, 1), block.getRelative(0, 0, -1))) {
      if (!adjacent.getWorld().isChunkLoaded(adjacent.getX() >> 4, adjacent.getZ() >> 4)) return true;
      Material type = adjacent.getType();
      if (type == Material.CHEST || type == Material.TRAPPED_CHEST) return true;
    }
    return false;
  }

  private boolean isOwned(Block block) {
    if (block.getType() != Material.CHEST) return false;
    BlockState state = block.getState();
    return state instanceof Chest chest
        && chest.getPersistentDataContainer().has(ownedKey, PersistentDataType.BYTE);
  }

  private void remember(World world, Block chest) {
    String worldPath = "worlds." + world.getUID();
    store.set(worldPath + ".name", world.getName());
    store.set(worldPath + ".x", chest.getX());
    store.set(worldPath + ".y", chest.getY());
    store.set(worldPath + ".z", chest.getZ());
    try {
      store.save(storeFile);
    } catch (IOException error) {
      plugin.getLogger().log(Level.WARNING, "Could not persist test supply chest location; ownership marker remains on the chest.", error);
    }
  }

  private boolean reserveIssuance(String worldPath, String worldId) {
    store.set(worldPath + ".issued", true);
    try {
      store.save(storeFile);
      issuance.markIssued(worldId);
      return true;
    } catch (IOException error) {
      // Keep this manager fail-closed for the rest of the current run too.
      issuance.markIssued(worldId);
      plugin.getLogger().log(Level.WARNING,
          "Could not persist the test supply issuance tombstone; no chest was placed.", error);
      return false;
    }
  }

  private void seedOnce(Chest chest) {
    var inventory = chest.getBlockInventory();
    for (TestSupplyKit.Stack stack : TestSupplyKit.stacks()) {
      inventory.addItem(new ItemStack(stack.material(), stack.amount()));
    }
  }

  private record SearchResult(
      Block block, int columns, int loadedColumns, int surfacesInRange, int safeFloors, int clearSpots) {}
}
