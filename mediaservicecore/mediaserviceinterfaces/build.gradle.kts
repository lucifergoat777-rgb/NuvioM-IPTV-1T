plugins { id("com.android.library") }

android {
    namespace = "com.liskovsoft.mediaserviceinterfaces"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":mediaservicecore:sharedutils"))
    api("io.reactivex.rxjava2:rxjava:2.2.21")
    api("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("androidx.annotation:annotation:1.9.1")
}
