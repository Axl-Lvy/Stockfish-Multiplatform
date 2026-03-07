import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

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
      commonWebpackConfig {
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
