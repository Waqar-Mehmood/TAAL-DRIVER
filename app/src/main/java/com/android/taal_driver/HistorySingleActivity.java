package com.android.taal_driver;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.android.taal_driver.AppConstants.DISTANCE;
import static com.android.taal_driver.AppConstants.RIDER;
import static com.android.taal_driver.AppConstants.RIDER_RATING;
import static com.android.taal_driver.AppConstants.RIDE_PRICE;
import static com.android.taal_driver.AppConstants.TIME_STAMP;

public class HistorySingleActivity extends AppCompatActivity implements OnMapReadyCallback, RoutingListener {

    private TextView mRideLocation;
    private TextView mRideDistance;
    private TextView mRideDate;
    private TextView mUserName;
    private TextView mUserPhone;
    private TextView mRidePrice;
    private ImageView mUserImage;

    private RatingBar mRatingBar;

    private LatLng mDestinationLatLng;
    private LatLng mPickupLatLng;

    private GoogleMap mMap;
    private SupportMapFragment mMapFragment;

    private DatabaseReference mHistoryRideInfoDb;

    private List<Polyline> mPolylines;

    private String mRideId;
    private String mRiderId;
    private String mDriverId;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};
    private String distance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_single);

        mPolylines = new ArrayList<>();

        mRideId = getIntent().getExtras().getString(AppConstants.RIDE_ID);

        mMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mMapFragment.getMapAsync(this);

        mRideLocation = findViewById(R.id.ride_location);
        mRideDistance = findViewById(R.id.ride_distance);
        mRideDate = findViewById(R.id.ride_date);
        mUserName = findViewById(R.id.user_name);
        mUserPhone = findViewById(R.id.user_phone);
        mUserImage = findViewById(R.id.user_image);

        mRidePrice = findViewById(R.id.ride_price);

        mRatingBar = findViewById(R.id.rating_bar);

        mDriverId = FirebaseAuth.getInstance().getUid();

        mHistoryRideInfoDb = FirebaseDatabase.getInstance().getReference().child(AppConstants.HISTORY).child(mRideId);

        getRideInformation();
    }

    private void getRideInformation() {

        mHistoryRideInfoDb.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot child : dataSnapshot.getChildren()) {

                        if (child.getKey().equals(RIDER)) {
                            mRiderId = child.getValue(String.class);
                            getUserInformation();
                        }

                        if (child.getKey().equals(TIME_STAMP)) {
                            mRideDate.setText(getDate(Long.valueOf(child.getValue().toString())));
                        }

                        if (child.getKey().equals(RIDER_RATING)) {
                            mRatingBar.setRating(Integer.valueOf(child.getValue().toString()));
                        }

                        if (child.getKey().equals(DISTANCE)) {
                            mRideDistance.setText(child.getValue().toString() + " km");
                        }

                        if (child.getKey().equals(RIDE_PRICE)) {
                            mRidePrice.setText(child.getValue().toString() + " Rs");
                        }

                        if (child.getKey().equals(AppConstants.DESTINATION)) {
                            mRideLocation.setText(child.getValue().toString());
                        }

                        if (child.getKey().equals(AppConstants.ROUTE_LOCATION)) {
                            mPickupLatLng = new LatLng(Double.valueOf(child.child("from").child("lat").getValue().toString()),
                                    Double.valueOf(child.child("from").child("lng").getValue().toString()));
                            mDestinationLatLng = new LatLng(Double.valueOf(child.child("to").child("lat").getValue().toString()),
                                    Double.valueOf(child.child("to").child("lng").getValue().toString()));
                            if (mDestinationLatLng != new LatLng(0, 0)) {
                                getRouteToMarker();
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private void getUserInformation() {
        mRatingBar.setVisibility(View.VISIBLE);
        mRatingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean b) {

                mHistoryRideInfoDb.child(RIDER_RATING).setValue(rating);

                DatabaseReference mDriverRatingDb = FirebaseDatabase.getInstance().getReference().child(AppConstants.USERS)
                        .child(AppConstants.RIDERS).child(mRiderId).child(RIDER_RATING).child(mRideId);
                mDriverRatingDb.setValue(rating);
            }
        });

        DatabaseReference mOtherUserDB = FirebaseDatabase.getInstance().getReference().child(AppConstants.USERS)
                .child(AppConstants.RIDERS).child(mRiderId).child(AppConstants.USER_DETAILS);
        mOtherUserDB.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();

                    if (map.get(AppConstants.NAME) != null) {
                        mUserName.setText(map.get(AppConstants.NAME).toString());
                    }

                    if (map.get(AppConstants.PHONE_NUMBER) != null) {
                        mUserPhone.setText(map.get(AppConstants.PHONE_NUMBER).toString());
                    }

                    if (map.get(AppConstants.PROFILE_IMAGE_URL) != null) {
                        Picasso.with(getApplication())
                                .load(map.get(AppConstants.PROFILE_IMAGE_URL).toString())
                                .placeholder(R.drawable.progress_animation)
                                .into(mUserImage);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private String getDate(Long time) {
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(time * 1000);
        String date = DateFormat.format("MM-dd-yyyy hh:mm", cal).toString();
        return date;
    }

    private void getRouteToMarker() {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(mPickupLatLng, mDestinationLatLng)
                .build();
        routing.execute();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
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

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(mPickupLatLng);
        builder.include(mDestinationLatLng);
        LatLngBounds bounds = builder.build();

        int width = getResources().getDisplayMetrics().widthPixels;
        int padding = (int) (width * 0.2);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);

        mMap.animateCamera(cameraUpdate);

        mMap.addMarker(new MarkerOptions().position(mPickupLatLng).title("Pickup Location"));
        mMap.addMarker(new MarkerOptions().position(mDestinationLatLng).title("Destination"));

        if (mPolylines.size() > 0) {
            for (Polyline poly : mPolylines) {
                poly.remove();
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
        for (Polyline line : mPolylines) {
            line.remove();
        }
        mPolylines.clear();
    }

}
