package com.richardsand.novelkms.ai;

public record WeatherInterpretationRequest(
        String apiKey,
        String model,
        String weatherFacts,
        String sceneContext) {
}
