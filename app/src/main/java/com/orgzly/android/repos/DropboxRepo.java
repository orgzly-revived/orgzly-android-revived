package com.orgzly.android.repos;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.orgzly.android.BookName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class DropboxRepo implements SyncRepo {
    public static final String SCHEME = "dropbox";

    private final Uri repoUri;
    private final DropboxClient client;

    public DropboxRepo(RepoWithProps repoWithProps, Context context) {
        this.repoUri = Uri.parse(repoWithProps.getRepo().getUrl());
        this.client = new DropboxClient(context, repoWithProps.getRepo().getId());
    }

    @Override
    public boolean isConnectionRequired() {
        return true;
    }

    @Override
    public boolean isAutoSyncSupported() {
        return false;
    }

    @Override
    public Uri getUri() {
        return repoUri;
    }

    @Override
    public List<VersionedRook> getBooks() throws IOException {
        RepoIgnoreNode ignores = new RepoIgnoreNode(this);
        return client.getBooks(repoUri, ignores);
    }

    @Override
    public VersionedRook retrieveBook(String fileName, File file) throws IOException {
        return client.download(repoUri, fileName, file);
    }

    @Override
    public InputStream openRepoFileInputStream(String fileName) throws IOException {
        return client.streamFile(repoUri, fileName);
    }

    @Override
    public VersionedRook storeBook(File file, String fileName) throws IOException {
        return client.upload(file, repoUri, fileName);
    }

    @Override
    public VersionedRook renameBook(Uri oldFullUri, String newName) throws IOException {
        BookName oldBookName = BookName.fromFileName(BookName.getFileName(repoUri, oldFullUri));
        String newRelativePath = BookName.fileName(newName, oldBookName.getFormat());
        String newEncodedRelativePath = Uri.encode(newRelativePath, "/");
        Uri newFullUri = repoUri.buildUpon().appendEncodedPath(newEncodedRelativePath).build();
        return client.move(repoUri, oldFullUri, newFullUri);
    }

    @Override
    public void delete(Uri uri) throws IOException {
        client.delete(uri.getPath());
    }

    /**
     * Intended for tests. The delete() method does not allow deleting directories.
     */
    public void deleteDirectory(Uri uri) throws IOException {
        client.deleteFolder(uri.getPath());
    }

    @NonNull
    @Override
    public String toString() {
        return repoUri.toString();
    }
}
