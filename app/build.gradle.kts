import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.x ships the Compose compiler with the language itself, as its
    // own plugin, instead of a separately versioned extension that had to be
    // matched to the Kotlin version by hand. Replaces the composeOptions block.
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Real signing details, if they exist.
 *
 * Looked for in `~/JApps-Signing/keystore.properties` first — the same key the
 * five JApps sign with, despite this not being one of them. One key per author
 * rather than one per app, because the failure that actually matters here is
 * losing a keystore: every extra key is another thing whose loss would strand
 * an installed app with no way to ever update it. Nothing is shared in the
 * other direction — this app has no LicenceProvider and no activation.
 *
 * A copy in the project root still wins if this is a fresh clone somewhere
 * else. Absent both, the build falls back to the debug key.
 */
val releaseKeystore = Properties().apply {
    val shared = rootProject.file(
        System.getProperty("user.home") + "/JApps-Signing/keystore.properties")
    val local = rootProject.file("keystore.properties")
    val file = if (shared.exists()) shared else local
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.filexplor.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.filexplor.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // The fallback, so `assembleRelease` works on a fresh clone with nothing
        // set up. Fine for measuring against a debug build; not fine for anything
        // you hand to another person — see the release build type below.
        create("sideload") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // Created only when keystore.properties is present, so the build never
        // demands a key that isn't there.
        if (releaseKeystore.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(releaseKeystore.getProperty("storeFile"))
                storePassword = releaseKeystore.getProperty("storePassword")
                keyAlias = releaseKeystore.getProperty("keyAlias")
                keyPassword = releaseKeystore.getProperty("keyPassword")

                // All three schemes, stated rather than inferred. AGP decides
                // v1 from minSdk and leaves it off at 26 — right as far as
                // Android goes, which has preferred v2 since 7.0. But this is
                // sideloaded from a file, and the tools someone checks it with
                // can be older than their phone: jarsigner and a good few
                // file-manager installers read v1 and nothing else.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Left off for now. The point of this build is to isolate one
            // variable — a debug APK is marked debuggable, which stops ART from
            // optimising properly and is the usual reason Compose stutters for
            // the first few interactions and then settles. Turning R8 on at the
            // same time would confound that, and needs keep rules checking for
            // sshj and smbj first.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // A real key the moment one exists, the debug key until then.
            //
            // Which one signs the first build people install matters more than it
            // looks: Android identifies an app by package name *and* signature, so
            // changing the key later is not an update — every installed copy has
            // to be uninstalled first, taking its events with it.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // The SMB/SFTP/FTP stack drags in several jars that each ship their own
    // copies of these metadata files; without excluding them the packager fails
    // on duplicates.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/INDEX.LIST",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }
}

// Tells the Compose compiler which outside types are actually immutable, so
// composables keyed on them can be skipped instead of recomposed on every
// unrelated state change. See compose_stability.conf. Under Kotlin 1.9 this
// was hand-rolled into freeCompilerArgs; the plugin has a typed setting now.
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    // Declared rather than left to arrive through material3. Canvas, the
    // gesture detectors, the lazy grids and the preview pager are all used
    // directly, and a direct dependency that is only satisfied transitively
    // breaks the day the library in the middle reorganises its own.
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Thumbnails for the pictures a file list runs into, and nothing more.
    // No image-loading library: a list shows one small bitmap per visible row,
    // which the platform decoder does in a few lines, and a caching stack would
    // be the largest dependency in the app for the least of its work.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // The unlock prompt in front of saved servers. Biometric where there is a
    // sensor, the phone's own PIN, pattern or password where there is not —
    // one prompt, the OS decides which it can offer.
    implementation("androidx.biometric:biometric:1.1.0")

    // Arrives transitively through biometric; named because BiometricPrompt
    // takes a FragmentActivity and MainActivity therefore extends one.
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Network storage protocols. Each is pure-Java and works on API 26+.
    implementation("commons-net:commons-net:3.10.0")          // FTP / FTPS
    implementation("com.hierynomus:sshj:0.38.0")              // SFTP
    implementation("com.hierynomus:smbj:0.12.2")              // SMB2/3
    // sshj and smbj both log through slf4j; without a binding they spam a
    // warning on every call. NOP discards it rather than adding a log pipeline.
    implementation("org.slf4j:slf4j-nop:1.7.36")

    testImplementation("junit:junit:4.13.2")
}
