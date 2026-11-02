plugins {
    id("com.android.library")
}

android {
    namespace = "com.liskovsoft.sharedutils"
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

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.core:core-ktx:1.15.0")

    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-brotli:4.12.0")
    implementation("com.localebro:okhttpprofiler:1.0.8") {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }

    implementation("org.chromium.net:cronet-api:105.5195.68")
    implementation("org.chromium.net:cronet-embedded:105.5195.68")

    implementation("com.google.code.gson:gson:2.10.1")
    api("io.reactivex.rxjava2:rxandroid:2.1.1")
    api("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("com.jakewharton:disklrucache:2.0.2")
    implementation("info.guardianproject.netcipher:netcipher:2.1.0")
    implementation("dnsjava:dnsjava:2.1.9")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }
