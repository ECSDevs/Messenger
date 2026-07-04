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
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * 从 Git 自动计算版本信息：
 * - versionCode: git commit 总数
 * - versionName: 最新 version tag（如 v1.0.0），无 tag 时返回 "1.0.0-dev"
 */
fun getGitVersionCode(): Int {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    return output.toIntOrNull() ?: 1
}

fun getGitVersionName(): String {
    val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    return if (output.isNotEmpty()) output.removePrefix("v") else "1.0.0-dev"
}

ext["gitVersionCode"] = getGitVersionCode()
ext["gitVersionName"] = getGitVersionName()