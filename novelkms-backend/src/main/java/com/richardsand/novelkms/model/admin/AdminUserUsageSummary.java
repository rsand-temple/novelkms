package com.richardsand.novelkms.model.admin;

public record AdminUserUsageSummary(
        int projectCount,
        int bookCount,
        int partCount,
        int chapterCount,
        int sceneCount,
        int codexEntryCount,
        int aiReviewCount) {
}