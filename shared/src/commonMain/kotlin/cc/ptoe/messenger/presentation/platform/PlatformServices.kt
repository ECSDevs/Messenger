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

package cc.ptoe.messenger.presentation.platform

import cc.ptoe.messenger.data.util.FileKit
import cc.ptoe.messenger.data.util.randomUuid
import cc.ptoe.messenger.di.AppContainerHolder

/** Shows a short platform toast/notification (Android Toast, Desktop stdout). */
expect fun showPlatformToast(message: String)

/** Copies text to the system clipboard. */
expect fun copyTextToClipboard(text: String)

/** App version name for the settings screen (`null` when unavailable). */
expect fun appVersionName(): String?

/**
 * Copies a picked/cropped image file into app-private avatar storage
 * (`filesDir/<subdir>`) and returns the new absolute path. Shared by
 * the settings (user avatar) and agent-edit (agent avatar) flows.
 */
fun copyAvatarToInternal(sourcePath: String, subdir: String): String? {
    return try {
        val dir = AppContainerHolder.instance.appDirs.filesDir.resolve(subdir)
        FileKit.mkdirs(dir)
        val extension = FileKit.extensionOf(sourcePath).ifBlank { "jpg" }
        val dest = dir.resolve("${randomUuid()}.$extension")
        FileKit.copy(sourcePath, dest.toString())
        dest.toString()
    } catch (e: Exception) {
        null
    }
}

/** Deletes a previously stored avatar file, if it exists. */
fun deleteAvatarFile(path: String?) {
    if (path.isNullOrBlank()) return
    FileKit.delete(path)
}
