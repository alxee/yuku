# OkHttp is only used for URL parsing / public suffix lookup here.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep any @JavascriptInterface members if you add a JS bridge later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
