# hKeyAll

A Paper plugin that gives each player key rewards on their own timer.

## Behavior

- Each player gets an independent timer.
- Timers count online time only.
- Timer progress persists across logout and server restart.
- The next reward interval is randomized between `timer.minimum-seconds` and `timer.maximum-seconds`.
- Set both values to `3600` for a fixed one-hour timer.
- Reward names and commands are configurable in `config.yml`.

## Commands

- `/hkeyall time` - Shows your remaining time.
- `/hkeyall reload` - Reloads config and saved timers. Requires `hkeyall.admin`.

## Placeholders

Reward commands support:

- `%player%` - Player name.
- `%uuid%` - Player UUID.
- `%reward%` - Reward name.

## Build

```powershell
.\gradlew build
```

If your target Paper version is not `1.20.2`, update `paperApiVersion` in `gradle.properties`.
