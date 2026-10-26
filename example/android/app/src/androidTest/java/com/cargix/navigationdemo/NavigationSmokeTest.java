package com.cargix.navigationdemo;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Opt-in real SDK smoke test. Supply -e apiKey KEY; no credentials in source. */
@RunWith(AndroidJUnit4.class)
public class NavigationSmokeTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private boolean isGuidanceRunning(android.app.Activity screen) throws Exception {
        return (Boolean)screen.getClass().getMethod("isGuidanceRunning").invoke(screen);
    }
    private String js(String code) throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] result={null};
        instrumentation.runOnMainSync(()->activity.getBridge().getWebView().evaluateJavascript(code,value->{result[0]=value;done.countDown();}));
        assertTrue("WebView JS timed out",done.await(10,TimeUnit.SECONDS));return result[0];
    }
    private String waitFor(String code,java.util.function.Predicate<String> predicate,int seconds) throws Exception {
        long until=SystemClock.elapsedRealtime()+seconds*1000L;String result="";
        while(SystemClock.elapsedRealtime()<until){result=js(code);if(predicate.test(result))return result;SystemClock.sleep(500);}
        fail("Timed out. Last JS result: "+result);return result;
    }
    private void openSettings() throws Exception {
        js("document.getElementById('settings-open').click();true");
        waitFor("document.getElementById('settings-modal').hidden",value->"false".equals(value),10);
    }
    private void assertPreviewVisibility(int visibility) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+5000;boolean[] visible={false};
        while(SystemClock.elapsedRealtime()<deadline){
            instrumentation.runOnMainSync(()->{android.view.View holder=activity.findViewById(android.R.id.content).findViewWithTag("nb-native-map-container");visible[0]=holder!=null && holder.getVisibility()==visibility;});
            if(visible[0])return;SystemClock.sleep(100);
        }
        fail("Native preview visibility did not become "+visibility);
    }
    private Object previewField(String field) throws Exception {
        Object plugin=activity.getBridge().getPlugin("NativeNavigation").getInstance();
        java.lang.reflect.Field f=plugin.getClass().getDeclaredField("preview");f.setAccessible(true);Object preview=f.get(plugin);
        assertNotNull("Native preview was not created",preview);
        f=preview.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(preview);
    }
    private void assertPreviewBounds() throws Exception {
        String raw=js("JSON.stringify({width:document.getElementById('map-stage').getBoundingClientRect().width,height:document.getElementById('map-stage').getBoundingClientRect().height})");
        org.json.JSONObject bounds=new org.json.JSONObject(new org.json.JSONTokener(raw).nextValue().toString());
        double scale=activity.getResources().getDisplayMetrics().density;
        android.view.View[] holder={null};instrumentation.runOnMainSync(()->holder[0]=activity.findViewById(android.R.id.content).findViewWithTag("nb-native-map-container"));
        assertNotNull(holder[0]);assertEquals(bounds.getDouble("width")*scale,holder[0].getWidth(),3);assertEquals(bounds.getDouble("height")*scale,holder[0].getHeight(),3);
    }
    private String camera() throws Exception {
        Object map=previewField("map");String[] value={null};
        instrumentation.runOnMainSync(()->{try{value[0]=map.getClass().getMethod("getCameraPosition").invoke(map).toString();}catch(Exception error){throw new AssertionError(error);}});
        return value[0];
    }
    private void panPreview() throws Exception {
        String before=camera();int[] location=new int[2];android.view.View[] holder={null};
        instrumentation.runOnMainSync(()->{holder[0]=activity.findViewById(android.R.id.content).findViewWithTag("nb-native-map-container");holder[0].getLocationOnScreen(location);});
        float x=location[0]+holder[0].getWidth()*0.6f,y=location[1]+holder[0].getHeight()*0.55f;
        long down=SystemClock.uptimeMillis();
        instrumentation.sendPointerSync(android.view.MotionEvent.obtain(down,down,android.view.MotionEvent.ACTION_DOWN,x,y,0));
        for(int i=1;i<=10;i++){SystemClock.sleep(25);instrumentation.sendPointerSync(android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_MOVE,x-i*12,y,0));}
        instrumentation.sendPointerSync(android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_UP,x-120,y,0));
        SystemClock.sleep(500);assertNotEquals("Dragging the native map did not move the camera",before,camera());
    }
    private void assertRouteFitsPreview() throws Exception {
        Object map=previewField("map");
        android.view.View view=(android.view.View)previewField("mapView");
        java.util.List<?> routes=(java.util.List<?>)previewField("routes");int selected=(Integer)previewField("selected");
        Class<?> routeClass=Class.forName("ai.nextbillion.kits.directions.models.DirectionsRoute");
        Class<?> lineClass=Class.forName("ai.nextbillion.kits.geojson.LineString");
        java.util.List<Object> points=new java.util.ArrayList<>();
        for(Object route:routes){String geometry=(String)routeClass.getMethod("geometry").invoke(route);Object line=lineClass.getMethod("fromPolyline",String.class,int.class).invoke(null,geometry,6);points.addAll((java.util.List<?>)lineClass.getMethod("coordinates").invoke(line));}
        for(Object route:routes){
            java.util.List<?> legs=(java.util.List<?>)routeClass.getMethod("legs").invoke(route);
            Class<?> legClass=Class.forName("ai.nextbillion.kits.directions.models.RouteLeg"),stepClass=Class.forName("ai.nextbillion.kits.directions.models.LegStep");
            for(Object leg:legs)for(Object step:(java.util.List<?>)legClass.getMethod("steps").invoke(leg)){
                String geometry=(String)stepClass.getMethod("geometry").invoke(step);if(geometry==null)continue;
                Object line=lineClass.getMethod("fromPolyline",String.class,int.class).invoke(null,geometry,6);points.addAll((java.util.List<?>)lineClass.getMethod("coordinates").invoke(line));
            }
        }
        Class<?> pointClass=Class.forName("ai.nextbillion.kits.geojson.Point"),latLngClass=Class.forName("ai.nextbillion.maps.geometry.LatLng");
        double[] extent={Double.MAX_VALUE,Double.MAX_VALUE,-Double.MAX_VALUE,-Double.MAX_VALUE};
        instrumentation.runOnMainSync(()->{try{
            Object projection=map.getClass().getMethod("getProjection").invoke(map);
            for(Object point:points){
                Object latLng=latLngClass.getConstructor(double.class,double.class).newInstance(pointClass.getMethod("latitude").invoke(point),pointClass.getMethod("longitude").invoke(point));
                android.graphics.PointF pixel=(android.graphics.PointF)projection.getClass().getMethod("toScreenLocation",latLngClass).invoke(projection,latLng);
                extent[0]=Math.min(extent[0],pixel.x);extent[1]=Math.min(extent[1],pixel.y);extent[2]=Math.max(extent[2],pixel.x);extent[3]=Math.max(extent[3],pixel.y);
            }
        }catch(Exception error){throw new AssertionError(error);}});
        String info="Geometry bounds "+java.util.Arrays.toString(extent)+" in "+view.getWidth()+"x"+view.getHeight()+"; camera="+camera();
        assertTrue(info,extent[0]>=-5 && extent[1]>=-5 && extent[2]<=view.getWidth()+5 && extent[3]<=view.getHeight()+5);
    }
    private void capture(String name) throws Exception {
        if(!"true".equals(InstrumentationRegistry.getArguments().getString("capturePreview")))return;
        instrumentation.waitForIdleSync();SystemClock.sleep(1000);
        android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(activity.getCacheDir(),name))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}screenshot.recycle();
    }
    private android.view.accessibility.AccessibilityNodeInfo arrivalDone() {
        android.view.accessibility.AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();
        if(root==null)return null;
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> matches=root.findAccessibilityNodeInfosByViewId("com.cargix.navigationdemo:id/nav_dialog_done_button");
        for(android.view.accessibility.AccessibilityNodeInfo node:matches)if(node.isVisibleToUser())return node;
        return null;
    }
    @Test public void planLaunchAndReturn() throws Exception { runTrip(false); }
    @Test public void truckPlanLaunchAndReturn() throws Exception { runTrip(true); }
    private void runTrip(boolean truck) throws Exception {
        String key=InstrumentationRegistry.getArguments().getString("apiKey");
        org.junit.Assume.assumeTrue("Supply -e apiKey KEY",key!=null&&!key.isEmpty());
        instrumentation.getUiAutomation().grantRuntimePermission("com.cargix.navigationdemo",Manifest.permission.ACCESS_FINE_LOCATION);
        instrumentation.getUiAutomation().grantRuntimePermission("com.cargix.navigationdemo",Manifest.permission.ACCESS_COARSE_LOCATION);
        Intent intent=new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity=(MainActivity)instrumentation.startActivitySync(intent);
        waitFor("document.querySelectorAll('#stops .stop').length",s->"2".equals(s),30);
        openSettings();
        js("document.getElementById('key').value="+org.json.JSONObject.quote(key)+";true");
        if (truck) {
            js("document.querySelector('[data-profile=truck]').click();document.querySelector('[data-tab=truck]').click();document.querySelector('#avoid-options input[value=toll]').click();document.querySelector('#avoid-options input[value=ferry]').click();document.getElementById('truck-type').value='rigid_truck';document.getElementById('truck-type').dispatchEvent(new Event('change',{bubbles:true}));document.querySelector('#hazmat-options input[value=general]').click();true");
            assertEquals("false",js("document.getElementById('truck-options').hidden"));
        }
        js("document.getElementById('settings-save').click();document.getElementById('preset').value='San Francisco · Short trip';document.getElementById('preset').dispatchEvent(new Event('change'));true");
        waitFor("document.getElementById('map-status').textContent",value->value.contains("NATIVE MAP")||value.contains("RETRY"),45);
        assertTrue("Native map did not load: "+js("document.getElementById('map-message').textContent"),js("document.getElementById('map-status').textContent").contains("NATIVE MAP"));
        assertEquals(Boolean.TRUE,previewField("ready"));assertPreviewVisibility(android.view.View.VISIBLE);
        js("document.getElementById('plan').click();true");
        String result=waitFor("document.getElementById('status').textContent",s->s.contains("Route ready")||s.contains("failed")||s.contains("timed out"),60);
        assertTrue("Actual SDK routing failed: "+result,result.contains("Route ready"));
        assertEquals("3",js("document.querySelectorAll('#stops .stop').length"));
        assertEquals("false",js("document.getElementById('start').disabled"));
        if (truck) assertTrue(js("document.getElementById('route-settings').textContent").contains("350 × 250 × 1000 cm"));
        assertTrue("Map has no native route geometry",((java.util.List<?>)previewField("routes")).size()>0);
        if(!truck) {
            assertPreviewBounds();panPreview();
            instrumentation.runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            waitFor("innerWidth>innerHeight",value->"true".equals(value),15);SystemClock.sleep(500);assertPreviewBounds();
            instrumentation.runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            waitFor("innerHeight>innerWidth",value->"true".equals(value),15);SystemClock.sleep(500);assertPreviewBounds();
            js("document.getElementById('fit-map').click();true");SystemClock.sleep(500);
        }
        SystemClock.sleep(500);assertRouteFitsPreview();
        capture("embedded-map-preview.png");
        // Draft edits must not invalidate an applied plan; modal must receive touches above the native map.
        openSettings();assertPreviewVisibility(android.view.View.INVISIBLE);
        if(truck){js("document.querySelector('[data-tab=truck]').click();true");capture("embedded-map-settings.png");}
        js("document.querySelector('#avoid-options input[value=highway]').click();document.getElementById('settings-cancel').click();true");
        assertEquals("false",js("document.getElementById('start').disabled"));assertPreviewVisibility(android.view.View.VISIBLE);
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor("ai.nextbillion.navigation.capacitor.NBNavigationActivity",null,false);
        js("document.getElementById('start').click();true");
        android.app.Activity nativeScreen=monitor.waitForActivityWithTimeout(30000);
        assertNotNull("Native navigation Activity did not open",nativeScreen);
        instrumentation.removeMonitor(monitor);
        long deadline=SystemClock.elapsedRealtime()+45000;
        while(!isGuidanceRunning(nativeScreen) && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(500);
        assertTrue("SDK guidance never entered running state",isGuidanceRunning(nativeScreen));
        assertFalse("Native screen closed before navigation started",nativeScreen.isFinishing());
        // The native route JSON passed to the Activity must retain its planning options.
        if (truck) {
            java.io.File route = new java.io.File(instrumentation.getTargetContext().getCacheDir(),"navigation-route.json");
            String json;
            try(java.io.FileInputStream input=new java.io.FileInputStream(route);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[8192];int length;while((length=input.read(buffer))!=-1)output.write(buffer,0,length);json=output.toString("UTF-8");}
            org.json.JSONObject options = new org.json.JSONObject(json).getJSONObject("routeOptions");
            assertEquals("truck",options.getString("mode"));
            assertEquals("flexible",options.getString("option"));
            assertTrue(options.toString(),options.toString().contains("20000"));
            assertTrue(options.toString(),options.toString().contains("8.5"));
        }
        instrumentation.runOnMainSync(()->nativeScreen.onBackPressed());
        result=waitFor("document.getElementById('status').textContent",s->s.contains("back in nb.ai"),20);
        assertTrue(result,result.contains("Navigation closed"));
        if (truck) {
            openSettings();
            js("document.querySelector('[data-profile=car]').click();document.getElementById('settings-save').click();true");
            assertEquals("true",js("document.getElementById('start').disabled"));
            assertEquals("0",js("document.querySelectorAll('#routes input').length"));
            js("document.getElementById('plan').click();true");
            result=waitFor("document.getElementById('status').textContent",s->s.contains("Route ready")||s.contains("failed"),60);
            assertTrue("Replanning as car failed: "+result,result.contains("Route ready"));
            assertTrue(js("document.getElementById('route-settings').textContent").contains("Car"));
        }
        if(!truck && "true".equals(InstrumentationRegistry.getArguments().getString("verifyArrival"))) {
            // Arrival must keep the native sheet and bridge promise open until the user taps Done.
            android.accessibilityservice.AccessibilityServiceInfo info=instrumentation.getUiAutomation().getServiceInfo();
            info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            instrumentation.getUiAutomation().setServiceInfo(info);
            js("(() => {const original=window.Capacitor.nativePromise;window.Capacitor.nativePromise=function(plugin,method,options){const promise=original.apply(this,arguments);if(plugin==='NativeNavigation'&&method==='startNavigation'){window.__arrivalSettled=false;return promise.finally(()=>{window.__arrivalSettled=true;});}return promise;};return true;})()");
            js("document.querySelectorAll('#stops .remove')[2].click();document.getElementById('plan').click();true");
            result=waitFor("document.getElementById('status').textContent",s->s.contains("Route ready")||s.contains("failed"),60);
            assertTrue(result,result.contains("Route ready"));
            Instrumentation.ActivityMonitor arrivalMonitor=instrumentation.addMonitor("ai.nextbillion.navigation.capacitor.NBNavigationActivity",null,false);
            js("document.getElementById('start').click();true");
            android.app.Activity arrivalScreen=arrivalMonitor.waitForActivityWithTimeout(30000);instrumentation.removeMonitor(arrivalMonitor);
            assertNotNull("Native arrival test Activity missing",arrivalScreen);
            deadline=SystemClock.elapsedRealtime()+180000;
            while(arrivalDone()==null&&!arrivalScreen.isFinishing()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(250);
            assertFalse("Arrival closed navigation automatically",arrivalScreen.isFinishing());
            assertNotNull("Native arrival sheet missing",arrivalDone());
            SystemClock.sleep(5000);
            assertNotNull("Arrival sheet disappeared without user confirmation",arrivalDone());
            assertEquals("Bridge resolved before user confirmation","false",js("window.__arrivalSettled"));
            capture("arrival-awaiting-user.png");
            assertTrue("Cannot tap native Done",arrivalDone().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
            result=waitFor("document.getElementById('status').textContent",s->s.contains("Trip completed")||s.contains("Navigation closed"),30);
            assertTrue("Confirmed final arrival did not return arrived: "+result,result.contains("Trip completed"));
            assertPreviewVisibility(android.view.View.VISIBLE);
            assertTrue("Preview route lost after confirmed arrival",((java.util.List<?>)previewField("routes")).size()>0);
            capture("arrival-user-confirmed.png");
        }
    }
}
