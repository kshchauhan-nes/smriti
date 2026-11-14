package com.example.myapplication.ui

import com.example.myapplication.ui.UiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

@Composable
fun PersonalisedHome(patientKey:String, name:String, medicines:String, hydration:String, routine:String,
    onGames:()->Unit,onMemories:()->Unit,onMedicine:()->Unit,onHydration:()->Unit,onRoutine:()->Unit,onChat:()->Unit,onCaregiver:()->Unit) {
    val context=LocalContext.current
    val prefs=remember(patientKey){context.getSharedPreferences("personalisation_$patientKey",0)}
    var editing by remember{mutableStateOf(false)}
    if(editing) { PersonalisationScreen(patientKey){editing=false}; return }
    val region=prefs.getString("region","Assam") ?: "Assam"
    Column(Modifier.fillMaxSize().background(SmritiPaper).navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(shape=CircleShape,color=Color(0xFFE6F2E8),modifier=Modifier.size(48.dp)) { Box(contentAlignment=Alignment.Center){UiText(name.take(1).uppercase().ifBlank{"S"},fontSize=24.sp,color=SmritiPurple)} }
                Column(Modifier.weight(1f)) {
                    UiText(stringResource(R.string.language_greeting,name.ifBlank{stringResource(R.string.language_friend)}),fontWeight=FontWeight.Bold,fontSize=20.sp,color=SmritiInk)
                    UiText("Your Smriti is here for you ♡",fontSize=12.sp,color=Color(0xFF66758A))
                }
                TextButton(onClick={editing=true}) { UiText("⚙",fontSize=25.sp) }
            }
            Surface(shape=RoundedCornerShape(20.dp),color=Color(0xFFEDF5E9)) { UiText("◉ ${UiCatalog.translate(context, region)} · ${context.resources.configuration.locales[0].getDisplayLanguage(context.resources.configuration.locales[0])}",Modifier.padding(horizontal=12.dp,vertical=5.dp),fontSize=12.sp,color=Color(0xFF416C50)) }
            Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Color(0xFFE1E7E7))) {
                Box {
                    Landscape(Modifier.fillMaxWidth().height(155.dp))
                    Column(Modifier.padding(16.dp)) {
                        Surface(color=Color(0xEEFFFCF0),shape=RoundedCornerShape(10.dp)) { UiText("Today's highlight",Modifier.padding(8.dp),fontSize=12.sp,fontWeight=FontWeight.Bold,color=SmritiInk) }
                        Spacer(Modifier.height(42.dp))
                        Surface(color=Color(0xEEFFFCF0),shape=RoundedCornerShape(10.dp)) { UiText("A little care. A brighter day.",Modifier.padding(8.dp),fontWeight=FontWeight.Bold,color=SmritiInk) }
                    }
                }
                TextButton(onClick=onChat,modifier=Modifier.fillMaxWidth()) { UiText("Take a moment to talk with Smriti  →",color=SmritiPurple) }
            }
            UiText("What would you like to do?",fontSize=18.sp,fontWeight=FontWeight.Bold,color=SmritiInk)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                HomeTile("🧠",stringResource(R.string.lang_7),"For a sharper mind",Color(0xFFFFF5E9),Modifier.weight(1f),onGames)
                HomeTile("👪",stringResource(R.string.lang_4),stringResource(R.string.lang_5),Color(0xFFF3EEFF),Modifier.weight(1f),onMemories)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                HomeTile("💊",stringResource(R.string.lang_2),medicines,Color(0xFFEAF8EE),Modifier.weight(1f),onMedicine)
                HomeTile("💧",stringResource(R.string.lang_3),hydration,Color(0xFFEAF6FF),Modifier.weight(1f),onHydration)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                HomeTile("📅",stringResource(R.string.lang_6),routine,Color(0xFFFFF7E9),Modifier.weight(1f),onRoutine)
                HomeTile("♡",stringResource(R.string.lang_8),"A familiar companion",Color(0xFFF3EEFF),Modifier.weight(1f),onChat)
            }
            Spacer(Modifier.height(8.dp))
        }
        Surface(color=Color.White,shadowElevation=3.dp) {
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                TextButton(onClick={}) { UiText("⌂ Home",color=SmritiPurple) }
                TextButton(onClick=onCaregiver) { UiText("♡ Caregiver",color=SmritiInk) }
                TextButton(onClick={editing=true}) { UiText("☷ Personalise",color=SmritiInk) }
            }
        }
    }
}

@Composable
private fun HomeTile(icon:String,title:String,subtitle:String,tint:Color,modifier:Modifier,onClick:()->Unit) {
    Card(onClick=onClick,modifier=modifier.heightIn(min=120.dp),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,tint.copy(red=(tint.red*.9f),green=(tint.green*.9f),blue=(tint.blue*.95f))),colors=CardDefaults.cardColors(containerColor=tint)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            UiText(icon,fontSize=27.sp,color=SmritiPurple)
            UiText(title,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=SmritiInk)
            UiText(subtitle,fontSize=12.sp,color=Color(0xFF657488))
        }
    }
}
