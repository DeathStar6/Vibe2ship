package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.BackendClient
import com.example.api.BenchmarkManager
import com.example.api.TaskApiModel
import com.example.api.AnalyzeRequest
import com.example.api.ChatRequest
import com.example.api.SuggestedAction
import com.example.api.ActionTaken
import com.example.api.ErrorResponse
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
import java.util.UUID
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

    private var lastRequest: (suspend () -> Unit)? = null
    private val _canRetry = MutableStateFlow(false)
    val canRetry: StateFlow<Boolean> = _canRetry.asStateFlow()

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
                id = UUID.randomUUID().toString(),
                title = "Draft Q1 Quarterly Proposal",
                description = "Outline budget proposals, client acquisition stats, and feature pipeline details.",
                deadline = "today 5 PM",
                priority = "HIGH"
            )
        )
        repository.insertTask(
            TaskEntity(
                id = UUID.randomUUID().toString(),
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

    // Manual add task from form - Optimistic UI!
    fun addNewTask(title: String, description: String, deadline: String, priority: String) {
        val newId = UUID.randomUUID().toString()
        viewModelScope.launch {
            repository.insertTask(
                TaskEntity(
                    id = newId,
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
            // Trigger background analysis without blocking the UI flow
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
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTaskById(taskId)
        }
    }

    fun acceptSuggestedAction(task: TaskEntity, actionJson: String) {
        viewModelScope.launch {
            // Simplified: actionJson would contain enough to know what tool to apply
            // For now, if the suggestion is to complete or prioritize, we apply it.
            if (actionJson.contains("mark_task_complete", ignoreCase = true)) {
                repository.updateTaskCompletion(task.id, true)
            } else if (actionJson.contains("update_task_priority", ignoreCase = true)) {
                // Regex or simple parse to find new priority
                val newPriority = if (actionJson.contains("HIGH")) "HIGH" else if (actionJson.contains("MEDIUM")) "MEDIUM" else "LOW"
                repository.updateTaskPriority(task.id, newPriority)
            }
            repository.updateTaskPendingAction(task.id, false, null)
            repository.insertSuggestion(SuggestionEntity(message = "Accepted AI suggestion for: ${task.title}", type = "INFO"))
        }
    }

    fun rejectSuggestedAction(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTaskPendingAction(task.id, false, null)
            repository.insertSuggestion(SuggestionEntity(message = "Rejected AI suggestion for: ${task.title}", type = "INFO"))
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
            _canRetry.value = false

            val currentTasks = tasks.value.map { it.toApiModel() }

            val actionToExecute: suspend () -> Unit = {
                val startTime = System.currentTimeMillis()
                try {
                    _isAnalyzing.value = true
                    _errorMessage.value = null

                    val response = if (userRequestMessage.isNullOrBlank()) {
                        BackendClient.service.analyze(AnalyzeRequest(tasks = currentTasks))
                    } else {
                        BackendClient.service.chat(ChatRequest(message = userRequestMessage, tasks = currentTasks))
                    }

                    val duration = System.currentTimeMillis() - startTime
                    BenchmarkManager.recordRequest(duration, response.actionsTaken.size + response.suggestedActions.size, true)

                    // 1. Sync updated tasks
                    repository.insertTasks(response.updatedTasks.map { it.toEntity() })

                    // 2. Handle Suggested Actions (Human-in-the-Loop)
                    for (suggestion in response.suggestedActions) {
                        val taskId = suggestion.arguments["task_id"] as? String
                        taskId?.let { id ->
                            repository.updateTaskPendingAction(id, true, suggestion.reason ?: suggestion.tool)
                        }
                    }

                    // 3. Log agent_summary + actions_taken
                    _aiInsight.value = response.agentSummary
                    repository.insertSuggestion(SuggestionEntity(message = response.agentSummary, type = "DECISION"))

                    for (action in response.actionsTaken) {
                        val taskId = action.arguments?.get("task_id") as? String
                        val taskIdStr = taskId?.let { " (Task: $it)" } ?: ""
                        val confidenceStr = " [Conf: ${(action.confidence * 100).toInt()}%]"
                        val urgencyStr = " [Urgency: ${action.urgency}]"
                        repository.insertSuggestion(
                            SuggestionEntity(
                                message = "Action [${action.tool.uppercase()}]$taskIdStr: ${action.reason}$confidenceStr$urgencyStr",
                                type = "DECISION"
                            )
                        )
                    }
                    _canRetry.value = false
                    lastRequest = null
                    
                    // Periodically output benchmark
                    BenchmarkManager.generateReport()
                } catch (e: Exception) {
                    BenchmarkManager.recordRequest(0, 0, false)
                    e.printStackTrace()
                    
                    var friendlyMessage = "AI Connection Failed"
                    
                    if (e is retrofit2.HttpException) {
                        try {
                            val errorBody = e.response()?.errorBody()?.string()
                            val errorResponse = errorBody?.let {
                                BackendClient.moshi.adapter(ErrorResponse::class.java).fromJson(it)
                            }
                            
                            if (errorResponse != null) {
                                Log.e("MainViewModel", "AI Error: ${errorResponse.error} (Source: ${errorResponse.source}, Retryable: ${errorResponse.retryable})")
                                if (errorResponse.source == "gemini" && errorResponse.retryable) {
                                    friendlyMessage = "AI is temporarily busy. Please try again in a few moments."
                                } else if (!errorResponse.retryable) {
                                    friendlyMessage = "AI request failed. Please review your request and try again."
                                }
                            } else {
                                Log.e("MainViewModel", "AI HTTP Error: ${e.code()} - ${e.message()}")
                            }
                        } catch (parseException: Exception) {
                            Log.e("MainViewModel", "Failed to parse AI error response", parseException)
                        }
                    } else {
                        Log.e("MainViewModel", "Network/AI Error", e)
                    }
                    
                    _errorMessage.value = friendlyMessage
                    _canRetry.value = true
                } finally {
                    _isAnalyzing.value = false
                }
            }

            // Save the action for potential retry
            lastRequest = { actionToExecute() }
            
            // Execute the network call
            actionToExecute()
        }
    }

    fun retryLastAction() {
        val request = lastRequest
        if (request != null) {
            viewModelScope.launch {
                request.invoke()
            }
        }
    }

    private fun TaskEntity.toApiModel(): TaskApiModel {
        return TaskApiModel(
            id = this.id,
            title = this.title,
            description = this.description,
            deadline = this.deadline,
            priority = this.priority,
            isCompleted = this.isCompleted,
            steps = this.getStepsList(),
            reminder = this.reminderMessage
        )
    }

    private fun TaskApiModel.toEntity(): TaskEntity {
        return TaskEntity(
            id = this.id,
            title = this.title,
            description = this.description ?: "",
            deadline = this.deadline,
            priority = this.priority,
            isCompleted = this.isCompleted ?: false,
            stepsJson = TaskEntity.createStepsJson(this.steps ?: emptyList()),
            reminderMessage = this.reminder
        )
    }
}
