plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yuku.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yuku.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
}

// The launcher's static shortcuts (res/xml/shortcuts.xml) are generated rather
// than checked in, because their intent needs a LITERAL package name and there
// is no way to put a variable one in a resource: `${applicationId}` is only
// substituted into AndroidManifest.xml, and a @string reference is read as raw
// text by the framework's shortcut parser. Written from the variant's own
// applicationId, so the debug suffix is accounted for by construction.
abstract class GenerateShortcutsTask : DefaultTask() {
    @get:InputFile
    abstract val template: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val xml = outputDir.get().asFile.resolve("xml").apply { mkdirs() }
        xml.resolve("shortcuts.xml").writeText(
            template.get().asFile.readText().replace(
                "android:targetPackage=\"\${applicationId}\"",
                "android:targetPackage=\"${applicationId.get()}\""
            )
        )
    }
}

androidComponents {
    onVariants { variant ->
        val generate = tasks.register<GenerateShortcutsTask>(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}Shortcuts"
        ) {
            template.set(layout.projectDirectory.file("src/main/shortcuts-template.xml"))
            applicationId.set(variant.applicationId)
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            generate,
            GenerateShortcutsTask::outputDir
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.webkit)
    // Makes us a Custom Tabs provider: the abstract CustomTabsService other
    // apps bind to, plus the extra keys their CustomTabsIntent puts on the
    // VIEW intent. See CustomTabsProvider / CustomTabRequest.
    implementation(libs.androidx.browser)
    implementation(libs.androidx.swiperefreshlayout)
    // The device's own PIN/pattern/fingerprint, in front of the saved-password
    // list. Done with the library rather than by hand because "confirm it's
    // you, however this phone is unlocked" is four different APIs across the
    // levels this app supports — and it is what forces MainActivity to be a
    // FragmentActivity, which is all BiometricPrompt will take.
    implementation(libs.androidx.biometric)
    // Pulled forward off the fragment 1.2.5 that biometric 1.1.0 depends on,
    // and it is a bug fix rather than housekeeping: that FragmentActivity
    // implements ActivityCompat.RequestPermissionsRequestCodeValidator, which
    // rejects any request code with bits above the low 16 — and the request
    // codes ActivityResultRegistry generates are random across the whole int.
    // So every runtime permission asked for through the ActivityResult APIs
    // (the site-permission path, and the download one on API 28) threw
    // "Can only use lower 16 bits for requestCode" instead of showing the
    // system's dialog. Fragment dropped that validator with its own move to
    // the ActivityResult APIs; 1.2.5 predates it.
    implementation(libs.androidx.fragment)
    implementation(libs.okhttp)
    implementation(libs.androidsvg)

    debugImplementation(libs.androidx.ui.tooling)
}
