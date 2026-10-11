# Awepeeps Navigator

A scaffold for an independently maintained Minecraft compass navigation plugin.
Reviewed source and build tooling will be imported in a later change.

## Intended behavior

- An ordinary craftable compass opens a shared destination menu.
- Selecting a destination provides direction and distance guidance.
- Operators manage shared destinations.
- Player and waypoint teleportation are not plugin features.

## Repository layout

- `src/`: plugin source and tests, to be imported after review.
- `docs/`: player instructions, build, maintenance, compatibility and release notes.

## Release workflow

Development takes place in a personal fork, with pull requests targeting the org
repository. Future tagged releases will include built artifacts and checksums; the
server blueprint will pin a reviewed plugin release instead of duplicating source.

Only scaffolding is published now. Do not import deployment history, server data,
addresses, credentials, player identifiers, live configs or generated output.

The org repository is `Awepeeps/Awepeeps-Navigator`; the personal fork is
`deathpie/Awepeeps-Navigator`. Use `origin` for the fork and `upstream` for the org,
with pull requests targeting the org's `main` branch.
