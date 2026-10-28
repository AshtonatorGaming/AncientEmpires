# DESIGN.md
## Ancient Warfare-style Mod – Full Vision & Priorities

**Target:** Minecraft 1.20.1 Forge (later NeoForge ports possible)  
**License:** GPL-3.0 (derivative of original Ancient Warfare 2)  
**Philosophy:** Cleaner, more performant foundation. Prefer new Goals, capabilities, auras, NBT flags, and equipment-driven behaviour. Heavy reuse of ownership, orders, leveling, upkeep, and inventory concepts. Everything configurable. Performance first (cache scans, equipment-change driven updates, short TTLs).

### Combat Integration Policy
The mod owns its attack system in core. NPCs must be able to use Better Combat-style timing and animations the way players do, without requiring the mob-compat stack.

- **Core owns** the attack goal, wind-up timer, hit application, cooldown, and unarmed/empty-hand fallback.
- **Better Combat** is a soft dependency only: when present, read its weapon attributes (range, attacks, timing data) to drive the internal wind-up and hit logic. When absent, use a simple internal cooldown + swing + damage path.
- **EMF/ETF** is a soft dependency only: when present, use it to play player-style animations on NPCs. The mod’s attack controller owns the attack window so the two systems do not fight.
- **No dependency** on Better Mob Combat, BMCfix, Mob Player Animations, or similar bridge mods. Those are unnecessary.
- Unarmed / empty-hand and special animation sets (claw, boxing, etc.) are handled internally via profiles or overrides. No ghost items.
- Spartan Weaponry traits remain optional bonuses.
- All thresholds, wind-up lengths, and animation choices are configurable.

### Dependencies
**Hard**
- Minecraft 1.20.1
- Forge 47.x

**Soft (optional – mod must work without them)**
- Better Combat (attribute data only)
- Spartan Weaponry (backstab, throwing weapons, traits)
- EMF + ETF (player-style animations)
- GeckoLib (if keeping animated models)
- JEI (recipes only)

---

## Revised Priority Tiers

### Tier 1 – Foundation (implement first)
- Clean NPC entity with inventory, ownership, basic commands/jobs, upkeep
- Weapon personality system: automatic primary ↔ sidearm switching based on distance vs weapon range
- Threat scoring for target selection (distance + health % + threat to owner/allies + facing angle + role match)
- Strict respect for weapon engagement ranges
- Basic hazard avoidance (lava, fire, steep drops, open water, etc.)
- Internal attack goal with wind-up, hit timing, and solid unarmed/empty-hand fallback (reads Better Combat attributes when present)
- Commander-style aura (damage/armor/morale, works with any melee weapon) + simple succession (highest-level nearby NPC inherits weakened aura + last orders)
- Governor-style efficiency aura + passive tax income into linked chest / town hall
- Structure templates + basic placement/spawner
- Simple work areas / job assignment

### Tier 2 – Broadly useful roles & systems
- Skirmisher (javelins/throwing → melee sidearm when closed; limited ammo, restock at upkeep)
- Pikeman / Spearman (reach weapon at distance → sidearm when close; respects range values; optional brace)
- Banner Bearer / Standard Bearer (morale/damage resistance aura; less likely to break; short panic if dies; optional plant-as-rally-point)
- Taunt / Aggro Magnet (Shieldbearer or Banner variant that pulls enemy attention)
- Quartermaster (slower food/ammo consumption nearby; faster auto-refill from chests)
- On-the-fly equipment repair (support NPC slowly repairs durability out of combat)
- Scout (periodic wide scan + LOS; reports count/distance/direction to owner)
- Medic improvements (triage priority, short speed burst when healing, better sidearms)
- Night Lantern/Torch Protocol (auto-equip light at night, put away at dawn)
- Dynamic/adaptive patrol routes with night behaviour changes
- Night Watch / Sentinel (detection and damage bonuses at night or low light)
- Adaptive combat styles via NBT (after enough kills with a weapon type, unlock small permanent quirks)
- Silent promotion tokens / commendation items (rare items that specialize a base NPC)
- Stronger explicit ammo/consumable slots and multi-weapon loadouts
- Simple Army Doctrine modifiers (Aggressive, Defensive, Skirmish, etc. on Commander or town hall)
- Emergent local squad rules (“stay near shields if short weapon”, “fall back with Commander”, “look for flank”)

### Tier 3 – Specialized / later roles
- Assassin (crouch while approaching, actively get behind, heavy Spartan backstab, short aggression window then optional flee, prefers isolated/low-health, stealth focus, optional night bonuses, reduced noise, cover-seeking)
- Flamethrower / Incendiary Specialist (short-range area denial, high self-risk, limited ammo)
- Mounted Skirmishers / Horse Archers (hit-and-run while mounted)
- Explicit Duelist / Parry Specialist (prioritizes block/parry → short counter window)
- Rogue (subdue/capture below ~30% health instead of kill)
- Siege Engineer (strong preference to mount/operate siege weapons)
- Messenger (deliver items or simple messages)
- Spy (disguise as neutral/enemy, get hired, report back)
- Other equipment-driven variants (Crossbowmen, Musketeers, Slingers, Two-Handed Shock Troopers, Halberdiers, etc.)

### Tier 4 – Larger systems & atmosphere
- Settlement Heartbeat (single value from Governor + worker happiness + recent battles; affects work speed, tax, morale)
- Living Chain of Command / automatic succession (already partially in Tier 1)
- Persistent NPC Legends & Kill Memory (special NPCs remember dangerous foes; short legend entries give small army bonuses)
- Player Style Adaptation (army slowly shifts based on how the player fights)
- Shared Ammo / Sidearm Pool
- Last Stand / Desperate Measures (low-health burst)
- Bloodied Veteran stacks
- Echo Ambushes (Scouts/Assassins mark locations for temporary buffs)
- Legacy Gear (signature weapons of fallen specials become unique items)
- Light terrain interaction (temporary cover block or break weak wood)
- Capture & conversion of low-health enemy NPCs
- Full daily schedules (muster, meals, free time, sleep/watch)
- Personal inventory clutter and trinkets
- Camp idle animations
- Visual dirt/fatigue
- True fatigue from marches and battles
- Morale shocks on leader death
- Homesickness / desertion risk
- Bonding pairs
- Sound-based awareness
- Weather-appropriate gear and reactions
- Field promotions mid-battle
- Unique death lines
- Post-battle respect / memorial animations
- Wounded crawling / limping
- Low-ammo conservative behaviour
- Smarter shield-wall spacing
- Whistle-style quick commands
- Ammo scavenging after combat
- Torch wall lighting orders
- Scout flare / signal arrow
- Campfire resting gatherings
- Victory cheer / defeat posture
- Optional pet companions

### Tier 5 – Ancient Heroes (special / cameo characters)
Named, high-value NPCs with curated gear, 1–2 unique AI flags, and a small passive. Still fully use ownership, orders, leveling, doctrines, weapon switching, etc. Unlock via rare items, structures, promotion tokens, or creative spawn.

Examples discussed:
- Goblin Slayer + full party
- Dragonborn (shout-style cooldown)
- Guts-style Black Swordsman (berserk low-health)
- Witcher-style (monster-tag bonuses + light potion use)
- Flame Hashira / fire specialist
- Quiet Healer with dark side
- Tragic Lancer / Spear of the End
- Wandering Samurai (iai-style first hit)
- Plague Doctor
- Pure original “Silent Blade” ultimate Assassin
- Unbreakable Shield tank
- Legendary Ranger
- Ashen One / cursed undead knight
- Goblin King
- And others in the same vein

---

## Implementation Notes
- Start with Tier 1 only. Get a compilable NPC + internal attack goal (with optional Better Combat attribute reading and unarmed fallback) working before adding roles.
- Prefer capabilities and NBT over deep entity subclasses.
- Core attack path always works; Better Combat and EMF/ETF only enhance it.
- All auras, switching thresholds, scan radii, wind-up times, and cooldowns must be configurable.
- Performance: equipment-change and goal-start driven updates preferred over per-tick scans. Cache threat and ally queries with short TTLs.
- Ancient Combat and Ancient Tactics features should be native here, making those compat mods unnecessary for this project.

This document is the complete current vision. All ideas are potentially addable. Prioritize by the tiers above.

---

## Later Expansion Notes (added from extended design discussions — do not treat as current priorities)

These items reuse the existing primitives (flags, anchors, slow director, data templates) and can be layered later without rewriting core systems. They are recorded here so they are not lost; full detail lives in VISION.md.

- **Settlement binding over radius:** NPCs bind themselves to a Town Hall, Fort, Outpost, or similar anchor block instead of continuous radius checks. This allows arbitrarily large settlements without the lag that large AW-style areas produced. Binding is a one-time or rare registration; the block owns the list.
- **Additional specialized blocks:** Fort (defensive morale source + Director pathing restriction except for intrigue/clandestine goals), Outpost (awareness/score), Granary (supply ledger that can be drained in sieges), Armory (repair modifier), Chapel (morale recovery anchor), War Council (high-level order entry), Siege Camp / War Camp (attrition objective, closer to Villager Recruits style siege cores).
- **Garrison refinements:** Banner or structure anchor acts as dummy leader and morale source for owner-faction units. Commanders stay data-only until the player is in fighting range or the banner is attacked. Sally-out order on low health or death of the top commander. Owner vs visitor/invader split (visitors get no defensive buff). Foreign mobs (e.g. Grimoire of Gaia) resolve via entity registry and can be marked temporary allies if desired.
- **Civilian hierarchy (block-centric):** Laborer draws buffs from their work block. Foreman / Overseer / Governor apply modifiers to the block itself rather than ticking on individual NPCs. Supports simplified levies.
- **Optional hardcore heir respawn:** MCA Descendants-style overwrite of an heir NPC with the player (traits follow). Strictly optional and off by default.
- **Director events and loyalty:** Rare director-spawned groups (caravans, refugees, scouts). Interaction-only loyalty offers with intermediate consideration state and trait-gated immediate accept/reject. Threat assessment before any superior-UUID rewrite. In-character reports from spies/nobles. Guilds and subfactions as lightweight flags or records.
- **Personality flags:** Simple trait flags (Martial, Just, Cruel, Ambitious, etc.) and leanings read only on rare events or ledger ticks. No continuous AI.

All of the above remain configurable and absent-safe. They do not change the priority order of the tiers above.
