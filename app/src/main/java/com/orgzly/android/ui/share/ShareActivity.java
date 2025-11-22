package com.orgzly.android.ui.share;

import androidx.appcompat.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.TaskStackBuilder;
import androidx.core.content.pm.ShortcutManagerCompat;

import com.orgzly.BuildConfig;
import com.orgzly.R;
import com.orgzly.android.App;
import com.orgzly.android.AppIntent;
import com.orgzly.android.data.DataRepository;
import com.orgzly.android.db.entity.Book;
import com.orgzly.android.db.entity.Note;
import com.orgzly.android.db.entity.SavedSearch;
import com.orgzly.android.prefs.AppPreferences;
import com.orgzly.android.query.Query;
import com.orgzly.android.query.QueryUtils;
import com.orgzly.android.query.user.DottedQueryParser;
import com.orgzly.android.sync.AutoSync;
import com.orgzly.android.ui.AppSnackbarUtils;
import com.orgzly.android.ui.CommonActivity;
import com.orgzly.android.ui.NotePlace;
import com.orgzly.android.SharingShortcutsManager;
import com.orgzly.android.ui.sync.SyncFragment;
import com.orgzly.android.ui.note.NoteFragment;
import com.orgzly.android.ui.util.ActivityUtils;
import com.orgzly.android.usecase.UseCase;
import com.orgzly.android.usecase.UseCaseResult;
import com.orgzly.android.util.LogUtils;
import com.orgzly.android.util.MiscUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.inject.Inject;

/**
 * Activity started when shared to Orgzly.
 *
 * TODO: Resuming - intent will stay the same.
 * If activity is not finished (by save, cancel or pressing back), next share will resume the
 * activity and the intent will stay the same. Other apps seem to have the same problem and
 * it's not a common scenario, but it should be fixed.
 */
public class ShareActivity extends CommonActivity
        implements
        NoteFragment.Listener,
        SyncFragment.Listener {

    public static final String TAG = ShareActivity.class.getName();

    /** Shared text files are read and their content is stored as note content. */
    private static final long MAX_TEXT_FILE_LENGTH_FOR_CONTENT = 1024 * 1024 * 2; // 2 MB

    private SyncFragment mSyncFragment;

    private String mError;

    private AlertDialog dialog;

    @Inject
    DataRepository dataRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.appComponent.inject(this);

        super.onCreate(savedInstanceState);

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState);

        setContentView(R.layout.activity_share);

        Data data = getDataFromIntent(getIntent());

        setupFragments(savedInstanceState, data);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private Data getTextDataFromIntent(Intent intent) {
        Data data = new Data();
        if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            if (AppPreferences.sharedTextPlacement(App.getAppContext()).equals("in_note_heading")) {
                data.title = intent.getStringExtra(Intent.EXTRA_TEXT);
            } else {
                data.content = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
        } else if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

            data.title = uri.getLastPathSegment();

            /*
             * Store file's content as note content.
             */
            try {
                File file = new File(uri.getPath());

                /* Don't read large files. */
                if (file.length() > MAX_TEXT_FILE_LENGTH_FOR_CONTENT) {
                    mError = "File has " + file.length() +
                            " bytes (refusing to read files larger then " +
                            MAX_TEXT_FILE_LENGTH_FOR_CONTENT + " bytes)";

                } else {
                    data.content = MiscUtils.readStringFromFile(file);
                }

            } catch (IOException e) {
                e.printStackTrace();
                mError = "Failed reading the content of " + uri.toString() + ": " + e.toString();
            }
        }
        if (data.content != null && data.title == null && intent.hasExtra(Intent.EXTRA_SUBJECT)) {
            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (subject != null && !subject.isEmpty()) {
                data.title = subject;
            }
        }
        // if it's a url with title, let's turn it into org url
        if (data.content != null && data.title != null) {
            // A multi-line "subject" will not make a good link
            if (!data.title.contains("\n")) {
                try {
                    new URI(data.content);
                    data.title = "[[" + data.content + "][" + data.title + "]]";
                    data.content = null;
                } catch (URISyntaxException ignored) {}
            }
        }
        // TODO: Was used for direct share shortcuts to pass the book name. Used someplace else?
        if (intent.hasExtra(AppIntent.EXTRA_QUERY_STRING)) {
            Query query = new DottedQueryParser().parse(intent.getStringExtra(AppIntent.EXTRA_QUERY_STRING));
            String bookName = QueryUtils.extractFirstBookNameFromQuery(query.getCondition());

            if (bookName != null) {
                Book book = dataRepository.getBook(bookName);
                if (book != null) {
                    data.bookId = book.getId();
                    if (BuildConfig.LOG_DEBUG)
                        LogUtils.d(TAG, "Using book " + data.bookId
                                + " from passed query " + query + " (" + bookName + ")");
                }
            }
        }
        if (intent.hasExtra(AppIntent.EXTRA_BOOK_ID)) {
            data.bookId = intent.getLongExtra(AppIntent.EXTRA_BOOK_ID, 0L);
            if (BuildConfig.LOG_DEBUG)
                LogUtils.d(TAG, "Using book " + data.bookId
                        + " from passed book ID");
        }
        // Coming from Direct Share shortcut
        if (intent.hasExtra(Intent.EXTRA_SHORTCUT_ID)) {
            String shortcutId = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID);
            data.bookId = SharingShortcutsManager.bookIdFromShortcutId(shortcutId);
            if (BuildConfig.LOG_DEBUG)
                LogUtils.d(TAG, "Using book " + data.bookId
                        + " from passed shortcut ID");
        }
        return data;
    }

    private Data getDataFromIntent(Intent intent) {
        Data data = new Data();
        mError = null;

        String action = intent.getAction();
        String type = intent.getType();

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, intent);

        if (action != null && type != null) {
            switch (action) {
                case Intent.ACTION_SEND:
                    if (type.startsWith("text/")) {
                        data = getTextDataFromIntent(intent);
                    } else if (type.startsWith("image/")) {
                        handleSendImage(intent, data); // Handle single image being sent
                    } else {
                        mError = getString(R.string.share_type_not_supported, type);
                    }
                    break;
                case "com.google.android.gm.action.AUTO_SEND":
                    if (type.startsWith("text/") && intent.hasExtra(Intent.EXTRA_TEXT)) {
                        data.title = intent.getStringExtra(Intent.EXTRA_TEXT);
                    }
                    break;
                default:
                    mError = getString(R.string.share_action_not_supported, action);
            }
        }

        /* Make sure that title is never empty. */
        if (data.title == null) data.title = "";

        return data;
    }

    private void setupFragments(Bundle savedInstanceState, Data data) {
        NoteFragment noteFragment;

        if (savedInstanceState == null) { /* Create and add fragments. */

            mSyncFragment = SyncFragment.getInstance();

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(mSyncFragment, SyncFragment.FRAGMENT_TAG)
                    .commit();

            try {
                long bookId;
                if (data.bookId == null) {
                    bookId = dataRepository.getTargetBook(this).getBook().getId();
                } else {
                    bookId = data.bookId;
                }

                noteFragment = NoteFragment.forNewNote(
                        new NotePlace(bookId), data.title, data.content);

                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.activity_share_main, noteFragment, NoteFragment.FRAGMENT_TAG)
                        .commit();
            } catch (IOException ex) {
                ex.printStackTrace();
                // bail out here
                finish();
            }
        } else { /* Get existing fragments. */
            mSyncFragment = (SyncFragment) getSupportFragmentManager().findFragmentByTag(SyncFragment.FRAGMENT_TAG);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        autoSync.trigger(AutoSync.Type.APP_RESUMED);

        if (mError != null) {
            AppSnackbarUtils.showSnackbar(this, mError);
            mError = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        autoSync.trigger(AutoSync.Type.APP_SUSPENDED);
    }

    public static PendingIntent createNewNotePendingIntent(Context context, String category, SavedSearch savedSearch) {
        Intent resultIntent = createNewNoteIntent(context);

        // For distinguishing pending events
        resultIntent.addCategory(category);

        if (savedSearch != null) {
            resultIntent.putExtra(AppIntent.EXTRA_QUERY_STRING, savedSearch.getQuery());
        }

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, resultIntent);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(ShareActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);

        return stackBuilder.getPendingIntent(
                0, ActivityUtils.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static Intent createNewNoteIntent(Context context) {
        Intent intent = new Intent(context, ShareActivity.class);
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "");
        return intent;
    }

    @Override
    public void onNoteCreated(Note note) {
        finish();
    }

    @Override
    public void onNoteUpdated(Note note) {
    }

    @Override
    public void onNoteCanceled() {
        finish();
    }

    /**
     * User action succeeded.
     */
    @Override
    public void onSuccess(UseCase action, UseCaseResult result) {
    }

    /**
     * User action failed.
     */
    @Override
    public void onError(UseCase action, Throwable throwable) {
        AppSnackbarUtils.showSnackbar(this, throwable.getLocalizedMessage());
    }

    private class Data {
        String title;
        String content;
        Long bookId = null;
    }

    /**
     * Get file path from image shared with Orgzly
     * and put it as a file link in the note's content.
     */
    private void handleSendImage(Intent intent, Data data) {
        // Get file uri from intent which probably looks like this:
        // content://media/external/images/...
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null) {
                cursor.moveToFirst();

                if (BuildConfig.LOG_DEBUG)
                    LogUtils.d(TAG, DatabaseUtils.dumpCursorToString(cursor));

                /*
                 * Get real file path from content:// link pointing to file
                 * ( https://stackoverflow.com/a/20059657 )
                 */
                int dataColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);

                if (dataColumnIndex != -1) {
                    String mediaData = cursor.getString(dataColumnIndex);
                    if (mediaData != null) {
                        data.content = "file:" + mediaData;
                    }
                }

                if (data.content == null) {
                    data.content = uri.toString()
                            + "\n\nCannot determine path to this image "
                            + "and only linking to an image is currently supported.";

                    Log.e(TAG, DatabaseUtils.dumpCursorToString(cursor));
                }

                int displayNameColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);

                if (displayNameColumnIndex != -1) {
                    data.title = cursor.getString(displayNameColumnIndex);
                } else {
                    data.title = uri.toString();
                }
            }
        }

        if (data.title == null) {
            data.title = uri.toString();
            data.content = "Cannot find image using this URI.";
        }
    }
}
