package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureRecallProgressionTest {
    @Test fun perfectThreeAdvancesToFour() = assertEquals(2, nextPictureRecallLevel(1, 3, 3, 0))
    @Test fun imperfectThreeRepeatsThree() = assertEquals(1, nextPictureRecallLevel(1, 2, 3, 0))
    @Test fun perfectFourAdvancesToFive() = assertEquals(3, nextPictureRecallLevel(2, 4, 4, 0))
    @Test fun imperfectFourRepeatsFourEvenWithLowScore() = assertEquals(2, nextPictureRecallLevel(2, 1, 4, 0))
    @Test fun extraSelectionsPreventAdvancement() = assertEquals(1, nextPictureRecallLevel(1, 3, 3, 1))
    @Test fun fiveRemainsCapped() = assertEquals(3, nextPictureRecallLevel(3, 5, 5, 0))
}
