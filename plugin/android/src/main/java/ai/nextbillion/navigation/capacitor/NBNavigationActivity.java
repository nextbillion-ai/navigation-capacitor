package ai.nextbillion.navigation.capacitor;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import ai.nextbillion.kits.directions.models.DirectionsRoute;
import ai.nextbillion.kits.geojson.Point;
import ai.nextbillion.navigation.core.navigator.NavProgress;
import ai.nextbillion.navigation.ui.*;
import ai.nextbillion.navigation.ui.listeners.*;

/** The SDK's native NavigationView lives in its own Activity, outside the Capacitor WebView. */
public class NBNavigationActivity extends AppCompatActivity implements OnNavigationReadyCallback, NavigationListener, RouteListener, ai.nextbillion.navigation.core.navigator.ProgressChangeListener {
    private static final String STATE_ARRIVED = "nb_arrived";
    private NavigationView navigationView;
    private boolean closing, arrived;
    private volatile boolean navigationRunning;
    public boolean isGuidanceRunning() { return navigationRunning; }
    private String startupError;
    private final android.os.Handler startupHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private DirectionsRoute route;
    @Override public void onCreate(Bundle state) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar);
        super.onCreate(state);
        arrived=state!=null && state.getBoolean(STATE_ARRIVED,false);
        // Explicit SDK navigation style: do not depend on a map-only default style completing its callback.
        getSharedPreferences("navigation_view_prefs",MODE_PRIVATE).edit().putString("navigation_map_style",
            "https://api.nextbillion.io/tt/style/1/style/22.2.1-9?map=2/basic_street-light").apply();
        try {
            java.io.File file=new java.io.File(getCacheDir(),"navigation-route.json");
            try(java.io.FileInputStream input=new java.io.FileInputStream(file);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int length;
                while((length=input.read(buffer))!=-1)output.write(buffer,0,length);
                route=DirectionsRoute.fromJson(output.toString("UTF-8"));
            }
            navigationView=new NavigationView(this);
            setContentView(navigationView);
            navigationView.onCreate(state);
            startupHandler.postDelayed(()->{
                if(!navigationRunning && !closing){startupError="Navigation did not start. Check network and Maps access for this API key.";close("cancelled");}
            },45000);
            // Wait for the map's initial setup to finish before the navigation SDK replaces its style.
            // Otherwise the map's default style can overwrite the navigation style callback.
            ai.nextbillion.maps.core.MapView mapView=navigationView.findViewById(ai.nextbillion.navigation.ui.R.id.navigationMapView);
            mapView.getMapAsync(map -> map.getStyle(initialStyle -> navigationView.post(() -> {
                if(!closing) navigationView.initialize(this);
            })));
            getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){
                @Override public void handleOnBackPressed(){close(arrived ? "arrived" : "cancelled");}
            });
        } catch(Exception error){close("cancelled");}
    }
    @Override public void onNavigationReady(boolean running) {
        android.util.Log.i("CargixNavigation","Navigation map ready, route legs="+route.legs().size());
        if(!closing) navigationView.startNavigation(NavViewConfig.builder().route(route).shouldSimulateRoute(getIntent().getBooleanExtra("simulate",true)).navigationListener(this).routeListener(this).progressChangeListener(this).shouldShowArriveDialog(true).build());
    }
    private void close(String reason) {
        if(closing)return;closing=true;
        startupHandler.removeCallbacksAndMessages(null);
        Intent result=new Intent().putExtra("reason",reason);
        if(startupError!=null)result.putExtra("error",startupError);
        setResult(Activity.RESULT_OK,result);
        if(navigationView!=null && navigationView.retrieveNextbillionNav()!=null)navigationView.stopNavigation();
        finish();
    }
    @Override public void onCancelNavigation(){close(arrived ? "arrived" : "cancelled");}
    @Override public void onNavigationFinished(){close(arrived ? "arrived" : "cancelled");}
    @Override public void onProgressChange(Location location,NavProgress progress){
        navigationRunning=true;
        // Some SDK paths pause for the arrival sheet before onArrival is dispatched.
        // Record the SDK's own arrival zone, but never close the screen from progress.
        if(progress.currentLegIndex>=progress.route.legs().size()-1
                && progress.distanceRemaining<=navigationView.retrieveNextbillionNav().navEngineConfig().metersRemainingTillArrival())
            arrived=true;
    }
    @Override public void onNavigationRunning(){navigationRunning=true;android.util.Log.i("CargixNavigation","Guidance running");}
    @Override public void onBackPressed(){close(arrived ? "arrived" : "cancelled");}
    @Override public void onArrival(NavProgress progress,int waypointIndex){
        android.util.Log.i("CargixNavigation","Arrived leg="+progress.currentLegIndex+" legs="+progress.route.legs().size());
        if(progress.currentLegIndex>=progress.route.legs().size()-1) arrived=true;
    }
    @Override public boolean allowRerouteFrom(Location location){return true;}
    @Override public void onOffRoute(Point point){}
    @Override public void onRerouteAlong(DirectionsRoute route){}
    @Override public void onFailedReroute(String message){}
    @Override public void onUserInTunnel(boolean value){}
    @Override public boolean shouldShowArriveDialog(NavProgress progress,int index){
        // The SDK may pause simulation for the sheet before dispatching onArrival.
        if(progress.currentLegIndex>=progress.route.legs().size()-1) arrived=true;
        return true;
    }
    @Override public BottomSheetDialog customArriveDialog(NavProgress progress,int index){return null;}
    @Override public void onStart(){super.onStart();if(navigationView!=null)navigationView.onStart();}
    @Override public void onResume(){super.onResume();if(navigationView!=null)navigationView.onResume();}
    @Override public void onPause(){if(navigationView!=null)navigationView.onPause();super.onPause();}
    @Override public void onStop(){if(navigationView!=null)navigationView.onStop();super.onStop();}
    @Override public void onDestroy(){startupHandler.removeCallbacksAndMessages(null);if(navigationView!=null)navigationView.onDestroy();if(isFinishing())new java.io.File(getCacheDir(),"navigation-route.json").delete();super.onDestroy();}
    @Override public void onLowMemory(){super.onLowMemory();if(navigationView!=null)navigationView.onLowMemory();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean(STATE_ARRIVED,arrived);if(navigationView!=null)navigationView.onSaveInstanceState(state);super.onSaveInstanceState(state);}
    @Override protected void onRestoreInstanceState(Bundle state){super.onRestoreInstanceState(state);if(navigationView!=null)navigationView.onRestoreInstanceState(state);}
}
