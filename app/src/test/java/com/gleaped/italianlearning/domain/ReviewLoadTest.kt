package com.gleaped.italianlearning.domain

import com.gleaped.italianlearning.domain.srs.ReviewLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewLoadTest {
    @Test fun smallBacklogIsShownInFull() {
        assertEquals(20, ReviewLoad.shown(20, 0))
        assertFalse(ReviewLoad.eased(20, 0))
    }

    @Test fun bigBacklogIsCapped() {
        assertEquals(ReviewLoad.DAILY_CAP, ReviewLoad.shown(300, 0))
        assertTrue(ReviewLoad.eased(300, 0))
    }

    @Test fun cardsAlreadyReviewedTodayUseUpTheAllowance() {
        assertEquals(10, ReviewLoad.shown(300, ReviewLoad.DAILY_CAP - 10))
        assertEquals(0, ReviewLoad.shown(300, ReviewLoad.DAILY_CAP))
        assertEquals(0, ReviewLoad.allowance(500))
    }

    @Test fun finishingTheCapMeansNothingElseIsEased() {
        assertFalse(ReviewLoad.eased(0, ReviewLoad.DAILY_CAP))
    }
}
