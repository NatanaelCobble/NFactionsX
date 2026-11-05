# NFactionsX 1.0.0

Competitive factions for PowerNukkitX 3.0.6. The plugin provides native Bedrock forms, chunk claims, power, TNT raids, diplomacy, faction banking and rank-based protection. The default player messages are in French and can be edited.

## Installation

1. Use PowerNukkitX 3.0.6 and Java 21 or newer. The tested setup with the published ShopUI 2.0.1 JAR requires Java 25.
2. Install the main [NVaultX](https://github.com/NatanaelCobble/NVaultX/releases/tag/v1.0.0) and [NFactionsX](https://github.com/NatanaelCobble/NFactionsX/releases/tag/v1.0.0) JARs in `plugins/`.
3. Install FrenchEconomy 1.0 if you need faction bank deposits and withdrawals. LuckPerms, PlaceholderAPI and SRegionProtector are optional.
4. Restart to generate `config.yml`, `messages.yml` and `menus.yml` in `plugins/NFactionsX/`.
5. Open the menu with `/f` and list commands with `/f help`. From the console, run `f admin status`.

Use the API JARs as compile dependencies when developing plugins. Install the main JARs on the server.

If PNX reports an access error for `ClassLoader.defineClass`, use these Java options:

```text
java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED -jar powernukkitx.jar
```

## Gameplay

- Create a faction with a unique name, description and welcome message. Player and faction records use UUIDs.
- Recruit members through open enrollment or expiring invitations. Manage four ranks: recruit, member, officer and leader. Leaders can rename, transfer or disband the faction. Disbanding requires an empty bank and no pending transactions.
- Claim chunks in loaded worlds. Adjacency and power limits are configurable. Players lose power on death and regain it while online. A combat logout can also reduce power.
- Capture border claims from enemies who hold more chunks than their power allows. Alliances require mutual agreement. Enemy and neutral relations are bilateral.
- Set territorial TNT rules, offline raid protection and a truce deadline. Administrative safe zones and war zones protect their blocks from explosions.
- Protect construction, interactions, double chests, vehicles with inventories and ongoing inventory access after a rank change. Protection also covers buckets, fluids, pistons, fire and block changes by entities.
- Configure PvP, including friendly fire and projectiles, and block teleportation or selected commands during combat.
- Set faction homes and warps on claims. Teleports can be delayed and canceled by movement or damage. The plugin rechecks destination safety, ownership and permissions.
- Deposit and withdraw through NVaultX. Faction bank balances use integer cents, with a persistent transfer journal and withdrawal reservations.
- Buy upgrades for membership, claim and warp limits from the faction bank. Increasing the claim limit does not increase faction power.
- Use faction and alliance chat, an opt-in staff spy mode, a claim map, a treasury leaderboard and a log of the 100 most recent faction actions.

## Menus and configuration

The dashboard covers overview, members, territory, home, warps, banking, diplomacy and management. Management includes settings, permissions, history, upgrades and confirmations for destructive actions.

`menus.yml` controls titles, colors, button text, icons, section order and page size. `messages.yml` controls messages and labels, including rank names. Minecraft color codes such as `&b` and `&6` are supported. Bedrock renders the form layout and resolves icons against the client's active textures.

| `config.yml` section | Settings |
| --- | --- |
| `limits` | Base faction, membership, claim and warp limits |
| `power` | Starting, minimum and maximum power, death penalty and regeneration rate |
| `claims` | Adjacency, conquest and excluded worlds |
| `raids` | TNT, offline raids and an absolute truce deadline |
| `combat` | Enemy-only PvP, friendly fire, combat duration, blocked commands and logout penalty |
| `economy` | Claim cost, initial upgrade price and bank limit |
| `upgrades` | Maximum levels and gains per level |
| `roles` | Minimum ranks per action, with faction settings available to leaders |
| `features` | Banking, diplomacy, upgrades, teleportation and chat toggles |
| `integrations` | PlaceholderAPI and SRegionProtector region checks |

Use decimal strings such as `"500.00"` for prices and limits, with up to two decimal places. Run `/f admin reload` to reload configuration. An invalid reload leaves the active settings in place.

Claim capacity is the lower of the upgraded claim limit and the sum of member power rounded down, with a minimum of zero. Negative member power counts toward the sum. If power drops, excess claims remain and can become vulnerable to capture.

The rules of the affected territory determine explosion damage. Wilderness uses vanilla behavior. Diplomacy controls relations and conquest independently from territorial TNT protection.

## Compatibility

| Plugin | Support |
| --- | --- |
| NVaultX 1.0.0 | Required. Economy providers are resolved at the time of each operation. |
| FrenchEconomy 1.0 | Player debits and credits tested through NVaultX. |
| LuckPerms PNX 5.4.0 | Global `nfactions.*` permissions through PNX. NFactionsX manages faction ranks. |
| PlaceholderAPI 1.2.4 | Seven registered and verified placeholders. |
| SRegionProtector 30.2 | A claim is refused if a protected region intersects the chunk column or the region check fails. |
| ShopUI 2.0.1 and StatBoard 1.0.0 | Loaded together in testing using FrenchEconomy player wallets. Faction bank deposits are explicit. |
| Scoreboard and PlaceholderChat | Can display faction values if configured to use PlaceholderAPI. Check the display in-game. |
| WorldManager | Claims and warps reference loaded PNX world names. |
| NPC and TapToDo | Can open the menus if configured to execute `/f` as the player. |
| EssentialsPNX and other protection plugins | NFactionsX respects canceled PNX events. Verify interactions with the installed combination in-game. |

LlamaEconomy support requires a compatible NVaultX economy provider. The 1.0.0 release has been tested with FrenchEconomy.

```text
%nfactions_name%
%nfactions_role%
%nfactions_power%
%nfactions_claims%
%nfactions_bank%
%nfactions_members%
%nfactions_territory%
```

## Administration

```text
/f admin status
/f admin reload
/f admin zone safe|war|none
/f admin unclaim
/f admin power <player> <value>
/f admin bypass
/f admin spy
/f admin pending
/f admin resolve <id> success|failed confirm
/f admin disband <faction> confirm
```

Zone and unclaim commands target the current chunk. `nfactions.admin` controls administration. Bypass requires both `nfactions.bypass` and explicit activation. Both permissions default to operators.

## Bank recovery and backups

NFactionsX writes a journal entry before calling the external economy. Withdrawals reserve faction funds before the call. A confirmed transfer can be applied once.

When an economy call returns `UNKNOWN`, throws, or is interrupted between writes, the transfer can remain `PENDING`. The affected faction's bank, claim and upgrade spending, and disbanding are blocked while it remains pending.

1. Run `/f admin pending` to get the ID, account, direction, amount, provider and the player's balance before the operation.
2. Check the provider's balances and logs, accounting for other transactions.
3. Run `/f admin resolve <id> success confirm` if the external transfer completed or `/f admin resolve <id> failed confirm` if it did not.

Resolution updates the internal ledger or releases its reservation. Reconcile an uncertain transfer before attempting it again.

NVaultX and FrenchEconomy confirm their in-memory state. The faction journal and the external economy have separate persistence. Stop the server and back up both plugins together. After a crash or restoration, check for transfers that need reconciliation.

NFactionsX writes `state.json` atomically on the same disk and retains `state.json.bak`. A write error freezes changes and keeps the latest loaded protections active. An invalid startup snapshot stops the server. Stop the server and preserve both files before restoring a backup.

## Developer API

Compile against `NFactionsX-API-1.0.0.jar` as a provided dependency. Declare `depend: [NFactionsX]` and retrieve the read-only service:

```java
var registration = getServer().getServiceManager().getProvider(
        org.nfactionsx.api.FactionsService.class);
if (registration != null) {
    var faction = registration.getProvider().faction(player.getUniqueId());
}
```

Faction and territory views are detached snapshots. Commands and policy changes run on the main server loop. The engine serializes state changes and protection queries to support parallel level callbacks. This version uses local JSON snapshots, so measure storage performance on the target server before a large rollout.

## Build and test

On Windows with JDK 21 or newer:

```powershell
./download-dependencies.ps1
./build.ps1 -JavaHome 'C:/path/to/jdk-21'
```

The build verifies dependencies by SHA-256, runs tests and produces the plugin and API JARs. Server probes are in `src/integration/`. See [TESTING.md](TESTING.md) for results and the remaining in-game checks.

NFactionsX is an independent PowerNukkitX plugin under the MIT license. Its faction mechanics draw inspiration from [FactionsUUID](https://factions.support/introduction/) and [SaberFactions](https://github.com/SaberLLC/Saber-Factions).
