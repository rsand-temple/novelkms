package com.richardsand.novelkms.service.tools;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.ai.AiProvider;
import com.richardsand.novelkms.ai.WeatherInterpretationRequest;
import com.richardsand.novelkms.ai.WeatherInterpretationResult;
import com.richardsand.novelkms.dao.ai.AiCredentialDao;
import com.richardsand.novelkms.model.ai.AiCredential;

public class WeatherLookupService {

    private final WeatherProvider         provider;
    private final AiCredentialDao         aiCredentialDao;
    private final Map<String, AiProvider> aiProviders;
    private final ObjectMapper            mapper = new ObjectMapper();

    public WeatherLookupService(WeatherProvider provider,
            AiCredentialDao aiCredentialDao,
            Map<String, AiProvider> aiProviders) {
        this.provider = provider;
        this.aiCredentialDao = aiCredentialDao;
        this.aiProviders = aiProviders;
    }

    public WeatherLookupResult lookup(String location, LocalDate date, String units) throws Exception {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Enter a city, state, or country.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Enter a date.");
        }
        String normalizedUnits = "metric".equalsIgnoreCase(units) ? "metric" : "imperial";
        return provider.lookup(location.strip(), date, normalizedUnits);
    }

    public WeatherInterpretationResponse interpret(UUID userId, WeatherInterpretationInput input) throws Exception {
        AiCredential credential = aiCredentialDao.findDefault(userId)
                .orElseThrow(() -> new IllegalStateException("Add an AI API key in Settings before using AI weather interpretation."));
        AiProvider   aiProvider = aiProviders.get(credential.getProvider());
        if (aiProvider == null) {
            throw new IllegalStateException("No AI provider is registered for " + credential.getProvider() + ".");
        }

        String apiKey = aiCredentialDao.getDecryptedKey(credential.getId(), userId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("The selected AI credential could not be read.");
        }

        String                      weatherFacts = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(input.weather());
        WeatherInterpretationResult result       = aiProvider.interpretWeather(new WeatherInterpretationRequest(
                apiKey,
                credential.getDefaultModel(),
                weatherFacts,
                input.sceneContext()));

        return new WeatherInterpretationResponse(result.content(), result.promptVersion(), credential.getProvider(), credential.getDefaultModel());
    }

    public record WeatherLookupResult(
            String provider,
            String sourceName,
            String dataKind,
            String sourceNote,
            LocationMatch location,
            String timezone,
            String units,
            String temperatureUnit,
            String windSpeedUnit,
            String precipitationUnit,
            WeatherDaily daily) {
    }

    public record LocationMatch(
            String displayName,
            String name,
            String admin1,
            String country,
            String countryCode,
            double latitude,
            double longitude) {
    }

    public record WeatherDaily(
            String date,
            Integer weatherCode,
            String weatherLabel,
            Double temperatureMax,
            Double temperatureMin,
            Double apparentTemperatureMax,
            Double apparentTemperatureMin,
            Double precipitationSum,
            Double rainSum,
            Double snowfallSum,
            Double precipitationHours,
            Double windSpeedMax,
            Double windGustsMax,
            Double windDirectionDominant,
            String sunrise,
            String sunset,
            Double daylightDuration,
            Double sunshineDuration) {
    }

    public record WeatherInterpretationInput(
            WeatherLookupResult weather,
            String sceneContext) {
    }

    public record WeatherInterpretationResponse(
            String content,
            String promptVersion,
            String provider,
            String model) {
    }
}
