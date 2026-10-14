# AEGIR security report

Scan date: 2026-10-14. Pack: NeoForge 21.1.259, Minecraft 1.21.1, 129 mods (+176 nested jars).

## Summary

**No malware found.** Nothing suspicious; no mod needs removing.

| Step | Result |
|---|---|
| 1. Hash match | 129/129 match the pinned packwiz hash (Modrinth SHA-512 / CurseForge SHA-1) |
| 2. Right project | 119/119 Modrinth jars reverse-resolve to the intended project; 10 CurseForge jars fetched by pinned project+file ID |
| 3. VirusTotal (hash lookup) | 119/129 known to VT, **0 malicious / 0 suspicious** (49+ engines each). 10 unknown to VT (404, never uploaded; not a finding): Cold Sweat Altitude, Easy NPC + Config UI, eZWeight, Footprint Particle, Particle Rain, Raised, Renourished Delight, Roaster's Delight, WN Siege |
| 4a. jNeedle 1.1.0 | 305 jars checked, 0 found |
| 4b. nekodetector 1.1 | No infected jars (5 Java-24 classes in ImmediatelyFast's nested `Reflect` lib unparseable by its ASM; also unused on Java 21) |
| 4c. Overwolf jar-infection-scanner | **GUI only**: Ronald runs it once on `%USERPROFILE%\aegir-tools\scan` |
| 4d. Windows Defender | No threats (all 305 jars) |
| 5. Decompile FLAG + MED (CFR 0.152) | Reviewed; findings per mod below |
| 6. Nested jars | 176 extracted and included in steps 4 and 5 |
| IOC string sweep (all 305 jars) | No webhooks, pastebin, ip-api, telegram, browser/launcher data paths, persistence strings |

## Outbound connections by design

- Warring Nations / WN Siege: bStats. Turn off: `metrics.metricsEnabled=false` (WN), `metrics.enabled=false` (Siege), `config/bStats/config.txt` `enabled=false`.
- Iron's Lib: Patreon cache poll to `code.redspace.io` every 5 min (no config switch).
- MCA Reborn villager chat AI: `api.conczin.net` (section 6).
- Easy NPC remote skins: set `blockPrivateAddresses=true`.
- Iris update check, Citadel/Kiwi contributor lists, Steam 'n' Rails dev capes: small GitHub fetches.

## Provenance of NEW mods

- **balm**: LOW: BlayTheNinth (official), 62M downloads, since 2022, public source.
- **hopo-better-underwater-ruins**: LOW: no code (datapack mod), 2.7M downloads, since 2022; source not public.
- **incendium**: LOW: Stardust Labs (official), 10M downloads, public source; no code (datapack mod). Only NeoForge 1.21.1 build is marked alpha on Modrinth.
- **nuit**: LOW: FlashyReese (official), 14M downloads, MIT, public source. Build is beta.
- **nullscape**: LOW: Stardust Labs, 5.8M downloads; no code.
- **structory**: LOW: Stardust Labs, 9M downloads; no code. File labelled 26.2 but declares pack formats 48 to 107 (covers 1.21.1).
- **structory-towers**: LOW: Stardust Labs, 9.7M downloads; no code. Same 26.2 label note.
- **waystones**: LOW: BlayTheNinth (official), 27M downloads, public source.
- **dynamic-trees-terralith**: LOW: DynamicTreesTeam repo, 134k downloads, MIT.
- **fallingleavesforge**: LOW: Cheaterpaul, 5.9M downloads since 2022, LGPL, public source.

No NEW mod came out HIGH.

## Per mod

| Jar | Source | Side | Hash | Notes |
|---|---|---|---|---|
| `aeronauticscompat-1.1.3.jar` | modrinth | both | OK |  |
| `alexsmobs-1.22.17.jar` | modrinth | both | OK |  |
| `AmbientSounds_NEOFORGE_v6.3.10_mc1.21.1.jar` | modrinth | client | OK |  |
| `architectury-13.0.11-neoforge.jar` | modrinth | both | OK |  |
| `astikorcartsredux-1.2.4.jar` | modrinth | both | OK |  |
| `azimuth-1.4.10.jar` | modrinth | both | OK | MED reviewed. No network/process code. Writes `ponders_watched_azimuth.json` in the game directory (client). Reflection for its own annotation registry. |
| `balm-neoforge-1.21.1-21.0.66.jar` | modrinth | both | OK |  |
| `bettercombat-neoforge-2.4.0+1.21.1.jar` | modrinth | both | OK |  |
| `biolith-neoforge-3.0.14.jar` | modrinth | server | OK |  |
| `bits_n_bobs-2.3.5.jar` | modrinth | both | OK |  |
| `blueprint-1.21.1-8.2.0.jar` | modrinth | both | OK |  |
| `blur-neoforge-6.3.1+1.21.1.jar` | modrinth | client | OK |  |
| `BrewinAndChewin-neoforge-4.5.0+1.21.1.jar` | modrinth | both | OK |  |
| `Chunky-NeoForge-1.4.23.jar` | modrinth | server | OK |  |
| `citadel-1.21.1-2.7.6.jar` | modrinth | both | OK | Known: fetches contributor list from raw.githubusercontent. |
| `cloth-config-15.0.140-neoforge.jar` | modrinth | both | OK |  |
| `ColdSweat-2.4.3.1.jar` | modrinth | both | OK |  |
| `coldsweat_altitude-0.8.1.jar` | modrinth | both | OK |  |
| `comfortable_campfires-2.0.1+1.21.1-neoforge.jar` | modrinth | server | OK |  |
| `copycats-3.0.9+mc.1.21.1-neoforge.jar` | modrinth | both | OK |  |
| `create-1.21.1-6.0.10.jar` | modrinth | both | OK |  |
| `create-aeronautics-bundled-1.21.1-1.3.2.jar` | modrinth | both | OK |  |
| `create-enchantment-industry-2.5.4b.jar` | modrinth | both | OK |  |
| `create-new-age-1.2.0+neoforge-mc1.21.1.jar` | modrinth | both | OK |  |
| `create_connected-1.3.3-mc1.21.1.jar` | modrinth | both | OK |  |
| `create_jetpack-forge-5.2.1.jar` | modrinth | both | OK |  |
| `create_submarine-3.3.0.jar` | modrinth | both | OK |  |
| `createaddition-1.7.2.jar` | modrinth | both | OK |  |
| `createbigcannons-5.11.7+mc.1.21.1.jar` | modrinth | both | OK |  |
| `createdeco-2.1.3.jar` | modrinth | both | OK |  |
| `createdieselgenerators-1.21.1-1.3.15.jar` | modrinth | both | OK |  |
| `CreateDragonsPlus-1.11.9.jar` | modrinth | both | OK |  |
| `CreateNumismatics-1.1.0+neoforge-mc1.21.1.jar` | modrinth | both | OK |  |
| `createpropulsion-1.1.5.jar` | modrinth | both | OK |  |
| `CreativeCore_NEOFORGE_v2.13.50_mc1.21.1.jar` | modrinth | both | OK |  |
| `curios-neoforge-9.5.1+1.21.1.jar` | modrinth | both | OK |  |
| `Design-n-Decor-1.21.1-2.2b.jar` | modrinth | both | OK |  |
| `DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar` | modrinth | both | OK |  |
| `dtterralith-1.3.0.jar` | modrinth | both | OK |  |
| `DungeonsArise-1.21.1-2.1.68-release.jar` | modrinth | server | OK |  |
| `DungeonsAriseSevenSeas-1.21.x-1.0.4-neoforge.jar` | modrinth | server | OK |  |
| `dynamictrees-neoforge-1.21.1-1.7.2.jar` | modrinth | both | OK |  |
| `dynamicwaters-11.1.2.jar` | curseforge | both | OK | FLAG reviewed. SHA-1 matches spec. No network/process code. Rewrites `config/tectonic.json` to set `continents.underground_rivers=false` when its own config `disableTectonicUndergroundRivers` is on (performance). Mixins: worldgen, fluids, entities. |
| `easy_npc-neoforge-1.21.1-7.14.0.jar` | modrinth | both | OK |  |
| `easy_npc_config_ui-neoforge-1.21.1-7.14.0.jar` | modrinth | both | OK |  |
| `emi-1.1.24+1.21.1+neoforge.jar` | modrinth | both | OK |  |
| `EuphoriaPatcher-1.10.5-r5.9.3-neoforge.jar` | modrinth | client | OK |  |
| `eZWeight-neoforge-1.21.1-1.8.4.jar` | modrinth | both | OK |  |
| `fallingleaves-1.21.1-2.5.1.jar` | modrinth | client | OK |  |
| `FarmersDelight-1.21.1-1.3.4.jar` | modrinth | both | OK |  |
| `ferritecore-7.0.3-neoforge.jar` | modrinth | both | OK |  |
| `fish_of_thieves-mc1.21.1-v21.1.2.1-neoforge.jar` | modrinth | both | OK |  |
| `footprintparticle-0.6.0+1.21.1-neoforge.jar` | modrinth | client | OK |  |
| `ftb-chunks-neoforge-2101.1.21.jar` | curseforge | both | OK |  |
| `ftb-essentials-neoforge-2101.1.10.jar` | curseforge | both | OK |  |
| `ftb-library-neoforge-2101.1.35.jar` | curseforge | both | OK |  |
| `ftb-quests-neoforge-2101.1.35.jar` | curseforge | both | OK |  |
| `ftb-teams-neoforge-2101.1.10.jar` | curseforge | both | OK |  |
| `gadgets-and-gizmos-bundled-V1.2.7.jar` | modrinth | both | OK |  |
| `geckolib-neoforge-1.21.1-4.9.3.jar` | modrinth | both | OK |  |
| `GlitchCore-neoforge-1.21.1-2.1.0.2.jar` | modrinth | both | OK |  |
| `gravestone-neoforge-1.21.1-1.0.40.jar` | modrinth | both | OK |  |
| `historystages-5.6.1-1.21.1.jar` | modrinth | both | OK |  |
| `HopoBetterUnderwaterRuins-[1.21.1-1.21.3]-1.2.1b.jar` | modrinth | both | OK |  |
| `ImmediatelyFast-NeoForge-1.6.14+1.21.1.jar` | modrinth | client | OK |  |
| `ImmersiveUI-NEOFORGE-0.3.3+1.21.1.jar` | modrinth | client | OK |  |
| `Incendium_1.21.x_v5.4.4.jar` | modrinth | both | OK |  |
| `iris-flywheel-compat-NeoForge-2.4.0.jar` | modrinth | client | OK |  |
| `iris-neoforge-1.8.14-beta.1+mc1.21.1.jar` | modrinth | client | OK | Known: update checker (raw.githubusercontent). |
| `irisveil-0.3.0.jar` | modrinth | client | OK |  |
| `irons_lib-1.21.1-2.2.0.jar` | modrinth | both | OK | MED reviewed. **Outbound by design, no off switch:** every 5 min the server GETs `code.redspace.io/cache/game-data` with a random server UUID, player count, dedicated flag and mod version, and receives Patreon statue/cosmetic data. Client downloads player skins from Mojang URLs for statues. Not malicious; documented in FRIENDS.md. |
| `irons_spellbooks-1.21.1-3.16.3.jar` | modrinth | both | OK |  |
| `Jade-1.21.1-NeoForge-15.10.6.jar` | modrinth | both | OK |  |
| `Kiwi-1.21.1-NeoForge-15.8.7.jar` | modrinth | both | OK |  |
| `kotlinforforge-5.12.0-all.jar` | modrinth | both | OK |  |
| `L_Ender's Cataclysm 1.21.1-3.33.jar` | modrinth | both | OK |  |
| `lionfishapi-3.1.jar` | modrinth | both | OK |  |
| `lithium-neoforge-0.15.4+mc1.21.1.jar` | modrinth | both | OK |  |
| `lithostitched-1.8.0-neoforge-21.1.jar` | modrinth | server | OK |  |
| `lootr-neoforge-1.21.1-1.11.38.127.jar` | modrinth | both | OK |  |
| `max-health-fix-1.0.jar` | modrinth | server | OK |  |
| `mca-neoforge-7.7.36-beta.3+1.21.1.jar` | modrinth | both | OK |  |
| `midnightlib-neoforge-1.9.3+1.21.1.jar` | modrinth | both | OK |  |
| `modernfix-neoforge-5.27.26+mc1.21.1.jar` | modrinth | both | OK |  |
| `MouseTweaks-neoforge-mc1.21-2.26.1.jar` | modrinth | client | OK |  |
| `nuit-neoforge-1.0.0-beta.6+mc1.21.1.jar` | modrinth | client | OK |  |
| `Nullscape_1.21.x_v1.2.14.jar` | modrinth | both | OK |  |
| `OctoLib-NEOFORGE-0.6.2+1.21.jar` | modrinth | both | OK |  |
| `particlerain-4.0.1+1.21.1-neoforge.jar` | modrinth | client | OK |  |
| `particular-1.21.1-NeoForge-1.5.7.jar` | modrinth | client | OK |  |
| `PickUpNotifier-v21.1.1-1.21.1-NeoForge.jar` | modrinth | both | OK |  |
| `player-animation-lib-forge-2.0.4+1.21.1.jar` | modrinth | both | OK |  |
| `pmmo-1.21.1-2.10.47.jar` | modrinth | both | OK |  |
| `puzzleslib-v21.1.63-mc1.21.1+neoforge.jar` | modrinth | both | OK |  |
| `railways-0.2.1+neoforge-mc1.21.1.jar` | modrinth | both | OK | Known: dev capes list (raw.githubusercontent). |
| `raised-neoforge-1.21.1-6.0.2.jar` | modrinth | client | OK |  |
| `renourisheddelight-1.21.1_3.2.1-release.jar` | modrinth | both | OK | FLAG reviewed. Class list identical to public source tag `1.21.1_3.2.0-release`. Only extra class is Architectury's injected `PlatformMethods` (returns "neoforge"). `java.net.URL` used only for `getResource` of its built-in datapack. |
| `ritchiesprojectilelib-2.1.2+mc.1.21.1-neoforge.jar` | modrinth | both | OK |  |
| `roasters_delight-1.0.0.1.jar` | curseforge | both | OK | FLAG reviewed. Diff vs 1.0.0.0: +285 assets/data files, new/changed food, block, entity (civet, tea pet), registry and gametest classes. No class loaders, `defineClass`, Base64, network or process code. "Cookies" string hit = gametest food names. |
| `sable-neoforge-1.21.1-2.0.6.jar` | modrinth | both | OK |  |
| `sableexplosionfix-1.0.0.jar` | modrinth | server | OK |  |
| `sablejade-1.2.1.jar` | modrinth | client | OK |  |
| `sablephysicscompat-1.3.0.jar` | modrinth | server | OK |  |
| `sablewaves-2.25.1.jar` | curseforge | both | OK | FLAG reviewed. Single namespace `dev.ashok.sablewaves`; no classes in Sable's or another mod's namespace. No network/process/file code. Reflection only for the NeoForge config screen. |
| `salts_animal_farm-1.21.1-NeoForge-0.2.jar` | modrinth | both | OK | FLAG reviewed. Public repo builds Fabric only, so not source-verified. Decompiled: no network, process or file access. Mixins: Animal, Mob, LivingEntity, WalkNodeEvaluator, renderers. |
| `SereneSeasons-neoforge-1.21.1-10.1.0.9.jar` | modrinth | both | OK |  |
| `SnowRealMagic-1.21.1-NeoForge-12.2.3.jar` | modrinth | both | OK |  |
| `sodium-neoforge-0.8.12+mc1.21.1.jar` | modrinth | client | OK |  |
| `sophisticatedbackpacks-1.21.1-3.26.9.2195.jar` | modrinth | both | OK |  |
| `sophisticatedcore-1.21.1-1.5.7.2381.jar` | modrinth | both | OK |  |
| `spark-1.10.124-neoforge.jar` | modrinth | both | OK |  |
| `spawn-4.0.7-1.21.1.jar` | modrinth | both | OK |  |
| `SSRD-1.8.7-1.21.1.jar` | modrinth | both | OK |  |
| `Structory_26.2_v1.3.7.jar` | modrinth | both | OK |  |
| `Structory_Towers_26.2_v1.0.17.jar` | modrinth | both | OK |  |
| `struts-1.3.1.jar` | modrinth | both | OK | MED reviewed. No network/process/file code. Bundles `sable-companion` (Sable's public API). |
| `teallib-1.3.teal.jar` | modrinth | both | OK | MED reviewed. No network/process/file code. |
| `tectonic-3.0.28-neoforge-21.1.jar` | modrinth | both | OK |  |
| `Terralith_1.21.1_v2.6.2_Neoforge.jar` | modrinth | both | OK |  |
| `tide-neoforge-1.21.1-2.1.1.jar` | modrinth | both | OK |  |
| `tool-trims-v3.0.7-for-1.21.1.jar` | modrinth | both | OK | MED reviewed. No code at all: data + resource pack packaged as a mod (`lowcodefml`). Fabric API dep on Modrinth applies to the Fabric loader only. |
| `upgrade_aquatic-1.21.1-7.0.2.jar` | modrinth | both | OK |  |
| `voicechat-neoforge-1.21.1-2.6.22.jar` | modrinth | both | OK |  |
| `warring-nations-neoforge-4.0.0.jar` | curseforge | both | OK | FLAG reviewed. Only network code is shaded bStats (`MetricsBase`), gated by `metrics.metricsEnabled` and the bStats global config. No hard-coded UUIDs or player names. Mixins: FTB Chunks/Teams UI, cape layer, piston helper. CurseForge-only licence: never redistributed. |
| `waystones-neoforge-1.21.1-21.1.46.jar` | modrinth | both | OK |  |
| `waystonessable-1.0.7.jar` | modrinth | both | OK | FLAG reviewed. MIT, public source. No network/process/file code. Mixins: Sable sublevel packets/assembly, waystone blocks. |
| `wn-siege-1.13.0.jar` | curseforge | server | OK | FLAG reviewed. Only network code is shaded bStats, gated by `metrics.enabled`. Mixins target weapon/explosion/Create machine and Create packet classes only (siege claim protection, e.g. cancel `ToolboxEquipPacket` in protected chunks). No vanilla login, networking or permission mixins. |
| `xaerominimap-neoforge-1.21.1-26.1.0.jar` | modrinth | both | OK |  |
| `xaeroworldmap-neoforge-1.21.1-1.40.16.jar` | modrinth | both | OK |  |

## Addendum 2026-10-14 (evening): jars added after the first scan

These were added and run before being scanned, which broke the scan-first rule; the scan was done
retroactively the same day. Result: **nothing suspicious**.

| Step | Result |
|---|---|
| 1-2. Hash match / right project | 15/15 match their official Modrinth project (`POST /v2/version_files`, SHA-512) |
| 3. VirusTotal (hash lookup only) | 15 files 0/50-66 malicious; `exotic_worlds-1.0.0` and `c2me-...-opts-accel-opencl` not yet in VT (unknown, not flagged); Temurin 25 JRE zip 0/66 |
| 4. jNeedle / nekodetector / Defender | 14 jars, 0 found / no infected jars / no threats |
| IOC string sweep (`ioc_grep.py`) | 0 hits |
| Persistence check (section 4) | fractureiser, WeedHack, Spark, Myth Stealer paths, Run key, tasks, DisableTaskMgr: all clean. Defender exclusions not readable without admin. |

| Jar | Notes |
|---|---|
| `exotic_worlds-1.0.0+mc1.21.1.jar` | NEW, low downloads (1.7k), public source (github.com/anyttng/exotic_worlds, owner account from 2018), PolyForm Shield. FLAG-level read of all 2,731 classes incl. nested MixinSquared: only `java.net.URL` use is `ClassLoader.getResource` for mod detection; no network, process or out-of-game file access. |
| `c2me-neoforge-...0.123.jar` | MIT, public source. Base C2ME only in the pack. |
| `c2me-neoforge-opts-accel-opencl-...0.123.jar` | AEGIR-GPU test instance only, not in the pack. ARR. |
| `wakes-1.21.1-NeoForge-1.4.1.jar` | GPL-3.0, no networking code (client visual). |
| `Chunky`, `structure_layout_optimizer`, `resourcefulconfig`, `AI-Improvements`, `Clumps`, `entityculling`, `zfastnoise` | Established (8M-182M downloads); zFastNoise later removed again. |
| `xaerominimap 26.6.0`, `xaeroworldmap 1.47.0` | Version bump for a NoSuchMethodError crash (Warring Nations calls `getThirdPartyWaypointManager`, added in minimap 26.2). |
| `Structory_Towers_26.1_v1.0.16.jar` | Downgrade: 1.0.17's `neoforge.mods.toml` lacks `modLoader`, NeoForge rejects the file. |
| `aegir-tweaks-*.jar` | Our own data-only jar (no classes), built from `tools/aegir-tweaks`. |

## Still to do

- Overwolf scanner run (GUI).
- Persistence check after the first client launch (section 4).
