package com.scrub.app.face

import java.util.Locale

/** One photo's scores under every strategy, for the Match lab. */
class PhotoScores(
    val scores: Map<MatchStrategy, Float>,
    /** The strategy that gave the highest score, and that score. */
    val bestStrategy: MatchStrategy,
    val bestScore: Float,
    /** Quality of the face behind [bestScore]. */
    val quality: FaceQuality,
    val usableFaces: Int,
) {
    companion object {
        /** Null when the photo has no usable face. */
        fun of(faces: List<FaceRecord>, refs: ReferenceSet): PhotoScores? {
            val perStrategy = MatchStrategy.entries.mapNotNull { s -> refs.bestFace(faces, s)?.let { s to it } }
            if (perStrategy.isEmpty()) return null
            val (bestStrategy, best) = perStrategy.maxBy { it.second.second }
            return PhotoScores(
                scores = perStrategy.associate { (s, fs) -> s to fs.second },
                bestStrategy = bestStrategy,
                bestScore = best.second,
                quality = best.first.quality,
                usableFaces = faces.count { it.quality.usable },
            )
        }
    }
}

/** Confusion counts when photos scoring >= [threshold] count as "them". */
data class ThresholdStats(val threshold: Float, val tp: Int, val fp: Int, val fn: Int, val tn: Int) {
    /** NaN when nothing is predicted as a match. */
    val precision: Float get() = if (tp + fp == 0) Float.NaN else tp.toFloat() / (tp + fp)
    val recall: Float get() = if (tp + fn == 0) Float.NaN else tp.toFloat() / (tp + fn)
    val f1: Float get() = if (tp == 0) 0f else 2f * tp / (2f * tp + fp + fn)
}

/** A labelled score: [them] is true for "Them", false for "Not them". */
data class Labeled(val score: Float, val them: Boolean)

object MatchEval {

    fun statsAt(samples: List<Labeled>, threshold: Float): ThresholdStats {
        var tp = 0; var fp = 0; var fn = 0; var tn = 0
        for (s in samples) {
            val predicted = s.score >= threshold
            when {
                predicted && s.them -> tp++
                predicted -> fp++
                s.them -> fn++
                else -> tn++
            }
        }
        return ThresholdStats(threshold, tp, fp, fn, tn)
    }

    /**
     * The threshold with the highest F1. Candidates sit halfway between neighbouring scores, so
     * the suggestion doesn't sit exactly on a labelled photo. Ties go to the higher precision,
     * then the higher (stricter) threshold. Null without any "Them" labels.
     */
    fun bestThreshold(samples: List<Labeled>): ThresholdStats? {
        if (samples.none { it.them }) return null
        val distinct = samples.map { it.score }.distinct().sortedDescending()
        var best: ThresholdStats? = null
        for ((i, s) in distinct.withIndex()) {
            val below = distinct.getOrNull(i + 1)
            val t = if (below == null) s - 0.01f else (s + below) / 2f
            val stats = statsAt(samples, t)
            val b = best
            if (b == null || stats.f1 > b.f1 ||
                (stats.f1 == b.f1 && stats.precision.orZero() > b.precision.orZero())
            ) best = stats
        }
        return best
    }

    /** Counts per bin of width 1/[bins] over 0..1. Scores outside are clamped into the end bins. */
    fun histogram(scores: List<Float>, bins: Int = 20): IntArray {
        val h = IntArray(bins)
        for (s in scores) h[(s * bins).toInt().coerceIn(0, bins - 1)]++
        return h
    }

    /**
     * Plain-text report for sharing: scores, labels and quality numbers only, never photo
     * names, IDs or URIs.
     */
    fun summary(
        rows: List<Pair<PhotoScores, Boolean?>>,
        referenceCount: Int,
        defaultStrategy: MatchStrategy,
        defaultThreshold: Float,
    ): String = buildString {
        val them = rows.count { it.second == true }
        val notThem = rows.count { it.second == false }
        appendLine("Scrub Match lab")
        appendLine("references: $referenceCount, default strategy: ${defaultStrategy.label}, default threshold: ${f2(defaultThreshold)}")
        appendLine("photos with a usable face: ${rows.size} (them $them, not them $notThem, unlabeled ${rows.size - them - notThem})")
        appendLine("quality rules: size>=${FaceQuality.MIN_SIZE_PX}px |yaw|<=${FaceQuality.MAX_YAW.toInt()} sharpness>=${FaceQuality.MIN_SHARPNESS.toInt()}")
        appendLine()
        appendLine("strategy  best_t  P     R     F1    | at ${f2(defaultThreshold)}: P     R     F1")
        for (s in MatchStrategy.entries) {
            val samples = labeled(rows, s)
            val best = bestThreshold(samples)
            val at = statsAt(samples, defaultThreshold)
            append(s.label.padEnd(10))
            if (best == null) append("–".padEnd(6) + "  –     –     –    ")
            else append("${f2(best.threshold).padEnd(6)}  ${f2(best.precision)}  ${f2(best.recall)}  ${f2(best.f1)} ")
            appendLine(" |       ${f2(at.precision)}  ${f2(at.recall)}  ${f2(at.f1)}")
        }
        appendLine()
        appendLine("label     max   centroid hybrid best      faces size yaw   roll  sharp")
        for ((p, label) in rows.sortedByDescending { it.first.bestScore }) {
            val l = when (label) { true -> "them"; false -> "not_them"; null -> "?" }
            appendLine(
                "${l.padEnd(9)} ${f2(p.scores[MatchStrategy.Max])}  ${f2(p.scores[MatchStrategy.Centroid]).padEnd(8)} " +
                    "${f2(p.scores[MatchStrategy.Hybrid])}  ${p.bestStrategy.label.padEnd(9)} ${p.usableFaces.toString().padEnd(5)} " +
                    "${p.quality.sizePx.toString().padEnd(4)} ${f1(p.quality.yaw).padEnd(5)} ${f1(p.quality.roll).padEnd(5)} " +
                    f0(p.quality.sharpness)
            )
        }
    }

    fun labeled(rows: List<Pair<PhotoScores, Boolean?>>, strategy: MatchStrategy): List<Labeled> =
        rows.mapNotNull { (p, label) ->
            val score = p.scores[strategy] ?: return@mapNotNull null
            label?.let { Labeled(score, it) }
        }

    fun f2(v: Float?): String = if (v == null || v.isNaN()) "–" else String.format(Locale.US, "%.2f", v)
    private fun f1(v: Float): String = String.format(Locale.US, "%.1f", v)
    private fun f0(v: Float): String = String.format(Locale.US, "%.0f", v)
    private fun Float.orZero() = if (isNaN()) 0f else this
}
