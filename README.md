# Plugin-Dev
DemiseCore is a Paper 1.21.1+ all-in-one core plugin with economy, admin, and moderation tools.

## Build
```bash
mvn -DskipTests package
```

## Install
1. Copy the shaded JAR from `target/` into your server's `plugins/` folder.
2. Start the server to generate configuration files.

## Configuration
- `config.yml` - Prefix and date formatting
- `economy.yml` - Currency name, starting balance, and limits
- `moderation.yml` - Default durations and moderation messages

## Commands
| Command | Description | Permission |
| --- | --- | --- |
| `/balance [player]` | View balance | `demisecore.economy.balance` |
| `/pay <player> <amount>` | Pay a player | `demisecore.economy.pay` |
| `/setbalance <player> <amount>` | Set balance | `demisecore.admin.setbalance` |
| `/givemoney <player> <amount>` | Give money | `demisecore.admin.give` |
| `/takemoney <player> <amount>` | Take money | `demisecore.admin.take` |
| `/mute <player> [duration] [reason]` | Mute player | `demisecore.moderation.mute` |
| `/unmute <player>` | Unmute player | `demisecore.moderation.unmute` |
| `/warn <player> <reason> [severity]` | Warn player | `demisecore.moderation.warn` |
| `/ban <player> [duration] [reason]` | Ban player | `demisecore.moderation.ban` |
| `/unban <player>` | Unban player | `demisecore.moderation.unban` |
| `/kick <player> [reason]` | Kick player | `demisecore.moderation.kick` |
| `/modlog <player>` | View moderation logs | `demisecore.moderation.modlog` |
