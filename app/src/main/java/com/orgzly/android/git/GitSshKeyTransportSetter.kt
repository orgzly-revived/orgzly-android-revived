package com.orgzly.android.git

import android.os.Build
import androidx.annotation.RequiresApi
import com.orgzly.android.App
import org.eclipse.jgit.annotations.NonNull
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.internal.transport.sshd.OpenSshServerKeyDatabase
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import java.io.File
import java.security.KeyPair

class GitSshKeyTransportSetter: GitTransportSetter {
    private val configCallback: TransportConfigCallback
    private val context = App.getAppContext()

    init {
        val factory: SshSessionFactory = object : SshdSessionFactory(null, null) {

            override fun getHomeDirectory(): File { return context.filesDir }

            override fun getDefaultPreferredAuthentications(): String { return "publickey" }

            override fun createServerKeyDatabase(
                @NonNull homeDir: File,
                @NonNull sshDir: File
            ): ServerKeyDatabase {
                // We override this method because we want to set "askAboutNewFile" to False.
                return OpenSshServerKeyDatabase(
                    false,
                    getDefaultKnownHostsFiles(sshDir)
                )
            }

            @RequiresApi(Build.VERSION_CODES.N)
            override fun getDefaultKeys(@NonNull sshDir: File): Iterable<KeyPair>? {
                return if (SshKey.exists) {
                    listOf(SshKey.getKeyPair())
                } else {
                    SshKey.promptForKeyGeneration()
                    null
                }
            }
        }

        SshSessionFactory.setInstance(factory)

        // org.apache.sshd.common.config.keys.IdentityUtils freaks out if user.home is not set
        System.setProperty("user.home", context.filesDir.toString())

        configCallback = TransportConfigCallback { transport: Transport ->
            val sshTransport = transport as SshTransport
            sshTransport.sshSessionFactory = factory
        }
    }

    override fun setTransport(tc: TransportCommand<*, *>): TransportCommand<*, *> {
        tc.setTransportConfigCallback(configCallback)
        tc.setCredentialsProvider(SshCredentialsProvider())
        return tc
    }

}
