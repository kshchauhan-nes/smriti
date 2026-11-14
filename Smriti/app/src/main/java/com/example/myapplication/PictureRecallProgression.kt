package com.example.myapplication

/** Advance only after complete recall with no distractors; unsuccessful rounds repeat the level. */
fun nextPictureRecallLevel(level: Int, correct: Int, total: Int, incorrect: Int): Int {
    require(level in 1..3 && total == level + 2 && correct in 0..total && incorrect >= 0)
    return if (correct == total && incorrect == 0) (level + 1).coerceAtMost(3) else level
}
