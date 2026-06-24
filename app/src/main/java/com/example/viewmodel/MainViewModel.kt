package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GeminiClient
import com.example.api.GeminiTools
import com.example.api.Part
import com.example.api.FunctionResponse
import com.example.data.AppDatabase
import com.example.data.SuggestionEntity
import com.example.data.TaskEntity
import com.example.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository
    val tasks: StateFlow<List<TaskEntity>>
    val suggestions: StateFlow<List<SuggestionEntity>>

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _aiInsight = MutableStateFlow("I am currently monitoring your tasks. Ask me to optimize your schedule, or add some tasks to see me take action!")
    val aiInsight: StateFlow<String> = _aiInsight.asStateFlow()

    private val _nextUpTask = MutableStateFlow<TaskEntity?>(null)
    val nextUpTask: StateFlow<TaskEntity?> = _nextUpTask.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TaskRepository(database.taskDao(), database.suggestionDao())
        
        tasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        suggestions = repository.allSuggestions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Prepopulate with a few starter tasks and a friendly suggestion if the database is empty
        viewModelScope.launch {
            repository.allTasks.collect { list ->
                if (list.isEmpty()) {
                    prepopulateData()
                } else {
                    // Update the "Next Up" task based on priority and deadline
                    val highPriority = list.filter { !it.isCompleted }.sortedWith(
                        compareBy<TaskEntity> {
                            when (it.priority) {
                                "HIGH" -> 1
                                "MEDIUM" -> 2
                                "LOW" -> 3
                                else -> 4
                            }
                        }.thenBy { it.deadline }
                    )
                    _nextUpTask.value = highPriority.firstOrNull()
                }
            }
        }
    }

    private suspend fun prepopulateData() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        repository.insertTask(
            TaskEntity(
                title = "Draft Q1 Quarterly Proposal",
                description = "Outline budget proposals, client acquisition stats, and feature pipeline details.",
                deadline = "today 5 PM",
                priority = "HIGH"
            )
        )
        repository.insertTask(
            TaskEntity(
                title = "Sync with Dev Team",
                description = "Quick status update on current sprint blockers and deployment plan.",
                deadline = "tomorrow 10 AM",
                priority = "MEDIUM"
            )
        )
        repository.insertSuggestion(
            SuggestionEntity(
                message = "Welcome to Last-Minute Life Saver! I've pre-loaded some tasks for you. Tap 'Run Agent Analysis' or ask me to optimize in the chat below.",
                type = "INFO"
            )
        )
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // Manual add task from form
    fun addNewTask(title: String, description: String, deadline: String, priority: String) {
        viewModelScope.launch {
            repository.insertTask(
                TaskEntity(
                    title = title,
                    description = description,
                    deadline = deadline,
                    priority = priority
                )
            )
            repository.insertSuggestion(
                SuggestionEntity(
                    message = "Added task: \"$title\". I'm analyzing how this affects your priority schedule.",
                    type = "INFO"
                )
            )
            // Trigger automatic background analysis for a proactive assistant experience!
            triggerAutonomousAgentAnalysis(null)
        }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            val updatedState = !task.isCompleted
            repository.updateTaskCompletion(task.id, updatedState)
            repository.insertSuggestion(
                SuggestionEntity(
                    message = "Marked task \"${task.title}\" as ${if (updatedState) "completed" else "active"}.",
                    type = "INFO"
                )
            )
            // Trigger proactive update
            triggerAutonomousAgentAnalysis(null)
        }
    }

    fun deleteTask(taskId: Int) {
        viewModelScope.launch {
            repository.deleteTaskById(taskId)
            triggerAutonomousAgentAnalysis(null)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllSuggestions()
        }
    }

    // Function to run the AI Agent's analysis loop autonomously!
    fun triggerAutonomousAgentAnalysis(userRequestMessage: String?) {
        viewModelScope.launch {
            if (_isAnalyzing.value) return@launch
            _isAnalyzing.value = true
            _errorMessage.value = null

            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _errorMessage.value = "Gemini API Key is not set or placeholder. Please set a valid key in the Secrets panel."
                    _isAnalyzing.value = false
                    return@launch
                }

                // Gather current task context
                val currentTasksList = tasks.value
                val formattedTasksText = if (currentTasksList.isEmpty()) {
                    "No active tasks."
                } else {
                    currentTasksList.joinToString("\n") { task ->
                        "ID: ${task.id} | Title: ${task.title} | Priority: ${task.priority} | Deadline: ${task.deadline} | Completed: ${task.isCompleted} | Description: ${task.description} | Steps: ${task.getStepsList().joinToString(", ")}"
                    }
                }

                val systemPrompt = """
                    You are "Last-Minute Life Saver" - a proactive, high-performance, autonomous AI productivity agent designed to save a user's day when they are overwhelmed.
                    You analyze their task list and make smart, autonomous scheduling adjustments.
                    You have access to 5 crucial tools to directly manage their tasks:
                    1. 'add_task': Adds a brand new task.
                    2. 'update_task_priority': Bumps priority up or down based on deadlines/importance.
                    3. 'break_task_into_steps': Breaks down a broad or difficult task into a list of 2-5 bite-sized actionable sub-steps.
                    4. 'send_reminder': Sends a proactive, highly customized nudge or tip for a specific urgent task.
                    5. 'mark_task_complete': Marks a task as completed.

                    Your instructions:
                    - Actively look for things that need fixing:
                      * If a task has a short deadline but LOW/MEDIUM priority, update its priority to HIGH.
                      * If a task is very general or broad (e.g. "Review budget", "Q1 Proposal"), autonomously call 'break_task_into_steps' to create concrete actions.
                      * If a HIGH priority task is due soon, call 'send_reminder' with an action-focused, motivating reminder.
                      * If the user specifically asks you to do something (e.g., in a re-planning chat prompt), execute it by calling the appropriate tool.
                    - You can make multiple tool calls in a single turn. You can chain actions.
                    - Be helpful, direct, and action-oriented.
                    - Always output your final answer (after all tool calls have completed) as a concise, empathetic, high-level summary of what adjustments you made and why. Keep this summary within 3 sentences so it fits perfectly on the Bento Dashboard.
                """.trimIndent()

                // Construct initial contents
                val userPrompt = if (userRequestMessage.isNullOrBlank()) {
                    "Analyze my task list autonomously and perform any required actions (prioritize, break down, or send reminders) to optimize my day."
                } else {
                    userRequestMessage
                }

                val fullPromptText = """
                    Current Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
                    Current Active Tasks:
                    $formattedTasksText
                    
                    User Request/Trigger:
                    $userPrompt
                """.trimIndent()

                val chatContents = mutableListOf<Content>()
                chatContents.add(
                    Content(
                        role = "user",
                        parts = listOf(Part(text = fullPromptText))
                    )
                )

                var continueLoop = true
                var iterationCount = 0
                val maxIterations = 5 // Protect against infinite tool invocation loops

                while (continueLoop && iterationCount < maxIterations) {
                    iterationCount++
                    
                    val request = GenerateContentRequest(
                        contents = chatContents,
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        tools = GeminiTools.allTools
                    )

                    val response = GeminiClient.service.generateContent(apiKey, request)
                    val candidate = response.candidates?.firstOrNull()
                    val assistantContent = candidate?.content

                    if (assistantContent != null) {
                        // Append assistant content to the conversation history
                        chatContents.add(assistantContent)

                        val functionCalls = assistantContent.parts.mapNotNull { it.functionCall }

                        if (functionCalls.isNotEmpty()) {
                            // Execute functions and generate function responses
                            val responseParts = mutableListOf<Part>()
                            
                            for (call in functionCalls) {
                                val result = executeTool(call.name, call.args ?: emptyMap())
                                responseParts.add(
                                    Part(
                                        functionResponse = FunctionResponse(
                                            name = call.name,
                                            response = mapOf("result" to result)
                                        )
                                    )
                                )
                            }

                            // Append function response content
                            chatContents.add(
                                Content(
                                    role = "function",
                                    parts = responseParts
                                )
                            )
                        } else {
                            // No function call returned, so the agent has finished its work!
                            val finalReplyText = assistantContent.parts.firstOrNull { it.text != null }?.text
                            if (finalReplyText != null) {
                                _aiInsight.value = finalReplyText
                                repository.insertSuggestion(
                                    SuggestionEntity(
                                        message = finalReplyText,
                                        type = "DECISION"
                                    )
                                )
                            }
                            continueLoop = false
                        }
                    } else {
                        continueLoop = false
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Error executing AI Agent: ${e.localizedMessage ?: e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private suspend fun executeTool(name: String, args: Map<String, Any>): String {
        return try {
            when (name) {
                "add_task" -> {
                    val title = args["title"]?.toString() ?: "Unnamed Task"
                    val description = args["description"]?.toString() ?: ""
                    val deadline = args["deadline"]?.toString() ?: "Today"
                    val priority = args["priority"]?.toString()?.uppercase() ?: "MEDIUM"

                    repository.insertTask(
                        TaskEntity(
                            title = title,
                            description = description,
                            deadline = deadline,
                            priority = priority
                        )
                    )
                    val logMessage = "Autonomously added task: \"$title\" ($priority priority, due $deadline)"
                    repository.insertSuggestion(SuggestionEntity(message = logMessage, type = "DECISION"))
                    "Successfully added task: $title"
                }

                "update_task_priority" -> {
                    val id = safeToInt(args["id"]) ?: return "Error: Invalid task ID"
                    val priority = args["priority"]?.toString()?.uppercase() ?: "MEDIUM"

                    val task = repository.getTaskById(id)
                    if (task != null) {
                        repository.updateTaskPriority(id, priority)
                        val logMessage = "Autonomously re-prioritized \"${task.title}\" from ${task.priority} to $priority"
                        repository.insertSuggestion(SuggestionEntity(message = logMessage, type = "DECISION"))
                        "Successfully updated priority of task $id to $priority"
                    } else {
                        "Error: Task with ID $id not found"
                    }
                }

                "break_task_into_steps" -> {
                    val id = safeToInt(args["id"]) ?: return "Error: Invalid task ID"
                    val stepsList = safeToStringList(args["steps"])

                    val task = repository.getTaskById(id)
                    if (task != null) {
                        repository.updateTaskSteps(id, stepsList)
                        val logMessage = "Autonomously broke down \"${task.title}\" into ${stepsList.size} sub-steps"
                        repository.insertSuggestion(SuggestionEntity(message = logMessage, type = "DECISION"))
                        "Successfully added steps to task $id"
                    } else {
                        "Error: Task with ID $id not found"
                    }
                }

                "send_reminder" -> {
                    val id = safeToInt(args["id"]) ?: return "Error: Invalid task ID"
                    val message = args["message"]?.toString() ?: "Don't forget this task!"

                    val task = repository.getTaskById(id)
                    if (task != null) {
                        repository.updateTaskReminder(id, message)
                        val logMessage = "Proactive Reminder: \"$message\" (for \"${task.title}\")"
                        repository.insertSuggestion(SuggestionEntity(message = logMessage, type = "REMINDER"))
                        "Successfully sent reminder for task $id"
                    } else {
                        "Error: Task with ID $id not found"
                    }
                }

                "mark_task_complete" -> {
                    val id = safeToInt(args["id"]) ?: return "Error: Invalid task ID"

                    val task = repository.getTaskById(id)
                    if (task != null) {
                        repository.updateTaskCompletion(id, true)
                        val logMessage = "Autonomously completed \"${task.title}\""
                        repository.insertSuggestion(SuggestionEntity(message = logMessage, type = "DECISION"))
                        "Successfully marked task $id as completed"
                    } else {
                        "Error: Task with ID $id not found"
                    }
                }

                else -> "Error: Unknown function name $name"
            }
        } catch (e: Exception) {
            "Error executing function $name: ${e.message}"
        }
    }

    private fun safeToInt(value: Any?): Int? {
        if (value == null) return null
        if (value is Number) return value.toInt()
        val str = value.toString()
        return str.toDoubleOrNull()?.toInt() ?: str.toIntOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun safeToStringList(value: Any?): List<String> {
        if (value == null) return emptyList()
        if (value is List<*>) {
            return value.map { it.toString() }
        }
        return emptyList()
    }
}
