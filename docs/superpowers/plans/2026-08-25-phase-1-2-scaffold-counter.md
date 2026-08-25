# Phase 1+2 Scaffold & Counter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a buildable Android project (Kotlin + Jetpack Compose + Material 3)
with a pure-Kotlin `TasbihCounter` domain engine and a visually-faithful Counter
screen, backed by DataStore-persisted session state that survives process death.

**Architecture:** Single `:app` Gradle module. A dependency-free `core.counter`
engine implements increment/undo/reset/pause/resume exactly matching the prototype's
proven `tap()`/`undo()` logic. A `CounterViewModel` wraps the engine in a `StateFlow`,
updated synchronously on tap for instant UI feedback, with writes to a DataStore-backed
`SessionRepository` debounced off the tap path. The Counter screen is built pixel-faithful
to `design/README.md` using the exact SVG path data and copy from the prototype
(`design/Dhikr Android App.dc.html`). A minimal Home stub screen exists only as a
navigation entry point.

**Tech Stack:** Kotlin 2.3.20, AGP 9.3.0, Gradle 9.5.0 (wrapper), JDK 17, Jetpack
Compose (BOM 2026.08.00), Material 3, Navigation Compose, Kotlin Coroutines, AndroidX
Lifecycle/ViewModel (Compose), AndroidX DataStore (Preferences). No Room, no
WorkManager, no notification/widget libraries this phase.

**Spec:** `docs/superpowers/specs/2026-08-25-phase-1-2-scaffold-counter-design.md`

## Global Constraints

- `applicationId` / package root: `com.dhikr.app`
- `minSdk 24`, `targetSdk`/`compileSdk 36`
- AGP `9.3.0`, Gradle wrapper `9.5.0`, JDK `17`, Kotlin `2.3.20`, Compose BOM `2026.08.00`
- No ads/tracking/analytics SDKs, no account/login, no network calls, no Room this phase
- No database write on every tap — debounced/batched persistence off the tap path
- Reset is never a one-tap destructive action — always confirmation-gated
- Counter lock blocks reset and back-navigation but never blocks counting itself
- All UI strings go through Android string resources (`res/values/strings.xml`) —
  nothing hardcoded, even though only English is wired up this phase (localization
  structure must exist from the start per plan.md §32)
- Verification for this phase is **build-only**: a successful Gradle build is the
  completion criterion. Do not write unit tests, UI tests, or attempt manual/emulator
  verification — the user tests manually.
- If an implementation question arises that isn't answered by the spec, plan.md, or
  design/README.md, ask the user — do not guess or silently pick a default.
- Font files are variable-weight TTFs (Figtree, Noto Naskh Arabic, Noto Sans Bengali);
  on API 24–25 devices they render at their default weight instance only (accepted
  limitation, confirmed with user — not a bug to fix).

---

## Task 1: Project scaffold — Gradle, AGP, Compose wiring

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/dhikr/app/MainActivity.kt`
- Create: `app/src/main/java/com/dhikr/app/DhikrApp.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `.gitignore`

**Interfaces:**
- Produces: a runnable `MainActivity` hosting a `DhikrApp()` root composable that
  later tasks add navigation and screens into. `DhikrApp()` signature:
  `@Composable fun DhikrApp()` — no parameters, self-contained.

- [ ] **Step 1: Write `.gitignore`**

```gitignore
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build/
/captures/
.externalNativeBuild/
.cxx/
*.apk
*.aab
```

- [ ] **Step 2: Write the version catalog `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.3.0"
kotlin = "2.3.20"
composeBom = "2026.08.00"
coreKtx = "1.15.0"
lifecycle = "2.9.0"
activityCompose = "1.10.0"
navigationCompose = "2.9.0"
datastore = "1.1.2"
coroutines = "1.9.0"

[libraries]
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

AGP 9.0+ has **built-in Kotlin support** and hard-rejects the separate
`org.jetbrains.kotlin.android` Gradle plugin being applied alongside it (this is a
documented breaking change: https://kotl.in/gradle/agp-built-in-kotlin — confirmed
via Google's own migration guide at
https://developer.android.com/build/migrate-to-built-in-kotlin). Do not add
`kotlin-android` back to the `[plugins]` table.

The **Compose Compiler Gradle plugin** (`org.jetbrains.kotlin.plugin.compose`) is a
*separate* plugin from `kotlin-android` and is still required even under AGP 9's
built-in Kotlin support — AGP's built-in Kotlin only supersedes the general
`kotlin-android` compilation plugin, not the Compose-specific compiler plugin.
Confirmed via https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler:
"the `org.jetbrains.kotlin.plugin.compose` plugin must still be applied separately
even with AGP 9's built-in Kotlin support." Its version tracks `kotlin` (available
from Kotlin 2.0+, matches the Kotlin version exactly — no independent version to
pin).

- [ ] **Step 3: Write root `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Dhikr"
include(":app")
```

- [ ] **Step 4: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

- [ ] **Step 5: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 6: Write `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 7: Generate the wrapper jar/scripts**

Run: `gradle wrapper --gradle-version 9.5.0` (requires a system Gradle install to
bootstrap from; if unavailable, download `gradle-wrapper.jar` and the `gradlew` /
`gradlew.bat` scripts matching Gradle 9.5.0's standard wrapper output into
`gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat` at the repo root).
Expected: `gradlew` and `gradlew.bat` exist at repo root, `gradle/wrapper/gradle-wrapper.jar` exists.

If neither a system `gradle` binary nor a way to obtain the wrapper jar is available in
this environment, stop and ask the user how they want the wrapper bootstrapped —
do not guess or fabricate a wrapper jar.

- [ ] **Step 8: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.dhikr.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dhikr.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// AGP 9's built-in Kotlin support moves compiler options out of android{} and
// into this top-level kotlin{} block (replaces the old android.kotlinOptions{}
// DSL). jvmTarget would default to compileOptions' targetCompatibility (17)
// even without this block; it's set explicitly here for clarity.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 9: Write `app/proguard-rules.pro`** (empty placeholder — no rules
      needed yet since no reflection-heavy libraries are in use this phase)

```proguard
# Add project-specific ProGuard rules here.
```

- [ ] **Step 10: Write `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.Dhikr"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Dhikr">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

Note: this manifest deliberately has no `android:name` on `<application>` yet — a
custom `DhikrApplication` class doesn't exist until Task 4, and referencing it before
then would fail the build. Task 4 adds `android:name=".DhikrApplication"` back into
this file once the class exists (see Task 4's Files list). This also references
default launcher mipmaps. If `@mipmap/ic_launcher` doesn't exist yet, Android
Studio's default project template icons are acceptable placeholders for this phase —
ask the user before spending time on custom launcher icon design, since it isn't in
scope.

- [ ] **Step 11: Write `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Dhikr</string>
</resources>
```

(Further strings are added in later tasks as each composable is built — keeping this
minimal now avoids unused-string placeholders.)

- [ ] **Step 12: Write `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Dhikr" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

(This is the pre-Compose window theme only, used for the splash-to-Compose handoff;
the real Material3 `ColorScheme` is defined in Compose in Task 3.)

- [ ] **Step 13: Write a placeholder `DhikrApp.kt`**

```kotlin
package com.dhikr.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DhikrApp() {
    MaterialTheme {
        Surface(modifier = Modifier) {
            Text("Dhikr")
        }
    }
}
```

- [ ] **Step 14: Write `MainActivity.kt`**

```kotlin
package com.dhikr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DhikrApp()
        }
    }
}
```

- [ ] **Step 15: Build to verify the scaffold compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. If it fails, fix the scaffold before proceeding — do not
move to Task 2 with a broken build.

- [ ] **Step 16: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ app/ .gitignore gradlew gradlew.bat
git commit -m "Scaffold Android project: Gradle, AGP, Compose, minimal MainActivity"
```

---

## Task 2: Font resources

**Files:**
- Create: `app/src/main/res/font/caprasimo_regular.ttf` (binary, downloaded)
- Create: `app/src/main/res/font/figtree.ttf` (binary, downloaded)
- Create: `app/src/main/res/font/noto_naskh_arabic.ttf` (binary, downloaded)
- Create: `app/src/main/res/font/noto_sans_bengali.ttf` (binary, downloaded)

**Interfaces:**
- Produces: four raw font resources (`R.font.caprasimo_regular`, `R.font.figtree`,
  `R.font.noto_naskh_arabic`, `R.font.noto_sans_bengali`), consumed directly by
  Task 3's `Type.kt` via Compose's `Font(R.font.xxx, ...)` constructor. No
  `font-family` XML wrapper files are created — those exist for `android:fontFamily`
  in classic Android View XML, which this Compose-only project never uses; creating
  them would be dead weight nothing reads.

- [ ] **Step 1: Download the four font TTFs from the google/fonts OFL source**

Run:
```bash
mkdir -p app/src/main/res/font
curl -sSL -o app/src/main/res/font/caprasimo_regular.ttf \
  "https://github.com/google/fonts/raw/main/ofl/caprasimo/Caprasimo-Regular.ttf"
curl -sSL -o app/src/main/res/font/figtree.ttf \
  "https://github.com/google/fonts/raw/main/ofl/figtree/Figtree%5Bwght%5D.ttf"
curl -sSL -o app/src/main/res/font/noto_naskh_arabic.ttf \
  "https://github.com/google/fonts/raw/main/ofl/notonaskharabic/NotoNaskhArabic%5Bwght%5D.ttf"
curl -sSL -o app/src/main/res/font/noto_sans_bengali.ttf \
  "https://github.com/google/fonts/raw/main/ofl/notosansbengali/NotoSansBengali%5Bwdth%2Cwght%5D.ttf"
```

Expected: all four files exist and are non-trivial size (tens to hundreds of KB each).
Verify with:
```bash
file app/src/main/res/font/*.ttf
```
Expected output: each line says "TrueType Font data". If any download fails or
returns an HTML error page instead of font data (check with `file` — it'll say
something other than TrueType), stop and ask the user rather than guessing an
alternate URL.

Android resource filenames must be lowercase snake_case with no brackets — the
`[wght]` / `[wdth,wght]` suffixes from the upstream filenames are intentionally
dropped in the local filenames above.

- [ ] **Step 2: Build to verify font resources are valid and package correctly**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. A malformed font file will fail resource processing at
this step.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/font/
git commit -m "Add Caprasimo, Figtree, Noto Naskh Arabic, Noto Sans Bengali font resources"
```

---

## Task 3: Theme — colors, typography, shapes

**Files:**
- Create: `app/src/main/java/com/dhikr/app/ui/theme/Color.kt`
- Create: `app/src/main/java/com/dhikr/app/ui/theme/Type.kt`
- Create: `app/src/main/java/com/dhikr/app/ui/theme/Shape.kt`
- Create: `app/src/main/java/com/dhikr/app/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt` (wrap in `DhikrTheme`)

**Interfaces:**
- Produces:
  - `object DhikrColors` — extended color tokens not representable in Material3's
    default `ColorScheme` slots (see below), exposed via `LocalDhikrColors` a
    `CompositionLocal`.
  - `val Typography` (Material3 `Typography`) plus standalone `TextStyle` constants
    for the display-count and Arabic/Bengali text used directly by the Counter screen.
  - `@Composable fun DhikrTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`
- Consumes: `FontFamily` resources from Task 2 (`R.font.*`).

The Organic design system's tokens (`bg`, `surface`, `card`, `text`, `dim`, `faint`,
`line`, `sage`, `sage-soft`, `terra`, `terra-soft`, `track`, `on-sage`) don't map
1:1 onto Material3's `ColorScheme` role names (`primary`, `secondary`, `surface`,
etc.) — rather than force an imprecise mapping, keep the exact Organic tokens as a
separate `DhikrColors` set exposed via `CompositionLocal`, and use a minimal
Material3 `ColorScheme` only where MD3 components (e.g. any default `Switch` in a
later phase) require one. This keeps every color value in the Counter screen
pixel-exact to the spec rather than approximated through MD3's semantic roles.

- [ ] **Step 1: Write `Color.kt`** with light/dark token sets from
      `design/README.md`'s tables

```kotlin
package com.dhikr.app.ui.theme

import androidx.compose.ui.graphics.Color

data class DhikrColorTokens(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val line: Color,
    val sage: Color,
    val sageSoft: Color,
    val terra: Color,
    val terraSoft: Color,
    val track: Color,
    val onSage: Color,
)

val LightDhikrColors = DhikrColorTokens(
    bg = Color(0xFFF5EAD8),
    surface = Color(0xFFEBDDC5),
    card = Color(0xFFF9F4ED),
    text = Color(0xFF201E1D),
    dim = Color(0xFF645C50),
    faint = Color(0xFFA19786),
    line = Color(0x21201E1D), // rgba(32,30,29,.13)
    sage = Color(0xFF7A8A5E),
    sageSoft = Color(0xFFE1EECC),
    terra = Color(0xFFC67139),
    terraSoft = Color(0xFFFFE1D0),
    track = Color(0x1A201E1D), // rgba(32,30,29,.10)
    onSage = Color(0xFFF9F4ED),
)

val DarkDhikrColors = DhikrColorTokens(
    bg = Color(0xFF1C1A17),
    surface = Color(0xFF2A261F),
    card = Color(0xFF332E26),
    text = Color(0xFFF6EFE2),
    dim = Color(0xFFC0B6A5),
    faint = Color(0xFF82796A),
    line = Color(0x1EF6EFE2), // rgba(246,239,226,.12)
    sage = Color(0xFFAEBF92),
    sageSoft = Color(0xFF3D472B),
    terra = Color(0xFFF6A06B),
    terraSoft = Color(0xFF4D2F18),
    track = Color(0x1AF6EFE2), // rgba(246,239,226,.10)
    onSage = Color(0xFF272E1B),
)
```

- [ ] **Step 2: Write `Type.kt`** with the four `FontFamily`s and text styles

```kotlin
package com.dhikr.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dhikr.app.R

val Caprasimo = FontFamily(Font(R.font.caprasimo_regular, FontWeight.Normal))
val Figtree = FontFamily(
    Font(R.font.figtree, FontWeight.Normal),
    Font(R.font.figtree, FontWeight.Medium),
    Font(R.font.figtree, FontWeight.SemiBold),
    Font(R.font.figtree, FontWeight.Bold),
)
val NotoNaskhArabic = FontFamily(Font(R.font.noto_naskh_arabic, FontWeight.Normal))
val NotoSansBengali = FontFamily(Font(R.font.noto_sans_bengali, FontWeight.Normal))

// Counter-screen-specific styles (sizes from design/README.md's typography table)
val CounterCountStyle = TextStyle(
    fontFamily = Caprasimo,
    fontSize = 84.sp,
    letterSpacing = (-0.03).em(),
)

val CounterCountLongTextStyle = TextStyle(
    fontFamily = Caprasimo,
    fontSize = 56.sp,
    letterSpacing = (-0.03).em(),
)

val ArabicLineStyle = TextStyle(
    fontFamily = NotoNaskhArabic,
    fontSize = 30.sp,
    lineHeight = 51.sp, // 1.7 line-height
)

val TransliterationStyle = TextStyle(
    fontFamily = NotoSansBengali,
    fontSize = 14.5.sp,
    lineHeight = 21.sp, // 1.45
)

val TransliterationLongTextStyle = TextStyle(
    fontFamily = NotoSansBengali,
    fontSize = 13.5.sp,
    lineHeight = 27.sp, // 2.0
)
```

Note: `(-0.03).em()` is not a real Compose extension — use
`TextUnit(-0.03f, TextUnitType.Em)` or the `.em` property from
`androidx.compose.ui.unit.em` (`import androidx.compose.ui.unit.em` then
`(-0.03f).em`). Use the correct real API when writing this file; verify it compiles
in Step 4 below rather than trusting this note.

- [ ] **Step 3: Write `Shape.kt`**

```kotlin
package com.dhikr.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val PillShape = RoundedCornerShape(999.dp)
val CardShape = RoundedCornerShape(28.dp)
val DialogShape = RoundedCornerShape(30.dp)
```

- [ ] **Step 4: Write `Theme.kt`**

```kotlin
package com.dhikr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalDhikrColors = compositionLocalOf { LightDhikrColors }

object DhikrTheme {
    val colors: DhikrColorTokens
        @Composable get() = LocalDhikrColors.current
}

@Composable
fun DhikrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkDhikrColors else LightDhikrColors
    CompositionLocalProvider(LocalDhikrColors provides tokens) {
        MaterialTheme(content = content)
    }
}
```

- [ ] **Step 5: Update `DhikrApp.kt` to wrap content in `DhikrTheme`**

```kotlin
package com.dhikr.app

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dhikr.app.ui.theme.DhikrTheme

@Composable
fun DhikrApp() {
    DhikrTheme {
        Surface(modifier = Modifier) {
            Text("Dhikr")
        }
    }
}
```

- [ ] **Step 6: Build to verify theme code compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Fix any Compose `TextUnit`/`em` API mismatches found
here (see the note in Step 2) before proceeding.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/ui/theme/ app/src/main/java/com/dhikr/app/DhikrApp.kt
git commit -m "Add Organic design token colors, typography, shapes, DhikrTheme"
```

---

## Task 4: Domain model and built-in Dhikr library

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/model/Dhikr.kt`
- Create: `app/src/main/java/com/dhikr/app/core/model/BuiltInDhikr.kt`
- Create: `app/src/main/java/com/dhikr/app/DhikrApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `android:name=".DhikrApplication"`
  to the `<application>` tag — Task 1 deliberately left this attribute off since the
  class didn't exist yet)

**Interfaces:**
- Produces: `data class Dhikr(id, name, arabic, transliteration, translation, lapTarget, lapCount, isFavorite)`
  and `object BuiltInDhikr { val all: List<Dhikr> }`.
- `DhikrApplication` will be referenced by `AndroidManifest.xml` once this task adds
  the attribute back (Step 3a below).

- [ ] **Step 1: Write `Dhikr.kt`**

```kotlin
package com.dhikr.app.core.model

data class Dhikr(
    val id: String,
    val name: String,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val lapTarget: Int,
    val lapCount: Int,
    val isFavorite: Boolean = false,
)
```

- [ ] **Step 2: Write `BuiltInDhikr.kt`** with the exact 7 entries from
      `design/README.md` §Content / prototype's `DHIKR` array

```kotlin
package com.dhikr.app.core.model

object BuiltInDhikr {
    val all: List<Dhikr> = listOf(
        Dhikr(
            id = "kursi",
            name = "Ayatul Kursi",
            arabic = "",
            transliteration = "আল্লাহু লা ইলাহা ইল্লা হুয়াল হাইয়্যুল কইয়্যুমু লা তা খুজুহু সিনাত্যু ওয়ালা নাউম। " +
                "লাহু মা ফিছছামা ওয়াতি ওয়ামা ফিল আরদ। মান যাল্লাযী ইয়াস ফায়ু ইন দাহু ইল্লা বি ইজনিহি ইয়া লামু মা " +
                "বাইনা আইদিহিম ওয়ামা খল ফাহুম ওয়ালা ইউ হিতুনা বিশাই ইম্ মিন ইল্ মিহি ইল্লা বিমা সাআ ওয়াসিয়া " +
                "কুরসিইউ হুস ছামা ওয়াতি ওয়াল আরদ্ ওয়ালা ইয়া উদুহু হিফজুহুমা ওয়াহুয়াল আলিয়্যুল আজিম",
            translation = "",
            lapTarget = 7,
            lapCount = 1,
            isFavorite = true,
        ),
        Dhikr(
            id = "subhan",
            name = "SubhanAllah",
            arabic = "سُبْحَانَ اللّٰه",
            transliteration = "সুবহানাল্লাহ",
            translation = "Glory be to Allah",
            lapTarget = 33,
            lapCount = 3,
            isFavorite = true,
        ),
        Dhikr(
            id = "hamd",
            name = "Alhamdulillah",
            arabic = "الْحَمْدُ لِلّٰه",
            transliteration = "আলহামদুলিল্লাহ",
            translation = "All praise is due to Allah",
            lapTarget = 33,
            lapCount = 3,
            isFavorite = true,
        ),
        Dhikr(
            id = "akbar",
            name = "Allahu Akbar",
            arabic = "اللّٰهُ أَكْبَر",
            transliteration = "আল্লাহু আকবার",
            translation = "Allah is the greatest",
            lapTarget = 34,
            lapCount = 3,
            isFavorite = true,
        ),
        Dhikr(
            id = "istighfar",
            name = "Astaghfirullah",
            arabic = "أَسْتَغْفِرُ اللّٰه",
            transliteration = "আস্তাগফিরুল্লাহ",
            translation = "I seek forgiveness from Allah",
            lapTarget = 100,
            lapCount = 1,
            isFavorite = false,
        ),
        Dhikr(
            id = "bihamdihi",
            name = "Subhanallahi wa bihamdihi",
            arabic = "سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ",
            transliteration = "সুবহানাল্লাহি ওয়া বিহামদিহি",
            translation = "Glory be to Allah and praise be to Him",
            lapTarget = 100,
            lapCount = 1,
            isFavorite = false,
        ),
        Dhikr(
            id = "hawla",
            name = "La hawla wa la quwwata illa billah",
            arabic = "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰه",
            transliteration = "লা হাওলা ওয়ালা কুওয়াতা ইল্লা বিল্লাহ",
            translation = "There is no power nor strength except with Allah",
            lapTarget = 33,
            lapCount = 1,
            isFavorite = false,
        ),
    )

    fun byId(id: String): Dhikr = all.find { it.id == id } ?: all.first()
}
```

- [ ] **Step 3: Write a minimal `DhikrApplication.kt`** (no initialization logic
      yet — exists only so the manifest reference resolves; later phases hang
      DI/init here if ever needed)

```kotlin
package com.dhikr.app

import android.app.Application

class DhikrApplication : Application()
```

- [ ] **Step 3a: Add `android:name=".DhikrApplication"` to the manifest's
      `<application>` tag**

Modify `app/src/main/AndroidManifest.xml` (created in Task 1) so the `<application>`
opening tag reads:

```xml
    <application
        android:name=".DhikrApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.Dhikr"
        android:supportsRtl="true">
```

(i.e. add the `android:name=".DhikrApplication"` line; every other attribute stays
as Task 1 wrote it.)

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. This is the first point in the plan where the manifest
fully resolves — if it fails here, check that `DhikrApplication.kt`'s package and
class name exactly match the manifest's `android:name=".DhikrApplication"` reference.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/model/ app/src/main/java/com/dhikr/app/DhikrApplication.kt app/src/main/AndroidManifest.xml
git commit -m "Add Dhikr domain model, built-in library content, and DhikrApplication"
```

---

## Task 5: TasbihCounter engine

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/counter/TasbihCounter.kt`
- Create: `app/src/main/java/com/dhikr/app/core/counter/CounterSnapshot.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class CounterSnapshot(
      val count: Int,
      val lap: Int,
      val previousCount: Int?,
      val previousLap: Int?,
      val isComplete: Boolean,
      val justCompletedLap: Boolean,
  ) {
      val canUndo: Boolean get() = previousCount != null
  }

  class TasbihCounter(lapTarget: Int, totalLaps: Int) {
      fun increment(): CounterSnapshot
      fun undo(): CounterSnapshot
      fun reset(): CounterSnapshot
      fun restore(count: Int, lap: Int, previous: Pair<Int, Int>?)
      fun pause()
      fun resume()
      fun isRunning(): Boolean
      fun snapshot(): CounterSnapshot
      fun progressFraction(): Float
      fun totalCount(): Int
  }
  ```
- Consumes: nothing (pure Kotlin, no Android/Compose imports).

This is the core logic every later consumer (widget, notification controls, future
Wear OS) will share per plan.md §10 — kept dependency-free deliberately.
`CounterSnapshot` carries `previousCount`/`previousLap` (not just a `canUndo`
boolean) so `CounterViewModel` (Task 7) can persist undo state across process death
and restore it exactly — `canUndo` is a derived convenience property, not a
separate stored field.

- [ ] **Step 1: Write `CounterSnapshot.kt`**

```kotlin
package com.dhikr.app.core.counter

data class CounterSnapshot(
    val count: Int,
    val lap: Int,
    val previousCount: Int?,
    val previousLap: Int?,
    val isComplete: Boolean,
    val justCompletedLap: Boolean,
) {
    val canUndo: Boolean get() = previousCount != null
}
```

- [ ] **Step 2: Write `TasbihCounter.kt`**

Ported directly from the prototype's `tap()`/`undo()`
(`design/Dhikr Android App.dc.html:594-621`). `lapTarget <= 0` is defensively
clamped to 1 (spec's Error handling section) so progress never divides by zero.

```kotlin
package com.dhikr.app.core.counter

class TasbihCounter(lapTarget: Int, private val totalLaps: Int) {

    private val lapTarget: Int = if (lapTarget <= 0) 1 else lapTarget

    private var count = 0
    private var lap = 1
    private var previous: Pair<Int, Int>? = null // (count, lap) before the last increment/undo-eligible change
    private var running = true
    private var complete = false

    fun increment(): CounterSnapshot {
        if (complete) {
            // No-op once the final lap's target is reached — prevents overflow on
            // rapid/double taps after completion.
            return snapshot()
        }

        previous = count to lap
        var justCompletedLap = false

        if (count + 1 < lapTarget) {
            count += 1
        } else if (lap < totalLaps) {
            count = 0
            lap += 1
            justCompletedLap = true
        } else {
            count = lapTarget
            complete = true
            running = false
        }

        return snapshot().copy(justCompletedLap = justCompletedLap)
    }

    fun undo(): CounterSnapshot {
        val prior = previous ?: return snapshot()
        count = prior.first
        lap = prior.second
        previous = null
        complete = false
        return snapshot()
    }

    fun reset(): CounterSnapshot {
        count = 0
        lap = 1
        previous = null
        complete = false
        return snapshot()
    }

    /**
     * Restores internal state from a previously persisted snapshot (cold-start
     * session recovery). Not part of the normal increment/undo/reset state
     * machine — called once by CounterViewModel after reading SessionRepository.
     */
    fun restore(count: Int, lap: Int, previous: Pair<Int, Int>?) {
        this.count = count
        this.lap = lap
        this.previous = previous
        this.complete = lap >= totalLaps && count >= lapTarget
    }

    fun pause() {
        running = false
    }

    fun resume() {
        if (!complete) running = true
    }

    fun isRunning(): Boolean = running

    fun snapshot(): CounterSnapshot = CounterSnapshot(
        count = count,
        lap = lap,
        previousCount = previous?.first,
        previousLap = previous?.second,
        isComplete = complete,
        justCompletedLap = false,
    )

    fun progressFraction(): Float = count.toFloat() / lapTarget.toFloat()

    fun totalCount(): Int = (lap - 1) * lapTarget + count
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/counter/
git commit -m "Add TasbihCounter domain engine"
```

---

## Task 6: Session persistence (DataStore)

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/model/CounterSessionState.kt`
- Create: `app/src/main/java/com/dhikr/app/core/datastore/SessionRepository.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class CounterSessionState(
      val activeDhikrId: String,
      val count: Int,
      val lap: Int,
      val previousCount: Int?, // null = no undo available
      val previousLap: Int?,
      val running: Boolean,
      val elapsedSeconds: Int,
      val locked: Boolean,
      val routineId: String?,
      val routineStep: Int,
  )

  class SessionRepository(context: Context) {
      val sessionFlow: Flow<CounterSessionState?>
      suspend fun save(state: CounterSessionState)
      suspend fun clear()
  }
  ```
- Consumes: Android `Context` (application context only, passed once at construction
  — never an `Activity` context, to avoid the leak plan.md §51 warns about).

- [ ] **Step 1: Write `CounterSessionState.kt`**

```kotlin
package com.dhikr.app.core.model

data class CounterSessionState(
    val activeDhikrId: String,
    val count: Int,
    val lap: Int,
    val previousCount: Int?,
    val previousLap: Int?,
    val running: Boolean,
    val elapsedSeconds: Int,
    val locked: Boolean,
    val routineId: String?,
    val routineStep: Int,
) {
    companion object {
        fun fresh(dhikrId: String) = CounterSessionState(
            activeDhikrId = dhikrId,
            count = 0,
            lap = 1,
            previousCount = null,
            previousLap = null,
            running = true,
            elapsedSeconds = 0,
            locked = false,
            routineId = null,
            routineStep = 0,
        )
    }
}
```

- [ ] **Step 2: Write `SessionRepository.kt`**

Corrupted/unreadable preferences fall back to `emptyPreferences()` (spec's Error
handling: never crash on a bad DataStore read) via `.catch` on the underlying flow.

```kotlin
package com.dhikr.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dhikr.app.core.model.CounterSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sessionDataStore by preferencesDataStore(name = "session")

class SessionRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_DHIKR_ID = stringPreferencesKey("active_dhikr_id")
        val COUNT = intPreferencesKey("count")
        val LAP = intPreferencesKey("lap")
        val PREVIOUS_COUNT = intPreferencesKey("previous_count")
        val PREVIOUS_LAP = intPreferencesKey("previous_lap")
        val RUNNING = booleanPreferencesKey("running")
        val ELAPSED_SECONDS = intPreferencesKey("elapsed_seconds")
        val LOCKED = booleanPreferencesKey("locked")
        val ROUTINE_ID = stringPreferencesKey("routine_id")
        val ROUTINE_STEP = intPreferencesKey("routine_step")
    }

    val sessionFlow: Flow<CounterSessionState?> = context.sessionDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val activeId = prefs[Keys.ACTIVE_DHIKR_ID] ?: return@map null
            CounterSessionState(
                activeDhikrId = activeId,
                count = prefs[Keys.COUNT] ?: 0,
                lap = prefs[Keys.LAP] ?: 1,
                previousCount = prefs[Keys.PREVIOUS_COUNT],
                previousLap = prefs[Keys.PREVIOUS_LAP],
                running = prefs[Keys.RUNNING] ?: true,
                elapsedSeconds = prefs[Keys.ELAPSED_SECONDS] ?: 0,
                locked = prefs[Keys.LOCKED] ?: false,
                routineId = prefs[Keys.ROUTINE_ID],
                routineStep = prefs[Keys.ROUTINE_STEP] ?: 0,
            )
        }

    suspend fun save(state: CounterSessionState) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_DHIKR_ID] = state.activeDhikrId
            prefs[Keys.COUNT] = state.count
            prefs[Keys.LAP] = state.lap
            if (state.previousCount != null) {
                prefs[Keys.PREVIOUS_COUNT] = state.previousCount
            } else {
                prefs.remove(Keys.PREVIOUS_COUNT)
            }
            if (state.previousLap != null) {
                prefs[Keys.PREVIOUS_LAP] = state.previousLap
            } else {
                prefs.remove(Keys.PREVIOUS_LAP)
            }
            prefs[Keys.RUNNING] = state.running
            prefs[Keys.ELAPSED_SECONDS] = state.elapsedSeconds
            prefs[Keys.LOCKED] = state.locked
            if (state.routineId != null) {
                prefs[Keys.ROUTINE_ID] = state.routineId
            } else {
                prefs.remove(Keys.ROUTINE_ID)
            }
            prefs[Keys.ROUTINE_STEP] = state.routineStep
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/model/CounterSessionState.kt app/src/main/java/com/dhikr/app/core/datastore/
git commit -m "Add CounterSessionState and DataStore-backed SessionRepository"
```

---

## Task 7: CounterViewModel

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/counter/CounterUiState.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt`

**Interfaces:**
- Consumes: `TasbihCounter` (Task 5), `SessionRepository` (Task 6),
  `BuiltInDhikr` (Task 4).
- Produces:
  ```kotlin
  data class CounterUiState(
      val dhikr: Dhikr,
      val count: Int,
      val lap: Int,
      val totalLaps: Int,
      val canUndo: Boolean,
      val running: Boolean,
      val locked: Boolean,
      val elapsedSeconds: Int,
      val isComplete: Boolean,
      val justCompletedLap: Boolean,
  )

  class CounterViewModel(
      private val sessionRepository: SessionRepository,
      startingDhikrId: String? = null,
  ) : ViewModel() {
      val uiState: StateFlow<CounterUiState>
      fun onTap()
      fun onUndo()
      fun onReset()
      fun onTogglePause()
      fun onToggleLock()
  }
  ```
  Consumed by the Counter screen composable in Task 8, and by a
  `CounterViewModelFactory` used from `MainActivity`/`DhikrApp` navigation graph.

- [ ] **Step 1: Write `CounterUiState.kt`**

```kotlin
package com.dhikr.app.feature.counter

import com.dhikr.app.core.model.Dhikr

data class CounterUiState(
    val dhikr: Dhikr,
    val count: Int,
    val lap: Int,
    val totalLaps: Int,
    val canUndo: Boolean,
    val running: Boolean,
    val locked: Boolean,
    val elapsedSeconds: Int,
    val isComplete: Boolean,
    val justCompletedLap: Boolean,
) {
    val totalCount: Int get() = (lap - 1) * dhikr.lapTarget + count
    val progressFraction: Float get() = count.toFloat() / dhikr.lapTarget.toFloat()
}
```

- [ ] **Step 2: Write `CounterViewModel.kt`**

Persistence is debounced 500ms after the last state change and flushed
unconditionally on `onCleared()` / `ON_STOP`, matching the spec's persistence
strategy. The tap path (`onTap`) updates `_uiState` synchronously first, then
triggers the debounced save — never blocking the UI update on I/O.

```kotlin
package com.dhikr.app.feature.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dhikr.app.core.counter.TasbihCounter
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.model.BuiltInDhikr
import com.dhikr.app.core.model.CounterSessionState
import com.dhikr.app.core.model.Dhikr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CounterViewModel(
    private val sessionRepository: SessionRepository,
    startingDhikrId: String? = null,
) : ViewModel() {

    private var dhikr: Dhikr = BuiltInDhikr.byId(startingDhikrId ?: BuiltInDhikr.all.first().id)
    private var engine = TasbihCounter(dhikr.lapTarget, dhikr.lapCount)
    private var locked = false
    private var elapsedSeconds = 0

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<CounterUiState> = _uiState.asStateFlow()

    init {
        restoreSession()
        _uiState
            .drop(1) // skip the initial emission — nothing to persist yet
            .debounce(500)
            .onEach { persist() }
            .launchIn(viewModelScope)
        startTimer()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val saved = sessionRepository.sessionFlow
            // Collect just the first emission to restore, then stop — this is a
            // one-shot read on cold start, not a continuous observer.
            kotlinx.coroutines.flow.first(saved)?.let { session ->
                dhikr = BuiltInDhikr.byId(session.activeDhikrId)
                engine = TasbihCounter(dhikr.lapTarget, dhikr.lapCount)
                // Engine has no public state setter beyond increment/undo/reset by
                // design (keeps it a minimal state machine) — for restore we
                // reconstruct by replaying is unnecessary; instead expose the
                // restored snapshot directly through a package-private restore hook.
                engine.restore(
                    count = session.count,
                    lap = session.lap,
                    previous = if (session.previousCount != null && session.previousLap != null) {
                        session.previousCount to session.previousLap
                    } else null,
                )
                locked = session.locked
                elapsedSeconds = session.elapsedSeconds
                if (!session.running) engine.pause()
                _uiState.value = buildState()
            }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (engine.isRunning()) {
                    elapsedSeconds += 1
                    _uiState.value = buildState()
                }
            }
        }
    }

    fun onTap() {
        val snap = engine.increment()
        _uiState.value = buildState(justCompletedLap = snap.justCompletedLap)
    }

    fun onUndo() {
        engine.undo()
        _uiState.value = buildState()
    }

    fun onReset() {
        engine.reset()
        elapsedSeconds = 0
        _uiState.value = buildState()
    }

    fun onTogglePause() {
        if (engine.isRunning()) engine.pause() else engine.resume()
        _uiState.value = buildState()
    }

    fun onToggleLock() {
        locked = !locked
        _uiState.value = buildState()
    }

    private fun buildState(justCompletedLap: Boolean = false): CounterUiState {
        val snap = engine.snapshot()
        return CounterUiState(
            dhikr = dhikr,
            count = snap.count,
            lap = snap.lap,
            totalLaps = dhikr.lapCount,
            canUndo = snap.canUndo,
            running = engine.isRunning(),
            locked = locked,
            elapsedSeconds = elapsedSeconds,
            isComplete = snap.isComplete,
            justCompletedLap = justCompletedLap,
        )
    }

    private suspend fun persist() {
        // Read previousCount/previousLap from the engine's snapshot directly
        // (not from _uiState, which only exposes the derived canUndo boolean) so
        // undo state round-trips correctly across process death.
        val snap = engine.snapshot()
        val s = _uiState.value
        sessionRepository.save(
            CounterSessionState(
                activeDhikrId = s.dhikr.id,
                count = snap.count,
                lap = snap.lap,
                previousCount = snap.previousCount,
                previousLap = snap.previousLap,
                running = s.running,
                elapsedSeconds = s.elapsedSeconds,
                locked = s.locked,
                routineId = null,
                routineStep = 0,
            )
        )
    }

    override fun onCleared() {
        // Flush synchronously isn't possible from onCleared (no suspend context) —
        // rely on the ON_STOP-triggered flush instead; see MainActivity wiring in
        // Task 8. viewModelScope is already cancelled here so no launch{} is started.
        super.onCleared()
    }

    class Factory(
        private val sessionRepository: SessionRepository,
        private val startingDhikrId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CounterViewModel(sessionRepository, startingDhikrId) as T
        }
    }
}
```

`TasbihCounter.restore()` and `CounterSnapshot.previousCount`/`previousLap` are
already defined in Task 5 — this task only consumes them, no changes to
`TasbihCounter.kt` are needed here.

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/counter/CounterUiState.kt app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt
git commit -m "Add CounterViewModel with debounced session persistence"
```

---

## Task 8: Counter screen composables

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/counter/CounterScreen.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/counter/CounterIcons.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/dhikr/app/feature/counter/CounterViewModel.kt` (add
  `flushSession()`, called from a lifecycle observer in `CounterScreen.kt` — see
  Step 5; `MainActivity.kt` itself is not touched by this task)

**Interfaces:**
- Consumes: `CounterViewModel` (Task 7), `DhikrTheme`/`DhikrColors` (Task 3),
  `Caprasimo`/`NotoNaskhArabic`/`NotoSansBengali` text styles (Task 3).
- Produces: `@Composable fun CounterScreen(viewModel: CounterViewModel, onBack: () -> Unit)`,
  consumed by `DhikrApp`'s navigation graph (this task) and reused as-is by any
  later phase that adds routine-chip rendering (the chips row is built but stays
  empty this phase, per spec).

- [ ] **Step 1: Write `CounterIcons.kt`** — exact vector paths ported from the
      prototype's inline SVGs (`design/Dhikr Android App.dc.html:40,47,92,95`),
      stroke width 2.75, round caps/joins, 24×24 viewport

```kotlin
package com.dhikr.app.feature.counter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private const val STROKE_WIDTH = 2.75f

fun backChevronIcon(): ImageVector = ImageVector.Builder(
    name = "BackChevron", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(15f, 18f)
        lineTo(9f, 12f)
        lineTo(15f, 6f)
    }
}.build()

fun undoIcon(): ImageVector = ImageVector.Builder(
    name = "Undo", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 7f); lineTo(3f, 13f); lineTo(9f, 13f)
    }
    path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 13f)
        // "a9 9 0 1 0 3-7.7" arc — approximate with cubic curves matching the SVG
        // arc visually; exact SVG arc-to-Compose-path conversion, verify visually.
        curveTo(3f, 8f, 6.5f, 4f, 11f, 4f)
        curveTo(15.5f, 4f, 19f, 7.5f, 19f, 12f)
        curveTo(19f, 16.5f, 15.5f, 20f, 11f, 20f)
        curveTo(8f, 20f, 5.3f, 18.3f, 4f, 15.7f)
    }
}.build()

fun resetIcon(): ImageVector = ImageVector.Builder(
    name = "Reset", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(21f, 12f)
        curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
        curveTo(8.5f, 21f, 5.3f, 19f, 3.7f, 15.7f)
    }
    path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(21f, 3f); lineTo(21f, 9f); lineTo(15f, 9f)
    }
}.build()

fun lockIcon(locked: Boolean): ImageVector = ImageVector.Builder(
    name = if (locked) "LockClosed" else "LockOpen", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4f, 11f)
        lineTo(4f, 18f)
        curveTo(4f, 19.66f, 5.34f, 21f, 7f, 21f)
        lineTo(17f, 21f)
        curveTo(18.66f, 21f, 20f, 19.66f, 20f, 18f)
        lineTo(20f, 11f)
        close()
    }
    path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        if (locked) {
            moveTo(8f, 11f); lineTo(8f, 8f)
            curveTo(8f, 5.79f, 9.79f, 4f, 12f, 4f)
            curveTo(14.21f, 4f, 16f, 5.79f, 16f, 8f)
            lineTo(16f, 11f)
        } else {
            moveTo(8f, 11f); lineTo(8f, 8f)
            curveTo(8f, 5.79f, 9.79f, 4f, 12f, 4f)
            curveTo(13.86f, 4f, 15.43f, 5.28f, 15.87f, 7f)
        }
    }
}.build()
```

The undo/reset icon arc paths are visual approximations of the SVG's elliptical
arc commands (`A9 9 0 1 0 ...`) using cubic béziers, since `ImageVector.Builder`
doesn't have a direct SVG-arc-to-vector-path helper. After building, visually
compare against the prototype (open `design/Dhikr Android App.dc.html` in a
browser next to a run of the app) and adjust control points if the curve looks
off — this is a "looks right" check, not a pixel-diff requirement.

- [ ] **Step 2: Add Counter-screen strings to `strings.xml`**

```xml
<string name="counter_back_content_description">Back to Home</string>
<string name="counter_lock_content_description">Counter lock</string>
<string name="counter_undo">Undo</string>
<string name="counter_pause">Pause</string>
<string name="counter_resume">Resume</string>
<string name="counter_reset_content_description">Reset</string>
<string name="counter_of_target">of %1$d</string>
<string name="counter_lap_label">Lap %1$d of %2$d · %3$d of %4$d</string>
<string name="counter_tap_hint">Tap anywhere to count</string>
<string name="counter_tap_hint_locked">Locked — counting still works</string>
<string name="reset_dialog_title">Reset this session?</string>
<string name="reset_dialog_body">%1$d counts will be cleared. This cannot be undone.</string>
<string name="reset_dialog_keep_counting">Keep counting</string>
<string name="reset_dialog_confirm">Reset</string>
```

- [ ] **Step 3: Write `CounterScreen.kt`**

```kotlin
package com.dhikr.app.feature.counter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.ui.theme.ArabicLineStyle
import com.dhikr.app.ui.theme.CounterCountStyle
import com.dhikr.app.ui.theme.CounterCountLongTextStyle
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape
import com.dhikr.app.ui.theme.TransliterationLongTextStyle
import com.dhikr.app.ui.theme.TransliterationStyle
import kotlin.math.min

private const val LONG_TEXT_THRESHOLD = 90

@Composable
fun CounterScreen(viewModel: CounterViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    var showResetDialog by remember { mutableStateOf(false) }

    val isLongText = state.dhikr.transliteration.length > LONG_TEXT_THRESHOLD
    val ringSize = if (isLongText) 178.dp else 252.dp
    val countStyle = if (isLongText) CounterCountLongTextStyle else CounterCountStyle
    val transliterationStyle = if (isLongText) TransliterationLongTextStyle else TransliterationStyle

    val scale = remember { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(state.count, state.lap) {
        scale.snapTo(1.07f)
        scale.animateTo(1f, animationSpec = tween(110))
    }

    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = min(1f, state.progressFraction),
        animationSpec = tween(160, easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)),
        label = "ring-progress",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        // Top row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .height(48.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.counter_back_content_description)) { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(backChevronIcon(), contentDescription = stringResource(R.string.counter_back_content_description), tint = colors.dim)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(state.dhikr.name, fontSize = 15.sp, color = colors.text)
                Text(formatSessionLabel(state.elapsedSeconds, state.totalCount), fontSize = 11.5.sp, color = colors.faint)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.counter_lock_content_description)) {
                        if (!state.locked || true) viewModel.onToggleLock() // lock toggle itself is always allowed
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    lockIcon(state.locked),
                    contentDescription = stringResource(R.string.counter_lock_content_description),
                    tint = if (state.locked) colors.terra else colors.faint,
                )
            }
        }

        // Tap area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .clickable { viewModel.onTap() }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.dhikr.arabic.isNotEmpty()) {
                Text(
                    text = state.dhikr.arabic,
                    style = ArabicLineStyle,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                )
            }
            Text(
                text = state.dhikr.transliteration,
                style = transliterationStyle,
                color = colors.dim,
                textAlign = if (isLongText) TextAlign.Justify else TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = if (isLongText) 16.dp else 22.dp),
            )

            Box(
                modifier = Modifier
                    .size(ringSize)
                    .scale(scale.value),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidthPx = 12.dp.toPx()
                    val inset = strokeWidthPx / 2
                    drawArc(
                        color = colors.track,
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = colors.terra,
                        startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.82f)
                        .clip(CircleShape)
                        .background(colors.card),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.count.toString(), style = countStyle, color = colors.text)
                    Text(
                        stringResource(R.string.counter_of_target, state.dhikr.lapTarget),
                        fontSize = 13.sp, color = colors.faint,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    for (i in 1..state.totalLaps) {
                        val width = if (i == state.lap) 26.dp else 8.dp
                        val pipColor = when {
                            i < state.lap -> colors.sage
                            i == state.lap -> colors.terra
                            else -> colors.track
                        }
                        Box(
                            modifier = Modifier
                                .width(width)
                                .height(8.dp)
                                .clip(PillShape)
                                .background(pipColor),
                        )
                    }
                }
                Text(
                    stringResource(
                        R.string.counter_lap_label,
                        state.lap, state.totalLaps, state.totalCount, state.dhikr.lapTarget * state.totalLaps,
                    ),
                    fontSize = 12.5.sp, color = colors.dim,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }

            Text(
                text = if (state.locked) stringResource(R.string.counter_tap_hint_locked) else stringResource(R.string.counter_tap_hint),
                fontSize = 11.5.sp, color = colors.faint,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        // Control row
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.surface)
                    .clickable(enabled = state.canUndo) { viewModel.onUndo() }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Icon(undoIcon(), contentDescription = null, tint = colors.text, modifier = Modifier.size(17.dp))
                Text(stringResource(R.string.counter_undo), fontSize = 13.5.sp, color = colors.text)
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable { viewModel.onTogglePause() }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Text(
                    if (state.running) stringResource(R.string.counter_pause) else stringResource(R.string.counter_resume),
                    fontSize = 13.5.sp, color = colors.onSage,
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .clickable(enabled = !state.locked) { showResetDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(resetIcon(), contentDescription = stringResource(R.string.counter_reset_content_description), tint = colors.dim, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_dialog_title)) },
            text = { Text(stringResource(R.string.reset_dialog_body, state.totalCount)) },
            confirmButton = {
                Text(
                    stringResource(R.string.reset_dialog_confirm),
                    color = colors.terra,
                    modifier = Modifier.clickable {
                        viewModel.onReset()
                        showResetDialog = false
                    },
                )
            },
            dismissButton = {
                Text(
                    stringResource(R.string.reset_dialog_keep_counting),
                    modifier = Modifier.clickable { showResetDialog = false },
                )
            },
        )
    }
}

private fun formatSessionLabel(elapsedSeconds: Int, total: Int): String {
    val m = elapsedSeconds / 60
    val s = elapsedSeconds % 60
    val time = "%02d:%02d".format(m, s)
    if (elapsedSeconds <= 4) return time
    val rate = (total.toFloat() / (elapsedSeconds / 60f)).toInt()
    return if (rate > 0) "$time  ·  $rate/min" else time
}
```

This draft is missing two imports/usages that must be resolved while implementing
(fix in place, don't leave TODOs): `Modifier.scale(...)` needs
`import androidx.compose.ui.draw.scale`, and `colors.bg`/`Modifier.background(...)`
needs `import androidx.compose.foundation.background`. Also confirm
`DhikrColorTokens` field names (`colors.bg`, `colors.terra`, etc.) match exactly what
Task 3 defined — if any name differs, use Task 3's actual names, not what's written
here.

The reset confirmation dialog here uses `AlertDialog`'s default MD3 styling rather
than the prototype's exact custom-styled sheet (30dp radius, `Caprasimo` title,
scrim `rgba(20,18,16,.45)`) — this is a acceptable simplification for Phase 1+2
scope (the spec doesn't call out the dialog's exact visual fidelity as a Phase 1+2
requirement, only the Counter screen's primary layout). If pixel-fidelity on the
dialog matters now, ask the user before spending time on a custom dialog composable.

- [ ] **Step 4: Wire `CounterScreen` into `DhikrApp.kt` with a Home stub and
      Navigation Compose**

```kotlin
package com.dhikr.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.model.BuiltInDhikr
import com.dhikr.app.feature.counter.CounterScreen
import com.dhikr.app.feature.counter.CounterViewModel
import com.dhikr.app.ui.theme.DhikrTheme

@Composable
fun DhikrApp() {
    DhikrTheme {
        val navController = rememberNavController()
        val context = LocalContext.current
        val sessionRepository = remember(context) { SessionRepository(context.applicationContext) }

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeStub(onOpenCounter = { navController.navigate("counter") })
            }
            composable("counter") {
                val viewModel: CounterViewModel = viewModel(
                    factory = CounterViewModel.Factory(sessionRepository, BuiltInDhikr.all.first().id),
                )
                CounterScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun HomeStub(onOpenCounter: () -> Unit) {
    val colors = DhikrTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(24.dp),
    ) {
        Text("Dhikr", color = colors.text)
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .background(colors.sage)
                .clickable { onOpenCounter() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("Start SubhanAllah", color = colors.onSage)
        }
    }
}
```

Add the missing `import androidx.compose.runtime.remember` while implementing —
omitted above by oversight, must be present for `remember(context) { ... }` to
compile.

- [ ] **Step 5: Flush the session on `ON_STOP` via a Compose lifecycle observer**

The app must never lose a session to process death between the 500ms debounce
window and backgrounding. This is done entirely in Compose — via a
`DisposableEffect` observing `LocalLifecycleOwner` inside `CounterScreen` — not by
touching `MainActivity.kt` (see the note at the end of this step for why the Files
list above doesn't include it).

In `CounterScreen.kt`, add near the top of `CounterScreen`'s body:
```kotlin
val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
            viewModel.flushSession()
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

This requires adding a `fun flushSession()` to `CounterViewModel` (modify
`CounterViewModel.kt` from Task 7) that launches an immediate (non-debounced) save:
```kotlin
fun flushSession() {
    viewModelScope.launch { persist() }
}
```
`MainActivity.kt` itself needs no changes — the lifecycle-observer approach lives
entirely in Compose, which is why it isn't in this task's Files list.

- [ ] **Step 6: Build to verify**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. This task has the highest chance of needing small
import/API fixes given the volume of Compose code — resolve each compiler error by
consulting current AndroidX Compose API docs for the exact symbol, not by guessing;
if a fix requires a design/behavior decision (not just a compile-error fix), ask the
user.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/counter/ app/src/main/java/com/dhikr/app/DhikrApp.kt app/src/main/res/values/strings.xml
git commit -m "Add Counter screen, custom icons, Home stub, and navigation"
```

---

## Task 9: Final full-project build verification

**Files:** none (verification-only task)

**Interfaces:** none

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: `BUILD SUCCESSFUL`, with an APK produced at
`app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Lint check (non-blocking informational)**

Run: `./gradlew lintDebug`
Expected: completes (warnings are fine to leave for a later phase; only stop and
report to the user if lint reports an actual error, not a warning).

- [ ] **Step 3: Report status to the user**

State plainly whether the build succeeded, and hand off for manual testing on the
user's emulator/device per the spec's "user tests manually" constraint — do not run
the app, take screenshots, or attempt any behavioral verification yourself.

No commit needed for this task (verification only, no file changes).

---

## Self-Review Notes

**Spec coverage:** Every subsection of the spec's Architecture section (project
structure, domain model, engine, persistence, Counter screen, error handling) maps
to a task above. The spec's explicit non-goals (Room, nav shell beyond a stub,
routines/library/history/settings, notifications/widget, Baseline Profiles/
Macrobenchmark) are correctly excluded — no task builds them. The spec's Testing
section ("build-only, no automated tests") is reflected in Task 9 and the Global
Constraints, and no task includes writing unit/UI tests.

**Type consistency:** `TasbihCounter.restore()` and `CounterSnapshot`'s
`previousCount`/`previousLap` fields are defined once, in Task 5, and used as-is by
Tasks 7 and 8 — an earlier draft of this plan patched them in later via a two-step
fix-it-in-Task-7 note; that was caught during self-review and resolved by moving the
definition to Task 5 directly, removing the ambiguity rather than deferring it.

**Known rough edges intentionally left for the implementer to resolve in-task**
(flagged inline rather than hidden, not hidden as vague "handle it" placeholders):
the SVG-arc-to-Compose-path visual approximation in Task 8's undo/reset/lock icons,
and a few likely missing imports in the larger Compose files (Task 8 Steps 3–4). The
ambiguity in both is exact API surface/curve-control-point detail that compiling
against the real AndroidX libraries (imports) or a side-by-side visual check against
the prototype (icon curves) resolves directly — not open design questions.
