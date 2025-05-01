# WiFi Map

![MIT License](https://img.shields.io/badge/License-MIT-blue.svg)

This Android app visualizes Wi-Fi signal strength (RSSI) as a heatmap on Google Maps and logs
location-tagged RSSI data to a CSV file. Ideal for wireless analysis, indoor/outdoor coverage
testing, and environmental signal studies.

---

## Setup

- Android Studio
- Android 10+ (API level 29+)
- Google Maps API key

Add this to your `local.properties` file:

```properties
MAPS_API_KEY=your_google_maps_api_key_here
```

---

## Usage

1. Launch the app and grant location permissions
2. The app reads Wi-Fi RSSI and GPS data every 5 seconds
3. Points are plotted on a live heatmap
4. Data is saved to a CSV in the Downloads folder

---

## Running the App

- Open the project in Android Studio
- Run on a real device with Wi-Fi and GPS enabled
- Ensure `local.properties` is properly configured before building

---

## Output Format

Example CSV output (in Downloads):

```
SSID,RSSI,Latitude,Longitude,Time
HomeNetwork,-63,35.9132,-79.0558,14:32:11
```

---

## License

MIT © 2025

---

## Credits

- [Google Maps SDK](https://developers.google.com/maps/documentation/android-sdk)
- [Maps Utils Heatmap](https://github.com/googlemaps/android-maps-utils)
- [FusedLocationProviderClient](https://developer.android.com/training/location)