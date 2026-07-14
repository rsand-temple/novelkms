package com.richardsand.novelkms.model.book;

/**
 * The two kinds of entity that occupy the book-level outline sequence.
 *
 * <p>A book's outline is the single, contiguous {@code display_order} sequence
 * shared by its {@code part} rows and its direct-book {@code chapter} rows
 * (those with {@code part_id IS NULL AND codex_id IS NULL}). Parts and direct
 * chapters interleave freely, which is what allows a prologue to sit before
 * Part I and an epilogue after Part IV.
 *
 * <p>Chapters that live <em>inside</em> a part are not outline items; they have
 * their own {@code display_order} sequence scoped to that part.
 */
public enum OutlineItemType {
    PART,
    CHAPTER
}
