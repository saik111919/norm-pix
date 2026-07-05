package com.ritesh.cashiro.presentation.ui.features.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.ChatMessage
import com.ritesh.cashiro.data.repository.ModelState
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.effects.rememberOverscrollFlingBehavior
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.LoadingCircle
import com.ritesh.cashiro.presentation.ui.components.LoadingLine
import com.ritesh.cashiro.presentation.ui.components.SearchBarBox
import com.ritesh.cashiro.presentation.ui.features.categories.NavigationContent
import com.ritesh.cashiro.presentation.ui.icons.Bag
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import com.ritesh.cashiro.presentation.ui.icons.Send
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.LocalBlurEffects
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.TokenUtils
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val modelState by chatViewModel.modelState.collectAsStateWithLifecycle()
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val currentResponse by chatViewModel.currentResponse.collectAsStateWithLifecycle()
    val isTokenInfoEnabled by chatViewModel.isTokenInfoEnabled.collectAsStateWithLifecycle()
    val chatStats by chatViewModel.chatStats.collectAsStateWithLifecycle()
    val chatSessions by chatViewModel.chatSessions.collectAsStateWithLifecycle()
    val currentSessionId by chatViewModel.currentSessionId.collectAsStateWithLifecycle()
    
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var showMenu by remember { mutableStateOf(false) }
    
    var showHistorySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val context = LocalContext.current
    val blurEffects = LocalBlurEffects.current
    
    // Only auto-scroll on new messages, not on initial load
    var isInitialLoad by remember { mutableStateOf(true) }
    
    // Collect toast events
    LaunchedEffect(Unit) {
        chatViewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, currentResponse) {
        if (isInitialLoad) {
            isInitialLoad = false
            return@LaunchedEffect
        }
        if (messages.isNotEmpty() || currentResponse.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    index = if (currentResponse.isNotEmpty()) messages.size else messages.size - 1
                )
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }
    val dropContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.cashiro_ai),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = {NavigationContent(onNavigateBack)},
                actionContent = {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            shapes =  IconButtonDefaults.shapes(),
                            modifier = Modifier.padding(end =Spacing.md)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = stringResource(R.string.more_options),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .then(
                                    if (blurEffects) Modifier.hazeEffect(
                                        state = hazeState,
                                        block = fun HazeEffectScope.() {
                                            style = HazeDefaults.style(
                                                backgroundColor = Color.Transparent,
                                                tint = HazeTint(dropContainerColor.copy(0.5f)),
                                                blurRadius = 36.dp,
                                                noiseFactor = -1f,
                                            )
                                            blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                                        }
                                    ) else Modifier
                                ),
                            containerColor = dropContainerColor.copy(
                                alpha = if (blurEffects) 0.7f else 1f
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_chat)) },
                                onClick = {
                                    showMenu = false
                                    chatViewModel.startNewChat()
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                }
                            )
                            HorizontalDivider(
                                thickness = 1.5.dp,
                                color = MaterialTheme.colorScheme.surface.copy(0.6f)
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_history)) },
                                onClick = {
                                    showMenu = false
                                    showHistorySheet = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.History, contentDescription = null)
                                }
                            )
                            HorizontalDivider(
                                thickness = 1.5.dp,
                                color = MaterialTheme.colorScheme.surface.copy(0.6f)
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_chat)) },
                                onClick = {
                                    showMenu = false
                                    chatViewModel.clearChat()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, contentDescription = null)
                                },
                                enabled = messages.isNotEmpty()
                            )
                        }
                    }
                },
                extraInfoCard = {
                    // Developer info card
                    if(isTokenInfoEnabled && messages.isNotEmpty()) {
                        DeveloperInfoCard(
                            chatStats = chatStats,
                            modifier = Modifier.padding(end =Spacing.md)
                        )
                    }
                }
            )
        },

    ) { paddingValues ->
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            when (modelState) {
                ModelState.NOT_DOWNLOADED, ModelState.ERROR -> {
                    // Show existing messages if any, but disable input
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = paddingValues.calculateTopPadding(), bottom = paddingValues.calculateBottomPadding())
                    ) {
                        // If no messages, show the download prompt centered
                        if (messages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                                ) {
                                    Icon(
                                        Icons.Rounded.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.qwen_model_required),
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Text(
                                        text = stringResource(R.string.download_ai_model_prompt),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = onNavigateToSettings) {
                                        Text(stringResource(R.string.go_to_settings))
                                    }
                                }
                            }
                        } else {
                            // Show existing messages (read-only)
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .hazeSource(hazeState)
                                    .fillMaxWidth()
                                    .overScrollVertical(),
                                flingBehavior = rememberOverscrollFlingBehavior { listState },
                                contentPadding = PaddingValues(
                                    start = Dimensions.Padding.content,
                                    end = Dimensions.Padding.content,
                                    top = Dimensions.Padding.content,
                                    bottom = Spacing.lg
                                ),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                items(messages) { message ->
                                    ChatMessageItem(
                                        message = message,
                                        onCopy = { 
                                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Copied Text", it.message))
                                        },
                                        onDelete = { chatViewModel.deleteMessage(it.id) },
                                        onEdit = {
                                            editingMessageId = it.id
                                            inputText = TextFieldValue(
                                                text = it.message,
                                                selection = TextRange(it.message.length)
                                            )
                                            focusRequester.requestFocus()
                                        },
                                        onRegenerate = { chatViewModel.regenerateMessage(it.id) }
                                    )
                                }
                            }
                        }

                        // Show model required banner at bottom
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            tonalElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimensions.Padding.content),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.model_required),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.download_to_continue_chatting),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier.padding(start = Spacing.sm)
                                ) {
                                    Text(stringResource(R.string.download))
                                }
                            }
                        }
                    }
                }

                ModelState.DOWNLOADING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            LoadingCircle(modifier = Modifier.size(64.dp))
                            Text(
                                text = stringResource(R.string.downloading_model_format, uiState.downloadProgress),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            LoadingLine(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Spacing.xs)
                                    .clip(RoundedCornerShape(Spacing.xs)),
                                progress = uiState.downloadProgress / 100f
                            )
                            Text(
                                text = stringResource(R.string.check_settings_for_more_details),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                ModelState.READY, ModelState.LOADING -> {
                    // Show loading overlay when model is loading
                    if (modelState == ModelState.LOADING) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                LoadingCircle()
                                Text(
                                    text = stringResource(R.string.initializing_ai_model),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.this_may_take_a_few_seconds),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ){
                            Column{
                                // Messages list
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .hazeSource(hazeState)
                                        .fillMaxWidth()
                                        .clip( CardDefaults.shape)
                                        .overScrollVertical(),
                                    flingBehavior = rememberOverscrollFlingBehavior { listState },
                                    contentPadding = PaddingValues(
                                        start = Dimensions.Padding.content,
                                        end = Dimensions.Padding.content,
                                        top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                                        bottom = Dimensions.Padding.content
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    reverseLayout = false
                                ) {
                                    items(messages) { message ->
                                        ChatMessageItem(
                                            message = message,
                                            onCopy = { 
                                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Copied Text", it.message))
                                            },
                                            onDelete = { chatViewModel.deleteMessage(it.id) },
                                            onEdit = {
                                                editingMessageId = it.id
                                                inputText = TextFieldValue(
                                                    text = it.message,
                                                    selection = TextRange(it.message.length)
                                                )
                                                focusRequester.requestFocus()
                                            },
                                            onRegenerate = { chatViewModel.regenerateMessage(it.id) }
                                        )
                                    }

                                    // Show streaming response if available
                                    if (currentResponse.isNotEmpty()) {
                                        item {
                                            ChatMessageItem(
                                                message = ChatMessage(
                                                    message = currentResponse,
                                                    isUser = false,
                                                    timestamp = System.currentTimeMillis()
                                                ),
                                                isStreaming = true
                                            )
                                        }
                                    } else if (uiState.isLoading) {
                                        // Show typing indicator while waiting for response
                                        item {
                                            TypingIndicator()
                                        }
                                    }
                                    item{
                                        Spacer(modifier = Modifier.height(180.dp))
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            ) {
                                // Error message
                                AnimatedVisibility(
                                    visible = uiState.error != null,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Dimensions.Padding.content),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(Dimensions.Padding.content),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = uiState.error ?: "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { chatViewModel.clearError() }) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    contentDescription = stringResource(R.string.dismiss),
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }

                                // Token limit warning
                                AnimatedVisibility(
                                    visible = chatStats.contextUsagePercent >= 80,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    TokenLimitWarning(
                                        usagePercent = chatStats.contextUsagePercent,
                                        onClearChat = { chatViewModel.clearChat() }
                                    )
                                }

                                // Input field
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.surface,
                                                )
                                            )
                                        )
                                        .navigationBarsPadding(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Dimensions.Padding.content),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        TextField(
                                            value = inputText,
                                            onValueChange = { inputText = it },
                                            placeholder = { Text(stringResource(R.string.ask_about_expenses_placeholder)) },
                                            enabled = !uiState.isLoading,
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(focusRequester),
                                            shape = RoundedCornerShape(Spacing.xxl),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    0.7f
                                                )
                                            ),
                                            trailingIcon = {
                                                FilledIconButton(
                                                    onClick = {
                                                        if (uiState.isLoading) {
                                                            chatViewModel.stopGeneration()
                                                        } else {
                                                            if (editingMessageId != null) {
                                                                chatViewModel.editMessage(editingMessageId!!, inputText.text)
                                                                editingMessageId = null
                                                            } else {
                                                                chatViewModel.sendMessage(inputText.text)
                                                            }
                                                            inputText = TextFieldValue("")
                                                            // Keep keyboard open by requesting focus
                                                            focusRequester.requestFocus()
                                                        }
                                                    },
                                                    enabled = uiState.isLoading || inputText.text.isNotBlank(),
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .padding(Spacing.xs)
                                                ) {
                                                    if (uiState.isLoading) {
                                                        Icon(
                                                            Icons.Rounded.Stop,
                                                            contentDescription = "Stop",
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    } else {
                                                        Icon(
                                                            Iconax.Send,
                                                            contentDescription = stringResource(R.string.send),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Chat History Bottom Sheet
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Chat History",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
                SearchBarBox(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    modifier = Modifier.padding(bottom = 16.dp),
                    label = { Text("Search chats...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
                )
                LazyColumn {
                    val filteredSessions = chatSessions.filter { 
                        it.title.contains(searchQuery.text, ignoreCase = true) 
                    }
                    items(filteredSessions) { session ->
                        val isSelected = session.id == currentSessionId
                        ListItem(
                            modifier = Modifier.clickable {
                                chatViewModel.loadSession(session.id)
                                showHistorySheet = false
                            },
                            headlineContent = { Text(session.title, fontWeight = if (isSelected) FontWeight.Bold else null) },
                            supportingContent = { Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(Date(session.createdAt))) },
                            trailingContent = {
                                IconButton(onClick = { chatViewModel.deleteSession(session.id) }) {
                                    Icon(
                                        Iconax.Bag,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenLimitWarning(
    usagePercent: Int,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        usagePercent >= 95 -> MaterialTheme.colorScheme.errorContainer
        usagePercent >= 90 -> Color(0xFFFFF3E0) // Orange container
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    
    val contentColor = when {
        usagePercent >= 95 -> MaterialTheme.colorScheme.onErrorContainer
        usagePercent >= 90 -> Color(0xFF5D4037) // Dark orange
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    
    val icon = when {
        usagePercent >= 95 -> Icons.Rounded.Error
        else -> Icons.Rounded.Warning
    }
    
    val message = when {
        usagePercent >= 95 -> stringResource(R.string.chat_memory_full_warning)
        usagePercent >= 90 -> stringResource(R.string.chat_memory_almost_full_warning_format, usagePercent)
        else -> stringResource(R.string.chat_memory_usage_format, usagePercent)
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
            if (usagePercent >= 90) {
                TextButton(
                    onClick = onClearChat,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = contentColor
                    )
                ) {
                    Text(stringResource(R.string.clear), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DeveloperInfoCard(
    chatStats: ChatStats,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val usageHint = remember(chatStats.contextUsagePercent) {
        TokenUtils.getUsageColorHint(chatStats.contextUsagePercent)
    }
    val usageColor = when (usageHint) {
        "critical" -> MaterialTheme.colorScheme.error
        "warning" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.content)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.qwen_model_messages_format, chatStats.messageCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tokens_format, TokenUtils.formatNumber(chatStats.estimatedTokens)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = usageColor
                    )
                    Icon(
                        if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    
                    // Context usage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.context_usage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.percentage_format_no_brackets, chatStats.contextUsagePercent),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = usageColor
                        )
                    }
                    
                    LinearProgressIndicator(
                        progress = { chatStats.contextUsagePercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = usageColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {}
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.tokens_usage_format, TokenUtils.formatNumber(chatStats.estimatedTokens), TokenUtils.formatNumber(chatStats.maxTokens)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        if (chatStats.systemPromptTokens > 0) {
                            Text(
                                text = stringResource(R.string.system_tokens_format, TokenUtils.formatNumber(chatStats.systemPromptTokens)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 1200,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_alpha"
    )
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Dimensions.Padding.content,
                vertical = Spacing.md
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.cashiro),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(alpha = alpha),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Thinking about your request...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer(alpha = alpha)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    isStreaming: Boolean = false,
    onEdit: (ChatMessage) -> Unit = {},
    onDelete: (ChatMessage) -> Unit = {},
    onCopy: (ChatMessage) -> Unit = {},
    onRegenerate: (ChatMessage) -> Unit = {}
) {
    val timeFormat = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }
    var showTimestamp by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (message.isUser) {
            Box {
                Card(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .animateContentSize()
                        .clip(
                            RoundedCornerShape(
                                topEnd = 24.dp,
                                topStart = 24.dp,
                                bottomStart = 24.dp,
                                bottomEnd = 8.dp
                            )
                        )
                        .combinedClickable(
                            onClick = { showTimestamp = !showTimestamp },
                            onLongClick = { showMenu = true }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(
                        topEnd = 24.dp,
                        topStart = 24.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 8.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Padding.content)
                    ) {
                        Text(
                            text = message.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        AnimatedVisibility(visible = showTimestamp) {
                            Column {
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    Text(
                                        text = timeFormat.format(java.util.Date(message.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = {
                            showMenu = false
                            onCopy(message)
                        },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) }
                    )
                    HorizontalDivider(
                        thickness = 1.5.dp,
                        color = MaterialTheme.colorScheme.surface.copy(0.6f)
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit(message)
                        },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
                    )
                    HorizontalDivider(
                        thickness = 1.5.dp,
                        color = MaterialTheme.colorScheme.surface.copy(0.6f)
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete(message)
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) }
                    )
                }
            }
        } else {
            // AI Message Layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm)
            ) {
                // Markdown Content
                Markdown(
                    content = message.message,
                    modifier = Modifier.fillMaxWidth(),
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        h2 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        h3 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        h4 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        h5 = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        h6 = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                )
                
                Spacer(modifier = Modifier.height(Spacing.xs))
                
                // Bottom row with streaming indicator, time and actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isStreaming) {
                        LoadingCircle(modifier = Modifier.size(12.dp))
                    }
                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )

                    // Action Buttons (Only show when not streaming)
                    if (!isStreaming) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            IconButton(
                                onClick = { onCopy(message) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                )
                            }
                            IconButton(
                                onClick = { onRegenerate(message) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Regenerate",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                )
                            }
                            IconButton(
                                onClick = { onDelete(message) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Iconax.Bag,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
