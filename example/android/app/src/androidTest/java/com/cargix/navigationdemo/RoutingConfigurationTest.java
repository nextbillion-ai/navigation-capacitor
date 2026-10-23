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

    private Object make(String json) throws Exception {
        Object value = request.getMethod("builder").invoke(null);
        Class<?> point = Class.forName("ai.nextbillion.kits.geojson.Point");
        Object origin = point.getMethod("fromLngLat", double.class, double.class).invoke(null, -122.423579, 37.761689);
        Object destination = point.getMethod("fromLngLat", double.class, double.class).invoke(null, -122.426183, 37.760872);
        builder.getMethod("origin", point).invoke(value, origin);
        builder.getMethod("destination", point).invoke(value, destination);
        Method apply = Class.forName("ai.nextbillion.navigation.capacitor.RouteConfiguration").getDeclaredMethod("apply", JSONObject.class, builder);
        apply.setAccessible(true);
        apply.invoke(null, new JSONObject(json), value);
        return builder.getMethod("build").invoke(value);
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
}
