plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val generatedSharedPhraseAssets =
    layout.buildDirectory.dir("generated/assets/sharedPhrases")
val syncSharedPhrases by tasks.registering(Sync::class) {
    from(rootProject.layout.projectDirectory.file("../shared/phrases/zh-CN.json")) {
        into("phrases")
    }
    into(generatedSharedPhraseAssets)
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

android {
    namespace = "com.wakemove.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wakemove.android"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets.named("main") {
        assets.directories.add(generatedSharedPhraseAssets.get().asFile.absolutePath)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

tasks.matching {
    it.name.startsWith("merge") && it.name.endsWith("Assets")
}.configureEach {
    dependsOn(syncSharedPhrases)
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mediapipe.tasks.vision)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    debugImplementation(libs.androidx.compose.ui.tooling)
}
