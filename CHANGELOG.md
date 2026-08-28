# Changelog

## 2.1.2 - 2026-08-28

- Combined Coordinates, GPS Accuracy, and GPS Satellites into one automatically migrated GPS Sky gauge.
- Added a real north-up satellite sky plot using Android GNSS azimuth and elevation, with per-satellite constellation/SVID labels.
- Added green, amber, and red C/N0 signal-strength bands, visible-versus-used-in-fix styling, a scaled horizontal-accuracy radius, live coordinates, speed, altitude, and course.

## 2.1.1 - 2026-08-28

- Added full foreground GPS and GNSS monitoring whenever the cockpit is visible, even at home with no active trip.
- Added live idle-state speed, altitude, course, accuracy, coordinates, and satellites without creating or persisting trip points.
- Changed the idle cockpit status from GPS standby to live GPS searching/fix quality while preserving manual and automatic recording behavior.

## 2.1.0 - 2026-08-28

- Added an authenticated diagnostic heartbeat on every manual, trip, and periodic sync, including when no trip data is pending.
- Added a bounded on-device event log for sync, GPS recovery, automatic recording, app starts, and HA commands.
- Added HA-to-app control responses for notices, update metadata, recording settings, and safe sync/re-arm commands.
- Added a 15-minute network-aware diagnostic heartbeat and boot/update scheduling.
- Added an in-app HA Control + Updates panel with update notices, release links, and SHA-256 display.
- Added Home Assistant mobile status, log, and control entities plus services for setting control data and clearing logs.

## 2.0.1 - 2026-08-28

- Fixed automatic departure recording so a missing or uncertain first GPS fix retries instead of silently ending the check.
- Changed departure confirmation to request high-accuracy GPS with a bounded linear retry backoff.
- Added an in-trip GPS watchdog that restarts location updates after a request failure or 45 seconds without a fix.
- Re-arm automatic recording whenever the app resumes, including when it opens directly to the cockpit.
- Added visible GPS search and retry diagnostics to the cockpit and trip-status panel.

## 2.0.0 - 2026-08-23

- Promoted the rebuilt landscape cockpit to the public v2 dashboard.
- Added true procedural 3D scene vehicles: Truck, SxS, Sand rail, Snowmobile, and Mini jet boat.
- Combined pitch and mirrored roll in the live 3D Attitude gauge with a theme-coloured centre line, short rotation history, radial course arcs, high-rear camera, orbit modes, and configurable caution/limit bezel alerts.
- Rebuilt outer ticks from the same scale definitions used by each gauge.
- Added all 13 gauges to unlimited footer-arrow navigation and preserved user order.
- Moved the six side icons onto the approved radial arc while preserving their label panels and touch regions.
- Added editable side labels, icons, built-in actions, and installed-app launching with Spotify/Maps/Camera and Trip/Start/Stop defaults.
- Added Street, dirt Off road, Sand dunes, Snow, and Water scene selection with automatic matching vehicles.
- Added hybrid GPS plus Wi-Fi home detection, automatic departure/return recording, boot/update re-arming, and adjustable timing/distance thresholds.
- Added stationary phone-mount pitch/roll zero calibration.
- Added GPX, KML, GeoJSON, and CSV trip export.
- Added an explicit background-location disclosure, separate just-in-time permission requests, an in-app privacy summary, launcher icons, and Android 16/API 36 support.
- Added production upload-key signing for Play bundles while preserving an upgrade-compatible sideload build.
- Added complete user, settings, sensor, architecture, Home Assistant, Play listing, submission, privacy, and security documentation.

## 1.15.1

- Previous private test release and last v1 sideload signing lineage.
