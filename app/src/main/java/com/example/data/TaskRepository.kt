package com.example.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao, private val suggestionDao: SuggestionDao) {
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val allSuggestions: Flow<List<SuggestionEntity>> = suggestionDao.getAllSuggestions()

    suspend fun getIncompleteTasks(): List<TaskEntity> = taskDao.getIncompleteTasks()

    suspend fun getTaskById(id: String): TaskEntity? = taskDao.getTaskById(id)

    suspend fun insertTask(task: TaskEntity) = taskDao.insertTask(task)

    suspend fun insertTasks(tasks: List<TaskEntity>) = taskDao.insertTasks(tasks)

    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun deleteTaskById(id: String) = taskDao.deleteTaskById(id)

    suspend fun updateTaskCompletion(id: String, isCompleted: Boolean) = taskDao.updateTaskCompletion(id, isCompleted)

    suspend fun updateTaskPriority(id: String, priority: String) = taskDao.updateTaskPriority(id, priority)

    suspend fun updateTaskSteps(id: String, steps: List<String>) {
        val stepsJson = TaskEntity.createStepsJson(steps)
        taskDao.updateTaskSteps(id, stepsJson)
    }

    suspend fun updateTaskReminder(id: String, message: String?) = taskDao.updateTaskReminder(id, message)

    suspend fun updateTaskPendingAction(id: String, isPending: Boolean, actionJson: String?) = 
        taskDao.updateTaskPendingAction(id, isPending, actionJson)

    suspend fun insertSuggestion(suggestion: SuggestionEntity) = suggestionDao.insertSuggestion(suggestion)

    suspend fun clearAllSuggestions() = suggestionDao.clearAllSuggestions()
}
