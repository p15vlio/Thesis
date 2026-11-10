package net.kustax.opendelivery.web.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kustax.opendelivery.web.Screen
import net.kustax.opendelivery.web.api.ApiClient
import net.kustax.opendelivery.web.auth.AuthSession
import net.kustax.opendelivery.web.util.toLabel

private val SidebarBg      = Color(0xFF0F172A)  // slate-900 dark navy
private val SidebarHeader  = Color(0xFF0A1929)  // slightly darker for header
private val SidebarActive  = Color.White.copy(alpha = 0.12f)
private val SidebarFgOn    = Color.White
private val SidebarFgOff   = Color.White.copy(alpha = 0.60f)
private val SidebarLabel   = Color.White.copy(alpha = 0.35f)
private val SidebarDivider = Color.White.copy(alpha = 0.10f)
private val SidebarAccent  = Color(0xFF0D9488)  // teal secondary

private data class NavItem(val label: String, val icon: ImageVector, val screen: Screen)

@Composable
fun AppScaffold(
    session: AuthSession,
    currentScreen: Screen,
    navigate: (Screen) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showChangePassword by remember { mutableStateOf(false) }
    if (showChangePassword) {
        val client = remember(session) { ApiClient(ApiClient.BASE_URL, session.accessToken) }
        ChangePasswordDialog(client = client, onDismiss = { showChangePassword = false })
    }
    val snackbarMessage by SnackbarController.message
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage!!)
            SnackbarController.clear()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Dark sidebar
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(SidebarBg)
            ) {
                // Branding header — slightly darker strip at top
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SidebarHeader)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        "Guest Desk",
                        style = MaterialTheme.typography.titleLarge,
                        color = SidebarFgOn,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (session.role == "PLATFORM_ADMIN") "Platform Admin" else "Owner Portal",
                        style = MaterialTheme.typography.bodySmall,
                        color = SidebarFgOff
                    )
                }

                // Nav items area + bottom user block
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Spacer(Modifier.height(4.dp))
                        if (session.role == "PLATFORM_ADMIN") {
                            AdminSidebarContent(currentScreen, navigate)
                        } else {
                            OwnerSidebarContent(currentScreen, navigate)
                        }
                    }

                    // Bottom: role label + sign-out
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = SidebarDivider
                        )
                        Text(
                            session.role.toLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = SidebarLabel
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { showChangePassword = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = SidebarFgOff)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Change Password")
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SidebarFgOff
                            ),
                            border = BorderStroke(
                                1.dp,
                                SidebarDivider
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }

            val focusManager = LocalFocusManager.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .onPreviewKeyEvent { event ->
                        when {
                            event.key == Key.Tab && event.type == KeyEventType.KeyDown -> {
                                if (event.isShiftPressed) focusManager.moveFocus(FocusDirection.Previous)
                                else focusManager.moveFocus(FocusDirection.Next)
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                content()
            }
        }
    }
}

@Composable
private fun AdminSidebarContent(currentScreen: Screen, navigate: (Screen) -> Unit) {
    var managementExpanded by remember { mutableStateOf(true) }

    SidebarNavItem(
        item = NavItem("Dashboard", Icons.Default.Home, Screen.AdminDashboard),
        isActive = currentScreen == Screen.AdminDashboard,
        navigate = navigate
    )

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
        color = SidebarDivider
    )

    // Management section header
    TextButton(
        onClick = { managementExpanded = !managementExpanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = SidebarLabel
                )
                Text(
                    "MANAGEMENT",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = SidebarLabel
                )
            }
            Icon(
                if (managementExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = SidebarLabel
            )
        }
    }

    if (managementExpanded) {
        val subItems = listOf(
            NavItem("Owners", Icons.Default.People, Screen.OwnerList),
            NavItem("Properties", Icons.Default.LocationOn, Screen.AdminProperties),
            NavItem("Devices", Icons.Default.PhoneAndroid, Screen.AdminDevices),
            NavItem("Products", Icons.Default.ShoppingCart, Screen.AdminProducts)
        )
        subItems.forEach { item ->
            val isSubItemActive = currentScreen == item.screen
                || (item.screen == Screen.OwnerList && currentScreen is Screen.AdminOwnerView)
            SidebarNavItem(
                item = item,
                isActive = isSubItemActive,
                navigate = navigate,
                startPadding = 16.dp
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
        color = SidebarDivider
    )

    SidebarNavItem(
        item = NavItem("Audit Logs", Icons.AutoMirrored.Filled.List, Screen.AuditLogs),
        isActive = currentScreen == Screen.AuditLogs,
        navigate = navigate
    )
    SidebarNavItem(
        item = NavItem("Settings", Icons.Default.Settings, Screen.Settings),
        isActive = currentScreen == Screen.Settings,
        navigate = navigate
    )
    SidebarNavItem(
        item = NavItem("About", Icons.Default.Info, Screen.About),
        isActive = currentScreen == Screen.About,
        navigate = navigate
    )
    SidebarNavItem(
        item = NavItem("Help", Icons.AutoMirrored.Filled.HelpOutline, Screen.Help),
        isActive = currentScreen is Screen.Help,
        navigate = navigate
    )
}

@Composable
private fun OwnerSidebarContent(currentScreen: Screen, navigate: (Screen) -> Unit) {
    val navItems = listOf(
        NavItem("Dashboard", Icons.Default.Home, Screen.Dashboard),
        NavItem("Properties", Icons.Default.LocationOn, Screen.PropertyList),
        NavItem("Orders", Icons.Default.Receipt, Screen.OwnerOrders),
        NavItem("Products", Icons.Default.ShoppingCart, Screen.ProductList),
        NavItem("Food delivery", Icons.Default.Restaurant, Screen.FoodDelivery)
    )
    navItems.forEach { item ->
        val isActive = currentScreen == item.screen
            || (item.screen == Screen.PropertyList && (currentScreen is Screen.DeviceList || currentScreen is Screen.OrderList))
        SidebarNavItem(item = item, isActive = isActive, navigate = navigate)
    }
    SidebarNavItem(
        item = NavItem("Help", Icons.AutoMirrored.Filled.HelpOutline, Screen.Help),
        isActive = currentScreen is Screen.Help,
        navigate = navigate
    )
}

@Composable
private fun SidebarNavItem(
    item: NavItem,
    isActive: Boolean,
    navigate: (Screen) -> Unit,
    startPadding: Dp = 0.dp
) {
    val fgColor  = if (isActive) SidebarFgOn else SidebarFgOff
    val bgColor  = if (isActive) SidebarActive else Color.Transparent
    val accentColor = if (isActive) SidebarAccent else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = accentColor,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
            .background(bgColor)
            .clickable { navigate(item.screen) }
            .padding(start = 16.dp + startPadding, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(18.dp), tint = fgColor)
        Text(
            item.label,
            style = if (isActive) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    else MaterialTheme.typography.labelLarge,
            color = fgColor
        )
    }
}
