package com.example.hyperosai

import android.os.Bundle
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

private data class ChatMessage(val role: String, val text: String)
private data class Palette(val name: String, val accent: Color, val secondary: Color)
private val palettes = listOf(
    Palette("Violet", Color(0xFFB7A0FF), Color(0xFF70E7F5)),
    Palette("Matrix", Color(0xFF63E69A), Color(0xFFB8F6CB)),
    Palette("Copper", Color(0xFFB87333), Color(0xFFE7B98B)),
    Palette("Arctic", Color(0xFF69AFFF), Color(0xFF9DEBFA)),
    Palette("Crimson", Color(0xFFFF718B), Color(0xFFFFB1BE)),
    Palette("Pure AMOLED", Color.White, Color(0xFFB0B0B0))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = LocalSettings(this)
        setContent { HyperOsApp(settings) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HyperOsApp(settings: LocalSettings) {
    val appContext = LocalContext.current
    var tab by remember { mutableStateOf("Chat") }
    var version by remember { mutableStateOf(settings.hyperOsVersion) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var paletteName by remember { mutableStateOf(settings.palette) }
    var amoled by remember { mutableStateOf(settings.amoled) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCommand by remember { mutableStateOf<String?>(null) }
    var pendingWriteAction by remember { mutableStateOf<String?>(null) }
    var commandOutput by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf(ChatMessage("assistant", "Hey! I'm your Redmi Note 14 5G HyperOS assistant. Set your HyperOS version in Settings, add a Gemini key, then ask about safe tweaks.") ) }
    val scope = rememberCoroutineScope()
    val client = remember { GeminiClient() }
    val palette = palettes.firstOrNull { it.name == paletteName } ?: palettes[0]
    val background = if (amoled || paletteName == "Pure AMOLED") Color.Black else Color(0xFF0B0C12)
    val surface = if (amoled || paletteName == "Pure AMOLED") Color(0xFF080808) else Color(0xB81D2030)
    val scheme = darkColorScheme(
        primary = palette.accent, secondary = palette.secondary,
        background = background, surface = surface,
        onBackground = Color(0xFFF4F3FA), onSurface = Color(0xFFF4F3FA)
    )
    SideEffect { settings.hyperOsVersion = version; settings.apiKey = apiKey; settings.palette = paletteName; settings.amoled = amoled }

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("HYPEROS AI", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                            Text("REDMI NOTE 14 5G  •  ${version.ifBlank { "VERSION NOT SET" }}", fontSize = 10.sp, color = palette.secondary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { tab = "Settings" }) { Icon(Icons.Default.Tune, "Settings") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background.copy(alpha = 0.88f))
                )
            },
            bottomBar = {
                NavigationBar(containerColor = surface) {
                    listOf("Home", "Chat", "Tools", "Settings").forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(when (item) {
                                    "Home" -> Icons.Default.Dashboard
                                    "Chat" -> Icons.Default.AutoAwesome
                                    "Tools" -> Icons.Default.Terminal
                                    else -> Icons.Default.Settings
                                }, contentDescription = item)
                            },
                            label = { Text(item) }
                        )
                    }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.padding(padding).fillMaxSize(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen"
            ) { screen ->
                when (screen) {
                    "Home" -> HomeScreen(version, palette) { tab = it }
                    "Settings" -> SettingsScreen(
                        version, { version = it }, apiKey, { apiKey = it },
                        paletteName, { paletteName = it }, amoled, { amoled = it }, palette
                    )
                    "Tools" -> ToolsScreen(
                        commandOutput, { commandOutput = it },
                        { pendingCommand = it }, { pendingWriteAction = it }, palette
                    )
                    else -> ChatScreen(
                        messages, input, { input = it }, busy, error, palette,
                        onSend = {
                            val prompt = input.trim()
                            if (prompt.isNotEmpty() && !busy) {
                                messages.add(ChatMessage("user", prompt))
                                input = ""; error = null; busy = true
                                scope.launch {
                                    try {
                                        val reply = client.ask(apiKey, version, prompt, messages.dropLast(1).map { it.role to it.text })
                                        messages.add(ChatMessage("assistant", reply))
                                    } catch (e: Exception) {
                                        error = e.message ?: "Something went wrong."
                                        messages.add(ChatMessage("assistant", "I couldn't complete that request. Check the API key, internet connection, and selected model in the error message."))
                                    } finally { busy = false }
                                }
                            }
                        }
                    )
                }
            }
        }
        if (pendingCommand != null) {
            AlertDialog(
                onDismissRequest = { pendingCommand = null },
                title = { Text("Run read-only command?") },
                text = { Text("This runs “${pendingCommand}” through Shizuku. The command is from the app's allowlist and does not intentionally change settings.") },
                confirmButton = {
                    TextButton(onClick = {
                        val cmd = pendingCommand!!
                        pendingCommand = null
                        scope.launch {
                            commandOutput = runCatching { ShizukuTools.executeReadOnly(cmd) }
                                .getOrElse { "Command failed: ${it.message}" }
                            tab = "Tools"
                        }
                    }) { Text("Run command") }
                },
                dismissButton = { TextButton(onClick = { pendingCommand = null }) { Text("Cancel") } }
            )
        }
        if (pendingWriteAction != null) {
            val action = ShizukuTools.writeActions.firstOrNull { it.id == pendingWriteAction }
            if (action != null) {
                AlertDialog(
                    onDismissRequest = { pendingWriteAction = null },
                    title = { Text("Confirm system-setting change") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(action.title, fontWeight = FontWeight.Bold)
                            Text(action.detail)
                            Text("Rollback guidance", fontWeight = FontWeight.SemiBold)
                            Text(action.rollbackGuidance, color = Color.LightGray)
                            Text("Only the app's fixed allowlisted settings are used. No arbitrary shell text is accepted.", fontSize = 12.sp, color = palette.secondary)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val actionId = action.id
                            pendingWriteAction = null
                            scope.launch {
                                commandOutput = runCatching { ShizukuTools.executeWriteAction(appContext, actionId) }
                                    .getOrElse { "Action failed: ${it.message}" }
                                tab = "Tools"
                            }
                        }) { Text(if (action.id == "animations_rollback") "Confirm rollback" else "Apply change") }
                    },
                    dismissButton = { TextButton(onClick = { pendingWriteAction = null }) { Text("Cancel") } }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(version: String, palette: Palette, setTab: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GlassCard(palette) {
                Text("Your device. Your rules.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("A focused AI lab for Redmi Note 14 5G and HyperOS.", color = Color.LightGray)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Smartphone, null, tint = palette.accent)
                    Column {
                        Text("Redmi Note 14 5G", fontWeight = FontWeight.SemiBold)
                        Text(version.ifBlank { "Set your HyperOS version in Settings" }, color = palette.secondary, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Text("QUICK LABS", fontWeight = FontWeight.Bold, color = palette.secondary, letterSpacing = 1.4.sp) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard("AI Expert", "Ask version-aware questions", Icons.Default.AutoAwesome, palette, Modifier.weight(1f)) { setTab("Chat") }
                QuickCard("Tweak Lab", "Read-only diagnostics", Icons.Default.Terminal, palette, Modifier.weight(1f)) { setTab("Tools") }
            }
        }
        item {
            GlassCard(palette) {
                Text("Starter knowledge", fontWeight = FontWeight.Bold)
                Text("• Confirm exact build before applying a tweak\n• Prefer Settings UI for reversible changes\n• Animation scales affect perceived speed, not raw CPU performance\n• Avoid random debloat lists and unknown system packages\n• Back up important data before advanced modifications", color = Color.LightGray)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    version: String, onVersion: (String) -> Unit,
    apiKey: String, onApiKey: (String) -> Unit,
    paletteName: String, onPalette: (String) -> Unit,
    amoled: Boolean, onAmoled: (Boolean) -> Unit, palette: Palette
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            GlassCard(palette) {
                Text("Device context", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Enter the exact HyperOS version/build from Settings → About phone. This is sent with your questions to Gemini.", color = Color.LightGray, fontSize = 13.sp)
                OutlinedTextField(
                    value = version, onValueChange = onVersion, modifier = Modifier.fillMaxWidth(),
                    label = { Text("HyperOS version / build") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) }
                )
            }
        }
        item {
            GlassCard(palette) {
                Text("Gemini API", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Your key is stored locally with encrypted preferences. For public distribution, use a backend proxy.", color = Color.LightGray, fontSize = 13.sp)
                OutlinedTextField(
                    value = apiKey, onValueChange = onApiKey, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = { Icon(Icons.Default.Key, null) }
                )
                Text("Model: gemini-2.5-flash (change in GeminiClient.kt)", color = palette.secondary, fontSize = 12.sp)
            }
        }
        item {
            GlassCard(palette) {
                Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                palettes.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { onPalette(p.name); if (p.name == "Pure AMOLED") onAmoled(true) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(p.accent))
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, Modifier.weight(1f))
                        if (paletteName == p.name) Icon(Icons.Default.CheckCircle, null, tint = p.accent)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pure AMOLED black", fontWeight = FontWeight.SemiBold)
                        Text("True black app backgrounds", fontSize = 12.sp, color = Color.LightGray)
                    }
                    Switch(checked = amoled, onCheckedChange = onAmoled)
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    messages: List<ChatMessage>, input: String, onInput: (String) -> Unit,
    busy: Boolean, error: String?, palette: Palette, onSend: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (msg.role == "user") palette.accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.accent.copy(alpha = if (msg.role == "user") 0.35f else 0.12f)),
                        modifier = Modifier.fillMaxWidth(if (msg.role == "user") 0.88f else 0.96f)
                    ) {
                        Text(msg.text, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent) }
            if (error != null) item {
                Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input, onValueChange = onInput, modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about HyperOS tweaks…") },
                shape = RoundedCornerShape(24.dp), maxLines = 5
            )
            FilledIconButton(onClick = onSend, enabled = !busy && input.isNotBlank(), modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun ToolsScreen(
    output: String, onOutput: (String) -> Unit,
    onRun: (String) -> Unit, onWriteAction: (String) -> Unit, palette: Palette
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var ready by remember { mutableStateOf(ShizukuTools.isBinderReady()) }
    var granted by remember { mutableStateOf(ShizukuTools.isPermissionGranted()) }
    val refreshStatus = remember {
        {
            ready = ShizukuTools.isBinderReady()
            granted = ShizukuTools.isPermissionGranted()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) refreshStatus()
        }
        val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshStatus() }
        val binderDeadListener = Shizuku.OnBinderDeadListener { refreshStatus() }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
        }
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        refreshStatus()
        onDispose {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(palette) {
                Text("Shizuku bridge", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Optional ADB-level access. Install and start Shizuku separately, then grant this app permission in Shizuku.", color = Color.LightGray)
                Spacer(Modifier.height(8.dp))
                StatusLine("Binder running", ready, palette)
                StatusLine("Permission granted", granted, palette)
                if (ready && !granted) {
                    Button(onClick = {
                        runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE) }
                        refreshStatus()
                    }) { Text("Request Shizuku permission") }
                }
                Text("Status updates when permission results arrive, Shizuku connects/disconnects, and the app resumes.", color = Color.LightGray, fontSize = 12.sp)
                Text("Read-only diagnostics", fontWeight = FontWeight.SemiBold)
            }
        }
        items(ShizukuTools.commandNames()) { name ->
            GlassCard(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text("Read-only diagnostic", color = Color.LightGray, fontSize = 12.sp)
                    }
                    Button(onClick = { onRun(name) }, enabled = ready && granted) { Text("Run") }
                }
            }
        }
        item {
            GlassCard(palette) {
                Text("Reviewed write actions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("These actions only change Android's three global animation-scale values. Each action requires a second confirmation. The app saves the previous values before writing and attempts an automatic rollback if applying the change fails.", color = Color.LightGray, fontSize = 13.sp)
                ShizukuTools.writeActions.forEach { action ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(action.title, fontWeight = FontWeight.SemiBold)
                        Text(action.detail, color = Color.LightGray, fontSize = 12.sp)
                        Button(
                            onClick = { onWriteAction(action.id) },
                            enabled = ready && granted && (action.id != "animations_rollback" || context.getSharedPreferences("hyperos_ai_animation_rollback", Context.MODE_PRIVATE).getBoolean("valid", false)),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (action.id == "animations_rollback") "Rollback last change" else "Review & apply 0.5×") }
                    }
                    if (action.id != ShizukuTools.writeActions.last().id) HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                }
                Text("Manual fallback: Settings → Additional settings → Developer options → set Window, Transition, and Animator duration scales. Exact menu labels vary by HyperOS build.", color = palette.secondary, fontSize = 12.sp)
            }
        }
        if (output.isNotBlank()) item {
            GlassCard(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Output", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onOutput("") }) { Text("Clear") }
                }
                Text(output, fontSize = 12.sp, color = palette.secondary)
            }
        }
    }
}

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 4107

@Composable
private fun StatusLine(label: String, active: Boolean, palette: Palette) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (active) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (active) palette.accent else Color.Gray)
        Text("$label: ${if (active) "Yes" else "No"}")
    }
}

@Composable
private fun GlassCard(palette: Palette, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.accent.copy(alpha = 0.22f)),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, palette: Palette, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.accent.copy(alpha = 0.22f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = palette.accent, modifier = Modifier.size(28.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.LightGray, fontSize = 12.sp)
        }
    }
}
