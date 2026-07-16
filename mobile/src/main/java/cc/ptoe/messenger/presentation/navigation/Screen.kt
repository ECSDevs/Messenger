package cc.ptoe.messenger.presentation.navigation

sealed class Screen(val route: String) {
    data object Conversations : Screen("conversations")
    data object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    data object Agents : Screen("agents")
    data object AgentMarket : Screen("agent_market")
    data object AgentMarketDetail : Screen("agent_market_detail/{marketAgentId}") {
        fun createRoute(marketAgentId: String) = "agent_market_detail/$marketAgentId"
    }
    data object Providers : Screen("providers")
    data object Settings : Screen("settings")
    data object CloudSettings : Screen("cloud_settings")

    data object ProviderEdit : Screen("provider_edit?providerId={providerId}") {
        fun createRoute(providerId: String? = null) =
            if (providerId != null) "provider_edit?providerId=$providerId" else "provider_edit"
    }

    data object ProviderDetail : Screen("provider_detail/{providerId}") {
        fun createRoute(providerId: String) = "provider_detail/$providerId"
    }

    data object AgentEdit : Screen("agent_edit?agentId={agentId}") {
        fun createRoute(agentId: String? = null) =
            if (agentId != null) "agent_edit?agentId=$agentId" else "agent_edit"
    }

    data object ConversationRename : Screen("conversation_rename/{conversationId}") {
        fun createRoute(conversationId: String) = "conversation_rename/$conversationId"
    }

    data object ConversationSettings : Screen("conversation_settings/{conversationId}") {
        fun createRoute(conversationId: String) = "conversation_settings/$conversationId"
    }

    data object Licenses : Screen("licenses")
}
