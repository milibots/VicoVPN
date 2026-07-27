# Architecture

The project is organized as a conventional Android application with Kotlin,
XML layouts, Gradle, and Android services.

Main areas include:

- application and activity lifecycle
- VPN service and connection state
- server and route storage
- free subscription discovery and refresh
- premium subscription profile
- connection-priority settings
- split tunneling
- onboarding
- theme, language, and typography
- background workers and notifications

Background tasks must remain lifecycle-safe and must not block the main
thread. UI state should be derived from persisted connection and subscription
state rather than temporary view state.
