package com.orgzly.android.repos;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.orgzly.BuildConfig;
import com.orgzly.R;
import com.orgzly.android.BookName;
import com.orgzly.android.db.entity.Repo;
import com.orgzly.android.prefs.AppPreferences;
import com.orgzly.android.util.LogUtils;
import com.orgzly.android.util.MiscUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Using DocumentFile, for devices running Lollipop or later.
 */
public class DocumentRepo implements SyncRepo {
    private static final String TAG = DocumentRepo.class.getName();

    public static final String SCHEME = "content";

    private final long repoId;
    private final Uri repoUri;

    private final Context context;

    private final DocumentFile repoDocumentFile;

    public DocumentRepo(RepoWithProps repoWithProps, Context context) {
        Repo repo = repoWithProps.getRepo();

        this.repoId = repo.getId();
        this.repoUri = Uri.parse(repo.getUrl());

        this.context = context;

        this.repoDocumentFile = DocumentFile.fromTreeUri(context, repoUri);
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
    public List<VersionedRook> getBooks() throws IOException {
        List<VersionedRook> result = new ArrayList<>();
        List<DocumentFile> files = walkFileTree();
        if (!files.isEmpty()) {
            for (DocumentFile file : files) {
                if (BuildConfig.LOG_DEBUG) {
                    LogUtils.d(TAG,
                            "file.getName()", file.getName(),
                            "getUri()", getUri(),
                            "repoDocumentFile.getUri()", repoDocumentFile.getUri(),
                            "file", file,
                            "file.getUri()", file.getUri(),
                            "file.getParentFile()", Objects.requireNonNull(file.getParentFile()).getUri());
                }
                result.add(new VersionedRook(
                        repoId,
                        RepoType.DOCUMENT,
                        getUri(),
                        file.getUri(),
                        String.valueOf(file.lastModified()),
                        file.lastModified()
                ));
            }
        } else {
            Log.e(TAG, "Listing files in " + getUri() + " returned null.");
        }
        return result;
    }

    /**
     * @return All file nodes in the repo tree which are not excluded by .orgzlyignore
     */
    private List<DocumentFile> walkFileTree() {
        List<DocumentFile> result = new ArrayList<>();
        List<DocumentFile> directoryNodes = new ArrayList<>();
        directoryNodes.add(repoDocumentFile);
        RepoIgnoreNode ignoreNode = new RepoIgnoreNode(this);
        StringBuilder repoRelativePath = new StringBuilder();
        while (!directoryNodes.isEmpty()) {
            DocumentFile currentDir = directoryNodes.remove(0);
            DocumentFile parentDir = currentDir.getParentFile();
            if (parentDir != null) {
                if (parentDir == repoDocumentFile) {
                    repoRelativePath = new StringBuilder(Objects.requireNonNull(currentDir.getName()));
                } else {
                    repoRelativePath.append("/").append(currentDir.getName());
                }
            }
            for (DocumentFile node : currentDir.listFiles()) {
                if (node.isDirectory() && AppPreferences.subfolderSupport(context)) {
                    // Avoid descending into completely ignored directories
                    if (Build.VERSION.SDK_INT >= 26) {
                        if (!ignoreNode.isPathIgnored(repoRelativePath.toString(), true)) {
                            directoryNodes.add(node);
                        }
                    } else {
                        directoryNodes.add(node);
                    }
                } else {
                    if (BookName.isSupportedFormatFileName(node.getName())) {
                        // Check for matching ignore rules
                        if (Build.VERSION.SDK_INT >= 26) {
                            repoRelativePath.append("/").append(node.getName());
                            if (ignoreNode.isPathIgnored(repoRelativePath.toString(), false))
                                continue;
                        }
                        result.add(node);
                    }
                }
            }
        }
        return result;
    }

    private DocumentFile getDocumentFileFromPath(String path) {
        String fullUri = repoDocumentFile.getUri() + Uri.encode("/" + path);
        return DocumentFile.fromSingleUri(context, Uri.parse(fullUri));
    }

    @Override
    public VersionedRook retrieveBook(Uri uri, File destinationFile) throws IOException {
        DocumentFile sourceFile = DocumentFile.fromSingleUri(context, uri);
        assert sourceFile != null;
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("File " + sourceFile.getUri() + " not found in " + repoUri);
        } else {
            if (BuildConfig.LOG_DEBUG) {
                LogUtils.d(TAG, "Found DocumentFile for " + sourceFile.getUri());
            }
        }

        /* "Download" the file. */
        try (InputStream is = context.getContentResolver().openInputStream(sourceFile.getUri())) {
            assert is != null;
            MiscUtils.writeStreamToFile(is, destinationFile);
        }

        String rev = String.valueOf(sourceFile.lastModified());
        long mtime = sourceFile.lastModified();

        return new VersionedRook(repoId, RepoType.DOCUMENT, repoUri, sourceFile.getUri(), rev, mtime);
    }

    @Override
    public InputStream openRepoFileInputStream(String repoRelativePath) throws IOException {
        DocumentFile sourceFile = getDocumentFileFromPath(repoRelativePath);
        if (!sourceFile.exists()) throw new FileNotFoundException();
        return context.getContentResolver().openInputStream(sourceFile.getUri());
    }

    @Override
    public VersionedRook storeBook(File file, String repoRelativePath) throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("File " + file + " does not exist");
        }
        DocumentFile destinationDir = repoDocumentFile;
        if (repoRelativePath.contains("/")) {
            if (AppPreferences.subfolderSupport(context)) {
                destinationDir = ensureDirectoryHierarchy(repoRelativePath);
            } else {
                throw new IOException(context.getString(R.string.subfolder_support_disabled));
            }
        }
        String destinationFileName = Uri.parse(repoRelativePath).getLastPathSegment();
        assert destinationFileName != null;
        DocumentFile destinationFile = destinationDir.findFile(destinationFileName);
        if (destinationFile == null) {
            destinationFile = destinationDir.createFile("text/*", destinationFileName);
        }

        Uri destinationFileUri = Objects.requireNonNull(destinationFile).getUri();
        try (OutputStream out = context.getContentResolver().openOutputStream(destinationFileUri, "wt")) {
            MiscUtils.writeFileToStream(file, out);
        }

        long mtime = destinationFile.lastModified();
        String rev = String.valueOf(mtime);

        return new VersionedRook(repoId, RepoType.DOCUMENT, getUri(), destinationFile.getUri(), rev, mtime);
    }

    /**
     * Given a relative path, ensures that all directory levels are created unless they already
     * exist.
     * @param relativePath Path relative to the repository root directory
     * @return The DocumentFile object of the leaf directory where the file should be placed.
     */
    private DocumentFile ensureDirectoryHierarchy(String relativePath) {
        List<String> levels = new ArrayList<>(Arrays.asList(relativePath.split("/")));
        DocumentFile currentDir = repoDocumentFile;
        while (levels.size() > 1) {
            String nextDirName = levels.remove(0);
            DocumentFile nextDir = Objects.requireNonNull(currentDir).findFile(nextDirName);
            if (nextDir == null) {
                currentDir = currentDir.createDirectory(nextDirName);
            } else {
                currentDir = nextDir;
            }
        }
        return currentDir;
    }

    /**
     * Allows renaming a notebook to any subdirectory (indicated with a "/"), ensuring that all
     * required subdirectories are created, if they do not already exist. Note that the file is
     * moved, but any emptied directories are not deleted.
     * @param oldFullUri Original URI
     * @param newName The user's chosen display name
     * @return a VersionedRook representation of the new file
     * @throws IOException
     */
    @Override
    public VersionedRook renameBook(Uri oldFullUri, String newName) throws IOException {
        DocumentFile oldDocFile = Objects.requireNonNull(DocumentFile.fromSingleUri(context, oldFullUri));
        long mtime = oldDocFile.lastModified();
        String rev = String.valueOf(mtime);
        String oldDocFileName = oldDocFile.getName();
        Uri oldDirUri = Uri.parse(
                oldFullUri.toString().replace(
                        Uri.encode("/" + oldDocFile.getName()),
                        ""
                )
        );
        BookName oldBookName = BookName.fromRepoRelativePath(BookName.getRepoRelativePath(repoUri, oldFullUri));
        String newRelativePath = BookName.repoRelativePath(newName, oldBookName.getFormat());
        String newDocFileName = Objects.requireNonNull(Uri.parse(newRelativePath).getLastPathSegment());
        DocumentFile newDir;
        Uri newUri = oldFullUri;

        if (newName.contains("/")) {
            if (AppPreferences.subfolderSupport(context)) {
                newDir = ensureDirectoryHierarchy(newName);
            } else {
                throw new IOException(context.getString(R.string.subfolder_support_disabled));
            }
        } else {
            newDir = repoDocumentFile;
        }

        /* Abort if destination file already exists. */
        DocumentFile existingFile = newDir.findFile(newDocFileName);
        if (existingFile != null) {
            throw new IOException("File at " + existingFile.getUri() + " already exists");
        }

        if (!newDir.getUri().toString().equals(oldDirUri.toString())) {
            // File should be moved to a different directory
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                newUri = DocumentsContract.moveDocument(
                        context.getContentResolver(), oldFullUri,
                        oldDirUri,
                        newDir.getUri()
                );
                assert newUri != null;
            } else {
                throw new IllegalArgumentException(
                        context.getString(R.string.moving_between_subdirectories_requires_api_24));
            }
        }

        if (!Objects.equals(newDocFileName, oldDocFileName)) {
            // File should be renamed
            newUri = DocumentsContract.renameDocument(
                    context.getContentResolver(),
                    newUri,
                    newDocFileName
            );
            assert newUri != null;
        }

        return new VersionedRook(repoId, RepoType.DOCUMENT, repoUri, newUri, rev, mtime);
    }

    @Override
    public void delete(Uri uri) throws IOException {
        DocumentFile docFile = DocumentFile.fromSingleUri(context, uri);

        if (docFile != null && docFile.exists()) {
            if (! docFile.delete()) {
                throw new IOException("Failed deleting document " + uri);
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return getUri().toString();
    }
}
