package com.richardsand.novelkms.model;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One node in a project's Artifacts tree — a non-manuscript file/folder store
 * (query letters, research, cover-art sources, etc.). The tree is
 * self-referential: {@code parentId} null means the (virtual) project root.
 *
 * <p>A FOLDER node groups children; a FILE node points at one
 * {@code artifact_blob} (the bytes live on disk) and carries denormalized
 * {@code sizeBytes}/{@code contentType} so the Explorer details view and the
 * tree read need no blob join. {@code blobId} is intentionally not serialized —
 * the frontend never needs the content-store key.
 *
 * <p>Windows case convention: {@code name} is preserved exactly as authored;
 * case-insensitive sibling uniqueness is driven by the {@code name_normalized}
 * column (not exposed here) inside the mutating DAO transaction.
 *
 * <p>Boolean fields are deliberately absent; per the Lombok/Jackson is-prefix
 * collision rule, {@code type} carries FOLDER/FILE rather than an {@code isFile}
 * flag.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactNode {

    @JsonProperty
    private UUID id;

    @JsonProperty
    private UUID projectId;

    /** Null when the node sits at the project root. */
    @JsonProperty
    private UUID parentId;

    /** FOLDER or FILE. */
    @JsonProperty
    private String type;

    @JsonProperty
    private String name;

    @JsonProperty
    private int displayOrder;

    /** Bytes for a FILE node; 0 for a FOLDER. */
    @JsonProperty
    private long sizeBytes;

    /** MIME type for a FILE node; null for a FOLDER. */
    @JsonProperty
    private String contentType;

    @JsonProperty
    private Instant createdAt;

    @JsonProperty
    private Instant updatedAt;
}
