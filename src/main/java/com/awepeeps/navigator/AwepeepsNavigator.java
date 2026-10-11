package com.awepeeps.navigator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** A shared directory of points of interest. The vanilla compass needle is never changed. */
public final class AwepeepsNavigator extends JavaPlugin implements Listener {
  private static final int PAGE_SIZE = 45;
  private NamespacedKey managed;
  private NamespacedKey uses;
  private NamespacedKey maxUses;
  private NamespacedKey legacyDestination;
  private NamespacedKey legacyWaypointId;
  private int capacity;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    managed = new NamespacedKey(this, "managed");
    uses = new NamespacedKey(this, "uses");
    maxUses = new NamespacedKey(this, "max_uses");
    legacyDestination = new NamespacedKey(this, "destination");
    legacyWaypointId = new NamespacedKey(this, "waypoint_id");
    capacity = readCapacity();
    migrateIds();
    Objects.requireNonNull(getCommand("navigator")).setExecutor(this);
    Bukkit.getPluginManager().registerEvents(this, this);
  }

  @Override
  public boolean onCommand(
      org.bukkit.command.CommandSender sender,
      org.bukkit.command.Command command,
      String label,
      String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("This command requires a player.");
      return true;
    }
    if (args.length == 0) {
      ItemStack item = compass(player);
      if (!NavigatorPolicy.canUseCompass(item != null, item == null ? 0 : item.getAmount())) {
        player.sendMessage(ChatColor.RED + "Hold exactly one ordinary compass in either hand.");
        return true;
      }
      EquipmentSlot hand = selectedHand(player);
      cleanLegacy(item);
      writeHand(player, hand, item);
      openMenu(player, 0, item.clone(), hand);
      return true;
    }
    if (args[0].equalsIgnoreCase("set")) {
      if (args.length < 2 || args.length > 3) {
        usage(player);
      } else {
        create(player, args[1], args.length == 3 ? args[2] : "General");
      }
      return true;
    }
    if (args[0].equalsIgnoreCase("clear") && args.length == 1) {
      player.sendMessage(
          ChatColor.YELLOW + "Navigator no longer binds compasses; there is nothing to clear.");
      return true;
    }
    if (args[0].equalsIgnoreCase("remove") && args.length == 2) {
      remove(player, args[1]);
      return true;
    }
    if (args[0].equalsIgnoreCase("item") && (args.length == 1 || args.length == 2)) {
      if (!player.hasPermission("awepeepsnavigator.admin")) {
        player.sendMessage(ChatColor.RED + "That subcommand is for administrators.");
        return true;
      }
      Player recipient = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : player;
      if (recipient == null) {
        player.sendMessage(ChatColor.RED + "Player is not online.");
      } else {
        recipient.getInventory().addItem(new ItemStack(Material.COMPASS));
        player.sendMessage(
            ChatColor.GREEN + "Gave a regular compass to " + recipient.getName() + ".");
      }
      return true;
    }
    usage(player);
    return true;
  }

  private void usage(Player player) {
    player.sendMessage(
        ChatColor.YELLOW
            + "/navigator | /navigator set <name> [group] | /navigator clear | /navigator remove"
            + " <name> | /navigator item [player]");
  }

  private void create(Player player, String name, String group) {
    if (!player.hasPermission("awepeepsnavigator.create")) {
      player.sendMessage(ChatColor.RED + "You cannot create points of interest.");
      return;
    }
    ItemStack item = compass(player);
    if (!NavigatorPolicy.canUseCompass(item != null, item == null ? 0 : item.getAmount())) {
      player.sendMessage(ChatColor.RED + "Hold exactly one ordinary compass in either hand.");
      return;
    }
    EquipmentSlot hand = selectedHand(player);
    cleanLegacy(item);
    Block target = player.getTargetBlockExact(5);
    if (target == null || target.getType() != Material.LODESTONE) {
      player.sendMessage(ChatColor.RED + "Look directly at a lodestone within 5 blocks to mark a point.");
      writeHand(player, hand, item);
      return;
    }
    if (!valid(name) || !valid(group)) {
      player.sendMessage(
          ChatColor.RED
              + "Names and groups must be 1–32 letters, numbers, underscores, or hyphens.");
      writeHand(player, hand, item);
      return;
    }
    if (nameExists(name)) {
      player.sendMessage(ChatColor.RED + "A point of interest with that name already exists.");
      writeHand(player, hand, item);
      return;
    }
    if (managed(item) && remaining(item) <= 0) {
      player.sendMessage(ChatColor.RED + "This compass has no uses remaining.");
      writeHand(player, hand, item);
      return;
    }

    String path = "teleports." + name;
    var location = target.getLocation();
    UUID id = UUID.randomUUID();
    getConfig().set(path + ".id", id.toString());
    getConfig().set(path + ".creator", player.getUniqueId().toString());
    getConfig().set(path + ".world", location.getWorld().getName());
    getConfig().set(path + ".x", location.getBlockX());
    getConfig().set(path + ".y", location.getBlockY());
    getConfig().set(path + ".z", location.getBlockZ());
    getConfig().set(path + ".group", group);
    if (!saveChecked()) {
      getConfig().set(path, null);
      player.sendMessage(
          ChatColor.RED + "Could not save point of interest; no compass use charged.");
      writeHand(player, hand, item);
      return;
    }

    if (!managed(item)) initialize(item);
    boolean survived = charge(item);
    writeHand(player, hand, item);
    player.sendMessage(
        survived
            ? ChatColor.GREEN + "Created point of interest " + name + "."
            : ChatColor.YELLOW + "Point of interest created; the compass broke on its final use.");
  }

  private void remove(Player player, String name) {
    Destination destination = find(name);
    if (destination == null) {
      player.sendMessage(ChatColor.RED + "Unknown point of interest.");
      return;
    }
    if (!player.hasPermission("awepeepsnavigator.admin")
        && !player.getUniqueId().toString().equals(destination.creator)) {
      player.sendMessage(ChatColor.RED + "You can remove only points you created.");
      return;
    }
    String path = "teleports." + destination.name;
    Map<String, Object> old =
        new LinkedHashMap<>(getConfig().getConfigurationSection(path).getValues(true));
    getConfig().set(path, null);
    if (!saveChecked()) {
      getConfig().set(path, old);
      player.sendMessage(ChatColor.RED + "Could not save config; removal rolled back.");
      return;
    }
    player.sendMessage(ChatColor.GREEN + "Removed point of interest " + destination.name + ".");
  }

  private boolean saveChecked() {
    try {
      getConfig().save(getDataFolder().toPath().resolve("config.yml").toFile());
      return true;
    } catch (IOException exception) {
      getLogger().severe("Could not save config.yml: " + exception.getMessage());
      return false;
    }
  }

  @EventHandler
  public void onUse(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_AIR
        && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    boolean clickedLodestone =
        event.getClickedBlock() != null
            && event.getClickedBlock().getType() == Material.LODESTONE;
    ItemStack mainHand = player.getInventory().getItemInMainHand();
    boolean offhandFallback =
        event.getHand() == EquipmentSlot.OFF_HAND
            && (plain(mainHand) || isLodestoneCompass(mainHand));
    if (NavigatorPolicy.shouldPassRightClick(
        clickedLodestone, isLodestoneCompass(item), offhandFallback)) return;
    if (!plain(item) || item.getAmount() != 1) return;
    cleanLegacy(item);
    event.setCancelled(true);
    writeHand(player, event.getHand(), item);
    ItemStack snapshot = item.clone();
    EquipmentSlot hand = event.getHand();
    Bukkit.getScheduler()
        .runTask(
            this,
            () -> {
              if (player.isOnline()) openMenu(player, 0, snapshot, hand);
            });
  }

  @EventHandler
  public void click(InventoryClickEvent event) {
    if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (event.getClickedInventory() != event.getView().getTopInventory()) return;
    int slot = event.getRawSlot();
    if (slot == 49) {
      Bukkit.getScheduler().runTask(this, player::closeInventory);
    } else if (slot == 48) {
      player.sendMessage(
          ChatColor.YELLOW + "Create a point of interest with: /navigator set <name> [group]");
      player.sendMessage(
          ChatColor.GRAY
              + "Look at a lodestone within 5 blocks. Hold one ordinary compass; a successful creation spends one use.");
      Bukkit.getScheduler().runTask(this, player::closeInventory);
    } else if (slot == 45 && holder.page > 0) {
      scheduleMenu(player, holder.page - 1, holder.snapshot, holder.hand);
    } else if (slot == 53 && (holder.page + 1) * PAGE_SIZE < holder.entries.size()) {
      scheduleMenu(player, holder.page + 1, holder.snapshot, holder.hand);
    } else if (slot >= 0 && slot < PAGE_SIZE && slot + holder.page * PAGE_SIZE < holder.entries.size()) {
      Destination destination = holder.entries.get(slot + holder.page * PAGE_SIZE);
      Bukkit.getScheduler().runTask(this, () -> select(player, destination, holder));
    }
  }

  @EventHandler
  public void drag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
  }

  private void scheduleMenu(Player player, int page, ItemStack snapshot, EquipmentSlot hand) {
    Bukkit.getScheduler().runTask(this, () -> openMenu(player, page, snapshot, hand));
  }

  private void openMenu(Player player, int page, ItemStack snapshot, EquipmentSlot hand) {
    if (!player.isOnline()) return;
    ItemStack held = hand == EquipmentSlot.HAND
        ? player.getInventory().getItemInMainHand()
        : player.getInventory().getItemInOffHand();
    if (!plain(held) || held.getAmount() != 1 || snapshot == null || !held.isSimilar(snapshot)) return;
    List<Destination> entries = destinations();
    Holder holder = new Holder(page, entries, snapshot, hand);
    Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_GREEN + "Navigator");
    holder.inventory = inventory;
    for (int slot = 0; slot < PAGE_SIZE && page * PAGE_SIZE + slot < entries.size(); slot++) {
      Destination destination = entries.get(page * PAGE_SIZE + slot);
      ItemStack icon = new ItemStack(Material.COMPASS);
      ItemMeta meta = icon.getItemMeta();
      meta.setDisplayName(
          ChatColor.GREEN + "[" + destination.group + "] " + ChatColor.GOLD + destination.name);
      meta.setLore(
          List.of(
              ChatColor.GRAY
                  + destination.world
                  + " "
                  + destination.x
                  + ", "
                  + destination.y
                  + ", "
                  + destination.z));
      icon.setItemMeta(meta);
      inventory.setItem(slot, icon);
    }
    inventory.setItem(45, button(Material.ARROW, "Previous page"));
    inventory.setItem(49, button(Material.BARRIER, "Close"));
    inventory.setItem(53, button(Material.ARROW, "Next page"));
    if (player.hasPermission("awepeepsnavigator.create")) {
      inventory.setItem(
          48, button(Material.EMERALD, "Create: /navigator set <name> [group]"));
    }
    player.openInventory(inventory);
    if (entries.isEmpty()) player.sendMessage(ChatColor.YELLOW + "No points of interest configured.");
  }

  private ItemStack button(Material material, String text) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(ChatColor.YELLOW + text);
    item.setItemMeta(meta);
    return item;
  }

  private void select(Player player, Destination destination, Holder expected) {
    if (!player.isOnline()
        || player.getOpenInventory().getTopInventory().getHolder() != expected) return;
    Destination current = findById(destination.id);
    player.closeInventory();
    if (current == null) return;
    player.sendMessage(
        ChatColor.GREEN + "[" + current.group + "] " + current.name);
    player.sendMessage(
        ChatColor.GRAY
            + current.world
            + " "
            + current.x
            + ", "
            + current.y
            + ", "
            + current.z);
  }

  private ItemStack compass(Player player) {
    ItemStack main = player.getInventory().getItemInMainHand();
    if (plain(main)) return main;
    ItemStack off = player.getInventory().getItemInOffHand();
    return plain(off) ? off : null;
  }

  private boolean plain(ItemStack item) {
    return item != null && item.getType() == Material.COMPASS && !isLodestoneCompass(item);
  }

  private boolean isLodestoneCompass(ItemStack item) {
    if (item == null
        || item.getType() != Material.COMPASS
        || !(item.getItemMeta() instanceof CompassMeta meta)) return false;
    boolean hasTarget = meta.hasLodestone();
    boolean hasTrackingComponent = meta.serialize().containsKey("LodestoneTracked");
    return NavigatorPolicy.isLodestoneCompass(hasTarget, hasTrackingComponent)
        || meta.isLodestoneTracked();
  }

  private boolean managed(ItemStack item) {
    return item != null
        && item.getItemMeta() != null
        && item.getItemMeta()
            .getPersistentDataContainer()
            .has(managed, PersistentDataType.BYTE);
  }

  private String get(ItemStack item, NamespacedKey key) {
    return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
  }

  private int itemCapacity(ItemStack item) {
    Integer stored =
        item.getItemMeta().getPersistentDataContainer().get(maxUses, PersistentDataType.INTEGER);
    return NavigationMath.safeCapacity(stored, capacity);
  }

  private int remaining(ItemStack item) {
    Integer stored =
        item.getItemMeta().getPersistentDataContainer().get(uses, PersistentDataType.INTEGER);
    return NavigationMath.safeRemaining(managed(item), stored, itemCapacity(item));
  }

  private void initialize(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    PersistentDataContainer data = meta.getPersistentDataContainer();
    data.set(managed, PersistentDataType.BYTE, (byte) 1);
    data.set(uses, PersistentDataType.INTEGER, capacity);
    data.set(maxUses, PersistentDataType.INTEGER, capacity);
    item.setAmount(1);
    try {
      meta.setMaxStackSize(1);
      if (meta instanceof Damageable damageable) damageable.setMaxDamage(capacity);
    } catch (UnsupportedOperationException ignored) {
      // Some server implementations do not expose custom item durability.
    }
    item.setItemMeta(meta);
    lore(item);
  }

  private boolean charge(ItemStack item) {
    int after = NavigatorPolicy.remainingAfterCreation(true, remaining(item));
    if (after < 0) return false;
    ItemMeta meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(uses, PersistentDataType.INTEGER, after);
    item.setItemMeta(meta);
    lore(item);
    if (after == 0) {
      item.setAmount(0);
      return false;
    }
    return true;
  }

  private void lore(ItemStack item) {
    ItemMeta meta = item.getItemMeta();
    int itemCapacity = itemCapacity(item);
    int left = remaining(item);
    List<String> lines = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
    lines.removeIf(
        line -> {
          String plain = ChatColor.stripColor(line);
          return plain.startsWith("Navigator uses:") || plain.startsWith("Navigator bound:");
        });
    lines.add(ChatColor.GRAY + "Navigator uses: " + left + "/" + itemCapacity);
    meta.setLore(lines);
    if (meta instanceof Damageable damageable) {
      try {
        damageable.setMaxDamage(itemCapacity);
        damageable.setDamage(Math.max(0, itemCapacity - left));
      } catch (UnsupportedOperationException ignored) {
        // PDC remains the authoritative use counter.
      }
    }
    item.setItemMeta(meta);
  }

  private void cleanLegacy(ItemStack item) {
    if (!plain(item) || item.getItemMeta() == null) return;
    ItemMeta meta = item.getItemMeta();
    PersistentDataContainer data = meta.getPersistentDataContainer();
    boolean hadLegacy = data.has(legacyDestination, PersistentDataType.STRING)
        || data.has(legacyWaypointId, PersistentDataType.STRING);
    data.remove(legacyDestination);
    data.remove(legacyWaypointId);
    if (meta.hasLore()) {
      List<String> lines = new ArrayList<>(meta.getLore());
      boolean removed =
          lines.removeIf(line -> ChatColor.stripColor(line).startsWith("Navigator bound:"));
      if (removed) meta.setLore(lines.isEmpty() ? null : lines);
      hadLegacy |= removed;
    }
    if (hadLegacy) item.setItemMeta(meta);
  }

  private void writeHand(Player player, EquipmentSlot hand, ItemStack item) {
    if (hand == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(item);
    else player.getInventory().setItemInOffHand(item);
  }

  private List<Destination> destinations() {
    List<Destination> result = new ArrayList<>();
    ConfigurationSection section = getConfig().getConfigurationSection("teleports");
    if (section == null) return result;
    for (String name : section.getKeys(false)) {
      ConfigurationSection point = section.getConfigurationSection(name);
      if (point == null
          || !valid(name)
          || !NavigationMath.isFiniteCoordinate(point.get("x"))
          || !NavigationMath.isFiniteCoordinate(point.get("y"))
          || !NavigationMath.isFiniteCoordinate(point.get("z"))) continue;
      String world = point.getString("world");
      String id = point.getString("id");
      if (world == null || id == null) continue;
      result.add(
          new Destination(
              name,
              world,
              point.getDouble("x"),
              point.getDouble("y"),
              point.getDouble("z"),
              point.getString("group", "General"),
              point.getString("creator"),
              id));
    }
    result.sort(
        Comparator.comparing(
                (Destination point) -> point.group, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(point -> point.name, String.CASE_INSENSITIVE_ORDER));
    return result;
  }

  private Destination find(String name) {
    return destinations().stream()
        .filter(point -> point.name.equalsIgnoreCase(name))
        .findFirst()
        .orElse(null);
  }

  private Destination findById(String id) {
    return destinations().stream()
        .filter(point -> point.id.equals(id))
        .findFirst()
        .orElse(null);
  }

  private boolean nameExists(String name) {
    ConfigurationSection points = getConfig().getConfigurationSection("teleports");
    return points != null
        && points.getKeys(false).stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
  }

  private void migrateIds() {
    ConfigurationSection points = getConfig().getConfigurationSection("teleports");
    if (points == null) return;
    boolean changed = false;
    for (String name : points.getKeys(false)) {
      ConfigurationSection point = points.getConfigurationSection(name);
      if (point != null && !point.contains("id")) {
        point.set("id", UUID.randomUUID().toString());
        changed = true;
      }
    }
    if (changed && !saveChecked()) {
      throw new IllegalStateException("Could not save point ID migration");
    }
  }

  private int readCapacity() {
    int configured = getConfig().getInt("compass-max-uses", 64);
    return configured > 0 ? Math.min(configured, 1_000_000) : 64;
  }

  private boolean valid(String value) {
    return value != null && value.matches("[A-Za-z0-9_-]{1,32}");
  }

  private EquipmentSlot selectedHand(Player player) {
    return plain(player.getInventory().getItemInMainHand())
        ? EquipmentSlot.HAND
        : EquipmentSlot.OFF_HAND;
  }

  private record Destination(
      String name,
      String world,
      double x,
      double y,
      double z,
      String group,
      String creator,
      String id) {}

  private static final class Holder implements InventoryHolder {
    final int page;
    final List<Destination> entries;
    final ItemStack snapshot;
    final EquipmentSlot hand;
    Inventory inventory;

    Holder(int page, List<Destination> entries, ItemStack snapshot, EquipmentSlot hand) {
      this.page = page;
      this.entries = List.copyOf(entries);
      this.snapshot = snapshot == null ? null : snapshot.clone();
      this.hand = hand;
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }
  }
}
