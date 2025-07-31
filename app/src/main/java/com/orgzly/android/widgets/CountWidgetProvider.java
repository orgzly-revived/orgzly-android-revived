package com.orgzly.android.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;

import com.orgzly.BuildConfig;
import com.orgzly.R;
import com.orgzly.android.App;
import com.orgzly.android.AppIntent;
import com.orgzly.android.data.DataRepository;
import com.orgzly.android.db.entity.Book;
import com.orgzly.android.ui.main.MainActivity;
import com.orgzly.android.util.LogUtils;

import javax.inject.Inject;

public class CountWidgetProvider extends AppWidgetProvider {

    private static final String TAG = CountWidgetProvider.class.getName();

    @Inject
    DataRepository dataRepository;

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences prefs = CountWidgetConfigurationActivity.getPrefs(context);
        CountWidgetConfigurationActivity.removeCountWidgets(prefs.edit(), appWidgetIds);
    }

    @Override
    public void onDisabled(Context context) {
        // There shouldn't be any preferences left, but clear them, just in case
        SharedPreferences prefs = CountWidgetConfigurationActivity.getPrefs(context);
        CountWidgetConfigurationActivity.removeAllCountWidgets(prefs.edit());
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        App.appComponent.inject(this);

        for (int appWidgetId : appWidgetIds) {
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "id=" + appWidgetId);

            SharedPreferences prefs = CountWidgetConfigurationActivity.getPrefs(context);
            Long bookId =
                CountWidgetConfigurationActivity.getWidgetBook(prefs, appWidgetId);
            updateWidget(context, appWidgetManager, appWidgetId, bookId, dataRepository);
        }
    }

    public static void updateWidget(Context context,
                                    AppWidgetManager appWidgetManager,
                                    int appWidgetId,
                                    Long bookId,
                                    DataRepository dataRepository) {
        String name = null;
        int count = 0;
        Intent onClickIntent = null;
        Book book = null;

        // Check bookId; onUpdate can get called even before configuration,
        // despite the docs. See: https://stackoverflow.com/a/12236443
        if (bookId != null) {
            book = dataRepository.getBook(bookId);
            count = dataRepository.getNoteCount(bookId);
        }

        if (book != null) {
            onClickIntent = new Intent(context, MainActivity.class);
            onClickIntent.setAction(Intent.ACTION_MAIN);
            onClickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                   Intent.FLAG_ACTIVITY_CLEAR_TASK);
            onClickIntent.putExtra(AppIntent.EXTRA_BOOK_ID, book.getId());
            name = book.getName();
        }

        // Defaults for name and intent make a shortcut to configure the widget
        if (name == null)
            name = context.getString(R.string.count_widget_label);

        if (onClickIntent == null) {
            onClickIntent = new Intent(context, CountWidgetConfigurationActivity.class);
            onClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        }

        PendingIntent pendingIntent =
            PendingIntent.getActivity(
                context, appWidgetId, onClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        RemoteViews views = new RemoteViews(context.getPackageName(),
                                            R.layout.count_widget);
        views.setOnClickPendingIntent(R.id.count_widget_layout, pendingIntent);
        views.setTextViewText(R.id.count_widget_book_name, name);
        views.setTextViewText(R.id.note_count, Integer.toString(count));
        int badgeVisibility = count == 0 ? View.GONE : View.VISIBLE;
        views.setViewVisibility(R.id.note_count, badgeVisibility);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateCounts(Context context) {
        ComponentName thisWidget =
            new ComponentName(context, CountWidgetProvider.class);
        int[] ids = AppWidgetManager.getInstance(context).getAppWidgetIds(thisWidget);
        Intent intent = new Intent(context, CountWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(intent);
    }
}
