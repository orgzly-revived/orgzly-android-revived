package com.orgzly.android.repos;

import android.net.Uri;

import com.orgzly.android.data.DbRepoBookRepository;
import com.orgzly.android.util.MiscUtils;
import com.orgzly.android.util.UriUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Repo which stores all its files in a local database.
 * Used for testing by {@link com.orgzly.android.repos.MockRepo}.
 */
public class DatabaseRepo implements SyncRepo {
    private final long repoId;
    private final Uri repoUri;

    private DbRepoBookRepository dbRepo;

    public DatabaseRepo(RepoWithProps repoWithProps, DbRepoBookRepository dbRepo) {
        this.repoId = repoWithProps.getRepo().getId();
        this.repoUri = Uri.parse(repoWithProps.getRepo().getUrl());
        this.dbRepo = dbRepo;
    }

    @Override
    public boolean isConnectionRequired() {
        return false;
    }

    @Override
    public boolean isAutoSyncSupported() {
        return true;
    }

    @Override
    public Uri getUri() {
        return repoUri;
    }

    @Override
    public List<VersionedRook> getBooks() {
        return dbRepo.getBooks(repoId, repoUri);
    }

    @Override
    public VersionedRook retrieveBook(Uri uri, File file) {
        return dbRepo.retrieveBook(repoId, repoUri, uri, file);
    }

    @Override
    public InputStream openRepoFileInputStream(String repoRelativePath) throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public VersionedRook storeBook(File file, String repoRelativePath) throws IOException {
        String content = MiscUtils.readStringFromFile(file);

        String rev = "MockedRevision-" + System.currentTimeMillis();
        long mtime = System.currentTimeMillis();

        Uri uri = repoUri.buildUpon().appendPath(repoRelativePath).build();

        VersionedRook vrook = new VersionedRook(repoId, RepoType.MOCK, repoUri, uri, rev, mtime);

        return dbRepo.createBook(repoId, vrook, content);
    }

    @Override
    public VersionedRook renameBook(Uri oldFullUri, String newName) {
        Uri toUri = UriUtils.getUriForNewName(oldFullUri, newName);
        return dbRepo.renameBook(repoId, oldFullUri, toUri);
    }

    @Override
    public void delete(Uri uri) throws IOException {
        dbRepo.deleteBook(uri);
    }

    @Override
    public String toString() {
        return repoUri.toString();
    }
}
