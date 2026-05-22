package com.orgzly.android.repos;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.orgzly.android.ui.note.NoteAttachmentData;

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
    VersionedRook retrieveBook(String repoRelativePath, File destination) throws IOException;

    /**
     * Open a file in the repository for reading. Originally added for parsing the .orgzlyignore
     * file.
     * @param repoRelativePath The file to open
     * @throws IOException
     */
    InputStream openRepoFileInputStream(String repoRelativePath) throws IOException;

    /**
     * Uploads book storing it under given filename under repo's url.
     * @param file The contents of this file should be stored at the remote location/repo
     * @param repoRelativePath The contents ({@code file}) should be stored under this
     *                       (non-encoded) name
     */
    VersionedRook storeBook(File file, String repoRelativePath) throws IOException;

    /**
     * Returns a Uri specifically for the given path within the repository.
     */
    Uri getUriForPath(String path);

    /**
     * Uploads file storing it under directory (pathInRepo) under repo's url.
     * @param file The contents of this file should be stored at the remote location/repo
     * @param pathInRepo The "/" separated path within the remote location/repo, create it if it doesn't exist
     * @param fileName The contents ({@code file}) should be stored under this name
     * @return {@code VersionedRook}
     * @throws IOException
     */
    VersionedRook storeFile(File file, String pathInRepo, String fileName) throws IOException;

    /**
     * Lists the files under directory (pathInRepo) under repo's url.
     * @param pathInRepo
     * @return list of {@link NoteAttachmentData}
     */
    List<NoteAttachmentData> listFilesInPath(String pathInRepo);

    /**
     *
     * @param oldFullUri Uri of the original repository file
     * @param newName The new desired book name
     * @return
     * @throws IOException
     */
    VersionedRook renameBook(Uri oldFullUri, String newName) throws IOException;

    // VersionedRook moveBook(Uri from, Uri uri) throws IOException;

    void delete(Uri uri) throws IOException;

    @NonNull
    String toString();
}
