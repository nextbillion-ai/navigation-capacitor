package com.cargix.navigationdemo;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Opt-in live E2E. Credentials are supplied at runtime; requests are never mocked. */
@RunWith(AndroidJUnit4.class)
public class RoutePreferencesE2ETest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final JSONArray results = new JSONArray();
    private final List<String> failures = new ArrayList<>();
    private MainActivity activity;
    private Activity guidance;
    private String key;
    private File output;
    private final String[] fields = {"origin","destination","waypoints","mode","geometry","overview","alternatives","altcount","route_type","departure_time","cross_border","turn_angle_range","approaches","lang","unit","avoid","truck_size","truck_weight","truck_axle_load","hazmat_type","prefer","truck_type"};

    private String js(String code) throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] value={null};
        instrumentation.runOnMainSync(()->activity.getBridge().getWebView().evaluateJavascript(code,v->{value[0]=v;done.countDown();}));
        assertTrue("WebView timed out",done.await(10,TimeUnit.SECONDS));return value[0];
    }
    private String text(String code) throws Exception { return String.valueOf(new JSONTokener(js(code)).nextValue()); }
    private void waitTrue(String code,int seconds) throws Exception {
        long end=SystemClock.elapsedRealtime()+seconds*1000L;
        while(SystemClock.elapsedRealtime()<end){if("true".equals(js(code)))return;SystemClock.sleep(100);}
        fail("Timed out: "+code+"; "+text("document.getElementById('status').textContent"));
    }
    private Object field(Object object,String name) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private Object plugin(){return activity.getBridge().getPlugin("NativeNavigation").getInstance();}
    private Object route() throws Exception{return ((List<?>)field(plugin(),"routes")).get(0);}
    private JSONObject routeJson(Object route) throws Exception{return new JSONObject((String)Class.forName("ai.nextbillion.kits.directions.models.DirectionsRoute").getMethod("toJson").invoke(route));}
    private JSONObject wire() throws Exception {
        String raw=activity.getSharedPreferences("NBNavigationRequest",0).getString("last_request_info",null);
        assertNotNull("Missing actual SDK request metadata",raw);JSONObject body=new JSONObject(raw),safe=new JSONObject();
        for(String field:fields)if(body.has(field)&&!body.isNull(field))safe.put(field,body.get(field));return safe;
    }
    private void preview() throws Exception {
        Object preview=field(plugin(),"preview");assertNotNull(preview);assertEquals(Boolean.TRUE,field(preview,"ready"));
        View holder=(View)field(preview,"holder");
        // The page updates its status before the asynchronous updateMap bridge call finishes.
        long deadline=SystemClock.elapsedRealtime()+5000;
        int[] visibility={View.INVISIBLE};
        do {
            instrumentation.runOnMainSync(()->visibility[0]=holder.getVisibility());
            if(visibility[0]==View.VISIBLE)break;
            SystemClock.sleep(50);
        } while(SystemClock.elapsedRealtime()<deadline);
        assertEquals("Native preview did not recover within five seconds",View.VISIBLE,visibility[0]);
        assertTrue(holder.getWidth()>0&&holder.getHeight()>0);
        assertFalse("Preview lost route geometry",((List<?>)field(preview,"routes")).isEmpty());
    }
    private void open() throws Exception {js("document.getElementById('settings-open').click();true");waitTrue("!document.getElementById('settings-modal').hidden",10);}
    private void trip(boolean border) throws Exception {
        js("document.getElementById('preset').value='San Francisco · Short trip';document.getElementById('preset').dispatchEvent(new Event('change'));true");
        if(border)js("(() => {while(document.querySelectorAll('#stops .stop').length>2)document.querySelector('#stops .remove').click();const points=[['Seattle',47.6062,-122.3321],['Vancouver',49.2827,-123.1207]];document.querySelectorAll('#stops .stop').forEach((row,i)=>row.querySelectorAll('input').forEach((input,j)=>{input.value=points[i][j];input.dispatchEvent(new Event('input',{bubbles:true}));}));return true;})()");
    }
    private void configure(JSONObject options) throws Exception {
        open();
        js("(() => {const o="+options+";const e=id=>document.getElementById(id);const set=(id,v)=>{e(id).value=v;e(id).dispatchEvent(new Event('change',{bubbles:true}));};e('route-defaults').click();e('avoid-clear').click();e('truck-clear').click();document.querySelector('[data-profile='+ (o.profile||'car') +']').click();document.querySelectorAll('#avoid-options input').forEach(i=>{i.checked=(o.avoid||[]).includes(i.value);i.dispatchEvent(new Event('change',{bubbles:true}));});if(o.departureTime!==undefined){const d=new Date(o.departureTime*1000);const p=x=>String(x).padStart(2,'0');set('route-departure-mode','custom');set('route-departure-time',d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate())+'T'+p(d.getHours())+':'+p(d.getMinutes())+':'+p(d.getSeconds()));}if(o.crossBorder!==undefined)set('route-cross-border',String(o.crossBorder));if(o.language)set('route-language',o.language);if(o.unit)set('route-unit',o.unit);if(o.turnAngleRange!==undefined)set('route-turn-angle',String(o.turnAngleRange));if(o.approaches){set('route-approaches-mode','custom');e('route-approaches').querySelectorAll('select').forEach((s,i)=>s.value=o.approaches[i]);}if(o.truck){const t=o.truck,s=t.size||{};for(const [id,v] of Object.entries({'truck-height':s.heightCm,'truck-width':s.widthCm,'truck-length':s.lengthCm,'truck-weight':t.weightKg,'truck-axle':t.axleLoadTonnes}))if(v!==undefined)set(id,String(v));e('truck-prefer').checked=!!t.preferTruckRoutes;if(t.types&&t.types.length)set('truck-type',t.types[0]);document.querySelectorAll('#hazmat-options input').forEach(i=>i.checked=(t.hazmatTypes||[]).includes(i.value));}e('simulate').checked=true;e('settings-save').click();return true;})()");
        waitTrue("document.getElementById('settings-modal').hidden",10);
        assertEquals("true",js("document.getElementById('start').disabled"));
    }
    private String join(JSONArray values,String separator)throws Exception{List<String> out=new ArrayList<>();for(int i=0;i<values.length();i++)out.add(values.getString(i));return String.join(separator,out);}
    private void check(JSONObject o,JSONObject body,JSONObject trace,long started)throws Exception{
        assertEquals(o.optString("profile","car"),body.getString("mode"));assertEquals("fastest",body.getString("route_type"));
        assertEquals(2,body.getInt("altcount"));assertTrue(body.getBoolean("alternatives"));
        for(String name:Arrays.asList("departureTime","language","unit","crossBorder","turnAngleRange","approaches"))
            assertEquals("Unexpected bridge default: "+name,o.has(name),trace.has(name));
        long departure=body.getLong("departure_time");
        if(o.has("departureTime"))assertEquals(o.getLong("departureTime"),departure);
        else assertTrue("SDK current time is stale",departure>=started&&departure<=System.currentTimeMillis()/1000+2);
        String language=o.optString("language","en");if("zh".equals(language))language="zh-Hans";
        assertEquals(language,body.getString("lang"));assertEquals(o.optString("unit","metric"),body.getString("unit"));
        assertEquals(o.optBoolean("crossBorder",false),body.getBoolean("cross_border"));
        if(o.has("turnAngleRange"))assertEquals(o.getInt("turnAngleRange"),body.getInt("turn_angle_range"));else assertFalse("Stale angle",body.has("turn_angle_range"));
        if(o.has("approaches"))assertEquals(join(o.getJSONArray("approaches"),";"),body.getString("approaches"));else assertFalse("Stale approaches",body.has("approaches"));
        if(o.has("avoid")&&o.getJSONArray("avoid").length()>0)assertEquals(join(o.getJSONArray("avoid"),"|"),body.getString("avoid"));else assertFalse("Stale avoids",body.has("avoid"));
        JSONObject params=routeJson(route()).getJSONObject("routeOptions");assertEquals("flexible",params.getString("option"));
        assertEquals(departure,params.getLong("departureTime"));assertEquals(language,params.getString("language"));assertEquals(body.getString("unit"),params.getString("unit"));
        JSONObject truck=o.optJSONObject("truck");
        if(truck!=null){
            if(truck.has("size")){JSONObject s=truck.getJSONObject("size");assertEquals(s.getInt("heightCm")+","+s.getInt("widthCm")+","+s.getInt("lengthCm"),body.getString("truck_size"));}
            if(truck.has("weightKg"))assertEquals(truck.getInt("weightKg"),body.getInt("truck_weight"));
            if(truck.has("axleLoadTonnes"))assertEquals(truck.getDouble("axleLoadTonnes"),body.getDouble("truck_axle_load"),0.00001);
            if(truck.has("hazmatTypes"))assertEquals(join(truck.getJSONArray("hazmatTypes"),"|"),body.getString("hazmat_type"));
            if(truck.optBoolean("preferTruckRoutes"))assertEquals("truck_route",body.getString("prefer"));
            if(truck.has("types"))assertEquals(join(truck.getJSONArray("types"),"|"),body.getString("truck_type"));
        }else for(String f:Arrays.asList("truck_size","truck_weight","truck_axle_load","hazmat_type","prefer","truck_type"))assertFalse("Stale "+f,body.has(f));
    }
    private JSONArray instructions(JSONObject route)throws Exception{
        JSONArray out=new JSONArray(),legs=route.getJSONArray("legs");
        for(int i=0;i<legs.length();i++){JSONArray steps=legs.getJSONObject(i).getJSONArray("steps");
            for(int j=0;j<steps.length()&&out.length()<4;j++){JSONObject m=steps.getJSONObject(j).optJSONObject("maneuver");if(m!=null&&!m.optString("instruction").isEmpty())out.put(m.getString("instruction"));}}
        assertTrue("No turn instructions",out.length()>0);return out;
    }
    private void capture(String name)throws Exception{
        instrumentation.waitForIdleSync();SystemClock.sleep(200);android.graphics.Bitmap image=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(image);
        try(FileOutputStream stream=new FileOutputStream(new File(output,name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}image.recycle();
    }
    private void visibleText(View view,JSONArray out){
        if(view.getVisibility()!=View.VISIBLE)return;if(view instanceof TextView){String t=((TextView)view).getText().toString();if(!t.isEmpty())out.put(t);}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)visibleText(((ViewGroup)view).getChildAt(i),out);
    }
    private JSONObject navigate(String name,JSONObject expected)throws Exception{
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor("ai.nextbillion.navigation.capacitor.NBNavigationActivity",null,false);
        js("document.getElementById('start').click();true");guidance=monitor.waitForActivityWithTimeout(30000);instrumentation.removeMonitor(monitor);assertNotNull("Guidance Activity missing",guidance);
        long end=SystemClock.elapsedRealtime()+45000;
        while(!(Boolean)guidance.getClass().getMethod("isGuidanceRunning").invoke(guidance)&&!guidance.isFinishing()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(200);
        assertFalse("Guidance closed during startup",guidance.isFinishing());assertTrue("Guidance not running",(Boolean)guidance.getClass().getMethod("isGuidanceRunning").invoke(guidance));
        Object navView=field(guidance,"navigationView"),navigator=navView.getClass().getMethod("retrieveNextbillionNav").invoke(navView);
        Class<?> type=Class.forName("ai.nextbillion.navigation.core.navigator.ProgressChangeListener");AtomicInteger updates=new AtomicInteger();
        Object listener=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            if("onProgressChange".equals(method.getName()))updates.incrementAndGet();if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);
            if("equals".equals(method.getName()))return proxy==args[0];if("toString".equals(method.getName()))return "E2EProgressListener";return null;});
        navigator.getClass().getMethod("addProgressChangeListener",type).invoke(navigator,listener);
        end=SystemClock.elapsedRealtime()+15000;while(updates.get()<2&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
        assertTrue("No actual guidance progress",updates.get()>=2);navigator.getClass().getMethod("removeProgressChangeListener",type).invoke(navigator,listener);
        JSONObject cached=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(activity.getCacheDir(),"navigation-route.json").toPath()),StandardCharsets.UTF_8)).getJSONObject("routeOptions");
        for(String f:Arrays.asList("departureTime","unit","crossBorder","turnAngleRange","approaches"))if(expected.has(f))assertEquals("Activity cache lost "+f,expected.get(f).toString(),cached.get(f).toString());
        String language=expected.optString("language","en");if("zh".equals(language))language="zh-Hans";assertEquals(language,cached.getString("language"));
        JSONArray texts=new JSONArray();instrumentation.runOnMainSync(()->visibleText(guidance.getWindow().getDecorView(),texts));capture(name+"-navigation");
        Activity screen=guidance;instrumentation.runOnMainSync(screen::onBackPressed);
        waitTrue("document.getElementById('status').textContent.includes('Navigation closed')",20);guidance=null;preview();
        return new JSONObject().put("progressUpdates",updates.get()).put("visibleText",texts).put("returned",true);
    }
    private void save()throws Exception{
        JSONObject report=new JSONObject().put("device",android.os.Build.MODEL).put("android",android.os.Build.VERSION.RELEASE).put("cases",results).put("failures",new JSONArray(failures));
        try(FileOutputStream stream=new FileOutputStream(new File(output,"results.json"))){stream.write(report.toString(2).replace(key,"[REDACTED]").getBytes(StandardCharsets.UTF_8));}
    }
    private void runCase(String name,JSONObject options,boolean nav,boolean border)throws Exception{
        JSONObject result=new JSONObject().put("name",name).put("options",options);results.put(result);long began=SystemClock.elapsedRealtime();
        try{
            trip(border);configure(options);long started=System.currentTimeMillis()/1000;js("document.getElementById('plan').click();true");
            waitTrue("!document.getElementById('plan').disabled",60);
            JSONObject trace=new JSONObject(text("JSON.stringify(window.__e2ePlan)")),body=wire();result.put("bridge",trace).put("request",body).put("statusText",text("document.getElementById('status').textContent"));
            if(name.equals("border-penalty") && result.getString("statusText").contains("HTTP 422")) {
                assertFalse(body.getBoolean("cross_border"));assertFalse(trace.getBoolean("crossBorder"));
                assertTrue("No-route response lacks a useful explanation",result.getString("statusText").contains("No route matches"));
                assertEquals("true",js("document.getElementById('start').disabled"));
                assertTrue("Stale routes survived failure",((List<?>)field(plugin(),"routes")).isEmpty());
                assertEquals(Boolean.FALSE,field(plugin(),"planning"));
                assertTrue("Stale preview routes survived failure",((List<?>)field(field(plugin(),"preview"),"routes")).isEmpty());
                result.put("status","passed").put("outcome","no_route_for_constraints");return;
            }
            assertTrue("Live route failed: "+result.getString("statusText"),result.getString("statusText").contains("Route ready"));
            check(options,body,trace,started);preview();result.put("instructions",instructions(routeJson(route()))).put("routeCount",((List<?>)field(plugin(),"routes")).size());
            String cards=text("document.getElementById('routes').textContent");result.put("routeCards",cards);
            assertTrue("Route cards ignore unit",cards.contains("imperial".equals(options.optString("unit"))?" mi":" km"));
            if(nav)result.put("navigation",navigate(name,trace));result.put("status","passed");
        }catch(Throwable failure){
            String message=(failure.getMessage()==null?failure.toString():failure.getMessage()).replace(key,"[REDACTED]");result.put("status","failed").put("failure",message);failures.add(name+": "+message);
            try{result.put("request",wire());capture(name+"-failure");}catch(Exception ignored){}
            if(guidance!=null){Activity screen=guidance;instrumentation.runOnMainSync(screen::onBackPressed);guidance=null;SystemClock.sleep(400);}
            try{if("false".equals(js("document.getElementById('settings-modal').hidden")))js("document.getElementById('settings-cancel').click();true");}catch(Exception ignored){}
        }finally{result.put("elapsedMs",SystemClock.elapsedRealtime()-began);save();System.out.println("E2E "+name+": "+result.getString("status"));}
    }
    private void invalidForm(String name,String edits)throws Exception{
        JSONObject result=new JSONObject().put("name",name);results.put(result);
        String previous=activity.getSharedPreferences("NBNavigationRequest",0).getString("last_request_id","");
        try{
            open();js(edits+";document.getElementById('settings-save').click();true");
            assertEquals("false",js("document.getElementById('settings-modal').hidden"));
            String message=text("document.getElementById('settings-error').textContent");assertFalse("Invalid input was accepted",message.isEmpty());
            assertEquals("Invalid form made a routing request",previous,activity.getSharedPreferences("NBNavigationRequest",0).getString("last_request_id",""));
            assertEquals("Invalid form discarded the applied route","false",js("document.getElementById('start').disabled"));
            result.put("status","passed").put("outcome","invalid_input_rejected").put("message",message);
        }catch(Throwable failure){String message=failure.toString().replace(key,"[REDACTED]");result.put("status","failed").put("failure",message);failures.add(name+": "+message);}
        finally{js("document.getElementById('settings-cancel').click();true");save();}
    }
    @Test public void liveRouteOptionsMatrix()throws Exception{
        key=InstrumentationRegistry.getArguments().getString("apiKey");org.junit.Assume.assumeTrue("Supply -e apiKey KEY",key!=null&&!key.isEmpty());
        instrumentation.getUiAutomation().grantRuntimePermission("com.cargix.navigationdemo",Manifest.permission.ACCESS_FINE_LOCATION);
        instrumentation.getUiAutomation().grantRuntimePermission("com.cargix.navigationdemo",Manifest.permission.ACCESS_COARSE_LOCATION);
        activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        output=new File(activity.getFilesDir(),"route-e2e");assertTrue(output.isDirectory()||output.mkdirs());waitTrue("document.querySelectorAll('#stops .stop').length===2",30);
        js("(() => {const original=window.Capacitor.nativePromise;window.Capacitor.nativePromise=function(plugin,method,options){if(plugin==='NativeNavigation'&&method==='planRoute'){window.__e2ePlan=JSON.parse(JSON.stringify(options));delete window.__e2ePlan.apiKey;}return original.apply(this,arguments);};window.__e2eErrors=[];window.addEventListener('unhandledrejection',e=>window.__e2eErrors.push(String(e.reason?.message||e.reason)));window.addEventListener('error',e=>window.__e2eErrors.push(e.message));return true;})()");
        open();js("document.getElementById('key').value="+JSONObject.quote(key)+";document.getElementById('settings-save').click();true");
        waitTrue("document.getElementById('map-status').textContent.includes('NATIVE MAP')",60);
        runCase("01-defaults",new JSONObject(),true,false);long future=System.currentTimeMillis()/1000+3600;
        runCase("02-custom-fr-imperial",new JSONObject().put("departureTime",future).put("crossBorder",false).put("avoid",new JSONArray("[\"sharp_turn\"]")).put("turnAngleRange",120).put("approaches",new JSONArray("[\"unrestricted\",\"curb\"]")).put("language","fr").put("unit","imperial"),true,false);
        runCase("03-custom-zh-metric-minimum",new JSONObject().put("crossBorder",true).put("avoid",new JSONArray("[\"sharp_turn\"]")).put("turnAngleRange",1).put("approaches",new JSONArray("[\"curb\",\"curb\"]")).put("language","zh-Hans").put("unit","metric"),true,false);
        runCase("04-reset-defaults",new JSONObject(),false,false);
        for(String language:Arrays.asList("en","es","de","ca","zh"))runCase("language-"+language,new JSONObject().put("language",language),false,false);
        for(String profile:Arrays.asList("bike","motorcycle"))runCase("profile-"+profile,new JSONObject().put("profile",profile),false,false);
        for(String avoid:Arrays.asList("toll","highway","ferry","sharp_turn","uturn","service_road","left_turn","right_turn","tunnel"))runCase("avoid-"+avoid,new JSONObject().put("avoid",new JSONArray().put(avoid)),false,false);
        JSONObject truck=new JSONObject("{\"size\":{\"heightCm\":350,\"widthCm\":250,\"lengthCm\":1000},\"weightKg\":20000,\"axleLoadTonnes\":8.5,\"hazmatTypes\":[\"general\"],\"preferTruckRoutes\":true,\"types\":[\"rigid_truck\"]}");
        runCase("truck-full",new JSONObject().put("profile","truck").put("truck",truck).put("avoid",new JSONArray("[\"toll\",\"ferry\",\"uturn\"]")).put("departureTime",future).put("language","fr").put("unit","imperial").put("approaches",new JSONArray("[\"curb\",\"unrestricted\"]")),true,false);
        for(String type:Arrays.asList("semi_trailer","b_double","road_train","generic_truck"))runCase("truck-type-"+type,new JSONObject().put("profile","truck").put("truck",new JSONObject().put("preferTruckRoutes",true).put("types",new JSONArray().put(type))),false,false);
        for(String cargo:Arrays.asList("circumstantial","explosive","harmful_to_water"))runCase("hazmat-"+cargo,new JSONObject().put("profile","truck").put("truck",new JSONObject().put("hazmatTypes",new JSONArray().put(cargo))),false,false);
        runCase("approaches-unrestricted",new JSONObject().put("approaches",new JSONArray("[\"unrestricted\",\"unrestricted\"]")),false,false);
        runCase("border-expected",new JSONObject().put("crossBorder",true),false,true);
        runCase("border-penalty",new JSONObject().put("crossBorder",false),false,true);
        runCase("final-reset",new JSONObject(),true,false);
        invalidForm("invalid-departure-empty","document.getElementById('route-departure-mode').value='custom';document.getElementById('route-departure-time').value=''");
        invalidForm("invalid-departure-overflow","document.getElementById('route-departure-mode').value='custom';document.getElementById('route-departure-time').value='2040-01-01T12:00:00'");
        invalidForm("invalid-language","document.getElementById('route-language').value='en-US'");
        String sharp="document.querySelector('#avoid-options input[value=sharp_turn]').click();document.getElementById('route-turn-angle').value=";
        invalidForm("invalid-angle-zero",sharp+"'0'");invalidForm("invalid-angle-overflow",sharp+"'181'");
        invalidForm("invalid-truck-incomplete","document.querySelector('[data-profile=truck]').click();document.getElementById('truck-clear').click();document.getElementById('truck-height').value='350'");
        JSONArray errors=new JSONArray(text("JSON.stringify(window.__e2eErrors)"));results.put(new JSONObject().put("name","javascript-errors").put("errors",errors));if(errors.length()>0)failures.add("Unhandled JavaScript errors: "+errors);save();
        assertTrue("E2E failures: "+failures,failures.isEmpty());
    }
}
