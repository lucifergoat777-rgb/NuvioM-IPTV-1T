plugins {
    id("com.android.library")
}

android {
    namespace = "com.liskovsoft.youtubeapi"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":mediaservicecore:commons-io"))
    implementation(project(":mediaservicecore:sharedutils"))
    api(project(":mediaservicecore:mediaserviceinterfaces"))
    implementation(project(":mediaservicecore:j2v8"))

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.jayway.jsonpath:json-path:2.4.0")

    implementation("com.google.net.cronet:cronet-okhttp:0.1.0")
    implementation("org.chromium.net:cronet-api:105.5195.68")

    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.github.florianingerl.util:regex:1.1.11")
    implementation("androidx.webkit:webkit:1.11.0")
    compileOnly("com.grack:nanojson:1.7") // runtime copy comes from NewPipe extractor in :app
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }
