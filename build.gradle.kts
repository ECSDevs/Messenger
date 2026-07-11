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

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * 从 .env 文件加载环境变量（不覆盖已设置的系统环境变量）
 */
fun loadDotEnv() {
    val envFile = rootDir.resolve(".env")
    if (!envFile.exists()) return
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
        val eqIndex = trimmed.indexOf('=')
        if (eqIndex < 0) return@forEach
        val key = trimmed.substring(0, eqIndex).trim()
        val value = trimmed.substring(eqIndex + 1).trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
        if (System.getenv(key) == null) {
            System.setProperty(key, value)
        }
    }
}

loadDotEnv()

/**
 * 从环境变量或 Git 自动计算版本信息：
 * - versionCode: 优先读取 VERSION_CODE 环境变量，否则 git commit 总数
 * - versionName: "v" + 最新提交日期（yyyyMMdd），保证可复现；可被 VERSION_NAME 环境变量覆盖
 */
fun getEnv(key: String): String? {
    return System.getenv(key) ?: System.getProperty(key)
}

fun computeVersionCode(): Int {
    val envValue = getEnv("VERSION_CODE")
    if (envValue != null) {
        return envValue.toIntOrNull() ?: 1
    }
    val output = providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootDir
    }.standardOutput.asText.get().trim()
    return output.toIntOrNull() ?: 1
}

fun computeVersionName(): String {
    // 用最新一次提交的日期作为 versionName（vyyyyMMdd），保证同一提交的构建产物可复现。
    // 可被 VERSION_NAME 环境变量覆盖。
    val envValue = getEnv("VERSION_NAME")
    if (envValue != null) return envValue
    val output = providers.exec {
        commandLine("git", "log", "-1", "--format=%cd", "--date=format:%Y%m%d")
        workingDir = rootDir
    }.standardOutput.asText.get().trim()
    return "v$output"
}

val verCode = computeVersionCode()
ext["versionCode"] = verCode
ext["versionName"] = computeVersionName()
ext["keystoreFile"] = rootDir.resolve("keyring/messenger-release.jks")
ext["keystorePassword"] = getEnv("KEYSTORE_PASSWORD") ?: ""
ext["keyAlias"] = getEnv("KEY_ALIAS") ?: "messenger"
ext["keyPassword"] = getEnv("KEY_PASSWORD") ?: ""
