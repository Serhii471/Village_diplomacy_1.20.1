# Guard patrol integration

All development in this slice belongs to **`2nd-test`**. Arthur's **`test`** is a
read-only upstream reference. Integration commit `98634d9` retains both branch
histories and uses `test` commit `41653db` for the shared settlement/trade core.

## Boundaries

- Guard behavior: `entity/VillageGuardEntity.java`.
- Guard rendering: `client/renderer/VillageGuardRenderer.java`.
- Read the shared settlement API; do not introduce a second warehouse, trade
  service, relationship model or NBT schema for guards.
- Sealed-crate and mount-cargo sources are archived under
  `prototypes/sealed-cargo`. They are outside the Gradle source set.
- The original `2nd-test` state remains at
  `codex/backup-2nd-test-20261025` (`1b59c3d`). Old prototype worlds require a
  separate migration before use with the new trade core.

## Current behavior

- Spawned guards bind to a registered settlement at their position and retain
  their existing iron sword and chainmail equipment.
- A guard does not despawn because the player walks away.
- Settlement affiliation survives NBT reload. The patrol restriction is
  restored on load and refreshed once per second, including center/radius
  changes and removal of the settlement.
- Returning to the patrol area has priority over melee pursuit. A guard drops
  targets outside the patrol area and no longer attacks after relations cease
  to be hostile.
- Same-settlement, neutral, friendly and allied guards are not valid targets,
  including retaliation. Unassigned guards do not fight other guards.
- A guard will not attack villagers, iron golems, its leader, or leaders of
  friendly/allied settlements. Other players are not acquired proactively;
  the existing retaliation goal may target them if they attack the guard.
- The renderer references the built-in Minecraft Steve texture as a temporary
  fallback for the missing custom texture. No Minecraft texture is copied into
  the mod jar. A custom guard skin is a separate visual task.
- Hostile mobs receive a guard-targeting goal when they enter the server level,
  including after chunk/world reload. Existing retaliation and player/villager
  targets retain their higher priorities. Brain-driven hostile mobs also receive
  the corresponding attack memory; slimes and magma cubes can deal contact
  damage to a guard they are chasing.
- Spider acquisition still requires darkness. Endermen, zombified piglins,
  ordinary piglins and wardens retain their existing anger/gold/sensory rules.

## Verification commands

```powershell
.\gradlew.bat test build --offline
.\gradlew.bat -PguardGameTests runGameTestServer --offline
```

The first successful run may need online dependency resolution without
`--offline`. GameTest sources and the original test-arena NBT are included only
when `-PguardGameTests` is explicitly supplied. Every run creates a fresh world
under `build/guard-gametest-runs/<run-id>`, leaving `run/saves` untouched.
Regenerate the arena with `python tools/create_guard_test_arena.py`.

The server scenarios exercise spawn/equipment, NBT reload, center/radius
updates, friendly retaliation, changing diplomacy during combat, protected
leaders/civilians, an escaping enemy, return navigation, actual monster damage,
and cleanup after deleting a settlement.

The hostile-mob regression scenarios use a guard with AI disabled, so damage
proves the monster initiated combat instead of merely retaliating. They cover
melee, ranged attacks, creeper fuse activation, spiders in dark/bright conditions,
slime/magma contact damage, hoglin and piglin-brute brains, neutral endermen,
and reloaded mobs.

For a manual mutual-combat check, restart the client with the updated code, then
spawn a guard and run `/summon minecraft:husk ~4 ~ ~` near it on Normal difficulty.
Do not add `{NoAI:1b}` to the hostile mob: that deliberately disables its movement
and attacks. No replacement of existing guards is required.

Headless tests verify server behavior. Client rendering, attack animation and
player interaction still need a visual check in an isolated client world.
Build a regular release after GameTests; test classes and arena resources must
not be present in the release jar.
