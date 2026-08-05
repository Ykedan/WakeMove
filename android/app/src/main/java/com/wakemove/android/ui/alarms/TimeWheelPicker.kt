package com.wakemove.android.ui.alarms

import com.wakemove.android.i18n.tr

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wakemove.android.ui.theme.WakeMoveMutedText
import com.wakemove.android.ui.theme.WakeMoveText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    selectedColor: Color = WakeMoveText,
    unselectedColor: Color = WakeMoveMutedText,
) {
    var wheelHour by remember(hour) { mutableIntStateOf(hour) }
    var wheelMinute by remember(minute) { mutableIntStateOf(minute) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_time_wheels"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberWheel(
            values = (0..23).toList(),
            selectedValue = wheelHour,
            label = tr("小时"),
            modifier = Modifier.weight(1f),
            selectedColor = selectedColor,
            unselectedColor = unselectedColor,
            onSelected = { selectedHour ->
                wheelHour = selectedHour
                onTimeChange(wheelHour, wheelMinute)
            },
        )
        Text(
            text = ":",
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Light,
            color = selectedColor,
        )
        NumberWheel(
            values = (0..59).toList(),
            selectedValue = wheelMinute,
            label = tr("分钟"),
            modifier = Modifier.weight(1f),
            selectedColor = selectedColor,
            unselectedColor = unselectedColor,
            onSelected = { selectedMinute ->
                wheelMinute = selectedMinute
                onTimeChange(wheelHour, wheelMinute)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberWheel(
    values: List<Int>,
    selectedValue: Int,
    label: String,
    modifier: Modifier,
    selectedColor: Color,
    unselectedColor: Color,
    onSelected: (Int) -> Unit,
) {
    val itemHeightPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val currentSelectedValue by rememberUpdatedState(selectedValue)
    val currentOnSelected by rememberUpdatedState(onSelected)
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOf(selectedValue).coerceAtLeast(0),
    )
    val flingBehavior = rememberSnapFlingBehavior(state)

    LaunchedEffect(selectedValue) {
        val selectedIndex = values.indexOf(selectedValue)
        if (selectedIndex >= 0 && !state.isScrollInProgress &&
            state.firstVisibleItemIndex != selectedIndex
        ) {
            state.animateScrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(state, values) {
        snapshotFlow {
            Triple(
                state.isScrollInProgress,
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
            )
        }
            .filter { (scrolling) -> !scrolling }
            .map { (_, index, offset) ->
                (index + if (offset >= itemHeightPx / 2) 1 else 0)
                    .coerceIn(values.indices)
            }
            .distinctUntilChanged()
            .collect { index ->
                val value = values[index]
                if (value != currentSelectedValue) currentOnSelected(value)
            }
    }

    Box(
        modifier = modifier
            .height(168.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${label}_wheel"),
            contentPadding = PaddingValues(vertical = 60.dp),
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(values, key = { it }) { value ->
                val isSelected = value == selectedValue
                Text(
                    text = value.toString().padStart(2, '0'),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .clickable(role = Role.Button) { onSelected(value) }
                        .testTag("${label}_${value.toString().padStart(2, '0')}")
                        .semantics { selected = isSelected }
                        .alpha(if (isSelected) 1f else 0.34f),
                    textAlign = TextAlign.Center,
                    style = if (isSelected) {
                        MaterialTheme.typography.displaySmall
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) selectedColor else unselectedColor,
                )
            }
        }
    }
}
