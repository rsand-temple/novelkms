package com.richardsand.novelkms.service;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.dao.PartDao;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.model.AppUser;
import com.richardsand.novelkms.model.Part;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;

/**
 * Seeds a welcoming starter project hierarchy for a newly registered user so
 * they land on an empty scene and can begin writing immediately.
 *
 * The call in AuthResource is intentionally wrapped in a try/catch so that a
 * seeding failure never blocks a successful registration response.
 */
public class StarterContentService {

    private static final Logger logger = LoggerFactory.getLogger(StarterContentService.class);

    /** TipTap HTML content shown in the first scene. Word count = 4. */
    private static final String STARTER_CONTENT    = "<p>Begin your scene here.</p>";
    private static final int    STARTER_WORD_COUNT = 4;

    private final ProjectDao projectDao;
    private final BookDao    bookDao;
    private final PartDao    partDao;
    private final ChapterDao chapterDao;
    private final SceneDao   sceneDao;

    public StarterContentService(
            ProjectDao projectDao,
            BookDao bookDao,
            PartDao partDao,
            ChapterDao chapterDao,
            SceneDao sceneDao) {
        this.projectDao = projectDao;
        this.bookDao = bookDao;
        this.partDao = partDao;
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
    }

    /**
     * Creates:
     * Project "My Project" (with author info from the user's registration data)
     * └─ Book "My Book"
     * └─ Part (blank title → renders "Part I")
     * └─ Chapter (blank title → renders "Chapter 1")
     * └─ Scene with welcoming placeholder content
     *
     * Throws SQLException on any database failure; callers are expected to catch
     * and log rather than surface the error to the user.
     */
    public void seedForNewUser(AppUser user) throws SQLException {
        logger.info("Seeding starter content for new user: userId={}", user.id());

        // 1. Create the project.
        Project project = projectDao.createForUser(user.id(), "My Project", null);

        // 2. Populate author fields from the registration data. displayName and
        // emailAddress are always present; firstName / lastName may be null if
        // the OAuth provider did not supply them.
        Project withAuthor = Project.builder()
                .id(project.getId())
                .title(project.getTitle())
                .description(null)
                .authorFirstName(user.firstName())
                .authorLastName(user.lastName())
                .displayName(user.displayName())
                .emailAddress(user.emailAddress())
                .phoneNumber(null)
                .copyright(null)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
        projectDao.updateForUser(user.id(), withAuthor);

        // 3. Create the book.
        Book book = bookDao.create(project.getId(), "My Book", null, null, null);

        // 4. Create a part. Blank title renders as "Part I" via the auto-numbering CTE.
        Part part = partDao.create(book.getId(), null, null, null);

        // 5. Create a chapter under the part. Blank title renders as "Chapter 1".
        Chapter chapter = chapterDao.create(book.getId(), part.getId(), null, null, null);

        // 6. Create the first scene and set welcoming placeholder content.
        Scene scene = sceneDao.create(chapter.getId(), null, null);
        sceneDao.saveContent(scene.getId(), STARTER_CONTENT, STARTER_WORD_COUNT);

        logger.info(
                "Starter content seeded: userId={} projectId={} bookId={} partId={} chapterId={} sceneId={}",
                user.id(), project.getId(), book.getId(), part.getId(), chapter.getId(), scene.getId());
    }
}