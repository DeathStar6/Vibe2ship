package com.example.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao, private val suggestionDao: SuggestionDao) {
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val allSuggestions: Flow<List<SuggestionEntity>> = suggestionDao.getAllSuggestions()

    suspend fun getTaskById(id: Int): TaskEntity? = taskDao.getTaskById(id)

    suspend fun insertTask(task: TaskEntity): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun deleteTaskById(id: Int) = taskDao.deleteTaskById(id)

    suspend fun updateTaskCompletion(id: Int, isCompleted: Boolean) = taskDao.updateTaskCompletion(id, isCompleted)

    suspend fun updateTaskPriority(id: Int, priority: String) = taskDao.updateTaskPriority(id, priority)

    suspend fun updateTaskSteps(id: Int, steps: List<String>) {
        val stepsJson = TaskEntity.createStepsJson(steps)
        taskDao.updateTaskSteps(id, stepsJson)
    }

    suspend fun updateTaskReminder(id: Int, message: String?) = taskDao.updateTaskReminder(id, message)

    suspend fun insertSuggestion(suggestion: SuggestionEntity) = suggestionDao.insertSuggestion(suggestion)

    suspend fun clearAllSuggestions() = suggestionDao.clearAllSuggestions()
}
