package com.orgzly.android.repos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.orgzly.android.OrgzlyTest;

import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

public class DropboxRepoTest extends OrgzlyTest {
    private static final String DROPBOX_TEST_DIR = "/orgzly-android-tests";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        testUtils.dropboxTestPreflight();
    }

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
