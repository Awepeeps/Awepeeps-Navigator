# Player guide

Navigator stores a shared directory of POIs with a name, group, world, and saved coordinates. It does not provide live guidance, distance, or teleporting.

Run `/navigator` to browse. By default, hold exactly one compass in either hand; operators can configure item-free viewing. Browsing is free. Permission `awepeepsnavigator.view` controls viewing.

## Create a point

Look directly at an allowed block within 5 blocks, hold exactly one configured item, then run:

```text
/navigator set home
/navigator set mine landmarks
```

Defaults are an ordinary compass and a lodestone. Configure `item` and `creation.allowed-blocks` to change these. Creation always requires the item and `awepeepsnavigator.create`. Names and groups allow 1–32 letters, numbers, underscores, or hyphens; names are unique without regard to case. Saved coordinates are the target block's integer coordinates.

With `menu.sneak-right-click: true`, sneak-right-click while holding the configured item to open the POI menu. Ordinary non-sneak right-click passes through to vanilla, including normal lodestone compass binding. Sneak-right-clicking a lodestone can open the menu; it never auto-binds or creates a point. Bound/tracking compasses are always excluded from Navigator handling and remain untouched.

Durability is enabled by default: each successful creation uses one charge (64 by default), and the item breaks after its last charge. Failed creation is free. Set `durability.enabled: false` to disable all Navigator wear. In this mode, old managed items are not charged, have no lore or damage changed, and are never broken by Navigator.

## Remove a point

`/navigator remove <name>` removes a point you created. Operators with `awepeepsnavigator.admin` can remove any point. Removing/replacing the physical block does not remove the saved POI. Existing entries survive upgrades with their coordinates and creator data.

The optional BlueMap marker feature stays visible independently of in-game item visibility. `/navigator clear` is retained as a compatibility command and does not change item data.
