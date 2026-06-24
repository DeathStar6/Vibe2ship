const express = require('express');
const cors = require('cors');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 8080;

app.use(cors());
app.use(express.json());

// Helper logger to log decisions server-side for auditability
function serverLog(action, details) {
  const timestamp = new Date().toISOString();
  console.log(`[${timestamp}] [AGENT_${action.toUpperCase()}] ${JSON.stringify(details)}`);
}

// Gemini Tools Specification
const tools = [
  {
    functionDeclarations: [
      {
        name: "add_task",
        description: "Adds a new high-priority or urgent task to the user's list when missing or requested.",
        parameters: {
          type: "OBJECT",
          properties: {
            title: { type: "STRING", description: "The title of the task" },
            description: { type: "STRING", description: "A detailed description of what needs to be done" },
            deadline: { type: "STRING", description: "The task deadline, e.g. 'today 5 PM', 'in 2 hours'" },
            priority: { type: "STRING", enum: ["HIGH", "MEDIUM", "LOW"], description: "The priority of the task" }
          },
          required: ["title", "description", "deadline", "priority"]
        }
      },
      {
        name: "update_task_priority",
        description: "Bumps the priority of an existing task to manage workload or handle short deadlines.",
        parameters: {
          type: "OBJECT",
          properties: {
            task_id: { type: "STRING", description: "The unique ID of the task to update" },
            new_priority: { type: "STRING", enum: ["HIGH", "MEDIUM", "LOW"], description: "The new priority level" },
            reason: { type: "STRING", description: "Detailed justification for updating the priority" }
          },
          required: ["task_id", "new_priority", "reason"]
        }
      },
      {
        name: "break_task_into_steps",
        description: "Decomposes a generic, large, or complex task into 2 to 5 actionable sub-steps.",
        parameters: {
          type: "OBJECT",
          properties: {
            task_id: { type: "STRING", description: "The unique ID of the task to break down" },
            steps: {
              type: "ARRAY",
              items: { type: "STRING" },
              description: "A list of 2 to 5 concrete action sub-steps"
            }
          },
          required: ["task_id", "steps"]
        }
      },
      {
        name: "send_reminder",
        description: "Proactively schedules a high-urgency nudge or reminder notification for an active high-priority task.",
        parameters: {
          type: "OBJECT",
          properties: {
            task_id: { type: "STRING", description: "The unique ID of the task" },
            message: { type: "STRING", description: "An action-focused, helpful, or encouraging reminder message" },
            urgency_level: { type: "STRING", enum: ["HIGH", "MEDIUM", "LOW"], description: "The priority of this reminder" }
          },
          required: ["task_id", "message", "urgency_level"]
        }
      },
      {
        name: "mark_task_complete",
        description: "Marks an existing task as completed in the list.",
        parameters: {
          type: "OBJECT",
          properties: {
            task_id: { type: "STRING", description: "The unique ID of the task to complete" }
          },
          required: ["task_id"]
        }
      }
    ]
  }
];

// System prompt defining autonomous persona and guidelines
const SYSTEM_PROMPT = `
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
- Always output your final answer (after all tool calls have completed) as a concise, empathetic, high-level summary of what adjustments you made and why. Keep this summary within 3 sentences.
`;

// Helper: Safely compare IDs that can be either Number or String
const matchTaskId = (task, idToMatch) => {
  return String(task.id) === String(idToMatch);
};

// Orchestration engine using native fetch to keep dependency footprint light & stable
async function runAgentOrchestrator(tasksInput, userMessage) {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new Error("GEMINI_API_KEY is not defined in the server environment variables.");
  }

  // Deep copy tasks input to avoid mutating parameters directly
  let updatedTasks = JSON.parse(JSON.stringify(tasksInput || []));
  const actionsTaken = [];

  const formattedTasks = updatedTasks.length === 0 
    ? "No active tasks." 
    : updatedTasks.map(t => 
        `ID: ${t.id} | Title: ${t.title} | Priority: ${t.priority} | Deadline: ${t.deadline} | Completed: ${t.isCompleted || false} | Description: ${t.description || ""} | Steps: ${(t.steps || []).join(", ")} | Reminder: ${t.reminder || ""}`
      ).join("\n");

  const promptText = `
Current Time: ${new Date().toISOString()}
Current Active Tasks:
${formattedTasks}

User Input/Trigger:
${userMessage || "Analyze my task list autonomously and perform any required actions (prioritize, break down, or send reminders) to optimize my day."}
`;

  const contents = [
    {
      role: "user",
      parts: [{ text: promptText }]
    }
  ];

  let continueLoop = true;
  let iteration = 0;
  const maxIterations = 5;
  let finalSummary = "";

  while (continueLoop && iteration < maxIterations) {
    iteration++;
    serverLog("iteration_start", { iteration, taskCount: updatedTasks.length });

    const requestBody = {
      contents: contents,
      systemInstruction: {
        parts: [{ text: SYSTEM_PROMPT }]
      },
      tools: tools
    };

    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestBody)
      }
    );

    if (!response.ok) {
      const errorMsg = await response.text();
      throw new Error(`Gemini API error (Status ${response.status}): ${errorMsg}`);
    }

    const resJson = await response.json();
    const candidate = resJson.candidates?.[0];
    const assistantContent = candidate?.content;

    if (!assistantContent) {
      break;
    }

    // Append response to conversation context
    contents.push(assistantContent);

    const parts = assistantContent.parts || [];
    const functionCalls = parts.filter(p => p.functionCall);

    if (functionCalls.length > 0) {
      const responseParts = [];

      for (const call of functionCalls) {
        const { name, args } = call.functionCall;
        serverLog("tool_call_received", { name, args });

        let resultMessage = "";

        try {
          switch (name) {
            case "add_task": {
              const newId = updatedTasks.length > 0 ? Math.max(...updatedTasks.map(t => parseInt(t.id) || 0)) + 1 : 1;
              const newTaskObj = {
                id: newId,
                title: args.title || "Unnamed Task",
                description: args.description || "",
                deadline: args.deadline || "today",
                priority: args.priority || "MEDIUM",
                isCompleted: false,
                steps: [],
                reminder: null
              };
              updatedTasks.push(newTaskObj);
              resultMessage = `Successfully added task: ${args.title} with ID ${newId}`;
              
              actionsTaken.push({
                tool: "add_task",
                task_id: newId,
                reason: `Added new task "${args.title}"`
              });
              serverLog("add_task_success", newTaskObj);
              break;
            }

            case "update_task_priority": {
              const taskId = args.task_id;
              const newPriority = args.new_priority;
              const reason = args.reason || "Urgency recalculation";
              
              const taskIndex = updatedTasks.findIndex(t => matchTaskId(t, taskId));
              if (taskIndex !== -1) {
                const oldPriority = updatedTasks[taskIndex].priority;
                updatedTasks[taskIndex].priority = newPriority;
                resultMessage = `Successfully updated task ${taskId} priority to ${newPriority}`;
                
                actionsTaken.push({
                  tool: "update_task_priority",
                  task_id: taskId,
                  reason: `Re-prioritized "${updatedTasks[taskIndex].title}" from ${oldPriority} to ${newPriority}: ${reason}`
                });
                serverLog("update_task_priority_success", { taskId, oldPriority, newPriority, reason });
              } else {
                resultMessage = `Error: Task with ID ${taskId} not found.`;
                serverLog("update_task_priority_error", { taskId, message: "Task not found" });
              }
              break;
            }

            case "break_task_into_steps": {
              const taskId = args.task_id;
              const steps = args.steps || [];
              
              const taskIndex = updatedTasks.findIndex(t => matchTaskId(t, taskId));
              if (taskIndex !== -1) {
                updatedTasks[taskIndex].steps = steps;
                resultMessage = `Successfully added sub-steps to task ${taskId}`;
                
                actionsTaken.push({
                  tool: "break_task_into_steps",
                  task_id: taskId,
                  reason: `Decomposed "${updatedTasks[taskIndex].title}" into steps: ${steps.join(", ")}`
                });
                serverLog("break_task_into_steps_success", { taskId, steps });
              } else {
                resultMessage = `Error: Task with ID ${taskId} not found.`;
                serverLog("break_task_into_steps_error", { taskId, message: "Task not found" });
              }
              break;
            }

            case "send_reminder": {
              const taskId = args.task_id;
              const message = args.message;
              
              const taskIndex = updatedTasks.findIndex(t => matchTaskId(t, taskId));
              if (taskIndex !== -1) {
                updatedTasks[taskIndex].reminder = message;
                resultMessage = `Successfully applied proactive reminder for task ${taskId}`;
                
                actionsTaken.push({
                  tool: "send_reminder",
                  task_id: taskId,
                  reason: `Scheduled notification for "${updatedTasks[taskIndex].title}": ${message}`
                });
                serverLog("send_reminder_success", { taskId, message });
              } else {
                resultMessage = `Error: Task with ID ${taskId} not found.`;
                serverLog("send_reminder_error", { taskId, message: "Task not found" });
              }
              break;
            }

            case "mark_task_complete": {
              const taskId = args.task_id;
              const taskIndex = updatedTasks.findIndex(t => matchTaskId(t, taskId));
              if (taskIndex !== -1) {
                updatedTasks[taskIndex].isCompleted = true;
                resultMessage = `Successfully completed task ${taskId}`;
                
                actionsTaken.push({
                  tool: "mark_task_complete",
                  task_id: taskId,
                  reason: `Completed task "${updatedTasks[taskIndex].title}"`
                });
                serverLog("mark_task_complete_success", { taskId });
              } else {
                resultMessage = `Error: Task with ID ${taskId} not found.`;
                serverLog("mark_task_complete_error", { taskId, message: "Task not found" });
              }
              break;
            }

            default:
              resultMessage = `Unsupported action: ${name}`;
              serverLog("unsupported_tool", { name });
          }
        } catch (err) {
          resultMessage = `Execution error: ${err.message}`;
          serverLog("tool_execution_exception", { name, error: err.message });
        }

        responseParts.push({
          functionResponse: {
            name: name,
            response: { result: resultMessage }
          }
        });
      }

      // Feed function results back to Gemini
      contents.push({
        role: "function",
        parts: responseParts
      });

    } else {
      // No tool calls returned; retrieve final natural language message
      finalSummary = parts.find(p => p.text)?.text || "Completed all task management optimization actions.";
      continueLoop = false;
      serverLog("final_summary", { finalSummary });
    }
  }

  return {
    actions_taken: actionsTaken,
    agent_summary: finalSummary,
    updated_tasks: updatedTasks
  };
}

// REST Endpoints

// 1. GET /health - Cloud Run healthiness probe
app.get('/health', (req, res) => {
  res.status(200).json({ status: "healthy", timestamp: new Date().toISOString() });
});

// 2. POST /agent/analyze - Trigger autonomous evaluation
app.post('/agent/analyze', async (req, res) => {
  try {
    const { tasks } = req.body;
    serverLog("analyze_request_received", { taskCount: tasks?.length || 0 });
    const result = await runAgentOrchestrator(tasks, null);
    res.status(200).json(result);
  } catch (error) {
    console.error("Analysis route error:", error);
    res.status(500).json({ error: error.message || "Internal server error" });
  }
});

// 3. POST /agent/chat - Target specific user requests
app.post('/agent/chat', async (req, res) => {
  try {
    const { message, tasks } = req.body;
    if (!message) {
      return res.status(400).json({ error: "Missing 'message' field in chat request." });
    }
    serverLog("chat_request_received", { message, taskCount: tasks?.length || 0 });
    const result = await runAgentOrchestrator(tasks, message);
    res.status(200).json(result);
  } catch (error) {
    console.error("Chat route error:", error);
    res.status(500).json({ error: error.message || "Internal server error" });
  }
});

// Catch-all 404
app.use((req, res) => {
  res.status(404).json({ error: "Endpoint not found." });
});

// Start listening
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Last-Minute Life Saver server is running on port ${PORT}`);
});
