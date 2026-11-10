package com.scrub.app.face

/**
 * Ways to combine several reference faces into one score per face.
 * With a single reference all three give the same number.
 */
enum class MatchStrategy(val label: String) {
    /** Best similarity to any single reference. */
    Max("max"),

    /** Similarity to the normalised average of all references. */
    Centroid("centroid"),

    /** 0.5 * max + 0.5 * centroid. */
    Hybrid("hybrid"),
}

/** The reference faces the user picked, ready to score gallery faces against. */
class ReferenceSet(val embeddings: List<FloatArray>) {

    val centroid: FloatArray = FaceMath.normalize(
        FloatArray(embeddings.firstOrNull()?.size ?: 0).also { c ->
            for (e in embeddings) for (i in c.indices) c[i] += e[i]
        }
    )

    val size: Int get() = embeddings.size

    /** The strategy scans use for this many references. */
    val defaultStrategy: MatchStrategy get() = defaultStrategy(size)

    /** Similarity of one face to these references. */
    fun score(face: FloatArray, strategy: MatchStrategy = defaultStrategy): Float {
        if (embeddings.isEmpty()) return -1f
        return when (strategy) {
            MatchStrategy.Max -> maxSimilarity(face)
            MatchStrategy.Centroid -> FaceMath.similarity(face, centroid)
            MatchStrategy.Hybrid -> 0.5f * maxSimilarity(face) + 0.5f * FaceMath.similarity(face, centroid)
        }
    }

    /**
     * Score of a photo: its best usable face. Null when it has no usable face (too small,
     * turned away or blurry), so it never counts as a match.
     */
    fun photoScore(faces: List<FaceRecord>, strategy: MatchStrategy = defaultStrategy): Float? =
        bestFace(faces, strategy)?.second

    /** The usable face with the highest score, and that score. */
    fun bestFace(faces: List<FaceRecord>, strategy: MatchStrategy = defaultStrategy): Pair<FaceRecord, Float>? {
        var best: Pair<FaceRecord, Float>? = null
        for (f in faces) {
            if (!f.quality.usable) continue
            val s = score(f.embedding, strategy)
            if (best == null || s > best.second) best = f to s
        }
        return best
    }

    private fun maxSimilarity(face: FloatArray): Float {
        var best = -1f
        for (r in embeddings) {
            val s = FaceMath.similarity(face, r)
            if (s > best) best = s
        }
        return best
    }

    companion object {
        /**
         * Not yet chosen from real data: the emulator only has two photos per person, too few to
         * compare strategies. Hybrid keeps max's tolerance for one great reference while the
         * centroid term damps a single misleading one. Revisit with Match lab results.
         */
        fun defaultStrategy(referenceCount: Int): MatchStrategy =
            if (referenceCount >= 2) MatchStrategy.Hybrid else MatchStrategy.Max
    }
}
