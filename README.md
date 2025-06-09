# InOsso 🏠
InOsso is a feature-rich Discord music bot designed to bring high-quality music playback and a variety of music-related functionalities to your Discord server.

Created for friends, InOsso aims to enhance your server's entertainment experience with seamless music streaming, playlist management, and user-friendly commands. Designed with simplicity in mind, InOsso ensures that users of all experience levels can enjoy its features.

[More info](https://www.casiebarie.dev/discord/inosso/)

## Changelog:
### v1.5.4 - Dependency updates & Bugfixes
- **Fix** | MessageDeleter not working for webhook messages.
- **Dependency** | Updated `net.dv8tion_jda` from `5.5.1` to `5.6.1`.
- **Other** | Code cleanup.

### v1.5.3 - Dependency updates & Bugfixes
- **Fix** | Dependency checker fixes.
- **Dependency** | Updated `net.dv8tion_jda` from `5.5.0` to `5.5.1`.
- **Dependency** | Updated `org.slf4j_slf4j-api` from `2.0.16` to `2.0.17`. 

### v1.5.2 - Dependency update
- **Dependency** | Updated `net.dv8tion_jda` from `5.4.0` to `5.5.0`.

### v1.5.1 - Dependency update & Other
- **Dependency** | Updated `net.dv8tion_jda` from `5.3.2` to `5.4.0`.
- **Other** | Activity status changes.
- **Removed** | Checks for sending dependency update message.

### v1.5.0 - React `❌` to delete messages
- **Added** | Reacting `❌` to a bot message will delete the message.
- **Added** | Warnings and errors will now be sent to Cas directly.
- **Fix** | Music replay not working.
- **Fix** | Webhook creation causing error.
- **Ohter** | Changes to instance loading.

### v1.4.0 - Added `Idle` status
- **Added** | InOsso now shows `Idle` when nobody is in the call.
- **Dependency** | Updated `net.dv8tion_jda` from `5.3.1` to `5.3.2`.
- **Fix** | Request message not updating when enveryone leaves at the same time.
- **Fix** | Dependency checker not sending a message.
- **Fix** | Some error handling issues.

### v1.3.0 - Support for audio attachments
- **Added** | Support for audio attachments.
- **Fix** | SoundCloud GO+ tracks are now filtered from the five search options.
- **Fix** | Search message formatting.
- **Fix** | Space in 'Poortwachter' webhook name.

### v1.2.2 - Dependency updates & Bugfixes
- **Dependency** | Updated `net.dv8tion_jda` from `5.3.0` to `5.3.1`.
- **Dependency** | Updated `ch.qos.logback_logback-classic` from `1.5.17` to `1.5.18`.
- **Fix** | Replace size check in the dependency checker with a deep comparison of map entries.
- **Fix** | Changed dependency checker log level for warnings to debug.

### v1.2.1 - Bugfixes
- **Fix** | Dependency update message now only refreshes when there are updates.
- **Fix** | Spaces in webhook names.
- **Fix** | Controller message still referring to YouTube instead of SoundCloud.
- **Fix** | Automatic role rename now doesn't change the bot role.

### v1.2.0 - View changelog
- **Added** | The `/help changelog` command to view the changelog of InOsso.
- **Fix** | SoundCloud GO+ tracks no longer play a 30-second preview and are now filtered.
- **Fix** | Track queue not working properly.

### v1.1.2 - Cleanup
- **Other** | Code cleanup.
- **Ohter** | pom.xml cleanup.

### v1.1.1 - Guild IDs
- **Fix** | Updated the guild IDs to the correct ones.

### v1.1.0 - Removed YouTube support
- **Removed** | Support to play YouTube links.
- **Changed** | Music will now be searched on SoundCloud instead of YouTube.
- **Added** | Request button will now be disabled when nobody is in the call.
- **Added** | The dependency checker will now send updates to CasieBarie privately.
- **Removed** | The `/setup` command.
- **Other** | Code cleanup.

### v1.0.1 - Bugfixes
- **Fix** | Music formatting.
- **Fix** | Audio url filtering not working properly.
- **Fix** | Avatar loading not working.
- **Fix** | Errors when interactions are not properly acknowledged.
- **Fix** | Fixes based on SonarQube hints.