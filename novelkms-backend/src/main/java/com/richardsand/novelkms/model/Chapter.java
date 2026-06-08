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
public class Chapter {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID bookId;

    /** Nullable — chapter may belong directly to a book without a part. */
    @JsonProperty
    private UUID partId;

    @JsonProperty
    private String title;

    /** Optional subtitle — may be null or blank. */
    @JsonProperty
    private String subtitle;

    @JsonProperty
    private int displayOrder;

    @JsonProperty
    private String notes;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;

    /** computed, not stored */
    @JsonProperty
    private int chapterNumber;

    public void setChapterNumber(int chapterNumber) {
        this.chapterNumber = chapterNumber;
    }
}
