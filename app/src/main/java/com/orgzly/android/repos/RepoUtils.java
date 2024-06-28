package com.orgzly.android.repos;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.Collection;

public class RepoUtils {
    /**
     * @return true if there is a repository that requires connection, false otherwise
     */
    public static boolean isConnectionRequired(Collection<SyncRepo> repos) {
        for (SyncRepo repo: repos) {
            if (repo.isConnectionRequired()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if all repositories support auto-sync, false otherwise
     */
    public static boolean isAutoSyncSupported(Collection<SyncRepo> repos) {
        for (SyncRepo repo: repos) {
            if (!repo.isAutoSyncSupported()) {
                return false;
            }
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void ensureFileNameIsNotIgnored(SyncRepo repo, String fileName) {
        new RepoIgnoreNode(repo).ensureFileNameIsNotIgnored(fileName);
    }
}

