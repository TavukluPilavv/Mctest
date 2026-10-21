# Vexion Client

A Fabric client mod for **Minecraft Java 1.21.4** (Java 21), intended for a private test world.

## Controls

- **Right Shift** opens the Vexion Client module menu.
- **Left-click** a module to toggle it.
- **Middle-click** a module, then press a key to assign its toggle key.
- **Right-click** a module to open its settings.
- **Escape** or **Right Shift** closes the current menu.

The menu includes **Attack Aura**, **Fly**, and **X-Ray**. Module settings are currently in-memory and reset when Minecraft restarts.

## Attack Aura test fixture

Attack Aura is deliberately restricted to a local singleplayer world and only targets an armor stand whose custom name is exactly `VEXION-TEST-DUMMY`. It exposes test range and attack-interval settings. It does **not** target real players or implement shield bypass. To test anti-cheat behavior, create a named armor stand in a private test world and review your server's detection/logging separately.

## Fly and X-Ray

- Fly's vertical movement test is restricted to local singleplayer.
- X-Ray can be toggled from the module menu; its rendering behavior is implemented by the existing block-render mixin.

## Build

Requirements: Java 21 and Gradle.

From the repository root, run:

```sh
gradle build
```

The mod JAR should be created in `build/libs/`. This source change has been committed to GitHub, but the build still needs to be run to confirm compilation before installing the JAR.
