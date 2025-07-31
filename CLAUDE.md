# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Use the `just` command runner for all development tasks:

- `just build` - Build all app variants
- `just unit-test` - Run unit tests
- `just espresso` - Run UI tests with coverage
- `just format` - Format code with ktfmt
- `just clean` - Clean build artifacts
- `just local-stack` - Start local MQTT test stack for testing

For specific build variants:
- `./gradlew assembleGmsDebug` - Build Google Play Services debug variant
- `./gradlew assembleOssDebug` - Build open-source debug variant

## Architecture Overview

OwnTracks is a location tracking Android app with two main build flavors:
- **gms**: Uses Google Play Services (Maps, Location APIs)
- **oss**: Open-source variant using OpenStreetMap

The app follows MVVM architecture with:
- **Hilt** for dependency injection
- **Room** for local database
- **Kotlin Coroutines** for async operations
- **WorkManager** for background tasks

Key architectural components:
- `BackgroundService` - Core service managing location tracking, messaging, and state
- `LocationProcessor` - Handles location updates and geofencing
- `MessageProcessor` - Processes MQTT/HTTP messages
- Repository pattern for data access via `ContactsRepo` and `WaypointsRepo`

## Code Organization

- `project/app/src/main/java/org/owntracks/android/`:
  - `data/` - Data layer (repositories, Room entities)
  - `services/` - Background services and processors
  - `net/` - Network endpoints (MQTT, HTTP)
  - `location/` - Location providers and geofencing
  - `ui/` - Activities, Fragments, ViewModels
  - `model/messages/` - Message types for communication

## Testing

Run tests with:
- `just unit-test` - Unit tests
- `just espresso` - UI tests
- `./gradlew testGmsDebugUnitTest` - GMS variant unit tests
- `./gradlew testOssDebugUnitTest` - OSS variant unit tests

When adding new features, ensure tests are added in the appropriate test directories.

## Key Development Considerations

1. **Flavor-specific code**: Use `gms/` and `oss/` source sets for variant-specific implementations
2. **Location permissions**: The app has comprehensive permission handling - see `ui/welcome/permission/`
3. **Background restrictions**: Battery optimization and background location handling is critical
4. **MQTT persistence**: Custom implementation using Room database
5. **Message formats**: See `model/messages/` for supported message types

## Common Tasks

- To add a new preference: Update `preferences/Preferences.kt` and `preferences/types/`
- To modify MQTT behavior: Check `services/MessageProcessor.kt` and `net/mqtt/`
- To change location tracking: See `services/LocationProcessor.kt`
- To update UI: Follow existing MVVM patterns in `ui/` packages

## Dependencies

Managed via version catalog in `gradle/libs.versions.toml`. Major dependencies:
- Android Jetpack components
- Hilt for DI
- Paho for MQTT
- OsmDroid for maps (OSS flavor)
- Google Play Services (GMS flavor only)