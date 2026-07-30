package com.wakemove.android.ui.alarms

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmListGreetingTest {
    @Test
    fun `greeting and subtitle follow the current time period`() {
        val cases = listOf(
            4 to GreetingCopy("晚上好", "为明天准备一个可靠的开始"),
            5 to GreetingCopy("早上好", "让今天从真正醒来开始"),
            10 to GreetingCopy("早上好", "让今天从真正醒来开始"),
            11 to GreetingCopy("中午好", "给午后的安排留一个准时提醒"),
            13 to GreetingCopy("中午好", "给午后的安排留一个准时提醒"),
            14 to GreetingCopy("下午好", "把接下来的计划稳稳叫醒"),
            17 to GreetingCopy("下午好", "把接下来的计划稳稳叫醒"),
            18 to GreetingCopy("晚上好", "为明天准备一个可靠的开始"),
            23 to GreetingCopy("晚上好", "为明天准备一个可靠的开始"),
        )

        cases.forEach { (hour, expected) ->
            assertEquals("hour=$hour", expected, greetingFor(hour))
        }
    }
}
