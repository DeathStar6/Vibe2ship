package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.example.worker.AgentAnalysisWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Schedule the periodic background AI analysis
    schedulePeriodicAgentAnalysis()

    setContent {
      MyApplicationTheme {
        val viewModel: MainViewModel = viewModel()
        DashboardScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
      }
    }
  }

  private fun schedulePeriodicAgentAnalysis() {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    val periodicWorkRequest = PeriodicWorkRequestBuilder<AgentAnalysisWorker>(
        3, TimeUnit.HOURS
    )
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
        "AgentAnalysisWork",
        ExistingPeriodicWorkPolicy.KEEP,
        periodicWorkRequest
    )
  }
}
