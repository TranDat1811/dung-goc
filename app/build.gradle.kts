import java.io.File
import java.net.URI
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "vn.dunggoc.camera"
    compileSdk = 35
    defaultConfig {
        applicationId = "vn.dunggoc.camera"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    androidResources { noCompress += "task" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/poseAssets"))
}

// Build-time download only. The installed APK never needs a network connection.
// A fixed model revision is cached as a generated asset, never fetched at runtime.
val preparePoseModel by tasks.registering {
    val model = layout.buildDirectory.file("generated/poseAssets/pose_landmarker_lite.task")
    val suppliedModel = providers.gradleProperty("poseModelPath")
    inputs.property("modelRevision", "pose_landmarker_lite/float16/1")
    if (suppliedModel.isPresent) inputs.file(suppliedModel.map { file(it) })
    outputs.file(model)
    doLast {
        val target = model.get().asFile
        target.parentFile.mkdirs()
        val localModel = providers.gradleProperty("poseModelPath").orNull?.let(::file)
        val temporary = File(target.parentFile, "${target.name}.part")
        try {
            if (localModel != null) {
                require(localModel.isFile) { "poseModelPath must point to a model .task file" }
                localModel.copyTo(temporary, overwrite = true)
            } else {
                val connection = URI(
                    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
                ).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                }
                connection.getInputStream().use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            }
            require(temporary.length() > 1_000_000) { "Incomplete pose model download" }
            ZipFile(temporary).use { zip ->
                require(zip.getEntry("pose_landmarks_detector.tflite") != null) { "Invalid pose model archive" }
                require(zip.getEntry("pose_detector.tflite") != null) { "Missing pose detector" }
            }
            temporary.copyTo(target, overwrite = true)
        } finally {
            temporary.delete()
        }
    }
}
tasks.named("preBuild") { dependsOn(preparePoseModel) }

dependencies {
    implementation(project(":guidance"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.exifinterface:exifinterface:1.4.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.21")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("org.mockito:mockito-android:5.15.2")
}

