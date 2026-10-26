# Village Diplomacy development log

## 2026-10-26 — settlement guard skin prototypes

- Save three user-requested adaptations of the supplied 64x64 wide-player skin for the existing PLAINS, MOUNTAIN and FOREST settlement types.
- Keep the original PNG, standalone PNG variants, embedded-texture Blockbench projects, a front/back comparison and validation results under `prototypes/guard-skins`.
- Include the reproducible authoring script at `tools/create_guard_skins.cjs` and document provenance and editing instructions in the prototype README.
- Verification: exported PNGs were read back; dimensions, unchanged head pixels and unchanged alpha were checked. The comparison image was visually inspected. Opening the finished Blockbench projects was interrupted by the user; those projects and client rendering remain unverified.
- These are visual prototypes, not an active renderer change. Existing guard AI fixes remain in their earlier commits on `2nd-test`. Existing IDE configuration and runtime-log changes are unrelated and excluded from this asset commit.

## 2026-10-25 — guard patrol on Arthur's shared core

- Authorized scope: use Arthur's `test` as the shared settlement/trade foundation; work on guards without building a second trade implementation.
- Base: `origin/test` at `41653dbc49d1a89a22b30b75d3952b437a598ea6`.
- Working branch: **`2nd-test`**, as explicitly requested by the user. `test` is Arthur's branch and must never be modified or pushed to.
- Integration commit: `98634d9` merges `origin/test` into `2nd-test`, retaining both histories. Original `2nd-test` tip `1b59c3d` is preserved at `codex/backup-2nd-test-20261025`; the incompatible crate/cargo sources are preserved under `prototypes/sealed-cargo` outside the active Gradle source set.
- Guard edits were initially prepared on `codex/guard-patrol` and transferred to `2nd-test` through a stash after the user's branch clarification. No work continues on the temporary branch.
- Runtime: Minecraft 1.20.1, Forge 47.4.28, Java 17.0.20.1; retain the existing Forge loader/API route.
- Shared design: ordinary Minecraft resources and `TradeContractService` / `TradeHandoverService`, not sealed crates. No change to the friend's settlement, warehouse, contract, menu, or save schema is planned.
- Guard slice: restore settlement restrictions after reload, keep patrol/combat near home, prevent friendly retaliation, react to diplomatic changes, and replace the missing texture reference with a built-in Minecraft fallback.
- Verification: baseline `test build` passed after downloading JUnit: 43 tests, no failures. Initial GameTests exposed fixture issues: persisted settlements contaminated a reused test world, and entities spawned inside the arena's raised stone floor. Reading the saved region block states confirmed the floor at helper Y=1; spawn at Y=2 instead. The return failure did not establish a vanilla AI bug; retain vanilla navigation and verify it on the corrected arena. Each run now uses a fresh directory under `build/guard-gametest-runs`.
- Test worlds must use an isolated development directory; never open the existing `run/saves/New World` for automated tests. The main mod's old crate NBT schema is not automatically migrated; see `prototypes/sealed-cargo/README.md`.
- A successful build or headless GameTest run does not prove client rendering or manual gameplay.

### Final verification

- `gradlew.bat -PguardGameTests runGameTestServer --offline`: **all 9 required GameTests passed** on a fresh isolated server world. Vanilla return navigation works on the corrected arena; no custom return goal is shipped.
- `gradlew.bat test build --offline`: **43 JUnit tests passed**, no failures, errors or skipped tests; regular release build succeeded.
- Release: `build/libs/villagediplomacy-0.1.0.jar` (165,030 bytes at verification). ZIP inspection confirmed the guard, table GUI and shared trade implementation are present; GameTest classes, the arena, active sealed-crate/mount-cargo classes and copied Minecraft textures are absent.
- Logs: `build/guard-gametest-output.log` and `build/guard-release-build-output.log`.
- Shared diplomacy/gameplay/menu/screen sources match `origin/test` at `41653db`. Active Java changes relative to that base are confined to the guard entity and renderer.
- Existing `run/saves` worlds were not opened. Client appearance/animations remain visually unverified; the renderer uses the confirmed built-in Steve texture as a temporary fallback.
- Work is local to **`2nd-test`**. No push was performed; local `test` remains `01b33f7` and `origin/test` remains `41653db`.

## 2026-10-26 — hostile mobs must initiate attacks on guards

- User report: hostile mobs do not attack guards proactively. Root cause confirmed in local mapped Minecraft sources: their target selectors name players/villagers/golems but do not include `VillageGuardEntity`.
- Add an automatically registered Forge entity-join handler under `entity/ai`, so existing loaded mobs as well as newly spawned mobs receive the guard target goal. Do not edit shared diplomacy/trade code or `test`.
- Keep existing retaliation/player priorities and neutral-mob rules. Spider acquisition retains the vanilla brightness check; piglins, anger-based neutral mobs and wardens retain their native conditional hostility.
- Brain-based hostile mobs require attack memory as well as Mob target state; slimes/magma cubes additionally require contact damage because vanilla collision code only recognizes players and iron golems.
- Existing unrelated changes in `.idea/runConfigurations/runClient.xml` and `run-data/logs/*` predate this fix and must remain untouched.
- Verification: **all 21 required server GameTests passed** (the original 9 plus 12 hostile-mob regressions). The new tests disable the guard's AI and prove unprovoked melee/ranged damage, creeper fuse activation, slime/magma contact damage, hoglin/piglin-brute brain combat, neutral/light behavior, and targeting after NBT reload. Separate batches prevent higher-priority villagers from another test from diverting zombies.
- `gradlew.bat test build --offline`: **43 JUnit tests passed**, no failures/errors/skips, and the regular release build succeeded.
- Release JAR: `build/libs/villagediplomacy-0.1.0.jar` (169,401 bytes at verification). Both hostility classes are present; GameTest classes and the arena are absent.
- Logs: `build/hostile-guard-gametest-output.log`, `build/hostile-guard-release-build-output.log`. Existing user worlds were not opened; visual client gameplay was not automated.
- Manual retest: restart the client and summon `minecraft:husk` near a guard **without** `{NoAI:1b}`. The earlier manual command deliberately disabled the husk's AI and therefore could only test the guard's attack, not mutual combat.
