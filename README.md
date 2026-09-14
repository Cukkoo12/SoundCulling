# Sound Culling

Sound Culling is a client-side Minecraft mod that reduces repetitive sounds in
busy areas, such as farms, spawner rooms and machinery. It can lower a sound's
volume or prevent it from starting when nearby sound activity exceeds the
configured limits.

Download from [Modrinth](https://modrinth.com/mod/sound-culling) or
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/sound-culling).
Choose the file listed for your Minecraft version and mod loader. Files for
different Minecraft versions can have the same JAR name.

## Installation

For Sound Culling 2.0 on Fabric:

1. Install Fabric Loader and Fabric API for your Minecraft version.
2. Place the matching Sound Culling JAR in the instance's `mods` folder.
3. Install Mod Menu if you want to use the settings screen.
4. Start Minecraft. Open Sound Culling through Mod Menu, or use `/soundculling`
   to check its status.

The mod runs on the client; it does not need to be installed on the server.
Without Mod Menu, commands and the configuration file are still available.

The sections below describe the Fabric 2.0 implementation. Older Fabric, Forge
and NeoForge projects in this repository have different feature sets. Refer to
the download page for the supported versions of a particular release.

## Sound handling

The mod tracks sound events in spatial cells over a rolling tick window. Nearby
cells contribute to the same decision, so crossing a cell boundary does not
immediately remove all surrounding sound pressure.

Each decision considers repeated sound IDs, activity at the source position,
category limits and overall event rate. Distance, direction and player-sound
protection can adjust the priority. A source's contribution to the shared
regional count is capped, while repetition at that source is counted separately.

Sounds that exceed the effective limits are dampened or rejected before an
audio channel starts. Music and relative sounds, such as interface audio, bypass
culling. Sounds outside their resolved attenuation range are excluded from the
tracker. An empty category filter enables all otherwise eligible categories.

Looping and tickable sounds are re-evaluated after playback starts. Their volume
falls faster than it recovers, and these checks do not count as new sound events.
Ordinary load-based culling keeps an admitted loop audible; an explicit
`ALWAYS_CULL` rule can fade it to zero. Repeated starts of the same persistent
sound can still be rejected.

This reduces the number of admitted sound starts in noisy areas. It does not
reduce entity ticking, rendering or other work performed by the source mod.
The effect on performance depends on the workload.

## Configuration

Settings are stored in `config/soundculling.json` inside the Minecraft instance.
Close the game before editing this file manually. It is loaded at startup;
there is no file-watching reload.

The settings screen has four pages:

| Page | Purpose |
| --- | --- |
| Overview | Enable the mod, choose a preset and view session statistics. |
| Performance | Adjust adaptive thresholds and category limits. |
| Live Sounds | Inspect recent sound IDs and add or remove rules. |
| Advanced | Adjust spatial tracking, priority options and debug logging. |

Use **Save & Close** to apply edits. **Cancel** discards pending settings changes.
Resetting session statistics takes effect immediately. The screen does not pause
the single-player world.

![Overview settings](docs/images/overview.png)

![Live Sounds inspector](docs/images/live-sounds.png)

Additional screenshots: [Performance](docs/images/performance.png) and
[Advanced](docs/images/advanced.png).

### Presets

| Preset | Use |
| --- | --- |
| Balanced | A starting point for ordinary play. |
| Performance | Lower limits and earlier adaptive pressure for busy areas. |
| Aggressive | Tighter limits for sustained sound spam. |
| Custom | Shown after individual settings are changed. |

Presets change category limits, the regional limit and adaptive thresholds.
They do not remove sound rules or reset every advanced setting.

### Main settings

| JSON field | Meaning |
| --- | --- |
| `enabled` | Enables or disables Sound Culling. |
| `windowTicks` | How long events affect local counts. The allowed range is 5–200 ticks; 20 ticks is about one second at normal tick speed. |
| `regionSize` | Cell width in blocks, from 2 to 64. Larger cells group more sources together. |
| `spatialNeighborRadius` | How many cells to inspect in each direction, from 0 to 2. Larger values increase the area and work per evaluation. |
| `maxTotalPerRegion` | Base regional limit before priority and adaptive adjustments. |
| `limitHostile`, `limitNeutral`, `limitBlock`, `limitAmbient`, `limitDefault` | Base repetition limits for each category. Other categories use `limitDefault`. |
| `adaptiveCulling` | Adjusts limits according to the tracked event rate. |
| `adaptiveStartPerSecond`, `adaptiveFullPerSecond` | Event rates at which adaptive pressure starts and reaches its maximum. |
| `adaptiveStrength` | Strength of the adaptive adjustment, from 0 to 1. |
| `distancePriority`, `directionalPriority` | Adjust priority using distance and the player's facing direction. |
| `protectPlayerSounds` | Adds priority and a minimum volume for player feedback during ordinary culling. |
| `enabledCategories` | Category names to process, such as `blocks` or `ambient`. An empty list adds no category restriction. |
| `debugLogging` | Writes periodic tracker statistics to the log. Disabled by default. |

These limits are inputs to the culling decision, not a hard limit on the number
of active audio channels. The event rate includes tracked sounds that are later
culled; it is not the number of sounds currently playing.

Older configuration files are migrated where possible, and numeric values are
clamped to their supported ranges. If loading fails, defaults are used and saved
to the same path. Back up a broken file before restarting if you need to inspect it.

## Sound rules

Select a sound on the Live Sounds page to protect it, raise its priority, make it
more aggressive or always cull it. **Protect Mod** adds a `NEVER_CULL` rule for
the sound's namespace. **Clear Rule** removes the exact rule first; if none
exists, it tries the namespace rule.

In the current Fabric 26.2 source, visible rows keep their order while their
counters update. Use **Refresh** to load the latest sound list. Rule edits take
effect when settings are saved.

The JSON `rules` array supports these actions:

| Action | Effect |
| --- | --- |
| `NEVER_CULL` | Bypasses Sound Culling's volume reduction and rejection. |
| `CRITICAL` | Applies the highest priority multiplier. |
| `HIGH` | Raises priority. |
| `NORMAL` | Uses the normal priority calculation. |
| `AGGRESSIVE` | Lowers priority. |
| `ALWAYS_CULL` | Rejects new starts and fades tracked active loops toward zero. |

`CRITICAL` and `NORMAL` can be set in JSON; the Live Sounds buttons expose a
subset of the actions. High priority is not an unconditional exemption.

Patterns are case-insensitive and support `*` for any sequence of characters.
For example, this `rules` value reduces piston priority while protecting sounds
from one namespace:

```json
[
  { "pattern": "minecraft:block.piston.*", "action": "AGGRESSIVE" },
  { "pattern": "presencefootsteps:*", "action": "NEVER_CULL" }
]
```

The last matching rule wins. A later `NORMAL` rule can override an earlier
wildcard rule. Exact IDs in `whitelistedSounds` take precedence over all rules,
including `ALWAYS_CULL`; remove an ID from that list if you want to cull it.
Rules only apply to sounds that reach the tracker, so they do not override the
music, relative-sound or category exclusions.

Default rules raise the priority of vanilla player attacks and `bettercombat:*`.
The default whitelist also protects sounds such as player hurt, item pickup and
explosions. These defaults are not a guarantee of compatibility with every mod
that changes sound playback.

## Commands

These are client-side commands and do not require server operator permissions.
Setting commands apply and save immediately.

| Command | Purpose |
| --- | --- |
| `/soundculling` | Show status, preset, event rate, pressure and session counts. |
| `/soundculling toggle` | Enable or disable the mod. |
| `/soundculling preset balanced` | Apply Balanced. Also accepts `performance` and `aggressive`. |
| `/soundculling limit <1–50>` | Change the default category limit and select Custom. |
| `/soundculling total <1–100>` | Change the regional limit and select Custom. |
| `/soundculling reset` | Clear session counters and the recent-sound history. |

Resetting statistics does not clear the rolling activity used for culling.

## Source projects and building

Each version directory is a separate Gradle project. The Fabric 2.0 projects
currently compile against the following Minecraft versions:

| Project | Minecraft build target | JDK |
| --- | --- | --- |
| [`fabric/1211`](fabric/1211) | 1.21.1 | 21 |
| [`fabric/1216`](fabric/1216) | 1.21.6 | 21 |
| [`fabric/12111`](fabric/12111) | 1.21.11 | 21 |
| [`fabric/2612`](fabric/2612) | 26.1.2 | 25 |
| [`fabric/262`](fabric/262) | 26.2 | 25 |

A release may advertise a wider compatible version range than its build target.
Use the file's download listing when choosing a release. Changes in one source
directory are not automatically shared with the others.

For Fabric 26.2, install JDK 25 and use the included Gradle wrapper:

```sh
git clone https://github.com/Cukkoo12/SoundCulling.git
cd SoundCulling/fabric/262
```

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```sh
sh gradlew build
```

The first build needs internet access to download Gradle and dependencies.
Output is written to that project's `build/libs/` directory. Install the mod
JAR, not the `-sources.jar` file.

The current Fabric 26.2 source includes shared-state synchronization, isolated
settings updates and fixes for recent-sound selection and expired event storage.
These changes are specific to that directory and may not be in a published file.

Translations belong in the relevant project's
`src/main/resources/assets/soundculling/lang/` directory. Keep translation keys
consistent with `en_us.json`.

## Reporting problems

Open an [issue](https://github.com/Cukkoo12/SoundCulling/issues) with:

- Minecraft, loader, Fabric API and Sound Culling versions.
- The downloaded file name and its listed Minecraft version.
- Steps to reproduce, including the sound source and any custom rules.
- Other mods that change sound playback.
- `logs/latest.log` and the crash report, if one was generated.

For missing sounds, check the category filter, whitelist and matching rules.
Compare playback with Sound Culling disabled, and note whether the sound is a
one-shot or a persistent loop. Evaluation errors are logged and normally allow
playback to continue, but that fallback does not cover every possible crash.

## License

[MIT](LICENSE), copyright Cukkoo.
