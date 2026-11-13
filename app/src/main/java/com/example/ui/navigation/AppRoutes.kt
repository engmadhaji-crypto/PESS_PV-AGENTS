package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object CustomersRoute

@Serializable
data class CustomerDetailRoute(val customerId: String)

@Serializable
data class PlanDetailRoute(val planId: String)

@Serializable
data object AiAssistantRoute

@Serializable
data object SyncSettingsRoute
