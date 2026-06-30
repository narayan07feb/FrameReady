package com.example.samplemetricsonly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frameready.FrameReady
import com.frameready.StartupMetrics

class MetricsOnlyMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MetricsOnlyScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsOnlyScreen() {
    var metrics by remember { mutableStateOf<StartupMetrics?>(null) }

    LaunchedEffect(Unit) {
        FrameReady.metricsFlow.collect { metrics = it }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF80CBC4),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Metrics Only (0 Initializers)", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E1E1E),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF121212)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF004D40).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "FrameReady — Cold Start Tracking Only",
                            color = Color(0xFF80CBC4),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "0 initializers registered. Library overhead = ContentProvider + lifecycle callbacks only.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }

                if (metrics != null) {
                    val m = metrics!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricRow("TTFF", "${m.ttffMs} ms", Color(0xFF80CBC4))
                            MetricRow("Cold Start Rate", "${m.coldStartRate}%", Color.White)
                            MetricRow("Total Launches", "${FrameReady.storage?.getTotalLaunchCount() ?: 0}", Color.White)
                            MetricRow("Cold Launches", "${FrameReady.storage?.getColdLaunchCount() ?: 0}", Color.White)
                            MetricRow("Initializers", "${m.initializerCount}", Color(0xFF80CBC4))
                        }
                    }
                } else {
                    CircularProgressIndicator(color = Color(0xFF80CBC4))
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Text(text = value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
