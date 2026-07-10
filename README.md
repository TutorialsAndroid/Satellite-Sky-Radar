# 🛰️ Satellite Sky Radar

Real‑time GNSS satellite viewer for Android – see every GPS, GLONASS, Galileo, and BeiDou satellite overhead on a professional radar display.

<p align="center">
  <img src="/Screenshot_20260710_155546.png" width="280" alt="Satellite radar demo"/>
</p>

## Features

- **Live Sky Plot** – Azimuth and elevation of all visible satellites, updating in real time.
- **Multi‑Constellation** – GPS, GLONASS, Galileo, BeiDou, QZSS, IRNSS, SBAS – all recognised with correct names.
- **Raw Measurements** – Pseudorange rate (speed), carrier frequency, accumulated delta range, multipath flags, and more with a single tap.
- **Compass Overlay** – A blue heading arrow shows your device direction; point your phone at the sky to identify satellites.
- **Privacy First** – No data collection, no ads, no tracking. All satellite information stays on your device.
- **Play Store Ready** – Runtime location permission with rationale, in‑app privacy policy, and support for permanent denial.

## Permissions

- **`ACCESS_FINE_LOCATION`** – required to read raw GNSS data from the device’s GPS receiver.  
  The app does **not** use your location for any other purpose and never sends it off‑device.

## Privacy Policy

A full privacy policy is available [here](https://tutorialsandroid.github.io/Satellite-Sky-Radar/privacy.html).  
You can also view it in‑app by tapping the **ℹ️ Privacy** button.

## Getting Started (Build & Run)

1. Clone the repository:
   ```bash
   git clone https://github.com/TutorialsAndroid/Satellite-Sky-Radar.git
   ```
2. Open the project in **Android Studio** (Arctic Fox or later recommended).
3. Sync the Gradle files.
4. Connect an Android device (API 24+ with GPS hardware) or use an emulator.
5. Run the app on your device – go outside for the best satellite visibility!

## Tech Stack

- Language: Java
- Minimum SDK: Android 7.0 (API 24)
- Target SDK: 33
- UI: Custom `View` for radar, `AlertDialog` for details
- Location: `LocationManager`, `GnssStatus`, `GnssMeasurementsEvent`

## Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you’d like to change.

## License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.

## Contact

Developer: Heatic Apps  
Email: [heaticdeveloper@gmail.com](mailto:heaticdeveloper@gmail.com)  
Google Play: (link coming soon)
