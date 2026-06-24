# Last-Minute Life Saver - Backend Engine 🚀

This is the server-side autonomous brain of the **Last-Minute Life Saver** productivity app. It orchestrates high-stress scheduling decisions using Google's Gemini 3.5 Flash model with server-side tool use (function calling).

## 🌟 Key Features
- **Server-Side API Execution**: Your Gemini API key is safely hidden on the backend and never exposed to client-side Android APKs.
- **Stateless Agent State-Passing**: Receives task state from the client, performs multiple analytical tool calls, updates the task array in-memory, and returns the list of actions taken, reasonings, and the final state.
- **Comprehensive Tools Suite**: Supports `add_task`, `update_task_priority`, `break_task_into_steps`, `send_reminder`, and `mark_task_complete` tool chained chains in a single turn.
- **Robust Deployment**: Ready for one-command Google Cloud Run containerization and scaling.

---

## 🛠️ Local Development

### 1. Installation
Ensure you have Node.js 18+ installed, then navigate to `/backend` and install dependencies:
```bash
npm install
```

### 2. Configure Environment Variables
Create a `.env` file from the example:
```bash
cp .env.example .env
```
Open `.env` and fill in your actual **Gemini API Key**:
```env
PORT=8080
GEMINI_API_KEY=AIzaSy...
```

### 3. Run Server
Start the live development reload server:
```bash
npm run dev
```

---

## 🧪 Testing Endpoints with cURL

### 1. Health Probe
```bash
curl http://localhost:8080/health
```

### 2. Autonomous Task List Analysis
```bash
curl -X POST http://localhost:8080/agent/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "tasks": [
      {
        "id": "1",
        "title": "Review Q1 Financials",
        "description": "Examine overall revenue targets and budget cuts.",
        "deadline": "today 5 PM",
        "priority": "LOW",
        "isCompleted": false,
        "steps": [],
        "reminder": null
      }
    ]
  }'
```

### 3. Re-planning via Chat
```bash
curl -X POST http://localhost:8080/agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "I feel very overwhelmed. Please prioritize my review financials task immediately.",
    "tasks": [
      {
        "id": "1",
        "title": "Review Q1 Financials",
        "description": "Examine overall revenue targets and budget cuts.",
        "deadline": "today 5 PM",
        "priority": "LOW",
        "isCompleted": false,
        "steps": [],
        "reminder": null
      }
    ]
  }'
```

---

## ☁️ Production Deployment (Google Cloud Run)

### Option A: Standard Google Cloud SDK Deployment (Recommended)
Configure your CLI project and deploy with a single CLI command from the `/backend` folder:
```bash
gcloud run deploy last-minute-life-saver-backend \
  --source . \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars="GEMINI_API_KEY=YOUR_ACTUAL_API_KEY"
```

### Option B: Automated Cloud Build Pipeline
If you have Google Cloud Build connected, trigger it using the provided pipeline spec:
```bash
gcloud builds submit --config cloudbuild.yaml
```
