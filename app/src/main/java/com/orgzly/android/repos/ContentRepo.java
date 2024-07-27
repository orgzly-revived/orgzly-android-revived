package com.orgzly.android.repos;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.orgzly.BuildConfig;
import com.orgzly.R;
import com.orgzly.android.BookName;
import com.orgzly.android.db.entity.Repo;
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
public class ContentRepo implements SyncRepo {
    private static final String TAG = ContentRepo.class.getName();

    public static final String SCHEME = "content";

    private final long repoId;
    private final Uri repoUri;

    private final Context context;

    private final DocumentFile repoDocumentFile;

    public ContentRepo(RepoWithProps repoWithProps, Context context) {
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

        if (files.size() > 0) {
            for (DocumentFile file : files) {
                if (BookName.isSupportedFormatFileName(file.getName())) {

                    if (BuildConfig.LOG_DEBUG) {
                        LogUtils.d(TAG,
                                "file.getName()", file.getName(),
                                "getUri()", getUri(),
                                "repoDocumentFile.getUri()", repoDocumentFile.getUri(),
                                "file", file,
                                "file.getUri()", file.getUri(),
                                "file.getParentFile()", file.getParentFile().getUri());
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
        RepoIgnoreNode ignores = new RepoIgnoreNode(this);
        directoryNodes.add(repoDocumentFile);
        while (!directoryNodes.isEmpty()) {
            DocumentFile currentDir = directoryNodes.remove(0);
            for (DocumentFile node : currentDir.listFiles()) {
                String relativeFileName = BookName.getFileName(repoUri, node.getUri());
                if (node.isDirectory()) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        if (ignores.isPathIgnored(relativeFileName, true)) {
                            continue;
                        }
                    }
                    directoryNodes.add(node);
                } else {
                    if (Build.VERSION.SDK_INT >= 26) {
                        if (ignores.isPathIgnored(relativeFileName, false)) {
                            continue;
                        }
                    } result.add(node);
                }
            }
        }
        return result;
    }

    private DocumentFile getDocumentFileFromFileName(String fileName) {
        String fullUri = repoDocumentFile.getUri() + Uri.encode("/" + fileName);
        return DocumentFile.fromSingleUri(context, Uri.parse(fullUri));
    }

    @Override
    public VersionedRook retrieveBook(String fileName, File destinationFile) throws IOException {
        DocumentFile sourceFile = getDocumentFileFromFileName(fileName);
        if (sourceFile == null) {
            throw new FileNotFoundException("Book " + fileName + " not found in " + repoUri);
        } else {
            if (BuildConfig.LOG_DEBUG) {
                LogUtils.d(TAG, "Found DocumentFile for " + fileName + ": " + sourceFile.getUri());
            }
        }

        /* "Download" the file. */
        try (InputStream is = context.getContentResolver().openInputStream(sourceFile.getUri())) {
            MiscUtils.writeStreamToFile(is, destinationFile);
        }

        String rev = String.valueOf(sourceFile.lastModified());
        long mtime = sourceFile.lastModified();

        return new VersionedRook(repoId, RepoType.DOCUMENT, repoUri, sourceFile.getUri(), rev, mtime);
    }

    @Override
    public InputStream openRepoFileInputStream(String fileName) throws IOException {
        DocumentFile sourceFile = getDocumentFileFromFileName(fileName);
        if (!sourceFile.exists()) throw new FileNotFoundException();
        return context.getContentResolver().openInputStream(sourceFile.getUri());
    }

    @Override
    public VersionedRook storeBook(File file, String path) throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException("File " + file + " does not exist");
        }
        DocumentFile destinationFile = getDocumentFileFromFileName(path);
        if (path.contains("/")) {
            DocumentFile destinationDir = ensureDirectoryHierarchy(path);
            String fileName = Uri.parse(path).getLastPathSegment();
            if (destinationDir.findFile(fileName) == null) {
                destinationFile = destinationDir.createFile("text/*", fileName);
            }
        } else {
            if (!destinationFile.exists()) {
                repoDocumentFile.createFile("text/*", path);
            }
        }

        try (OutputStream out = context.getContentResolver().openOutputStream(destinationFile.getUri())) {
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
            DocumentFile nextDir = currentDir.findFile(nextDirName);
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
     * moved, but no "abandoned" directories are deleted.
     * @param oldFullUri
     * @param newName
     * @return
     * @throws IOException
     */
    @Override
    public VersionedRook renameBook(Uri oldFullUri, String newName) throws IOException {
        DocumentFile oldDocFile = DocumentFile.fromSingleUri(context, oldFullUri);
        long mtime = oldDocFile.lastModified();
        String rev = String.valueOf(mtime);
        String oldDocFileName = oldDocFile.getName();
        Uri oldDirUri = Uri.parse(
                oldFullUri.toString().replace(
                        Uri.encode("/" + oldDocFile.getName()),
                        ""
                )
        );
        BookName oldBookName = BookName.fromFileName(BookName.getFileName(repoUri, oldFullUri));
        String newRelativePath = BookName.fileName(newName, oldBookName.getFormat());
        String newDocFileName = Uri.parse(newRelativePath).getLastPathSegment();
        DocumentFile newDir;
        Uri newUri = oldFullUri;

        if (newName.contains("/")) {
            newDir = ensureDirectoryHierarchy(newName);
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

    @Override
    public String toString() {
        return getUri().toString();
    }
}
