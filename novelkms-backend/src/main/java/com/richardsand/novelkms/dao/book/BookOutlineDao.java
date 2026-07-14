package com.richardsand.novelkms.dao.book;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.dbcp2.BasicDataSource;

import com.richardsand.novelkms.model.book.OutlineRef;

/**
 * DAO surface for a book's outline — the unified {@code display_order} sequence
 * shared by the book's parts and its direct-book chapters.
 *
 * <p>All the real work lives in the static {@link BookOutline} primitives, which
 * take a {@link Connection} so that {@code ChapterDao} and {@code PartDao} can
 * allocate outline positions inside their own insert transactions. This class
 * exists to give the resource layer a normal injectable DAO over the same code.
 */
public class BookOutlineDao {

    private final BasicDataSource ds;

    public BookOutlineDao(BasicDataSource ds) {
        this.ds = ds;
    }

    /** Parts and direct-book chapters of this book, in linear outline order. */
    public List<OutlineRef> findByBookId(UUID bookId) throws SQLException {
        try (Connection c = ds.getConnection()) {
            return BookOutline.find(c, bookId);
        }
    }

    /**
     * Renumbers the whole book outline to the supplied order, in one
     * transaction. A single drag can move a chapter across a part boundary, so
     * parts and chapters must be renumbered together or not at all.
     */
    public void reorder(UUID bookId, List<OutlineRef> items) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                BookOutline.applyOrder(c, bookId, items);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
}
