package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END, deadline ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0")
    suspend fun getIncompleteTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("UPDATE tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateTaskCompletion(id: String, isCompleted: Boolean)

    @Query("UPDATE tasks SET priority = :priority WHERE id = :id")
    suspend fun updateTaskPriority(id: String, priority: String)

    @Query("UPDATE tasks SET stepsJson = :stepsJson WHERE id = :id")
    suspend fun updateTaskSteps(id: String, stepsJson: String)

    @Query("UPDATE tasks SET reminderMessage = :message WHERE id = :id")
    suspend fun updateTaskReminder(id: String, message: String?)

    @Query("UPDATE tasks SET isPendingAction = :isPending, pendingActionJson = :actionJson WHERE id = :id")
    suspend fun updateTaskPendingAction(id: String, isPending: Boolean, actionJson: String?)
}
