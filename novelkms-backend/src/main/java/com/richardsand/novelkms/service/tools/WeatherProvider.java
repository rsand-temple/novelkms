package com.richardsand.novelkms.service.tools;

import java.time.LocalDate;

public interface WeatherProvider {
    String providerKey();
    WeatherLookupService.WeatherLookupResult lookup(String location, LocalDate date, String units) throws Exception;
}
