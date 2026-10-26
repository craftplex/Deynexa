package com.niloy

import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.niloy.ui.navigation.Screen
import com.niloy.ui.screens.*
import com.niloy.ui.theme.DaynexaTheme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

val LocalBottomBarVisible = staticCompositionLocalOf { true }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as DaynexaApplication
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    app.repository,
                    app.backupService,
                    app.focentraIntegrationManager,
                    app.dataPortabilityManager
                )
            )
            val settingsState by settingsViewModel.uiState.collectAsState()

            val context = androidx.compose.ui.platform.LocalContext.current
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            var permissionsGranted by remember {
                mutableStateOf(
                    checkNotificationPermission(context) &&
                    checkBatteryOptimizationPermission(context) &&
                    checkAlarmPermission(context)
                )
            }

            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        permissionsGranted = checkNotificationPermission(context) &&
                                checkBatteryOptimizationPermission(context) &&
                                checkAlarmPermission(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            DaynexaTheme(darkTheme = false) {
                if (settingsState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (!settingsState.isOnboardingCompleted) {
                    OnboardingScreen(onComplete = {
                        settingsViewModel.completeOnboarding()
                    })
                } else if (!permissionsGranted) {
                    PermissionGateScreen(
                        onAllPermissionsGranted = {
                            permissionsGranted = true
                        }
                    )
                } else {
                    MainContent(
                        app = app,
                        settingsViewModel = settingsViewModel,
                        isOnboardingCompleted = settingsState.isOnboardingCompleted
                    ) {
                        settingsViewModel.completeOnboarding()
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent(
    app: DaynexaApplication,
    settingsViewModel: SettingsViewModel,
    isOnboardingCompleted: Boolean,
    onOnboardingComplete: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val startDestination = if (isOnboardingCompleted) Screen.Today else Screen.Onboarding

    var isBottomBarVisible by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -8f) {
                    if (isBottomBarVisible) isBottomBarVisible = false
                } else if (delta > 8f) {
                    if (!isBottomBarVisible) isBottomBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(currentDestination?.route) {
        isBottomBarVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF2F7FF), // Soft light blue touch
                        Color(0xFFF3FAF5), // Soft light green touch
                        Color(0xFFF6F3FE)  // Soft light purple touch
                    )
                )
            )
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            bottomBar = {
            val currentRoute = currentDestination?.route
            val showBottomBar = isOnboardingCompleted &&
                    currentRoute != Screen.Onboarding::class.qualifiedName &&
                    currentRoute?.contains("TaskDetail") != true

            if (showBottomBar) {
                val bottomBarOffsetY by animateDpAsState(
                    targetValue = if (isBottomBarVisible) 0.dp else 120.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "bottomBarOffset"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = bottomBarOffsetY.toPx()
                        }
                        .navigationBarsPadding()
                        .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .padding(horizontal = 4.dp)
                        ) {
                        val isToday = currentDestination?.route == Screen.Today::class.qualifiedName
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isToday) Icons.Filled.Today else Icons.Outlined.Today,
                                    contentDescription = "Today",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    "Today",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            selected = isToday,
                            alwaysShowLabel = true,
                            onClick = {
                                navController.navigate(Screen.Today) {
                                    popUpTo(Screen.Today) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )

                        val isCalendar = currentDestination?.route == Screen.Calendar::class.qualifiedName
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isCalendar) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                                    contentDescription = "Calendar",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    "Calendar",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            selected = isCalendar,
                            alwaysShowLabel = true,
                            onClick = {
                                navController.navigate(Screen.Calendar) {
                                    popUpTo(Screen.Today) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )

                        val isStats = currentDestination?.route == Screen.Statistics::class.qualifiedName
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isStats) Icons.Filled.Insights else Icons.Outlined.Insights,
                                    contentDescription = "Stats",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    "Stats",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            selected = isStats,
                            alwaysShowLabel = true,
                            onClick = {
                                navController.navigate(Screen.Statistics) {
                                    popUpTo(Screen.Today) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )

                        val isCategories = currentDestination?.route == Screen.Categories::class.qualifiedName
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isCategories) Icons.Filled.Category else Icons.Outlined.Category,
                                    contentDescription = "Categories",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    "Categories",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            selected = isCategories,
                            alwaysShowLabel = true,
                            onClick = {
                                navController.navigate(Screen.Categories) {
                                    popUpTo(Screen.Today) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )

                        val isSettings = currentDestination?.route == Screen.Settings::class.qualifiedName
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isSettings) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    "Settings",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            selected = isSettings,
                            alwaysShowLabel = true,
                            onClick = {
                                navController.navigate(Screen.Settings) {
                                    popUpTo(Screen.Today) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    }
) { innerPadding ->
        CompositionLocalProvider(LocalBottomBarVisible provides isBottomBarVisible) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) +
                    androidx.compose.animation.slideInHorizontally(
                        initialOffsetX = { 60 },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                },
                exitTransition = {
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(250)) +
                    androidx.compose.animation.slideOutHorizontally(
                        targetOffsetX = { -60 },
                        animationSpec = androidx.compose.animation.core.tween(250)
                    )
                },
                popEnterTransition = {
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) +
                    androidx.compose.animation.slideInHorizontally(
                        initialOffsetX = { -60 },
                        animationSpec = androidx.compose.animation.core.tween(300)
                    )
                },
                popExitTransition = {
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(250)) +
                    androidx.compose.animation.slideOutHorizontally(
                        targetOffsetX = { 60 },
                        animationSpec = androidx.compose.animation.core.tween(250)
                    )
                }
            ) {
            composable<Screen.Onboarding> {
                OnboardingScreen(onComplete = {
                    onOnboardingComplete()
                    navController.navigate(Screen.Today) {
                        popUpTo(Screen.Onboarding) { inclusive = true }
                    }
                })
            }
            composable<Screen.Today> {
                val todayViewModel: TodayViewModel = viewModel(
                    factory = TodayViewModel.Factory(app.repository, app.schedulingService)
                )
                TodayScreen(
                    viewModel = todayViewModel,
                    onAddTask = { navController.navigate(Screen.TaskDetail()) },
                    onEditTask = { taskId -> navController.navigate(Screen.TaskDetail(taskId = taskId)) }
                )
            }
            composable<Screen.Calendar> {
                val calendarViewModel: CalendarViewModel = viewModel(
                    factory = CalendarViewModel.Factory(app.repository, app.schedulingService)
                )
                CalendarScreen(
                    viewModel = calendarViewModel,
                    onEditTask = { taskId -> navController.navigate(Screen.TaskDetail(taskId = taskId)) }
                )
            }
            composable<Screen.Statistics> {
                val statisticsViewModel: StatisticsViewModel = viewModel(
                    factory = StatisticsViewModel.Factory(app.repository, app.schedulingService)
                )
                StatisticsScreen(
                    viewModel = statisticsViewModel
                )
            }
            composable<Screen.Categories> {
                val categoriesViewModel: CategoriesViewModel = viewModel(
                    factory = CategoriesViewModel.Factory(app.repository)
                )
                CategoriesScreen(viewModel = categoriesViewModel)
            }
            composable<Screen.Settings> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<Screen.TaskDetail> { backStackEntry ->
                val route: Screen.TaskDetail = backStackEntry.toRoute()
                val detailViewModel: TaskDetailViewModel = viewModel(
                    factory = TaskDetailViewModel.Factory(
                        app.repository,
                        app.reminderScheduler,
                        app.schedulingService,
                        route.taskId
                    )
                )
                TaskDetailScreen(
                    viewModel = detailViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
}
}
