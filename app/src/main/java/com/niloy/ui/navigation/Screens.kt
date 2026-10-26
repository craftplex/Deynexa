package com.niloy.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable data object Today : Screen
    @Serializable data object Calendar : Screen
    @Serializable data object Statistics : Screen
    @Serializable data object Categories : Screen
    @Serializable data object Settings : Screen
    @Serializable data object Onboarding : Screen
    @Serializable data class TaskDetail(val taskId: Long? = null) : Screen
}
