package com.example.myapplication.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Answers factual patient-record questions directly from stored data.
 * These replies do not require the language model.
 */
object PatientRecordAnswers {

    fun answer(
        snapshot: JSONObject,
        input: String,
        history: List<com.example.myapplication.data.ChatMessage> = emptyList()
    ): String? {

        if (
            snapshot
                .optString("replyLanguage", "English")
                .lowercase() !in listOf("en", "english")
        ) {
            return null
        }

        val q = input
            .lowercase(java.util.Locale.ROOT)
            .replace('’', '\'')

        val simple = q
            .trim()
            .trimEnd('?', '.', '!')

        fun rows(key: String): List<JSONObject> {
            val array =
                snapshot.optJSONArray(key)
                    ?: JSONArray()

            return (0 until array.length())
                .mapNotNull {
                    array.optJSONObject(it)
                }
        }

        val medicationQuery = Regex("\\b(medicine|medicines|medication|medications|meds|dose|dosage|pill|pills)\\b").containsMatchIn(q)
        if (medicationQuery && Regex("\\b(should|side effect|stop|change|extra|double|overdose|what do i do|what to do)\\b").containsMatchIn(q)) {
            return "Please check with your caregiver or clinician about medicine decisions. I cannot advise changing a dose; an unchecked record does not mean you should take another dose."
        }
        if (OfflineConversationSupport.companionship(input, history)) return null
        if (simple in setOf("what time is it", "what is the date", "what day is it", "what is today's date")) {
            return snapshot.optString("localTime").takeIf { it.isNotBlank() }?.let { "Your device's local date and time: $it." }
                ?: "The local time is not available in this record."
        }
        if (Regex("\\b(reminder|reminders|notifications)\\b").containsMatchIn(q)) {
            val reminders = snapshot.optJSONObject("reminders") ?: return "Reminder settings are not available in this record."
            return "Care reminders are configured for ${reminders.optInt("leadMinutes")} minutes before, at the scheduled time, and ${reminders.optInt("uncheckedDelayMinutes")} minutes after if still unchecked." +
                if (!reminders.optBoolean("notificationsAllowed")) " Notifications are currently blocked in device settings."
                else if (!reminders.optBoolean("exactAlarmsAllowed")) " Exact alarm permission is currently unavailable, so timing may vary."
                else " Device notification and exact alarm permissions are enabled."
        }
        val profile = snapshot.optJSONObject("patient") ?: JSONObject()
        val profileField = when (simple) {
            "what is my name", "who am i" -> "name"
            "where do i live", "what is my location" -> "location"
            "what is my language", "what is my preferred language" -> "preferredLanguage"
            else -> null
        }
        if (profileField != null) return profile.optString(profileField).takeIf { it.isNotBlank() }
            ?.let { "Your saved ${if (profileField == "preferredLanguage") "preferred language" else profileField} is $it." } ?: "That detail has not been added yet."
        if (simple in setOf("where am i from", "what is my region")) return snapshot.optString("region").takeIf { it.isNotBlank() }
            ?.let { "Your selected region is $it." } ?: "Your region has not been selected yet."
        val preference = Regex("^do i (?:like|enjoy|love) (.+)$").matchEntire(simple)?.groupValues?.get(1)
        if (preference != null) {
            fun topics(key: String): List<String> {
                val values = snapshot.optJSONArray(key) ?: JSONArray()
                return (0 until values.length()).map { values.optString(it) }
            }
            val liked = topics("likedTopics") + snapshot.optString("selectedInterests").split(',')
            return when {
                topics("avoidedTopics").any { normalizeIdentity(it) == normalizeIdentity(preference) } -> "Your saved preferences list $preference as a topic to avoid."
                liked.any { normalizeIdentity(it) == normalizeIdentity(preference) } -> "Your saved interests include $preference."
                else -> "A preference about $preference has not been recorded yet."
            }
        }
        val interestQuestion = simple in setOf("my interests", "my hobbies", "my liked topics", "my likes", "what do i like", "what are my interests", "what are my liked topics") ||
            Regex("^(?:what|which|tell me|show me|list).*(?:my (?:interests|hobbies|liked topics)|(?:do i|i) (?:like|enjoy))").containsMatchIn(simple)
        if (interestQuestion) {
            val likes = snapshot.optJSONArray("likedTopics") ?: JSONArray()
            val topics = (0 until likes.length()).map { likes.optString(it) }.filter { it.isNotBlank() }
            val interests = snapshot.optString("selectedInterests").trim()
            return if (topics.isEmpty() && interests.isBlank()) "Your interests have not been added yet."
                else "Your saved interests and liked topics: ${(topics + listOf(interests)).filter { it.isNotBlank() }.joinToString(", ")}."
        }
        // ----------------------------------
        // FAMILY
        // ----------------------------------

        val family = rows("family")

        if (simple in setOf("family", "my family", "show my family", "who is in my family", "who are in my family")) {
            return if (family.isEmpty()) "No family members have been added yet."
            else family.joinToString("\n") { describePerson(it) }
        }
        val explicit = Regex("^(?:who is|who's|who are|tell me about|tell me abt|what do you know about|what is the name of|what's the name of) (.+)$").matchEntire(simple)
        val subject = explicit?.groupValues?.get(1) ?: simple
        val possessive = subject.startsWith("my ")
        val target = normalizeIdentity(subject.removePrefix("my "))
        val matches = family.filter {
            target.isNotBlank() && (normalizeIdentity(it.optString("relationship")) == target ||
                (!possessive && normalizeIdentity(it.optString("name")) == target))
        }
        if (matches.isNotEmpty()) return matches.joinToString("\n") { describePerson(it) }
        // A unique one-edit relationship match tolerates typing mistakes without guessing names.
        val closeRelations = family.map { normalizeIdentity(it.optString("relationship")) }.distinct()
            .filter { target.length >= 4 && oneEditApart(target, it) }
        if (closeRelations.size == 1) return family.filter { normalizeIdentity(it.optString("relationship")) == closeRelations.single() }
            .joinToString("\n") { describePerson(it) }
        if (closeRelations.size > 1) return "I found more than one similar saved relationship. Please use the saved name or relationship."

        // Only an explicit "who" query establishes that an unknown subject is a person.
        // Unknown phrases (including "my ...") must not automatically become family lookups.
        if (explicit != null && simple.startsWith("who ")) {
            return "${subject.replaceFirstChar { it.uppercase() }} has not been added to your family records yet."
        }

        val pronounFollowUp = Regex("\\b(she|he|her|him|they|their)\\b").containsMatchIn(q)
        val identityText = q + if (pronounFollowUp) " " + history.takeLast(4).joinToString(" ") { it.text } else ""
        val mentionedPeople = family.filter { mentionsIdentity(identityText, it.optString("name")) || mentionsIdentity(identityText, it.optString("relationship")) }
        if (mentionedPeople.isNotEmpty() && Regex("^(?:who|what|when|where|how|did|does|has|have|tell me about)\\b").containsMatchIn(simple)) {
            if (Regex("\\b(game|games|score|scores|level)\\b").containsMatchIn(q))
                return "The saved game results belong to you. No separate game results for family members are recorded."
            // Repeat the actual note instead of inferring visits, memories or family relationships from it.
            return mentionedPeople.joinToString("\n") { describePerson(it) } +
                if (Regex("\\b(did|has|have)\\b.*\\b(visit|come|arrive)\\w*\\b").containsMatchIn(q))
                    " These are caregiver notes; a planned visit does not confirm that a particular visit happened." else ""
        }

        // ----------------------------------
        // HYDRATION
        // ----------------------------------

        val hydrationQuestion =
            Regex(
                "\\b(water|hydration|glasses)\\b"
            ).containsMatchIn(q)

        if (hydrationQuestion && (simple.startsWith("how ") || simple.contains("my ") || simple.contains("have i") || simple in setOf("water", "hydration"))) {

            val hydration =
                snapshot.optJSONObject("hydration")
                    ?: return "No hydration record is available today."

            val recorded =
                hydration.optInt(
                    "recordedGlasses",
                    0
                )

            val target =
                hydration.optInt(
                    "targetGlasses",
                    0
                )

            return if (target > 0) {

                "You have recorded $recorded glasses of water today. Your recorded target is $target glasses."

            } else {

                "You have recorded $recorded glasses of water today."
            }
        }

        // ----------------------------------
        // GAME PERFORMANCE
        // ----------------------------------

        val gameQuestion =
            Regex(
                "\\b(game|games|score|scores|memory game|performance|level)\\b"
            ).containsMatchIn(q)

        if (gameQuestion) {
            val mentionedFamily = family.filter { mentionsIdentity(q, it.optString("name")) || mentionsIdentity(q, it.optString("relationship")) }
            if (mentionedFamily.isNotEmpty() || Regex("\\b(their|her|his)\\b").containsMatchIn(q))
                return "The saved game results belong to you. No separate game results for family members are recorded."


            val games =
                rows("recentGames").sortedByDescending { it.optLong("timestamp") }

            if (games.isEmpty()) {
                return "No recent cognitive game sessions are recorded."
            }

            val scores =
                games.mapNotNull {

                    if (!it.has("score")) {
                        null
                    } else {
                        it.optDouble(
                            "score",
                            Double.NaN
                        ).takeIf { value ->
                            !value.isNaN()
                        }
                    }
                }

            val latest =
                games.firstOrNull()

            val latestScore =
                latest?.optInt(
                    "score",
                    -1
                ) ?: -1

            val latestLevel =
                latest?.optInt(
                    "level",
                    -1
                ) ?: -1

            val average =
                if (scores.isNotEmpty()) {
                    scores.average().toInt()
                } else {
                    -1
                }

            return buildString {

                append(
                    "You have ${games.size} recorded game session"
                )

                if (games.size != 1) {
                    append("s")
                }

                append(".")

                if (latestScore >= 0) {
                    append(
                        " Your latest recorded score is $latestScore%."
                    )
                }

                if (latestLevel >= 0) {
                    append(
                        " The latest recorded level is $latestLevel."
                    )
                }

                if (average >= 0) {
                    append(
                        " Your average recorded score is $average%."
                    )
                }

                if (Regex("\\b(response|speed|fast|reaction)\\b").containsMatchIn(q)) {
                    val times = games.map { it.optLong("responseMs", -1) }.filter { it > 0 }
                    if (times.isNotEmpty()) append(" Average recorded session response time: ${times.average().toLong()} milliseconds.")
                    else append(" Response times have not been recorded.")
                }
                if (scores.isNotEmpty()) append(" Your best recorded score is ${scores.maxOrNull()!!.toInt()}%.")
                if (scores.size >= 4) append(" " + PatientContextSelector.gameTrendText(PatientContextSelector.gameTrend(scores)))
                append(
                    " These are gameplay results, not a medical assessment."
                )
            }
        }

        // ----------------------------------
        // MEDICINES + ROUTINES
        // ----------------------------------

        val namedMedicine = rows("medicines").any { it.optString("name").isNotBlank() && q.contains(it.optString("name").lowercase()) }
        val namedRoutine = rows("routines").any { it.optString("title").isNotBlank() && q.contains(it.optString("title").lowercase()) }
        val medicine = namedMedicine ||
            Regex(
                "\\b(medicine|medicines|medication|medications|meds|pill|pills)\\b"
            ).containsMatchIn(q)

        val routine = namedRoutine ||
            Regex(
                "\\b(task|tasks|routine|routines|activities|activity)\\b"
            ).containsMatchIn(q)

        val general =
            Regex(
                "what (?:have i |did i |is |are |do i have |have i got )?" +
                        "(?:done|completed|left|next|upcoming)|" +
                        "what's (?:left|next)|" +
                        "what have i (?:not done|missed)"
            ).containsMatchIn(q)

        if (
            !medicine &&
            !routine &&
            !general
        ) {
            return null
        }

        if (Regex("\\b(yesterday|tomorrow|last week)\\b").containsMatchIn(q)) return "I can show today's care records here. Please check the activity history with your caregiver for other dates."

        if (
            (q.contains("tell me about") && !namedMedicine && !namedRoutine) ||
            q.contains("what is a ")
        ) {
            return null
        }

        val keys =
            when {
                general || (medicine && routine) ->
                    listOf(
                        "medicines",
                        "routines"
                    )

                medicine ->
                    listOf("medicines")

                else ->
                    listOf("routines")
            }

        val pending =
            Regex(
                "not (?:done|complete|completed|taken)|" +
                        "haven't|pending|remaining|left|incomplete"
            ).containsMatchIn(q)

        val missed =
            Regex(
                "\\b(missed|overdue)\\b"
            ).containsMatchIn(q)

        val due = Regex("\\bdue\\b").containsMatchIn(q)

        val upcoming =
            Regex(
                "\\b(upcoming|next|later)\\b"
            ).containsMatchIn(q)

        val done =
            !pending &&
                    Regex(
                        "\\b(done|completed|taken|finished)\\b"
                    ).containsMatchIn(q)

        val heading =
            when {
                missed ->
                    "Overdue and not marked complete"

                upcoming ->
                    "Upcoming"

                pending ->
                    "Not marked complete"

                done ->
                    "Marked complete today"

                else ->
                    "Today's records"
            }

        val selected =
            keys.flatMap { key ->

                rows(key)
                    .filter { row ->
                        val names = rows(key).map { it.optString("name", it.optString("title")).lowercase() }.filter { it.isNotBlank() }
                        val mentioned = names.filter { q.contains(it) }
                        mentioned.isEmpty() || row.optString("name", row.optString("title")).lowercase() in mentioned
                    }
                    .map {
                        key to it
                    }

            }.filter { (_, row) ->

                val status =
                    row.optString("status")

                when {
                    missed ->
                        status == "overdue_unchecked"

                    upcoming ->
                        status in listOf(
                            "upcoming_within_15_minutes",
                            "scheduled_later"
                        )

                    due -> status in listOf("due_unchecked", "overdue_unchecked", "upcoming_within_15_minutes")

                    pending ->
                        status != "marked_complete"

                    done ->
                        status == "marked_complete"

                    else ->
                        true
                }
            }

        if (selected.isEmpty()) {

            return if (done) {
                "No ${heading.lowercase()} items are recorded."
            } else {
                "No ${heading.lowercase()} items are recorded for today."
            }
        }

        val lines =
            selected.take(8)
                .joinToString("; ") { (key, row) ->

                    val name =
                        if (key == "medicines") {
                            row.optString("name")
                        } else {
                            row.optString("title")
                        }

                    val status =
                        when (
                            row.optString("status")
                        ) {
                            "marked_complete" ->
                                "marked complete"

                            "overdue_unchecked" ->
                                "overdue, not marked complete"

                            "due_unchecked" ->
                                "due, not marked complete"

                            "upcoming_within_15_minutes" ->
                                "within 15 minutes"

                            "scheduled_later" ->
                                "scheduled later"

                            else ->
                                "schedule needs checking"
                        }

                    val dosage = if (key == "medicines") row.optString("prescribedDosage").takeIf { it.isNotBlank() }?.let { " (recorded dose: $it)" }.orEmpty() else ""
                    val instructions = if (key == "medicines") row.optString("instructions").takeIf { it.isNotBlank() }?.let { "; saved instructions: $it" }.orEmpty() else ""
                    "$name$dosage at ${
                        row.optString(
                            "time",
                            "time not recorded"
                        )
                    } ($status)$instructions"
                }

        return "$heading: $lines." +
                if (selected.size > 8) {
                    " Showing 8 of ${selected.size}; ask about a name for more."
                } else {
                    ""
                }
    }
    private fun oneEditApart(a: String, b: String): Boolean {
        if (a == b || kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length == b.length) {
            val different = a.indices.filter { a[it] != b[it] }
            return different.size == 1 || (different.size == 2 && different[1] == different[0] + 1 &&
                a[different[0]] == b[different[1]] && a[different[1]] == b[different[0]])
        }
        val shorter = if (a.length < b.length) a else b
        val longer = if (a.length < b.length) b else a
        return longer.indices.any { longer.removeRange(it, it + 1) == shorter }
    }

    fun normalizeIdentity(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }

    fun mentionsIdentity(text: String, identity: String): Boolean {
        val target = normalizeIdentity(identity)
        if (target.isBlank()) return false
        val words = text.split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotBlank() }
        for (start in words.indices) {
            var joined = ""
            for (end in start until words.size) {
                joined += normalizeIdentity(words[end])
                if (joined == target) return true
                if (joined.length >= target.length) break
            }
        }
        return false
    }

    private fun describePerson(person: JSONObject): String {
        val name = person.optString("name").trim().ifBlank { "An unnamed person" }
        val relation = person.optString("relationship").trim()
        val notes = person.optString("notes").trim()
        return (if (relation.isBlank()) "$name is in your family records." else "$name is your $relation.") +
            if (notes.isBlank()) "" else " Caregiver note: $notes"
    }

}
