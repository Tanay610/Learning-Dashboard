# Learning Dashboard

A small Android learning app built with Kotlin and Jetpack Compose.

## Architecture

Compose screens collect immutable `StateFlow` state from ViewModels. The ViewModels depend on a `CourseRepository`, which owns the data-source boundary. The repository combines a deterministic mock remote source with a Room-backed local source; there is no Retrofit service or Hilt setup in this intentionally small implementation. Navigation Compose passes course IDs to the details destination. This MVVM plus Repository design keeps UI state separate from persistence and allows replacing the mock source without changing screens.

## Offline support

On first load, the mock remote source supplies courses and lessons, and the repository writes them transactionally to Room. On later launches the repository attempts a remote refresh but only seeds an empty local store, so saved lesson completion is never overwritten. If the source fails, the screens observe cached courses and lessons from Room. Marking a lesson complete updates Room, and progress is derived from the completed lesson count (shown as a whole percent). For example, 6 of 16 lessons is 37% after truncating to a whole percent, so the displayed value follows lesson state rather than retaining an inconsistent sample percentage. The mock source is local and deterministic, so it does not require internet to run; the repository failure path is verified with a simulated failing source in a unit test.

## Security

Login is intentionally a local demonstration and does not authenticate with a server. A production app should store authentication tokens using Android Keystore-backed secure storage, not plain SharedPreferences or an unencrypted database.

## Scaling

For a service with a million users and hundreds of courses, I would add paginated API responses, backend caching/CDN, database indexes for common queries, API rate limiting, and crash/latency monitoring. Cache invalidation and synchronization would also need a documented policy.

## iOS/macOS

A separate native Apple client could use SwiftUI, Swift Observation or ObservableObject, Swift Concurrency, URLSession, Codable, SwiftData or Core Data, and NavigationStack. Dependency injection can use composition and initializer injection. Repository boundaries and business rules can remain conceptually consistent, while platform-specific implementations change. The Android code itself does not run on iOS; no Apple client is implemented here.


