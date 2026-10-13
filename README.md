# hKeyAll

hKeyAll is a Paper/Purpur plugin that gives each player key rewards on their own independent online timer.

## Compatibility

- Minecraft `1.19` through `1.20.3`
- Paper and Purpur
- Java `17`
- One universal jar, compiled against the Paper `1.19` API to avoid newer-only API usage

## Behavior

- Each player gets an independent timer.
- Timers count online time only, not offline time.
- Timer progress persists across logout and server restart.
- The next reward interval is randomized between `timer.minimum-seconds` and `timer.maximum-seconds`.
- Set both values to `3600` for a fixed one-hour timer.
- Rewards, commands, permissions, messages, and command aliases are configurable in `config.yml`.

## Commands

The main command is always `/hkeyall`. By default, the plugin also registers `/keyall` and `/hka` from `config.yml`.

| Command | Description | Permission |
| --- | --- | --- |
| `/hkeyall time` | Shows your remaining time. | `hkeyall.time` |
| `/keyall time` | Alias for `/hkeyall time`. | `hkeyall.time` |
| `/hkeyall reload` | Reloads config, aliases, and saved timers. | `hkeyall.admin` |
| `/keyall reload` | Alias for `/hkeyall reload`. | `hkeyall.admin` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `hkeyall.time` | `op` | Allows checking the next key timer. |
| `hkeyall.admin` | `op` | Allows reloading hKeyAll. |

The permission nodes used by commands can be changed:

```yml
permissions:
  time-command: "hkeyall.time"
  reload-command: "hkeyall.admin"
```

## Aliases

Command aliases are configured in `config.yml`:

```yml
command:
  aliases:
    - keyall
    - hka
```

`/hkeyall` is always registered as the base command. Aliases are registered on startup and reloaded by `/hkeyall reload`. If another plugin already owns an alias, hKeyAll skips that alias and logs a warning.

## Placeholders

Reward commands support:

- `%player%` - Player name.
- `%uuid%` - Player UUID.
- `%reward%` - Reward name.

## Example Reward

```yml
rewards:
  - name: vote
    enabled: true
    command: "crate key give %player% vote 1"
```

## Build

```powershell
.\gradlew build
```

The output jar is written to `build/libs/`. The build targets the Paper `1.19` API intentionally. Do not raise `paperApiVersion` unless you are dropping older server support or adding split jars.
