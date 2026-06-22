package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single entry in the per-user Trash listing. Each entry corresponds to one
 * {@code trash_batch} row — a root the user clicked Delete on. Children are not
 * listed individually; they are removed/restored along with their root.
 *
 * <p>{@code rootType} is one of: PROJECT, BOOK, CHAPTER, SCENE, CODEX_CATEGORY,
 * CODEX_ENTRY, AI_REVIEW. {@code rootTitle} and {@code projectTitle} are display
 * snapshots captured at delete time. {@code childCount} is the number of
 * descendant items hidden with the root (display only).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashItem {

    @JsonProperty
    private UUID batchId;

    @JsonProperty
    private String rootType;

    @JsonProperty
    private UUID rootId;

    @JsonProperty
    private String rootTitle;

    @JsonProperty
    private UUID projectId;

    @JsonProperty
    private String projectTitle;

    @JsonProperty
    private int childCount;

    @JsonProperty
    private Instant deletedAt;
}
