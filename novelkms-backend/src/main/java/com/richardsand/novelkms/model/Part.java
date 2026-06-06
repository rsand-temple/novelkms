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
public class Part {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID bookId;

    @JsonProperty
    private String title;

    @JsonProperty
    private int displayOrder;

    @JsonProperty
    private String notes;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
