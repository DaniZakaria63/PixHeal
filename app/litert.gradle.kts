// LiteRT (TensorFlow Lite) native SDK integration
// Extracts C headers + prebuilt .so files from the official Google Maven AAR
// before CMake runs. Nothing committed — everything comes from Maven.

val litertVersion = "1.0.1"

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
