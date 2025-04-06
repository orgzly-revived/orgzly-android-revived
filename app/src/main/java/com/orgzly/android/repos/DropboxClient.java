package com.orgzly.android.repos;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.json.JsonReadException;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.LookupError;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.RelocationResult;
import com.dropbox.core.v2.files.WriteMode;
import com.orgzly.BuildConfig;
import com.orgzly.android.BookName;
import com.orgzly.android.prefs.AppPreferences;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DropboxClient {
    private static final String TAG = DropboxClient.class.getName();

    private static final long UPLOAD_FILE_SIZE_LIMIT = 150; // MB

    // TODO: Throw DropboxNotLinked etc. instead and let the client get message from resources
    private static final String NOT_LINKED = "Not linked to Dropbox";
    private static final String LARGE_FILE = "File larger then " + UPLOAD_FILE_SIZE_LIMIT + " MB";

    /* The empty string ("") represents the root folder in Dropbox API v2. */
    private static final String ROOT_PATH = "";

    final private Context mContext;
    final private long repoId;
    final private DbxRequestConfig requestConfig;
    private DbxCredential credential;
    private DbxClientV2 dbxClient;

    private boolean tryLinking = false;

    public DropboxClient(Context context, long id) {
        mContext = context;

        repoId = id;

        requestConfig = getRequestConfig();

        createClient();
    }

    private DbxRequestConfig getRequestConfig() {
        String userLocale = Locale.getDefault().toString();
        String clientId = String.format("%s/%s",
                BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME);
        return DbxRequestConfig
                .newBuilder(clientId)
                .withUserLocale(userLocale)
                .build();
    }

    private void createClient() {
        String serializedCredential = AppPreferences.dropboxSerializedCredential(mContext);
        if (serializedCredential != null && serializedCredential.length() > 0) {
            try {
                credential =
                        DbxCredential.Reader.readFully(serializedCredential);
            } catch (JsonReadException e) {
                throw new RuntimeException(e);
            }
            dbxClient = new DbxClientV2(requestConfig, credential);
        }
    }

    public boolean isLinked() {
        return dbxClient != null;
    }

    private void linkedOrThrow() throws IOException {
        if (! isLinked()) {
            throw new IOException(NOT_LINKED);
        }
    }

    public void unlink() {
        dbxClient = null;
        deleteCredential();
        tryLinking = false;
    }

    public void beginAuthentication(Activity activity) {
        tryLinking = true;
        Auth.startOAuth2PKCE(activity, BuildConfig.DROPBOX_APP_KEY, requestConfig);
    }

    public boolean finishAuthentication() {
        if (dbxClient == null && tryLinking) {
            credential = Auth.getDbxCredential();
            if (credential != null) {
                saveCredential();
                createClient();
                return true;
            }
        }
        return false;
    }

    private void saveCredential() {
        AppPreferences.dropboxSerializedCredential(mContext, credential.toString());
    }

    private void deleteCredential() {
        AppPreferences.dropboxSerializedCredential(mContext, null);
    }

    public List<VersionedRook> getBooks(Uri repoUri, RepoIgnoreNode ignores) throws IOException {
        linkedOrThrow();

        List<VersionedRook> list = new ArrayList<>();

        String path = repoUri.getPath();

        /* Fix root path. */
        if (path == null || path.equals("/")) {
            path = ROOT_PATH;
        }

        /* Strip trailing slashes. */
        path = path.replaceAll("/+$", "");

        List<String> folderPaths = new ArrayList<>(List.of(path));

        try {
            if (ROOT_PATH.equals(path) || dbxClient.files().getMetadata(path) instanceof FolderMetadata) {
                /* Get folder content. */
                while (folderPaths.size() > 0) {
                    ListFolderResult result = dbxClient.files().listFolder(folderPaths.remove(0));
                    while (true) {
                        for (Metadata metadata : result.getEntries()) {
                            String pathRelativeToRepoRoot =
                                    metadata.getPathDisplay().replaceAll("^" + path + "/", "");
                            if (metadata instanceof FileMetadata) {
                                FileMetadata file = (FileMetadata) metadata;

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    if (ignores.isPathIgnored(pathRelativeToRepoRoot, false)) {
                                        continue;
                                    }
                                }

                                if (BookName.isSupportedFormatFileName(file.getName())) {
                                    String encodedRelativePath = Uri.encode(pathRelativeToRepoRoot, "/");
                                    Uri uri = repoUri.buildUpon().appendEncodedPath(encodedRelativePath).build();
                                    VersionedRook book = new VersionedRook(
                                            repoId,
                                            RepoType.DROPBOX,
                                            repoUri,
                                            uri,
                                            file.getRev(),
                                            file.getServerModified().getTime());

                                    list.add(book);
                                }
                            }
                            if (metadata instanceof FolderMetadata && AppPreferences.subfolderSupport(mContext)) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    if (ignores.isPathIgnored(pathRelativeToRepoRoot, true)) {
                                        continue;
                                    }
                                }
                                folderPaths.add(metadata.getPathDisplay());
                            }
                        }

                        if (!result.getHasMore()) {
                            break;
                        }

                        result = dbxClient.files().listFolderContinue(result.getCursor());
                    }
                }

            } else {
                throw new IOException("Not a directory: " + repoUri);
            }

        } catch (DbxException e) {
            e.printStackTrace();

            /* If we get NOT_FOUND from Dropbox, just return the empty list. */
            if (e instanceof GetMetadataErrorException) {
                if (((GetMetadataErrorException) e).errorValue.getPathValue() == LookupError.NOT_FOUND) {
                    return list;
                }
            }

            throw new IOException("Failed getting the list of files in " + repoUri +
                                  " listing " + path + ": " +
                                  (e.getMessage() != null ? e.getMessage() : e.toString()));
        }

        return list;
    }

    private Uri getFullUriFromRelativePath(Uri repoUri, String repoRelativePath) {
        String encodedPath = Uri.encode(repoRelativePath, "/");
        return Uri.withAppendedPath(repoUri, encodedPath);
    }

    /**
     * Download file from Dropbox and store it to a local file.
     */
    public VersionedRook download(Uri repoUri, Uri uri, File localFile) throws IOException {
        linkedOrThrow();

        OutputStream out = new BufferedOutputStream(new FileOutputStream(localFile));

        try {
            Metadata pathMetadata = dbxClient.files().getMetadata(uri.getPath());

            if (pathMetadata instanceof FileMetadata) {
                FileMetadata metadata = (FileMetadata) pathMetadata;

                String rev = metadata.getRev();
                long mtime = metadata.getServerModified().getTime();

                dbxClient.files().download(metadata.getPathLower(), rev).download(out);

                return new VersionedRook(repoId, RepoType.DROPBOX, repoUri, uri, rev, mtime);

            } else {
                throw new IOException("Failed downloading Dropbox file " + uri + ": Not a file");
            }

        } catch (DbxException e) {
            if (e.getMessage() != null) {
                throw new IOException("Failed downloading Dropbox file " + uri + ": " + e.getMessage());
            } else {
                throw new IOException("Failed downloading Dropbox file " + uri + ": " + e.toString());
            }
        } finally {
            out.close();
        }
    }

    public InputStream streamFile(Uri repoUri, String repoRelativePath) throws IOException {
        linkedOrThrow();

        Uri uri = repoUri.buildUpon().appendPath(repoRelativePath).build();
        FileMetadata metadata;
        String rev;
        DbxDownloader<FileMetadata> downloader;

        try {
            Metadata pathMetadata = dbxClient.files().getMetadata(uri.getPath());
            metadata = (FileMetadata) pathMetadata;
            rev = metadata.getRev();
            downloader = dbxClient.files().download(metadata.getPathLower(), rev);
        } catch (DbxException e) {
            if (e instanceof GetMetadataErrorException) {
                if (((GetMetadataErrorException) e).errorValue.getPathValue() == LookupError.NOT_FOUND) {
                    throw new FileNotFoundException();
                }
            }
            throw new RuntimeException(e);
        }
        return downloader.getInputStream();
    }

    /** Upload file to Dropbox. */
    public VersionedRook upload(File file, Uri repoUri, String relativePath) throws IOException {
        linkedOrThrow();

        Uri bookUri = getFullUriFromRelativePath(repoUri, relativePath);

        if (file.length() > UPLOAD_FILE_SIZE_LIMIT * 1024 * 1024) {
            throw new IOException(LARGE_FILE);
        }

        FileMetadata metadata;
        InputStream in = new FileInputStream(file);

        try {
            metadata = dbxClient.files()
                    .uploadBuilder(bookUri.getPath())
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(in);

        } catch (DbxException e) {
            if (e.getMessage() != null) {
                throw new IOException("Failed overwriting " + bookUri.getPath() + " on Dropbox: " + e.getMessage());
            } else {
                throw new IOException("Failed overwriting " + bookUri.getPath() + " on Dropbox: " + e.toString());
            }
        }

        String rev = metadata.getRev();
        long mtime = metadata.getServerModified().getTime();

        return new VersionedRook(repoId, RepoType.DROPBOX, repoUri, bookUri, rev, mtime);
    }

    public void delete(String path) throws IOException {
        linkedOrThrow();

        try {
            if (dbxClient.files().getMetadata(path) instanceof FileMetadata) {
                dbxClient.files().deleteV2(path);
            } else {
                throw new IOException("Not a file: " + path);
            }

        } catch (DbxException e) {
            e.printStackTrace();

            if (e.getMessage() != null) {
                throw new IOException("Failed deleting " + path + " on Dropbox: " + e.getMessage());
            } else {
                throw new IOException("Failed deleting " + path + " on Dropbox: " + e.toString());
            }
        }
    }

    public VersionedRook move(Uri repoUri, Uri from, Uri to) throws IOException {
        linkedOrThrow();

        /* Abort if destination file already exists. */
        try {
            if (dbxClient.files().getMetadata(to.getPath()) instanceof FileMetadata)
                throw new IOException("File at " + to.getPath() + " already exists");
        } catch (DbxException ignored) {}

        try {
            RelocationResult relocationRes = dbxClient.files().moveV2(from.getPath(), to.getPath());
            Metadata metadata = relocationRes.getMetadata();

            if (! (metadata instanceof FileMetadata)) {
                throw new IOException("Relocated object not a file?");
            }

            FileMetadata fileMetadata = (FileMetadata) metadata;

            String rev = fileMetadata.getRev();
            long mtime = fileMetadata.getServerModified().getTime();

            return new VersionedRook(repoId, RepoType.DROPBOX, repoUri, to, rev, mtime);

        } catch (Exception e) {
            e.printStackTrace();

            if (e.getMessage() != null) { // TODO: Move this throwing to utils
                throw new IOException("Failed moving " + from + " to " + to + ": " + e.getMessage(), e);
            } else {
                throw new IOException("Failed moving " + from + " to " + to + ": " + e.toString(), e);
            }
        }
    }

    public void deleteFolder(String path) throws IOException {
        linkedOrThrow();

        try {
            if (dbxClient.files().getMetadata(path) instanceof FolderMetadata) {
                dbxClient.files().deleteV2(path);
            } else {
                throw new IOException("Not a directory: " + path);
            }
        } catch (GetMetadataErrorException e) {
            // Do nothing; the folder does not exist.
        } catch (DbxException e) {
            e.printStackTrace();
            if (e.getMessage() != null) {
                throw new IOException("Failed deleting " + path + " on Dropbox: " + e.getMessage());
            } else {
                throw new IOException("Failed deleting " + path + " on Dropbox: " + e);
            }
        }
    }
}
