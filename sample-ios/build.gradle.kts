plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "SampleIos"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":frameready"))
            implementation(project(":shared-ui"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.ui)
            implementation(compose.material3)
        }
    }
}
