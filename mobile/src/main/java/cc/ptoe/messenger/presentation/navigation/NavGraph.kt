package cc.ptoe.messenger.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cc.ptoe.messenger.MessengerApplication
import cc.ptoe.messenger.presentation.ui.agents.AgentEditScreen
import cc.ptoe.messenger.presentation.ui.agents.AgentsScreen
import cc.ptoe.messenger.presentation.ui.chat.ChatScreen
import cc.ptoe.messenger.presentation.ui.conversations.ConversationSettingsScreen
import cc.ptoe.messenger.presentation.ui.conversations.ConversationsScreen
import cc.ptoe.messenger.presentation.ui.providers.ProviderDetailScreen
import cc.ptoe.messenger.presentation.ui.providers.ProviderEditScreen
import cc.ptoe.messenger.presentation.ui.providers.ProvidersScreen
import cc.ptoe.messenger.presentation.ui.settings.LicensesScreen
import cc.ptoe.messenger.presentation.ui.settings.SettingsScreen
import cc.ptoe.messenger.presentation.viewmodel.ConversationsViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Conversations.route,
        modifier = modifier
    ) {
        composable(Screen.Conversations.route) {
            ConversationsScreen(
                onConversationClick = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                }
            )
        }
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ChatScreen(
                conversationId = conversationId,
                onBackClick = { navController.popBackStack() },
                onSettingsClick = {
                    navController.navigate(Screen.ConversationSettings.createRoute(conversationId))
                }
            )
        }
        composable(Screen.Agents.route) {
            val conversationsBackStackEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Conversations.route)
            }
            val conversationsViewModel: ConversationsViewModel = viewModel(
                conversationsBackStackEntry,
                factory = ConversationsViewModel.provideFactory(
                    conversationRepository = MessengerApplication.instance.conversationRepository,
                    currentAgentRepository = MessengerApplication.instance.currentAgentRepository,
                    agentRepository = MessengerApplication.instance.agentRepository,
                    modelRepository = MessengerApplication.instance.modelRepository
                )
            )
            AgentsScreen(
                onAddClick = {
                    navController.navigate(Screen.AgentEdit.createRoute())
                },
                onEditClick = { agentId ->
                    navController.navigate(Screen.AgentEdit.createRoute(agentId))
                },
                onAgentClick = { agentId ->
                    conversationsViewModel.switchAgent(agentId)
                    navController.popBackStack(Screen.Conversations.route, inclusive = false)
                }
            )
        }
        composable(Screen.Providers.route) {
            ProvidersScreen(
                onBackClick = { navController.popBackStack() },
                onAddClick = {
                    navController.navigate(Screen.ProviderEdit.createRoute())
                },
                onEditClick = { providerId ->
                    navController.navigate(Screen.ProviderEdit.createRoute(providerId))
                },
                onProviderClick = { providerId ->
                    navController.navigate(Screen.ProviderDetail.createRoute(providerId))
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onProvidersClick = {
                    navController.navigate(Screen.Providers.route)
                },
                onLicensesClick = {
                    navController.navigate(Screen.Licenses.route)
                },
                cloudSyncRepository = MessengerApplication.instance.cloudSyncRepository
            )
        }

        composable(
            route = Screen.ProviderEdit.route,
            arguments = listOf(
                navArgument("providerId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId")
            ProviderEditScreen(
                providerId = providerId,
                onBackClick = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ProviderDetail.route,
            arguments = listOf(
                navArgument("providerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: ""
            ProviderDetailScreen(
                providerId = providerId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AgentEdit.route,
            arguments = listOf(
                navArgument("agentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId")
            AgentEditScreen(
                agentId = agentId,
                onBackClick = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ConversationRename.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            SimplePlaceholderScreen(
                text = "重命名对话: $conversationId"
            )
        }

        composable(
            route = Screen.ConversationSettings.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ConversationSettingsScreen(
                conversationId = conversationId,
                onBackClick = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Screen.Licenses.route) {
            LicensesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun SimplePlaceholderScreen(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}
