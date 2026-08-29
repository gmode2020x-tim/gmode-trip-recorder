# Home Assistant Setup

## Install the integration

Copy this repository's `home-assistant/custom_components/gmode_trip_recorder` folder to Home Assistant:

```text
/config/custom_components/gmode_trip_recorder
```

Restart Home Assistant, then add/configure the GMODE Trip Recorder integration according to the component README. Confirm Home Assistant is reachable from the phone before entering credentials.

## Create a token

In Home Assistant, open the user's profile and create a **Long-Lived Access Token**. Copy it immediately; Home Assistant does not show it again. Treat it like a password.

## Connect the app

1. In GMODE settings open **System > Home Assistant connection**.
2. Enter the base URL, for example `http://192.168.1.10:8123` on a trusted LAN or an HTTPS/VPN URL remotely.
3. Paste the long-lived token and press **Save connection**.
4. Press **Sync now**. A manual sync cancels any delayed one-time retry and starts a fresh attempt immediately.
5. Read the status below the buttons. **Up to date** means no unsynchronized local points remain.

GMODE also sends an authenticated health heartbeat even when no trip points are pending. The app's **HA control + updates** section shows the last control revision, Home Assistant notice, and any newer version offered by your server.

## API contract

The app sends authenticated JSON to:

```text
POST /api/gmode_trip_recorder/mobile/upload
Authorization: Bearer HOME_ASSISTANT_TOKEN
```

One request carries one trip and at most 500 points. Stable trip/point IDs and the `acknowledgedPointIds` response make retries idempotent. The phone retains acknowledged data locally.

Diagnostics and control use:

```text
POST /api/gmode_trip_recorder/mobile/diagnostics
Authorization: Bearer HOME_ASSISTANT_TOKEN
```

Home Assistant exposes:

| Entity | Purpose |
| --- | --- |
| `sensor.gmode_mobile_status` | Latest phone heartbeat, sync/GPS/automatic-recording state, permissions, battery, pending points, and last-seen time. |
| `sensor.gmode_mobile_log` | Latest diagnostic event plus up to 100 retained recent events. |
| `sensor.gmode_mobile_control` | Current revisioned notice, update metadata, bounded settings, and pending safe command. |

### Send a notice, settings, or command

Open **Developer tools > Actions** and run `gmode_trip_recorder.set_mobile_control`. The next phone sync receives the new revision. Supported values are:

- `notice`: text displayed in GMODE.
- `latest_version`, `download_url`, and `sha256`: update information displayed for user-confirmed download. GMODE never silently installs an APK.
- `settings`: any subset of `homeRadiusMeters` (100-5000), `wifiDepartureDelayMinutes` (1-30), `returnDwellMinutes` (1-120), `locationIntervalSeconds` (2-300), `minimumDistanceMeters` (1-500), or `tripType` (`street`, `off_road`, `snow`, `water`).
- `command_action`: `sync` or `rearm`. Commands are ID-based and are removed after the app acknowledges them.

Example action data:

```yaml
action: gmode_trip_recorder.set_mobile_control
data:
  notice: "GPS comparison is active."
  settings:
    locationIntervalSeconds: 5
    minimumDistanceMeters: 5
  command_action: sync
```

Use `gmode_trip_recorder.clear_mobile_logs` to clear one phone's retained events by `device_id`, or omit it to clear all phone logs. The latest heartbeat remains available.

## Troubleshooting

| Status/problem | Check |
| --- | --- |
| Setup required | Save both a complete URL and token. Blank token input keeps an existing saved token. |
| Waiting for connection | Verify phone network/VPN, HA reachability, DNS/IP, port 8123, and TLS certificate. |
| Waiting for home Wi-Fi | The configured HA URL is private/LAN-only and no usable Wi-Fi or VPN route is available. Connect the phone to the home Wi-Fi, then press **Sync now**. |
| Error says `from /192.0.0.x` | Android attempted the private HA address through cellular CLAT. GMODE 2.1.6+ selects an available Wi-Fi network for LAN-only URLs; also check Samsung **Settings > Connections > Data usage > Allowed networks for apps** and do not force GMODE to mobile data only. |
| HTTP 401/403 | Create a new token for an authorized HA user and save it. |
| HTTP 404 | Confirm the custom integration is installed/restarted and the mobile upload route exists. |
| HTTP 5xx/408/429 | WorkManager retries automatically; inspect Home Assistant logs/resources. |
| Points remain pending | Keep Android network enabled, make the app battery-unrestricted, press Sync now, and check the detailed status. |
| Mobile status is unknown | Install integration 1.3.0+, update GMODE to 2.1.0+, save the HA connection, and press Sync now. |
| Control does not arrive | Confirm the control entity revision increased, then press **Check HA now** in the app. Commands are acknowledged only after successful receipt. |
| LAN hostname fails | Try the HA LAN IP. Some Android/DNS networks do not resolve `.local` names reliably. |

Never publish the token in screenshots, logs, source code, or support issues. Rotate it if exposed.
