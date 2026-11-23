import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "world.wander.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "world.wander.app"
        minSdk = 34
        targetSdk = 37
        versionCode = 31
        versionName = "0.13.0-alpha"
        // Pixel Fold 10 is 64-bit ARM (H3 ships no x86_64 Android build, so no emulator for now).
        ndk { abiFilters += setOf("arm64-v8a") }
    }
    sourceSets["main"].jniLibs.directories.add(layout.buildDirectory.dir("generated/h3jni").get().asFile.path)

    buildTypes {
        debug {
            applicationIdSuffix = ".alpha"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
            // H3 ships desktop natives as resources; only the Android ones are needed.
            excludes += setOf("linux-*/**", "darwin-*/**", "windows-*/**", "freebsd-*/**", "android-*/**")
        }
    }
}

// H3 ships its Android native code inside the jar as resources; repackage it as proper jniLibs.
val h3Jar by configurations.creating
val extractH3Natives by tasks.registering(Copy::class) {
    from({ h3Jar.map { zipTree(it) } }) {
        include("android-arm64/libh3-java.so")
        eachFile {
            relativePath = RelativePath(true, "arm64-v8a", name)
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/h3jni"))
}
tasks.named("preBuild") { dependsOn(extractH3Natives) }

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":engine"))
    implementation(libs.h3)
    h3Jar(libs.h3) { isTransitive = false }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.play.location)
    implementation(libs.maplibre)
    implementation(libs.jts)
    implementation(libs.health.connect)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.org.json)
}
