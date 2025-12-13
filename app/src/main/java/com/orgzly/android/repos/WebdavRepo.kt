package com.orgzly.android.repos

import android.net.Uri
import android.os.Build
import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.BookName
import com.orgzly.android.ui.note.NoteAttachmentData
import com.orgzly.android.util.UriUtils
import com.orgzly.android.prefs.AppPreferences
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import okhttp3.OkHttpClient
import okio.Buffer
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


class WebdavRepo(
        private val repoId: Long,
        private val uri: Uri,
        private val username: String,
        private val password: String,
        private val certificates: String? = null
) : SyncRepo {

    private val sardine by lazy {
        OkHttpSardine(okHttpClient())
    }

    private fun okHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()

        // Use certificate if specified
        if (!certificates.isNullOrEmpty()) {
            val trustManager = trustManagerForCertificates(certificates)

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), null)
            }

            val sslSocketFactory = sslContext.socketFactory

            builder.sslSocketFactory(sslSocketFactory, trustManager)
        }

        val credentials = Credentials(username, password)

        val basicAuthenticator = BasicAuthenticator(credentials)
        val digestAuthenticator = DigestAuthenticator(credentials)

        val authenticator = DispatchingAuthenticator.Builder()
            .with("digest", digestAuthenticator)
            .with("basic", basicAuthenticator)
            .build()

        val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()

        builder.authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
        builder.addInterceptor(AuthenticationCacheInterceptor(authCache))

        // Double the values as some users are seeing timeouts.
        // Make configurable if needed (https://github.com/orgzly/orgzly-android/issues/870).
        builder.connectTimeout(20, TimeUnit.SECONDS)
        builder.readTimeout(20, TimeUnit.SECONDS)
        builder.writeTimeout(20, TimeUnit.SECONDS)

        return builder.build()
    }

    private fun trustManagerForCertificates(str: String): X509TrustManager {
        // Read certificates
        val certificates = Buffer().writeUtf8(str).inputStream().use { stream ->
            CertificateFactory.getInstance("X.509").generateCertificates(stream)
        }

//        require(!certificates.isEmpty()) {
//            "Expected non-empty set of trusted certificates"
//        }

        // Create new key store
        val password = "password".toCharArray() // Any password will work
        val keyStore = newEmptyKeyStore(password)
        for ((index, certificate) in certificates.withIndex()) {
            val certificateAlias = index.toString()
            keyStore.setCertificateEntry(certificateAlias, certificate)
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        ).apply {
            init(keyStore)
        }

        val trustManagers = trustManagerFactory.trustManagers
        check(trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
            "Unexpected default trust managers: ${Arrays.toString(trustManagers)}"
        }

        return trustManagers[0] as X509TrustManager
    }

    private fun newEmptyKeyStore(password: CharArray): KeyStore {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        val inputStream: InputStream? = null // By convention, 'null' creates an empty key store.
        keyStore.load(inputStream, password)
        return keyStore
    }

    companion object {
        const val USERNAME_PREF_KEY = "username"
        const val PASSWORD_PREF_KEY = "password"
        const val CERTIFICATES_PREF_KEY = "certificates"

        fun getInstance(repoWithProps: RepoWithProps): WebdavRepo {
            val id = repoWithProps.repo.id

            val uri = Uri.parse(repoWithProps.repo.url)

            val username = checkNotNull(repoWithProps.props[USERNAME_PREF_KEY]) {
                "Username not found"
            }.toString()

            val password = checkNotNull(repoWithProps.props[PASSWORD_PREF_KEY]) {
                "Password not found"
            }.toString()

            val certificates = repoWithProps.props[CERTIFICATES_PREF_KEY]

            return WebdavRepo(id, uri, username, password, certificates)
        }
    }

    override fun isConnectionRequired(): Boolean {
        return true
    }

    override fun isAutoSyncSupported(): Boolean {
        return true
    }

    override fun getUri(): Uri {
        return uri
    }

    override fun getBooks(): MutableList<VersionedRook> {
        val url = uri.toUrl()

        if (!sardine.exists(url)) {
            sardine.createDirectory(url)
        }

        val ignores = RepoIgnoreNode(this)

        val listDepth = if (AppPreferences.subfolderSupport(App.getAppContext())) {
            -1
        } else {
            1
        }

        return sardine
                .list(url, listDepth)
                .mapNotNull {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (!BookName.isSupportedFormatFileName(it.name) || ignores.isPathIgnored(it.getRelativePath(), it.isDirectory)) {
                            null
                        } else {
                            it.toVersionedRook()
                        }
                    } else {
                        if (!BookName.isSupportedFormatFileName(it.name)) {
                            null
                        } else {
                            it.toVersionedRook()
                        }
                    }
                }
                .toMutableList()
    }

    override fun retrieveBook(repoRelativePath: String?, destination: File?): VersionedRook {
        val fileUrl = Uri.withAppendedPath(uri, repoRelativePath).toUrl()

        sardine.get(fileUrl).use { inputStream ->
            FileOutputStream(destination).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return sardine.list(fileUrl).first().toVersionedRook()
    }

    override fun openRepoFileInputStream(repoRelativePath: String): InputStream {
        val fileUrl = Uri.withAppendedPath(uri, repoRelativePath).toUrl()
        if (!sardine.exists(fileUrl))
            throw FileNotFoundException()
        return sardine.get(fileUrl)
     }

    private fun ensureDirectoryHierarchy(relativePath: String) {
        val levels: ArrayList<String> = ArrayList(relativePath.split("/"))
        // N.B. Strip off trailing slash from repo URL, if present
        var currentDir: String = uri.toString().replace(Regex("/$"), "")
        while (levels.size > 1) {
            val nextDirName: String = levels.removeAt(0)
            currentDir = "$currentDir/$nextDirName"
            if (!sardine.exists(currentDir)) {
                sardine.createDirectory(currentDir)
            }
        }
    }

    override fun storeBook(file: File, repoRelativePath: String): VersionedRook {
        val encodedRepoPath = Uri.encode(repoRelativePath, "/")
        if (encodedRepoPath != null) {
            if (encodedRepoPath.contains("/")) {
                val context = App.getAppContext()
                if (!AppPreferences.subfolderSupport(context))
                    throw IOException(context.getString(R.string.subfolder_support_disabled))
                ensureDirectoryHierarchy(encodedRepoPath)
            }
        }
        val fileUrl = uri.buildUpon().appendEncodedPath(encodedRepoPath).build().toUrl()

        sardine.put(fileUrl, file, null)

        return sardine.list(fileUrl).first().toVersionedRook()
    }

    override fun storeFile(file: File?, pathInRepo: String?, fileName: String?): VersionedRook {
        if (file == null || !file.exists()) {
            throw FileNotFoundException("File $file does not exist")
        }

        val folderUri = Uri.withAppendedPath(uri, pathInRepo)
        val fileUrl = Uri.withAppendedPath(folderUri, fileName).toUrl()

        createRecursive(uri.toUrl(), pathInRepo!!)

        sardine.put(fileUrl, file, null)

        return sardine.list(fileUrl).first().toVersionedRook()
    }

    override fun listFilesInPath(pathInRepo: String?): MutableList<NoteAttachmentData> {
        TODO("Not yet implemented")
    }

    private fun createRecursive(parent: String, path: String): String {
        if ("." == path || "" == path) {
            return parent
        }
        val l = path.lastIndexOf('/')
        val p = if (l >= 0) {
            createRecursive(parent, path.substring(0, l))
        } else {
            parent
        }
        val subdir = path.substring(l + 1)
        val folder = p + "/" + subdir
        if (!sardine.exists(folder)) {
            sardine.createDirectory(folder)
        }
        return folder
    }

    override fun renameBook(oldFullUri: Uri, newName: String): VersionedRook {
        val oldBookName = BookName.fromRepoRelativePath(BookName.getRepoRelativePath(uri, oldFullUri))
        val newRelativePath = BookName.repoRelativePath(newName, oldBookName.format)
        val newEncodedRelativePath = Uri.encode(newRelativePath, "/")
        val newFullUrl = uri.buildUpon().appendEncodedPath(newEncodedRelativePath).build().toUrl()

        /* Abort if destination file already exists. */
        if (sardine.exists(newFullUrl)) {
            throw IOException("File at $newFullUrl already exists")
        }

        if (newName.contains("/")) {
            val context = App.getAppContext()
            if (!AppPreferences.subfolderSupport(context))
                throw IOException(context.getString(R.string.subfolder_support_disabled))
            ensureDirectoryHierarchy(newEncodedRelativePath)
        }

        sardine.move(oldFullUri.toUrl(), newFullUrl)
        return sardine.list(newFullUrl).first().toVersionedRook()
    }

    override fun delete(uri: Uri) {
        sardine.delete(uri.toUrl())
    }

    private fun DavResource.toVersionedRook(): VersionedRook {
        return VersionedRook(
                repoId,
                RepoType.WEBDAV,
                uri,
                Uri.parse(this.getFullUrlString()),
                this.modified.time.toString(),
                this.modified.time
        )
    }

    /**
     * A WebDAV href can be either an absolute (full) URI, or an "absolute path"
     * (cf. http://www.webdav.org/specs/rfc4918.html#url-handling). The full URI can be built from
     * the absolute path.
     */
    private fun DavResource.getFullUrlString(): String {
        if (this.href.isAbsolute) {
            // absolute-URI - return the href as-is
            return this.href.toString()
        } else {
            // path-absolute - build the absolut URI
            return uri.scheme + "://" + uri.authority + this.href.toString()
        }
    }

    private fun DavResource.getRelativePath(): String {
        val absoluteUri = URI.create(this.getFullUrlString())
        val relativePath = URI.create(uri.toString()).relativize(absoluteUri)
        return relativePath.path
    }

    private fun Uri.toUrl(): String {
        return this.toString().replace("^(?:web)?dav(s?://)".toRegex(), "http$1")
    }
}