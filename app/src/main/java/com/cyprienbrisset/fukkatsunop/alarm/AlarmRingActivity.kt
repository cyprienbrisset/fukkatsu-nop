package com.cyprienbrisset.fukkatsunop.alarm

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyprienbrisset.fukkatsunop.data.AppDatabase
import com.cyprienbrisset.fukkatsunop.data.alarm.AlarmRepository
import com.cyprienbrisset.fukkatsunop.ui.sumi.SumiChoiceChip
import com.cyprienbrisset.fukkatsunop.ui.sumi.WatermarkKanji
import com.cyprienbrisset.fukkatsunop.ui.theme.Kinari
import com.cyprienbrisset.fukkatsunop.ui.theme.Mincho
import com.cyprienbrisset.fukkatsunop.ui.theme.MyPortalTheme
import com.cyprienbrisset.fukkatsunop.ui.theme.Shu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)

        setContent {
            var snoozeMinutes by remember { mutableStateOf(10) }
            if (alarmId >= 0) {
                LaunchedEffect(alarmId) {
                    val a = withContext(Dispatchers.IO) {
                        AlarmRepository(AppDatabase.get(this@AlarmRingActivity).alarmDao()).byId(alarmId)
                    }
                    if (a != null) snoozeMinutes = a.snoozeMinutes
                }
            }
            MyPortalTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        WatermarkKanji("鈴", size = 260.sp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RÉVEIL", color = Shu, fontFamily = Mincho, fontSize = 15.sp, letterSpacing = 4.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                fontFamily = Mincho, color = Kinari, fontSize = 78.sp)
                            Spacer(Modifier.height(36.dp))
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                SumiChoiceChip("Snooze $snoozeMinutes", selected = false, onClick = {
                                    if (alarmId >= 0) AlarmForegroundService.snooze(this@AlarmRingActivity, alarmId, snoozeMinutes)
                                    finish()
                                })
                                Box(
                                    Modifier.size(104.dp).clip(CircleShape)
                                        .border(BorderStroke(3.dp, Shu), CircleShape)
                                        .clickable { AlarmForegroundService.stop(this@AlarmRingActivity); finish() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Arrêter", color = Kinari, fontFamily = Mincho, fontSize = 15.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}
