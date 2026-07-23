/*
 * Copyright 2026 ECSDevs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("DEPRECATION", "OPT_IN_USAGE", "UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    androidLibrary {
        namespace = "cc.ptoe.messenger.shared"
        compileSdk {
            version = release(37)
        }
        minSdk = 30
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        androidResources { enable = true }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)

                implementation(libs.androidx.datastore.preferences)

                implementation(libs.jetbrains.navigation.compose)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.lifecycle.runtime.compose)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.coil3.compose)
                implementation(libs.coil3.network.ktor3)

                implementation(libs.kotlinx.datetime)
                implementation(libs.okio)

                // AndroidMath (transitive via llm-typewriter) drags in
                // com.github.jitpack:gradle-simple:1.0 -> com.google.guava:guava:18.0,
                // which bundles com.google.common.util.concurrent.ListenableFuture and
                // collides with the canonical listenablefuture:1.0 stub that androidx.core
                // depends on. We per-dependency exclude guava from llm-typewriter so
                // only the listenablefuture stub remains on the runtime classpath (no
                // duplicate class). KSP/Room compiler still gets guava via its own
                // kspAndroid configuration (independent of commonMain's implementation).
                // String notation is used because KotlinDependencyHandler.implementation
                // does not accept Provider + Action in this Kotlin Gradle Plugin version.
                // The composite build substitutes cc.ptoe:llm-typewriter:1.0 regardless
                // of the requested version.
                implementation("cc.ptoe:llm-typewriter:1.0") {
                    exclude(group = "com.google.guava", module = "guava")
                }
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.okhttp)

                implementation(libs.java.websocket)

                implementation(libs.ucrop)
                implementation(libs.androidx.exifinterface)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.transition)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.okhttp)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.resources {
    packageOfResClass = "cc.ptoe.messenger.generated.resources"
    generateResClass = always
}

// guava (transitive via llm-typewriter / AndroidMath, and required at build-time
// by KSP/Room compiler for com.google.common.collect.ImmutableList) bundles
// ListenableFuture. The standalone listenablefuture-1.0 stub (pulled by
// androidx.core) ships the same class and would collide. Exclude only the stub
// so checkDebugDuplicateClasses passes while guava stays for KSP/Room.
configurations {
    all {
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
}

dependencies {
    // Room compiler: per-target KSP registration (AGP 9 KMP library plugin pattern).
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}
