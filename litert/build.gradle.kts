plugins {
    alias(libs.plugins.android.library)
}

val litertVersion = "1.0.1"

android {
    namespace = "id.my.daniza.litert"
    compileSdk = 37

    defaultConfig {
        minSdk = 27

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

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/cpp/litert_sdk/jni")
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
}

// ── LiteRT native SDK extraction ────────────────────────────────────────
tasks.register<Sync>("extractLitertSdk") {
    description = "Extracts LiteRT C headers and native libs from AAR"
    group = "litert"

    into("src/main/cpp/litert_sdk")

    from(
        configurations.detachedConfiguration(
            dependencies.create("com.google.ai.edge.litert:litert:$litertVersion")
        ).files.map { zipTree(it) }
    ) {
        include("headers/**", "jni/**")
    }

    preserve {
        include("headers/**", "jni/**")
    }
}

tasks.named("preBuild") { dependsOn("extractLitertSdk") }

dependencies {
    implementation(libs.androidx.core.ktx)
}
