import de.undercouch.gradle.tasks.download.Download
import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.vanniktech.mavenPublish)
  alias(libs.plugins.download)
  alias(libs.plugins.ktfmt)
}

ktfmt { googleStyle() }

group = "fr.axl-lvy"

version = "0.1.0-alpha.3"

kotlin {
  jvm()
  android {
    namespace = "fr.axl_lvy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    withHostTestBuilder {}
    withDeviceTestBuilder { sourceSetTreeName = "test" }
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
      testTask { useKarma { useFirefoxHeadless() } }
    }
    binaries.executable()
  }

  sourceSets {
    val jvmCommon by creating { dependsOn(commonMain.get()) }
    jvmMain.get().dependsOn(jvmCommon)
    androidMain.get().dependsOn(jvmCommon)

    commonMain.dependencies { implementation(libs.kotlinx.coroutines.core) }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotest.assertions.core)
      implementation(libs.kotlinx.coroutines.test)
    }

    androidMain.dependencies { implementation(libs.android.startup) }
  }
}

dependencies {
  "androidDeviceTestImplementation"(libs.runner)
  "androidDeviceTestImplementation"(libs.core)
}

publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/Axl-Lvy/Stockfish-Multiplatform")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}

mavenPublishing {
  coordinates(group.toString(), "stockfish-multiplatform", version.toString())

  pom {
    name = "Stockfish Multiplatform"
    description =
      "A multiplatform implementation of Stockfish chess engine for Android, iOS, and JVM."
    inceptionYear = "2026"
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
val stockfishBaseUrl = "https://github.com/official-stockfish/Stockfish/releases/download/sf_18"

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
  src("https://github.com/official-stockfish/Stockfish/archive/refs/tags/sf_18.tar.gz")
  dest(layout.buildDirectory.file("stockfish-src.tar.gz"))
  onlyIfModified(true)
  outputs.dir(layout.projectDirectory.dir("cpp/stockfish"))
  doLast {
    copy {
      from(tarTree(resources.gzip(layout.buildDirectory.file("stockfish-src.tar.gz")))) {
        include("Stockfish-sf_18/src/**")
        eachFile { path = path.replaceFirst("Stockfish-sf_18/src/", "stockfish/") }
        includeEmptyDirs = false
      }
      into(layout.projectDirectory.dir("cpp"))
    }
  }
}

// Download NNUE network files required by the Stockfish engine
tasks.register<Download>("downloadNnueBig") {
  description = "Download Stockfish big NNUE network"
  group = "Resources"
  src("https://tests.stockfishchess.org/api/nn/nn-c288c895ea92.nnue")
  dest(layout.projectDirectory.file("src/jvmMain/resources/stockfish/nn-c288c895ea92.nnue"))
  onlyIfModified(true)
  overwrite(false)
}

tasks.register<Download>("downloadNnueSmall") {
  description = "Download Stockfish small NNUE network"
  group = "Resources"
  src("https://tests.stockfishchess.org/api/nn/nn-37f18f62d772.nnue")
  dest(layout.projectDirectory.file("src/jvmMain/resources/stockfish/nn-37f18f62d772.nnue"))
  onlyIfModified(true)
  overwrite(false)
}

tasks.register("downloadNnueNetworks") {
  description = "Download all NNUE network files"
  group = "Resources"
  dependsOn("downloadNnueBig", "downloadNnueSmall")
}

// Compile JVM native library using CMake on the host machine
tasks.register("compileJvmNative") {
  description = "Compile JVM native library using CMake on the host machine"
  group = "Resources"
  dependsOn("downloadStockfishSource")
  inputs.dir(layout.projectDirectory.dir("src/jvmMain/cpp"))
  inputs.dir(layout.projectDirectory.dir("cpp/stockfish")).optional()
  val jvmLibName =
    if (System.getProperty("os.name").lowercase().contains("mac")) "libstockfishjni.dylib"
    else "libstockfishjni.so"
  outputs.file(layout.projectDirectory.file("src/jvmMain/resources/stockfish/$jvmLibName"))
  doLast {
    val buildDir = layout.buildDirectory.dir("jvm-native").get().asFile
    buildDir.mkdirs()
    val cmakeSrcDir = layout.projectDirectory.dir("src/jvmMain/cpp").asFile.absolutePath
    exec {
      commandLine("cmake", cmakeSrcDir, "-B", buildDir.absolutePath, "-DCMAKE_BUILD_TYPE=Release")
    }
    exec { commandLine("cmake", "--build", buildDir.absolutePath, "--config", "Release") }
    val osName = System.getProperty("os.name").lowercase()
    val libName =
      when {
        osName.contains("mac") -> "libstockfishjni.dylib"
        else -> "libstockfishjni.so"
      }
    val nativeLib = fileTree(buildDir) { include("**/$libName") }.files.first()
    val destDir = layout.projectDirectory.dir("src/jvmMain/resources/stockfish").asFile
    destDir.mkdirs()
    copy {
      from(nativeLib)
      into(destDir)
    }
    val androidHostTestJniLibsDir =
      layout.projectDirectory.dir("src/androidHostTest/jniLibs").asFile
    androidHostTestJniLibsDir.mkdirs()
    copy {
      from(nativeLib)
      into(androidHostTestJniLibsDir)
    }
  }
}

// iOS
tasks.register<Download>("downloadStockfishIOS") {
  description = "Download Stockfish binary for IOS"
  group = "Resources"
  dependsOn("createResourceDirectories")
  src("$stockfishBaseUrl/stockfish-macos-m1-apple-silicon.tar")
  dest(
    layout.projectDirectory.file(
      "src/iosMain/resources/stockfish/stockfish-macos-m1-apple-silicon.tar"
    )
  )
  onlyIfModified(true)
  doLast {
    copy {
      from(
        tarTree(
          layout.projectDirectory.file(
            "src/iosMain/resources/stockfish/stockfish-macos-m1-apple-silicon.tar"
          )
        )
      )
      into(layout.projectDirectory.dir("src/iosMain/resources/stockfish"))
      include("stockfish/stockfish-macos-m1-apple-silicon")
      rename("stockfish/stockfish-macos-m1-apple-silicon", "stockfish")
    }
    delete(
      layout.projectDirectory.file(
        "src/iosMain/resources/stockfish/stockfish-macos-m1-apple-silicon.tar"
      )
    )
  }
}

// WASM - Download Stockfish.js lite multithreaded from npm package
tasks.register<Download>("downloadStockfishWasmPackage") {
  description = "Download Stockfish.js npm package"
  group = "Resources"
  src("https://registry.npmjs.org/stockfish/-/stockfish-18.0.5.tgz")
  dest(layout.buildDirectory.file("stockfish-js-package.tgz"))
  onlyIfModified(true)
}

tasks.register("extractStockfishWasm") {
  description = "Extract Stockfish.js lite multithreaded files"
  group = "Resources"
  dependsOn("downloadStockfishWasmPackage", "createResourceDirectories")
  inputs.file(layout.buildDirectory.file("stockfish-js-package.tgz"))
  outputs.files(
    layout.projectDirectory.file("src/wasmJsMain/resources/stockfish/stockfish-18.js"),
    layout.projectDirectory.file("src/wasmJsMain/resources/stockfish/stockfish-18.wasm"),
  )
  doLast {
    copy {
      from(tarTree(resources.gzip(layout.buildDirectory.file("stockfish-js-package.tgz"))))
      into(layout.buildDirectory.dir("stockfish-js-extracted"))
    }
    copy {
      from(layout.buildDirectory.file("stockfish-js-extracted/package/bin/stockfish-18.js"))
      from(layout.buildDirectory.file("stockfish-js-extracted/package/bin/stockfish-18.wasm"))
      into(layout.projectDirectory.dir("src/wasmJsMain/resources/stockfish"))
    }
    delete(layout.buildDirectory.dir("stockfish-js-extracted"))
  }
}

// Register a task that downloads/compiles everything needed
tasks.register("DownloadCompile") {
  description = "Downloads Stockfish source and compiles native libraries for all platforms"
  group = "Resources"
  dependsOn(
    "downloadStockfishSource",
    "compileJvmNative",
    "compileAndroidNative",
    "downloadStockfishIOS",
    "extractStockfishWasm",
  )
}

tasks.named("jvmProcessResources") { dependsOn("downloadNnueNetworks", "compileJvmNative") }

tasks.named("wasmJsProcessResources") { dependsOn("extractStockfishWasm") }

afterEvaluate {
  tasks
    .withType<com.android.build.gradle.tasks.MergeSourceSetFolders>()
    .matching { it.name.contains("Assets") }
    .configureEach {
      dependsOn("copyNnueToAndroid")
      sourceFolderInputs.from(layout.projectDirectory.dir("src/androidMain/assets"))
      doLast {
        copy {
          from(layout.projectDirectory.dir("src/androidMain/assets"))
          into(outputDir)
        }
      }
    }
  tasks
    .matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }
    .configureEach { dependsOn("compileAndroidNative") }
}

tasks.named("clean") {
  doLast {
    delete(fileTree("src") { include("**/resources/stockfish/**") })
    delete(layout.projectDirectory.dir(".cxx"))
    delete(layout.projectDirectory.dir("cpp/stockfish"))
    delete(layout.projectDirectory.dir("src/androidHostTest/jniLibs"))
    delete(layout.projectDirectory.dir("src/androidMain/jniLibs"))
    delete(layout.projectDirectory.dir("src/androidMain/assets/stockfish"))
    delete(layout.projectDirectory.dir("src/androidHostTest/resources/stockfish"))
    logger.lifecycle("Cleaned Stockfish resources and source directories")
  }
}

// Copy NNUE files to Android assets and host test resources
tasks.register("copyNnueToAndroid") {
  description = "Copy NNUE network files to Android assets and host test resources"
  group = "Resources"
  dependsOn("downloadNnueNetworks")
  doLast {
    val nnueFiles = listOf("nn-c288c895ea92.nnue", "nn-37f18f62d772.nnue")
    val dirs =
      listOf(
        layout.projectDirectory.dir("src/androidMain/assets/stockfish").asFile,
        layout.projectDirectory.dir("src/androidHostTest/resources/stockfish").asFile,
      )
    for (dir in dirs) {
      dir.mkdirs()
      for (nnue in nnueFiles) {
        val src = layout.projectDirectory.file("src/jvmMain/resources/stockfish/$nnue").asFile
        if (src.exists()) src.copyTo(file("${dir.absolutePath}/$nnue"), overwrite = true)
      }
    }
  }
}

// Compile native library for production Android using NDK
tasks.register("compileAndroidNative") {
  description = "Compile native library for Android using NDK CMake toolchain"
  group = "Resources"
  dependsOn("downloadStockfishSource")
  inputs.dir(layout.projectDirectory.dir("src/androidMain/cpp"))
  inputs.dir(layout.projectDirectory.dir("cpp/stockfish")).optional()
  outputs.dir(layout.projectDirectory.dir("src/androidMain/jniLibs"))
  doLast {
    val localProps = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use { localProps.load(it) }
    val sdkDir =
      localProps.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("Android SDK not found. Set sdk.dir in local.properties or ANDROID_HOME.")
    val ndkHome =
      localProps.getProperty("ndk.dir")
        ?: file(sdkDir)
          .resolve("ndk")
          .listFiles()
          ?.filter { it.isDirectory }
          ?.maxByOrNull { it.name }
          ?.absolutePath
        ?: error("NDK not found in $sdkDir. Install it via Android Studio SDK Manager.")
    val cmakeSrcDir = layout.projectDirectory.dir("src/androidMain/cpp").asFile.absolutePath
    val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    for (abi in abis) {
      val buildDir = layout.buildDirectory.dir("android-native/$abi").get().asFile
      buildDir.mkdirs()
      exec {
        commandLine(
          "cmake",
          cmakeSrcDir,
          "-B",
          buildDir.absolutePath,
          "-DCMAKE_TOOLCHAIN_FILE=$ndkHome/build/cmake/android.toolchain.cmake",
          "-DANDROID_ABI=$abi",
          "-DANDROID_PLATFORM=android-21",
          "-DCMAKE_BUILD_TYPE=Release",
        )
      }
      exec { commandLine("cmake", "--build", buildDir.absolutePath, "--config", "Release") }
      val destDir = layout.projectDirectory.dir("src/androidMain/jniLibs/$abi").asFile
      destDir.mkdirs()
      copy {
        from(buildDir) { include("libstockfishjni.so") }
        into(destDir)
      }
    }
  }
}
