import coil3.addAllMultiplatformTargets
import coil3.nonAndroidMain
import coil3.setupLibraryModule

plugins {
    id("com.android.library")
    id("kotlin-multiplatform")
    id("kotlinx-atomicfu")
    id("dev.drewhamilton.poko")
}

addAllMultiplatformTargets()
setupLibraryModule(name = "coil3.network")

kotlin {
    nonAndroidMain()

    sourceSets {
        commonMain {
            dependencies {
                api(projects.coilBase)
                api(libs.ktor.core)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.coilTestInternal)
                implementation(libs.bundles.test.common)
                implementation(libs.ktor.mock)
            }
        }
    }
}