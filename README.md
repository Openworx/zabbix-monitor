# Openworx Mobile for Zabbix

Android app and home-screen widget for Zabbix 6.x/7.x, built on the Zabbix JSON-RPC API.

**Author:** Openworx — <info@openworx.nl>

## Features

- Home-screen widget with current problems (Zabbix severity colors, freely resizable, compact or two-line view, configurable refresh interval down to 1 minute)
- Problems view with search, severity filter and detail screen: acknowledge, post comments, change severity, close problems, full acknowledge history
- Hosts and host groups with availability/maintenance status, item list (latest data) and vector graphs (1h/6h/24h/7d, with trend fallback)
- Notifications for new problems (background polling, severity threshold)
- Multiple Zabbix servers; the widget and notifications follow the active server
- Authentication via Zabbix API token (Authorization: Bearer), optional self-signed certificate support

## Building

Open the project in Android Studio, or run:

```
gradle :app:assembleDebug
```

Requires Android SDK 34. Minimum supported Android version: 8.0 (API 26).

## Configuration

1. Create an API token in Zabbix (Users → API tokens).
2. Open the app, add a server with the frontend base URL (e.g. `https://zabbix.example.com`) and the token.
3. Add the "Zabbix problems" widget to your home screen.
