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
    private String subtitle;

    @JsonProperty
    private int displayOrder;

    @JsonProperty
    private String notes;

    /**
     * 1-based ordinal position of this part within its book, computed via
     * ROW_NUMBER() in the DAO. Not stored in the database — transient, like
     * Chapter.chapterNumber. Used by the frontend to display "Part I/II/III"
     * when the part has no custom title.
     */
    @JsonProperty
    private int partNumber;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}