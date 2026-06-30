package com.richardsand.novelkms.resource;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.richardsand.novelkms.auth.CurrentUser;
import com.richardsand.novelkms.service.tools.CalendarToolsService;
import com.richardsand.novelkms.service.tools.WeatherLookupService;
import com.richardsand.novelkms.service.tools.WeatherLookupService.WeatherInterpretationInput;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("/tools")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ToolsResource {

    private final CalendarToolsService calendarToolsService;
    private final WeatherLookupService weatherLookupService;

    @Context
    ContainerRequestContext request;

    @Inject
    public ToolsResource(CalendarToolsService calendarToolsService, WeatherLookupService weatherLookupService) {
        this.calendarToolsService = calendarToolsService;
        this.weatherLookupService = weatherLookupService;
    }

    @GET
    @Path("/day-of-week")
    public Response dayOfWeek(@QueryParam("date") String date,
            @QueryParam("calendar") @DefaultValue("gregorian") String calendar) {
        if (date == null || date.isBlank()) {
            return error(Status.BAD_REQUEST, "date_required", "Enter a date.");
        }

        try {
            return Response.ok(calendarToolsService.dayOfWeek(date, calendar)).build();
        } catch (IllegalArgumentException e) {
            return error(Status.BAD_REQUEST, "invalid_date", e.getMessage());
        }
    }

    @GET
    @Path("/weather")
    public Response weather(@QueryParam("location") String location,
            @QueryParam("date") String date,
            @QueryParam("units") @DefaultValue("imperial") String units) {
        if (location == null || location.isBlank()) {
            return error(Status.BAD_REQUEST, "location_required", "Enter a city, state, or country.");
        }
        if (date == null || date.isBlank()) {
            return error(Status.BAD_REQUEST, "date_required", "Enter a date.");
        }

        try {
            return Response.ok(weatherLookupService.lookup(location, LocalDate.parse(date), units)).build();
        } catch (IllegalArgumentException e) {
            return error(Status.BAD_REQUEST, "invalid_weather_request", e.getMessage());
        } catch (Exception e) {
            return error(Status.BAD_GATEWAY, "weather_lookup_failed", e.getMessage());
        }
    }

    @POST
    @Path("/weather/interpret")
    public Response interpretWeather(WeatherInterpretationInput input) {
        if (input == null || input.weather() == null) {
            return error(Status.BAD_REQUEST, "weather_required", "Look up weather before asking AI to interpret it.");
        }

        UUID userId = CurrentUser.id(request);
        try {
            return Response.ok(weatherLookupService.interpret(userId, input)).build();
        } catch (IllegalStateException e) {
            return error(Status.BAD_REQUEST, "ai_unavailable", e.getMessage());
        } catch (SQLException e) {
            return error(Status.INTERNAL_SERVER_ERROR, "server_error", "Could not read AI credentials.");
        } catch (Exception e) {
            return error(Status.BAD_GATEWAY, "ai_weather_interpretation_failed", e.getMessage());
        }
    }

    private static Response error(Status status, String code, String message) {
        return Response.status(status)
                .entity(Map.of("error", code, "message", message))
                .build();
    }
}
