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

package cc.ptoe.messenger.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.R
import cc.ptoe.messenger.data.cloud.CloudLoginOutcome
import cc.ptoe.messenger.data.cloud.CloudSyncRepository
import cc.ptoe.messenger.data.cloud.DEFAULT_CLOUD_SERVER_URL
import cc.ptoe.messenger.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsScreen(
    onBackClick: () -> Unit,
    cloudSyncRepository: CloudSyncRepository,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(
            MessengerApplication.instance.themePreferences,
            MessengerApplication.instance.appPreferences,
            cloudSyncRepository
        )
    )
) {
    val user by viewModel.cloudUser.collectAsStateWithLifecycle()
    val savedServerUrl by viewModel.cloudServerUrl.collectAsStateWithLifecycle()
    var serverUrl by remember(savedServerUrl) { mutableStateOf(savedServerUrl) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var deletePassword by remember { mutableStateOf("") }
    var pendingLogin by remember { mutableStateOf<CloudLoginOutcome?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val loginSuccess = stringResource(R.string.cloud_settings_login_success)
    val localDataUploaded = stringResource(R.string.cloud_settings_local_data_uploaded)
    val cloudDataRestored = stringResource(R.string.cloud_settings_cloud_data_restored)
    val loginFailed = stringResource(R.string.cloud_settings_login_failed)
    val operationFailed = stringResource(R.string.cloud_settings_operation_failed)
    val logoutSuccess = stringResource(R.string.cloud_settings_logout_success)
    val passwordUpdated = stringResource(R.string.cloud_settings_password_updated)
    val accountDeleted = stringResource(R.string.cloud_settings_account_deleted)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun notify(result: Result<*>, success: String, close: Boolean = false) {
        result.onSuccess {
            isBusy = false
            showMessage(success)
            if (close) onBackClick()
        }.onFailure {
            isBusy = false
            showMessage(it.message ?: operationFailed)
        }
    }

    fun completeLogin(
        outcome: CloudLoginOutcome,
        useLocalData: Boolean,
        successMessage: String
    ) {
        viewModel.completeLogin(outcome, useLocalData) { result ->
            notify(result, successMessage, close = true)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.cloud_settings_account_sync),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.cloud_settings_sync_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CloudSectionCard(
                title = stringResource(R.string.cloud_settings_server),
                icon = Icons.Default.Cloud
            ) {
                Text(
                    text = stringResource(R.string.cloud_settings_server_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.cloud_settings_server_url_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isBusy
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { serverUrl = DEFAULT_CLOUD_SERVER_URL },
                        enabled = !isBusy
                    ) {
                        Text(stringResource(R.string.cloud_settings_use_default_server))
                    }
                }
            }

            if (user == null) {
                CloudSectionCard(
                    title = stringResource(if (register) R.string.cloud_settings_create_account else R.string.cloud_settings_login_account),
                    icon = Icons.Default.Lock
                ) {
                    Text(
                        text = stringResource(if (register) R.string.cloud_settings_create_account_desc else R.string.cloud_settings_login_account_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.cloud_settings_email_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.cloud_settings_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Button(
                        onClick = {
                            isBusy = true
                            val normalizedServerUrl = serverUrl.trim()
                            serverUrl = normalizedServerUrl
                            val callback: (Result<CloudLoginOutcome>) -> Unit = { result ->
                                result.onSuccess { outcome ->
                                    when {
                                        !outcome.hasLocalData -> {
                                            completeLogin(outcome, useLocalData = false, loginSuccess)
                                        }
                                        outcome.cloudVersion == 0L -> {
                                            completeLogin(outcome, useLocalData = true, localDataUploaded)
                                        }
                                        else -> {
                                            isBusy = false
                                            pendingLogin = outcome
                                        }
                                    }
                                }.onFailure {
                                    isBusy = false
                                    showMessage(it.message ?: loginFailed)
                                }
                            }
                            if (register) {
                                viewModel.register(email.trim(), password, normalizedServerUrl, callback)
                            } else {
                                viewModel.login(email.trim(), password, normalizedServerUrl, callback)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy &&
                            email.isNotBlank() &&
                            password.isNotBlank() &&
                            serverUrl.isNotBlank()
                    ) {
                        Text(if (register) stringResource(R.string.cloud_settings_register_and_login) else stringResource(R.string.action_login))
                    }
                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { register = !register },
                            enabled = !isBusy
                        ) {
                            Text(if (register) stringResource(R.string.cloud_settings_have_account) else stringResource(R.string.cloud_settings_no_account))
                        }
                    }
                }
            } else {
                CloudSectionCard(
                    title = stringResource(R.string.cloud_settings_account),
                    icon = Icons.Default.Cloud
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CloudIconContainer(icon = Icons.Default.Cloud)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user!!.email,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.cloud_settings_connected),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                CloudSectionCard(
                    title = stringResource(R.string.cloud_settings_sync_data),
                    icon = Icons.Default.Sync
                ) {
                    Text(
                        text = stringResource(R.string.cloud_settings_sync_data_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            isBusy = true
                            viewModel.upload(serverUrl.trim()) { notify(it, localDataUploaded) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Text(stringResource(R.string.cloud_settings_upload_sync))
                    }
                    FilledTonalButton(
                        onClick = {
                            isBusy = true
                            viewModel.restore(serverUrl.trim()) { notify(it, cloudDataRestored) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Text(stringResource(R.string.cloud_settings_restore))
                    }
                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                CloudSectionCard(
                    title = stringResource(R.string.cloud_settings_account_security),
                    icon = Icons.Default.Lock
                ) {
                    OutlinedButton(
                        onClick = { showPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Text(stringResource(R.string.cloud_settings_change_password))
                    }
                    TextButton(
                        onClick = { viewModel.logout { notify(it, logoutSuccess) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.cloud_settings_logout))
                    }
                    androidx.compose.material3.HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.cloud_settings_delete_account),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.cloud_settings_delete_account_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.cloud_settings_permanently_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showPasswordDialog = false },
            title = { Text(stringResource(R.string.cloud_settings_change_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text(stringResource(R.string.cloud_settings_current_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.cloud_settings_new_password_label)) },
                        supportingText = { Text(stringResource(R.string.cloud_settings_password_min_length)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isBusy = true
                        viewModel.changePassword(currentPassword, newPassword) { result ->
                            notify(result, passwordUpdated)
                            if (result.isSuccess) {
                                currentPassword = ""
                                newPassword = ""
                                showPasswordDialog = false
                            }
                        }
                    },
                    enabled = !isBusy && currentPassword.isNotBlank() && newPassword.length >= 8
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPasswordDialog = false },
                    enabled = !isBusy
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) showDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
            title = { Text(stringResource(R.string.cloud_settings_delete_account_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.cloud_settings_delete_account_confirm))
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text(stringResource(R.string.cloud_settings_current_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isBusy,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isBusy = true
                        viewModel.deleteAccount(deletePassword) { result ->
                            notify(result, accountDeleted, close = true)
                            if (result.isSuccess) {
                                deletePassword = ""
                                showDeleteDialog = false
                            }
                        }
                    },
                    enabled = !isBusy && deletePassword.isNotBlank()
                ) {
                    Text(stringResource(R.string.cloud_settings_permanently_delete_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isBusy
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    pendingLogin?.let { outcome ->
        CloudSyncChoiceDialog(
            outcome = outcome,
            onUseLocal = {
                pendingLogin = null
                isBusy = true
                completeLogin(outcome, useLocalData = true, localDataUploaded)
            },
            onUseCloud = {
                pendingLogin = null
                isBusy = true
                completeLogin(outcome, useLocalData = false, cloudDataRestored)
            },
            onDismiss = { pendingLogin = null }
        )
    }
}

@Composable
private fun CloudSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CloudIconContainer(icon = icon)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                content()
            }
        )
    }
}

@Composable
private fun CloudIconContainer(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null)
        }
    }
}
