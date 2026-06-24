package com.example.api

object GeminiTools {
    val addTaskTool = FunctionDeclaration(
        name = "add_task",
        description = "Adds a new task to the user's task list.",
        parameters = Schema(
            type = "OBJECT",
            properties = mapOf(
                "title" to Schema(type = "STRING", description = "The title of the task"),
                "description" to Schema(type = "STRING", description = "A detailed description of what needs to be done"),
                "deadline" to Schema(type = "STRING", description = "When the task is due, e.g. 'in 2 hours', 'today 5 PM', 'tomorrow 10 AM'"),
                "priority" to Schema(type = "STRING", description = "The priority of the task. Must be HIGH, MEDIUM, or LOW")
            ),
            required = listOf("title", "description", "deadline", "priority")
        )
    )

    val updateTaskPriorityTool = FunctionDeclaration(
        name = "update_task_priority",
        description = "Updates the priority of an existing task.",
        parameters = Schema(
            type = "OBJECT",
            properties = mapOf(
                "id" to Schema(type = "INTEGER", description = "The unique numerical ID of the task to update"),
                "priority" to Schema(type = "STRING", description = "The new priority. Must be HIGH, MEDIUM, or LOW")
            ),
            required = listOf("id", "priority")
        )
    )

    val breakTaskIntoStepsTool = FunctionDeclaration(
        name = "break_task_into_steps",
        description = "Breaks down a broad or complex task into multiple smaller, actionable sub-steps.",
        parameters = Schema(
            type = "OBJECT",
            properties = mapOf(
                "id" to Schema(type = "INTEGER", description = "The unique numerical ID of the task"),
                "steps" to Schema(
                    type = "ARRAY",
                    items = Schema(type = "STRING"),
                    description = "A list of 2 to 5 actionable sub-steps to complete the task"
                )
            ),
            required = listOf("id", "steps")
        )
    )

    val sendReminderTool = FunctionDeclaration(
        name = "send_reminder",
        description = "Autonomously sends a proactive reminder or nudge message for an urgent or high-priority task.",
        parameters = Schema(
            type = "OBJECT",
            properties = mapOf(
                "id" to Schema(type = "INTEGER", description = "The unique numerical ID of the task"),
                "message" to Schema(type = "STRING", description = "An urgent, encouraging, or helpful reminder nudge message")
            ),
            required = listOf("id", "message")
        )
    )

    val markTaskCompleteTool = FunctionDeclaration(
        name = "mark_task_complete",
        description = "Marks a task as completed in the list.",
        parameters = Schema(
            type = "OBJECT",
            properties = mapOf(
                "id" to Schema(type = "INTEGER", description = "The unique numerical ID of the task to complete")
            ),
            required = listOf("id")
        )
    )

    val allTools = listOf(
        Tool(
            functionDeclarations = listOf(
                addTaskTool,
                updateTaskPriorityTool,
                breakTaskIntoStepsTool,
                sendReminderTool,
                markTaskCompleteTool
            )
        )
    )
}
