package com.example.rssiapp;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MainActivity for RSSI-based Heatmap App.
 * - Captures RSSI values from the currently connected WiFi network.
 * - Logs geographic location and signal strength.
 * - Visualizes data on a live-updating Google Maps heatmap.
 * - Periodically saves logs as a CSV file to Downloads.
 */
public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final int UPDATE_INTERVAL_MS = 5000;
    private final List<WeightedLatLng> heatmapData = new ArrayList<>();
    private GoogleMap mMap;
    private WifiManager wifiManager;
    private FusedLocationProviderClient fusedLocationClient;
    private Handler handler;
    private TextView statusTextView;
    private HeatmapTileProvider heatmapProvider;
    private TileOverlay heatmapOverlay;

    private final Runnable updateRunnable = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void run() {
            updateLocationAndHeatmap();
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        handler = new Handler(Looper.getMainLooper());

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        mMap.setMyLocationEnabled(true);
        startPeriodicUpdates();
    }

    private void startPeriodicUpdates() {
        handler.post(updateRunnable);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void updateLocationAndHeatmap() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) return;

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo.getSSID().replace("\"", "");
            int rssi = wifiInfo.getRssi();

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            float normalizedRssi = Math.max(0f, 100 + rssi);
            heatmapData.add(new WeightedLatLng(latLng, normalizedRssi));
            drawOrUpdateHeatmap();

            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String status = "SSID: " + ssid + "\n" +
                    "RSSI: " + rssi + " dBm\n" +
                    "Lat: " + latLng.latitude + "\n" +
                    "Lon: " + latLng.longitude + "\n" +
                    "Updated at: " + time;
            statusTextView.setText(status);

            saveCSVToDownloadsFolder(ssid, rssi, latLng, time);

            if (heatmapData.size() == 1) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f));
            }
        });
    }

    private void drawOrUpdateHeatmap() {
        if (heatmapProvider == null) {
            heatmapProvider = new HeatmapTileProvider.Builder()
                    .weightedData(heatmapData)
                    .radius(40)
                    .build();
            heatmapOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(heatmapProvider));
        } else {
            heatmapProvider.setWeightedData(heatmapData);
            heatmapOverlay.clearTileCache();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void saveCSVToDownloadsFolder(String ssid, int rssi, LatLng latLng, String time) {
        if (ssid == null || ssid.isEmpty()) return;

        String safeSsid = ssid.replaceAll("[^a-zA-Z0-9_-]", "_");
        String fileName = safeSsid + ".csv";

        ContentResolver resolver = getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri fileUri = null;

        Cursor cursor = resolver.query(
                collection,
                new String[]{MediaStore.Downloads._ID},
                MediaStore.Downloads.DISPLAY_NAME + "=?",
                new String[]{fileName},
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
            fileUri = Uri.withAppendedPath(collection, String.valueOf(id));
            cursor.close();
        }

        if (fileUri == null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/");
            fileUri = resolver.insert(collection, values);

            if (fileUri != null) {
                try (OutputStream out = resolver.openOutputStream(fileUri, "w")) {
                    out.write("SSID,RSSI,Latitude,Longitude,Time\n".getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

        if (fileUri != null) {
            try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(fileUri, "wa");
                 FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {

                String row = String.format(Locale.US, "%s,%d,%.6f,%.6f,%s\n",
                        ssid, rssi, latLng.latitude, latLng.longitude, time);
                out.write(row.getBytes());

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to append to CSV", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onMapReady(mMap);
        }
    }
}