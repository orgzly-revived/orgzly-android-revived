package com.orgzly.android.ui;

import android.content.Intent;
import android.widget.Toast;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.orgzly.R;
import com.orgzly.android.AppIntent;
import com.orgzly.android.BookUtils;
import com.orgzly.android.db.entity.Book;
import com.orgzly.android.prefs.AppPreferences;
import com.orgzly.android.ui.capture.CaptureTemplate;
import com.orgzly.android.ui.capture.CaptureTemplateKt;
import com.orgzly.android.ui.share.ShareActivity;

import java.util.List;

/**
 * Activity for creating a "New note" home screen shortcut.
 *
 * The user picks a notebook and, if any capture templates are defined, a
 * template. The shortcut then opens the new-note screen with that template
 * applied, falling back to the chosen notebook when the template has no target.
 */
public class TemplateChooserActivity extends BookChooserActivity {
    @Override
    public void onBookClicked(long bookId) {
        if (action == null) {
            return;
        }
        if (!action.equals(Intent.ACTION_CREATE_SHORTCUT)) {
            return;
        }
        Book book = dataRepository.getBook(bookId);

        if (book == null) {
            Toast.makeText(this, R.string.book_does_not_exist_anymore, Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        List<CaptureTemplate> templates = AppPreferences.captureTemplates(this);

        if (templates.isEmpty()) {
            createShortcut(book, null);
            return;
        }

        String[] labels = new String[templates.size() + 1];
        labels[0] = getString(R.string.widget_capture_template_default_note);
        for (int i = 0; i < templates.size(); i++) {
            labels[i + 1] = templateDisplayName(templates.get(i), i);
        }

        setAlertDialog(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_capture_template)
                .setItems(labels, (dialog, which) ->
                        createShortcut(book, which == 0 ? null : templates.get(which - 1)))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> cancelShortcut())
                .setOnCancelListener(dialog -> cancelShortcut())
                .show());
    }

    private void cancelShortcut() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private String templateDisplayName(CaptureTemplate template, int index) {
        return CaptureTemplateKt.getDisplayName(
                template, getString(R.string.capture_template_numbered, index + 1));
    }

    private void createShortcut(Book book, CaptureTemplate template) {
        String id;
        String name;
        String title;

        Intent launchIntent = ShareActivity.createNewNoteIntent(this);
        launchIntent.putExtra(AppIntent.EXTRA_BOOK_ID, book.getId());

        if (template != null) {
            id = "template-" + book.getId() + "-" + template.getId();
            name = CaptureTemplateKt.getDisplayName(template, getString(R.string.capture_template));
            title = name;
            launchIntent.putExtra(AppIntent.EXTRA_CAPTURE_TEMPLATE_ID, template.getId());
        } else {
            id = "template-" + book.getId();
            name = book.getName();
            title = BookUtils.getFragmentTitleForBook(book);
        }

        IconCompat icon = createIcon();

        ShortcutInfoCompat shortcut =
                new ShortcutInfoCompat.Builder(this, id)
                        .setShortLabel(name)
                        .setLongLabel(title)
                        .setIcon(icon)
                        .setIntent(launchIntent)
                        .build();

        setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcut));

        finish();
    }

    private IconCompat createIcon() {
        return IconCompat.createWithResource(this, R.mipmap.cic_shortcut_new_note);
    }
}
