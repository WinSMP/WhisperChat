# WhisperChat

WhisperChat is a lightweight, configurable and intuitive plugin designed to for messaging players.

## Features

- **DMs**, where messages in chat get redirected to the receiver. DMs expire after 30 minutes of inactivity.
  - **Group DMs** (which expire after 24 hours).
- `/w` (aliases: `/msg`, `/tell`), `/r`, `/dm`.
- **Social Spy**: see what people send each other in DMs (replicating vanilla behavior with commands).

## For Server Administrators

### Installation

1. Download the latest WhisperChat JAR file from the [releases page](github.com/WinSMP/WhisperChat/releases).
2. Place the downloaded JAR file into your Minecraft server's `plugins/` directory.
3. Restart your Minecraft server to use the plugin.

### Configuration

When first run, WhisperChat will create a new `config.yml` file in the `plugins/WhisperChat/` directory. This file allows you to customize various aspects of the plugin, such as:
- message formats;
- command aliases;
- permissions.

#### Format

Configuration strings are formatted with both [MiniMessage](https://docs.papermc.io/adventure/minimessage/) and legacy Minecraft color codes (`&c`, `&l`). This allows for rich text formatting in your messages.

Depending on the configuration entry, there might be revelant placeholders you might find useful.

* `{target}`: The player who is the target of a direct message or DM session.
* `{player}`: A player, often used in messages related to session status.
* `{group}`: Denotes the name of a group in group-related messages.
* `{sender}`: Represents the player who sent a message.
* `{receiver}`: Represents the player who is receiving a message.
* `{message}`: Represents the actual content of the message being sent.

#### Options

Here's a description of each configurable option:

* `public-prefix`: Sets a prefix (like `!`) that, when prepended to a message in a DM session, will send the message to public chat instead of the private conversation. For example, if you send `"!Hello World"`, it gets sent in public chat, or else if it's `"Hello World"` it's sent in the DM (or group) you're in.

* `messages` section: This section contains all the user-facing messages for WhisperChat.
    * `self-whisper-error`: Sent when a player tries to send themselves a direct message.
    * `dm-start`: Sent to the `{player}` when a new DM session is initiated with another player.
    * `dm-switch`: Sent to the `{player}` when they change their active DM session to talk to a different player.
    * `no-dm-sessions`: Displayed when a player attempts to list their DM sessions but has none active.
    * `invalid-dm-target`: Shown when a player tries to switch to a DM session with someone they haven't previously messaged.
    * `dm-list-header`: The header message displayed before a list of active DM sessions.
    * `dm-list-item`: The format for each individual player listed in the active DM sessions.
    * `not-in-dm`: Message displayed when a player tries to perform a DM-related action but is not currently in an active DM session.
    * `dm-left`: Message confirming that a player has successfully left a DM session with a specific target.
    * `no-reply-target`: Displayed when a player uses the reply command (`/r`) but there's no previous message to reply to.
    * `target-offline`: Message indicating that the target player for a DM is currently offline.
    * `session-expired`: Informs a player that their DM session with another player has ended due to inactivity.
    * `group-deleted`: Message confirming a group DM has been deleted.
    * `group-joined`: Message confirming a player has joined a group DM.
    * `group-left`: Message confirming a player has left a group DM.
    * `group-created`: Message confirming a new group DM has been created.
    * `group-dm-switched`: Message confirming a player has switched their active conversation to a group DM.
    * `group-not-exist`: Displayed when a player tries to interact with a group DM that does not exist.
    * `not-in-group`: Message indicating a player is not part of a specific group.
    * `cannot-delete-group`: Message indicating a player does not have permission or is not the owner to delete a group.
    * `cannot-join-group`: Message indicating a player cannot join a group (e.g., already in another group).
    * `cannot-leave-group-with-dm`: Message instructing players to use `/dm group leave` to exit group DMs.
    * `group-members`: Header for listing group members.
    * `group-owner`: Displays the owner of a group.
    * `group-no-members`: Message when a group has no other members besides the owner.
    * `group-members-list`: Header for the list of group members.
    * `group-member`: Format for each individual member listed in a group.

* `formats`: Defines how different types of messages (whispers, replies, direct messages, and group direct messages) are displayed.
    * `whisper`: Format for messages sent using `/w`, `/msg`, or `/tell`.
    * `reply`: Format for messages sent using `/r`.
    * `dm`: Format for messages sent within a direct message session.
    * `group-dm`: Format for messages sent within a group direct message session.

* `socialspy`: Configures where social spy messages are logged. Valid options are `console` (logs to the server console) or `file` (logs to a dedicated file).

The default configuration should work well for most servers. Restart your server after making changes to the `config.yml` file for them to apply.

## Commands and Usage

WhisperChat provides intuitive commands for private messaging. Here are the basic commands:

* `/msg <player> <message>` (aliases: `/w`, `/tell`): Sends a private message directly to a specific player.
    * Example: `/msg Notch Hello there!`
* `/r <message>`: Replies to the last player who sent you a private message or to whom you last sent a private message. This is useful for quick follow-up conversations.
    * Example: `/r I'm doing great, thanks!`
* `/dm start <player>`: Initiates a new direct message session with the specified player. Once started, subsequent messages you type in chat (without a public prefix) will go directly to this player.
    * Example: `/dm start Herobrine`
* `/dm switch <player>`: Changes your active direct message session to another player you've previously messaged. This allows you to quickly switch between ongoing private conversations.
    * Example: `/dm switch Steve`
* `/dm list`: Displays a list of all players with whom you currently have active direct message sessions.
* `/dm leave`: Exits your current direct message session. You will no longer send messages directly to that player by default.
* `/dm group create`: Creates a new group direct message. The group will be given a unique, randomly generated name and will expire automatically after 24 hours. You will automatically join this group.
    * Example: `/dm group create`
* `/dm group leave`: Allows you to exit the group direct message you are currently a part of.
    * Example: `/dm group leave`
* `/dm group delete <group_name>`: Deletes a specific group direct message. Only the owner of the group can perform this action.
    * Example: `/dm group delete cat-falcon`
* `/dm group join <group_name>`: Allows you to join an existing group direct message, provided you are not already in another group.
    * Example: `/dm group join grape-dragon`
* `/dm group switch <group_name>`: Switches your active conversation to a specific group direct message you are a member of.
    * Example: `/dm group switch cat-falcon`
* `/dm group list-current`: Displays a list of all members currently in the group direct message you are active in, including the group owner.
* `/whisperchat help`: Displays a list of all available WhisperChat commands and their usage, similar to this section.

## Development

### Building the Project

To build WhisperChat from source, you will need Java Development Kit (JDK) 21 or newer and Gradle.

1.  Clone the repository:
    ```bash
    git clone https://github.com/WinSMP/WhisperChat.git
    cd WhisperChat
    ```
2.  Build the project using Gradle:
    ```bash
    ./gradlew build
    ```
    The compiled plugin JAR file will be located in the `build/libs/` directory.

## License

This project is licensed under the [MPL-2.0](LICENSE) License
