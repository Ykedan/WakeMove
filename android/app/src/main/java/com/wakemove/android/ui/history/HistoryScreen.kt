package com.wakemove.android.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.domain.AlarmEvent
import com.wakemove.android.domain.AlarmEventResult
import com.wakemove.android.domain.ChallengeType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    events: List<AlarmEvent>,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("历史记录", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (events.isNotEmpty()) {
                TextButton(onClick = onClearHistory) { Text("清除历史") }
            }
        }
        if (events.isEmpty()) {
            Text("还没有响铃记录", modifier = Modifier.padding(top = 28.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(events, key = AlarmEvent::id) { event ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text(event.result.label(), fontWeight = FontWeight.Bold)
                            Text(
                                "计划时间：${
                                    event.scheduledAt.localFormat(zoneId, includeSeconds = false)
                                }",
                            )
                            Text(
                                "实际响铃：${
                                    event.startedAt?.localFormat(zoneId, includeSeconds = true)
                                        ?: "未开始"
                                }",
                            )
                            Text(
                                "完成时间：${
                                    event.finishedAt?.localFormat(zoneId, includeSeconds = true)
                                        ?: "处理中"
                                }",
                            )
                            Text(
                                "${event.challengeType.label()} · 贪睡 ${event.snoozeCount} 次",
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
