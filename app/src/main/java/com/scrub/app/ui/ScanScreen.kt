package com.scrub.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrub.app.ScrubViewModel
import java.text.NumberFormat

@Composable
fun ScanScreen(vm: ScrubViewModel) {
    // Keep the screen awake while scanning
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val found = vm.matches.filter { it.score >= vm.strictness }
    ScanContent(
        checked = vm.progress.done,
        total = vm.progress.total,
        found = found.size,
        recent = found.takeLast(20).reversed().map { it.image.id to it.image.uri },
        onStop = { if (found.isNotEmpty()) vm.stopScan() else vm.goHome() },
    )
}

/** Stateless scan screen. [recent] is (id, uri) of the newest matches, newest first. */
@Composable
private fun ScanContent(
    checked: Int,
    total: Int,
    found: Int,
    recent: List<Pair<Long, Uri>>,
    onStop: () -> Unit,
) {
    val c = ScrubTheme.colors
    val fmt = NumberFormat.getIntegerInstance()

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

        // Photos checked: counts up digit by digit
        RollingNumber(checked, ScrubType.ScreenTitle.copy(fontSize = 40.sp, lineHeight = 48.sp), c.graphite)
        Text(
            if (total == 0) "Getting ready" else "of ${fmt.format(total)} photos",
            style = ScrubType.Body,
            color = c.pencil,
        )
        Spacer(Modifier.height(16.dp))
        EraserProgress(if (total == 0) 0f else checked.toFloat() / total)

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
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.height(64.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recent, key = { it.first }) { (_, uri) ->
                    PhotoThumb(uri, Modifier.size(64.dp))
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
        SecondaryButton(if (found > 0) "Stop and review" else "Stop", onClick = onStop)
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@ScreenPreviews
@Composable
private fun Scan0Preview() = PreviewFrame {
    ScanContent(checked = 0, total = 4812, found = 0, recent = emptyList(), onStop = {})
}

@ScreenPreviews
@Composable
private fun Scan40Preview() = PreviewFrame {
    ScanContent(
        checked = 1925, total = 4812, found = 3,
        recent = (1L..3L).map { it to Uri.EMPTY }, onStop = {},
    )
}

@ScreenPreviews
@Composable
private fun Scan100Preview() = PreviewFrame {
    ScanContent(
        checked = 4812, total = 4812, found = 12,
        recent = (1L..6L).map { it to Uri.EMPTY }, onStop = {},
    )
}
