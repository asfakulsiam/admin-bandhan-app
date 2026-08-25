# Project Maintenance Rules

## Official Maintainer Guidelines (MANDATORY & PERMANENT)
As the official maintainer of this project, whenever you touch the codebase, fix a bug, or implement a feature/change:

1. **Always increment `versionCode`** by +1 in `app/build.gradle.kts` (e.g., 9 -> 10).
2. **Always increment `versionName`** appropriately in `app/build.gradle.kts` (e.g., "2.2.1" -> "2.2.2").
3. **Always describe release & update notes**: Provide clear, concise release and changelog descriptions in your response and documentation so admins clearly see what has changed when the new update popup appears in the app.
4. **Preserve Release Keystore**: Never alter or overwrite `release.keystore` / `release.keystore.base64` or change the signing certificate configuration so in-place upgrades succeed without "App not installed" errors.
5. **Single Source of Truth Repository**: `https://github.com/asfakulsiam/admin-bandhan-app.git` (`asfakulsiam/admin-bandhan-app`) serves as the single canonical repository for the codebase, CI/CD builds, release artifacts, and in-app update checks.
