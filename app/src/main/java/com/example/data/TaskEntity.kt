package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val deadline: String,
    val priority: String, // "HIGH", "MEDIUM", "LOW"
    val isCompleted: Boolean = false,
    val stepsJson: String = "[]", // Serialized JSON list of sub-steps
    val reminderMessage: String? = null
) {
    fun getStepsList(): List<String> {
        return try {
            val moshi = Moshi.Builder().build()
            val listType = Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = moshi.adapter<List<String>>(listType)
            adapter.fromJson(stepsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun createStepsJson(steps: List<String>): String {
            return try {
                val moshi = Moshi.Builder().build()
                val listType = Types.newParameterizedType(List::class.java, String::class.java)
                val adapter = moshi.adapter<List<String>>(listType)
                adapter.toJson(steps)
            } catch (e: Exception) {
                "[]"
            }
        }
    }
}
