package com.example.schoolbusapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private DatabaseReference gpsRef;
    private Marker busMarker;
    ImageButton dir_school;

    Location lastLocation;
    String lastLatitude = "";
    String lastLongitude = "";
    double totalDistance = 0.0;

    TextView remainingTimeTextView;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;
    private NaverMap naverMap;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        dir_school = findViewById(R.id.imageButton);
        dir_school.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), SubActivity.class);
                startActivity(intent);
            }
        });

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        gpsRef = database.getReference("gps_coordinates");

        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment) fm.findFragmentById(R.id.map_fragment);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map_fragment, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);

        remainingTimeTextView = findViewById(R.id.remainingTimeTextView);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        Log.d("MapActivity", "onMapReady");

        this.naverMap = naverMap;
        naverMap.setLocationSource(locationSource);

        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setZoomControlEnabled(true);
        uiSettings.setLocationButtonEnabled(true);

        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

        PathOverlay path1 = new PathOverlay();
        path1.setCoords(Arrays.asList(
                // 남서울대 -> 성환
                new LatLng(36.9100, 127.1422), // 남서울대
                new LatLng(36.9105, 127.1424),
                new LatLng(36.9105, 127.1429),
                new LatLng(36.9120, 127.1437), // 삼거리
                new LatLng(36.9132, 127.1428),
                new LatLng(36.9134, 127.1419),
                new LatLng(36.9139, 127.1404), // 두번째 사거리
                new LatLng(36.9147, 127.1385),
                new LatLng(36.9146, 127.1353),
                new LatLng(36.9208, 127.1348),
                new LatLng(36.9206, 127.1277),
                new LatLng(36.9161, 127.1281) // 성환역


        ));
        path1.setWidth(20);
        path1.setColor(ContextCompat.getColor(this, R.color.blue));
        path1.setOutlineColor(R.color.blue);
        path1.setPatternImage(OverlayImage.fromResource(R.drawable.path_pattern));
        path1.setPatternInterval(100);
        path1.setPassedColor(Color.GRAY);
        path1.setMap(naverMap);

        PathOverlay path2 = new PathOverlay();
        path2.setCoords(Arrays.asList(
                new LatLng(36.9120, 127.1437), // 삼거리
                new LatLng(36.9118 , 127.1383), // 반점
                new LatLng(36.9117, 127.1355), // 사거리
                new LatLng(36.9146, 127.1353) //문화회관

        ));
        path2.setWidth(20);
        path2.setColor(ContextCompat.getColor(this, R.color.blue));
        path2.setOutlineColor(R.color.blue);
        path2.setPatternImage(OverlayImage.fromResource(R.drawable.path_pattern));
        path2.setPatternInterval(100);
        path2.setPassedColor(Color.GRAY);
        path2.setMap(naverMap);

        Marker busStop = new Marker();
        busStop.setPosition(new LatLng(36.9161, 127.1280));
        Bitmap busStopBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.flag2);
        OverlayImage busStopImage = OverlayImage.fromBitmap(busStopBitmap);
        busStop.setIcon(busStopImage);
        busStop.setMap(naverMap);

        Marker busStop2 = new Marker();
        busStop2.setPosition(new LatLng(36.9100, 127.1423));
        Bitmap busStopBitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.flag);
        OverlayImage busStopImage2 = OverlayImage.fromBitmap(busStopBitmap2);
        busStop2.setIcon(busStopImage2);
        busStop2.setMap(naverMap);

        LatLng initialPosition = new LatLng(36.9123, 127.1360);
        busMarker = new Marker();
        busMarker.setPosition(initialPosition);
        Bitmap markerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bus_img);
        OverlayImage markerImage = OverlayImage.fromBitmap(markerBitmap);
        busMarker.setIcon(markerImage);
        busMarker.setMap(naverMap);
        naverMap.moveCamera(CameraUpdate.scrollAndZoomTo(initialPosition, 13.5)
                .animate(CameraAnimation.Fly, 3000));

        gpsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Location lastCoordinates = dataSnapshot.getValue(Location.class);
                if (lastCoordinates != null) {
                    String latitudeStr = lastCoordinates.getLatitude();
                    String longitudeStr = lastCoordinates.getLongitude();

                    Double lat = Double.parseDouble(latitudeStr);
                    Double lng = Double.parseDouble(longitudeStr);

                    busMarker.setPosition(new LatLng(lat, lng));
                    Log.d("GPS2", "Latitude: " + lat + ", Longitude: " + lng);

                    if (!lastLatitude.isEmpty() && !lastLongitude.isEmpty()) {
                        double latitude = Double.parseDouble(latitudeStr);
                        double longitude = Double.parseDouble(longitudeStr);
                        double lastLat = Double.parseDouble(lastLatitude);
                        double lastLon = Double.parseDouble(lastLongitude);
                        double distance = calculateDistance(lastLat, lastLon, latitude, longitude);
                        totalDistance += distance;
                    }
                    lastLatitude = latitudeStr;
                    lastLongitude = longitudeStr;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            double remainingDistance = 4500 - totalDistance;
                            double remainingTimeSeconds = Math.floor((remainingDistance / 3000) * 600);
                            long remainingMinutes = (long) Math.floor(remainingTimeSeconds / 60);
                            long remainingSeconds = (long) (remainingTimeSeconds % 60);

                            remainingTimeTextView.setText("성환역까지 남은 시간: " + remainingMinutes + "분 " + remainingSeconds + "초");
                        }
                    });
                } else {
                    Log.d("GPS", "No data available");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w("GPS", "Failed to read value.", databaseError.toException());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371000;
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}



