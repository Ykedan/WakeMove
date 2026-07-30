package com.wakemove.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakemove.android.ui.theme.WakeMoveBlue
import com.wakemove.android.ui.theme.WakeMoveDawn
import com.wakemove.android.ui.theme.WakeMoveMist
import com.wakemove.android.ui.theme.WakeMoveNight
import com.wakemove.android.ui.theme.WakeOrbitMark

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "WAKE / MOVE",
                color = WakeMoveBlue,
                fontSize = 13.sp,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "离线 · 安心",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(WakeMoveNight, WakeMoveBlue),
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                    ),
            )
            WakeOrbitMark(
                size = 214.dp,
                dark = true,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Surface(
                color = WakeMoveDawn,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
            ) {
                Text(
                    text = "该起床了",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    color = WakeMoveNight,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(
            text = "别只是醒来。\n要真正起床。",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 36.sp,
                lineHeight = 43.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "闹钟响起后，用动作或语音完成挑战。没有顺手一划，只有清醒出发。",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnboardingFeature(Icons.Rounded.Alarm, "准时响铃", Modifier.weight(1f))
            OnboardingFeature(
                Icons.AutoMirrored.Rounded.DirectionsRun,
                "动作挑战",
                Modifier.weight(1f),
            )
            OnboardingFeature(Icons.Rounded.MicNone, "离线语音", Modifier.weight(1f))
        }

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp)
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WakeMoveBlue,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = "继续并设置权限",
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            )
        }
        Text(
            text = "相机与麦克风内容只在本机处理",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingFeature(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(WakeMoveMist, MaterialTheme.shapes.medium)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WakeMoveBlue,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
