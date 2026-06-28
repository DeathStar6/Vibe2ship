const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
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

/**
 * Ensures a flat, valid Action object that matches the Android Moshi expectation.
 */
function createSafeAction(name, args, classification, resultMessage) {
  return {
    tool: name,
    reason: args.reason || resultMessage || `Agent ${name} action.`,
    confidence: 0.85,
    urgency: classification === "AUTO_SAFE" ? 30 : 80,
    classification: classification,
    arguments: args || {}
  };
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
      },
      {
        name: "delete_task",
        description: "Deletes a task from the list when it is no longer relevant or requested for removal.",
        parameters: {
          type: "OBJECT",
          properties: {
            task_id: { type: "STRING", description: "The unique ID of the task to delete" }
          },
          required: ["task_id"]
        }
      }
    ]
  }
];

// System prompt defining autonomous persona and guidelines
const SYSTEM_PROMPT = `
You are an Advanced Autonomous Productivity Agent designed to analyze, optimize, and assist with a user's task list.

CORE RESPONSIBILITIES:
1. Analyze tasks based on: Deadline proximity, Priority, Task complexity, User productivity optimization.
2. Actions: break_task_into_steps, update_task_priority, mark_task_complete, delete_task, add_reminder.
3. You can chain multiple actions.

STRICT HUMAN-IN-THE-LOOP (HITL) CLASSIFICATION:
- AUTO_SAFE (can be executed immediately):
  • break_task_into_steps
  • add_reminder
- REQUIRES_APPROVAL (MUST NOT be executed automatically):
  • update_task_priority
  • mark_task_complete
  • delete_task

Rules:
- NEVER execute REQUIRES_APPROVAL actions directly. Instead, return them as "suggested_actions".
- EVERY action in "actions_taken" and "suggested_actions" MUST include: confidence (0.0-1.0), urgency (0-100), and classification.
- suggested_actions MUST only contain REQUIRES_APPROVAL actions.
- actions_taken MUST NOT contain REQUIRES_APPROVAL actions.
- If unsure: confidence = 0.7, urgency = 50.

Return ONLY valid JSON.
The "agent_summary" field MUST BE A PLAIN TEXT STRING (max 2 sentences).
ABSOLUTELY NO JSON, NO CURLY BRACES, AND NO QUOTES WITHIN THE agent_summary STRING.
EXAMPLE agent_summary: "I optimized your list by breaking down the project and setting a reminder."
`;

// Helper: Safely compare IDs
const matchTaskId = (task, idToMatch) => {
  return String(task.id) === String(idToMatch);
};

// Helper: Fetch with exponential retry
async function fetchWithRetry(url, options, maxRetries = 3) {
  let delay = 1000; // Start with 1 second
  for (let i = 0; i < maxRetries; i++) {
    try {
      const response = await fetch(url, options);
      if (response.ok) return response;

      // Only retry on specific status codes
      if (![429, 500, 502, 503, 504].includes(response.status)) {
        return response;
      }

      console.warn(`Gemini API transient error (${response.status}). Retrying in ${delay}ms... (Attempt ${i + 1}/${maxRetries})`);
    } catch (err) {
      console.error(`Fetch error: ${err.message}. Retrying in ${delay}ms... (Attempt ${i + 1}/${maxRetries})`);
    }

    await new Promise(resolve => setTimeout(resolve, delay));
    delay *= 2; // Exponential backoff
  }

  // Final attempt
  return fetch(url, options);
}

// Orchestration engine using native fetch
async function runAgentOrchestrator(tasksInput, userMessage) {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new Error("GEMINI_API_KEY is not defined in the server environment variables.");
  }

  let updatedTasks = JSON.parse(JSON.stringify(tasksInput || []));
  const actionsTaken = [];
  const suggestedActions = [];

  const formattedTasks = updatedTasks.length === 0 
    ? "No active tasks." 
    : updatedTasks.map(t => 
        `ID: ${t.id} | Title: ${t.title} | Priority: ${t.priority} | Deadline: ${t.deadline} | Completed: ${t.isCompleted || false} | Description: ${t.description || ""}`
      ).join("\n");

  const promptText = `
Current Time: ${new Date().toISOString()}
Current Active Tasks:
${formattedTasks}

User Input/Trigger:
${userMessage || "Analyze my task list autonomously and perform optimization actions. For each action, determine its Urgency (1-10) and your Confidence (0.0-1.0)."}
`;

  const contents = [{ role: "user", parts: [{ text: promptText }] }];

  let iteration = 0;
  const maxIterations = 5;
  let finalSummary = "";
  let continueLoop = true;

  while (continueLoop && iteration < maxIterations) {
    iteration++;
    const requestBody = {
      contents: contents,
      systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
      tools: tools
    };

    const response = await fetchWithRetry(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(requestBody)
      }
    );

    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      const error = new Error(`Gemini service temporarily unavailable`);
      error.status = response.status;
      error.source = "gemini";
      error.retryable = [429, 500, 502, 503, 504].includes(response.status);
      throw error;
    }

    const resJson = await response.json();
    const assistantContent = resJson.candidates?.[0]?.content;
    if (!assistantContent) break;

    contents.push(assistantContent);
    const parts = assistantContent.parts || [];
    const functionCalls = parts.filter(p => p.functionCall);

    if (functionCalls.length > 0) {
      const responseParts = [];
      for (const call of functionCalls) {
        const { name, args } = call.functionCall;

        // Classification logic
        const classification = ["break_task_into_steps", "add_reminder", "send_reminder", "add_task"].includes(name)
          ? "AUTO_SAFE"
          : "REQUIRES_APPROVAL";

        let resultMessage = "";

        // Tool logic
        if (classification === "AUTO_SAFE") {
          let actionApplied = false;
          try {
            switch (name) {
              case "add_task": {
                const newId = crypto.randomUUID();
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
                resultMessage = `Added task: ${args.title}`;
                actionApplied = true;
                break;
              }
              case "break_task_into_steps": {
                const taskIndex = updatedTasks.findIndex(t => matchTaskId(t, args.task_id));
                if (taskIndex !== -1) {
                  updatedTasks[taskIndex].steps = args.steps || [];
                  resultMessage = `Broke task into steps.`;
                  actionApplied = true;
                }
                break;
              }
              case "add_reminder":
              case "send_reminder": {
                const taskIndex = updatedTasks.findIndex(t => matchTaskId(t, args.task_id));
                if (taskIndex !== -1) {
                  updatedTasks[taskIndex].reminder = args.message;
                  resultMessage = `Set reminder.`;
                  actionApplied = true;
                }
                break;
              }
            }

            if (actionApplied) {
              actionsTaken.push(createSafeAction(name, args, classification, resultMessage));
            }
          } catch (err) {
            resultMessage = `Execution error: ${err.message}`;
          }
        } else {
          // Suggested Action (Requires Approval)
          suggestedActions.push(createSafeAction(name, args, classification));
          resultMessage = `Suggested ${name} for approval.`;
        }

        responseParts.push({ functionResponse: { name, response: { result: resultMessage } } });
      }
      contents.push({ role: "function", parts: responseParts });
    } else {
      let rawSummary = parts.find(p => p.text)?.text || "Analysis complete.";

      // Sanitization: If Gemini ignored the prompt and returned JSON in a string, extract the summary field or strip braces
      try {
        const parsed = JSON.parse(rawSummary);
        finalSummary = parsed.agent_summary || parsed.summary || rawSummary;
      } catch (e) {
        finalSummary = rawSummary.replace(/\{.*\}/g, "").trim();
      }

      continueLoop = false;
    }
  }

  return {
    actions_taken: actionsTaken,
    suggested_actions: suggestedActions,
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
    if (error.source === "gemini") {
      return res.status(error.status || 503).json({
        error: error.message,
        retryable: error.retryable,
        source: "gemini"
      });
    }
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
    if (error.source === "gemini") {
      return res.status(error.status || 503).json({
        error: error.message,
        retryable: error.retryable,
        source: "gemini"
      });
    }
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
