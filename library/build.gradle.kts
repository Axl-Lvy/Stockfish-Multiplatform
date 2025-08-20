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
}

group = "io.github.axl-lvy"
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
        }
        binaries.executable()
    }

    sourceSets {
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Add these to your androidTestImplementation
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
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

// Base URL for Stockfish binaries
val stockfishBaseUrl = "https://github.com/official-stockfish/Stockfish/releases/download/sf_17.1"

// Create resource directories
tasks.register("createResourceDirectories") {
    doLast {
        mkdir("src/jvmMain/resources/stockfish")
        mkdir("src/androidMain/resources/stockfish")
        mkdir("src/iosMain/resources/stockfish")
        mkdir("src/wasmJsMain/resources/stockfish")
    }
}

// JVM - Windows
tasks.register<Download>("downloadStockfishWindows") {
    dependsOn("createResourceDirectories")
    src("$stockfishBaseUrl/stockfish-windows-x86-64.zip")
    dest(layout.projectDirectory.file("src/jvmMain/resources/stockfish/stockfish-windows-x86-64.zip"))
    onlyIfModified(true)
    doLast {
        copy {
            from(zipTree(layout.projectDirectory.file("src/jvmMain/resources/stockfish/stockfish-windows-x86-64.zip")))
            into(layout.projectDirectory.dir("src/jvmMain/resources"))
            include("stockfish/stockfish-windows-x86-64.exe")
        }
        delete(layout.projectDirectory.file("src/jvmMain/resources/stockfish/stockfish-windows-x86-64.zip"))
    }
}

// JVM - macOS
tasks.register<Download>("downloadStockfishMacOS") {
    dependsOn("createResourceDirectories")
    src("$stockfishBaseUrl/stockfish-macos-x86-64.tar")
    dest(layout.projectDirectory.file("src/jvmMain/resources/stockfish/stockfish-macos-x86-64.tar"))
    onlyIfModified(true)
    doLast {
        copy {
            from(tarTree(layout.projectDirectory.file("src/jvmMain/resources/stockfish/stockfish-macos-x86-64.tar")))
            into(layout.projectDirectory.dir("src/jvmMain/resources"))
            include("stockfish/stockfish-macos-x86-64")
        }
        delete(layout.projectDirectory.file("src/jvmMain/resources/stockfish/stockfish-macos-x86-64.tar"))
    }
}

// JVM - Linux
tasks.register<Download>("downloadStockfishLinux") {
    dependsOn("createResourceDirectories")
    src("$stockfishBaseUrl/stockfish-ubuntu-x86-64.tar")
    dest(layout.projectDirectory.file("src/jvmMain/resources/stockfish/stockfish-ubuntu-x86-64.tar"))
    onlyIfModified(true)
    doLast {
        copy {
            from(tarTree(layout.projectDirectory.file("src/jvmMain/resources/stockfish/stockfish-ubuntu-x86-64.tar")))
            into(layout.projectDirectory.dir("src/jvmMain/resources"))
            include("stockfish/stockfish-ubuntu-x86-64")
        }
        delete(layout.projectDirectory.file("src/jvmMain/resources/stockfish/stockfish-ubuntu-x86-64.tar"))
    }
}

// Android
tasks.register<Download>("downloadStockfishAndroidArm64") {
    dependsOn("createResourceDirectories")
    src("$stockfishBaseUrl/stockfish-android-armv8.tar")
    dest(layout.projectDirectory.file("src/androidMain/resources/stockfish/stockfish-android-armv8.tar"))
    onlyIfModified(true)
    doLast {
        copy {
            from(tarTree(layout.projectDirectory.file("src/androidMain/resources/stockfish/stockfish-android-armv8.tar")))
            into(layout.projectDirectory.dir("src/androidMain/resources"))
            include("stockfish/stockfish-android-armv8")
            rename("stockfish/stockfish-android-armv8", "stockfish-arm64-v8a")
        }
        delete(layout.projectDirectory.file("src/androidMain/resources/stockfish/stockfish-android-armv8.tar"))
    }
}

tasks.register<Download>("downloadStockfishAndroidArm32") {
    dependsOn("createResourceDirectories")
    src("$stockfishBaseUrl/stockfish-android-armv7.tar")
    dest(layout.projectDirectory.file("src/androidMain/resources/stockfish/stockfish-android-armv7.tar"))
    onlyIfModified(true)
    doLast {
        copy {
            from(tarTree(layout.projectDirectory.file("src/androidMain/resources/stockfish/stockfish-android-armv7.tar")))
            into(layout.projectDirectory.dir("src/androidMain/resources"))
            include("stockfish/stockfish-android-armv7")
            rename("stockfish/stockfish-android-armv7", "stockfish-armeabi-v7a")
        }
        delete(layout.projectDirectory.file("src/androidMain/resources/stockfish/stockfish-android-armv7.tar"))
    }
}

// iOS
tasks.register<Download>("downloadStockfishIOS") {
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
    dependsOn("createResourceDirectories")
    src("https://registry.npmjs.org/stockfish.wasm/-/stockfish.wasm-0.10.0.tgz")
    dest(layout.buildDirectory.file("stockfish-wasm-package.tgz"))
    onlyIfModified(true)
}

tasks.register("extractStockfishWasm") {
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

// Register a task that downloads all the binaries
tasks.register("downloadStockfishBinaries") {
    dependsOn(
        "downloadStockfishWindows",
        "downloadStockfishMacOS",
        "downloadStockfishLinux",
        "downloadStockfishAndroidArm64",
        "downloadStockfishAndroidArm32",
        "downloadStockfishIOS",
        "extractStockfishWasm"
    )
    description = "Downloads all Stockfish binaries for all platforms"
    group = "stockfish"
}

tasks.named("clean") {
    doLast {
        delete(
            fileTree("src") {
                include("**/resources/stockfish/**")
            }
        )
        logger.lifecycle("Cleaned Stockfish resources directories")
    }
}
