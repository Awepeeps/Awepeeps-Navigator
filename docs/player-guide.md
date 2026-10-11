# Player guide

Awepeeps Navigator gives compass direction and distance guidance to waypoints in your current world. It never teleports you.

## Getting started

Hold exactly one ordinary compass, then run:

```text
/navigator
```

Choose a waypoint from the menu. Keep that same compass in your main or off hand to follow its guidance. A stack of compasses and a lodestone compass cannot be linked.

## Create a waypoint

Stand at the location you want to save and hold a compass. Then run:

```text
/navigator set home
```

An optional group can organize the menu:

```text
/navigator set mine landmarks
```

Creating a waypoint links that compass to the new waypoint and uses one charge. Waypoint names cannot overwrite existing names. A link must be in the same world and no more than 16 blocks away in three-dimensional distance. The radius is configurable by the server administrator.

## Uses and compass care

Each managed compass starts with up to 64 uses by default. Creating a waypoint or changing the compass to a different waypoint spends one use. Opening the menu, following guidance, selecting the compass's current waypoint, and failed attempts are free. Changing the configured maximum applies to newly initialized compasses; a managed compass retains its captured capacity.

To remove the binding without restoring uses, hold the compass and run:

```text
/navigator clear
```

A compass is consumed when its final use is spent. Managed compass use data travels with the item through trades and server restarts.

## Remove a waypoint

Run `/navigator remove <name>` to remove one you created. Administrators can remove any waypoint. A removed waypoint cannot be followed by compasses that still reference it.
