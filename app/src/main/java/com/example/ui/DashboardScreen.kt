package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SuggestionEntity
import com.example.data.TaskEntity
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val aiInsight by viewModel.aiInsight.collectAsState()
    val nextUpTask by viewModel.nextUpTask.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val canRetry by viewModel.canRetry.collectAsState()

    var showAddTaskDialog by varOf(false)
    var chatInputText by varOf("")
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BentoBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BentoBg,
                    titleContentColor = BentoPrimary
                ),
                title = {
                    Column {
                        Text(
                            text = "Life Saver",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Autonomous productivity",
                            fontSize = 12.sp,
                            color = Color(0xFF49454F)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.triggerAutonomousAgentAnalysis(null) },
                        enabled = !isAnalyzing,
                        modifier = Modifier
                            .testTag("sync_icon_button")
                            .clip(CircleShape)
                            .background(BentoPrimaryContainer)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = BentoOnPrimaryContainer,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync AI Agent",
                                tint = BentoOnPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
            )
        },
        bottomBar = {
            // Chat re-planner bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                color = BentoLogBg,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, BentoBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "AI Agent Assistant",
                            tint = BentoPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = chatInputText,
                        onValueChange = { chatInputText = it },
                        placeholder = { Text("Ask agent to re-plan...") },
                        modifier = Modifier
                            .weight(1.0f)
                            .testTag("chat_input_text_field"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = BentoPrimary,
                            unfocusedBorderColor = BentoBorder,
                            focusedTextColor = Color(0xFF1D1B20),
                            unfocusedTextColor = Color(0xFF1D1B20),
                            focusedPlaceholderColor = Color(0xFF49454F),
                            unfocusedPlaceholderColor = Color(0xFF49454F)
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = BentoPrimary
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (chatInputText.isNotBlank()) {
                                            viewModel.triggerAutonomousAgentAnalysis(chatInputText)
                                            chatInputText = ""
                                            focusManager.clearFocus()
                                        }
                                    },
                                    modifier = Modifier
                                        .testTag("chat_send_button")
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(BentoPrimary)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Send Prompt",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Show errors if any
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (canRetry) {
                                    TextButton(
                                        onClick = { viewModel.retryLastAction() },
                                        modifier = Modifier.testTag("retry_button")
                                    ) {
                                        Text(
                                            text = "Retry",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.clearError() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Error",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Bento Grid responsive wrapper
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val isWide = maxWidth >= 600.dp
                    if (isWide) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Left column: Next Up & Insight
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NextUpCard(
                                    task = nextUpTask,
                                    modifier = Modifier.weight(1.2f)
                                )
                                AutonomousInsightCard(
                                    insight = aiInsight,
                                    isAnalyzing = isAnalyzing,
                                    onAnalyzeClick = { viewModel.triggerAutonomousAgentAnalysis(null) },
                                    modifier = Modifier.weight(0.8f)
                                )
                            }

                            // Right column: Tasks & Agent Log
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SuggestedActionsCard(
                                    tasks = tasks,
                                    onAccept = { task, actionJson -> viewModel.acceptSuggestedAction(task, actionJson) },
                                    onReject = { task -> viewModel.rejectSuggestedAction(task) }
                                )
                                TasksCard(
                                    tasks = tasks,
                                    onAddTaskClick = { showAddTaskDialog = true },
                                    onToggleComplete = { viewModel.toggleTaskCompletion(it) },
                                    onDeleteTask = { viewModel.deleteTask(it) },
                                    modifier = Modifier.weight(1.1f)
                                )
                                AgentLogCard(
                                    suggestions = suggestions,
                                    onClearLogs = { viewModel.clearLogs() },
                                    modifier = Modifier.weight(0.9f)
                                )
                            }
                        }
                    } else {
                        // Phone scrollable vertical layout
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NextUpCard(
                                task = nextUpTask,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp)
                            )
                            SuggestedActionsCard(
                                tasks = tasks,
                                onAccept = { task, actionJson -> viewModel.acceptSuggestedAction(task, actionJson) },
                                onReject = { task -> viewModel.rejectSuggestedAction(task) }
                            )
                            TasksCard(
                                tasks = tasks,
                                onAddTaskClick = { showAddTaskDialog = true },
                                onToggleComplete = { viewModel.toggleTaskCompletion(it) },
                                onDeleteTask = { viewModel.deleteTask(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                            )
                            AgentLogCard(
                                suggestions = suggestions,
                                onClearLogs = { viewModel.clearLogs() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                            AutonomousInsightCard(
                                insight = aiInsight,
                                isAnalyzing = isAnalyzing,
                                onAnalyzeClick = { viewModel.triggerAutonomousAgentAnalysis(null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 150.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, deadline, priority ->
                viewModel.addNewTask(title, desc, deadline, priority)
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
fun NextUpCard(
    task: TaskEntity?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("next_up_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = BentoPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BentoPrimaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "AI NEXT UP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoOnPrimaryContainer,
                            letterSpacing = 1.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Bolt / Priority Star",
                        tint = BentoPink,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (task != null) {
                    Text(
                        text = task.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Show sub-steps if any have been broken down autonomously
                    val stepsList = task.getStepsList()
                    if (stepsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Actionable Steps:",
                                color = BentoPink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            stepsList.take(3).forEach { step ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(BentoPink)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = step,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Show any active proactive reminders
                    task.reminderMessage?.let { reminder ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoPink.copy(alpha = 0.15f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Reminder Alert",
                                tint = BentoPink,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = reminder,
                                color = BentoPink,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All clear! No pending high priority tasks.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            if (task != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Deadline Clock",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Due: ${task.deadline}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = task.priority,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TasksCard(
    tasks: List<TaskEntity>,
    onAddTaskClick: () -> Unit,
    onToggleComplete: (TaskEntity) -> Unit,
    onDeleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("tasks_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = BentoSecondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Tasks",
                        tint = BentoOnPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tasks",
                        fontWeight = FontWeight.Bold,
                        color = BentoOnPrimaryContainer,
                        fontSize = 15.sp
                    )
                }
                IconButton(
                    onClick = onAddTaskClick,
                    modifier = Modifier
                        .testTag("add_task_plus_button")
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BentoPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Task",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val activeTasks = tasks.filter { !it.isCompleted }
            if (activeTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active tasks. Tap '+' to add!",
                        fontSize = 12.sp,
                        color = BentoOnPrimaryContainer.copy(alpha = 0.6f),
                        fontStyle = FontStyle.Italic
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeTasks, key = { it.id }) { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { onToggleComplete(task) },
                                colors = CheckboxDefaults.colors(checkedColor = BentoPrimary),
                                modifier = Modifier.testTag("task_checkbox_${task.id}")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color(0xFF1D1B20)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = task.deadline,
                                        fontSize = 10.sp,
                                        color = Color(0xFF49454F)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                when (task.priority) {
                                                    "HIGH" -> BentoPink
                                                    "MEDIUM" -> BentoBg
                                                    else -> BentoLogBg
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = task.priority,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (task.priority) {
                                                "HIGH" -> BentoPinkText
                                                else -> BentoOnPrimaryContainer
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { onDeleteTask(task.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Task",
                                    tint = Color.Red.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentLogCard(
    suggestions: List<SuggestionEntity>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("agent_log_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BentoBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Agent Log",
                        tint = BentoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Agent Log",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20),
                        fontSize = 14.sp
                    )
                }
                if (suggestions.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        color = BentoPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .testTag("clear_logs_button")
                            .clickable { onClearLogs() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (suggestions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No autonomous logs yet.",
                        fontSize = 12.sp,
                        color = Color(0xFF49454F).copy(alpha = 0.6f),
                        fontStyle = FontStyle.Italic
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(suggestions, key = { it.id }) { log ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoLogBg)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(
                                    text = log.type,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = log.message,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    color = Color(0xFF1D1B20)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutonomousInsightCard(
    insight: String,
    isAnalyzing: Boolean,
    onAnalyzeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("autonomous_insight_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = BentoPink),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Insight icon",
                            tint = BentoPinkText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Autonomous Insight",
                            fontWeight = FontWeight.Bold,
                            color = BentoPinkText,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "\"$insight\"",
                    color = BentoPinkText,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 16.sp
                )
            }

            Button(
                onClick = onAnalyzeClick,
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BentoPinkText,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier
                    .testTag("insight_analyze_button")
                    .padding(top = 10.dp)
                    .align(Alignment.Start)
                    .height(32.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = Color.White,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Text(
                        text = "Run AI Agent Analysis",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestedActionsCard(
    tasks: List<TaskEntity>,
    onAccept: (TaskEntity, String) -> Unit,
    onReject: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingTasks = tasks.filter { it.isPendingAction }
    if (pendingTasks.isEmpty()) return

    Card(
        modifier = modifier.testTag("suggested_actions_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BentoBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Suggested",
                    tint = BentoPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Suggested Actions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1D1B20)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            pendingTasks.forEach { task ->
                // Basic proposal UI
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoLogBg)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Task: ${task.title}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Proposal: ${task.pendingActionJson ?: "Optimization"}",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onReject(task) }) {
                            Text("Reject", color = Color.Red, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onAccept(task, task.pendingActionJson ?: "") },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Accept", fontSize = 11.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, deadline: String, priority: String) -> Unit
) {
    var title by varOf("")
    var description by varOf("")
    var deadline by varOf("today 5 PM")
    var priority by varOf("HIGH")
    var hasError by varOf(false)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_task_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Urgent Task",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BentoPrimary
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) hasError = false
                    },
                    label = { Text("Task Title") },
                    isError = hasError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_title_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1D1B20),
                        unfocusedTextColor = Color(0xFF1D1B20),
                        focusedLabelColor = Color(0xFF49454F),
                        unfocusedLabelColor = Color(0xFF49454F)
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1D1B20),
                        unfocusedTextColor = Color(0xFF1D1B20),
                        focusedLabelColor = Color(0xFF49454F),
                        unfocusedLabelColor = Color(0xFF49454F)
                    )
                )

                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text("Deadline (e.g., today 5 PM, in 2 hours)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_task_deadline_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1D1B20),
                        unfocusedTextColor = Color(0xFF1D1B20),
                        focusedLabelColor = Color(0xFF49454F),
                        unfocusedLabelColor = Color(0xFF49454F)
                    )
                )

                Text(
                    text = "Priority",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF49454F)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("HIGH", "MEDIUM", "LOW").forEach { p ->
                        val isSelected = priority == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BentoPrimary else BentoLogBg)
                                .clickable { priority = p }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p,
                                color = if (isSelected) Color.White else Color(0xFF1D1B20),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = BentoPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                hasError = true
                            } else {
                                onConfirm(title, description, deadline, priority)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary),
                        modifier = Modifier.testTag("add_task_confirm_button")
                    ) {
                        Text("Add Task")
                    }
                }
            }
        }
    }
}

// Convenient custom delegate helper to reduce compose boilerplate state creation
@Composable
fun <T> varOf(initialValue: T): MutableState<T> = remember { mutableStateOf(initialValue) }
