import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
}

kotlin {
  jvm()
  android {
    namespace = "fr.axl_lvy.stockfish_multiplatform.integration_tests"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    withDeviceTestBuilder { sourceSetTreeName = "test" }
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      testTask { useKarma { useFirefoxHeadless() } }
    }
  }

  sourceSets {
    commonTest.dependencies {
      implementation(project(":stockfish-multiplatform"))
      implementation(libs.kotlin.test)
      implementation(libs.kotest.assertions.core)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

dependencies {
  "androidDeviceTestImplementation"(libs.runner)
  "androidDeviceTestImplementation"(libs.core)
}

tasks.register("copyWasmResources") {
  dependsOn(":stockfish-multiplatform:extractStockfishWasm")
  doLast {
    copy {
      from(
        project(":stockfish-multiplatform")
          .layout.projectDirectory.dir("src/wasmJsMain/resources/stockfish")
      )
      into(layout.projectDirectory.dir("src/wasmJsMain/resources/stockfish"))
    }
  }
}

tasks.named("wasmJsProcessResources") { dependsOn("copyWasmResources") }

tasks.named("clean") {
  doLast { delete(layout.projectDirectory.dir("src/wasmJsMain/resources/stockfish")) }
}

afterEvaluate {
  val libProject = project(":stockfish-multiplatform")
  tasks
    .withType<com.android.build.gradle.tasks.MergeSourceSetFolders>()
    .matching { it.name.contains("Assets") }
    .configureEach {
      dependsOn(":stockfish-multiplatform:copyNnueToAndroid")
      val assetsDir = libProject.layout.projectDirectory.dir("src/androidMain/assets")
      sourceFolderInputs.from(assetsDir)
      doLast {
        copy {
          from(assetsDir)
          into(outputDir)
        }
      }
    }
}
