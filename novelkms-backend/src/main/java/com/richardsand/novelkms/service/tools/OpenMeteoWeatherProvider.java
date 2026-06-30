package com.richardsand.novelkms.service.tools;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.service.tools.WeatherLookupService.LocationMatch;
import com.richardsand.novelkms.service.tools.WeatherLookupService.WeatherDaily;
import com.richardsand.novelkms.service.tools.WeatherLookupService.WeatherLookupResult;

public class OpenMeteoWeatherProvider implements WeatherProvider {

    private static final String PROVIDER_KEY = "open-meteo";
    private static final String GEOCODING_ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast";
    private static final String ARCHIVE_ENDPOINT = "https://archive-api.open-meteo.com/v1/archive";
    private static final String DAILY_FIELDS = String.join(",",
            "weather_code",
            "temperature_2m_max",
            "temperature_2m_min",
            "apparent_temperature_max",
            "apparent_temperature_min",
            "sunrise",
            "sunset",
            "daylight_duration",
            "sunshine_duration",
            "precipitation_sum",
            "rain_sum",
            "snowfall_sum",
            "precipitation_hours",
            "wind_speed_10m_max",
            "wind_gusts_10m_max",
            "wind_direction_10m_dominant");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public WeatherLookupResult lookup(String location, LocalDate date, String units) throws Exception {
        if (date.isBefore(LocalDate.of(1940, 1, 1))) {
            throw new IllegalArgumentException("Open-Meteo historical archive starts in 1940. Older dates need another source.");
        }

        LocationMatch match = geocode(location);
        boolean metric = "metric".equalsIgnoreCase(units);
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        String dataKind = date.isBefore(todayUtc) ? "historical_modeled" : "forecast";
        String endpoint = "historical_modeled".equals(dataKind) ? ARCHIVE_ENDPOINT : FORECAST_ENDPOINT;

        URI uri = URI.create(endpoint + "?latitude=" + match.latitude()
                + "&longitude=" + match.longitude()
                + "&start_date=" + date
                + "&end_date=" + date
                + "&daily=" + DAILY_FIELDS
                + "&timezone=auto"
                + "&temperature_unit=" + (metric ? "celsius" : "fahrenheit")
                + "&wind_speed_unit=" + (metric ? "kmh" : "mph")
                + "&precipitation_unit=" + (metric ? "mm" : "inch"));

        JsonNode root = getJson(uri);
        JsonNode daily = root.path("daily");
        if (daily.isMissingNode() || daily.path("time").size() == 0) {
            throw new IllegalArgumentException("No weather data was returned for that location/date.");
        }

        JsonNode unitsNode = root.path("daily_units");
        WeatherDaily weather = new WeatherDaily(
                textAt(daily, "time"),
                intAt(daily, "weather_code"),
                weatherCodeLabel(intAt(daily, "weather_code")),
                doubleAt(daily, "temperature_2m_max"),
                doubleAt(daily, "temperature_2m_min"),
                doubleAt(daily, "apparent_temperature_max"),
                doubleAt(daily, "apparent_temperature_min"),
                doubleAt(daily, "precipitation_sum"),
                doubleAt(daily, "rain_sum"),
                doubleAt(daily, "snowfall_sum"),
                doubleAt(daily, "precipitation_hours"),
                doubleAt(daily, "wind_speed_10m_max"),
                doubleAt(daily, "wind_gusts_10m_max"),
                doubleAt(daily, "wind_direction_10m_dominant"),
                textAt(daily, "sunrise"),
                textAt(daily, "sunset"),
                doubleAt(daily, "daylight_duration"),
                doubleAt(daily, "sunshine_duration"));

        String sourceNote = "historical_modeled".equals(dataKind)
                ? "Historical modeled/reanalysis estimate from Open-Meteo; not a station-specific observed record."
                : "Forecast from Open-Meteo. Forecasts become less certain farther into the future.";

        return new WeatherLookupResult(
                PROVIDER_KEY,
                "Open-Meteo",
                dataKind,
                sourceNote,
                match,
                root.path("timezone").asText(null),
                metric ? "metric" : "imperial",
                unitsNode.path("temperature_2m_max").asText(metric ? "°C" : "°F"),
                unitsNode.path("wind_speed_10m_max").asText(metric ? "km/h" : "mph"),
                unitsNode.path("precipitation_sum").asText(metric ? "mm" : "inch"),
                weather);
    }

    private LocationMatch geocode(String location) throws Exception {
        String cleaned = normalizeLocation(location);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Enter a city, state, or country.");
        }

        List<String> searchNames = geocodeSearchNames(cleaned);

        JsonNode best = null;
        int bestScore = Integer.MIN_VALUE;

        for (String searchName : searchNames) {
            String encoded = URLEncoder.encode(searchName, StandardCharsets.UTF_8);
            URI uri = URI.create(GEOCODING_ENDPOINT + "?name=" + encoded + "&count=10&language=en&format=json");

            JsonNode root = getJson(uri);
            JsonNode results = root.path("results");
            if (!results.isArray()) continue;

            for (JsonNode candidate : results) {
                int score = scoreLocationCandidate(cleaned, candidate);
                if (best == null || score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }

            if (best != null && bestScore >= 10) break;
        }

        if (best == null) {
            throw new IllegalArgumentException("No matching location found.");
        }

        StringBuilder display = new StringBuilder(best.path("name").asText(location));
        appendPart(display, best.path("admin1").asText(null));
        appendPart(display, best.path("country").asText(null));

        return new LocationMatch(
                display.toString(),
                best.path("name").asText(null),
                best.path("admin1").asText(null),
                best.path("country").asText(null),
                best.path("country_code").asText(null),
                best.path("latitude").asDouble(),
                best.path("longitude").asDouble());
    }
    
    private static String normalizeLocation(String location) {
        if (location == null) return "";
        return location.strip()
                .replaceAll("\\s*/\\s*", ", ")
                .replaceAll("\\s+", " ");
    }

    private static List<String> geocodeSearchNames(String location) {
        List<String> names = new ArrayList<>();

        String[] parts = location.split(",");
        if (parts.length > 0 && !parts[0].isBlank()) {
            names.add(parts[0].strip());
        }

        String noPunctuation = location
                .replace(',', ' ')
                .replace('/', ' ')
                .replaceAll("\\s+", " ")
                .strip();

        if (!noPunctuation.isBlank() && !names.contains(noPunctuation)) {
            names.add(noPunctuation);
        }

        if (!names.contains(location)) {
            names.add(location);
        }

        return names;
    }

    private static int scoreLocationCandidate(String requestedLocation, JsonNode candidate) {
        String haystack = (
                nullToBlank(candidate.path("name").asText(null)) + " " +
                nullToBlank(candidate.path("admin1").asText(null)) + " " +
                nullToBlank(candidate.path("admin2").asText(null)) + " " +
                nullToBlank(candidate.path("country").asText(null)) + " " +
                nullToBlank(candidate.path("country_code").asText(null))
        ).toLowerCase(Locale.ROOT);

        String requested = requestedLocation.toLowerCase(Locale.ROOT);
        int score = 0;

        for (String token : requested.split("[,\\s/]+")) {
            String t = token.strip().toLowerCase(Locale.ROOT);
            if (t.length() < 2) continue;

            if (haystack.contains(t)) {
                score += t.length() >= 4 ? 4 : 2;
            }
        }

        String name = candidate.path("name").asText("").toLowerCase(Locale.ROOT);
        String firstRequestedPart = requested.split(",")[0].strip();
        if (!firstRequestedPart.isBlank() && name.equals(firstRequestedPart)) {
            score += 8;
        }

        return score;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private JsonNode getJson(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("Weather provider returned HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    private static void appendPart(StringBuilder display, String part) {
        if (part != null && !part.isBlank() && !display.toString().contains(part)) {
            display.append(", ").append(part);
        }
    }

    private static String textAt(JsonNode node, String field) {
        JsonNode arr = node.path(field);
        if (!arr.isArray() || arr.size() == 0 || arr.get(0).isNull()) return null;
        return arr.get(0).asText();
    }

    private static Double doubleAt(JsonNode node, String field) {
        JsonNode arr = node.path(field);
        if (!arr.isArray() || arr.size() == 0 || arr.get(0).isNull()) return null;
        return arr.get(0).asDouble();
    }

    private static Integer intAt(JsonNode node, String field) {
        JsonNode arr = node.path(field);
        if (!arr.isArray() || arr.size() == 0 || arr.get(0).isNull()) return null;
        return arr.get(0).asInt();
    }

    private static String weatherCodeLabel(Integer code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snowfall";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Weather code " + code;
        };
    }
}
