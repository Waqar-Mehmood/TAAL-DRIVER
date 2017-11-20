package com.android.taal_driver;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.taal_driver.AppConstants.DISTANCE;
import static com.android.taal_driver.AppConstants.DRIVER;
import static com.android.taal_driver.AppConstants.DRIVERS;
import static com.android.taal_driver.AppConstants.DRIVER_RATING;
import static com.android.taal_driver.AppConstants.RIDER;
import static com.android.taal_driver.AppConstants.RIDER_ID;
import static com.android.taal_driver.AppConstants.RIDER_RATING;
import static com.android.taal_driver.AppConstants.RIDER_REQUEST;
import static com.android.taal_driver.AppConstants.RIDE_PRICE;
import static com.android.taal_driver.AppConstants.SERVICE;
import static com.android.taal_driver.AppConstants.TIME_STAMP;
import static com.android.taal_driver.AppConstants.USERS;
import static com.android.taal_driver.AppConstants.USER_DETAILS;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener, RoutingListener {

    private GoogleMap mMap;
    private static GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLocationRequest;
    private Marker mPickupMarker;
    private SupportMapFragment mMapFragment;
    private LatLng mDestinationLatLng;
    private LatLng mPickupLatLng;

    private DatabaseReference mAssignedRiderPickupLocationReference;
    private ValueEventListener mAssignedRiderPickupLocationReferenceListener;

    private LinearLayout mRiderInfo;
    private TextView mRiderName;
    private TextView mRiderPhoneNumber;
    private TextView mRiderDestination;
    private ImageView mRiderProfilePic;
    private Button mLogout;
    private Button mSettings;
    private Button mHistory;
    private Button mRideStatus;
    private Switch mWorkigSwitch;
    private RatingBar mRatingBar;

    private boolean mDriverStatus = false;
    private float mRideDistance;
    private String mRiderId = "";
    private String mDriverId;
    private String mDestination;
    private String mService;
    private int mStatus = 0;
    private final int LOCATION_REQUEST_CODE = 1;

    private List<Polyline> mPolylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};

    private Date mTime1;
    private Date mTime2;

    private double mRideStartTime;
    private double mRideEndTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_maps);

        String mProvider = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        // if gps is disabled show a message dialog to turn on gps
        if (!mProvider.contains("gps")) {
            buildAlertMessageNoGps();
        }

        // get driver id
        mDriverId = FirebaseAuth.getInstance().getUid();

        mRiderName = findViewById(R.id.d_rider_name);
        mRiderPhoneNumber = findViewById(R.id.d_rider_phone_number);
        mRiderDestination = findViewById(R.id.d_rider_destination);
        mRiderProfilePic = findViewById(R.id.d_rider_profile_pic);
        mRiderInfo = findViewById(R.id.d_rider_info);

        mRatingBar = findViewById(R.id.d_rider_rating_bar);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mMapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.driver_map);

        // driver logout button
        mLogout = findViewById(R.id.driver_logout);
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Signout the driver
                signingOutDriver();
            }
        });

        mHistory = findViewById(R.id.driver_history);
        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DriverMapActivity.this, HistoryActivity.class);
                startActivity(intent);
            }
        });

        mSettings = findViewById(R.id.driver_settings);
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DriverMapActivity.this, DriverSettingActivity.class);
                startActivity(intent);
            }
        });

        mWorkigSwitch = findViewById(R.id.working_switch);
        mWorkigSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    mDriverStatus = true;
                    connectDriver();
                } else {
                    disconnectDriver();
                }
            }
        });

        mRideStatus = findViewById(R.id.ride_status);
        mRideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (mStatus) {
                    case 1:
                        mStatus = 2;

                        mTime1 = new Date();

                        erasePolylines();

                        if (mDestinationLatLng.latitude != 0.0 && mDestinationLatLng.longitude != 0.0) {
                            getRouteToMarker();
                        }

                        mRideStatus.setText("Drive Completed");
                        break;

                    case 2:
                        mTime2 = new Date();

                        recordRide();
                        endRide();
                        break;
                }
            }
        });

        // request permission to access gps
        requestContactsPermission();

        // get driver service
        getDriverService();

        // get assigned rider information
        getAssignedRider();
    }

    private String rideTotalTime() {

        Calendar c = Calendar.getInstance();

        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
        String formattedDate = df.format(c.getTime());

        return formattedDate;
    }

    private void buildAlertMessageNoGps() {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("GPS Network Not Enabled")
                .setCancelable(false)
                .setTitle("GPS Alert")
                .setPositiveButton("Turn it On",
                        new DialogInterface.OnClickListener() {
                            public void onClick(
                                    @SuppressWarnings("unused") final DialogInterface dialog,
                                    @SuppressWarnings("unused") final int id) {
                                startActivity(new Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog,
                                                @SuppressWarnings("unused") final int id) {
                                dialog.cancel();

                            }
                        });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void recordRide() {

        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child(USERS)
                .child(DRIVERS).child(mDriverId).child(AppConstants.HISTORY);
        DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference().child(USERS)
                .child(AppConstants.RIDERS).child(mRiderId).child(AppConstants.HISTORY);
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child(AppConstants.HISTORY);

        String requestId = historyRef.push().getKey();
        driverRef.child(requestId).setValue(true);
        customerRef.child(requestId).setValue(true);

        String rideDistance = String.valueOf(mRideDistance);

        HashMap map = new HashMap();
        map.put(DRIVER, mDriverId);
        map.put(RIDER, mRiderId);
        map.put(DRIVER_RATING, "0");
        map.put(RIDER_RATING, "0");
        map.put(TIME_STAMP, getCurrentTimestamp());
        map.put(AppConstants.DESTINATION, mDestination);
        map.put("location/from/lat", mPickupLatLng.latitude);
        map.put("location/from/lng", mPickupLatLng.longitude);
        map.put("location/to/lat", mDestinationLatLng.latitude);
        map.put("location/to/lng", mDestinationLatLng.longitude);
        map.put(DISTANCE, rideDistance.substring(0, Math.min(rideDistance.length(), 5)));

        historyRef.child(requestId).updateChildren(map);

        ridePriceCalculation(requestId);
    }

    private void ridePriceCalculation(String requestId) {

        int basePrice = 0;

        switch (mService) {
            case "UberX":
                basePrice = 50;
                break;
            case "UberBlack":
                basePrice = 100;
                break;
            case "UberXl":
                basePrice = 150;
                break;
        }

        double mDistancePrice = Double.valueOf(mRideDistance) * 10;

        long mills = mTime1.getTime() - mTime2.getTime();
        int hours = Integer.parseInt(String.valueOf(mills / (1000 * 60 * 60)));
        int mins = Integer.parseInt(String.valueOf(mills % (1000 * 60 * 60)));

        int timePrice = 0;

        if (hours > 0) {
            timePrice = timePrice * hours * 60;
        }

        if (mins > 0) {
            timePrice = timePrice * mins;
        }

        if (timePrice > 0) {
            timePrice = timePrice * 10;
        }

        double mRidePrice = mDistancePrice + basePrice + timePrice;

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference().child(AppConstants.HISTORY);

        Map map = new HashMap();

        String price = String.valueOf(mRidePrice);

        map.put(RIDE_PRICE, price.substring(0, Math.min(price.length(), 5)));
        reference.child(requestId).updateChildren(map);
    }

    private void getDriverService() {
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference().child(USERS).child(DRIVERS)
                .child(mDriverId).child(USER_DETAILS).child(SERVICE);
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    mService = dataSnapshot.getValue(String.class);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private String getCurrentTimestamp() {
        Long timestamp = System.currentTimeMillis() / 1000;
        return timestamp.toString();
    }

    private void getRouteToMarker() {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), mDestinationLatLng)
                .build();
        routing.execute();
    }

    private void endRide() {
        // remove route lines
        if (mPolylines != null) {
            erasePolylines();
        }

        mRideStatus.setText("Picked Rider");

        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child(USERS)
                .child(DRIVERS).child(mDriverId).child(RIDER_REQUEST);
        driverRef.removeValue();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(AppConstants.RIDERS_REQUEST);
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(mRiderId);

        // reset rider id, ride distance
        mRiderId = "";
        mRideDistance = 0;

        // remove assigned rider marker on driver's map
        if (mPickupMarker != null) {
            mPickupMarker.remove();
        }

        // remove assign rider pickup location listener
        if (mAssignedRiderPickupLocationReference != null) {
            mAssignedRiderPickupLocationReference.removeEventListener(mAssignedRiderPickupLocationReferenceListener);
        }

        mRiderInfo.setVisibility(View.GONE);
        mRiderName.setText("");
        mRiderPhoneNumber.setText("");
        mRiderDestination.setText("Destination: --");
        mRiderProfilePic.setImageResource(R.drawable.unknown_profile_pic);
    }

    public void requestContactsPermission() {
        // get permission if it is not granted
        if (ContextCompat.checkSelfPermission(DriverMapActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        } else {
            mMapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mMapFragment.getMapAsync(this);
                } else {
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    // Signout the driver
    public void signingOutDriver() {
        // disconnect driver from app
        disconnectDriver();

        // signing out driver
        FirebaseAuth.getInstance().signOut();

        // move to main activity
        Intent intent = new Intent(DriverMapActivity.this, DriverLoginActivity.class);
        startActivity(intent);
        finish();
    }

    // get assigned rider information
    private void getAssignedRider() {

        // get reference of assigned Rider Id
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference().child(USERS)
                .child(DRIVERS).child(mDriverId).child(RIDER_REQUEST).child(RIDER_ID);

        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // check if rider assigned to any driver
                // if exist
                if (dataSnapshot.exists()) {
                    mStatus = 1;
                    // get assigned rider id
                    mRiderId = dataSnapshot.getValue().toString();
                    // get assigned rider pickup location
                    getAssignedRiderPickupLocation();
                    // get assigned rider info
                    getAssignedRiderInfo();
                    // get assigned rider destination
                    getAssignedRiderDestination();


                } else {
                    endRide();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    // get assigned rider pickup location
    private void getAssignedRiderPickupLocation() {

        mAssignedRiderPickupLocationReference = FirebaseDatabase.getInstance().getReference()
                .child(AppConstants.RIDERS_REQUEST).child(mRiderId).child(AppConstants.LOCATION);
        // add value event listener on rider request location
        mAssignedRiderPickupLocationReferenceListener = mAssignedRiderPickupLocationReference
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        if (dataSnapshot.exists() && !mRiderId.equals("")) {
                            List<Object> map = (List<Object>) dataSnapshot.getValue();

                            double locationLat = 0;
                            double locationLng = 0;

                            // get assigned rider latitude
                            if (map.get(0) != null) {
                                locationLat = Double.parseDouble(map.get(0).toString());
                            }
                            // get assigned rider longitude
                            if (map.get(1) != null) {
                                locationLng = Double.parseDouble(map.get(1).toString());
                            }

                            // store the latitude and longitude in mPickupLatLng
                            mPickupLatLng = new LatLng(locationLat, locationLng);

                            // add marker of assigned rider on driver's map
                            mPickupMarker = mMap.addMarker(new MarkerOptions().position(mPickupLatLng).title("Pickup location")
                                    .icon(BitmapDescriptorFactory.fromResource(R.mipmap.rider_location)));

                            // draw route lines on map
                            getRouteToMarker(mPickupLatLng);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
    }

    // draw route lines on map
    private void getRouteToMarker(LatLng riderLatLng) {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), riderLatLng)
                .build();
        routing.execute();
    }

    // get assigned rider info
    private void getAssignedRiderInfo() {

        mRiderInfo.setVisibility(View.VISIBLE);

        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference()
                .child(USERS).child(AppConstants.RIDERS).child(mRiderId);

        databaseReference.child(AppConstants.USER_DETAILS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();

                    if (map.get(AppConstants.NAME) != null) {
                        mRiderName.setText(map.get(AppConstants.NAME).toString().toUpperCase());
                    }

                    if (map.get(AppConstants.PHONE_NUMBER) != null) {
                        mRiderPhoneNumber.setText(map.get(AppConstants.PHONE_NUMBER).toString());
                    }

                    if (map.get(AppConstants.PROFILE_IMAGE_URL) != null) {
                        Picasso.with(getApplication())
                                .load(map.get(AppConstants.PROFILE_IMAGE_URL).toString())
                                .placeholder(R.drawable.progress_animation)
                                .into(mRiderProfilePic);
                    } else {
                        mRiderProfilePic.setImageResource(R.drawable.unknown_profile_pic);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        // get values from rating node
        databaseReference.child(RIDER_RATING).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    int ratingSum = 0;
                    int ratingsTotal = 0;

                    for (DataSnapshot child : dataSnapshot.getChildren()) {
                        ratingSum = ratingSum + Integer.valueOf(child.getValue().toString());
                        ratingsTotal++;
                    }

                    if (ratingsTotal != 0) {
                        mRatingBar.setRating(ratingSum / ratingsTotal);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    // get assigned rider destination
    private void getAssignedRiderDestination() {

        // get reference of assigned Rider destination
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference().child(USERS)
                .child(DRIVERS).child(mDriverId).child(RIDER_REQUEST);

        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.exists()) {

                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();

                    if (map.get(AppConstants.DESTINATION) != null) {
                        mDestination = map.get(AppConstants.DESTINATION).toString();
                        mRiderDestination.setText("Destination: " + mDestination);
                    } else {
                        mRiderDestination.setText("Destination: --");
                    }

                    double destinationLat = 0.0;
                    double destinationLng = 0.0;

                    if (map.get(AppConstants.DESTINATION_LATITUDE) != null) {
                        destinationLat = Double.valueOf(map.get(AppConstants.DESTINATION_LATITUDE).toString());
                    }

                    if (map.get(AppConstants.DESTINATION_LONGITUDE) != null) {
                        destinationLng = Double.valueOf(map.get(AppConstants.DESTINATION_LONGITUDE).toString());
                    }

                    mDestinationLatLng = new LatLng(destinationLat, destinationLng);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        buildGoogleApiClint();
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGoogleApiClint() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // set location request parameter's of driver
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {

        if (getApplicationContext() != null) {

            if (!mRiderId.equals("")) {
                mRideDistance += mLastLocation.distanceTo(location) / 1000;
            }

            mLastLocation = location;

            // update location of driver on location changed
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

            // if the WorkingSwitch is ON then do the following
            if (mDriverStatus) {

                DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference(AppConstants.DRIVERS_AVAILABLE);
                DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference(AppConstants.DRIVERS_WORKING);

                GeoFire geoFireAvailable = new GeoFire(refAvailable);
                GeoFire geoFireWorking = new GeoFire(refWorking);

                // change the driver status w.r.t assigned rider (available to working) or (working to available)
                switch (mRiderId) {
                    // if the driver has no assigned rider then its status is Available
                    case "":
                        geoFireWorking.removeLocation(mDriverId);
                        geoFireAvailable.setLocation(mDriverId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                        break;
                    // otherwise Working
                    default:
                        geoFireAvailable.removeLocation(mDriverId);
                        geoFireWorking.setLocation(mDriverId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                        break;
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        finishAffinity();
    }

    @Override
    public void onRoutingFailure(RouteException e) {
        if (e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if (mPolylines != null) {
            if (mPolylines.size() > 0) {
                for (Polyline poly : mPolylines) {
                    poly.remove();
                }
            }
        }

        mPolylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i < route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            mPolylines.add(polyline);

            Toast.makeText(getApplicationContext(), "Route " + (i + 1) + ": distance - " + route.get(i).getDistanceValue() + ": duration - " + route.get(i).getDurationValue(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingCancelled() {

    }

    private void erasePolylines() {
        if (mPolylines != null) {

            for (Polyline line : mPolylines) {
                line.remove();
            }

            mPolylines.clear();
        }
    }

    private void connectDriver() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    // disconnect the driver
    public void disconnectDriver() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference(AppConstants.DRIVERS_AVAILABLE);

        GeoFire geoFire = new GeoFire(reference);
        geoFire.removeLocation(mDriverId);
    }
}
