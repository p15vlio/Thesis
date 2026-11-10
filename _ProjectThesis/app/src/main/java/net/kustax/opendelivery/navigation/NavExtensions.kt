package net.kustax.opendelivery.navigation

import androidx.navigation.NavController

// Back to the attract screen, dropping every other screen so the next guest cannot navigate back into a visit
fun NavController.navigateToIdle() {
    navigate(Screen.Idle.route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

// Back to the existing Home entry (it sits at the bottom of every visit), so Home never piles up on the stack
fun NavController.returnToHome() {
    if (!popBackStack(Screen.Home.route, inclusive = false)) {
        navigate(Screen.Home.route) { launchSingleTop = true }
    }
}
