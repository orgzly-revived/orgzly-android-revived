package com.orgzly.android

import com.orgzly.android.espresso.ActionModeTest
import com.orgzly.android.espresso.AgendaFragmentTest
import com.orgzly.android.espresso.BookChooserActivityTest
import com.orgzly.android.espresso.BookPrefaceTest
import com.orgzly.android.espresso.BookTest
import com.orgzly.android.espresso.BooksSortOrderTest
import com.orgzly.android.espresso.BooksTest
import com.orgzly.android.espresso.CreatedAtPropertyTest
import com.orgzly.android.espresso.ExternalLinksTest
import com.orgzly.android.espresso.InternalLinksTest
import com.orgzly.android.espresso.MiscTest
import com.orgzly.android.espresso.NewNoteTest
import com.orgzly.android.espresso.NoteEventsTest
import com.orgzly.android.espresso.NoteFragmentTest
import com.orgzly.android.espresso.QueryFragmentTest
import com.orgzly.android.espresso.ReposActivityTest
import com.orgzly.android.espresso.SavedSearchesFragmentTest
import com.orgzly.android.espresso.SettingsChangeTest
import com.orgzly.android.espresso.SettingsFragmentTest
import com.orgzly.android.espresso.ShareActivityTest
import com.orgzly.android.espresso.SyncingTest
import com.orgzly.android.misc.BookNameTest
import com.orgzly.android.misc.BookParsingTest
import com.orgzly.android.misc.ContentCheckboxesTitleUpdateViaUpdateNoteContentTest
import com.orgzly.android.misc.ContentCheckboxesTitleUpdateViaUpdateNoteTest
import com.orgzly.android.misc.DataTest
import com.orgzly.android.misc.NewNoteContentCheckboxesTitleUpdateTest
import com.orgzly.android.misc.SettingsTest
import com.orgzly.android.misc.StateChangeParentTitleUpdateTest
import com.orgzly.android.misc.StateChangeTest
import com.orgzly.android.misc.StructureTest
import com.orgzly.android.misc.UriTest
import com.orgzly.android.query.QueryTokenizerTest
import com.orgzly.android.query.QueryUtilsTest
import com.orgzly.android.reminders.NoteRemindersTest
import com.orgzly.android.repos.DataRepositoryTest
import com.orgzly.android.repos.DirectoryRepoTest
import com.orgzly.android.repos.DropboxRepoTest
import com.orgzly.android.repos.LocalDbRepoTest
import com.orgzly.android.repos.RepoFactoryTest
import com.orgzly.android.ui.ImageLoaderTest
import com.orgzly.android.uiautomator.ListWidgetTest
import com.orgzly.android.usecase.NoteUpdateDeadlineTimeTest
import com.orgzly.android.usecase.NoteUpdateScheduledTimeTest
import com.orgzly.android.util.AgendaUtilsTest
import com.orgzly.android.util.EncodingDetectTest
import com.orgzly.android.util.MiscUtilsTest
import com.orgzly.android.util.OrgFormatterSpeedTest
import com.orgzly.android.util.OrgFormatterStyleTextTest
import com.orgzly.android.util.UriUtilsTest
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.Suite

@Ignore("Used only in case tests can't be started from Android Studio")
@RunWith(Suite::class)
@Suite.SuiteClasses(
        ActionModeTest::class,
        AgendaFragmentTest::class,
        BookChooserActivityTest::class,
        BookPrefaceTest::class,
        BooksSortOrderTest::class,
        BooksTest::class,
        BookTest::class,
        CreatedAtPropertyTest::class,
        ExternalLinksTest::class,
        InternalLinksTest::class,
        MiscTest::class,
        NewNoteTest::class,
        NoteEventsTest::class,
        NoteFragmentTest::class,
        QueryFragmentTest::class,
        ReposActivityTest::class,
        SavedSearchesFragmentTest::class,
        SettingsChangeTest::class,
        SettingsFragmentTest::class,
        ShareActivityTest::class,
        SyncingTest::class,

        BookNameTest::class,
        BookParsingTest::class,
        CreatedAtPropertyTest::class,
        DataTest::class,
        SettingsTest::class,
        StateChangeTest::class,
        StructureTest::class,
        UriTest::class,

        QueryTokenizerTest::class,
        QueryUtilsTest::class,

        NoteRemindersTest::class,

        DataRepositoryTest::class,
        DirectoryRepoTest::class,
        DropboxRepoTest::class,
        LocalDbRepoTest::class,
        RepoFactoryTest::class,

        ImageLoaderTest::class,

        ListWidgetTest::class,

        NoteUpdateDeadlineTimeTest::class,
        NoteUpdateScheduledTimeTest::class,

        AgendaUtilsTest::class,
        EncodingDetectTest::class,
        MiscUtilsTest::class,
        OrgFormatterSpeedTest::class,
        OrgFormatterStyleTextTest::class,
        ContentCheckboxesTitleUpdateViaUpdateNoteTest::class,
        ContentCheckboxesTitleUpdateViaUpdateNoteContentTest::class,
        NewNoteContentCheckboxesTitleUpdateTest::class,
        StateChangeParentTitleUpdateTest::class,
        UriUtilsTest::class
)
class AllTestSuite