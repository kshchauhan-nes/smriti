package com.example.myapplication
import com.example.myapplication.chat.BriefReply
import org.junit.Assert.*
import org.junit.Test
class BriefReplyTest {
 @Test fun conversationalReplyKeepsTwoSentences() { assertEquals("Bihu is celebrated in Assam. Pitha is a festive food.", BriefReply.concise("Bihu is celebrated in Assam. Pitha is a festive food. An unrelated third sentence.")) }
 @Test fun hidesIncompleteThinking() { assertEquals("", BriefReply.visible("<think>private reasoning")) }
 @Test fun preservesDecimalsAndNames() { assertEquals("Your caregiver is Dr. Das.", BriefReply.shorten("Your caregiver is Dr. Das. More text.")); assertNull(BriefReply.end("Recorded dose is 2.")); assertEquals("Recorded dose is 2.5 mg.",BriefReply.shorten("Recorded dose is 2.5 mg. More text.")) }
 @Test fun capsLongOutputAndKeepsFirstSentence() { assertTrue(BriefReply.shorten("word ".repeat(100)).length<=240); assertEquals("Your granddaughter is Ananya.",BriefReply.shorten("Your granddaughter is Ananya. Hello there.")) }
}
