plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

apply(from = "litert.gradle.kts")

android {
    namespace = "id.my.daniza.pixheal"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.my.daniza.pixheal"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DLITERT_SDK_DIR=${layout.projectDirectory}/src/main/cpp/litert_sdk"
            }
        }
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
        }
        create("prod") {
            dimension = "env"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/cpp/litert_sdk/jni")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
