package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TaskApiModel(
    val id: String,
    val title: String,
    val description: String?,
    val deadline: String,
    val priority: String,
    @Json(name = "isCompleted") val isCompleted: Boolean?,
    val steps: List<String>?,
    val reminder: String?
)

@JsonClass(generateAdapter = true)
data class ActionTaken(
    val tool: String,
    val reason: String? = null,
    val confidence: Double = 0.7,
    val urgency: Int = 50,
    val classification: String = "AUTO_SAFE",
    val arguments: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class SuggestedAction(
    val tool: String,
    val reason: String? = null,
    val confidence: Double = 0.7,
    val urgency: Int = 50,
    val classification: String = "REQUIRES_APPROVAL",
    val arguments: Map<String, Any> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class AgentResponse(
    @Json(name = "actions_taken") val actionsTaken: List<ActionTaken>,
    @Json(name = "suggested_actions") val suggestedActions: List<SuggestedAction> = emptyList(),
    @Json(name = "agent_summary") val agentSummary: String,
    @Json(name = "updated_tasks") val updatedTasks: List<TaskApiModel>
)

@JsonClass(generateAdapter = true)
data class AnalyzeRequest(
    val tasks: List<TaskApiModel>
)

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val message: String,
    val tasks: List<TaskApiModel>
)

@JsonClass(generateAdapter = true)
data class ErrorResponse(
    val error: String,
    val retryable: Boolean,
    val source: String
)
