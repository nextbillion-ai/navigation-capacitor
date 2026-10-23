package ai.nextbillion.navigation.capacitor;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.webkit.WebView;
import android.widget.FrameLayout;
import ai.nextbillion.maps.core.*;
import ai.nextbillion.maps.annotations.*;
import ai.nextbillion.maps.camera.CameraUpdateFactory;
import ai.nextbillion.maps.geometry.*;
import ai.nextbillion.kits.directions.models.DirectionsRoute;
import ai.nextbillion.kits.geojson.LineString;
import ai.nextbillion.kits.geojson.Point;
import ai.nextbillion.navigation.ui.route.NavigationMapRoute;
import com.getcapacitor.*;
import java.util.*;

/** Native sibling overlay, clipped to the host WebView. All methods run on the UI thread. */
final class EmbeddedMap {
    interface SelectionListener { void selected(int index); }
    static final String STYLE = "https://api.nextbillion.io/tt/style/1/style/22.2.1-9?map=2/basic_street-light";
    private final WebView webView;
    private final FrameLayout root, holder;
    private final MapView mapView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PluginCall pending;
    private NextbillionMap map;
    private NavigationMapRoute lines;
    private final List<Marker> markers = new ArrayList<>();
    private List<DirectionsRoute> routes = new ArrayList<>();
    private int selected = -1;
    private boolean ready, destroyed, started, resumed, visible = true;

    EmbeddedMap(Activity activity, WebView webView, JSObject bounds, PluginCall call, SelectionListener onSelection) {
        validateBounds(bounds);
        this.webView = webView;
        pending = call;
        root = activity.findViewById(android.R.id.content);
        holder = new FrameLayout(activity);
        holder.setTag("nb-native-map-container");
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xffe8eee7); background.setCornerRadius(14 * activity.getResources().getDisplayMetrics().density);
        holder.setBackground(background); holder.setClipToOutline(true);
        mapView = new MapView(activity);
        mapView.setTag("nb-native-preview-map");
        holder.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(holder);
        update(bounds, true);
        mapView.onCreate(null); start(); resume();
        mapView.addOnDidFailLoadingMapListener(error -> fail("Map failed to load. Check network and Maps access."));
        handler.postDelayed(() -> fail("Map timed out. Check network and Maps access."), 30000);
        mapView.getMapAsync(value -> {
            if (destroyed) return;
            map = value;
            // Let the SDK finish its initial style before replacing it, just as in the guidance screen.
            map.getStyle(initial -> mapView.post(() -> {
                if (destroyed) return;
                map.setStyle(STYLE, style -> {
                    if (destroyed) return;
                    try {
                    lines = new NavigationMapRoute(mapView, map);
                    lines.onStart();
                    lines.showAlternativeRoutes(true);
                    lines.updateRouteDurationVisibilityTo(false);
                    lines.setOnRouteSelectionChangeListener(route -> {
                        int index = routes.indexOf(route);
                        if (index >= 0 && index != selected) { selected = index; onSelection.selected(index); }
                    });
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(1.29, 103.85), 11));
                    ready = true;
                    handler.removeCallbacksAndMessages(null);
                    if (pending != null) { pending.resolve(); pending = null; }
                    } catch(Exception error) { fail("Unable to initialize native route preview."); }
                });
            }));
        });
    }
    private void fail(String message) {
        if (pending != null) { pending.reject(message); pending = null; destroy(); }
    }
    static void validateBounds(JSObject bounds) {
        if (bounds == null) throw new IllegalArgumentException("Map bounds are required.");
        for (String key : Arrays.asList("x", "y", "width", "height")) {
            Object raw = bounds.opt(key);
            if (!(raw instanceof Number)) throw new IllegalArgumentException("Invalid map bounds: " + key);
            double value = ((Number) raw).doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > 100000 || ((key.equals("width") || key.equals("height")) && value <= 0))
                throw new IllegalArgumentException("Invalid map bounds: " + key);
        }
    }
    void update(JSObject bounds, Boolean show) {
        if (show != null) visible = show;
        if (bounds != null) {
            validateBounds(bounds);
            float scale = webView.getResources().getDisplayMetrics().density;
            int[] web = new int[2], parent = new int[2]; webView.getLocationInWindow(web); root.getLocationInWindow(parent);
            int x = (int)Math.round(bounds.optDouble("x") * scale), y = (int)Math.round(bounds.optDouble("y") * scale);
            int width = Math.max(1, (int)Math.round(bounds.optDouble("width") * scale));
            int height = Math.max(1, (int)Math.round(bounds.optDouble("height") * scale));
            FrameLayout.LayoutParams frame = new FrameLayout.LayoutParams(width, height);
            frame.leftMargin = web[0] - parent[0] + x; frame.topMargin = web[1] - parent[1] + y;
            holder.setLayoutParams(frame);
            holder.setClipBounds(new Rect(Math.max(0,-x), Math.max(0,-y), Math.max(0,Math.min(width,webView.getWidth()-x)), Math.max(0,Math.min(height,webView.getHeight()-y))));
        }
        holder.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }
    void stops(List<Point> points, List<String> names) {
        requireReady(); lines.removeRoute(); routes.clear(); selected = -1;
        for (Marker marker : markers) map.removeMarker(marker); markers.clear();
        for (int i=0;i<points.size();i++) {
            Point p=points.get(i);
            markers.add(map.addMarker(new MarkerOptions().position(new LatLng(p.latitude(),p.longitude())).title((i+1)+". "+names.get(i))));
        }
        fit(points);
    }
    void route(List<DirectionsRoute> values, int index, boolean fit) {
        requireReady(); routes = new ArrayList<>(values); selected = index;
        for (Marker marker : markers) map.removeMarker(marker); markers.clear();
        lines.addRoutes(routes,index);
        if (fit) {
            List<Point> geometry=new ArrayList<>();
            for(DirectionsRoute route:routes) if(route.geometry()!=null) geometry.addAll(LineString.fromPolyline(route.geometry(),6).coordinates());
            fit(geometry);
        }
    }
    private void fit(List<Point> points) {
        if (points.isEmpty()) return;
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        Set<String> distinct = new HashSet<>();
        for (Point p:points) { bounds.include(new LatLng(p.latitude(),p.longitude())); distinct.add(p.latitude()+","+p.longitude()); }
        if (distinct.size()<2) map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(points.get(0).latitude(),points.get(0).longitude()),14));
        else map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),(int)(32*webView.getResources().getDisplayMetrics().density)));
    }
    private void requireReady() { if (!ready || destroyed) throw new IllegalStateException("Create the map and wait for it to load first."); }
    boolean isReady() { return ready && !destroyed; }
    void start() { if (!destroyed && !started) { mapView.onStart(); if(lines!=null)lines.onStart(); started=true; } }
    void resume() { if (!destroyed && !resumed) { mapView.onResume(); resumed=true; } }
    void pause() { if (!destroyed && resumed) { mapView.onPause(); resumed=false; } }
    void stop() { if (!destroyed && started) { if(lines!=null)lines.onStop(); mapView.onStop(); started=false; } }
    void destroy() {
        if (destroyed) return;
        handler.removeCallbacksAndMessages(null);
        if (pending!=null) { pending.reject("Map creation was cancelled."); pending=null; }
        pause(); stop();
        if (lines!=null) lines.onNavigationViewDestroy();
        mapView.onDestroy(); destroyed=true; ready=false; root.removeView(holder);
    }
}
