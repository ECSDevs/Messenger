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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/** Process-wide Android context, set by MessengerApplication.onCreate. */
object AndroidContextHolder {
    lateinit var appContext: Context
}

actual fun showPlatformToast(message: String) {
    Toast.makeText(AndroidContextHolder.appContext, message, Toast.LENGTH_LONG).show()
}

actual fun copyTextToClipboard(text: String) {
    val clipboard = AndroidContextHolder.appContext
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
}

actual fun appVersionName(): String? = runCatching {
    val context = AndroidContextHolder.appContext
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName
}.getOrNull()
