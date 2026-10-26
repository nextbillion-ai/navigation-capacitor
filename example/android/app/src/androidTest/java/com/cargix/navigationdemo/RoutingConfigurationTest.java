package com.cargix.navigationdemo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Inspect actual SDK request values without adding an SDK dependency to the consumer app. */
@RunWith(AndroidJUnit4.class)
public class RoutingConfigurationTest {
    private final Class<?> request = Class.forName("ai.nextbillion.kits.directions.models.RouteRequestParams");
    private final Class<?> builder = Class.forName(request.getName() + "$Builder");
    public RoutingConfigurationTest() throws Exception {}

    @Before public void initializeSDK() throws Exception {
        Class<?> sdk = Class.forName("ai.nextbillion.maps.Nextbillion");
        Class<?> server = Class.forName("ai.nextbillion.maps.style.NGLWellKnownTileServer");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                sdk.getMethod("getInstance", android.content.Context.class, String.class, server).invoke(null,
                    InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext(),
                    "configuration-test-no-network", server.getField("NGLTomTom").get(null));
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    private Object make(String json) throws Exception { return make(json, 2); }
    private Object make(String json, int stopCount) throws Exception {
        Object value = request.getMethod("builder").invoke(null);
        Class<?> point = Class.forName("ai.nextbillion.kits.geojson.Point");
        Object origin = point.getMethod("fromLngLat", double.class, double.class).invoke(null, -122.423579, 37.761689);
        Object destination = point.getMethod("fromLngLat", double.class, double.class).invoke(null, -122.426183, 37.760872);
        builder.getMethod("origin", point).invoke(value, origin);
        builder.getMethod("destination", point).invoke(value, destination);
        if (stopCount > 2) builder.getMethod("waypoints", List.class).invoke(value, new ArrayList<>(Collections.nCopies(stopCount - 2, origin)));
        apply(json, value, stopCount);
        return builder.getMethod("build").invoke(value);
    }
    private void apply(String json, Object value, int stopCount) throws Exception {
        Method method = Class.forName("ai.nextbillion.navigation.capacitor.RouteConfiguration").getDeclaredMethod("apply", JSONObject.class, builder, int.class);
        method.setAccessible(true);
        method.invoke(null, new JSONObject(json), value, stopCount);
    }
    private Object field(Object value, String name) throws Exception { return request.getMethod(name).invoke(value); }
    private void rejects(String json) throws Exception {
        try { make(json); fail("Accepted invalid configuration: " + json); }
        catch (InvocationTargetException error) { assertTrue(error.getCause().toString(), error.getCause() instanceof IllegalArgumentException); }
    }
    @Test public void defaultsAndProfiles() throws Exception {
        assertEquals("car", field(make("{}"), "mode"));
        for (String profile : Arrays.asList("car", "truck", "bike", "motorcycle")) {
            Object value = make("{\"profile\":\"" + profile + "\"}");
            assertEquals(profile, field(value, "mode"));
            assertEquals("flexible", field(value, "option"));
            JSONObject payload = body(value);
            assertEquals(profile, payload.getString("mode"));
            assertEquals("full", payload.getString("overview"));
            assertEquals("polyline6", payload.getString("geometry"));
            assertEquals("fastest", payload.getString("route_type"));
            assertTrue(payload.getBoolean("alternatives"));
            assertEquals(2, payload.getInt("altcount"));
        }
    }
    @Test public void preservesUnitsAndMultipleSelections() throws Exception {
        Object value = make("{\"profile\":\"truck\",\"avoid\":[\"toll\",\"ferry\",\"toll\",\"uturn\"],\"truck\":{\"size\":{\"heightCm\":350,\"widthCm\":250,\"lengthCm\":1000},\"weightKg\":20000,\"axleLoadTonnes\":8.5,\"hazmatTypes\":[\"general\",\"explosive\"],\"preferTruckRoutes\":true,\"types\":[\"rigid_truck\",\"semi_trailer\"]}}");
        assertEquals(Arrays.asList("350", "250", "1000"), field(value, "truckSize"));
        assertEquals(20000, field(value, "truckWeight"));
        assertEquals(8.5f, (Float)field(value, "truckAxleLoad"), 0f);
        assertEquals(Arrays.asList("toll", "ferry", "uturn"), field(value, "avoid"));
        assertEquals(Arrays.asList("general", "explosive"), field(value, "hazmatType"));
        assertEquals("truck_route", field(value, "prefer"));
        assertEquals(Arrays.asList("rigid_truck", "semi_trailer"), field(value, "truckType"));
        Object car = make("{\"profile\":\"car\",\"avoid\":[]}");
        assertEquals(Collections.emptyList(), field(car, "avoid"));
        assertEquals(0, field(car, "truckWeight"));
    }
    @Test public void rejectsInvalidConfiguration() throws Exception {
        for (String json : Arrays.asList(
            "{\"profile\":\"horse\"}", "{\"avoid\":[\"bbox\"]}", "{\"avoid\":\"toll\"}",
            "{\"profile\":\"car\",\"truck\":{}}", "{\"profile\":null}",
            "{\"profile\":\"truck\",\"truck\":{\"weightKg\":0}}",
            "{\"profile\":\"truck\",\"truck\":{\"weightKg\":100001}}",
            "{\"profile\":\"truck\",\"truck\":{\"weightKg\":20000.5}}",
            "{\"profile\":\"truck\",\"truck\":{\"weightKg\":\"20000\"}}",
            "{\"profile\":\"truck\",\"truck\":{\"size\":{\"heightCm\":350}}}",
            "{\"profile\":\"truck\",\"truck\":{\"axleLoadTonnes\":-1}}",
            "{\"profile\":\"truck\",\"truck\":{\"preferTruckRoutes\":1}}",
            "{\"profile\":\"truck\",\"truck\":{\"types\":[\"rigid_truck\"]}}",
            "{\"profile\":\"truck\",\"truck\":{\"hazmatTypes\":[\"invalid\"]}}"
        )) rejects(json);
    }
    /** Serialize through Retrofit/OkHttp without sending the request. */
    private JSONObject body(Object params) throws Exception {
        Class<?> service = Class.forName("ai.nextbillion.navigation.core.routefetcher.NBNavigationService");
        Object serviceBuilder = service.getMethod("builder", request).invoke(null, params);
        // The release SDK obfuscates this internal builder method. Select by its return type.
        Method build = Arrays.stream(Class.forName(service.getName() + "$Builder").getMethods())
            .filter(method -> method.getParameterCount() == 0 && service.isAssignableFrom(method.getReturnType()))
            .findFirst().orElseThrow(() -> new AssertionError("SDK service builder has no factory"));
        Object value = build.invoke(serviceBuilder);
        Object call = service.getMethod("post").invoke(value);
        Object httpRequest = Class.forName("retrofit2.Call").getMethod("request").invoke(call);
        Object requestBody = Class.forName("okhttp3.Request").getMethod("body").invoke(httpRequest);
        Object buffer = Class.forName("okio.Buffer").getConstructor().newInstance();
        Class.forName("okhttp3.RequestBody").getMethod("writeTo", Class.forName("okio.BufferedSink")).invoke(requestBody, buffer);
        return new JSONObject((String) Class.forName("okio.Buffer").getMethod("readUtf8").invoke(buffer));
    }

    private Set<String> selections(JSONObject body, String name) throws Exception {
        return new HashSet<>(Arrays.asList(body.getString(name).split("\\|")));
    }

    private String completeTruck() throws Exception {
        return new JSONObject("{\"profile\":\"truck\",\"avoid\":[\"toll\",\"highway\",\"ferry\",\"sharp_turn\",\"uturn\",\"service_road\",\"left_turn\",\"right_turn\",\"tunnel\",\"toll\"],\"truck\":{\"size\":{\"heightCm\":350,\"widthCm\":250,\"lengthCm\":1000},\"weightKg\":20000,\"axleLoadTonnes\":8.5,\"hazmatTypes\":[\"general\",\"circumstantial\",\"explosive\",\"harmful_to_water\"],\"preferTruckRoutes\":true,\"types\":[\"rigid_truck\",\"semi_trailer\",\"b_double\",\"road_train\",\"generic_truck\"]}}").put("departureTime", 1893456000).put("crossBorder", true)
            .put("turnAngleRange", 120).put("language", "fr").put("unit", "imperial")
            .put("approaches", new org.json.JSONArray("[\"curb\"]")).toString();
    }

    private void assertTruckBody(Object params) throws Exception {
        JSONObject json = body(params);
        assertEquals("truck", json.getString("mode"));
        assertEquals(1893456000, json.getInt("departure_time"));
        assertTrue(json.getBoolean("cross_border"));
        assertEquals(120, json.getInt("turn_angle_range"));
        assertEquals("fr", json.getString("lang"));
        assertEquals("imperial", json.getString("unit"));
        assertEquals("curb", json.getString("approaches"));
        assertEquals("350,250,1000", json.getString("truck_size"));
        assertEquals(20000, json.getInt("truck_weight"));
        assertEquals(8.5, json.getDouble("truck_axle_load"), 0.000001);
        assertEquals("truck_route", json.getString("prefer"));
        assertEquals(new HashSet<>(Arrays.asList("toll", "highway", "ferry", "sharp_turn", "uturn", "service_road", "left_turn", "right_turn", "tunnel")), selections(json, "avoid"));
        assertEquals(new HashSet<>(Arrays.asList("general", "circumstantial", "explosive", "harmful_to_water")), selections(json, "hazmat_type"));
        assertEquals(new HashSet<>(Arrays.asList("rigid_truck", "semi_trailer", "b_double", "road_train", "generic_truck")), selections(json, "truck_type"));
    }

    @Test public void serializedRequestPreservesEveryAvoidAndTruckValue() throws Exception {
        assertTruckBody(make(completeTruck()));
        for (String avoid : Arrays.asList("toll", "highway", "ferry", "sharp_turn", "uturn", "service_road", "left_turn", "right_turn", "tunnel")) {
            assertEquals(avoid, body(make("{\"avoid\":[\"" + avoid + "\",\"" + avoid + "\"]}")).getString("avoid"));
        }
        assertEquals("1000,5000,5000", body(make("{\"profile\":\"truck\",\"truck\":{\"size\":{\"heightCm\":1000,\"widthCm\":5000,\"lengthCm\":5000}}}")).getString("truck_size"));
    }

    @Test public void serializationAndBuilderCopyPreserveNavigationRestrictions() throws Exception {
        Object original = make(completeTruck());
        String json = (String) request.getMethod("toJson").invoke(original);
        assertTruckBody(request.getMethod("fromJson", String.class).invoke(null, json));
        // Mirror the plugin's cache file: serialize the entire route, then recover its request options.
        Class<?> routeClass = Class.forName("ai.nextbillion.kits.directions.models.DirectionsRoute");
        Class<?> routeBuilderClass = Class.forName(routeClass.getName() + "$Builder");
        Object routeBuilder = routeClass.getMethod("builder").invoke(null);
        routeBuilderClass.getMethod("distance", Double.class).invoke(routeBuilder, 100.0);
        routeBuilderClass.getMethod("duration", Double.class).invoke(routeBuilder, 10.0);
        routeBuilderClass.getMethod("legs", List.class).invoke(routeBuilder, Collections.emptyList());
        routeBuilderClass.getMethod("routeOptions", request).invoke(routeBuilder, original);
        Object route = routeBuilderClass.getMethod("build").invoke(routeBuilder);
        String cache = (String) routeClass.getMethod("toJson").invoke(route);
        Object restoredRoute = routeClass.getMethod("fromJson", String.class).invoke(null, cache);
        assertTruckBody(routeClass.getMethod("routeOptions").invoke(restoredRoute));
        Object copied = request.getMethod("toBuilder").invoke(original);
        assertTruckBody(builder.getMethod("build").invoke(copied));
        JSONObject car = body(make("{\"profile\":\"car\",\"avoid\":[]}"));
        for (String name : Arrays.asList("truck_size", "truck_weight", "truck_axle_load", "truck_type", "hazmat_type", "prefer", "avoid")) {
            assertFalse("Car inherited " + name, car.has(name) && !car.isNull(name));
        }
    }

    @Test public void omittedOptionsLeaveSDKDefaultsUntouched() throws Exception {
        Object sdkDefaults = request.getMethod("builder").invoke(null);
        Object expected = builder.getMethod("build").invoke(sdkDefaults);
        Object actual = make("{}");
        for (String name : Arrays.asList("departureTime", "language", "unit", "crossBorder", "turnAngleRange", "approaches"))
            assertEquals("Overrode native default: " + name, field(expected, name), field(actual, name));
        assertEquals(0, field(actual, "departureTime")); // RouteFetcher chooses current time later.
        Object seeded = request.getMethod("toBuilder").invoke(actual);
        builder.getMethod("departureTime", int.class).invoke(seeded, 1893456000);
        builder.getMethod("language", String.class).invoke(seeded, "de");
        builder.getMethod("unit", String.class).invoke(seeded, "imperial");
        apply("{}", seeded, 2);
        Object retained = builder.getMethod("build").invoke(seeded);
        assertEquals(1893456000, field(retained, "departureTime"));
        assertEquals("de", field(retained, "language"));
        assertEquals("imperial", field(retained, "unit"));
    }

    @Test public void approachOrderDuplicatesAndMinimumAngleArePreserved() throws Exception {
        assertEquals("curb;curb", body(make("{\"approaches\":[\"curb\",\"curb\"]}", 3)).getString("approaches"));
        assertEquals("unrestricted;curb", body(make("{\"approaches\":[\"unrestricted\",\"curb\"]}", 3)).getString("approaches"));
        assertEquals("unrestricted", body(make("{\"approaches\":[\"unrestricted\"]}")).getString("approaches"));
        JSONObject zero = body(make("{\"avoid\":[\"sharp_turn\"],\"turnAngleRange\":1,\"crossBorder\":false}"));
        assertEquals(1, zero.getInt("turn_angle_range"));
        assertFalse(zero.getBoolean("cross_border"));
        assertEquals("metric", body(make("{\"unit\":\"metric\",\"language\":\"zh-Hans\"}")).getString("unit"));
        assertEquals("zh-Hans", body(make("{\"language\":\"zh-Hans\"}")).getString("lang"));
    }

    @Test public void rejectsInvalidOptionalRouteSettings() throws Exception {
        for (String json : Arrays.asList(
            "{\"departureTime\":0}", "{\"departureTime\":-1}", "{\"departureTime\":2147483648}",
            "{\"departureTime\":1893456000000}", "{\"departureTime\":1.5}", "{\"departureTime\":true}",
            "{\"departureTime\":\"1893456000\"}", "{\"departureTime\":null}",
            "{\"crossBorder\":1}", "{\"crossBorder\":\"false\"}", "{\"crossBorder\":null}",
            "{\"turnAngleRange\":120}", "{\"avoid\":[\"sharp_turn\"],\"turnAngleRange\":0}", "{\"avoid\":[\"sharp_turn\"],\"turnAngleRange\":-1}",
            "{\"avoid\":[\"sharp_turn\"],\"turnAngleRange\":181}", "{\"avoid\":[\"sharp_turn\"],\"turnAngleRange\":1.5}",
            "{\"avoid\":[\"sharp_turn\"],\"turnAngleRange\":true}",
            "{\"language\":\"\"}", "{\"language\":\"en-US\"}", "{\"language\":null}",
            "{\"unit\":\"km\"}", "{\"unit\":null}",
            "{\"approaches\":[]}", "{\"approaches\":[\"curb\",\"curb\"]}",
            "{\"approaches\":[\"left\"]}", "{\"approaches\":\"curb\"}", "{\"approaches\":null}"
        )) rejects(json);
    }

}
