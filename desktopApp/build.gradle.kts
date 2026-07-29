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

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    // Main.kt constructs AppContainer (RoomDatabase.Builder / okio.Path / HttpClient leak
    // through shared's public desktop API) and configures the Coil ImageLoader directly.
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.ktor3)
    implementation(libs.okio)
    implementation(libs.ktor.client.core)
    implementation(libs.androidx.room.runtime)
}

compose.desktop {
    application {
        mainClass = "cc.ptoe.messenger.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Messenger"
            // Mirrors androidApp/wear: versionCode = git commit count (rootProject.ext),
            // which is monotonically increasing and keeps MSI/Deb upgrade ordering correct.
            // packageVersion must be numeric triplets (jpackage constraint); the human-readable
            // versionName "vYYYYMMDD" is tracked separately in rootProject.ext["versionName"].
            packageVersion = "1.0.${rootProject.ext["versionCode"]}"
        }
        // ProGuard (enabled by default for the release build type) fails because
        // OkHttp's optional platform classes (org.conscrypt, org.openjsse,
        // org.bouncycastle.jsse, org.graalvm) and Skiko's JBR adapter
        // (com.jetbrains.SharedTextures) are not on the Desktop classpath.
        // Disable it, mirroring the androidApp R8-disabled approach.
        buildTypes {
            release {
                proguard {
                    isEnabled.set(false)
                }
            }
        }
    }
}
