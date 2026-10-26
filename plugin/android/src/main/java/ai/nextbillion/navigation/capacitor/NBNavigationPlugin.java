package ai.nextbillion.navigation.capacitor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import ai.nextbillion.maps.Nextbillion;
import ai.nextbillion.maps.style.NGLWellKnownTileServer;
import ai.nextbillion.kits.geojson.Point;
import ai.nextbillion.kits.directions.models.*;
import ai.nextbillion.navigation.core.routefetcher.RouteFetcher;
import java.util.*;
import retrofit2.*;

@CapacitorPlugin(name="NativeNavigation", permissions={
    @Permission(alias="location", strings={Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
})
public class NBNavigationPlugin extends Plugin {
    private final List<DirectionsRoute> routes = new ArrayList<>();
    private EmbeddedMap preview;
    private boolean planning = false, navigating = false;
    private ai.nextbillion.maps.storage.FileSource mapFileSource;
    private ai.nextbillion.maps.storage.FileSource.ResourceTransformCallback mapTransform;

    @PluginMethod public void planRoute(PluginCall call) {
        getActivity().runOnUiThread(() -> plan(call));
    }
    private void plan(PluginCall call) {
        if (planning || navigating) { call.reject("Another operation is active."); return; }
        routes.clear();
        try {
            String key = call.getString("apiKey", "").trim();
            JSArray stops = call.getArray("stops");
            if (key.isEmpty() || stops == null || stops.length()<2 || stops.length()>12) throw new IllegalArgumentException("API key and 2–12 stops are required.");
            List<Point> points = new ArrayList<>();
            for(int i=0;i<stops.length();i++) {
                org.json.JSONObject s=stops.getJSONObject(i);
                double lat=s.getDouble("latitude"), lng=s.getDouble("longitude");
                if(Double.isNaN(lat)||Double.isInfinite(lat)||Double.isNaN(lng)||Double.isInfinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180) throw new IllegalArgumentException("Invalid coordinates.");
                points.add(Point.fromLngLat(lng,lat));
            }
            initializeSDK(key);
            // The SDK builder reads the global key/base URL and requires SDK initialization.
            RouteRequestParams.Builder builder=RouteRequestParams.builder().origin(points.get(0)).destination(points.get(points.size()-1));
            RouteConfiguration.apply(call.getData(),builder,points.size());
            if(points.size()>2) builder.waypoints(new ArrayList<>(points.subList(1,points.size()-1)));
            planning=true;
            RouteFetcher.getRoute(builder.build(),new Callback<DirectionsResponse>() {
                @Override public void onResponse(Call<DirectionsResponse> request, Response<DirectionsResponse> response) {
                    getActivity().runOnUiThread(() -> {
                        planning=false;
                        if(!response.isSuccessful()||response.body()==null||response.body().routes()==null||response.body().routes().isEmpty()) {String detail=response.code()==422?"No route matches the selected settings. Adjust stops, avoidances or vehicle restrictions.":response.code()==400?"Check route options and stop coordinates.":"Check key, coverage and stops.";call.reject("Route request failed (HTTP "+response.code()+"). "+detail);return;}
                        routes.addAll(response.body().routes());
                        JSArray summaries=new JSArray();
                        for(int i=0;i<routes.size();i++){DirectionsRoute r=routes.get(i);JSObject item=new JSObject();item.put("id",i);item.put("distance",r.distance());item.put("duration",r.duration());summaries.put(item);}
                        JSObject result=new JSObject();result.put("routes",summaries);call.resolve(result);
                    });
                }
                @Override public void onFailure(Call<DirectionsResponse> request,Throwable error) {getActivity().runOnUiThread(()->{planning=false;call.reject("Route request failed. Check network and API key.");});}
            });
        } catch(Exception error) {planning=false;call.reject(error.getMessage());}
    }
    private void initializeSDK(String key) {
        Nextbillion.getInstance(getContext().getApplicationContext(),key,NGLWellKnownTileServer.NGLTomTom);
        Nextbillion.setAccessKey(key);
        // HTTP style/sprite/tile URLs need explicit authentication in this Maps release.
        // Restrict the in-memory transform to the NextBillion API host.
        mapFileSource=ai.nextbillion.maps.storage.FileSource.getInstance(getContext());
        mapTransform=(kind,url)->{
            android.net.Uri uri=android.net.Uri.parse(url);
            if("https".equals(uri.getScheme()) && "api.nextbillion.io".equals(uri.getHost()) && uri.getQueryParameter("key")==null)
                return uri.buildUpon().appendQueryParameter("key",key).build().toString();
            return url;
        };
        mapFileSource.setResourceTransform(mapTransform);
    }
    @PluginMethod public void createMap(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                if (planning || navigating) throw new IllegalStateException("Wait for planning/navigation before creating a map.");
                String key=call.getString("apiKey", "").trim();
                if (key.isEmpty()) throw new IllegalArgumentException("An API key is required.");
                JSObject bounds=call.getObject("bounds"); EmbeddedMap.validateBounds(bounds);
                initializeSDK(key);
                if (preview!=null) preview.destroy();
                preview=new EmbeddedMap(getActivity(),getBridge().getWebView(),bounds,call,index -> {
                    JSObject event=new JSObject();event.put("routeId",index);notifyListeners("mapRouteSelected",event);
                });
            } catch(Exception error) {call.reject(error.getMessage());}
        });
    }
    @PluginMethod public void updateMap(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try { requireMap(); if(call.getData().has("bounds") && call.getObject("bounds")==null)throw new IllegalArgumentException("Invalid map bounds."); preview.update(call.getObject("bounds"),call.getBoolean("visible"));call.resolve(); }
            catch(Exception error) {call.reject(error.getMessage());}
        });
    }
    @PluginMethod public void setMapStops(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                requireMap(); JSArray stops=call.getArray("stops");
                if(stops==null || (stops.length()!=0 && stops.length()<2) || stops.length()>12) throw new IllegalArgumentException("Use zero or 2–12 stops.");
                List<Point> points=new ArrayList<>();List<String> names=new ArrayList<>();
                for(int i=0;i<stops.length();i++) {
                    org.json.JSONObject stop=stops.getJSONObject(i); double lat=stop.getDouble("latitude"),lng=stop.getDouble("longitude");
                    if(Double.isNaN(lat)||Double.isInfinite(lat)||Double.isNaN(lng)||Double.isInfinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180) throw new IllegalArgumentException("Invalid coordinates.");
                    points.add(Point.fromLngLat(lng,lat));names.add(stop.optString("name","Stop"));
                }
                preview.stops(points,names);call.resolve();
            } catch(Exception error) {call.reject(error.getMessage());}
        });
    }
    @PluginMethod public void showMapRoute(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                requireMap();int index=call.getInt("routeId",-1);
                if(index<0 || index>=routes.size()) throw new IllegalArgumentException("Generate routes before showing a route.");
                preview.route(routes,index,call.getBoolean("fit",true));call.resolve();
            } catch(Exception error) {call.reject(error.getMessage());}
        });
    }
    @PluginMethod public void destroyMap(PluginCall call) {
        getActivity().runOnUiThread(() -> {if(preview!=null){preview.destroy();preview=null;}call.resolve();});
    }
    private void requireMap() {if(preview==null)throw new IllegalStateException("Create the map first.");}
    @Override protected void handleOnStart(){if(preview!=null)preview.start();}
    @Override protected void handleOnResume(){if(preview!=null)preview.resume();}
    @Override protected void handleOnPause(){if(preview!=null)preview.pause();}
    @Override protected void handleOnStop(){if(preview!=null)preview.stop();}
    @Override protected void handleOnDestroy(){if(preview!=null){preview.destroy();preview=null;}}
    @PluginMethod public void startNavigation(PluginCall call) {
        getActivity().runOnUiThread(()->{
            if(planning||navigating){call.reject("Another operation is active.");return;}
            int index=call.getInt("routeId",-1);
            if(index<0||index>=routes.size()){call.reject("Generate routes before starting navigation.");return;}
            navigating=true;
            if(getPermissionState("location")!=PermissionState.GRANTED) requestPermissionForAlias("location",call,"locationResult");
            else launch(call);
        });
    }
    @PermissionCallback private void locationResult(PluginCall call) {
        if(getPermissionState("location")!=PermissionState.GRANTED){navigating=false;call.reject("Location permission is required. Enable precise location in Settings.");return;}launch(call);
    }
    private void launch(PluginCall call) {
        try {
            // A file avoids Android's Binder limit for long route geometries. No route or key in Intent extras.
            java.io.File routeFile=new java.io.File(getContext().getCacheDir(),"navigation-route.json");
            try(java.io.FileOutputStream stream=new java.io.FileOutputStream(routeFile)) {stream.write(routes.get(call.getInt("routeId",0)).toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            Intent intent=new Intent(getActivity(),NBNavigationActivity.class);
            intent.putExtra("simulate",call.getBoolean("simulate",true));
            startActivityForResult(call,intent,"navigationResult");
        } catch(Exception error){navigating=false;call.reject("Unable to launch native navigation.");}
    }
    @ActivityCallback private void navigationResult(PluginCall call,ActivityResult result) {
        navigating=false;
        if(call==null)return;
        if(result.getData()!=null && result.getData().hasExtra("error")){call.reject(result.getData().getStringExtra("error"));return;}
        String reason=result.getData()==null?"cancelled":result.getData().getStringExtra("reason");
        JSObject response=new JSObject();response.put("reason","arrived".equals(reason)?"arrived":"cancelled");call.resolve(response);
    }
}
