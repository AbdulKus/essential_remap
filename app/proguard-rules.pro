# Conscrypt contains compatibility adapters for Android platform TLS implementations
# that exist only on specific OS versions. They are selected conditionally at runtime.
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
