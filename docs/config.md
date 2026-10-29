# Configuration reference

Ancient Empires has two config files. Every setting, its default, and what it does is listed below.

| File | Where | Who it affects |
|---|---|---|
| Server config | `<world>/serverconfig/ancientempires-server.toml` | Gameplay. One per world (or per server). Synced to players when they join. |
| Client config | `config/ancientempires-client.toml` | Visuals on your own screen only. |

## How to change a setting

1. Start the game (or server) once with the mod installed, so the files are written.
2. Open the file in a text editor. Settings are grouped into sections such as `[combat]` and `[morale]`, matching the headings on this page.
3. Change the value and save.

Forge watches these files: most changes take effect while the game runs, the moment the file is saved. A few settings only apply to NPCs that load after the change (the setting's text says so); leaving and rejoining the world is the sure way.

To give every new world in a modpack the same server settings, put a copy of `ancientempires-server.toml` in the pack's `defaultconfigs/` folder. Forge copies it into each new world.

A value outside its "Allowed" range is reset to the default by Forge, which logs a warning naming the setting.

### Reading the tables

- **Ticks**: the game runs 20 ticks a second, so `40` ticks is 2 seconds.
- **Lists** such as `[0.0, 0.1, 0.2, 0.3]` hold four values, one per step of skill or rank. The section intro says what the steps are. Keep four entries.
- **Chances** run from 0 (never) to 1 (always).
- **Multipliers**: 1.0 means unchanged, 0.5 half, 2.0 double.

## Client settings

File: `config/ancientempires-client.toml`. No sections.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `showNametags` | `false` | true / false | Show an NPC's custom name above it in the world. The inventory preview never draws that nametag. |
| `showStatusIcons` | `true` | true / false | Show Ancient Warfare's task icon above an NPC: working, following, going home, sleeping, fighting, fleeing, hungry, and so on. |
| `statusIconRange` | `24` | 4 to 128 | Status icons show for NPCs within this many blocks. |
| `statusIconsOwnOnly` | `false` | true / false | Show status icons and morale badges only over your own NPCs, for less clutter in big battles. |

## Server settings

File: `<world>/serverconfig/ancientempires-server.toml`.

### `[combat]`

How NPCs swing, aim, move in a fight, and how hard NPC-on-NPC fights hit. Most values are in ticks (20 ticks = 1 second) or blocks.

The `[combat]` section holds three sub-sections (below) and these settings of its own.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `forwardAngle` | `50.0` | 1.0 to 360.0 | Cone, in degrees, for punches, stabs, and other attacks that do not define a sweep angle. The target must sit inside this arc in front of the NPC. |
| `sweepAngle` | `120.0` | 1.0 to 360.0 | Arc, in degrees, used when a sweeping attack does not specify its own angle. |
| `turnSpeed` | `12.0` | 1.0 to 180.0 | Degrees of body yaw the NPC may turn per tick while chasing. The body, not the look vector, has to face the target. |
| `turnSpeedInRange` | `24.0` | 1.0 to 180.0 | Degrees of body yaw per tick once the target is inside weapon range. Faster than turnSpeed so the swing is not stuck on the last step. |
| `maxYawError` | `180.0` | 1.0 to 180.0 | Largest body-yaw error, in degrees, that can still land a hit. The weapon arc is also required, so this cannot widen a narrow attack. 180 leaves the weapon arc in charge. |
| `forceSwingTicks` | `10` | 0 to 200 | Ticks spent in range but not yet facing before the NPC swings anyway. Stops a bad turn from freezing combat. 0 waits forever. |
| `rangeScaleBaseline` | `1.8` | 0.5 to 8.0 | Hitbox height, in blocks, that counts as 1.0x weapon range. A 1.8-tall NPC keeps the weapon's own reach. |
| `rangeScaleMin` | `0.25` | 0.05 to 4.0 | Smallest reach multiplier from hitbox height. Ready for a shorter NPC later. |
| `rangeScaleMax` | `4.0` | 1.0 to 16.0 | Largest reach multiplier from hitbox height. Ready for a taller NPC later. |
| `tallAimFactor` | `1.5` | 1.0 to 8.0 | When the NPC's hitbox is taller than the target's times this factor, the swing aims at the target's body instead of its eyes. A 1.8 NPC against a 1.8 target does not qualify at 1.5. |
| `tallPitchOffset` | `15.0` | 0.0 to 80.0 | Extra degrees downward added to that tall aim. Ignored when the height test does not pass. |
| `cancelIfOutOfRange` | `true` | true / false | Drop a wind-up that leaves weapon range before the hit frame. The swing still goes on cooldown. |
| `windupRangeSlack` | `0.5` | 0.0 to 4.0 | Extra blocks of reach allowed during wind-up so stopping to swing does not instantly cancel. The hit itself uses the real range. |
| `windupKeepRange` | `0.8` | 0.1 to 1.0 | During a wind-up the NPC keeps closing until the target is within this fraction of its reach, so a target drifting a little does not turn the swing into a sure miss (a real backstep still dodges it). |
| `dodgeLeadTicks` | `6` | 1 to 40 | A dodge is decided this many ticks before an NPC's wind-up lands (skill.dodgeChance), and backsteps for dodgeTicks. |
| `dodgeTicks` | `7` | 1 to 40 | How many ticks the dodge backstep lasts (see dodgeLeadTicks). |
| `flank` | `true` | true / false | Fighters without a shield work around a raised shield toward its holder's side or back (shields cover only the front) instead of hammering it, but still strike the moment it comes down. |
| `flankAngle` | `70.0` | 0.0 to 180.0 | Degrees off the shield holder's facing at which the flanker is round far enough. |
| `minGapReach` | `0.55` | 0.0 to 1.0 | Fighters keep the foe at least this fraction of their reach away (centre to centre), backing off when pressed closer, so they fight at weapon length instead of hugging. |
| `minGap` | `0.5` | 0.0 to 3.0 | And never closer than their two bodies plus this many blocks. |
| `stopMovingDuringWindup` | `true` | true / false | Stop pathing once the target is in range during wind-up. |
| `footwork` | `true` | true / false | Inside reach, while waiting to strike or recovering, NPCs now and then take a quick sidestep (stepping in at the edge of reach, giving ground when pressed) instead of standing like posts. |
| `footworkChance` | `0.35` | 0.0 to 1.0 | Inside reach an NPC mostly stands; after each pause of 1.5 to 3.5 seconds, the chance it takes one quick sidestep. |
| `crowdLimit` | `3` | 1 to 20 | Footwork is for duels: with this many other NPCs within 2.5 blocks, an NPC stands its ground instead of stepping (crowded lines turned into a scramble). |
| `duelRange` | `5.0` | 0.0 to 32.0 | Duel mode: no other NPC within this many blocks of an NPC and its foe. Duelists circle (steps kept to one side, shorter rests), and step back out of reach after their own blow, so trading hits takes timing. Otherwise line mode: hold the place, little footwork. |
| `duelDrift` | `0.25` | 0.0 to 1.0 | In a duel the NPC never stands still (ToroQuest's guards): it drifts sideways at this input (0 to 1; a little more behind a raised shield) in its stance's direction, which changes now and then, while closing to the edge of reach. |
| `backpedalChance` | `0.15` | 0.0 to 1.0 | Chance a new duel stance is a backpedal: out of the foe's reach, then in again. After its own blow a duelist backpedals half the time. |
| `sidestepSpeed` | `1.0` | 0.1 to 1.0 | Sidestep input, 0 to 1. High enough that the legs walk; slower drifts read as sliding. |
| `shieldSidestepScale` | `0.5` | 0.0 to 1.0 | When both fighters carry shields, sidesteps come this much less often: a standoff, not a dance. |
| `swingDelay` | `6` | 0 to 40 | Extra ticks after every NPC swing before the next, on top of the weapon's own cooldown, so attacks are not spammed. Ancient Warfare used only the weapon cooldown (20 ticks over attack speed); 0 matches it. 6 gives a readable pace without idling. |
| `pressureChance` | `0.12` | 0.01 to 1.0 | Against a raised shield, the chance each tick that an NPC without one starts its swing. Below 1 it picks its moments instead of hammering the shield as fast as its weapon allows. |
| `windupMoveSpeed` | `0.7` | 0.0 to 2.0 | Path speed multiplier while winding up and still short of the target. |
| `approachSpeed` | `1.12` | 0.1 to 2.0 | Path speed multiplier while closing to weapon range. A mob's speed counts twice (push and pace), so the multiplier's effect is squared: 1.0 is a player's walk, 1.12 a sprint. |
| `pathRecalcTicks` | `8` | 1 to 40 | Ticks between path recalculations while chasing. |
| `requireLineOfSight` | `true` | true / false | Require line of sight before starting a swing and again on the hit frame. |
| `bypassHurtCooldown` | `true` | true / false | NPC hits on other mobs ignore their short hurt invulnerability so fast weapons and fists land. Players always keep theirs. |
| `comboResetRate` | `3.0` | 0.5 to 20.0 | Combo step resets after this many weapon-cooldowns without a swing. |
| `npcWindupScale` | `1.25` | 0.5 to 3.0 | Multiplies every NPC wind-up, so NPCs swing a little slower than a player with the same weapon. Recovery is unchanged, so the whole swing gets longer. 1.0 matches players. |
| `maxSpeed` | `1.14` | 0.5 to 3.0 | The fastest an NPC moves, as a speed multiplier: 1.14 is a player's sprint (a mob's pace goes with the square of it). Fleeing, chasing, and every other goal are held to it; riding is the mount's own. |
| `npcVsNpcDamage` | `0.7` | 0.05 to 4.0 | Damage an NPC deals to another NPC, as a fraction, so battles between them last longer. Fights with players are unchanged. |
| `npcVsNpcKnockback` | `0.3` | 0.0 to 2.0 | Knockback an NPC takes from another NPC's hit, as a fraction of vanilla's. Full knockback shoved the one hit out of reach mid-swing, so its swing missed and whoever hit first won. |
| `engageRangeFactor` | `0.85` | 0.5 to 1.0 | A swing starts only inside this fraction of the weapon's reach, so a target stepping back during the wind-up is still hit. The hit checks the full reach. 1.0 swings from the edge. |
| `feints` | `true` | true / false | NPCs with a feint chance (set by specialized roles, zero otherwise) sometimes break off a swing halfway against a guarded target, like Better Combat's feint. |
| `feintRecoveryTicks` | `6` | 0 to 100 | Ticks after a feint before the next swing can start. |
| `dualWield` | `true` | true / false | Two one-handed Better Combat weapons alternate main-hand and off-hand swings, as Better Combat does for players. Off-hand swings use that weapon's damage, speed, reach, and attacks. |
| `sweepEffects` | `false` | true / false | A landed sword slash wider than forwardAngle plays the vanilla sweep sound and crescent. Off by default; Better Combat Particle trails cover NPC swings when that mod is installed. |

#### `[combat.unarmed]`

Empty-handed attacks (fists).

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `range` | `2.0` | 0.5 to 8.0 | Reach of an empty-hand attack, in blocks, when Better Combat's fist profile is not used. |
| `windupTicks` | `4` | 1 to 200 | Ticks after the swing starts before an empty-hand hit lands. |
| `cooldownTicks` | `10` | 1 to 200 | Ticks from the start of one empty-hand swing until the next can start. Includes the wind-up. |
| `damageMultiplier` | `1.0` | 0.0 to 100.0 | Multiplies the NPC attack damage attribute for an empty-hand hit. |
| `preferBetterCombatProfile` | `true` | true / false | When Better Combat is loaded, empty hands use its fist weapon attributes instead of the values above. No item is equipped. |

#### `[combat.vanilla]`

Weapons that have no Better Combat data, or every weapon when Better Combat is absent or turned off.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `range` | `2.5` | 0.5 to 16.0 | Reach used for a held weapon that has no Better Combat attributes. |
| `windupFraction` | `0.35` | 0.0 to 1.0 | Fraction of the weapon cooldown spent winding up when Better Combat timing is unavailable. |
| `minCooldownTicks` | `6` | 1 to 200 | Shortest weapon cooldown, in ticks, for the internal fallback. |

#### `[combat.betterCombat]`

Reading weapon data from Better Combat, when it is installed.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Read Better Combat weapon attributes when that mod is loaded. Turn this off to force the internal fallback. |
| `upswingMultiplier` | `0.5` | 0.2 to 1.0 | Scales a Better Combat attack's upswing. 0.5 matches Better Combat's default and lands the hit about 25% through a 0.5-upswing weapon. |
| `readUpswingFromMod` | `true` | true / false | When true, and Better Combat is loaded, use its server upswing_multiplier instead of upswingMultiplier above. |
| `minAttackInterval` | `2` | 1 to 40 | Minimum ticks between the starts of two attacks driven by Better Combat attributes. |

### `[sidearm]`

Archers and skirmishers switching to a melee weapon from their backpack when an enemy gets close.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | An archer whose target gets this close swaps its bow for the best melee weapon in its backpack. Archers on hold position never swap. |
| `corneredRange` | `3.0` | 0.5 to 16.0 | Distance, in blocks, at which an archer swaps to its sidearm. |
| `releaseRange` | `6.0` | 1.0 to 32.0 | Distance, in blocks, the target must stay beyond before the bow comes back. |
| `releaseTicks` | `20` | 0 to 400 | Ticks the target must stay beyond releaseRange before the bow comes back. |
| `swapCooldownTicks` | `40` | 0 to 400 | Ticks after any swap before the next one, so the NPC does not flip between weapons. A dead or lost target still brings the bow back at once. |

### `[navigation]`

Doors.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `openDoors` | `true` | true / false | NPCs path through and open doors a hand can open (wooden and modded doors built on the vanilla door). Iron doors stay shut. Applies to NPCs loaded after the change. |
| `closeDoors` | `true` | true / false | NPCs close a door behind them once they are through, as Ancient Warfare soldiers did. |
| `nightDoorRestriction` | `true` | true / false | At night NPCs do not open doors, as in Ancient Warfare, so they stay in or out until morning. NPCs that are following someone or fighting still use doors. |

### `[home]`

How far NPCs roam from the spot they call home, by day, at night, and in rain.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `wanderRadius` | `5` | 1 to 64 | Idle wandering stays within this many blocks of home. Ancient Warfare's default home area. |
| `dayRange` | `50.0` | 2.0 to 256.0 | By day, an NPC farther than this from home, with nothing to fight, walks back. |
| `ownedDayLeash` | `20.0` | 1.0 to 256.0 | By day, an owned NPC walking home stops once it is this close. |
| `factionDayLeash` | `30.0` | 1.0 to 256.0 | By day, a faction NPC walking home stops once it is this close. |
| `nightRange` | `5.0` | 1.0 to 256.0 | Faction NPCs keep night hours: at night, one farther than this from home walks back. Owned soldiers do not, as in Ancient Warfare. |
| `nightLeash` | `5.0` | 1.0 to 256.0 | At night, or when sheltering from rain, an NPC walking home stops once it is this close. |
| `rainShelter` | `true` | true / false | An NPC caught in the rain goes home and stays there until the rain stops. |
| `factionArcherPost` | `3.0` | 0.0 to 32.0 | Out of combat, a faction archer holds a post within this many blocks of home. 0 turns it off. |

### `[shield]`

Shield use: when NPCs raise and lower shields, guard (shield stamina), bashes, and duels.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | NPCs raise an off-hand shield (vanilla or modded, anything that can shield-block) against a target that is swinging at them or aiming a bow at them, as in Ancient Warfare. |
| `blockRange` | `4.0` | 1.0 to 16.0 | A swinging attacker must be within this many blocks to be blocked. Bow and crossbow aim is blocked at any range. |
| `aimWatchRange` | `24.0` | 0.0 to 64.0 | NPCs also raise the shield against anyone within this many blocks drawing a bow or crossbow at them, not only their own target. A player counts when looking straight at the NPC; never its owner. 0 turns this off. |
| `holdTicks` | `40` | 1 to 200 | Ticks the shield stays up after the threat stops swinging or aiming. |
| `guardInRange` | `true` | true / false | Guard stance: keep the shield up the whole time an enemy is within blockRange, not only while it swings. Vanilla shields only block after 5 ticks raised, so blocking only on the swing is close to a parry. |
| `minBlockTicks` | `12` | 0 to 100 | A raised shield is a commitment: it stays up at least this many ticks before the NPC may lower it to attack (vanilla shields only block after 5). An NPC never attacks with its shield up. |
| `strikeDelayTicks` | `6` | 0 to 40 | Ticks after lowering the shield before the NPC can begin a swing: the opening a player can punish. |
| `instantBlock` | `true` | true / false | A raised shield blocks from its first tick, for players and NPCs alike, so a shield that looks raised is raised. Vanilla waits five ticks. Mods like Responsive Shields do the same, and then this has nothing to do. |
| `riposteTicks` | `20` | 0 to 100 | After blocking a hit, the NPC may strike back within this many ticks without waiting out minBlockTicks. It never lowers the shield while the target is mid-swing. |
| `strikeChance` | `0.15` | 0.01 to 1.0 | Once the minimum block is over, the chance each tick that a guarding NPC lowers its shield to strike. Below 1, strikes come at a random beat, so they are hard to time and two guards do not open together. |
| `guardMax` | `30` | 1 to 200 | An NPC shield's guard, like stamina in Chivalry: each blocked hit drains it by the damage stopped (at least 1). At zero the guard breaks and the shield is knocked down for guardBreakTicks, so shield walls do not stalemate. |
| `guardRegen` | `0.2` | 0.0 to 10.0 | Guard recovered per tick with the shield down (a quarter of this while it is up). |
| `guardBreakTicks` | `40` | 0 to 400 | Ticks a broken guard keeps the shield down. |
| `advanceSpeed` | `0.7` | 0.1 to 2.0 | Path speed multiplier while advancing behind a raised shield (Ancient Warfare walked at half speed). |
| `axeDisableTicks` | `100` | 0 to 400 | Ticks an axe hit keeps an NPC's shield down, as it does a player's. |
| `npcAxeDisableChance` | `0.25` | 0.0 to 1.0 | Chance an NPC's axe knocks a shield out with a blocked hit. A player's axe always does (vanilla); a mob's did a quarter of the time, and an army of axemen disabling every shield made shield walls useless. |
| `bashMode` | `SPARTAN` | SPARTAN, ALL, OFF | Which shields NPCs can bash with after blocking a hit, mirroring Spartan Shields' bash. SPARTAN: shields in spartanshields:shields_with_bash (nothing without that mod). ALL: any shield. OFF: no bashing. |
| `bashWindupTicks` | `6` | 0 to 40 | Ticks an NPC braces behind its raised shield, with a warning sound, before the bash lands, so players can read it. |
| `bashRange` | `3.0` | 0.5 to 8.0 | Reach of a bash, in blocks, plus half the target's width. |
| `bashCooldownTicks` | `30` | 1 to 1000 | Ticks between bashes. Spartan Shields' default for a player. |
| `towerBashCooldownTicks` | `50` | 1 to 1000 | Ticks between bashes with a tower shield. Spartan Shields' default for a player. |
| `bashRecoveryTicks` | `10` | 0 to 100 | Ticks after a bash before the NPC raises its shield or swings again: the bash is the counter on its own, not the start of a combo. |
| `followThroughTicks` | `8` | 0 to 40 | Ticks after its own swing lands that an NPC keeps its shield down: the arm is still following through, as a player's is. |
| `mobPatienceTicks` | `40` | 0 to 400 | Against a mob (no wind-up to read), a shield NPC baits: shield up until the mob's blow lands on it, then ripostes while the mob's attack recovers. If the mob has not struck for this many ticks, it takes the opening anyway. Mobs that cannot hit back (not after this NPC, out of reach) are just attacked. |
| `heavyGuardDrain` | `1.75` | 1.0 to 10.0 | Guard drained by a blocked blow from a heavy weapon (an axe, a shield breacher like Spartan's halberds, a Better Combat two-hander), as a multiple of the damage. Players' heavy weapons count too. |
| `pressMinBlockTicks` | `4` | 0 to 100 | Against a foe that cannot block (no shield, or its shield knocked out) a shield NPC presses: its shield comes down after this many ticks up instead of minBlockTicks, and it strikes at pressStrikeChance per tick instead of strikeChance. It still guards; it just goes for the kill. |
| `pressStrikeChance` | `0.5` | 0.01 to 1.0 | Chance per tick to strike when pressing a foe that cannot block (see pressMinBlockTicks). |
| `duelBlockMin` | `20` | 1 to 400 | Duels (ToroQuest style): once the foe has struck, the shield goes up in spells of between duelBlockMin and duelBlockMax ticks (shorter up close), then stays down for between duelOpenMin and duelOpenMax while the NPC attacks. Half the time a blow on the shield cuts a spell to half a second. Lines keep the steady guard. |
| `duelBlockMax` | `80` | 1 to 400 | Longest shield-up spell in a duel, in ticks (see duelBlockMin). |
| `duelOpenMin` | `30` | 0 to 400 | Shortest shield-down spell in a duel, in ticks (see duelBlockMin). |
| `duelOpenMax` | `65` | 0 to 400 | Longest shield-down spell in a duel, in ticks (see duelBlockMin). |
| `arrowBlockTicks` | `25` | 0 to 200 | An arrow in flight at the NPC from a foe puts its shield up for this many ticks. |

### `[skill]`

Four-entry lists, one value per skill step. An NPC reads them along its level: entry 1 at level 1, entry 4 at the level cap (`levels.maxLevel`), in between at levels in between. An NPC with the `AeSkill` NBT tag (0 to 3) is pinned to that one entry instead.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `feintChance` | `[0.0, 0.1, 0.2, 0.3]` | list | Chance, 0 to 1, that a swing at a guarded target is a feint. An NPC's own AeFeintChance overrides this. |
| `dodgeChance` | `[0.1, 0.2, 0.3, 0.4]` | list | Chance, 0 to 1, that an NPC without a shield steps back out of reach of an NPC's visible wind-up instead of taking it (combat.dodgeLeadTicks before the hit), as a player can. Read along the levels like the other skill lists. |
| `bashChancePerBlock` | `[0.08, 0.12, 0.18, 0.25]` | list | Chance, 0 to 1, that a blocked hit is answered with a shield bash (see shield.bashMode). |
| `shieldReactionTicks` | `[8, 6, 4, 2]` | list | Most ticks before the shield comes up against a threat; each block waits a random amount up to this. |
| `guardByTier` | `[1.0, 1.25, 1.5, 1.75]` | list | Shield guard multiplier per skill tier (shield.guardMax times this): a leader's guard outlasts a grunt's. |

### `[civilians]`

Ancient Warfare's civilians, workers, and traders fled instead of fighting.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Settlers and workers (including traders, priests, bards, townsfolk) flee threats and fights only when cornered or levied. Off: every NPC fights. |
| `fleeRange` | `16.0` | 2.0 to 64.0 | A civilian notices a visible threat this many blocks away, and runs home only if the danger is farther than this from home. |
| `fleeHeight` | `3.0` | 1.0 to 16.0 | Vertical reach of that look-around, in blocks. |
| `fleeDistance` | `16.0` | 2.0 to 64.0 | How far, in blocks, a civilian runs from a threat when home is no refuge. |
| `fleeSpeed` | `1.2` | 0.5 to 3.0 | Speed multiplier while fleeing. |
| `stayAwayTicks` | `50` | 0 to 1200 | Ticks a civilian keeps away after losing sight of the threat. |
| `soldierCalmTicks` | `10` | 0 to 200 | Ticks taken off that for each allied soldier nearby, as Ancient Warfare's fear level did. Also the shortest stay. |
| `cornerRange` | `2.5` | 0.5 to 8.0 | A threat this close, in blocks, with no way left to run, corners a civilian. |
| `cornerTicks` | `20` | 0 to 200 | Ticks spent cornered before a civilian stands its ground and fights with whatever it holds. |
| `standGroundTicks` | `100` | 1 to 1200 | Ticks a cornered civilian fights before trying to run again. |
| `standBreakRange` | `6.0` | 1.0 to 32.0 | A cornered civilian stops fighting once its threat backs off this far, in blocks. It never chases. |
| `playerThreatHealth` | `0.5` | 0.0 to 1.0 | Civilians ignore a player who hits them (no running, no fighting back, no path for help) until their health drops below this fraction. |

### `[proficiency]`

How well an NPC fights outside its trade, from the AncientTactics trial, and the emergency levy.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `archerMelee` | `0.5` | 0.0 to 2.0 | Melee damage multiplier for an archer: a bow or crossbow in hand or backpack, so a sidearm swap counts. AncientTactics used 0.5. |
| `skirmisherMelee` | `0.75` | 0.0 to 2.0 | Melee damage multiplier for a skirmisher: a throwing weapon or trident in hand or backpack. AncientTactics used 0.75. |
| `levyDamage` | `0.7` | 0.0 to 2.0 | Damage multiplier, melee and arrows, for a levied civilian. Stacks with the two above. |
| `levyWindupScale` | `1.3` | 0.5 to 3.0 | Extra wind-up multiplier for a levied civilian, on top of combat.npcWindupScale. |
| `levyBreakHealth` | `0.3` | 0.0 to 1.0 | Below this fraction of its health a levy breaks and runs like any civilian. Levies never bash or feint. |

### `[allies]`

Owned NPCs defending their owner, and NPCs calling allies for help.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `ownerDefense` | `true` | true / false | Owned NPCs attack whoever hurts their owner, and whatever their owner attacks, as in Ancient Warfare. |
| `ownerDefenseRange` | `32.0` | 4.0 to 128.0 | How close, in blocks, an owned NPC must be to its owner to join the owner's fight. |
| `alertRange` | `16.0` | 0.0 to 64.0 | When an NPC is hurt, allies within this many blocks (same faction, or same owner) that have no target attack the attacker. 0 turns it off. |
| `alertHeight` | `8.0` | 0.0 to 32.0 | Vertical reach of that alert, in blocks, above and below the hurt NPC. |

### `[cavalry]`

Riders: faction cavalry and mounted archers, and owned NPCs put on a mount.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `mountSpeed` | `1.5` | 0.5 to 4.0 | Movement speed multiplier on a mount while an NPC rides it. Ancient Warfare used 1.5. |
| `reachBonus` | `1.0` | 0.0 to 4.0 | Extra melee reach, in blocks, from the saddle. A rider sits higher, so ground targets are farther away. |
| `remountRange` | `16.0` | 2.0 to 64.0 | A rider thrown from a living mount walks back to it within this many blocks. Farther, the mount is given up. |
| `mountRange` | `6.0` | 1.0 to 32.0 | How close, in blocks, a mount must be for the inventory Mount command. |

### `[relations]`

Faction relations and player standing, from Ancient Warfare 2 Tweaked. The table is assets/ancientempires/npc/factions.json.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Factions fight the factions they list, and players (and their NPCs) whose standing with them is below zero. Off: faction NPCs ignore players and owned NPCs fight every faction. |
| `revengeRange` | `50.0` | 0.0 to 128.0 | When a player (or their NPC) kills a faction NPC, its faction-mates within this many blocks turn on that player. Ancient Warfare used 50. |
| `revengeTicks` | `6000` | 0 to 72000 | How long that grudge lasts, in ticks. Ancient Warfare used 6000 (5 minutes). |
| `standingLimit` | `1000` | 1 to 100000 | Standings stay between minus and plus this value. |
| `countCreativeKills` | `false` | true / false | Whether kills by players in creative mode change standing and start grudges. |

### `[immigration]`

Settlers' Posts: Dwarf Fortress style immigration. Checked once per in-game day, at the day change.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Whether open posts draw settlers at all. |
| `chance` | `0.5` | 0.0 to 1.0 | Chance each day that an open post with room and food gets a group. |
| `maxGroup` | `3` | 1 to 16 | Largest group in one day. Free beds and food can make it smaller. |
| `bedRadius` | `32` | 4 to 96 | Beds within this many blocks of the post count as room, less the owner's NPCs already there. |
| `spawnDistance` | `24.0` | 4.0 to 64.0 | How far out, in blocks, newcomers appear before walking in. |
| `foodPerSettler` | `1` | 0 to 64 | Food bundles each newcomer takes from the post's store. 0 makes beds the only limit. |
| `foodCapacity` | `64` | 1 to 1024 | Food bundles a post can store. |

### `[work]`

Workers at worksites. The tool in a Worker-path NPC's main hand picks its trade (hoe, axe, pickaxe, shears, bow, fishing rod, bowl); it works its owner's nearest worksite of that kind by hand.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `farmDefaultSize` | `9` | 1 to 129 | A new crop farm's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Farmers hold a hoe. |
| `farmMaxSize` | `[15, 21, 27]` | list | Largest crop farm width or length: without an upgrade, with the medium upgrade, with the large one. |
| `scanTicks` | `100` | 10 to 1200 | Ticks between rebuilds of a worksite's to-do list. It is rebuilt only while a worker asks for work. |
| `claimTicks` | `400` | 20 to 2400 | How long a worker's hold on a tile or animal lasts, in ticks, so two workers do not go for the same one. |
| `actionTicks` | `12` | 1 to 100 | Ticks from a worker's swing to the action landing (digging takes a player's mining time instead). |
| `supplyReserve` | `16` | 1 to 64 | Supplies a worker keeps on hand (a farmer's seeds, a woodcutter's saplings, a rancher's feed); the rest goes into the worksite's store. |
| `searchRange` | `32` | 8 to 96 | How far from its home, in blocks, a worker looks for its owner's worksite. |
| `walkSpeed` | `0.9` | 0.3 to 1.5 | Walking speed while working, as a multiple of the NPC's speed. |
| `areaReach` | `8` | 0 to 32 | How far, in blocks, a worksite's area may sit from its block (0: it must touch or contain the block). |
| `toolWear` | `0.2` | 0.0 to 1.0 | Chance each work action wears the worker's tool by one, as a player's would be (Unbreaking still applies). 1 is a player's rate, 0 never wears. Ancient Warfare's workers did not wear tools. |
| `treeDefaultSize` | `13` | 1 to 129 | A new tree farm's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Woodcutters hold an axe. |
| `treeMaxSize` | `[21, 27, 33]` | list | Largest tree farm width or length: without an upgrade, with the medium upgrade, with the large one. |
| `treeSpacing` | `3` | 1 to 8 | Saplings are planted on a grid this many blocks apart, so grown trees have room. |
| `treeReach` | `4` | 1 to 12 | How far sideways from its base a tree's logs and branches can reach and still count as that tree. |
| `treeMaxLogs` | `128` | 8 to 1024 | Most logs one tree can have; bigger structures are not felled past this. |
| `quarryDefaultSize` | `7` | 1 to 129 | A new quarry pit's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. The pit starts in front of the block. Miners hold a pickaxe. Quarries take the quarry upgrades (Ancient Warfare: 16, 32, 64). |
| `quarryMaxSize` | `[16, 32, 64]` | list | Largest quarry pit width or length: without an upgrade, with the medium upgrade, with the large one. |
| `quarryMaxDepth` | `32` | 1 to 256 | Layers a quarry digs below its block before it is done. |
| `quarryStairWidth` | `2` | 1 to 3 | Width of the spiral stair a quarry leaves around its rim, in blocks. Wider is safer to walk; corners narrow so nobody hits their head. |
| `quarryStairStone` | `true` | true / false | Miners dig out stair blocks that are not plain stone (ore, dirt, sand, gravel, gaps) and lay stone they carry (the ancientempires:stair_stone item tag) in their place. |
| `ranchDefaultSize` | `11` | 1 to 129 | A new animal pen's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Ranchers hold shears. |
| `ranchMaxSize` | `[21, 27, 33]` | list | Largest animal pen width or length: without an upgrade, with the medium upgrade, with the large one. |
| `ranchHerdSize` | `8` | 2 to 64 | Adults of each kind a pen keeps: ranchers breed up to it and cull past it. |
| `huntDefaultSize` | `49` | 1 to 129 | A new hunting ground's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Hunters hold a bow. |
| `huntMaxSize` | `[81, 97, 129]` | list | Largest hunting ground width or length: without an upgrade, with the medium upgrade, with the large one. |
| `huntKeepPerKind` | `2` | 0 to 16 | Animals of each kind hunters leave in the ground, so it recovers. |
| `huntGameCap` | `6` | 0 to 64 | Game wanders in (from the biome's creature list, modded animals too) only while the ground holds fewer than this. |
| `huntSpawnInterval` | `2400` | 200 to 72000 | Ticks between creatures wandering into a worked hunting ground. This sets a hunter's yield. |
| `huntShootRange` | `16.0` | 4.0 to 48.0 | How far, in blocks, a hunter shoots from, with a clear line. |
| `fishDefaultSize` | `9` | 1 to 129 | A new fishing dock's width and length, in blocks. Sneak-click the block empty-handed to move or resize it. Fishers hold a fishing rod. |
| `fishMaxSize` | `[17, 23, 29]` | list | Largest fishing dock width or length: without an upgrade, with the medium upgrade, with the large one. |
| `fishRecast` | `true` | true / false | A fisher casts again into the same water after a catch, until its backpack is full. Off: it picks new water each time. |
| `cookTicks` | `100` | 10 to 1200 | Ticks a cook takes to smoke one raw food, and the fuel it burns (a smoker takes 100). Cooks hold a bowl. |
| `cookBundleNutrition` | `24` | 1 to 200 | Hunger of cooked food a cook packs into one food bundle. The crafting recipe takes about 37 and a water bottle. |
| `cookBundleTarget` | `16` | 1 to 1024 | A cook stops packing bundles once the kitchen holds this many. |

### `[upkeep]`

Food and the Town Hall, as in Ancient Warfare. NPCs feed themselves; nothing here runs on a timer.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Whether owned NPCs need food. Each eats a ration a day from its backpack, and fetches more from its owner's Town Hall. Off: nobody goes hungry. |
| `range` | `100.0` | 8.0 to 256.0 | NPCs fetch food from, and have their deaths recorded at, their owner's hall within this many blocks. Ancient Warfare used about 100. |
| `ration` | `6` | 1 to 40 | Nutrition one NPC eats a day. Food counts by its hunger value (bread is 5). |
| `bundleRations` | `8` | 1 to 64 | Daily rations in one food bundle. |
| `mealInterval` | `24000` | 1200 to 240000 | Ticks between an NPC's meals. 24000 is one Minecraft day. A new NPC's first meal is one interval after it appears. |
| `carryRations` | `3` | 1 to 32 | Rations an NPC takes from its hall when its backpack has no food, so it can eat on the road (campaigns) for a while. |
| `delivery` | `WALK` | WALK, TELEPORT | WALK: an NPC out of food walks to its hall to take some. TELEPORT: the food appears in its backpack, from the hall's store. |
| `requireHall` | `false` | true / false | If on, an NPC with no food and no Town Hall in reach goes hungry. Off: only NPCs near a hall depend on it; others live off the land. |
| `burningPanic` | `true` | true / false | An NPC on fire and not fighting runs for water nearby, as vanilla mobs panic. Civilians always do. |
| `fallenRecords` | `16` | 1 to 128 | Fallen NPCs a hall remembers for revival; the oldest go first. |
| `keepOnDeath` | `NONE` | NONE, EQUIPMENT, BACKPACK, ALL | Like Ancient Warfare Tweaked's keep-equipment option: when a Town Hall records an owned NPC's death, what it keeps for revival instead of dropping. NONE, EQUIPMENT (armor and hands), BACKPACK, or ALL. Deaths no hall records drop everything as usual. |

### `[perfectBlock]`

Perfect blocks, for players and NPCs alike: a shield raised just before a melee hit lands turns it aside whole, costing no guard, and the attacker is staggered (slowed, no swing). Held up longer it is an ordinary block.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Perfect blocks for players and NPCs. |
| `windowTicks` | `6` | 1 to 40 | A hit landing within this many ticks of raising the shield is a perfect block. |
| `cooldownTicks` | `24` | 0 to 200 | A raise counts for a perfect block only this many ticks after the last raise, so tapping the shield does not farm them. |
| `vsPlayerScale` | `0.35` | 0.0 to 1.0 | A player's swing shows no wind-up for an NPC to time against, so against players a shield NPC whose raised shield takes a hit turns it into a perfect block at this fraction of its skill's chanceByTier (a recruit almost never, a champion now and then), at most once per cooldownTicks. |
| `staggerTicks` | `20` | 0 to 100 | Ticks an attacker is staggered by a perfect block: slowed, and no swing. |
| `chanceByTier` | `[0.05, 0.15, 0.3, 0.45]` | list | Per skill tier (ordinary, elite, leader, elite leader): the chance a shield NPC with its shield down holds the raise for a perfect block against an NPC's visible wind-up, instead of raising at once. Players show no wind-up, so against them it raises at once. |
| `timingErrorByTier` | `[6, 4, 3, 2]` | list | Per skill tier: up to how many ticks an NPC's perfect-block raise is mistimed. Late, the hit lands; early, an ordinary block. |

### `[command]`

Command: ranked soldiers (sergeants, captains, generals) inspire the allied soldiers around them (more damage with any melee weapon, more armour). When a commander falls, the highest-ranked allied soldier near it takes command, weakened, and turns on the killer.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Commanders and their aura. |
| `strengthByRank` | `[0.0, 0.5, 1.0, 1.25]` | list | Aura strength by rank: soldier, sergeant, captain, general (0 commands nobody). Faction leaders are captains, elite leaders generals. |
| `radiusByRank` | `[0.0, 8.0, 12.0, 16.0]` | list | Blocks the aura reaches, by rank. |
| `promoteLevels` | `[1, 6, 12, 18]` | list | Level a player's soldier needs to be promoted to each rank with its baton: soldier, sergeant, captain, general. |
| `auraTicks` | `40` | 10 to 400 | Ticks between aura pulses; it lasts two pulses and a second, so it lapses soon after leaving. |
| `damageBonus` | `0.15` | 0.0 to 2.0 | Extra melee damage for the inspired, as a fraction (0.15 is 15% more). |
| `armorBonus` | `2.0` | 0.0 to 20.0 | Extra armour points for the inspired. |
| `successionRange` | `16.0` | 2.0 to 64.0 | Blocks from a fallen commander within which a successor is found. |
| `successionScale` | `0.6` | 0.1 to 1.0 | A successor's aura, as a fraction of the fallen commander's. |
| `minScale` | `0.3` | 0.05 to 1.0 | The weakest a passed-down aura gets, however many times command passes. |

### `[chain]`

Chain of command (see [chain-of-command.md](chain-of-command.md)): a player's sergeants, captains, and generals lead units of the soldiers assigned to them with the Signal Flag; a unit given Follow forms up on its leader and keeps formation. Off: every NPC acts alone, as before.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Units, orders, and formations. |
| `squadSize` | `16` | 1 to 64 | Soldiers one leader can have directly under it (a squad). |
| `squadsPerCaptain` | `4` | 1 to 16 | Sergeants (with their squads) a captain can have under it. |
| `captainsPerGeneral` | `0` | 0 to 64 | Captains and sergeants a general can have under it; 0 for any number. |
| `spacing` | `1.5` | 1.0 to 6.0 | Blocks between soldiers in formation (loose order doubles it). |
| `lineWidth` | `8` | 1 to 32 | Soldiers abreast in a line (and in loose order). |
| `columnWidth` | `2` | 1 to 8 | Soldiers abreast in a column. |
| `slotTolerance` | `1.5` | 0.5 to 8.0 | Blocks a soldier may stand off its slot before it moves back into it. |
| `catchUpDistance` | `6.0` | 2.0 to 64.0 | Blocks off its slot past which a soldier runs to catch up instead of walking. |
| `catchUpSpeed` | `1.12` | 0.5 to 1.4 | Speed multiplier while catching up (a player's sprint is about 1.12). |
| `walkSpeed` | `0.9` | 0.3 to 1.4 | Speed multiplier for small steps back into the slot. |
| `holdRange` | `4.0` | 1.0 to 32.0 | Hold: blocks from its place in the line within which a soldier fights; it does not chase further. |
| `defendRadius` | `16.0` | 2.0 to 64.0 | Defend: blocks from the defended spot within which the unit goes after enemies. |
| `followLeash` | `24.0` | 4.0 to 64.0 | Follow: blocks from the unit's leader within which its soldiers fight; they let go of anything further. |
| `rangedLeash` | `24.0` | 0.0 to 64.0 | Extra blocks of reach for a soldier with a bow, so a holding line of archers still shoots. |
| `cornerRange` | `2.5` | 1.0 to 8.0 | Fall back: an enemy this close is fought anyway (cornered). |
| `planTicks` | `20` | 5 to 200 | Ticks between a leader's looks over the field for its unit (one scan for the whole unit; its soldiers do none). |
| `engageRange` | `16.0` | 4.0 to 64.0 | Blocks around a leader in which it picks enemies for its unit (the order's leash still applies to each soldier). |
| `releaseStagger` | `3` | 0 to 40 | Ticks between soldiers released into a fight, nearest the enemy first. |
| `spreadWeight` | `3.0` | 0.0 to 32.0 | How much a soldier prefers an enemy fewer of its unit are on: blocks of extra distance per soldier already on it. 0 lets them all pile on the nearest. |

### `[sunlight]`

Daylight: factions with "sunlight": "burn" in factions.json (the undead) burn in daylight like zombies (a helmet takes the wear) and seek shade once burning; "avoid" (vampires, as in Ancient Warfare) also keep to shade by day.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Turns daylight burning and shade-seeking on or off for every faction. |

### `[skirmish]`

Skirmishers (DESIGN Tier 2, AncientTactics' numbers): an NPC holding a Spartan throwing weapon (javelin, tomahawk, throwing knife, boomerang) throws it at range, kites foes that close in, and draws its best backpack melee weapon when cornered or out of throws.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Turns skirmishers (NPCs holding a Spartan throwing weapon) on or off. |
| `minRange` | `4.5` | 1.0 to 32.0 | Closest it throws from. |
| `maxRange` | `28.0` | 4.0 to 64.0 | Farthest it throws from; beyond, it closes in. |
| `dangerRange` | `6.0` | 0.0 to 32.0 | A foe closer than this is kited: the skirmisher steps back instead of throwing. |
| `throwCooldownTicks` | `40` | 0 to 400 | Ticks between throws. |
| `throwSpeed` | `2.2` | 0.5 to 5.0 | Throw speed (a full bow shot is 3). |
| `restockTicks` | `100` | 1 to 24000 | Out of a fight, one spent throw comes back every this many ticks. |

### `[governor]`

Governor (DESIGN Tier 1): an owned Worker-path NPC holding a map goes to its owner's nearest Town Hall by day; the owner's workers around it work faster, and each one it oversees earns the hall a tax.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Turns the Governor (a worker holding a map) on or off. |
| `pulseTicks` | `40` | 10 to 400 | Ticks between the governor's rounds (aura and tax). |
| `auraRadius` | `24.0` | 4.0 to 64.0 | Blocks around the governor its aura reaches. |
| `workBonus` | `0.15` | 0.0 to 2.0 | Extra work speed for governed workers, as a fraction (0.15 is 15% faster). |
| `taxItem` | `minecraft:gold_nugget` | text | The tax, put in the Town Hall's store. |
| `taxTicksPerWorker` | `2400` | 0 to 240000 | Ticks of oversight per worker for one tax item (2400: one nugget per worker every two minutes). 0 turns tax off. |

### `[structures]`

Structure templates (Ancient Warfare's library, converted), placed by `/ancientempires structure place` and by world generation. This section also holds the settings for claiming a faction structure, its block protection and loot lock, and gates. The world-generation settings that live here are listed separately under [World generation](#world-generation) below.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `templateCacheMb` | `256` | 16 to 4096 | Memory for templates kept loaded, in MB; the least recently used are dropped past it. The largest AW island takes about 27. |
| `missingBlock` | `minecraft:air` | text | What a block becomes when neither this game nor the ae_remap tables know it (a mod that is not installed). |
| `missingEntity` | `minecraft:zombie` | text | What a spawner's mob becomes when neither this game nor the ae_remap tables know it (a mod that is not installed). Empty: nothing spawns. |
| `triggerMinRange` | `10` | 0 to 64 | An AW garrison's trigger range, raised to at least this many blocks when a structure is placed, so tight structures do not spawn their people on top of the player. 0 keeps AW's own. |
| `triggerRangeScale` | `1.5` | 0.5 to 4.0 | An AW garrison's trigger range is multiplied by this when placed (AW staged fights by range; scaling keeps the order: walls first, leaders last). |
| `claimThreshold` | `5` | 1 to 1000 | Claiming a structure's protection flag (AW's conquest): the hostiles still inside must score under this. 1: not one may be left. |
| `claimNormalPoints` | `1` | 0 to 1000 | Points an ordinary hostile NPC inside adds. |
| `claimElitePoints` | `2` | 0 to 1000 | Points an elite adds. |
| `claimLeaderPoints` | `5` | 0 to 1000 | Points a leader adds (AW's boss): with the default threshold, a living leader alone keeps the flag. |
| `claimSpawnerPoints` | `1` | 0 to 1000 | Points a hostile spawner still holding its people adds. |
| `claimStandingCost` | `10` | 0 to 1000 | Standing lost with the structure's faction for claiming it. |
| `claimWitnessTicks` | `1200` | 0 to 24000 | A failed claim marks what stands in the way (they glow) for this many ticks. |
| `protectionMultiplier` | `100.0` | 1.0 to 10000.0 | Blocks of an unclaimed faction structure take this many times as long to mine (AW's block protection). 1 turns it off. |
| `lootLock` | `true` | true / false | Containers in an unclaimed faction structure stay shut until it is claimed (AW's loot chest protection). |
| `allowStealing` | `true` | true / false | With lootLock, a container can still be opened while none of the faction can see you (AW's stealing). |
| `gateHealth` | `80` | 1 to 10000 | A gate's health (AW's 80). Explosions do ten times their damage; an axe half against wood, a pickaxe a half or third against iron, anything else a quarter; arrows nothing. |
| `gateMoveSpeed` | `0.05` | 0.005 to 1.0 | Blocks a gate's wall moves per tick (AW's was 0.025: a five-high gate took ten seconds). |
| `gateBridgeSpeed` | `1.0` | 0.1 to 10.0 | Degrees a drawbridge turns per tick (AW's 1: ninety ticks to lower). |
| `npcUse` | `false` | true / false | NPCs open and close gates for themselves (beyond AW, where gates were walls to NPCs). Only a player's own NPCs, through their owner's gates (built or claimed), never a garrisoned fort's, never while fighting or with an enemy near the gate. |
| `npcHostileRange` | `16.0` | 0.0 to 64.0 | With npcUse: no NPC opens a gate while a monster or a faction NPC hostile to its owner is this close to it. |

### `[morale]`

Morale, Total War style: soldiers waver, rout, and shatter. Each soldier's morale (0 to 100) drifts toward what its situation is worth: its rank's base, a commander's aura, the odds around it, its wounds, a recent hit from behind, hunger, and routing allies; deaths and kills nearby shock it. Below routBelow it runs (as civilians do, fighting only when cornered) and rallies once recovered; the third rout shatters it for good. Fearless factions (factions.json) never break.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Morale on. Off, soldiers fight to the death as before. |
| `ownedNpcs` | `true` | true / false | Players' own soldiers have morale too. |
| `baseByTier` | `[60.0, 70.0, 80.0, 90.0]` | list | Base morale by skill tier: ordinary, elite, leader, elite leader. |
| `updateTicks` | `20` | 5 to 200 | Ticks between morale checks per soldier (staggered). |
| `range` | `12.0` | 4.0 to 48.0 | Blocks around a soldier counted for the odds and routing allies. |
| `waverBelow` | `50.0` | 0.0 to 100.0 | Below this, the soldier wavers (a warning badge). |
| `routAt` | `12.0` | 0.0 to 100.0 | Below this, the soldier routs. |
| `rallyAbove` | `40.0` | 0.0 to 100.0 | A routing soldier rallies once its morale is back above this. |
| `minRoutTicks` | `200` | 0 to 6000 | A rout lasts at least this long before it can rally. |
| `shatterAfterRouts` | `3` | 1 to 20 | The rout that shatters a soldier: it cannot rally again, and faction soldiers leave the field. |
| `fallSpeed` | `0.15` | 0.01 to 1.0 | Fraction of the gap closed per check when morale is falling. |
| `riseRate` | `0.1` | 0.01 to 1.0 | Fraction of the gap closed per check when morale is rising (slower: heart is lost faster than it is regained). |
| `oddsWeight` | `15.0` | 0.0 to 100.0 | Morale from the odds: the full amount when alone against many (or many against none). Each side is weighed by health, so a giant counts for several men. |
| `woundsWeight` | `20.0` | 0.0 to 100.0 | Morale lost at the brink of death, in proportion to health lost. |
| `flankPenalty` | `15.0` | 0.0 to 100.0 | Morale lost while recently hit from behind or the side. |
| `flankTicks` | `60` | 0 to 1200 | How long a hit from behind counts. |
| `hungerPenalty` | `10.0` | 0.0 to 100.0 | Morale lost while hungry (upkeep). |
| `panicPerRouter` | `4.0` | 0.0 to 100.0 | Morale lost for each routing ally in range: panic spreads. |
| `routingAllyCap` | `24.0` | 0.0 to 100.0 | The most morale routing allies can take away. |
| `inspiredBonus` | `20.0` | 0.0 to 100.0 | Morale from a commander's aura, times the commander's strength. |
| `commanderDeathShock` | `35.0` | 0.0 to 100.0 | Shock when an allied commander falls. |
| `commanderDeathRange` | `32.0` | 0.0 to 128.0 | Blocks within which a commander's death is felt. |
| `allyDeathShock` | `5.0` | 0.0 to 100.0 | Shock when an ally falls close by. |
| `allyDeathRange` | `8.0` | 0.0 to 64.0 | Blocks within which an ally's death is felt. |
| `killBoost` | `6.0` | 0.0 to 100.0 | Morale gained by landing a kill. |
| `shockDecay` | `1.5` | 0.0 to 100.0 | How much a shock wears off per check. |
| `hornRally` | `60.0` | 0.0 to 100.0 | A War Horn rally lifts its soldiers' morale to at least this, and rallies the routing (not the shattered). |
| `surrender` | `true` | true / false | Cornered, badly hurt, routing faction soldiers may surrender: they kneel and nobody attacks them. Hit one and it shatters and runs. |
| `surrenderHealth` | `0.4` | 0.0 to 1.0 | Health fraction below which a cornered router may surrender. |
| `surrenderChance` | `0.5` | 0.0 to 1.0 | Chance to surrender each time it is cornered (otherwise it fights its way out). |
| `surrenderReleaseTicks` | `600` | 20 to 24000 | With no enemy in range this long, a surrendered soldier gets up and leaves, shattered. |
| `shatteredDesert` | `true` | true / false | Shattered faction soldiers leave the field (despawn) once no player is near. Named ones stay. |
| `desertRange` | `48.0` | 8.0 to 256.0 | No player within this many blocks: a shattered faction soldier is gone. |
| `shatteredRecoverTicks` | `1200` | 20 to 24000 | A player's shattered soldier recovers after this long with no enemy in range. |
| `ownedBaseBonus` | `10.0` | 0.0 to 100.0 | Extra base morale for players' own soldiers: losing your army unexpectedly feels worse than routing an enemy feels good. |
| `ownerPresenceBonus` | `15.0` | 0.0 to 100.0 | Morale a player's soldiers draw from their owner fighting nearby, as from a general. |
| `ownerPresenceRange` | `16.0` | 0.0 to 64.0 | Blocks within which the owner's presence counts. |
| `chaseRange` | `8.0` | 0.0 to 64.0 | Foot soldiers let routing enemies go once they are this far: they hold the field instead of scattering after them. Riders run them down at any range, as cavalry does. 0 chases everywhere. |
| `bannerBonus` | `10.0` | 0.0 to 100.0 | Banner bearers: a soldier with a banner in its off hand steadies allies within range by this much morale; when it falls, allies in range take bannerDeathShock. |
| `bannerDeathShock` | `15.0` | 0.0 to 100.0 | Morale lost by allies in range when a banner bearer dies (see bannerBonus). |

### `[levels]`

Levels: NPCs (soldiers and workers; not settlers) earn XP and level up to maxLevel. A level is mostly skill: the skill section's four-entry lists are read along the levels (entry 1 at level 1, entry 4 at the cap), so every level counts. Bands of five: soldiers Recruit, Regular, Veteran, Champion; workers Apprentice, Journeyman, Expert, Master. Ancient Warfare capped at 10 and gave XP only for kills.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | NPCs earn XP and level up. Off, levels stay as they are. |
| `maxLevel` | `20` | 4 to 100 | The highest level. |
| `xpBase` | `20.0` | 1.0 to 100000.0 | XP from level 1 to 2. |
| `xpStep` | `15.0` | 0.0 to 100000.0 | Extra XP needed for each level after that (20 then 35, 50, ...: 720 XP to level 10, 2945 to 20). |
| `xpPerDamage` | `0.2` | 0.0 to 100.0 | XP per point of damage dealt to an enemy, after armour (2 points is a heart). |
| `xpPerKill` | `3.0` | 0.0 to 1000.0 | XP for a kill. |
| `xpPerVictimLevel` | `0.5` | 0.0 to 1000.0 | Extra XP per level of a slain NPC (other creatures count as level 1). |
| `xpPerBlock` | `0.4` | 0.0 to 1000.0 | XP for a hit taken on the shield. |
| `xpPerPerfectBlock` | `2.0` | 0.0 to 1000.0 | XP for a perfect block. |
| `xpPerWork` | `0.1` | 0.0 to 1000.0 | XP for each work action (a harvest, a log, a block dug, a catch, a dish). |
| `healthPerLevel` | `0.25` | 0.0 to 10.0 | Max health per level past the first (0.25: about two and a half hearts at 20). |
| `damagePerLevel` | `0.01` | 0.0 to 1.0 | Melee and ranged damage per level past the first, as a fraction (0.01: 19% more at 20; Ancient Warfare gave 0.05). |
| `workSpeedPerLevel` | `0.03` | 0.0 to 1.0 | Workers: work speed per level past the first, as a fraction (0.03: 57% faster at 20; Ancient Warfare gave 0.05). |
| `toolWearCutPerLevel` | `0.02` | 0.0 to 0.1 | Workers: tool wear cut per level past the first, as a fraction (0.02: 38% less at 20). |
| `factionMinByTier` | `[1, 8, 12, 16]` | list | Faction NPCs start at a random level by rank: the lowest for ordinary, elite, leader, elite leader. |
| `factionMaxByTier` | `[5, 12, 16, 20]` | list | The highest starting level by rank. |
| `announce` | `true` | true / false | Tell an owner on screen when one of their NPCs levels up. |

### `[nightLight]`

Night watch lights: soldiers awake at night or in the dark hold up a torch or lantern (the ancientempires:night_lights item tag) in a free off hand. Dynamic light mods light them.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Soldiers carry lights at night. Owned ones use torches or lanterns from their backpack and put them back at dawn. |
| `factionGuards` | `true` | true / false | Faction soldiers with a free off hand are issued a torch or lantern for the night. It never drops or repacks. |
| `darkness` | `7` | 0 to 16 | Below this light level (0 to 15) it counts as dark by day too, as in caves. |
| `watchDamageBonus` | `0.1` | 0.0 to 2.0 | Night Watch (DESIGN Tier 2): soldiers awake at night or in the dark (the same test as the lights) hit this much harder, as a fraction, and keep watchMorale more morale: the watch expects trouble. |
| `watchMorale` | `5.0` | 0.0 to 100.0 | Extra morale for soldiers on night watch (see watchDamageBonus). |

### `[horn]`

The War Horn: blown by an owner to rally their soldiers (Rally), also levy their workers and settlers (Call to Arms), or send everyone back (Stand Down). Sneak-use switches the call.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `range` | `48.0` | 8.0 to 256.0 | Blocks from the horn within which the owner's NPCs answer it. |
| `cooldownTicks` | `100` | 0 to 1200 | Ticks before the horn can be blown again. |
| `rallyTicks` | `600` | 20 to 6000 | Ticks an NPC that answered a rally shows the alarm icon. |

### `[medic]`

Medics: soldiers with bandages in the off hand.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `bandageHeal` | `6.0` | 1.0 to 40.0 | Health one bandage restores, for a medic's patient or a player using one. |
| `range` | `16.0` | 2.0 to 48.0 | How far, in blocks, a medic looks for the wounded: allies, its owner, and itself. |
| `healBelow` | `0.7` | 0.1 to 1.0 | A medic tends those below this fraction of their health, the worst first. |
| `bindTicks` | `20` | 1 to 200 | Ticks a medic spends binding a wound once beside the patient. |
| `cooldownTicks` | `40` | 0 to 1200 | Ticks between bandages. |
| `meleeProficiency` | `0.75` | 0.0 to 2.0 | Melee damage multiplier for a medic: its hands are for healing, so not every soldier should be one. |

### `[priest]`

Priests: workers holding a book raise the fallen recorded at their owner's Town Hall.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `reviveTicks` | `100` | 20 to 2400 | Ticks a priest prays at the hall before one of the fallen rises. |
| `costEnabled` | `true` | true / false | Whether raising the fallen costs anything. The cost is taken from the hall's store. |
| `costItem` | `minecraft:gold_ingot` | text | Item id of the cost. |
| `costCount` | `2` | 1 to 64 | How many of the cost item one raising takes. |

### `[sleep]`

Sleeping in beds at night, as villagers do.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `enabled` | `true` | true / false | Owned workers and settlers claim a free bed near home and sleep in it at night. |
| `soldiers` | `false` | true / false | Soldiers sleep too. Off by default: they stay on watch (room for day and night patrols later). |
| `bedRange` | `32` | 4 to 96 | How far from its home, in blocks, an NPC looks for a free bed to claim. |

### `[hazard]`

Hazard avoidance: paths and the spots NPCs pick for themselves stay clear of lava, fire, cliffs, and deep water.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `maxFall` | `3` | 0 to 16 | Tallest drop, in blocks, an NPC's path may include. Vanilla lets a healthy mob in a fight take bigger ones, which is how mobs leap off cliffs. |
| `waterCost` | `8.0` | -1.0 to 64.0 | Path cost of water, as vanilla's malus: higher walks further to stay dry. -1 forbids it. |
| `fireCost` | `16.0` | -1.0 to 64.0 | Path cost next to fire (twice this through it). Vanilla uses 8. |
| `avoidWaterSpots` | `true` | true / false | Fleeing, wandering, and repositioning never pick a spot in deep water. |
| `safeSpotTries` | `10` | 1 to 40 | Random spots tried before an NPC gives up looking for a safe one this tick. |

### `[targeting]`

How NPCs pick who to fight. The defaults are tuned for big battles; `debugStats` is useful for profiling.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `legacyScan` | `false` | true / false | Use the two vanilla nearest-target goals instead of the shared scan. For comparison and as a fallback. Applies to NPCs loaded after the change. |
| `scanInterval` | `10` | 1 to 200 | Ticks between target scans for an NPC with no target. Each NPC is offset by its entity id so scans spread across ticks. |
| `retargetInterval` | `40` | 0 to 400 | Ticks between rescans while an NPC already has a scanned target. 0 keeps the current target until it is lost. |
| `switchMargin` | `3.0` | 0.0 to 32.0 | A rescan only switches when the new target scores at least this much better (in effective blocks) than the current one. |
| `threatScoring` | `true` | true / false | Threat scoring: a target's score is its distance less the bonuses below (in blocks); the lowest wins. Off: the nearest hostile in sight. |
| `ownerThreatWeight` | `16.0` | 0.0 to 64.0 | Bonus for an enemy attacking the NPC's owner. |
| `allyThreatWeight` | `8.0` | 0.0 to 64.0 | Bonus for an enemy attacking an ally (same faction or owner). |
| `selfThreatWeight` | `4.0` | 0.0 to 64.0 | Bonus for an enemy attacking the NPC itself. |
| `woundedWeight` | `6.0` | 0.0 to 64.0 | Bonus for a wounded enemy, scaled by its missing health (all of it at no health): finish them off. |
| `facingWeight` | `2.0` | 0.0 to 64.0 | Bonus for an enemy straight in front of the NPC, less to the side, none behind. |
| `roleWeight` | `6.0` | 0.0 to 64.0 | Role match: an archer's bonus for an enemy with a bow, crossbow, or throwing weapon. |
| `scanRange` | `32.0` | 4.0 to 64.0 | Search radius, in blocks. The NPC's follow range still caps it. |
| `cacheTicks` | `5` | 0 to 40 | Ticks a shared candidate list stays valid for every NPC in the same 16x8x16 cell. 0 queries per NPC. |
| `maxSightChecks` | `8` | 1 to 64 | Line-of-sight checks per scan, nearest candidate first. The scan stops at the first visible one. |
| `debugStats` | `false` | true / false | Log target-scan counts per second to the server log every 10 seconds. |
| `memoryTicks` | `100` | 0 to 1200 | Ticks an NPC searches where it last saw its target before giving up. The clock starts once it reaches that spot. |
| `pursuitTicks` | `600` | 20 to 6000 | Most ticks an NPC chases a target it cannot see, counted from the last sighting, travel included. |


## World generation

Ancient Warfare's structures generate in new chunks, **on by default**. These settings sit in the `[structures]` section of the server config (per world). They only affect chunks generated after a change; structures already standing stay.

| Setting | Default | Allowed | What it does |
|---|---|---|---|
| `worldGen` | `true` | true / false | Master switch (Ancient Warfare's `enable_structure_generation`). Off: no faction structures in new chunks. Placing by command still works. |
| `generationChance` | `0.075` | 0.0 to 1.0 | Chance per chunk of trying for a structure (Ancient Warfare's `random_generation_chance`). Each territory multiplies it by its own `structure_generation_chance_multiplier`, and the budget and spacing below thin it further. |
| `clusterChanceBoost` | `4.0` | 1.0 to 20.0 | Structures gather: a chunk within `clusterRange` of a standing structure rolls `generationChance` times this. 1 turns clustering off. |
| `clusterRange` | `96` | 16 to 512 | How near (blocks, centre to centre) a standing structure must be for the boost. |
| `spawnProtectionRadius` | `400` | 0 to 100000 | No structures within this many blocks of the world spawn (overworld only). |
| `chunkClusterValue` | `1.0` | 0.0 to 100.0 | Each chunk of a territory adds this much to the territory's structure budget, times the territory's own multiplier. Every structure spends its `clusterValue` (a village about 100, a small ruin 10 or less). Raise it for denser regions. |
| `territoryCellChunks` | `20` | 2 to 128 | How wide a territory (one faction's region) is, in chunks. Changing it moves the territories of chunks not yet generated. |
| `siteMargin` | `4` | 0 to 64 | Blocks kept clear between generated structures. |
| `groundSlope` | `3` | 0 to 64 | A land structure is skipped where the ground around it rises or falls more than this many blocks from its floor (or its template's own `leveling` / `fill`, if more), so hillsides are avoided. What passes is prepared as Ancient Warfare did: cleared above the footprint, filled under it, and a border ring (the template's `border`, 3 to 16 blocks) eased from the floor into the land around it. |

**How a spot is chosen** (as Ancient Warfare chose it):

1. The chunk rolls `generationChance` times its territory's multiplier (times `clusterChanceBoost` near a standing structure).
2. Its **territory** is the region it lies in. Each region holds one territory, picked from those with a template for the biome at the region's centre, weighted by how many templates they have (plus the generic ones) times their `territory_weight_multiplier`.
3. A template is picked from the territory's own and the generic ones that list this biome and dimension, still fit the territory's remaining budget, are not a unique one already placed or a copy within its `minDuplicateDistance`, and suit the spot (an island needs water of its depth). Weighted by `selectionWeight`; big ones (`clusterValue` over 50) are less likely away from the territory's centre.
4. The ground around it is checked (dry land within `maxSlope`; water deep enough; an island's sea floor at its depths), and it must stay `siteMargin` (plus its border ring) clear of every other structure.

**Per-territory tuning** is Ancient Warfare's own `territory_settings.json`, shipped as `data/ancientempires/ae_territories/ancientwarfare.json`. A data pack can add its own file in `data/<namespace>/ae_territories/` to retune a territory; later files win per setting. Each territory takes:

| Key | What it does |
|---|---|
| `max_radius_in_chunks` | How far the territory reaches: caps its budget, and how fast big structures fall off from its centre. |
| `per_chunk_cluster_value_multiplier` | Its budget per chunk, times `chunkClusterValue`. Higher: more structures in it. |
| `structure_generation_chance_multiplier` | Times `generationChance`. |
| `territory_weight_multiplier` | How often it is picked where it may stand. This is the knob for, say, more Empire towns and fewer ocean islands. |

A `defaults` block sets values for every territory not listed. Which templates may generate at all is each template's sidecar `validation` (see [data formats](data-formats.md)).

**Finding them**: `/locate structure ancientempires:aw_site` finds land structures, `ancientempires:aw_sea_site` water and island ones.

**Not generated yet**: Ancient Warfare's 28 islands wider than 256 blocks (Minecraft links a structure only to chunks within 8 of where it starts), and its sky, underground, harbor, and lava kinds. Towns are a later stage.
