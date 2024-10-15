# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the ProGuard
# include property in project.properties.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}


# Keep line numbers and file names
-keepattributes SourceFile,LineNumberTable

# Dropbox SDK Serialization

-keepattributes *Annotation*,EnclosingMethod,InnerClasses,Signature
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# Dropbox SSL trusted certs

-adaptresourcefilenames com/dropbox/core/http/trusted-certs.raw

# OkHttp and Servlet optional dependencies

-dontwarn okio.**
-dontwarn com.squareup.okhttp.**
-dontwarn javax.servlet.**
-dontwarn com.dropbox.core.http.GoogleAppEngineRequestor
-dontwarn com.dropbox.core.http.OkHttp3Requestor*
-dontwarn com.dropbox.core.http.GoogleAppEngineRequestor$Uploader
-dontwarn com.dropbox.core.http.GoogleAppEngineRequestor$FetchServiceUploader

# Support classes for compatibility with older API versions

-dontwarn android.support.**
-dontnote android.support.**

-dontwarn org.joda.convert.**

-dontwarn com.jcraft.**
-dontwarn org.slf4j.**

-keepclassmembers enum com.orgzly.android.ui.refile.RefileLocation$Type { *; }

-keep public class org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar
-keep public class org.apache.sshd.common.util.security.eddsa.EdDSASecurityProviderRegistrar
-keep public class net.i2p.crypto.eddsa.**

-keepnames public class * extends org.eclipse.jgit.nls.TranslationBundle
-keepclassmembers class * extends org.eclipse.jgit.nls.TranslationBundle { *; }

# Added when upgrading to AGP 8
-keep public class org.apache.sshd.common.**
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn javax.management.InstanceAlreadyExistsException
-dontwarn javax.management.InstanceNotFoundException
-dontwarn javax.management.JMException
-dontwarn javax.management.MBeanException
-dontwarn javax.management.MBeanRegistrationException
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.MalformedObjectNameException
-dontwarn javax.management.NotCompliantMBeanException
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn javax.management.ReflectionException
-dontwarn javax.security.auth.login.CredentialException
-dontwarn javax.security.auth.login.FailedLoginException
-dontwarn org.apache.sshd.sftp.SftpModuleProperties
-dontwarn org.apache.sshd.sftp.client.SftpClient$Attributes
-dontwarn org.apache.sshd.sftp.client.SftpClient$CloseableHandle
-dontwarn org.apache.sshd.sftp.client.SftpClient$CopyMode
-dontwarn org.apache.sshd.sftp.client.SftpClient$DirEntry
-dontwarn org.apache.sshd.sftp.client.SftpClient$Handle
-dontwarn org.apache.sshd.sftp.client.SftpClient
-dontwarn org.apache.sshd.sftp.client.SftpClientFactory
-dontwarn org.apache.sshd.sftp.common.SftpException
-dontwarn org.bouncycastle.crypto.prng.RandomGenerator
-dontwarn org.bouncycastle.crypto.prng.VMPCRandomGenerator
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.bouncycastle.openssl.PEMDecryptorProvider
-dontwarn org.bouncycastle.openssl.PEMEncryptedKeyPair
-dontwarn org.bouncycastle.openssl.PEMKeyPair
-dontwarn org.bouncycastle.openssl.PEMParser
-dontwarn org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
-dontwarn org.bouncycastle.openssl.jcajce.JcaPEMWriter
-dontwarn org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.MessageProp
-dontwarn org.ietf.jgss.Oid
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn sun.security.x509.X509Key
