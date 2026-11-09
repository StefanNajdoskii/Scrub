package com.scrub.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scrub.app.ScrubViewModel
import java.text.NumberFormat

@Composable
fun ScanScreen(vm: ScrubViewModel) {
    val c = ScrubTheme.colors

    // Keep the screen awake while scanning
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val p = vm.progress
    val animated by animateFloatAsState(p.fraction, label = "progress")
    val fmt = NumberFormat.getIntegerInstance()
    val foundList = vm.matches.filter { it.score >= vm.strictness }
    val found = foundList.size

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Gutter),
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            "Scanning your photos",
            style = ScrubType.ScreenTitle,
            color = c.graphite,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Everything happens on this phone. You can keep it open or put it down.",
            style = ScrubType.Body,
            color = c.pencil,
        )

        Spacer(Modifier.height(40.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text("${(animated * 100).toInt()}%", style = ScrubType.ScreenTitle, color = c.graphite)
            Spacer(Modifier.weight(1f))
            Text(
                if (p.total == 0) "Getting ready" else "${fmt.format(p.done)} of ${fmt.format(p.total)} photos",
                style = ScrubType.Small,
                color = c.pencil,
            )
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = c.graphite,
            trackColor = c.smudge,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )

        Spacer(Modifier.height(40.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))

        Text(
            when (found) {
                0 -> "No matches yet"
                1 -> "1 possible match so far"
                else -> "$found possible matches so far"
            },
            style = ScrubType.SectionTitle,
            color = c.graphite,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.height(64.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(foundList.takeLast(20).reversed(), key = { it.image.id }) {
                    PhotoThumb(it.image.uri, Modifier.size(64.dp))
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "The first scan takes a while. Next time Scrub remembers the photos it has already checked.",
            style = ScrubType.Small,
            color = c.pencil,
        )
        Spacer(Modifier.height(16.dp))
        SecondaryButton(
            if (found > 0) "Stop and review" else "Stop",
            onClick = { if (found > 0) vm.stopScan() else vm.goHome() },
        )
        Spacer(Modifier.height(24.dp))
    }
}
