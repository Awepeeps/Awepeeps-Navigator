package com.awepeeps.navigator;

import java.util.List;
import org.bukkit.Material;

/** Fixed one-time test supplies, deliberately separate from Navigator compass management. */
final class TestSupplyKit {
  private static final List<Stack> STACKS = List.of(
      new Stack(Material.LODESTONE, 8),
      new Stack(Material.RED_BED, 4),
      new Stack(Material.COMPASS, 16),
      new Stack(Material.BRUSH, 4),
      new Stack(Material.OAK_LOG, 64),
      new Stack(Material.OAK_PLANKS, 64),
      new Stack(Material.COBBLESTONE, 64),
      new Stack(Material.CRAFTING_TABLE, 1),
      new Stack(Material.STICK, 64),
      new Stack(Material.TORCH, 64),
      new Stack(Material.DIAMOND, 16),
      new Stack(Material.IRON_INGOT, 64));

  private TestSupplyKit() {}

  static List<Stack> stacks() {
    return STACKS;
  }

  record Stack(Material material, int amount) {}
}
