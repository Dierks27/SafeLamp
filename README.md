# SafeLamp

**A craftable magical lamp that makes hostile mobs quietly disappear — for the player carrying it, or for the whole area it is planted in.**

SafeLamp exists to solve one specific problem: a family server where a young child wants to play survival alongside adults, but creepers are genuinely upsetting. Rather than putting the entire server on Peaceful, the child carries a lamp. Hostile mobs within its radius are removed silently — no death animation, no drops, no XP, no sound. Everyone else gets a normal survival game. Craft the bigger tiers and you can plant one at spawn or in the family base and hold that whole area clear without anybody carrying anything.

No dependencies. No database. No economy plugin. Drop the jar in and it works.

---

## Screenshots

> _Placeholder — a Safe Lamp in the hotbar, and a creeper puffing out of existence._
>
> ![Safe Lamp in inventory](docs/screenshot-inventory.png)
> ![Mob despawning](docs/screenshot-despawn.gif)

---

## Installation

1. Download `SafeLamp-<version>.jar` from [Releases](https://github.com/Dierks27/SafeLamp/releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server.

That's it. The default config is designed to need no editing.

**Requires:** Paper 26.2+ (Java 25). No other plugins.

---

## The four tiers

| Tier | Item | Base item | Radius | Meant for |
|-----:|------|-----------|-------:|-----------|
| 1 | ✦ Safe Lamp ✦ | Lantern | 10 blocks | Starter. Carry it in a pocket. |
| 2 | ✦ Safe Lantern ✦ | Soul Lantern | 25 blocks | Roaming and exploring. |
| 3 | ✦ Safe Beacon ✦ | Beacon | 50 blocks | A base or a small settlement. |
| 4 | ✦ Safe Torch ✦ | Soul Torch | 100 blocks | Spawn and home. Place it and leave it. |

Carrying more than one? The highest tier wins.

Lamps are identified by a hidden data tag, not by their name. Renaming a lamp in an anvil keeps it working, and naming a plain lantern "Safe Lamp" does **not** turn it into one.

---

## Crafting

### Tier 1 — Safe Lamp

```
┌───┬───┬───┐
│ G │ L │ G │    G = Gold Ingot   (4)
├───┼───┼───┤    L = Lantern      (1)
│ G │ S │ G │    S = Glowstone    (1)
├───┼───┼───┤    I = Iron Ingot   (3)
│ I │ I │ I │
└───┴───┴───┘
```

### Tier 2 — Safe Lantern _(upgrade)_

```
┌───┬───┬───┐
│ D │ P │ D │    D = Diamond           (4)
├───┼───┼───┤    P = Prismarine Shard  (4)
│ P │ T │ P │    T = Tier 1 Safe Lamp  (1, consumed)
├───┼───┼───┤
│ D │ P │ D │
└───┴───┴───┘
```

### Tier 3 — Safe Beacon _(upgrade)_

```
┌───┬───┬───┐
│ E │ N │ E │    E = Emerald              (4)
├───┼───┼───┤    N = Nether Star          (4)
│ N │ T │ N │    T = Tier 2 Safe Lantern  (1, consumed)
├───┼───┼───┤
│ E │ N │ E │
└───┴───┴───┘
```

### Tier 4 — Safe Torch _(upgrade)_

```
┌───┬───┬───┐
│ G │ N │ G │    G = Glowstone            (4)
├───┼───┼───┤    N = Netherite Ingot      (4)
│ N │ T │ N │    T = Tier 3 Safe Beacon   (1, consumed)
├───┼───┼───┤
│ G │ N │ G │
└───┴───┴───┘
```

The upgrade recipes require a **real** lamp of the tier below in the centre. A vanilla lantern will not work, and the tier 1 recipe refuses to run if a real lamp is anywhere in the grid, so you cannot accidentally feed your Safe Beacon into it.

---

## Placing lamps

Any lamp can be **placed as a block**. A placed lamp protects the area around it with nobody carrying anything — the intended way to secure spawn or a family base.

- Break it and you get the lamp item back, not a vanilla lantern.
- Placed lamps refuse to be destroyed by explosions, pistons, or flowing water, so an expensive lamp cannot vanish to a stray creeper. (Configurable.)
- Placed lamps are remembered across restarts in `plugins/SafeLamp/placed-lamps.yml`.

---

## Carry mode

`carry-mode` decides where a player must be carrying a lamp for it to protect them:

| Mode | Lamp works when… |
|------|------------------|
| `INVENTORY` _(default)_ | It's anywhere in the backpack or hotbar. Pick it up once and forget it — the friendliest option for a young player. |
| `HAND` | It's held in the main hand or the offhand. |
| `MAIN_HAND` | It's actually held in the main hand. The strictest option: put the lamp away and mobs come back. |

Armor slots and ender chests never count. `check-offhand: false` drops the offhand from `INVENTORY` and `HAND`.

---

## Turning tiers on and off

Every tier is independent. Set `enabled: false` on a tier and it stops protecting anyone, its recipe is not registered, and it cannot be placed. Want only the small lamp? Turn off tiers 2–4. Only want a big base light? Turn off tiers 1–3.

---

## What is (and isn't) despawned

`despawn-mobs` is a **whitelist**: only the types listed there are ever removed. Everything else — animals, villagers, golems, pets — is untouched.

On top of that, these are never despawned:

- **Bosses.** The Ender Dragon, the Wither, and anything the server reports as a boss. A lamp must not be a one-click way to skip a boss fight, its loot and its return portal. Controlled by `allow-boss-despawn`, which ships **off** and warns loudly at startup if you turn it on.
- **Named mobs.** Anything with a name tag (`protect-named-mobs`).
- **Custom / plugin bosses.** A boss plugin's boss is usually a reskinned vanilla mob — a `ZOMBIE` with 4000 health and a boss bar — which would otherwise look like an ordinary despawnable zombie. Two guards catch those:
  - `protect-above-max-health` (default `500`, exactly a Warden's health, so no vanilla mob is affected). **Running a boss plugin? Lower this to around `100`.**
  - `protect-tagged-mobs`, a list of plugin namespaces whose mobs are left alone. MythicMobs, EliteMobs and friends are listed by default.
- **Mobs in a ride.** Anything riding something or being ridden.

Removal is `Entity.remove()`, not a kill: there is no death event, **no loot, no XP**, and no sound.

---

## Configuration

`plugins/SafeLamp/config.yml` is fully commented. The defaults are chosen so most servers never touch it. Run `/safelamp reload` to apply edits without a restart.

| Key | Default | What it does |
|-----|---------|--------------|
| `lamps.tierN.enabled` | `true` | Turn a whole tier on or off |
| `lamps.tierN.radius` | `10 / 25 / 50 / 100` | Protection radius in blocks (a sphere) |
| `lamps.tierN.display-name` | see config | Item name, `&` colour codes |
| `lamps.tierN.recipe-enabled` | `true` | Register that tier's crafting recipe |
| `scan-interval` | `40` | Ticks between scans (20 = 1 second) |
| `carry-mode` | `INVENTORY` | `INVENTORY`, `HAND` or `MAIN_HAND` |
| `check-offhand` | `true` | Does the offhand count? |
| `placed-lamps.enabled` | `true` | Allow lamps to be placed as blocks |
| `placed-lamps.radius-multiplier` | `1.0` | Scale the radius when placed |
| `placed-lamps.explosion-proof` | `true` | Placed lamps survive explosions |
| `despawn-mobs` | 33 hostile types | Whitelist of what gets removed |
| `protect-named-mobs` | `true` | Never remove name-tagged mobs |
| `allow-boss-despawn` | `false` | Leave this off |
| `protect-above-max-health` | `500` | Never remove mobs beefier than this (`0` = off) |
| `protect-tagged-mobs` | MythicMobs et al. | Plugin namespaces to leave alone |
| `particles` | `true` | Small smoke puff on despawn |
| `activation-message` | `true` | Action bar message when a lamp engages |
| `enabled-worlds` | `[]` (all) | Worlds lamps work in |
| `disabled-worlds` | `[]` | Worlds lamps are off in (wins over the above) |

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/safelamp give <player> <tier> [amount]` | Give someone a lamp | `safelamp.give` |
| `/safelamp reload` | Re-read `config.yml` | `safelamp.reload` |
| `/safelamp info` | Version, settings, active and placed lamps | `safelamp.give` |

---

## Permissions

All `use`, `craft` and `place` permissions default to **true**, so a basic setup needs no permission configuration at all. Restrictive servers can negate them per group.

| Permission | Default | Grants |
|------------|---------|--------|
| `safelamp.use.1` … `.4` | `true` | Being protected by that tier |
| `safelamp.craft.1` … `.4` | `true` | Crafting that tier |
| `safelamp.place.1` … `.4` | `true` | Placing that tier as a block |
| `safelamp.give` | `op` | `/safelamp give` and `/safelamp info` |
| `safelamp.reload` | `op` | `/safelamp reload` |

A player who lacks `use` for a high tier still gets the protection of the highest tier they *do* have permission for.

---

## FAQ

**Does it work with Bedrock players?**
Yes, through Geyser. Every lamp is a vanilla item (lantern, soul lantern, beacon, soul torch) carrying custom item data, so Bedrock clients render it as the normal item. The name and lore may look plainer, but it functions identically.

**What about performance?**
One scan per protected player and per placed lamp, every 2 seconds by default, and only over loaded chunks. Placed lamps in unloaded chunks are skipped entirely. On a family-sized server this is negligible. If you run large radii with many placed lamps, raise `scan-interval` to trade responsiveness for cost.

**Can I change the recipes?**
Yes. Set `recipe-enabled: false` on any tier and define your own with a recipe plugin — hand it the lamp item from `/safelamp give` as the result.

**Will it delete my base's mob farm?**
Only if the farm is inside a lamp's radius. Keep lamps away from farms, or use `enabled-worlds` / `disabled-worlds` to keep them out of a whole dimension.

**Will it eat the bosses from my boss plugin?**
It is built not to — see [What is (and isn't) despawned](#what-is-and-isnt-despawned). If you run a boss plugin, lower `protect-above-max-health` to around `100` and add your plugin's namespace to `protect-tagged-mobs`.

**Do despawned mobs drop loot?**
No. They are removed outright rather than killed, so there is no death event, no drops and no XP.

---

## Building from source

```bash
./gradlew build
# → build/libs/SafeLamp-<version>.jar
```

Requires JDK 25 (Paper 26.2's target). Gradle itself runs on JDK 21; see `.github/workflows/build.yml` for the exact setup CI uses.

---

## License

MIT — see [LICENSE](LICENSE). Free to use, modify and redistribute.
