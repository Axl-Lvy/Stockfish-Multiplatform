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
  val iosArchMap =
    mapOf(
      "iosArm64" to "arm64-device",
      "iosSimulatorArm64" to "arm64-simulator",
      "iosX64" to "x86_64-simulator",
    )

  val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())

  iosTargets.forEach { target ->
    target.compilations.getByName("main") {
      cinterops.create("stockfish") {
        defFile(project.file("src/nativeInterop/cinterop/stockfish.def"))
        compilerOpts("-I${project.file("cpp").absolutePath}")
        extraOpts(
          "-libraryPath",
          layout.buildDirectory
            .dir("ios-native/${iosArchMap[target.name]}")
            .get()
            .asFile
            .absolutePath,
        )
      }
    }
  }

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
    val jvmCommon by creating {
      dependsOn(commonMain.get())
      kotlin.srcDir(layout.buildDirectory.dir("generated/nnue"))
    }
    jvmMain.get().dependsOn(jvmCommon)
    androidMain.get().dependsOn(jvmCommon)

    commonMain.dependencies { implementation(libs.kotlinx.coroutines.core) }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotest.assertions.core)
      implementation(libs.kotlinx.coroutines.test)
    }

    wasmJsMain.get().kotlin.srcDir(layout.buildDirectory.dir("generated/wasmCdn"))
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

// Patch Stockfish source for MSVC compatibility (8MB thread stack size)
tasks.register("patchStockfishSource") {
  description = "Patch Stockfish source for MSVC compatibility"
  group = "Resources"
  dependsOn("downloadStockfishSource")
  val threadHeader = layout.projectDirectory.file("cpp/stockfish/thread_win32_osx.h")
  inputs.file(threadHeader)
  outputs.file(threadHeader)
  onlyIf { System.getProperty("os.name").lowercase().contains("win") }
  doLast {
    val file = threadHeader.asFile
    if (file.exists()) {
      val original = file.readText()
      if (!original.contains("_MSC_VER")) {
        val patched =
          original.replace(
            "#else  // Default case: use STL classes\n\nnamespace Stockfish {\n\nusing NativeThread = std::thread;\n\n}  // namespace Stockfish",
            """#elif defined(_MSC_VER)

    #include <functional>
    #include <windows.h>

namespace Stockfish {

class NativeThread {
    HANDLE thread;

    static constexpr size_t TH_STACK_SIZE = 8 * 1024 * 1024;

   public:
    template<class Function, class... Args>
    explicit NativeThread(Function&& fun, Args&&... args) {
        auto func = new std::function<void()>(
          std::bind(std::forward<Function>(fun), std::forward<Args>(args)...));

        thread = CreateThread(
          nullptr, TH_STACK_SIZE,
          [](LPVOID ptr) -> DWORD {
              auto f = reinterpret_cast<std::function<void()>*>(ptr);
              (*f)();
              delete f;
              return 0;
          },
          func, STACK_SIZE_PARAM_IS_A_RESERVATION, nullptr);
    }

    void join() {
        WaitForSingleObject(thread, INFINITE);
        CloseHandle(thread);
    }
};

}  // namespace Stockfish

#else  // Default case: use STL classes

namespace Stockfish {

using NativeThread = std::thread;

}  // namespace Stockfish""",
          )
        file.writeText(patched)
      }
    }
  }
}

// Compile JVM native library using CMake on the host machine
tasks.register("compileJvmNative") {
  description = "Compile JVM native library using CMake on the host machine"
  group = "Resources"
  dependsOn("patchStockfishSource")
  inputs.dir(layout.projectDirectory.dir("src/jvmMain/cpp"))
  inputs.dir(layout.projectDirectory.dir("cpp/stockfish")).optional()
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

// iOS — Compile Stockfish as a static library for each iOS architecture using CMake.
// NNUE networks are embedded into the binary (no NNUE_EMBEDDING_OFF).
tasks.register("compileIosNative") {
  description = "Compile Stockfish static library for iOS using CMake"
  group = "Resources"
  dependsOn("downloadStockfishSource", "downloadNnueNetworks")
  inputs.dir(layout.projectDirectory.dir("src/iosMain/cpp"))
  inputs.dir(layout.projectDirectory.dir("cpp/stockfish")).optional()
  outputs.dir(layout.buildDirectory.dir("ios-native"))
  onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
  doLast {
    val cmakeSrcDir = layout.projectDirectory.dir("src/iosMain/cpp").asFile.absolutePath
    val nnueDir = layout.projectDirectory.dir("src/jvmMain/resources/stockfish").asFile.absolutePath

    data class IosTarget(val archDir: String, val arch: String, val sysroot: String?)

    val targets =
      listOf(
        IosTarget("arm64-device", "arm64", null),
        IosTarget("arm64-simulator", "arm64", "iphonesimulator"),
        IosTarget("x86_64-simulator", "x86_64", "iphonesimulator"),
      )

    for (target in targets) {
      val buildDir = layout.buildDirectory.dir("ios-native/${target.archDir}").get().asFile
      buildDir.mkdirs()
      val cmakeArgs =
        mutableListOf(
          "cmake",
          cmakeSrcDir,
          "-B",
          buildDir.absolutePath,
          "-DCMAKE_SYSTEM_NAME=iOS",
          "-DCMAKE_OSX_ARCHITECTURES=${target.arch}",
          "-DCMAKE_OSX_DEPLOYMENT_TARGET=14.0",
          "-DCMAKE_BUILD_TYPE=Release",
          "-DNNUE_DIR=$nnueDir",
        )
      if (target.sysroot != null) {
        cmakeArgs.add("-DCMAKE_OSX_SYSROOT=${target.sysroot}")
      }
      exec { commandLine(cmakeArgs) }
      exec { commandLine("cmake", "--build", buildDir.absolutePath, "--config", "Release") }
    }
  }
}

// WASM - Download Stockfish .wasm binary from npm package
// The .js file is committed to the repo (patched for blob Worker URL handling).
tasks.register<Download>("downloadStockfishWasmPackage") {
  description = "Download Stockfish.js npm package"
  group = "Resources"
  src("https://registry.npmjs.org/stockfish/-/stockfish-18.0.5.tgz")
  dest(layout.buildDirectory.file("stockfish-js-package.tgz"))
  onlyIfModified(true)
}

tasks.register("extractStockfishWasm") {
  description = "Extract Stockfish .wasm binary from npm package"
  group = "Resources"
  dependsOn("downloadStockfishWasmPackage", "createResourceDirectories")
  inputs.file(layout.buildDirectory.file("stockfish-js-package.tgz"))
  outputs.file(layout.projectDirectory.file("src/wasmJsMain/resources/stockfish/stockfish-18.wasm"))
  doLast {
    copy {
      from(tarTree(resources.gzip(layout.buildDirectory.file("stockfish-js-package.tgz"))))
      into(layout.buildDirectory.dir("stockfish-js-extracted"))
    }
    copy {
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
    "compileIosNative",
    "extractStockfishWasm",
  )
}

tasks.named("jvmProcessResources") { dependsOn("downloadNnueNetworks", "compileJvmNative") }

tasks.named("wasmJsProcessResources") { dependsOn("extractStockfishWasm") }

afterEvaluate {
  tasks.matching { it.name.contains("JavaRes") }.configureEach { dependsOn("copyNnueToAndroid") }
  tasks
    .matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }
    .configureEach { dependsOn("compileAndroidNative") }
  // Ensure iOS static libraries are built before cinterop runs
  tasks
    .matching { it.name.startsWith("cinteropStockfish") }
    .configureEach { dependsOn("compileIosNative") }
}

tasks.named("clean") {
  doLast {
    delete(fileTree("src") { include("**/resources/stockfish/**") })
    delete(layout.projectDirectory.dir(".cxx"))
    delete(layout.projectDirectory.dir("cpp/stockfish"))
    delete(layout.projectDirectory.dir("src/androidHostTest/jniLibs"))
    delete(layout.projectDirectory.dir("src/androidMain/jniLibs"))
    delete(layout.projectDirectory.dir("src/androidMain/resources/stockfish"))
    delete(layout.projectDirectory.dir("src/androidHostTest/resources/stockfish"))
    logger.lifecycle("Cleaned Stockfish resources and source directories")
  }
}

// Generate NnueConfig.kt with the NNUE network filenames for this variant.
// The lite module generates its own version with smaller network names.
tasks.register("generateNnueConfig") {
  description = "Generate NnueConfig.kt with NNUE filenames"
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
        |internal val NNUE_FILES = listOf("nn-c288c895ea92.nnue", "nn-37f18f62d772.nnue")
        """
          .trimMargin()
          .trim() + "\n"
      )
  }
}

tasks.register("generateWasmCdnConfig") {
  description = "Generate WasmCdnConfig.kt with CDN URLs for Stockfish WASM files"
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
        |internal const val STOCKFISH_JS_CDN_URL = "https://unpkg.com/stockfish@18.0.5/bin/stockfish-18.js"
        |internal const val STOCKFISH_WASM_CDN_URL = "https://unpkg.com/stockfish@18.0.5/bin/stockfish-18.wasm"
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

// Copy NNUE files to Android resources and host test resources
tasks.register("copyNnueToAndroid") {
  description = "Copy NNUE network files to Android resources and host test resources"
  group = "Resources"
  dependsOn("downloadNnueNetworks")
  doLast {
    val nnueFiles = listOf("nn-c288c895ea92.nnue", "nn-37f18f62d772.nnue")
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
          "-G",
          "Ninja",
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
