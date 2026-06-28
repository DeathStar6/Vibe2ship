package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.api.AnalyzeRequest
import com.example.api.BackendClient
import com.example.api.TaskApiModel
import com.example.data.AppDatabase
import com.example.data.SuggestionEntity
import com.example.data.TaskEntity
import com.example.data.TaskRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AgentAnalysisWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TaskRepository(database.taskDao(), database.suggestionDao())

        try {
            // 1. Read all current incomplete tasks from Room database
            val incompleteTasks = repository.getIncompleteTasks()

            // 2. Skip run if the task list is empty
            if (incompleteTasks.isEmpty()) {
                repository.insertSuggestion(
                    SuggestionEntity(
                        message = "Background AI Agent checked: No active tasks found to analyze.",
                        type = "INFO"
                    )
                )
                return Result.success()
            }

            // 3. Map tasks to API models
            val currentTasksApi = incompleteTasks.map { it.toApiModel() }

            // 4. Call the backend POST /agent/analyze
            val request = AnalyzeRequest(tasks = currentTasksApi)
            val response = BackendClient.service.analyze(request)

            // 5. Update Room database with updated_tasks
            val entitiesToSync = response.updatedTasks.map { it.toEntity() }
            repository.insertTasks(entitiesToSync)

            // 6. Log agent_summary to Suggestions table
            repository.insertSuggestion(
                SuggestionEntity(
                    message = response.agentSummary,
                    type = "DECISION"
                )
            )

            // 7. Log each autonomous action in actions_taken
            for (action in response.actionsTaken) {
                val taskId = action.arguments?.get("task_id") as? String
                val taskIdStr = taskId?.let { " (Task ID: $it)" } ?: ""
                val toolFriendlyName = action.tool.replace("_", " ").uppercase()
                repository.insertSuggestion(
                    SuggestionEntity(
                        message = "Autonomous action [${toolFriendlyName}]${taskIdStr}: ${action.reason ?: "Executed successfully."}",
                        type = "DECISION"
                    )
                )
            }

            // 8. Trigger local notification if background run resulted in meaningful actions
            if (response.actionsTaken.isNotEmpty()) {
                showNotification(applicationContext, response.actionsTaken.size)
            }

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fail gracefully and let WorkManager retry later
            return Result.retry()
        }
    }

    private fun showNotification(context: Context, actionsCount: Int) {
        val channelId = "agent_analysis_channel"
        val notificationId = 1001

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AI Agent Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about background AI schedule optimizations"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Schedule Optimized")
            .setContentText("Your AI agent updated $actionsCount tasks while you were away.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
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
