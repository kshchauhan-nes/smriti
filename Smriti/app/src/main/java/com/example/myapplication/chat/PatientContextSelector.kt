package com.example.myapplication.chat

import com.example.myapplication.data.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

/** Search every record before fitting relevant context into the small local model. */
object PatientContextSelector {
    fun select(snapshot: JSONObject, input: String, history: List<ChatMessage>): JSONObject {
        val output = JSONObject()
        for (key in listOf("patient", "replyLanguage", "region", "localTime", "likedTopics", "avoidedTopics", "selectedInterests"))
            if (snapshot.has(key)) output.put(key, snapshot.get(key))
        val query = input.lowercase()

        // Emotion requests use profile/preferences only, including short follow-ups.
        val companionshipQuery = OfflineConversationSupport.companionship(input, history)
        if (companionshipQuery) return output

        val followUp = Regex(
            "\\b(she|he|they|her|him|their|more|again|that|it)\\b"
        ).containsMatchIn(query)
        val previous = if (followUp) history.takeLast(4).joinToString(" ") { it.text.lowercase() } else ""
        val search = "$query $previous"
        val terms = search.split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.length > 2 }.toSet()
        val family = snapshot.optJSONArray("family") ?: JSONArray()
        val rankedFamily = (0 until family.length()).mapNotNull { family.optJSONObject(it) }.map { row ->
            val name = PatientRecordAnswers.normalizeIdentity(row.optString("name"))
            val relationship = PatientRecordAnswers.normalizeIdentity(row.optString("relationship"))
            val score = (if (PatientRecordAnswers.mentionsIdentity(search, name)) 100 else 0) +
                (if (PatientRecordAnswers.mentionsIdentity(search, relationship)) 60 else 0)
            row to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        val familyQuestion =
            !companionshipQuery &&
                    (
                            rankedFamily.isNotEmpty() || Regex("\\b(family|relative|relatives)\\b").containsMatchIn(query)
                            )
        val gameQuestion = !familyQuestion && Regex("\\b(game|games|score|scores|level)\\b").containsMatchIn(search)
        val categories = mutableListOf<String>()
        if (familyQuestion) {
            categories += "family"}
        if (!familyQuestion && Regex("\\b(medicine|medicines|meds|dose)\\b").containsMatchIn(search)) categories += "medicines"
        if (!familyQuestion && Regex("\\b(task|tasks|routine|routines|activity|activities|schedule)\\b").containsMatchIn(search)) categories += "routines"
        if (gameQuestion) categories += "recentGames"
        if (!familyQuestion) for (category in listOf("medicines", "routines")) {
            val rows = snapshot.optJSONArray(category) ?: JSONArray()
            if ((0 until rows.length()).any { index ->
                val row = rows.optJSONObject(index)
                val name = row?.optString("name", row.optString("title"))?.lowercase().orEmpty()
                name.isNotBlank() && search.contains(name)
            } && category !in categories) categories += category
        }

        if (Regex("\\b(reminder|reminders|notifications)\\b").containsMatchIn(search)) output.put("reminders", snapshot.optJSONObject("reminders"))
        if (Regex("\\b(water|hydration)\\b").containsMatchIn(search)) output.put("hydration", snapshot.optJSONObject("hydration"))
        val coverage = JSONObject()
        for (category in listOf("family", "medicines", "routines", "recentGames")) {
            val all = snapshot.optJSONArray(category) ?: JSONArray()
            val rows = if (category == "family" && rankedFamily.isNotEmpty()) rankedFamily.map { it.first }
                else (0 until all.length()).mapNotNull { all.optJSONObject(it) }.sortedByDescending { row -> terms.count { row.toString().lowercase().contains(it) } }
            val selected = JSONArray()
            if (category in categories) for (row in rows) {
                selected.put(row)
                output.put(category, selected)
                if (output.toString().length > 3500) selected.remove(selected.length() - 1)
            }
            coverage.put(category, JSONObject().put("total", all.length()).put("included", selected.length()))
        }
        // Keep aggregate data out of unrelated conversation, especially family questions.
        val allInsights = buildInsights(snapshot)
        val insights = JSONObject()
        if (gameQuestion) {
            val games = allInsights.getJSONObject("games")
            if (games.has("trend")) games.put("trend", gameTrendText(games.getString("trend")))
            insights.put("games", games)
        }
        if ("medicines" in categories) insights.put("medicines", allInsights.getJSONObject("medicines"))
        if ("routines" in categories) insights.put("routines", allInsights.getJSONObject("routines"))
        if (output.has("hydration")) insights.put("hydration", allInsights.getJSONObject("hydration"))
        if (insights.length() > 0) output.put("computedInsights", insights)
        if (categories.isNotEmpty()) output.put("recordCoverage", coverage)
        require(output.toString().length < 9000) { "Patient profile is too large for offline AI." }
        return output
    }
    private fun buildInsights(
        snapshot: JSONObject
    ): JSONObject {

        val insights = JSONObject()

        // --------------------------
        // MEDICINES
        // --------------------------

        val medicines =
            snapshot.optJSONArray("medicines")
                ?: JSONArray()

        var takenMedicines = 0

        for (i in 0 until medicines.length()) {

            val medicine =
                medicines.optJSONObject(i)
                    ?: continue

            if (
                medicine.optString("status") == "marked_complete"
            ) {
                takenMedicines++
            }
        }

        insights.put(
            "medicines",
            JSONObject()
                .put(
                    "total",
                    medicines.length()
                )
                .put(
                    "markedTaken",
                    takenMedicines
                )
                .put(
                    "notMarkedTaken",
                    medicines.length() -
                            takenMedicines
                )
        )

        // --------------------------
        // ROUTINES
        // --------------------------

        val routines =
            snapshot.optJSONArray("routines")
                ?: JSONArray()

        var completedRoutines = 0

        for (i in 0 until routines.length()) {

            val routine =
                routines.optJSONObject(i)
                    ?: continue

            if (
                routine.optString("status") == "marked_complete"
            ) {
                completedRoutines++
            }
        }

        insights.put(
            "routines",
            JSONObject()
                .put(
                    "total",
                    routines.length()
                )
                .put(
                    "completed",
                    completedRoutines
                )
                .put(
                    "notMarkedCompleted",
                    routines.length() -
                            completedRoutines
                )
        )

        // --------------------------
        // HYDRATION
        // --------------------------

        val hydration =
            snapshot.optJSONObject(
                "hydration"
            )

        if (hydration != null) {

            insights.put(
                "hydration",
                JSONObject()
                    .put(
                        "loggedGlasses",
                        hydration.optInt(
                            "recordedGlasses",
                            0
                        )
                    )
                    .put(
                        "targetGlasses",
                        hydration.optInt(
                            "targetGlasses",
                            0
                        )
                    )
            )
        }

        // --------------------------
        // GAME PERFORMANCE
        // --------------------------

        val games =
            snapshot.optJSONArray(
                "recentGames"
            ) ?: JSONArray()

        val scores =
            mutableListOf<Double>()

        for (i in 0 until games.length()) {

            val game =
                games.optJSONObject(i)
                    ?: continue

            val score =
                when {

                    game.has("score") ->
                        game.optDouble(
                            "score",
                            Double.NaN
                        )

                    game.has("accuracy") ->
                        game.optDouble(
                            "accuracy",
                            Double.NaN
                        )

                    else ->
                        Double.NaN
                }

            if (!score.isNaN()) {
                scores += score
            }
        }

        val gameInsights =
            JSONObject()
                .put(
                    "sessions",
                    games.length()
                )

        if (scores.isNotEmpty()) {

            gameInsights.put(
                "averageScore",
                scores.average().toInt()
            )

            gameInsights.put(
                "bestScore",
                scores.maxOrNull()
                    ?.toInt()
                    ?: 0
            )

            gameInsights.put(
                "trend",
                gameTrend(scores)
            )
        }

        insights.put(
            "games",
            gameInsights
        )

        return insights
    }
    internal fun gameTrend(scoresNewestFirst: List<Double>): String = calculateGameTrend(scoresNewestFirst.asReversed())

    internal fun gameTrendText(trend: String): String = when (trend) {
        "HIGHER_RECENT_SCORES" -> "Recent scores are higher than earlier scores."
        "LOWER_RECENT_SCORES" -> "Recent scores are lower than earlier scores."
        "STABLE" -> "Recent scores are similar to earlier scores."
        else -> "There are not enough sessions to compare recent scores."
    }

    private fun calculateGameTrend(
        scores: List<Double>
    ): String {

        if (scores.size < 4) {
            return "INSUFFICIENT_DATA"
        }

        val middle =
            scores.size / 2

        val earlier =
            scores.take(middle)
                .average()

        val recent =
            scores.drop(middle)
                .average()

        return when {

            recent > earlier + 8 ->
                "HIGHER_RECENT_SCORES"

            recent < earlier - 8 ->
                "LOWER_RECENT_SCORES"

            else ->
                "STABLE"
        }
    }
    fun selectForCloud(snapshot: JSONObject, input: String, history: List<ChatMessage>): JSONObject =
        select(snapshot, input, history)
}
