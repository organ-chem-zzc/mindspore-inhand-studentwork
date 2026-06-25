# Add project specific ProGuard rules here.
# You can control the set of applied configuration rules using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS interfaces, you need to make sure
# the JavaScript interface methods are annotated with @JavascriptInterface
# and the class is annotated with @Keep.
-keepattributes *Annotation*
-keep @interface android.webkit.JavascriptInterface
-keep class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep MindSpore related classes
-keep class com.mindspore.** { *; }
-keep class * extends com.mindspore.** { *; }
