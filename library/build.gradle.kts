import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import de.undercouch.gradle.tasks.download.Download

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.download)
    alias(libs.plugins.ktfmt)
}

group = "fr.axl-lvy"
version = "0.1.0"

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // WebAssembly configuration
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer =
                    (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static =
                            (static ?: mutableListOf()).apply {
                                // Serve sources to debug inside browser
                                add(project.rootDir.path)
                                add(project.projectDir.path)
                            }
                    }
            }
            testTask {
                useKarma {
                    useFirefox()
                    useChrome()
                    useSafari()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jvmCommon by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmCommon)
        androidMain.get().dependsOn(jvmCommon)

        commonMain.dependencies {
                //put your multiplatform dependencies here
            }
        commonTest.dependencies {
                implementation(libs.kotlin.test)
            }

        androidMain.dependencies {
            implementation(libs.android.startup)
        }

        wasmJsMain.dependencies {
        }
    }
}

android {
    namespace = "io.github.axl_lvy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.all {
            it.jvmArgs("-Djava.library.path=${project.file("src/jvmMain/resources/stockfish").absolutePath}")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1+"
        }
    }
}

dependencies {
    // Add these to your androidTestImplementation
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.core)
    androidTestImplementation(libs.runner)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(group.toString(), "library", version.toString())

    pom {
        name = "Stockfish Multiplatform"
        description = "A multiplatform implementation of Stockfish chess engine for Android, iOS, and JVM."
        inceptionYear = "2025"
        url = "https://github.com/axl-lvy/stockfish-multiplatform/"
        licenses {
            license {
                name = "GNU General Public License v3.0"
                url = "https://www.gnu.org/licenses/gpl-3.0.html"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "axl-lvy"
                name = "Axel Levy"
                url = "https://github.com/axl-lvy"
            }
        }
    }
}

// Base URL for Stockfish releases
val stockfishBaseUrl = "https://github.com/official-stockfish/Stockfish/releases/download/sf_17.1"

// Create resource directories
tasks.register("createResourceDirectories") {
    description = "Create resource directories"
    group = "Resources"
    doLast {
        mkdir("src/jvmMain/resources/stockfish")
        mkdir("src/iosMain/resources/stockfish")
        mkdir("src/wasmJsMain/resources/stockfish")
    }
}

// Download Stockfish source code (used by both Android NDK and JVM CMake builds)
tasks.register<Download>("downloadStockfishSource") {
    description = "Download Stockfish source code (used by both Android NDK and JVM CMake builds)"
    group = "Resources"
    src("https://github.com/official-stockfish/Stockfish/archive/refs/tags/sf_17.1.tar.gz")
    dest(layout.buildDirectory.file("stockfish-src.tar.gz"))
    onlyIfModified(true)
    doLast {
        copy {
            from(tarTree(resources.gzip(layout.buildDirectory.file("stockfish-src.tar.gz")))) {
                include("Stockfish-sf_17.1/src/**")
                eachFile {
                    path = path.replaceFirst("Stockfish-sf_17.1/src/", "stockfish/")
                }
                includeEmptyDirs = false
            }
            into(layout.projectDirectory.dir("cpp"))
        }
    }
}

// Compile JVM native library using CMake on the host machine
tasks.register("compileJvmNative") {
    description = "Compile JVM native library using CMake on the host machine"
    group = "Resources"
    dependsOn("downloadStockfishSource")
    doLast {
        val buildDir = layout.buildDirectory.dir("jvm-native").get().asFile
        buildDir.mkdirs()
        val cmakeSrcDir = layout.projectDirectory.dir("src/jvmMain/cpp").asFile.absolutePath
        exec {
            commandLine("cmake", cmakeSrcDir, "-B", buildDir.absolutePath, "-DCMAKE_BUILD_TYPE=Release")
        }
        exec {
            commandLine("cmake", "--build", buildDir.absolutePath, "--config", "Release")
        }
        val osName = System.getProperty("os.name").lowercase()
        val libName = when {
            osName.contains("win") -> "stockfishjni.dll"
            osName.contains("mac") -> "libstockfishjni.dylib"
            else -> "libstockfishjni.so"
        }
        val destDir = layout.projectDirectory.dir("src/jvmMain/resources/stockfish").asFile
        destDir.mkdirs()
        copy {
            from(buildDir) { include(libName) }
            into(destDir)
        }
    }
}

// iOS
tasks.register<Download>("downloadStockfishIOS") {
    description = "Download Stockfish binary for IOS"
    group = "Resources"
    dependsOn("createResourceDirectories")
    src("$stockfishBaseUrl/stockfish-macos-m1-apple-silicon.tar")
    dest(layout.projectDirectory.file("src/iosMain/resources/stockfish/stockfish-macos-m1-apple-silicon.tar"))
    onlyIfModified(true)
    doLast {
        copy {
            from(tarTree(layout.projectDirectory.file("src/iosMain/resources/stockfish/stockfish-macos-m1-apple-silicon.tar")))
            into(layout.projectDirectory.dir("src/iosMain/resources/stockfish"))
            include("stockfish/stockfish-macos-m1-apple-silicon")
            rename("stockfish/stockfish-macos-m1-apple-silicon", "stockfish")
        }
        delete(layout.projectDirectory.file("src/iosMain/resources/stockfish/stockfish-macos-m1-apple-silicon.tar"))
    }
}

// WASM - Download from npm package
tasks.register<Download>("downloadStockfishWasmPackage") {
    description = "Download Stockfish binary for Wasm"
    group = "Resources"
    dependsOn("createResourceDirectories")
    src("https://registry.npmjs.org/stockfish.wasm/-/stockfish.wasm-0.10.0.tgz")
    dest(layout.buildDirectory.file("stockfish-wasm-package.tgz"))
    onlyIfModified(true)
}

tasks.register("extractStockfishWasm") {
    description = "Extract Stockfish Wasm"
    group = "Resources"
    dependsOn("downloadStockfishWasmPackage")
    doLast {
        // Extract from npm package
        copy {
            from(tarTree(resources.gzip(layout.buildDirectory.file("stockfish-wasm-package.tgz"))))
            into(layout.buildDirectory.dir("stockfish-wasm-extracted"))
        }

        // Copy required files to resources directory
        copy {
            from(layout.buildDirectory.file("stockfish-wasm-extracted/package/stockfish.wasm"))
            from(layout.buildDirectory.file("stockfish-wasm-extracted/package/stockfish.js"))
            from(layout.buildDirectory.file("stockfish-wasm-extracted/package/stockfish.worker.js"))
            into(layout.projectDirectory.dir("src/wasmJsMain/resources/stockfish"))
        }
        delete(layout.buildDirectory.file("stockfish-wasm-extracted.tgz"))
        delete(layout.buildDirectory.dir("stockfish-wasm-extracted"))
    }
}

// Register a task that downloads/compiles everything needed
tasks.register("DownloadCompile") {
    description = "Downloads Stockfish source and compiles native libraries for all platforms"
    group = "Resources"
    dependsOn(
        "downloadStockfishSource",
        "compileJvmNative",
        "downloadStockfishIOS",
        "extractStockfishWasm"
    )
}

tasks.named("clean") {
    doLast {
        delete(
            fileTree("src") {
                include("**/resources/stockfish/**")
            }
        )
        delete(layout.projectDirectory.dir(".cxx"))
        delete(layout.projectDirectory.dir("cpp/stockfish"))
        logger.lifecycle("Cleaned Stockfish resources and source directories")
    }
}
