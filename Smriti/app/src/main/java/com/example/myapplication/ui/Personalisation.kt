package com.example.myapplication.ui

import com.example.myapplication.ui.UiText

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.speech.NarrationScreen

val SmritiPurple = Color(0xFF6952C5)
val SmritiInk = Color(0xFF233449)
val SmritiPaper = Color(0xFFFAFCFC)

/** Local preferences are separated by patient; no invented clinical or progress data. */
@Composable
fun PersonalisationGate(patientKey: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(patientKey) { context.getSharedPreferences("personalisation_$patientKey", 0) }
    var done by remember(patientKey) { mutableStateOf(prefs.getBoolean("complete", false)) }
    if (done) content() else PersonalisationScreen(patientKey) { done = true }
}

@Composable
fun Landscape(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFFDDF1EE))) {
        val w = size.width; val h = size.height
        drawCircle(Color(0xFFFFF0BE), h * .23f, Offset(w * .76f, h * .2f))
        fun hill(color: Color, y: Float, amplitude: Float) {
            val p = Path().apply {
                moveTo(0f, h * y)
                cubicTo(w * .2f, h * (y-amplitude), w * .25f, h * (y+amplitude), w * .48f, h*y)
                cubicTo(w * .7f, h * (y-amplitude), w * .8f, h * (y+amplitude), w, h*(y-.1f))
                lineTo(w,h); lineTo(0f,h); close()
            }
            drawPath(p,color)
        }
        hill(Color(0xFF9CCBC6), .42f, .4f)
        hill(Color(0xFF64A89A), .62f, .35f)
        hill(Color(0xFF7DAE68), .82f, .3f)
        drawPath(Path().apply {
            moveTo(w*.6f,h*.57f); cubicTo(w*.27f,h*.8f,w*.9f,h*.75f,w*.55f,h)
            lineTo(w*.23f,h); cubicTo(w*.73f,h*.78f,w*.3f,h*.83f,w*.6f,h*.57f); close()
        }, Color(0xFFBEE6E1))
        for (x in listOf(.09f,.17f,.84f,.93f)) {
            drawLine(Color(0xFF665D3F),Offset(w*x,h*.67f),Offset(w*x,h*.91f),4f)
            drawCircle(Color(0xFF408368),h*.09f,Offset(w*x,h*.68f))
        }
    }
}

@Composable
fun PersonalisationScreen(patientKey: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(patientKey) { context.getSharedPreferences("personalisation_$patientKey",0) }
    var step by rememberSaveable(patientKey) { mutableIntStateOf(0) }
    var region by rememberSaveable(patientKey) { mutableStateOf(prefs.getString("region", "Assam") ?: "Assam") }
    var language by rememberSaveable(patientKey) { mutableStateOf(context.getSharedPreferences("interface_language",0).getString("code","en") ?: "en") }
    var interests by rememberSaveable(patientKey) { mutableStateOf(prefs.getString("interests", "Family & People, Nature & Landscapes") ?: "") }
    val languages = listOf("en" to "English", "as" to "অসমীয়া · Assamese", "bn" to "বাংলা · Bengali", "ne" to "नेपाली · Nepali")
    val regions = listOf("Assam", "Meghalaya", "Manipur", "Mizoram", "Nagaland", "Tripura", "Arunachal Pradesh", "Sikkim")
    val choices = listOf("👪" to "Family & People", "🍲" to "Local Food", "🥁" to "Festivals & Culture", "🌿" to "Nature & Landscapes", "🏡" to "Everyday Objects", "♫" to "Music & Art", "🌱" to "Traditional Work", "•••" to "Others")
    BackHandler(step > 0 || prefs.getBoolean("complete", false)) { if (step > 0) step-- else onDone() }
    val title = listOf("Let's personalise Smriti for you!", "Where are you from?", "Preferred language", "What feels familiar to you?", "You're all set!")[step]
    NarrationScreen("Personalise Smriti. $title. " + when(step) { 1 -> "Choose your region. Selected $region."; 2 -> "Choose English, Assamese, Bengali or Nepali."; 3 -> "Choose topics you enjoy. Selected $interests."; 4 -> "Region $region. Interests $interests. Continue to your home."; else -> "Choose your region, language and interests. Tap Get Started." }, assamese = "স্মৃতি নিজৰ পছন্দ অনুসৰি সাজক। " + when(step) {
 1 -> "আপোনাৰ ৰাজ্য বাছক। বৰ্তমান বাছনি $region।"
 2 -> "আপোনাৰ পছন্দৰ ভাষা বাছক। ইংৰাজী, অসমীয়া, বাংলা অথবা নেপালী।"
 3 -> "আপোনাৰ ভাল লগা বিষয় বাছক। পৰিয়াল, খাদ্য, সংস্কৃতি, প্ৰকৃতি অথবা সংগীত।"
 4 -> "সকলো সাজু হৈছে। ৰাজ্য $region। পছন্দ $interests। আগবাঢ়িবলৈ টিপক।"
 else -> "আপোনাৰ ৰাজ্য, ভাষা আৰু পছন্দ বাছক। আৰম্ভ কৰিবলৈ টিপক।"
})
    Column(Modifier.fillMaxSize().background(SmritiPaper).navigationBarsPadding().padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if(step > 0) TextButton(onClick={step--}) { UiText("‹",fontSize=28.sp) }
            UiText("Personalise Smriti",fontWeight=FontWeight.SemiBold,color=SmritiInk)
        }
        if(step > 0) Row(Modifier.fillMaxWidth().padding(vertical=18.dp),horizontalArrangement=Arrangement.Center) {
            repeat(4) { index -> Box(Modifier.padding(5.dp).size(if(index==step-1) 12.dp else 8.dp).background(if(index<step) SmritiPurple else Color(0xFFDFE5E9),RoundedCornerShape(50))) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(step==0) {
                Landscape(Modifier.fillMaxWidth().height(170.dp))
                UiText("Smriti",fontSize=38.sp,fontWeight=FontWeight.Bold,color=SmritiPurple)
                UiText("Better memories. Brighter days.",color=SmritiInk)
            }
            UiText(title,fontSize=25.sp,fontWeight=FontWeight.Bold,color=SmritiInk)
            UiText(when(step) { 0 -> "A few simple details help make your space feel familiar and meaningful."; 1 -> "Select your state or region."; 2 -> "Choose the language you are most comfortable with."; 3 -> "Select the people, places and topics that bring back good memories."; else -> "Here are your saved preferences for this patient on this device." },color=Color(0xFF66758A),fontSize=15.sp)
            when(step) {
                0 -> listOf("🌿  Your region and familiar surroundings", "Your preferred language", "💜  Your memories and interests").forEach { UiText(it,Modifier.padding(vertical=8.dp),color=SmritiInk) }
                1 -> regions.chunked(2).forEach { pair -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { pair.forEach { name ->
                    Card(onClick={region=name},modifier=Modifier.weight(1f),border=BorderStroke(1.dp,if(region==name) SmritiPurple else Color(0xFFDFE5E9)),colors=CardDefaults.cardColors(containerColor=Color.White)) {
                        Landscape(Modifier.fillMaxWidth().height(65.dp))
                        UiText((if(region==name) "✓ " else "")+name,Modifier.padding(12.dp),fontSize=14.sp,color=SmritiInk)
                    }
                } } }
                2 -> languages.forEach { (code,label) -> ChoiceCard(label,language==code) { language=code; context.getSharedPreferences("interface_language",0).edit().putString("code",code).apply() } }
                3 -> choices.chunked(2).forEach { pair -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { pair.forEach { (icon,label) ->
                    val checked = label in interests.split(", ")
                    Card(onClick={ val selected=interests.split(", ").filter{it.isNotBlank()}.toMutableList(); if(checked)selected.remove(label) else selected.add(label); interests=selected.joinToString(", ") },modifier=Modifier.weight(1f),border=BorderStroke(1.dp,if(checked) SmritiPurple else Color(0xFFDFE5E9)),colors=CardDefaults.cardColors(containerColor=if(checked) Color(0xFFF4F0FF) else Color.White)) {
                        UiText(icon+(if(checked) "  ✓" else ""),Modifier.padding(start=16.dp,top=14.dp),fontSize=26.sp,color=SmritiPurple)
                        UiText(label,Modifier.padding(16.dp),fontSize=13.sp,color=SmritiInk)
                    }
                } } }
                4 -> {
                    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFFCF5)),border=BorderStroke(1.dp,Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            UiText("📍 ${UiCatalog.translate(context, "Region")}\n${UiCatalog.translate(context, region)}",color=SmritiInk)
                            UiText("${UiCatalog.translate(context, "Language")}\n${languages.first{it.first==language}.second}",color=SmritiInk)
                            UiText("♡ ${UiCatalog.translate(context, "Interests")}\n${interests.split(", ").joinToString(", ") { UiCatalog.translate(context,it.ifBlank { "None selected" }) }}",color=SmritiInk)
                        }
                    }
                    Landscape(Modifier.fillMaxWidth().height(110.dp))
                }
            }
        }
        Button(onClick={ if(step<4) step++ else {
            prefs.edit().putString("region",region).putString("interests",interests).putBoolean("complete",true).apply()
            context.getSharedPreferences("interface_language",0).edit().putString("code",language).apply()
            onDone()
        } },modifier=Modifier.fillMaxWidth().padding(top=16.dp).height(52.dp)) { UiText(if(step==0) "Get Started  →" else if(step==4) "Continue  →" else "Next  →") }
    }
}

@Composable
private fun ChoiceCard(label:String, selected:Boolean, onClick:()->Unit) {
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),border=BorderStroke(1.dp,if(selected) SmritiPurple else Color(0xFFDFE5E9)),colors=CardDefaults.cardColors(containerColor=if(selected) Color(0xFFF4F0FF) else Color.White)) {
        Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween) { UiText(label,color=SmritiInk); UiText(if(selected) "◉" else "○",color=SmritiPurple) }
    }
}
