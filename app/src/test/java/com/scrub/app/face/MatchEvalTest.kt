package com.scrub.app.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchEvalTest {

    private val samples = listOf(
        Labeled(0.90f, true), Labeled(0.80f, true), Labeled(0.62f, true),
        Labeled(0.58f, false), Labeled(0.40f, true), Labeled(0.30f, false), Labeled(0.10f, false),
    )

    @Test
    fun statsAtThreshold() {
        val s = MatchEval.statsAt(samples, 0.55f)
        assertEquals(3, s.tp); assertEquals(1, s.fp); assertEquals(1, s.fn); assertEquals(2, s.tn)
        assertEquals(0.75f, s.precision, 1e-6f)
        assertEquals(0.75f, s.recall, 1e-6f)
        assertEquals(0.75f, s.f1, 1e-6f)
    }

    @Test
    fun nothingPredictedHasUndefinedPrecision() {
        val s = MatchEval.statsAt(samples, 0.99f)
        assertTrue(s.precision.isNaN())
        assertEquals(0f, s.f1, 0f)
    }

    @Test
    fun bestThresholdMaximisesF1AndSitsBetweenScores() {
        val best = MatchEval.bestThreshold(samples)!!
        // Perfect split is impossible (0.58 "not them" sits above 0.40 "them"). 0.60 gives
        // F1 6/7 (P 1, R 0.75); 0.35, halfway between 0.40 and 0.30, gives 8/9 (P 0.8, R 1).
        assertEquals(0.35f, best.threshold, 1e-6f)
        assertEquals(0.8f, best.precision, 1e-6f)
        assertEquals(1f, best.recall, 1e-6f)
    }

    @Test
    fun f1TiesGoToHigherPrecision() {
        // 0.65 -> tp1 fn1 (F1 2/3, P 1); 0.45 -> tp2 fp2 (F1 2/3, P 0.5)
        val tie = listOf(Labeled(0.7f, true), Labeled(0.6f, false), Labeled(0.55f, false), Labeled(0.5f, true))
        val best = MatchEval.bestThreshold(tie)!!
        assertEquals(1f, best.precision, 1e-6f)
        assertEquals(0.65f, best.threshold, 1e-6f)
    }

    @Test
    fun noPositivesNoSuggestion() {
        assertNull(MatchEval.bestThreshold(listOf(Labeled(0.5f, false))))
        assertNull(MatchEval.bestThreshold(emptyList()))
    }

    @Test
    fun histogramClampsIntoEndBins() {
        val h = MatchEval.histogram(listOf(-0.2f, 0.0f, 0.04f, 0.5f, 0.99f, 1.0f, 1.3f), bins = 20)
        assertEquals(3, h[0])
        assertEquals(1, h[10])
        assertEquals(3, h[19])
        assertEquals(7, h.sum())
    }

    @Test
    fun photoScoresPickTheBestStrategy() {
        val refs = ReferenceSet(listOf(FaceMath.normalize(floatArrayOf(1f, 0f)), FaceMath.normalize(floatArrayOf(0f, 1f))))
        val q = FaceQuality(100, 0f, 0f, 500f)
        val p = PhotoScores.of(listOf(FaceRecord(floatArrayOf(1f, 0f), q)), refs)!!
        assertEquals(MatchStrategy.Max, p.bestStrategy)
        assertEquals(1f, p.bestScore, 1e-6f)
        assertNull(PhotoScores.of(listOf(FaceRecord(floatArrayOf(1f, 0f), q.copy(sizePx = 10))), refs))
    }

    @Test
    fun summaryHasScoresButNoIdentifiers() {
        val refs = ReferenceSet(listOf(FaceMath.normalize(floatArrayOf(1f, 0f))))
        val q = FaceQuality(100, 3f, -2f, 500f)
        val p = PhotoScores.of(listOf(FaceRecord(FaceMath.normalize(floatArrayOf(1f, 1f)), q)), refs)!!
        val text = MatchEval.summary(listOf(p to true, p to null), 1, MatchStrategy.Max, 0.55f)
        assertTrue(text, text.contains("them 1, not them 0, unlabeled 1"))
        assertTrue(text, text.contains("0.71"))
        assertFalse(text, text.contains("content://"))
    }
}
