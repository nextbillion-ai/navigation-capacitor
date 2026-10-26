package ai.nextbillion.navigation.capacitor;

import ai.nextbillion.kits.directions.models.RouteRequestParams;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Validate the bridge boundary before configuring the native request. No unit conversion. */
final class RouteConfiguration {
    private static final Set<String> PROFILES = values("car", "truck", "bike", "motorcycle");
    private static final Set<String> AVOIDS = values("toll", "highway", "ferry", "sharp_turn", "uturn", "service_road", "left_turn", "right_turn", "tunnel");
    private static final Set<String> HAZMATS = values("general", "circumstantial", "explosive", "harmful_to_water");
    private static final Set<String> TRUCK_TYPES = values("rigid_truck", "semi_trailer", "b_double", "road_train", "generic_truck");

    static void apply(JSONObject input, RouteRequestParams.Builder builder, int stopCount) {
        String profile = input.has("profile") ? choice(input.opt("profile"), PROFILES, "profile") : "car";
        builder.mode(profile).option("flexible").routeType("fastest")
            .alternatives(true).altCount(2).avoid(list(input, "avoid", AVOIDS));
        applyRouteOptions(input, builder, stopCount);
        if (!input.has("truck")) return;
        if (!"truck".equals(profile)) throw invalid("truck options require profile=truck.");
        Object rawTruck = input.opt("truck");
        if (!(rawTruck instanceof JSONObject)) throw invalid("truck must be an object.");
        JSONObject truck = (JSONObject) rawTruck;
        if (truck.has("size")) {
            Object rawSize = truck.opt("size");
            if (!(rawSize instanceof JSONObject)) throw invalid("truck.size must contain heightCm, widthCm and lengthCm.");
            JSONObject size = (JSONObject) rawSize;
            int height = (int) number(size, "heightCm", 1000, true);
            int width = (int) number(size, "widthCm", 5000, true);
            int length = (int) number(size, "lengthCm", 5000, true);
            builder.truckSize(Arrays.asList(String.valueOf(height), String.valueOf(width), String.valueOf(length)));
        }
        if (truck.has("weightKg")) builder.truckWeight((int) number(truck, "weightKg", 100000, true));
        if (truck.has("axleLoadTonnes")) {
            double axle = number(truck, "axleLoadTonnes", Float.MAX_VALUE, false);
            if ((float) axle == 0) throw invalid("axleLoadTonnes is too small.");
            builder.truckAxleLoad((float) axle);
        }
        builder.hazmatType(list(truck, "hazmatTypes", HAZMATS));
        boolean prefer = false;
        if (truck.has("preferTruckRoutes")) {
            Object value = truck.opt("preferTruckRoutes");
            if (!(value instanceof Boolean)) throw invalid("preferTruckRoutes must be a boolean.");
            prefer = (Boolean) value;
        }
        List<String> types = list(truck, "types", TRUCK_TYPES);
        if (!types.isEmpty() && !prefer) throw invalid("truck.types requires preferTruckRoutes=true.");
        if (prefer) builder.prefer("truck_route");
        builder.truckType(types);
    }

    private static void applyRouteOptions(JSONObject input, RouteRequestParams.Builder builder, int stopCount) {
        // Leave absent fields untouched: RouteFetcher resolves its own current time and SDK defaults.
        if (input.has("departureTime")) builder.departureTime((int) number(input, "departureTime", Integer.MAX_VALUE, true));
        if (input.has("crossBorder")) {
            Object value = input.opt("crossBorder");
            if (!(value instanceof Boolean)) throw invalid("crossBorder must be a boolean.");
            builder.crossBorder((Boolean) value);
        }
        if (input.has("turnAngleRange")) {
            List<String> avoids = list(input, "avoid", AVOIDS);
            if (!avoids.contains("sharp_turn")) throw invalid("turnAngleRange requires avoid to include sharp_turn.");
            builder.turnAngleRange((int) number(input, "turnAngleRange", 180, true));
        }
        if (input.has("language")) {
            Object value = input.opt("language");
            if (!(value instanceof String) || !((String) value).matches("[a-z]{2}|zh-Hans"))
                throw invalid("language must be a two-letter code such as en/fr/zh, or zh-Hans.");
            builder.language((String) value);
        }
        if (input.has("unit")) builder.unit(choice(input.opt("unit"), values("metric", "imperial"), "unit"));
        if (input.has("approaches")) {
            List<String> approaches = list(input, "approaches", values("unrestricted", "curb"), false);
            if (approaches.size() != stopCount - 1)
                throw invalid("approaches must contain one value per non-origin stop (stops.length - 1).");
            builder.approaches(approaches);
        }
    }

    private static Set<String> values(String... values) { return new HashSet<>(Arrays.asList(values)); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static String choice(Object value, Set<String> allowed, String name) {
        if (!(value instanceof String) || !allowed.contains(value)) throw invalid("Unsupported " + name + ": " + value);
        return (String) value;
    }
    private static List<String> list(JSONObject input, String name, Set<String> allowed) {
        return list(input, name, allowed, true);
    }
    private static List<String> list(JSONObject input, String name, Set<String> allowed, boolean deduplicate) {
        if (!input.has(name)) return new ArrayList<>();
        Object raw = input.opt(name);
        if (!(raw instanceof JSONArray)) throw invalid(name + " must be an array.");
        JSONArray array = (JSONArray) raw;
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = choice(array.opt(i), allowed, name);
            if (!deduplicate || !result.contains(value)) result.add(value);
        }
        return result;
    }
    private static double number(JSONObject input, String name, double maximum, boolean integer) {
        Object raw = input.opt(name);
        if (!(raw instanceof Number)) throw invalid(name + " must be a number.");
        double value = ((Number) raw).doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0 || value > maximum || (integer && value != Math.rint(value)))
            throw invalid(name + " must be " + (integer ? "a whole number" : "a number") + " > 0 and <= " + maximum + ".");
        return value;
    }
}
