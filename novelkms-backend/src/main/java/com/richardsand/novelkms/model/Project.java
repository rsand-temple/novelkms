package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private String title;

    @JsonProperty
    private String description;

    @JsonProperty
    private String authorFirstName;

    @JsonProperty
    private String authorLastName;

    /**
     * Copyright line for the project / series (e.g. "© 2026 Richard Sand").
     * Resolved by the COPYRIGHT template token; also intended for a future
     * page template / about page.
     */
    @JsonProperty
    private String copyright;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
