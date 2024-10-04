package com.example.schoolbusapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
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
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class SubActivity extends AppCompatActivity implements OnMapReadyCallback {

    private DatabaseReference gpsRef;
    private Marker busMarker;
    ImageButton dir_school;
    ImageButton refreshButton;

    Location lastLocation;
    String lastLatitude = "";
    String lastLongitude = "";
    double totalDistance = 0.0;

    TextView remainingTimeTextView;
    TextView bus1;
    TextView bus2;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;
    private NaverMap naverMap;

    private RetrieveDataAsyncTask retrieveDataAsyncTask;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub);

        // 새로고침 버튼 클릭 이벤트 처리
        refreshButton = findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchData(); // 데이터 새로고침
            }
        });

        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        //개발자 정보 버튼 클릭시 액티비티 전환
        dir_school = findViewById(R.id.imageButton);
        dir_school.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
            }
        });

        // Firebase Realtime Database와의 연결 설정
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        gpsRef = database.getReference("gps_coordinates");

        // 지도를 표시하기 위해 MapFragment 추가
        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment) fm.findFragmentById(R.id.map_fragment);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map_fragment, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);

        // TextView 초기화
        remainingTimeTextView = findViewById(R.id.remainingTimeTextView);
        bus1 = findViewById(R.id.bus1);
        bus2 = findViewById(R.id.bus2);

        // 데이터 가져오기 및 버스 정보 표시
        fetchData();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) { // 권한 거부됨
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
        this.naverMap = naverMap;
        naverMap.setLocationSource(locationSource);

        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setZoomControlEnabled(true); // 확대/축소 컨트롤 활성화
        uiSettings.setLocationButtonEnabled(true); // 현재 위치 버튼 활성화

        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

        PathOverlay path1 = new PathOverlay();
        path1.setCoords(Arrays.asList(
                 // 성환 -> 남서울대
                new LatLng(36.9161, 127.1281),
                new LatLng(36.9114, 127.1283),
                new LatLng(36.9118, 127.1383),
                new LatLng(36.9117, 127.1435),
                new LatLng(36.9105, 127.1429),
                new LatLng(36.9105, 127.1424),
                new LatLng(36.9100, 127.1422)
        ));
        path1.setWidth(20);
        path1.setColor(ContextCompat.getColor(this, R.color.blue));
        path1.setOutlineColor(R.color.blue);
        path1.setPatternImage(OverlayImage.fromResource(R.drawable.path_pattern));
        path1.setPatternInterval(100);
        path1.setPassedColor(Color.GRAY);
        path1.setMap(naverMap);

        // 도착 마커 이미지 설정
        Marker busStop = new Marker();
        busStop.setPosition(new LatLng(36.9161, 127.1280));
        Bitmap busStopBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.flag);
        OverlayImage busStopImage = OverlayImage.fromBitmap(busStopBitmap);
        busStop.setIcon(busStopImage);
        busStop.setMap(naverMap);

        // 출발 마커 이미지 설정
        Marker busStop2 = new Marker();
        busStop2.setPosition(new LatLng(36.9100, 127.1423));
        Bitmap busStopBitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.flag2);
        OverlayImage busStopImage2 = OverlayImage.fromBitmap(busStopBitmap2);
        busStop2.setIcon(busStopImage2);
        busStop2.setMap(naverMap);

        // 초기 위치 설정
        LatLng initialPosition = new LatLng(36.9123, 127.1360);
        busMarker = new Marker();
        busMarker.setPosition(initialPosition);
        Bitmap markerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bus_img);
        OverlayImage markerImage = OverlayImage.fromBitmap(markerBitmap);
        busMarker.setIcon(markerImage);
        busMarker.setMap(naverMap);
        naverMap.moveCamera(CameraUpdate.scrollAndZoomTo(initialPosition, 13.5)
                .animate(CameraAnimation.Fly, 3000));

        // 20초마다 위치 업데이트
        startBusLocationUpdate();
    }

    private void startBusLocationUpdate() {
        if (retrieveDataAsyncTask != null) {
            retrieveDataAsyncTask.cancel(true);
        }
        retrieveDataAsyncTask = new RetrieveDataAsyncTask();
        retrieveDataAsyncTask.execute();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateBusLocation();
            }
        }, 0, 20000); // 초기 지연 없이 20초마다 실행
    }

    private void updateBusLocation() {
        gpsRef.addListenerForSingleValueEvent(new ValueEventListener() {
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

                    // 거리 및 시간 계산
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
                    // UI 스레드에서 TextView에 출력
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 남은 거리 및 시간 계산
                            double remainingDistance = 4000 - totalDistance;
                            double remainingTimeSeconds = Math.floor((remainingDistance / 3000) * 600); // 초 단위로 계산
                            long remainingMinutes = (long) Math.floor(remainingTimeSeconds / 60);
                            long remainingSeconds = (long) (remainingTimeSeconds % 60);

                            // TextView에 표시
                            remainingTimeTextView.setText("남서울대까지 남은 시간: " + remainingMinutes + "분 " + remainingSeconds + "초");
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


    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine 공식 사용
        double earthRadius = 6371000; // 지구 반지름 (미터)
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private void fetchData() {
        new RetrieveDataAsyncTask().execute();
    }

    // AsyncTask 내에서 네트워크 요청과 XML 데이터 처리 부분 수정
    private class RetrieveDataAsyncTask extends AsyncTask<Void, Void, List<BusInfo>> {
        @Override
        protected List<BusInfo> doInBackground(Void... voids) {
            List<BusInfo> busInfoList = new ArrayList<>();
            try {
                URL url = new URL("http://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList?serviceKey=vvBMjm9o30%2ByRZmA2waCZB%2BRC2jr6xAuYSXsKY2a%2BGzyNExdfoeNMpfnGDH7Mz0UCrcOiMcHEJzFx3prpzBUow%3D%3D&cityCode=34010&nodeId=CAB285000098&_type=xml");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                InputStream is = conn.getInputStream();
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser xpp = factory.newPullParser();
                xpp.setInput(new InputStreamReader(is, "UTF-8"));

                String tag;
                String routeNo = null;
                int arrivalTime = 0;
                xpp.next();
                int eventType = xpp.getEventType();

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    switch (eventType) {
                        case XmlPullParser.START_DOCUMENT:
                            break;

                        case XmlPullParser.START_TAG:
                            tag = xpp.getName();

                            if (tag.equals("item")) {
                                // 새로운 아이템 시작 시 이전 아이템 정보를 리스트에 추가
                                if (routeNo != null && (routeNo.equals("110") || routeNo.equals("100"))) {
                                    busInfoList.add(new BusInfo(routeNo, arrivalTime));
                                }
                            } else if (tag.equals("routeno")) {
                                routeNo = xpp.nextText();
                            } else if (tag.equals("arrtime")) {
                                arrivalTime = Integer.parseInt(xpp.nextText());
                            }
                            break;
                    }

                    eventType = xpp.next();
                }

                // 마지막 아이템 정보를 리스트에 추가
                if (routeNo != null && (routeNo.equals("110") || routeNo.equals("100"))) {
                    busInfoList.add(new BusInfo(routeNo, arrivalTime));
                }

                Collections.sort(busInfoList, new Comparator<BusInfo>() {
                    @Override
                    public int compare(BusInfo busInfo1, BusInfo busInfo2) {
                        return busInfo1.getArrivalTime() - busInfo2.getArrivalTime();
                    }
                });

            } catch (Exception e) {
                Log.e("SubActivity", "Error fetching data", e);
            }

            return busInfoList;
        }

        // onPostExecute 메서드에서 UI 요소가 null인지 확인하여 안전하게 접근하도록 수정
        @Override
        protected void onPostExecute(List<BusInfo> busInfoList) {
            super.onPostExecute(busInfoList);

            // 리스트가 비어있는 경우 처리
            if (busInfoList.isEmpty()) {
                bus1.setText("도착 예정\n정보 없음");
                bus2.setText("도착 예정\n정보 없음");
                return;
            }

            // 첫 번째 TextView에 가장 적은 시간 출력
            bus1.setText(" " + busInfoList.get(0).getRouteNo() + "번 버스\n " + convertTime(busInfoList.get(0).getArrivalTime()));

            // 두 번째 TextView에 두 번째로 적은 시간 출력 (리스트 크기가 1보다 클 때만)
            if (busInfoList.size() > 1) {
                bus2.setText(" " + busInfoList.get(1).getRouteNo() + "번 버스\n " + convertTime(busInfoList.get(1).getArrivalTime()));
            } else {
                bus2.setText("데이터 없음");
            }
        }
    }

    // onDestroy 메서드에서 타이머를 중지하도록 수정
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 액티비티 종료시 타이머 중지
        if (retrieveDataAsyncTask != null) {
            retrieveDataAsyncTask.cancel(true);
        }
    }

    private String convertTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return minutes + "분 " + remainingSeconds + "초";
    }
}

class BusInfo {
    private String routeNo;
    private int arrivalTime;

    public BusInfo(String routeNo, int arrivalTime) {
        this.routeNo = routeNo;
        this.arrivalTime = arrivalTime;
    }

    public String getRouteNo() {
        return routeNo;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }
}
