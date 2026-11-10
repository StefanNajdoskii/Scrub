package com.scrub.app.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMatcherTest {

    private fun vec(vararg v: Float) = FaceMath.normalize(v.copyOf())
    private val good = FaceQuality(sizePx = 120, yaw = 5f, roll = 0f, sharpness = 500f)

    @Test
    fun strategiesCombineReferences() {
        val refs = ReferenceSet(listOf(vec(1f, 0f), vec(0f, 1f)))
        val face = vec(1f, 0f)
        assertEquals(1f, refs.score(face, MatchStrategy.Max), 1e-6f)
        // Centroid is (1,1)/sqrt2, so cos = 1/sqrt2
        assertEquals(0.70710677f, refs.score(face, MatchStrategy.Centroid), 1e-6f)
        assertEquals(0.5f * 1f + 0.5f * 0.70710677f, refs.score(face, MatchStrategy.Hybrid), 1e-6f)
    }

    @Test
    fun oneReferenceGivesTheSameScoreForEveryStrategy() {
        val refs = ReferenceSet(listOf(vec(3f, 4f)))
        val face = vec(1f, 1f)
        val max = refs.score(face, MatchStrategy.Max)
        assertEquals(max, refs.score(face, MatchStrategy.Centroid), 1e-6f)
        assertEquals(max, refs.score(face, MatchStrategy.Hybrid), 1e-6f)
    }

    @Test
    fun defaultStrategyDependsOnReferenceCount() {
        assertEquals(MatchStrategy.Max, ReferenceSet.defaultStrategy(1))
        assertEquals(MatchStrategy.Hybrid, ReferenceSet.defaultStrategy(2))
        assertEquals(MatchStrategy.Hybrid, ReferenceSet.defaultStrategy(3))
    }

    @Test
    fun photoScoreSkipsUnusableFaces() {
        val refs = ReferenceSet(listOf(vec(1f, 0f)))
        val tiny = FaceRecord(vec(1f, 0f), good.copy(sizePx = 40))
        val profile = FaceRecord(vec(1f, 0f), good.copy(yaw = -60f))
        val blurry = FaceRecord(vec(1f, 0f), good.copy(sharpness = 10f))
        val ok = FaceRecord(vec(1f, 1f), good)
        assertNull(refs.photoScore(listOf(tiny, profile, blurry)))
        assertEquals(0.70710677f, refs.photoScore(listOf(tiny, profile, blurry, ok))!!, 1e-6f)
    }

    @Test
    fun qualityRules() {
        assertTrue(good.usable)
        assertFalse(good.copy(sizePx = 55).usable)
        assertTrue(good.copy(sizePx = 56).usable)
        assertFalse(good.copy(yaw = 46f).usable)
        assertTrue(good.copy(yaw = -45f).usable)
        assertFalse(good.copy(sharpness = FaceQuality.MIN_SHARPNESS - 1).usable)
        assertTrue(good.goodReference)
        assertFalse(good.copy(sizePx = 70).goodReference)
        assertFalse(good.copy(yaw = 35f).goodReference)
        assertFalse(good.copy(sharpness = 100f).goodReference)
    }

    @Test
    fun laplacianVarianceIsZeroOnFlatAndPositiveOnEdges() {
        val flat = FloatArray(64 * 64) { 128f }
        assertEquals(0f, FaceQuality.laplacianVariance(flat, 64, 64), 1e-6f)
        val checker = FloatArray(64 * 64) { i -> if ((i % 64 + i / 64) % 2 == 0) 0f else 255f }
        assertTrue(FaceQuality.laplacianVariance(checker, 64, 64) > 1000f)
    }
}
