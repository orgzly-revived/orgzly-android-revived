package com.orgzly.android.repos;

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Remote source of books (such as Dropbox directory, SSH directory, etc.)
 */
public interface SyncRepo {
    boolean isConnectionRequired();

    boolean isAutoSyncSupported();

    /**
     * Unique URL.
     */
    Uri getUri();

    /**
     * Retrieve the list of all available books.
     *
     * @return array of all available books
     * @throws IOException
     */
    List<VersionedRook> getBooks() throws IOException;

    /**
     * Download the latest available revision of the book and store its content to {@code File}.
     */
    VersionedRook retrieveBook(String fileName, File destination) throws IOException;

    /**
     * Open a file in the repository for reading. Originally added for parsing the .orgzlyignore
     * file.
     * @param fileName The file to open
     * @throws IOException
     */
    InputStream openRepoFileInputStream(String fileName) throws IOException;

    /**
     * Uploads book storing it under given filename under repo's url.
     * @param file The contents of this file should be stored at the remote location/repo
     * @param fileName The contents ({@code file}) should be stored under this name
     */
    VersionedRook storeBook(File file, String fileName) throws IOException;

    VersionedRook renameBook(Uri from, String name) throws IOException;

    // VersionedRook moveBook(Uri from, Uri uri) throws IOException;

    void delete(Uri uri) throws IOException;

    String toString();
}
