package com.example.myapplication.ui

import com.example.myapplication.ui.UiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.GameSessionRecord

@Composable
fun ProgressOverview(sessions: List<GameSessionRecord>) {
    Card(colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Color(0xFFE1E7EC)),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            UiText("Game performance",fontWeight=FontWeight.SemiBold,color=SmritiInk)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                val score=if(sessions.isEmpty()) 0 else sessions.map{it.score}.average().toInt()
                Box(contentAlignment=Alignment.Center,modifier=Modifier.size(84.dp)) {
                    CircularProgressIndicator(progress={score.coerceIn(0,100)/100f},modifier=Modifier.fillMaxSize(),color=Color(0xFF4D9B7C),trackColor=Color(0xFFEAF3EF),strokeWidth=7.dp)
                    UiText(if(sessions.isEmpty()) "—" else "$score%",fontWeight=FontWeight.Bold,fontSize=21.sp,color=SmritiInk)
                }
                Column { UiText("Average score",color=SmritiInk,fontWeight=FontWeight.SemiBold); UiText("${sessions.size} completed sessions",fontSize=13.sp,color=Color(0xFF66758A)) }
            }
            if(sessions.isEmpty()) UiText("Complete a memory game to see your progress here.",color=Color(0xFF66758A),fontSize=14.sp)
            sessions.groupBy{it.gameType}.forEach { (name, results) ->
                val average=results.map{it.score}.average().toInt().coerceIn(0,100)
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { UiText(name,fontSize=14.sp,color=SmritiInk); UiText("$average%",fontSize=14.sp,color=SmritiPurple) }
                    LinearProgressIndicator(progress={average/100f},modifier=Modifier.fillMaxWidth().height(6.dp),color=SmritiPurple,trackColor=Color(0xFFF0EBFF))
                }
            }
        }
    }
}
