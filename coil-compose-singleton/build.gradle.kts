import coil.addAllTargets
import coil.setupLibraryModule

plugins {
    id("com.android.library")
    id("kotlin-multiplatform")
    id("kotlinx-atomicfu")
    id("org.jetbrains.compose")
}

addAllTargets(project)
setupLibraryModule(name = "coil.compose.singleton")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.coilComposeBase)
                api(projects.coilSingleton)
            }
        }
    }
}

compose {
    kotlinCompilerPlugin = libs.jetbrains.compose.compiler.get().toString()
}
