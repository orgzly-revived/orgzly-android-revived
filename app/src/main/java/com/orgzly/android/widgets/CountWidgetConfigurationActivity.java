package com.orgzly.android.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.orgzly.BuildConfig;
import com.orgzly.R;
import com.orgzly.android.ui.BookChooserActivity;
import com.orgzly.android.util.LogUtils;

import java.util.Map;

/**
 * Activity for configuring the note count widget (choosing a notebook).
 */
public class CountWidgetConfigurationActivity extends BookChooserActivity {

    private static final String TAG =
        CountWidgetConfigurationActivity.class.getName();

    private static final String PREFERENCES_ID = "count-widget";
    private static final String WIDGET_PREFIX_KEY = "widget-";

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                                        AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // Default result
        Intent resultValue =
            new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                                  appWidgetId);
        setResult(Activity.RESULT_CANCELED, resultValue);

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onBookClicked(long bookId) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, bookId);

        SharedPreferences.Editor prefsEditor = getPrefs(this).edit();
        setWidgetBook(prefsEditor, appWidgetId, bookId);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        CountWidgetProvider.updateWidget(this, appWidgetManager,
                                         appWidgetId, bookId, dataRepository);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFERENCES_ID, Context.MODE_PRIVATE);
    }

    private static String widgetKey(int widgetId) {
        return WIDGET_PREFIX_KEY + widgetId;
    }

    static void setWidgetBook(SharedPreferences.Editor editor,
                              int widgetId, long bookId) {
        String key = widgetKey(widgetId);
        editor.putLong(key, bookId);
        editor.apply();
    }

    static void removeCountWidgets(SharedPreferences.Editor editor, int[] widgetIds) {
        for (int id : widgetIds) {
            editor.remove(widgetKey(id));
        }
        editor.apply();
    }

    static void removeAllCountWidgets(SharedPreferences.Editor editor) {
        editor.clear().apply();
    }

    static Long getWidgetBook(SharedPreferences prefs, int widgetId) {
        String key = widgetKey(widgetId);
        Map<String, ?> map = prefs.getAll();
        return (Long)map.get(key);
    }

}
