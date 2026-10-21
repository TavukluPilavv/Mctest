# Mctest

A starter Fabric client mod for **Minecraft Java 1.21.4**.

## Requirements
- Java 21
- Git
- Gradle wrapper (generate with `gradle wrapper` if needed)

## Build
From the repository root, run:

```sh
gradle build
```

The mod JAR will be in `build/libs/`.

## First test
Launch the client with Fabric Loader and Fabric API installed. Press **K** in a world to display a small message confirming that the mod loaded.

This starter is intended for a private test world. Flight behavior should be tested with Creative mode or explicit server-side permission; a client mod cannot grant survival flight on a server that disallows it.
