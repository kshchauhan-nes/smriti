package com.example.myapplication
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.chat.HybridAi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class BriefPatientReplyTest {
 @Test fun granddaughterAnswerIsExactAndUsesOnlySavedRecords() = runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val data=JSONObject("""{"replyLanguage":"en","family":[{"name":"Ananya","relationship":"Granddaughter"}]}""")
  val reply=HybridAi.reply(context,"local-patient",data,emptyList(),"Who is my granddaughter?")
  assertTrue(reply.text, reply.text.contains("Ananya") && reply.text.contains("Granddaughter"))
  assertEquals(HybridAi.AiMode.PATIENT_RECORDS,reply.mode)
  data.getJSONArray("family").getJSONObject(0).put("name","Rina")
  val changed = HybridAi.reply(context,"local-patient",data,emptyList(),"Who is my granddaughter?").text
  assertTrue(changed, changed.contains("Rina") && !changed.contains("Ananya"))
 }
}
