package com.orgzly.android.repos;

import com.orgzly.android.App;
import com.orgzly.android.BookName;
import com.orgzly.android.OrgzlyTest;
import com.orgzly.android.db.entity.BookView;
import com.orgzly.android.util.MiscUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

public class DropboxRepoTest extends OrgzlyTest {
    private static final String DROPBOX_TEST_DIR = "/orgzly-android-tests";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        testUtils.dropboxTestPreflight();
    }

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testUrl() {
        assertEquals(
                "dropbox:/dir",
                testUtils.repoInstance(RepoType.DROPBOX, "dropbox:/dir").getUri().toString());
    }

    @Test
    public void testSyncingUrlWithTrailingSlash() {
        testUtils.setupRepo(RepoType.DROPBOX, randomUrl() + "/");
        assertNotNull(testUtils.sync());
    }

    private String randomUrl() {
        return "dropbox:"+ DROPBOX_TEST_DIR + "/" + UUID.randomUUID().toString();
    }
}
