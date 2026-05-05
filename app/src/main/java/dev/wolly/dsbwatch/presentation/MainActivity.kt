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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
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
    val uiState by viewModel.uiState.collectAsState()
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
                    is UiState.SelectingClass -> ClassSelectionScreen(
                        classes = state.classes,
                        onClassSelected = { cls -> viewModel.selectClass(state.u, state.p, cls) }
                    )
                    is UiState.Success -> SubstitutionList(
                        entries = state.entries,
                        onRefresh = { viewModel.fetchData() },
                        onLogout = { viewModel.logout() },
                        onOpenSettings = { showSettings = true }
                    )
                    is UiState.Error -> ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.fetchData() }
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
                    color = MaterialTheme.colorScheme.error
                )
            }
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item {
                Button(onClick = onRetry) {
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
                ListHeader {
                    Text(stringResource(R.string.title_login))
                }
            }
            item {
                Button(
                    onClick = { launchInput(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (username.isEmpty()) "Set Username" else "User: $username")
                }
            }
            item {
                Button(
                    onClick = { launchInput(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (password.isEmpty()) "Set Password" else "Pass: ****")
                }
            }
            item {
                Button(
                    onClick = { onLogin(username, password) },
                    enabled = username.isNotEmpty() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
            item {
                ListHeader {
                    Text(stringResource(R.string.title_select_class))
                }
            }
            items(classes) { cls ->
                Button(
                    onClick = { onClassSelected(cls) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(cls)
                }
            }
            item {
                Button(
                    onClick = { launchManualInput() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
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
    val grouped = entries.groupBy { it.day }

    ScreenScaffold(
        scrollState = scrollState,
        edgeButton = {
            EdgeButton(onClick = onRefresh) {
                Text(stringResource(R.string.action_refresh))
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
                start = 10.dp,
                end = 10.dp
            )
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.msg_no_substitutions),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                grouped.forEach { (day, dayEntries) ->
                    item {
                        ListHeader(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = day,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                    items(dayEntries) { entry ->
                        SubstitutionItem(entry)
                    }
                }
            }
            item {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(stringResource(R.string.title_settings))
                }
            }
            item {
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(stringResource(R.string.action_logout))
                }
            }
        }
    }
}

@Composable
fun SubstitutionItem(entry: SubstitutionEntry) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.lesson,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.wrapContentWidth()
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
                Text(
                    text = entry.art,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.art.isNotEmpty() && entry.room.isNotEmpty()) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = entry.room,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            if (expanded && entry.text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
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
    val isRoomFirst by viewModel.isRoomFirst.collectAsState()
    val sortByPeriod by viewModel.sortByPeriod.collectAsState()

    ScreenScaffold(scrollState = scrollState) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = contentPadding
        ) {
            item {
                ListHeader {
                    Text(stringResource(R.string.title_settings))
                }
            }
            item {
                CheckboxButton(
                    checked = !isRoomFirst,
                    onCheckedChange = { viewModel.toggleColumnOrder() },
                    label = { Text(stringResource(R.string.action_swap_data)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                CheckboxButton(
                    checked = sortByPeriod,
                    onCheckedChange = { viewModel.toggleSortByPeriod() },
                    label = { Text(stringResource(R.string.label_sort_period)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = { viewModel.changeClass() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_switch_class))
                }
            }
            item {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
