package com.awepeeps.navigator;

import java.io.IOException;
import java.util.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class AwepeepsNavigator extends JavaPlugin implements Listener {
  private static final int PAGE_SIZE = 45;
  private NamespacedKey managed, boundName, boundId, uses, maxUses;
  private double radius;
  private int capacity;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    managed = new NamespacedKey(this, "managed");
    boundName = new NamespacedKey(this, "destination");
    boundId = new NamespacedKey(this, "waypoint_id");
    uses = new NamespacedKey(this, "uses");
    maxUses = new NamespacedKey(this, "max_uses");
    radius = readRadius();
    capacity = readCapacity();
    migrateIds();
    Objects.requireNonNull(getCommand("navigator")).setExecutor(this);
    Bukkit.getPluginManager().registerEvents(this, this);
    Bukkit.getScheduler().runTaskTimer(this, this::updateTracking, 20, 20);
  }

  @Override
  public void onDisable() {
    for (Player p : Bukkit.getOnlinePlayers()) resetCompass(p);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
    if (!(sender instanceof Player p)) {
      sender.sendMessage("This command requires a player.");
      return true;
    }
    if (a.length == 0) {
      ItemStack held = compass(p);
      if (held == null || held.getAmount() != 1) {
        p.sendMessage(ChatColor.RED + "Hold exactly one regular compass in your main or off hand.");
        return true;
      }
      openMenu(p, 0, held.clone(), selectedHand(p));
      return true;
    }
    if (a[0].equalsIgnoreCase("set")) {
      if (a.length < 2 || a.length > 3) {
        usage(p);
        return true;
      }
      create(p, a[1], a.length == 3 ? a[2] : "General");
      return true;
    }
    if (a[0].equalsIgnoreCase("clear") && a.length == 1) {
      ItemStack it = compass(p);
      if (it == null || it.getAmount() != 1) {
        p.sendMessage(ChatColor.RED + "Hold exactly one compass to unbind.");
        return true;
      }
      EquipmentSlot hand = selectedHand(p);
      if (managed(it)) clearBinding(it);
      writeHand(p, hand, it);
      resetCompass(p);
      p.sendMessage(ChatColor.GREEN + "Compass unbound. Wear was not restored.");
      return true;
    }
    if (a[0].equalsIgnoreCase("remove") && a.length == 2) {
      Destination d = find(a[1]);
      if (d == null) {
        p.sendMessage(ChatColor.RED + "Unknown destination.");
        return true;
      }
      if (!p.hasPermission("awepeepsnavigator.admin")
          && !p.getUniqueId().toString().equals(d.creator)) {
        p.sendMessage(ChatColor.RED + "You can remove only your own destinations.");
        return true;
      }
      Map<String, Object> old =
          new LinkedHashMap<>(
              getConfig().getConfigurationSection("teleports." + d.name).getValues(true));
      getConfig().set("teleports." + d.name, null);
      if (!saveChecked()) {
        getConfig().set("teleports." + d.name, old);
        p.sendMessage(ChatColor.RED + "Could not save config; removal rolled back.");
        return true;
      }
      for (Player online : Bukkit.getOnlinePlayers()) {
        ItemStack held = compass(online);
        if (managed(held) && Objects.equals(get(held, boundId), d.id)) resetCompass(online);
      }
      p.sendMessage(
          ChatColor.GREEN + "Removed " + d.name + ". Bound compasses will show unavailable.");
      return true;
    }
    if (!p.hasPermission("awepeepsnavigator.admin")) {
      p.sendMessage(ChatColor.RED + "That subcommand is for administrators.");
      return true;
    }
    if (a[0].equalsIgnoreCase("item") && (a.length == 1 || a.length == 2)) {
      Player to = a.length == 2 ? Bukkit.getPlayerExact(a[1]) : p;
      if (to == null) {
        p.sendMessage(ChatColor.RED + "Player is not online.");
        return true;
      }
      to.getInventory().addItem(new ItemStack(Material.COMPASS));
      p.sendMessage(ChatColor.GREEN + "Gave a regular compass to " + to.getName() + ".");
      return true;
    }
    usage(p);
    return true;
  }

  private void usage(Player p) {
    p.sendMessage(
        ChatColor.YELLOW
            + "/navigator | /navigator set <name> [group] | /navigator clear | /navigator remove"
            + " <name> | /navigator item [player]");
  }

  private void create(Player p, String name, String group) {
    if (!p.hasPermission("awepeepsnavigator.create")) {
      p.sendMessage(ChatColor.RED + "You cannot create waypoints.");
      return;
    }
    ItemStack it = compass(p);
    if (it == null || it.getAmount() != 1) {
      p.sendMessage(
          ChatColor.RED
              + "Hold exactly one regular compass (main hand preferred, off hand fallback).");
      return;
    }
    if (!valid(name) || !valid(group)) {
      p.sendMessage(
          ChatColor.RED
              + "Names and groups must be 1–32 letters, numbers, underscores, or hyphens.");
      return;
    }
    if (nameExists(name)) {
      p.sendMessage(ChatColor.RED + "A waypoint with that name already exists.");
      return;
    }
    if (managed(it) && remaining(it) <= 0) {
      p.sendMessage(ChatColor.RED + "This compass has no uses remaining.");
      return;
    }
    String path = "teleports." + name;
    Location l = p.getLocation();
    UUID id = UUID.randomUUID();
    getConfig().set(path + ".id", id.toString());
    getConfig().set(path + ".creator", p.getUniqueId().toString());
    getConfig().set(path + ".world", l.getWorld().getName());
    getConfig().set(path + ".x", l.getX());
    getConfig().set(path + ".y", l.getY());
    getConfig().set(path + ".z", l.getZ());
    getConfig().set(path + ".yaw", l.getYaw());
    getConfig().set(path + ".pitch", l.getPitch());
    getConfig().set(path + ".group", group);
    if (!saveChecked()) {
      getConfig().set(path, null);
      p.sendMessage(ChatColor.RED + "Could not save waypoint; no compass use charged.");
      return;
    }
    EquipmentSlot hand = selectedHand(p);
    if (!managed(it)) initialize(it);
    bind(it, name, id.toString());
    boolean survived = charge(it);
    writeHand(p, hand, it);
    if (!survived) {
      p.sendMessage(
          ChatColor.YELLOW + "Waypoint created, but that compass broke on its final use.");
      return;
    }
    p.sendMessage(ChatColor.GREEN + "Created and bound waypoint " + name + ".");
  }

  private boolean saveChecked() {
    try {
      getConfig().save(getDataFolder().toPath().resolve("config.yml").toFile());
      return true;
    } catch (IOException ex) {
      getLogger().severe("Could not save config.yml: " + ex.getMessage());
      return false;
    }
  }

  @EventHandler
  public void onUse(PlayerInteractEvent e) {
    if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
      return;
    Player p = e.getPlayer();
    ItemStack i = e.getItem();
    if (!plain(i) || isLodestone(i) || i.getAmount() != 1) return;
    if (e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.LODESTONE) return;
    if (e.getHand() == EquipmentSlot.OFF_HAND && plain(p.getInventory().getItemInMainHand()))
      return;
    e.setCancelled(true);
    EquipmentSlot hand = e.getHand();
    ItemStack snapshot = i.clone();
    Bukkit.getScheduler()
        .runTask(
            this,
            () -> {
              if (p.isOnline()) openMenu(p, 0, snapshot, hand);
            });
  }

  @EventHandler
  public void click(InventoryClickEvent e) {
    if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
    e.setCancelled(true);
    if (!(e.getWhoClicked() instanceof Player p)) return;
    if (e.getClickedInventory() != e.getView().getTopInventory()) return;
    int s = e.getRawSlot();
    if (s == 49) {
      Bukkit.getScheduler().runTask(this, p::closeInventory);
      return;
    }
    if (s == 48) {
      p.sendMessage(ChatColor.YELLOW + "Create with: /navigator set <name> [group]");
      p.sendMessage(
          ChatColor.GRAY
              + "Hold exactly one regular compass; creation binds it and spends one use.");
      Bukkit.getScheduler().runTask(this, p::closeInventory);
      return;
    }
    if (s == 45 && h.page > 0) {
      scheduleMenu(p, h.page - 1, h.snapshot, h.hand);
      return;
    }
    if (s == 53 && (h.page + 1) * PAGE_SIZE < h.entries.size()) {
      scheduleMenu(p, h.page + 1, h.snapshot, h.hand);
      return;
    }
    if (s >= 0 && s < PAGE_SIZE && s + h.page * PAGE_SIZE < h.entries.size()) {
      Destination d = h.entries.get(s + h.page * PAGE_SIZE);
      Bukkit.getScheduler().runTask(this, () -> select(p, d, h.snapshot, h.hand, h));
    }
  }

  @EventHandler
  public void drag(InventoryDragEvent e) {
    if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
  }

  @EventHandler
  public void close(PlayerQuitEvent e) {
    resetCompass(e.getPlayer());
  }

  private void scheduleMenu(Player p, int page, ItemStack snapshot, EquipmentSlot hand) {
    Bukkit.getScheduler().runTask(this, () -> openMenu(p, page, snapshot, hand));
  }

  private void openMenu(Player p, int page, ItemStack snapshot, EquipmentSlot hand) {
    if (!p.isOnline()) return;
    List<Destination> ds = destinations();
    Holder h = new Holder(page, ds, snapshot, hand);
    Inventory inv = Bukkit.createInventory(h, 54, ChatColor.DARK_GREEN + "Navigator");
    h.inv = inv;
    for (int s = 0; s < PAGE_SIZE && page * PAGE_SIZE + s < ds.size(); s++) {
      Destination d = ds.get(page * PAGE_SIZE + s);
      ItemStack icon = new ItemStack(Material.COMPASS);
      ItemMeta m = icon.getItemMeta();
      m.setDisplayName(ChatColor.GREEN + "[" + d.group + "] " + ChatColor.GOLD + d.name);
      m.setLore(
          List.of(
              ChatColor.GRAY + d.world + " " + d.x + ", " + d.y + ", " + d.z,
              ChatColor.DARK_GRAY + "Select to navigate"));
      icon.setItemMeta(m);
      inv.setItem(s, icon);
    }
    inv.setItem(45, button(Material.ARROW, "Previous page"));
    inv.setItem(49, button(Material.BARRIER, "Close"));
    inv.setItem(53, button(Material.ARROW, "Next page"));
    if (p.hasPermission("awepeepsnavigator.create"))
      inv.setItem(48, button(Material.EMERALD, "Create waypoint: /navigator set <name> [group]"));
    p.openInventory(inv);
    if (ds.isEmpty()) p.sendMessage(ChatColor.YELLOW + "No waypoints configured.");
  }

  private ItemStack button(Material mat, String text) {
    ItemStack i = new ItemStack(mat);
    ItemMeta m = i.getItemMeta();
    m.setDisplayName(ChatColor.YELLOW + text);
    i.setItemMeta(m);
    return i;
  }

  private void select(
      Player p, Destination d, ItemStack snap, EquipmentSlot hand, Holder expected) {
    if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != expected) return;
    ItemStack it =
        hand == EquipmentSlot.HAND
            ? p.getInventory().getItemInMainHand()
            : p.getInventory().getItemInOffHand();
    if (!sameItem(it, snap)) {
      p.closeInventory();
      p.sendMessage(
          ChatColor.RED + "Keep the same single compass in the same hand while selecting.");
      return;
    }
    Destination current = findById(d.id);
    if (current == null) {
      resetCompass(p);
      p.closeInventory();
      p.sendMessage(ChatColor.YELLOW + "That waypoint changed; reopen Navigator.");
      return;
    }
    String prior = get(it, boundId);
    if (!NavigationMath.shouldChargeBinding(prior, d.id)) {
      p.closeInventory();
      p.sendMessage(ChatColor.GREEN + "Already bound to " + d.name + "; no use charged.");
      return;
    }
    if (managed(it) && remaining(it) <= 0) {
      p.sendMessage(ChatColor.RED + "This compass has no uses remaining.");
      return;
    }
    Location here = p.getLocation();
    boolean sameWorld = p.getWorld().getName().equals(d.world);
    if (!NavigationMath.canBind(
        sameWorld, d.x - here.getX(), d.y - here.getY(), d.z - here.getZ(), radius)) {
      if (!sameWorld) resetCompass(p);
      p.sendMessage(
          ChatColor.RED
              + (sameWorld
                  ? "Waypoint is outside the " + radius + " block link radius; no use charged."
                  : "That waypoint is in another world; no use charged."));
      return;
    }
    if (!managed(it)) initialize(it);
    bind(it, d.name, d.id);
    boolean survived = charge(it);
    writeHand(p, hand, it);
    p.closeInventory();
    p.sendMessage(
        survived
            ? ChatColor.GREEN + "Compass bound to " + d.name + "."
            : ChatColor.YELLOW + "Compass bound to " + d.name + ", then broke on its final use.");
  }

  private void updateTracking() {
    for (Player p : Bukkit.getOnlinePlayers()) {
      EquipmentSlot hand = selectedHand(p);
      ItemStack i =
          hand == EquipmentSlot.HAND
              ? p.getInventory().getItemInMainHand()
              : p.getInventory().getItemInOffHand();
      if (!managed(i)) {
        resetCompass(p);
        continue;
      }
      syncDamage(i);
      writeHand(p, hand, i);
      String id = get(i, boundId), name = get(i, boundName);
      if (id == null || id.isBlank()) {
        resetCompass(p);
        continue;
      }
      Destination d = findById(id);
      if (d == null) {
        resetCompass(p);
        action(p, ChatColor.RED + "Waypoint unavailable: " + name);
        continue;
      }
      if (!p.getWorld().getName().equals(d.world)) {
        resetCompass(p);
        action(p, ChatColor.YELLOW + name + " is in another world");
        continue;
      }
      Location target = new Location(p.getWorld(), d.x, d.y, d.z);
      p.setCompassTarget(target);
      Location here = p.getLocation();
      double dx = d.x - here.getX(),
          dz = d.z - here.getZ(),
          dist = NavigationMath.horizontalDistance(dx, dz);
      String dir =
          dist < 1 ? "HERE" : NavigationMath.direction(NavigationMath.relativeBearing(0, dx, dz));
      action(
          p,
          ChatColor.GOLD
              + name
              + ChatColor.GRAY
              + "  "
              + dir
              + "  "
              + Math.round(dist)
              + " blocks");
    }
  }

  private void action(Player p, String s) {
    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(s));
  }

  private void resetCompass(Player p) {
    p.setCompassTarget(p.getWorld().getSpawnLocation());
  }

  private ItemStack compass(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    if (plain(main)) return main;
    ItemStack off = p.getInventory().getItemInOffHand();
    return plain(off) ? off : null;
  }

  private boolean plain(ItemStack i) {
    return i != null && i.getType() == Material.COMPASS && !isLodestone(i);
  }

  private boolean isLodestone(ItemStack i) {
    return i.getItemMeta() instanceof CompassMeta m && m.hasLodestone();
  }

  private boolean sameItem(ItemStack now, ItemStack snap) {
    return now != null
        && snap != null
        && now.getAmount() == 1
        && snap.getAmount() == 1
        && now.isSimilar(snap)
        && Objects.equals(get(now, boundId), get(snap, boundId))
        && remaining(now) == remaining(snap);
  }

  private boolean managed(ItemStack i) {
    return i != null
        && i.getItemMeta() != null
        && i.getItemMeta().getPersistentDataContainer().has(managed, PersistentDataType.BYTE);
  }

  private String get(ItemStack i, NamespacedKey k) {
    return i.getItemMeta().getPersistentDataContainer().get(k, PersistentDataType.STRING);
  }

  private int itemCapacity(ItemStack i) {
    Integer n =
        i.getItemMeta().getPersistentDataContainer().get(maxUses, PersistentDataType.INTEGER);
    return NavigationMath.safeCapacity(n, capacity);
  }

  private int remaining(ItemStack i) {
    Integer n = i.getItemMeta().getPersistentDataContainer().get(uses, PersistentDataType.INTEGER);
    return NavigationMath.safeRemaining(managed(i), n, itemCapacity(i));
  }

  private void initialize(ItemStack i) {
    ItemMeta m = i.getItemMeta();
    PersistentDataContainer c = m.getPersistentDataContainer();
    c.set(managed, PersistentDataType.BYTE, (byte) 1);
    c.set(uses, PersistentDataType.INTEGER, capacity);
    c.set(maxUses, PersistentDataType.INTEGER, capacity);
    i.setAmount(1);
    try {
      m.setMaxStackSize(1);
      if (m instanceof Damageable d) d.setMaxDamage(capacity);
    } catch (Exception ignored) {
    }
    i.setItemMeta(m);
    lore(i);
  }

  private void bind(ItemStack i, String name, String id) {
    ItemMeta m = i.getItemMeta();
    m.getPersistentDataContainer().set(boundName, PersistentDataType.STRING, name);
    m.getPersistentDataContainer().set(boundId, PersistentDataType.STRING, id);
    i.setItemMeta(m);
    lore(i);
  }

  private void clearBinding(ItemStack i) {
    ItemMeta m = i.getItemMeta();
    m.getPersistentDataContainer().remove(boundName);
    m.getPersistentDataContainer().remove(boundId);
    i.setItemMeta(m);
    lore(i);
  }

  private boolean charge(ItemStack i) {
    int n = NavigationMath.afterSuccessfulUse(remaining(i));
    if (n < 0) return false;
    ItemMeta m = i.getItemMeta();
    m.getPersistentDataContainer().set(uses, PersistentDataType.INTEGER, n);
    i.setItemMeta(m);
    lore(i);
    if (n == 0) {
      i.setAmount(0);
      return false;
    }
    return true;
  }

  private void lore(ItemStack i) {
    ItemMeta m = i.getItemMeta();
    int cap = itemCapacity(i), left = remaining(i);
    List<String> l = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
    l.removeIf(
        s ->
            ChatColor.stripColor(s).startsWith("Navigator uses:")
                || ChatColor.stripColor(s).startsWith("Navigator bound:"));
    l.add(ChatColor.GRAY + "Navigator uses: " + left + "/" + cap);
    String n = get(i, boundName);
    if (n != null) l.add(ChatColor.GRAY + "Navigator bound: " + n);
    m.setLore(l);
    if (m instanceof Damageable d) {
      try {
        d.setMaxDamage(cap);
        d.setDamage(Math.max(0, cap - left));
      } catch (UnsupportedOperationException ignored) {
      }
    }
    i.setItemMeta(m);
  }

  private void syncDamage(ItemStack i) {
    ItemMeta meta = i.getItemMeta();
    if (!(meta instanceof Damageable d)) return;
    int cap = itemCapacity(i), damage = Math.max(0, cap - remaining(i));
    if (!d.hasMaxDamage() || d.getMaxDamage() != cap || d.getDamage() != damage) {
      d.setMaxDamage(cap);
      d.setDamage(damage);
      i.setItemMeta(meta);
    }
  }

  private void writeHand(Player p, EquipmentSlot hand, ItemStack item) {
    if (hand == EquipmentSlot.HAND) p.getInventory().setItemInMainHand(item);
    else p.getInventory().setItemInOffHand(item);
  }

  private List<Destination> destinations() {
    List<Destination> out = new ArrayList<>();
    ConfigurationSection s = getConfig().getConfigurationSection("teleports");
    if (s == null) return out;
    for (String n : s.getKeys(false)) {
      ConfigurationSection p = s.getConfigurationSection(n);
      if (p == null
          || !valid(n)
          || !NavigationMath.isFiniteCoordinate(p.get("x"))
          || !NavigationMath.isFiniteCoordinate(p.get("y"))
          || !NavigationMath.isFiniteCoordinate(p.get("z"))) continue;
      String w = p.getString("world"), id = p.getString("id");
      if (w == null || id == null) continue;
      String g = p.getString("group", "General");
      out.add(
          new Destination(
              n,
              w,
              p.getDouble("x"),
              p.getDouble("y"),
              p.getDouble("z"),
              g,
              p.getString("creator"),
              id));
    }
    out.sort(
        Comparator.comparing((Destination d) -> d.group, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(d -> d.name, String.CASE_INSENSITIVE_ORDER));
    return out;
  }

  private Destination find(String n) {
    return destinations().stream().filter(d -> d.name.equalsIgnoreCase(n)).findFirst().orElse(null);
  }

  private Destination findById(String id) {
    return destinations().stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
  }

  private boolean nameExists(String name) {
    ConfigurationSection points = getConfig().getConfigurationSection("teleports");
    return points != null
        && points.getKeys(false).stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
  }

  private void migrateIds() {
    ConfigurationSection s = getConfig().getConfigurationSection("teleports");
    if (s == null) return;
    boolean changed = false;
    for (String n : s.getKeys(false)) {
      ConfigurationSection p = s.getConfigurationSection(n);
      if (p != null && !p.contains("id")) {
        p.set("id", UUID.randomUUID().toString());
        changed = true;
      }
    }
    if (changed && !saveChecked()) {
      getLogger()
          .severe(
              "Waypoint ID migration could not be saved; disabling Navigator to prevent unstable bindings.");
      throw new IllegalStateException("Waypoint ID migration must be saved before navigation starts");
    }
  }

  private double readRadius() {
    double x = getConfig().getDouble("link-radius", 16);
    return Double.isFinite(x) && x >= 0 ? Math.min(x, 1_000_000) : 16;
  }

  private int readCapacity() {
    int n = getConfig().getInt("compass-max-uses", 64);
    return n > 0 ? Math.min(n, 1_000_000) : 64;
  }

  private boolean valid(String s) {
    return s != null && s.matches("[A-Za-z0-9_-]{1,32}");
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

  private EquipmentSlot selectedHand(Player p) {
    return plain(p.getInventory().getItemInMainHand())
        ? EquipmentSlot.HAND
        : EquipmentSlot.OFF_HAND;
  }

  private static final class Holder implements InventoryHolder {
    final int page;
    final List<Destination> entries;
    final ItemStack snapshot;
    final EquipmentSlot hand;
    Inventory inv;

    Holder(int p, List<Destination> e, ItemStack i, EquipmentSlot hand) {
      page = p;
      entries = List.copyOf(e);
      snapshot = i == null ? null : i.clone();
      this.hand = hand;
    }

    @Override
    public Inventory getInventory() {
      return inv;
    }
  }
}
