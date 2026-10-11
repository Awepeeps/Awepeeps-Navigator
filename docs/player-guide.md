# Player guide

Awepeeps Navigator keeps a shared directory of points of interest (POIs). Each entry shows its name, group, world, and saved coordinates. The menu does not provide live compass guidance, distance, availability status, or teleport actions.

## Browse points

Hold exactly one ordinary compass in your main hand or offhand, then run:

```text
/navigator
```

Right-clicking with an ordinary compass also opens the menu. Selecting an entry displays its saved details. Browsing and viewing details do not change or charge the compass.

Lodestone-bound compasses do not open the Navigator menu and cannot be used to create POIs. Right-clicking a lodestone-bound compass always follows vanilla behavior, even if its lodestone was removed or its dimension is unavailable. Right-clicking a lodestone block also follows vanilla behavior. With an ordinary main-hand compass and a lodestone offhand compass, the main-hand action can open Navigator; an offhand fallback will not open a second menu when the main-hand lodestone compass is being used.

## Create a point

Stand at the location to save, hold exactly one ordinary compass, and run:

```text
/navigator set home
```

An optional group organizes the menu:

```text
/navigator set mine landmarks
```

Names and groups allow 1–32 letters, numbers, underscores, or hyphens. Names are unique without regard to case. A successful save spends one use from the compass. Failed creation, including invalid or duplicate names and config save failures, is free. The compass is consumed when its last use is spent.

New managed compasses have 64 uses by default. Existing managed compasses retain their stored capacity and remaining uses. The `/navigator clear` command remains for compatibility and explains that binding is no longer supported; it does not alter compass data.

## Remove a point

Run `/navigator remove <name>` to remove a POI you created. Administrators with `awepeepsnavigator.admin` can remove any POI. Removal does not affect compass targets or other items.

## Legacy ordinary compass cleanup

When an ordinary compass is next used to open the menu or create a POI, its 1.1-era destination or waypoint ID metadata and `Navigator bound:` lore are removed. Its use count, capacity, and compass target are preserved. Lodestone-bound compasses, including ones carrying old Navigator tags, are not modified.
