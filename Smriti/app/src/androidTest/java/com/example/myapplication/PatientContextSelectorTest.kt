package com.example.myapplication
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.chat.PatientContextSelector
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PatientContextSelectorTest {
 @Test fun findsLateCaregiverRecordWithoutTruncatingItsNotes() {
  val rows=JSONArray()
  repeat(60) { rows.put(JSONObject().put("name","Relative$it").put("notes","Background ".repeat(30))) }
  rows.put(JSONObject().put("name","Ananya").put("notes","Granddaughter visits every Sunday at 5 PM"))
  val data=JSONObject().put("family",rows).put("patient",JSONObject().put("caregiverPhone","12345"))
  val result=PatientContextSelector.select(data,"When does Ananya visit?",emptyList())
  assertEquals("Ananya",result.getJSONArray("family").getJSONObject(0).getString("name"))
  assertTrue(result.toString().contains("Sunday at 5 PM"))
  assertEquals(61,result.getJSONObject("recordCoverage").getJSONObject("family").getInt("total"))
  assertEquals(61,data.getJSONArray("family").length())
  assertTrue(result.toString().length<9000)
 }
 @Test fun freshSnapshotChangesTaskStatus() {
  fun snapshot(status:String)=JSONObject().put("medicines",JSONArray().put(JSONObject().put("name","TestMed").put("status",status)))
  assertTrue(PatientContextSelector.select(snapshot("due_unchecked"),"TestMed",emptyList()).toString().contains("due_unchecked"))
  assertFalse(PatientContextSelector.select(snapshot("marked_complete"),"TestMed",emptyList()).toString().contains("due_unchecked"))
 }
}
