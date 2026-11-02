rootProject.name = "Nuvio"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // MediaServiceCore (YouTube engine): Cronet is published on Google's maven under org.chromium
        google {
            mavenContent { includeGroupAndSubgroups("org.chromium") }
        }
        // ... and a couple of libraries come from JitPack
        maven("https://jitpack.io") {
            mavenContent { includeGroupAndSubgroups("com.github") }
        }
    }
}

include(":composeApp")
include(":androidApp")

// SmartTube's YouTube engine (MediaServiceCore, MIT). See mediaservicecore/LICENSE-*.
include(":mediaservicecore:mediaserviceinterfaces")
include(":mediaservicecore:youtubeapi")
include(":mediaservicecore:sharedutils")
include(":mediaservicecore:commons-io")
include(":mediaservicecore:j2v8")
