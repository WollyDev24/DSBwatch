package dev.wolly.dsbwatch.presentation

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import dev.wolly.dsbwatch.R
import dev.wolly.dsbwatch.data.SubstitutionEntry
import dev.wolly.dsbwatch.presentation.theme.DSBwatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DSBwatchApp()
        }
    }
}

@Composable
fun DSBwatchApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    
    DSBwatchTheme {
        AppScaffold {
            if (showSettings) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { showSettings = false }
                )
            } else {
                when (val state = uiState) {
                    is UiState.Loading -> LoadingScreen()
                    is UiState.NeedsLogin -> LoginScreen(onLogin = viewModel::login)
                    is UiState.SelectingClass -> {
                        val onClassSelected = remember(state.u, state.p) {
                            { cls: String -> viewModel.selectClass(state.u, state.p, cls) }
                        }
                        ClassSelectionScreen(
                            classes = state.classes,
                            onClassSelected = onClassSelected
                        )
                    }
                    is UiState.Success -> {
                        val onOpenSettings = remember { { showSettings = true } }
                        SubstitutionList(
                            entries = state.entries,
                            onRefresh = viewModel::fetchData,
                            onLogout = viewModel::logout,
                            onOpenSettings = onOpenSettings
                        )
                    }
                    is UiState.Error -> ErrorScreen(
                        message = state.message,
                        onRetry = viewModel::fetchData
                    )
                    else -> LoadingScreen()
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    val scrollState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.msg_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                )
            }
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(16.dp)
                        .transformedHeight(this, transformationSpec)
                )
            }
            item {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLogin: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scrollState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val usernameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = RemoteInput.getResultsFromIntent(result.data)
            username = results?.getCharSequence("input_result")?.toString() ?: ""
        }
    }

    val passwordLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = RemoteInput.getResultsFromIntent(result.data)
            password = results?.getCharSequence("input_result")?.toString() ?: ""
        }
    }

    fun launchInput(isPassword: Boolean) {
        val remoteInput = RemoteInput.Builder("input_result")
            .setLabel(if (isPassword) "Password" else "Username")
            .build()
        val intent = Intent("android.support.wearable.input.action.REMOTE_INPUT")
            .putExtra("android.support.wearable.input.extra.REMOTE_INPUTS", arrayOf(remoteInput))
        
        if (isPassword) passwordLauncher.launch(intent)
        else usernameLauncher.launch(intent)
    }

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, transformationSpec)) {
                    Text(stringResource(R.string.title_login))
                }
            }
            item {
                Button(
                    onClick = { launchInput(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    shape = CircleShape
                ) {
                    Text(if (username.isEmpty()) "Set Username" else "User: $username")
                }
            }
            item {
                Button(
                    onClick = { launchInput(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    shape = CircleShape
                ) {
                    Text(if (password.isEmpty()) "Set Password" else "Pass: ****")
                }
            }
            item {
                Button(
                    onClick = { onLogin(username, password) },
                    enabled = username.isNotEmpty() && password.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.action_submit))
                }
            }
        }
    }
}

@Composable
fun ClassSelectionScreen(classes: List<String>, onClassSelected: (String) -> Unit) {
    val scrollState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val manualLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = RemoteInput.getResultsFromIntent(result.data)
            val cls = results?.getCharSequence("input_result")?.toString() ?: ""
            if (cls.isNotEmpty()) onClassSelected(cls)
        }
    }

    fun launchManualInput() {
        val remoteInput = RemoteInput.Builder("input_result")
            .setLabel("Class (e.g. 10a)")
            .build()
        val intent = Intent("android.support.wearable.input.action.REMOTE_INPUT")
            .putExtra("android.support.wearable.input.extra.REMOTE_INPUTS", arrayOf(remoteInput))
        manualLauncher.launch(intent)
    }

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = contentPadding
        ) {
            item(key = "select_class_header") {
                ListHeader(modifier = Modifier.transformedHeight(this, transformationSpec)) {
                    Text(stringResource(R.string.title_select_class))
                }
            }
            items(classes, key = { it }) { cls ->
                Button(
                    onClick = { onClassSelected(cls) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    shape = CircleShape
                ) {
                    Text(cls)
                }
            }
            item(key = "manual_class_btn") {
                Button(
                    onClick = { launchManualInput() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.label_manual_class))
                }
            }
        }
    }
}

@Composable
fun SubstitutionList(
    entries: List<SubstitutionEntry>,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scrollState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val grouped = remember(entries) { entries.groupBy { it.day } }

    ScreenScaffold(
        scrollState = scrollState,
        edgeButton = {
            EdgeButton(onClick = onRefresh) {
                Text(stringResource(R.string.action_refresh))
            }
        }
    ) { contentPadding ->
        val padding = remember(contentPadding) {
            PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
                start = 10.dp,
                end = 10.dp
            )
        }
        
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = padding
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.msg_no_substitutions),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .transformedHeight(this, transformationSpec),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                grouped.forEach { (day, dayEntries) ->
                    item(key = "header_$day", contentType = "header") {
                        ListHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        ) {
                            Text(
                                text = day,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                    items(
                        dayEntries, 
                        key = { it.day + it.lesson + it.subject + it.room + it.art + it.text },
                        contentType = { "entry" }
                    ) { entry ->
                        SubstitutionItem(entry, transformationSpec)
                    }
                }
            }
            item(key = "settings_btn", contentType = "button") {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.title_settings))
                }
            }
            item(key = "logout_btn", contentType = "button") {
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.action_logout))
                }
            }
        }
    }
}

@Composable
fun TransformingLazyColumnItemScope.SubstitutionItem(
    entry: SubstitutionEntry,
    transformationSpec: TransformationSpec
) {
    var expanded by remember { mutableStateOf(false) }
    val cardShape = MaterialTheme.shapes.extraLarge
    
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .transformedHeight(this, transformationSpec)
            .graphicsLayer {
                // Use graphicsLayer to offload some work to the GPU during transformations
                clip = true
                shape = cardShape
            },
        transformation = SurfaceTransformation(transformationSpec),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = cardShape
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.lesson,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.art.isNotEmpty()) {
                    Text(
                        text = entry.art,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (entry.art.isNotEmpty() && entry.room.isNotEmpty()) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (entry.room.isNotEmpty()) {
                    Text(
                        text = entry.room,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (expanded && entry.text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val scrollState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRoomFirst by viewModel.isRoomFirst.collectAsStateWithLifecycle()
    val sortByPeriod by viewModel.sortByPeriod.collectAsStateWithLifecycle()

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = contentPadding
        ) {
            item(key = "settings_header") {
                ListHeader(modifier = Modifier.transformedHeight(this, transformationSpec)) {
                    Text(stringResource(R.string.title_settings))
                }
            }
            item(key = "swap_data") {
                SwitchButton(
                    checked = !isRoomFirst,
                    onCheckedChange = { viewModel.toggleColumnOrder() },
                    label = { Text(stringResource(R.string.label_room)) },
                    secondaryLabel = { Text(stringResource(R.string.action_swap_data)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
            item(key = "sort_period") {
                SwitchButton(
                    checked = sortByPeriod,
                    onCheckedChange = { viewModel.toggleSortByPeriod() },
                    label = { Text(stringResource(R.string.label_sort_period)) },
                    secondaryLabel = { Text(if (sortByPeriod) "Chronological" else "Default") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }
            item(key = "switch_class") {
                Button(
                    onClick = { 
                        viewModel.changeClass()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.action_switch_class))
                }
            }
            item(key = "back_btn") {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.action_back))
                }
            }
        }
    }
}

@WearPreviewDevices
@Composable
fun DefaultPreview() {
    DSBwatchTheme {
        SubstitutionList(
            entries = listOf(
                SubstitutionEntry("Monday", "Vertretung", "10a", "1-2", "Math", "R101", "", "", "Teacher sick", ""),
                SubstitutionEntry("Monday", "Entfall", "10a", "3", "Physic", "R102", "", "", "", "")
            ),
            onRefresh = {},
            onLogout = {},
            onOpenSettings = {}
        )
    }
}
