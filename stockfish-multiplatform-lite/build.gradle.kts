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

version = findProperty("library.version") as String

// ---------------------------------------------------------------------------
// Lite variant configuration
// ---------------------------------------------------------------------------
val nnueSmallName = "nn-37f18f62d772.nnue"
// The full module's source root (for sharing Kotlin sources and Stockfish C++ code)
val fullModuleDir = project(":stockfish-multiplatform").projectDir

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

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig {
        outputFileName = "composeApp.js"
        devServer =
          (devServer ?: KotlinWebpackConfig.DevServer()).apply {
            static =
              (static ?: mutableListOf()).apply {
                add(project.rootDir.path)
                add(project.projectDir.path)
              }
          }
      }
      testTask { useKarma { useFirefoxHeadless() } }
    }
    binaries.executable()
  }

  // -----------------------------------------------------------------------
  // Source sets — share all Kotlin source from the full module.
  // Only the generated NnueConfig.kt (in jvmCommon) differs.
  // -----------------------------------------------------------------------
  sourceSets {
    commonMain {
      kotlin.srcDir("$fullModuleDir/src/commonMain/kotlin")
      dependencies { implementation(libs.kotlinx.coroutines.core) }
    }
    commonTest {
      kotlin.srcDir("$fullModuleDir/src/commonTest/kotlin")
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotest.assertions.core)
        implementation(libs.kotlinx.coroutines.test)
      }
    }

    val jvmCommon by creating {
      dependsOn(commonMain.get())
      kotlin.srcDir("$fullModuleDir/src/jvmCommon/kotlin")
      kotlin.srcDir(layout.buildDirectory.dir("generated/nnue"))
    }
    jvmMain {
      dependsOn(jvmCommon)
      kotlin.srcDir("$fullModuleDir/src/jvmMain/kotlin")
    }
    androidMain {
      dependsOn(jvmCommon)
      kotlin.srcDir("$fullModuleDir/src/androidMain/kotlin")
      dependencies { implementation(libs.android.startup) }
    }

    wasmJsMain {
      kotlin.srcDir("$fullModuleDir/src/wasmJsMain/kotlin")
      kotlin.srcDir(layout.buildDirectory.dir("generated/wasmCdn"))
    }

    iosX64Main { kotlin.srcDir("$fullModuleDir/src/iosX64Main/kotlin") }
    iosArm64Main { kotlin.srcDir("$fullModuleDir/src/iosArm64Main/kotlin") }
    iosSimulatorArm64Main { kotlin.srcDir("$fullModuleDir/src/iosSimulatorArm64Main/kotlin") }
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
  coordinates(group.toString(), "stockfish-multiplatform-lite", version.toString())

  pom {
    name = "Stockfish Multiplatform Lite"
    description =
      "A lightweight multiplatform Stockfish chess engine library using a smaller NNUE network."
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

// ---------------------------------------------------------------------------
// Generate NnueConfig.kt with lite NNUE filenames
// ---------------------------------------------------------------------------
tasks.register("generateNnueConfig") {
  description = "Generate NnueConfig.kt with lite NNUE filenames"
  group = "Resources"
  val outputDir = layout.buildDirectory.dir("generated/nnue")
  outputs.dir(outputDir)
  doLast {
    val dir = outputDir.get().asFile
    dir.mkdirs()
    dir
      .resolve("NnueConfig.kt")
      .writeText(
        """
      |package fr.axl_lvy.stockfish_multiplatform
      |
      |internal val NNUE_FILES = listOf("$nnueSmallName")
      """
          .trimMargin()
          .trim() + "\n"
      )
  }
}

tasks.register("generateWasmCdnConfig") {
  description = "Generate WasmCdnConfig.kt with CDN URLs for Stockfish WASM lite files"
  group = "Resources"
  val outputDir = layout.buildDirectory.dir("generated/wasmCdn")
  outputs.dir(outputDir)
  doLast {
    val dir = outputDir.get().asFile
    dir.mkdirs()
    dir
      .resolve("WasmCdnConfig.kt")
      .writeText(
        """
        |package fr.axl_lvy.stockfish_multiplatform
        |
        |internal const val STOCKFISH_JS_CDN_URL = "https://unpkg.com/stockfish@18.0.5/bin/stockfish-18-lite.js"
        |internal const val STOCKFISH_WASM_CDN_URL = "https://unpkg.com/stockfish@18.0.5/bin/stockfish-18-lite.wasm"
        """
          .trimMargin()
          .trim() + "\n"
      )
  }
}

tasks.configureEach {
  if (
    name.contains("compileKotlin") ||
      name.startsWith("compileAndroid") ||
      name.contains("SourcesJar")
  ) {
    dependsOn("generateNnueConfig", "generateWasmCdnConfig")
  }
}

// ---------------------------------------------------------------------------
// Stockfish source patching — add __LITE_NET__ support to evaluate.h
// ---------------------------------------------------------------------------
tasks.register("patchStockfishForLite") {
  description = "Patch Stockfish evaluate.h for __LITE_NET__ support and create lite_nets.h"
  group = "Resources"
  dependsOn(":stockfish-multiplatform:patchStockfishSource")
  val sfDir = file("$fullModuleDir/cpp/stockfish")
  inputs.dir(sfDir).optional()
  doLast {
    // Create lite_nets.h — only uses the small network.
    val liteNetsH = file("$sfDir/lite_nets.h")
    liteNetsH.writeText(
      """
      |#ifndef LITE_NETS_H
      |#define LITE_NETS_H
      |
      |// Lite build: big network is disabled, only the small network is used.
      |#define EvalFileDefaultNameBig ""
      |#define EvalFileDefaultNameSmall "$nnueSmallName"
      |
      |#endif // LITE_NETS_H
      """
        .trimMargin()
        .trim() + "\n"
    )

    // Patch evaluate.h to add #ifdef __LITE_NET__ support
    val evaluateH = file("$sfDir/evaluate.h")
    if (evaluateH.exists()) {
      val content = evaluateH.readText()
      if (!content.contains("__LITE_NET__")) {
        val patched =
          content.replace(
            """#define EvalFileDefaultNameBig "nn-c288c895ea92.nnue"""",
            """#if defined(__LITE_NET__)
#include "lite_nets.h"
#else
#define EvalFileDefaultNameBig "nn-c288c895ea92.nnue"""",
          )
        val patchedFinal =
          patched.replace(
            """#define EvalFileDefaultNameSmall "nn-37f18f62d772.nnue"""",
            """#define EvalFileDefaultNameSmall "nn-37f18f62d772.nnue"
#endif // __LITE_NET__""",
          )
        evaluateH.writeText(patchedFinal)
      }
    }

    // Patch evaluate.cpp — when __LITE_NET__, always use small network only
    val evaluateCpp = file("$sfDir/evaluate.cpp")
    if (evaluateCpp.exists()) {
      var src = evaluateCpp.readText()
      if (!src.contains("__LITE_NET__")) {
        // Patch the main evaluate() function
        src =
          src.replace(
            """    bool smallNet           = use_smallnet(pos);
    auto [psqt, positional] = smallNet ? networks.small.evaluate(pos, accumulators, caches.small)
                                       : networks.big.evaluate(pos, accumulators, caches.big);

    Value nnue = (125 * psqt + 131 * positional) / 128;

    // Re-evaluate the position when higher eval accuracy is worth the time spent
    if (smallNet && (std::abs(nnue) < 277))
    {
        std::tie(psqt, positional) = networks.big.evaluate(pos, accumulators, caches.big);
        nnue                       = (125 * psqt + 131 * positional) / 128;
        smallNet                   = false;
    }""",
            """#ifdef __LITE_NET__
    auto [psqt, positional] = networks.small.evaluate(pos, accumulators, caches.small);
    bool smallNet           = true;
#else
    bool smallNet           = use_smallnet(pos);
    auto [psqt, positional] = smallNet ? networks.small.evaluate(pos, accumulators, caches.small)
                                       : networks.big.evaluate(pos, accumulators, caches.big);
#endif

    Value nnue = (125 * psqt + 131 * positional) / 128;

#ifndef __LITE_NET__
    // Re-evaluate the position when higher eval accuracy is worth the time spent
    if (smallNet && (std::abs(nnue) < 277))
    {
        std::tie(psqt, positional) = networks.big.evaluate(pos, accumulators, caches.big);
        nnue                       = (125 * psqt + 131 * positional) / 128;
        smallNet                   = false;
    }
#endif""",
          )

        // Patch the trace() function
        src =
          src.replace(
            """    auto [psqt, positional] = networks.big.evaluate(pos, *accumulators, caches->big);""",
            """#ifdef __LITE_NET__
    auto [psqt, positional] = networks.small.evaluate(pos, *accumulators, caches->small);
#else
    auto [psqt, positional] = networks.big.evaluate(pos, *accumulators, caches->big);
#endif""",
          )

        evaluateCpp.writeText(src)
      }
    }

    // Patch engine.cpp — skip big network loading/verification when __LITE_NET__
    val engineCpp = file("$sfDir/engine.cpp")
    if (engineCpp.exists()) {
      var src = engineCpp.readText()
      if (!src.contains("__LITE_NET__")) {
        // Patch load_networks()
        src =
          src.replace(
            """        networks_.big.load(binaryDirectory, options["EvalFile"]);
        networks_.small.load(binaryDirectory, options["EvalFileSmall"]);""",
            """#ifndef __LITE_NET__
        networks_.big.load(binaryDirectory, options["EvalFile"]);
#endif
        networks_.small.load(binaryDirectory, options["EvalFileSmall"]);""",
          )

        // Patch verify_networks()
        src =
          src.replace(
            """    networks->big.verify(options["EvalFile"], onVerifyNetworks);
    networks->small.verify(options["EvalFileSmall"], onVerifyNetworks);""",
            """#ifndef __LITE_NET__
    networks->big.verify(options["EvalFile"], onVerifyNetworks);
#endif
    networks->small.verify(options["EvalFileSmall"], onVerifyNetworks);""",
          )

        engineCpp.writeText(src)
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Resource directories
// ---------------------------------------------------------------------------
tasks.register("createResourceDirectories") {
  description = "Create resource directories"
  group = "Resources"
  doLast {
    mkdir("src/jvmMain/resources/stockfish")
    mkdir("src/iosMain/resources/stockfish")
    mkdir("src/wasmJsMain/resources/stockfish")
  }
}

// ---------------------------------------------------------------------------
// Download lite NNUE network
// ---------------------------------------------------------------------------
tasks.register<Download>("downloadNnueSmall") {
  description = "Download Stockfish NNUE small network"
  group = "Resources"
  src("https://tests.stockfishchess.org/api/nn/$nnueSmallName")
  dest(layout.projectDirectory.file("src/jvmMain/resources/stockfish/$nnueSmallName"))
  onlyIfModified(true)
  overwrite(false)
}

tasks.register("downloadNnueNetworks") {
  description = "Download lite NNUE network files"
  group = "Resources"
  dependsOn("downloadNnueSmall")
}

// ---------------------------------------------------------------------------
// Compile JVM native library with __LITE_NET__
// ---------------------------------------------------------------------------
tasks.register("compileJvmNative") {
  description = "Compile JVM native library (lite) using CMake"
  group = "Resources"
  dependsOn(":stockfish-multiplatform:patchStockfishSource", "patchStockfishForLite")
  inputs.dir(layout.projectDirectory.dir("src/jvmMain/cpp"))
  inputs.dir(file("$fullModuleDir/cpp/stockfish")).optional()
  val osName = System.getProperty("os.name").lowercase()
  val jvmLibName =
    when {
      osName.contains("mac") -> "libstockfishjni.dylib"
      osName.contains("win") -> "stockfishjni.dll"
      else -> "libstockfishjni.so"
    }
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
        osName.contains("win") -> "stockfishjni.dll"
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

// ---------------------------------------------------------------------------
// Compile Android native library with __LITE_NET__
// ---------------------------------------------------------------------------
tasks.register("compileAndroidNative") {
  description = "Compile native library for Android (lite) using NDK"
  group = "Resources"
  dependsOn(":stockfish-multiplatform:patchStockfishSource", "patchStockfishForLite")
  inputs.dir(layout.projectDirectory.dir("src/androidMain/cpp"))
  inputs.dir(file("$fullModuleDir/cpp/stockfish")).optional()
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

// ---------------------------------------------------------------------------
// iOS — reuses the same pre-built binary (no lite variant available)
// ---------------------------------------------------------------------------
val stockfishBaseUrl = "https://github.com/official-stockfish/Stockfish/releases/download/sf_18"

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

// ---------------------------------------------------------------------------
// WASM — extract lite variant files, renamed to standard names so the shared
// WasmStockfishEngine.kt works unchanged.
// ---------------------------------------------------------------------------
tasks.register<Download>("downloadStockfishWasmPackage") {
  description = "Download Stockfish.js npm package"
  group = "Resources"
  src("https://registry.npmjs.org/stockfish/-/stockfish-18.0.5.tgz")
  dest(layout.buildDirectory.file("stockfish-js-package.tgz"))
  onlyIfModified(true)
}

tasks.register("extractStockfishWasm") {
  description = "Extract Stockfish.js lite multithreaded files (renamed to standard names)"
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
    // Copy lite files, renaming them to the standard names
    copy {
      from(layout.buildDirectory.file("stockfish-js-extracted/package/bin/stockfish-18-lite.js"))
      from(layout.buildDirectory.file("stockfish-js-extracted/package/bin/stockfish-18-lite.wasm"))
      into(layout.projectDirectory.dir("src/wasmJsMain/resources/stockfish"))
      rename("stockfish-18-lite\\.js", "stockfish-18.js")
      rename("stockfish-18-lite\\.wasm", "stockfish-18.wasm")
    }
    delete(layout.buildDirectory.dir("stockfish-js-extracted"))
  }
}

// ---------------------------------------------------------------------------
// Aggregate download/compile task
// ---------------------------------------------------------------------------
tasks.register("DownloadCompile") {
  description = "Downloads and compiles everything for the lite variant"
  group = "Resources"
  dependsOn(
    "compileJvmNative",
    "compileAndroidNative",
    "downloadStockfishIOS",
    "extractStockfishWasm",
  )
}

tasks.named("jvmProcessResources") { dependsOn("downloadNnueNetworks", "compileJvmNative") }

tasks.withType<Test> {
  testLogging {
    showStandardStreams = true
    showExceptions = true
    showCauses = true
    showStackTraces = true
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
  jvmArgs("-Xss8m")
}

tasks.named("wasmJsProcessResources") { dependsOn("extractStockfishWasm") }

afterEvaluate {
  tasks.matching { it.name.contains("JavaRes") }.configureEach { dependsOn("copyNnueToAndroid") }
  tasks
    .matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }
    .configureEach { dependsOn("compileAndroidNative") }
}

tasks.named("clean") {
  doLast {
    delete(fileTree("src") { include("**/resources/stockfish/**") })
    delete(layout.projectDirectory.dir(".cxx"))
    delete(layout.projectDirectory.dir("src/androidHostTest/jniLibs"))
    delete(layout.projectDirectory.dir("src/androidMain/jniLibs"))
    delete(layout.projectDirectory.dir("src/androidMain/resources/stockfish"))
    delete(layout.projectDirectory.dir("src/androidHostTest/resources/stockfish"))
    logger.lifecycle("Cleaned Stockfish Lite resources")
  }
}

// Copy lite NNUE files to Android resources and host test resources
tasks.register("copyNnueToAndroid") {
  description = "Copy lite NNUE network files to Android resources and host test resources"
  group = "Resources"
  dependsOn("downloadNnueNetworks")
  doLast {
    val nnueFiles = listOf(nnueSmallName)
    val dirs =
      listOf(
        layout.projectDirectory.dir("src/androidMain/resources/stockfish").asFile,
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
