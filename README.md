# Village Diplomacy — Minecraft Forge Mod 1.20.1

A Forge mod that adds a full diplomatic relation system between villages — alliances, trade, tensions, and wars.

## Requirements

- Java 17 (Eclipse Temurin recommended)
- Minecraft Forge `1.20.1-47.4.28`

## Setup (IntelliJ IDEA)

```bash
./gradlew genIntellijRuns
```

Open the folder in IntelliJ IDEA, trust the Gradle project, then run the **`runClient`** configuration.

## Current Features

### Diplomacy Core
- **`DiplomacyRelation`** — five-tier relation scale: `ALLY → FRIENDLY → NEUTRAL → TENSION → HOSTILE`
- **`Settlement`** — per-village data: name, center, radius, leader UUID, specializations, local wood, warehouse, and relations
- **`SettlementSavedData`** — world-level NBT persistence via Forge `SavedData`
- **Terrain specialization** — one-time sampled analysis assigns plains, mountain, forest, or a mixed profile when a settlement is registered
- **Virtual production** — day-based resources accrue into a capped five-day trade warehouse, including catch-up while settlement chunks are unloaded
- **Trial delivery contracts** — persisted offers reserve warehouse stock, track partial handover, expire safely, and prevent duplicate rewards
- **Physical contract handover** — an assigned carrier uses an empty hand on each settlement's central Bell to receive, partially deliver, and redeem ordinary Minecraft item stacks

### Items
| Item | Action |
|---|---|
| Diplomatic Scroll | Right-click a foreign Bell → discovers the settlement and shows its coordinates, specialization, resources, leader, relations, and distance |
| Leadership Seal | Right-click a Bell twice within 10 seconds → confirms leadership; one player can lead only one settlement |
| Village Guard Spawn Egg | Spawns a guard for testing (Creative only) |

### Residents and garrison (0.2.0)
- **Human residents** — vanilla villagers retain their professions, trading and breeding while using human models, regional clothing and stable identity palettes.
- **Resident recruitment** — open the diplomacy table's Garrison, supply a chosen sword, chestplate and six bread, and train an adult unemployed resident for one game day. Queued recruits count toward the 25% military cap; at least four adult civilians must remain.
- **Reversible service** — the soldier retains the original resident's name and face. Cancel training to refund supplies, or demobilize a recruited soldier to restore the original villager and return current equipment once. Training and origin data survive saving/loading.

See [resident and garrison behavior, screenshots and verification](docs/development/residents-garrison.md).

### Entities
- **Village Guard** — patrols its settlement, fights monsters and guards from `HOSTILE` settlements. Creative spawn eggs provide iron sword + chainmail; recruited soldiers use the equipment actually supplied by the player.

Guards restore their patrol area after reload, return home before pursuing enemies,
and stop combat when diplomacy changes. Friendly guards, village civilians and
friendly leaders are protected from retaliation. Regional guard uniforms include
all skin layers and retain the civilian's identity face after recruitment.
See [guard development and verification](docs/development/guard-patrol.md).

## Project Structure

```
src/main/java/com/diplomacy/villagediplomacy/
├── VillageDiplomacyMod.java
├── core/
│   ├── ModEntities.java
│   └── ModItems.java
├── diplomacy/
│   ├── DiplomacyRelation.java
│   ├── Settlement.java
│   └── SettlementSavedData.java
├── entity/
│   └── VillageGuardEntity.java
└── client/renderer/
    └── VillageGuardRenderer.java
```

## Roadmap

- [ ] Diplomat NPC (quest giver, relation broker)
- [ ] Courier / Trade caravan AI
- [ ] Diplomacy table GUI
- [ ] Autonomous events (raids, siege, allied reinforcements)
- [ ] Custom guard textures
- [ ] Village auto-detection on world load
- [ ] Client-side sync packets
