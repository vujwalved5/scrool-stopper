# Implementation Plan - Make App Lightweight

This plan outlines steps to reduce the APK size and resource usage of the "Shorts Interrupter" app.

## Proposed Changes

### Build Configuration

#### [build.gradle.kts](file:///C:/Users/aruko/OneDrive/Desktop/Documents/shortstopper/app/build.gradle.kts)
- Enable R8 minification and resource shrinking for release builds.
- Remove unused Jetpack Compose dependencies and plugins.
- Add `resConfigs "en"` to strip non-English resources.

```kotlin
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```

#### [libs.versions.toml](file:///C:/Users/aruko/OneDrive/Desktop/Documents/shortstopper/gradle/libs.versions.toml)
- Remove unused Compose-related versions and libraries.

---

### Accessibility Service Optimization

#### [accessibility_service_config.xml](file:///C:/Users/aruko/OneDrive/Desktop/Documents/shortstopper/app/src/main/res/xml/accessibility_service_config.xml)
- Restrict the service to only receive events from the YouTube app.
- Remove unnecessary flags.

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:packageNames="com.google.android.youtube"
    android:description="@string/accessibility_service_description" />
```

#### [ShortsAccessibilityService.kt](file:///C:/Users/aruko/OneDrive/Desktop/Documents/shortstopper/app/src/main/java/com/example/myapplication/ShortsAccessibilityService.kt)
- Wrap debug-only receivers in `BuildConfig.DEBUG` checks (or remove them if not needed).
- Optimize tree traversal to be more efficient.

---

### Resource Optimization

#### [message.mp3](file:///C:/Users/aruko/OneDrive/Desktop/Documents/shortstopper/app/src/main/res/raw/message.mp3)
- (Recommendation) Compress or shorten the audio file to reduce its 1.6MB size.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleRelease` to verify the build succeeds with R8 enabled.
- Compare the size of the new APK with the original (~32.6 MB).

### Manual Verification
- Deploy the optimized app to a device/emulator.
- Verify the Accessibility Service still correctly detects Shorts in YouTube.
- Verify the dashboard still displays stats correctly.
- Verify audio still plays when triggered.
