package com.wakemove.android.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.ChallengeType
import com.wakemove.android.ui.theme.WakeMoveDawnSoft
import com.wakemove.android.ui.theme.WakeMoveMint
import com.wakemove.android.ui.theme.WakeMoveMist
import com.wakemove.android.ui.theme.WakeOrbitMark
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    events: List<AlarmEvent>,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "WAKE LOG",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "历史记录",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (events.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) { Text("清除历史") }
                }
            }
        }

        if (events.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 54.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WakeOrbitMark(size = 132.dp)
                    Text(
                        text = "还没有响铃记录",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "完成第一次起床挑战后，这里会留下记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(events, key = AlarmEvent::id) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = event.scheduledAt.localFormat(
                                    zoneId,
                                    includeSeconds = false,
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                color = if (event.result == AlarmEventResult.COMPLETED) {
                                    WakeMoveMint.copy(alpha = 0.2f)
                                } else {
                                    WakeMoveDawnSoft
                                },
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = event.result.label(),
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Text(
                            text = "${event.challengeType.label()} · 贪睡 ${event.snoozeCount} 次",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(WakeMoveMist, MaterialTheme.shapes.medium)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                "实际响铃：${
                                    event.startedAt?.localFormat(zoneId, includeSeconds = true)
                                        ?: "未开始"
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "完成时间：${
                                    event.finishedAt?.localFormat(zoneId, includeSeconds = true)
                                        ?: "处理中"
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun java.time.Instant.localFormat(zoneId: ZoneId, includeSeconds: Boolean): String =
    atZone(zoneId).format(
        DateTimeFormatter.ofPattern(if (includeSeconds) "MM-dd HH:mm:ss" else "MM-dd HH:mm"),
    )

private fun AlarmEventResult.label(): String = when (this) {
    AlarmEventResult.COMPLETED -> "挑战完成"
    AlarmEventResult.BYPASSED -> "紧急停止"
    AlarmEventResult.MISSED -> "已错过"
}

private fun ChallengeType.label(): String = when (this) {
    ChallengeType.SQUAT -> "深蹲"
    ChallengeType.JUMPING_JACK -> "开合跳"
    ChallengeType.HANDS_UP -> "双手举起"
    ChallengeType.VOICE_PHRASE -> "语音短句"
}
