package com.focusflow.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class TodayFocusWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayFocusGlanceWidget
}

object TodayFocusGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val minutes = (context.applicationContext as FocusFlowApplication).todayFocusMinutes()
        provideContent { TodayFocusContent(minutes) }
    }
}

@Composable
private fun TodayFocusContent(minutes: Long) {
    Box(
        GlanceModifier.cornerRadius(20.dp).background(Color(0xFF686DFA)).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                if (minutes > 0) "今日专注 $minutes 分钟" else "今天，先专注一件事",
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.padding(bottom = 2.dp),
            )
            Text("点击打开 FocusFlow", style = TextStyle(color = ColorProvider(Color(0xFFD9DBFF)), fontSize = 11.sp))
        }
    }
}
