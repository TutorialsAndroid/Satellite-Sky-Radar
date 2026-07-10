package com.heatic.satelliteskyradar;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private LocationManager locationManager;
    private SatelliteRadarView radarView;
    private TextView tvLocation, tvSatCount, tvRawStatus, tvNoSignal;
    private GnssStatus.Callback gnssStatusCallback;
    private GnssMeasurementsEvent.Callback gnssMeasurementsCallback;
    private LocationListener locationListener;

    private final Map<String, Float> speedMap = new HashMap<>();
    private final Map<String, GnssMeasurement> measurementMap = new HashMap<>();

    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private boolean hasGravity = false;
    private boolean hasGeomagnetic = false;

    private volatile boolean rawMeasurementsActive = false;
    private long lastRawTimestamp = 0;
    private static final long RAW_TIMEOUT_MS = 2000;

    private Button btnPrivacy;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        radarView = findViewById(R.id.radarView);
        tvLocation = findViewById(R.id.tvLocation);
        tvSatCount = findViewById(R.id.tvSatCount);
        tvRawStatus = findViewById(R.id.tvRawStatus);
        tvNoSignal = findViewById(R.id.tvNoSignal);
        btnPrivacy = findViewById(R.id.btnPrivacy);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        prefs = getPreferences(MODE_PRIVATE);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                runOnUiThread(() -> {
                    String locText = String.format("Lat: %.6f  Lon: %.6f",
                            location.getLatitude(), location.getLongitude());
                    tvLocation.setText(locText);
                });
            }
            @Override public void onProviderDisabled(@NonNull String provider) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
        };

        gnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                List<SatelliteInfo> satList = new ArrayList<>();
                for (int i = 0; i < status.getSatelliteCount(); i++) {
                    float azim = status.getAzimuthDegrees(i);
                    float elev = status.getElevationDegrees(i);
                    if (elev > 0) {
                        int svid = status.getSvid(i);
                        int constellation = status.getConstellationType(i);
                        boolean used = status.usedInFix(i);
                        float cn0 = status.getCn0DbHz(i);
                        String key = constellation + "_" + svid;
                        Float speed = speedMap.get(key);
                        float rate = (speed != null) ? speed : 0.0f;
                        satList.add(new SatelliteInfo(svid, constellation, azim, elev, used, cn0, rate));
                    }
                }
                final int count = satList.size();
                runOnUiThread(() -> {
                    radarView.setSatellites(satList);
                    tvSatCount.setText("Satellites: " + count);
                    if (count == 0) {
                        tvNoSignal.setVisibility(View.VISIBLE);
                    } else {
                        tvNoSignal.setVisibility(View.GONE);
                    }
                    updateRawStatusText();
                });
            }
        };

        gnssMeasurementsCallback = new GnssMeasurementsEvent.Callback() {
            @Override
            public void onGnssMeasurementsReceived(@NonNull GnssMeasurementsEvent event) {
                rawMeasurementsActive = true;
                lastRawTimestamp = System.currentTimeMillis();
                runOnUiThread(() -> updateRawStatusText());

                for (GnssMeasurement meas : event.getMeasurements()) {
                    int svid = meas.getSvid();
                    int constellation = meas.getConstellationType();
                    String key = constellation + "_" + svid;
                    measurementMap.put(key, meas);
                    if (meas.getPseudorangeRateUncertaintyMetersPerSecond() > 0.0) {
                        double rate = meas.getPseudorangeRateMetersPerSecond();
                        if (Math.abs(rate) > 0.01) {
                            speedMap.put(key, (float) rate);
                        }
                    }
                }
            }
        };

        radarView.setOnSatelliteClickListener(satellite -> {
            String key = satellite.constellationType + "_" + satellite.svid;
            GnssMeasurement meas = measurementMap.get(key);
            showSatelliteDetails(satellite, meas);
        });

        startRawDataWatchdog();

        // Initiate permission check immediately
        checkAndRequestLocationPermission();

        // Show privacy policy only when button is clicked (NOT automatically)
        btnPrivacy.setOnClickListener(v -> showPrivacyPolicy());
    }

    private void showPrivacyPolicy() {
        String policy = "Privacy Policy for Satellite Sky Radar\n\n" +
                "Effective Date: July 1, 2026\n\n" +
                "This app does NOT collect, store, or transmit any personal information. " +
                "It uses your device’s GPS/GNSS receiver only to display live satellite positions and raw measurements on the screen. " +
                "All data remains on your device and is never sent to any server.\n\n" +
                "Location Permission: We request access to fine location solely to obtain satellite status (azimuth, elevation, signal strength, etc.). " +
                "This is the core functionality of the app.\n\n" +
                "If you have questions, contact: heaticdeveloper@gmail.com";

        new AlertDialog.Builder(this)
                .setTitle("Privacy Policy")
                .setMessage(policy)
                .setPositiveButton("Close", null)
                .show();
    }

    private void checkAndRequestLocationPermission() {
        if (checkLocationPermission()) {
            startAllUpdates();
            return;
        }

        // Permission not granted
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // User has denied once – show rationale and ask again
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission Required")
                    .setMessage("Satellite Sky Radar needs access to your location to display live GPS satellites on the radar. Please grant the permission.")
                    .setPositiveButton("Grant", (dialog, which) ->
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    LOCATION_PERMISSION_REQUEST))
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            // First launch OR permanently denied
            boolean hasAskedBefore = prefs.getBoolean("permission_requested", false);
            if (!hasAskedBefore) {
                // First time – show system prompt
                prefs.edit().putBoolean("permission_requested", true).apply();
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST);
            } else {
                // User previously chose "Don't ask again" → show a non‑intrusive message instead of forced dialog
                showPermissionDeniedPermanentlyUI();
            }
        }
    }

    private void showPermissionDeniedPermanentlyUI() {
        Toast.makeText(this, "Location permission permanently denied. Grant it in Settings.", Toast.LENGTH_LONG).show();
        // Optionally, you could show a dialog only once per session, but here we just use a Toast.
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAllUpdates();
            } else {
                // Permission denied again
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // The user checked "Don't ask again" – show the permanent denial dialog
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Denied")
                            .setMessage("You have denied location permission permanently. To use this app, you must enable it manually from the Settings.")
                            .setPositiveButton("Open Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    Toast.makeText(this, "Location permission is required to display satellites.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void startRawDataWatchdog() {
        Thread watchdog = new Thread(() -> {
            while (!Thread.interrupted()) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                long now = System.currentTimeMillis();
                if (rawMeasurementsActive && (now - lastRawTimestamp > RAW_TIMEOUT_MS)) {
                    rawMeasurementsActive = false;
                    runOnUiThread(() -> updateRawStatusText());
                }
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void updateRawStatusText() {
        if (rawMeasurementsActive) {
            tvRawStatus.setText("Raw data: Active ✅");
            tvRawStatus.setTextColor(0xFF00FF00);
        } else {
            tvRawStatus.setText("Raw data: Inactive ❌");
            tvRawStatus.setTextColor(0xFFFF5555);
        }
    }

    private void showSatelliteDetails(SatelliteInfo sat, GnssMeasurement meas) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(sat.getName()).append("\n");
        sb.append("Constellation: ").append(getConstellationName(sat.constellationType)).append("\n");
        sb.append("SVID: ").append(sat.svid).append("\n");
        sb.append("Azimuth: ").append(String.format("%.1f°", sat.azimuth)).append("\n");
        sb.append("Elevation: ").append(String.format("%.1f°", sat.elevation)).append("\n");
        sb.append("C/N0: ").append(String.format("%.1f dB‑Hz", sat.cn0DbHz)).append("\n");
        sb.append("Used in fix: ").append(sat.usedInFix ? "Yes" : "No").append("\n");
        sb.append("Range rate: ");
        if (Math.abs(sat.rangeRateMps) > 0.1f) {
            sb.append(String.format("%.1f m/s", sat.rangeRateMps));
        } else {
            sb.append("N/A (raw data needed)");
        }
        sb.append("\n");

        if (meas != null) {
            sb.append("\n--- Raw Measurements ---\n");
            if (meas.hasCarrierFrequencyHz()) {
                sb.append("Carrier freq: ").append(String.format("%.2f MHz", meas.getCarrierFrequencyHz() / 1e6)).append("\n");
            }
            if (meas.getAccumulatedDeltaRangeUncertaintyMeters() > 0.0) {
                sb.append("Accum. delta range: ").append(String.format("%.3f m", meas.getAccumulatedDeltaRangeMeters())).append("\n");
            }
            if (meas.getPseudorangeRateUncertaintyMetersPerSecond() > 0.0) {
                sb.append("Pseudo-rate uncertainty: ").append(String.format("%.2f m/s", meas.getPseudorangeRateUncertaintyMetersPerSecond())).append("\n");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int mp = meas.getMultipathIndicator();
                String mpText;
                switch (mp) {
                    case GnssMeasurement.MULTIPATH_INDICATOR_DETECTED: mpText = "Detected"; break;
                    case GnssMeasurement.MULTIPATH_INDICATOR_NOT_DETECTED: mpText = "Not detected"; break;
                    default: mpText = "Unknown"; break;
                }
                sb.append("Multipath: ").append(mpText).append("\n");
            }
            sb.append("Received GPS Time: ").append(meas.getReceivedSvTimeNanos()).append(" ns\n");
        } else {
            sb.append("\n(No raw measurement data)");
        }

        new AlertDialog.Builder(this)
                .setTitle("Satellite Details")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private String getConstellationName(int type) {
        switch (type) {
            case GnssStatus.CONSTELLATION_GPS: return "GPS";
            case GnssStatus.CONSTELLATION_GLONASS: return "GLONASS";
            case GnssStatus.CONSTELLATION_BEIDOU: return "BeiDou";
            case GnssStatus.CONSTELLATION_GALILEO: return "Galileo";
            case GnssStatus.CONSTELLATION_QZSS: return "QZSS";
            case GnssStatus.CONSTELLATION_IRNSS: return "IRNSS";
            case GnssStatus.CONSTELLATION_SBAS: return "SBAS";
            default: return "Unknown (" + type + ")";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (checkLocationPermission()) {
            startAllUpdates();
        } else {
            stopAllUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        stopAllUpdates();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            hasGravity = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, 3);
            hasGeomagnetic = true;
        }
        if (hasGravity && hasGeomagnetic) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                float heading = (float) Math.toDegrees(orientation[0]);
                if (heading < 0) heading += 360;
                radarView.setHeading(heading);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startAllUpdates() {
        if (locationManager == null) return;
        try {
            if (checkLocationPermission()) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L, 0f, locationListener);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    locationManager.registerGnssStatusCallback(gnssStatusCallback);
                    locationManager.registerGnssMeasurementsCallback(gnssMeasurementsCallback);
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void stopAllUpdates() {
        if (locationManager == null) return;
        locationManager.removeUpdates(locationListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
            locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback);
        }
    }
}