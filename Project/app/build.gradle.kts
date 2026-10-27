import java.util.Properties
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystorePropsFile.exists() &&
        keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

val syncTemplates = tasks.register<Copy>("syncTemplates") {
    from(rootProject.file("docs/templates")) {
        include("题库模板.*")
    }
    into(layout.projectDirectory.dir("src/main/assets/templates"))
}

tasks.named("preBuild") { dependsOn(syncTemplates) }

android {
    namespace = "com.banana.search"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.banana.search"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += listOf("json")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.mlkit.text.recognition.chinese)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
