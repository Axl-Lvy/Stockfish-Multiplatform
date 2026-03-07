plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply  false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "Axl-Lvy_Stockfish-Multiplatform")
        property("sonar.organization", "axl-lvy")
        property("sonar.projectName", "Stockfish Multiplatform")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.exclusions",
            listOf(
                "stockfish-multiplatform/cpp/stockfish/**",
                "stockfish-multiplatform/src/**/resources/stockfish/**",
                "stockfish-multiplatform/src/androidMain/jniLibs/**",
                "stockfish-multiplatform/src/androidMain/assets/stockfish/**",
                "stockfish-multiplatform/src/androidHostTest/jniLibs/**",
                "stockfish-multiplatform/src/androidHostTest/resources/stockfish/**",
            ).joinToString(","),
        )
    }
}
