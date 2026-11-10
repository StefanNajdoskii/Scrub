package com.scrub.app

import android.app.Application
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scrub.app.data.EmbeddingCache
import com.scrub.app.data.Gallery
import com.scrub.app.data.GalleryImage
import com.scrub.app.data.Vault
import com.scrub.app.data.VaultItem
import com.scrub.app.face.DetectedFace
import com.scrub.app.face.FaceFinder
import com.scrub.app.face.FaceRecord
import com.scrub.app.face.ReferenceSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { Home, PickFace, Scanning, Results, Done, Vault }

class RefFace(val thumbnail: Bitmap, val embedding: FloatArray)

class Match(val image: GalleryImage, val score: Float)

/** A scanned photo with at least one usable face. Only collected in debug builds (Match lab). */
class ScannedPhoto(val image: GalleryImage, val faces: List<FaceRecord>)

data class ScanProgress(val done: Int = 0, val total: Int = 0) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

class ScrubViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver = app.contentResolver
    private val finderLazy = lazy { FaceFinder(app) }
    private val finder by finderLazy
    private val cache = EmbeddingCache(File(app.filesDir, "faces.bin"))
    private val vault = Vault(app)

    // ---------- UI state ----------
    var screen by mutableStateOf(Screen.Home)
    val references = mutableStateListOf<RefFace>()

    var pickLoading by mutableStateOf(false)
    var candidateFaces by mutableStateOf<List<DetectedFace>>(emptyList())

    /** A tapped face that's small, blurry or turned away, waiting for "Use this face". */
    var weakCandidate by mutableStateOf<DetectedFace?>(null)

    var progress by mutableStateOf(ScanProgress())
    val matches = mutableStateListOf<Match>()

    /** How similar a face must be to count. Higher = stricter (fewer false matches). */
    var strictness by mutableFloatStateOf(DEFAULT_STRICTNESS)
    var deselected by mutableStateOf(emptySet<Long>())

    var busyMessage by mutableStateOf<String?>(null)
    var doneTitle by mutableStateOf("")
    var doneBody by mutableStateOf("")

    var vaultItems by mutableStateOf<List<VaultItem>>(emptyList())

    /** Debug builds only: the last scan's photos and references, for the Match lab. */
    var labPhotos by mutableStateOf<List<ScannedPhoto>>(emptyList())
        private set
    var labReferences by mutableStateOf<ReferenceSet?>(null)
        private set
    private val labBuffer = ArrayList<ScannedPhoto>()

    private var scanJob: Job? = null
    private var pendingVault: List<Pair<GalleryImage, VaultItem>> = emptyList()
    private var pendingTrash: List<GalleryImage> = emptyList()

    init {
        refreshVault()
    }

    val visibleMatches: List<Match>
        get() = matches.filter { it.score >= strictness }

    val selectedMatches: List<Match>
        get() = visibleMatches.filter { it.image.id !in deselected }

    fun toggle(match: Match) {
        val id = match.image.id
        deselected = if (id in deselected) deselected - id else deselected + id
    }

    fun selectAll(all: Boolean) {
        deselected = if (all) emptySet() else visibleMatches.map { it.image.id }.toSet()
    }

    // ---------- choosing who to find ----------

    fun onReferencePhotoPicked(uri: Uri) {
        screen = Screen.PickFace
        pickLoading = true
        candidateFaces = emptyList()
        weakCandidate = null
        viewModelScope.launch {
            val faces: List<DetectedFace> = withContext(Dispatchers.Default) {
                val bmp = Gallery.decode(resolver, uri, 1600) ?: return@withContext emptyList<DetectedFace>()
                finder.findFaces(bmp, withThumbnails = true).sortedByDescending { it.box.width() }
            }
            candidateFaces = faces
            pickLoading = false
        }
    }

    /** A face was tapped: a clear one is used straight away, a weak one waits for confirmation. */
    fun tapCandidate(face: DetectedFace) {
        if (face.quality.goodReference) chooseReference(face) else weakCandidate = face
    }

    fun chooseReference(face: DetectedFace) {
        val thumb = face.thumbnail ?: return
        references += RefFace(thumb, face.embedding)
        candidateFaces = emptyList()
        weakCandidate = null
        screen = Screen.Home
    }

    fun removeReference(ref: RefFace) {
        references.remove(ref)
    }

    // ---------- scanning ----------

    fun startScan() {
        if (references.isEmpty()) return
        scanJob?.cancel()
        matches.clear()
        deselected = emptySet()
        progress = ScanProgress()
        screen = Screen.Scanning
        val refs = ReferenceSet(references.map { it.embedding })
        if (BuildConfig.DEBUG) {
            labBuffer.clear()
            labPhotos = emptyList()
            labReferences = refs
        }

        scanJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { cache.load() }
                val images = withContext(Dispatchers.IO) { Gallery.queryAll(resolver) }
                progress = ScanProgress(0, images.size)
                var unsaved = 0

                for (chunk in images.chunked(PARALLEL)) {
                    ensureActive()
                    // Photos we've already fingerprinted come straight from the cache
                    val known = chunk.associateWith { cache.get(it.id, it.dateModified) }
                    val fresh = known.filterValues { it == null }.keys.map { img ->
                        async(Dispatchers.Default) { img to fingerprint(img) }
                    }.awaitAll()
                    fresh.forEach { (img, faces) -> cache.put(img.id, img.dateModified, faces) }
                    unsaved += fresh.size

                    val all = known.mapNotNull { (img, f) -> f?.let { img to it } } + fresh
                    for ((img, faces) in all) {
                        // Null when the photo has no usable face (too small, turned, blurry)
                        val score = refs.photoScore(faces) ?: continue
                        if (score >= MIN_SCORE) matches += Match(img, score)
                        if (BuildConfig.DEBUG) labBuffer += ScannedPhoto(img, faces)
                    }
                    progress = ScanProgress(progress.done + chunk.size, images.size)

                    if (unsaved >= 150) {
                        withContext(Dispatchers.IO) { cache.save() }
                        unsaved = 0
                    }
                }
                showResults()
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { cache.save() }
            }
        }
    }

    /** Stop the scan early and look at what's been found so far. */
    fun stopScan() {
        scanJob?.cancel()
        showResults()
    }

    private fun showResults() {
        if (BuildConfig.DEBUG) labPhotos = labBuffer.toList()
        val sorted = matches.sortedByDescending { it.score }
        matches.clear()
        matches.addAll(sorted)
        screen = Screen.Results
    }

    private suspend fun fingerprint(img: GalleryImage): List<FaceRecord> {
        val bmp = Gallery.decode(resolver, img.uri, 1024) ?: return emptyList()
        return try {
            finder.findFaces(bmp).map { it.record }
        } catch (t: Throwable) {
            emptyList()
        } finally {
            bmp.recycle()
        }
    }

    // ---------- acting on matches ----------

    /** Ask Android to move the selected photos to the system trash (recoverable for 30 days). */
    fun trashRequest(): IntentSender? {
        val chosen = selectedMatches.map { it.image }
        if (chosen.isEmpty()) return null
        pendingTrash = chosen
        return MediaStore.createTrashRequest(resolver, chosen.map { it.uri }, true).intentSender
    }

    fun onTrashResult(approved: Boolean) {
        val done = pendingTrash
        pendingTrash = emptyList()
        if (!approved || done.isEmpty()) return
        removeFromMatches(done)
        doneTitle = "Scrubbed."
        doneBody = "${plural(done.size)} moved to the trash. You can still recover them from your " +
            "gallery's trash for about 30 days, after which they're gone for good."
        screen = Screen.Done
    }

    /**
     * Copy the selected photos into the vault, then return the system dialog that asks
     * permission to remove the originals from the gallery.
     */
    fun prepareVault(onReady: (IntentSender) -> Unit) {
        val chosen = selectedMatches.map { it.image }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            busyMessage = "Moving ${plural(chosen.size)} to the vault…"
            val copied = withContext(Dispatchers.IO) {
                chosen.mapNotNull { img -> vault.copyIn(resolver, img)?.let { img to it } }
            }
            busyMessage = null
            if (copied.isEmpty()) return@launch
            pendingVault = copied
            onReady(MediaStore.createDeleteRequest(resolver, copied.map { it.first.uri }).intentSender)
        }
    }

    fun onVaultDeleteResult(approved: Boolean) {
        val copied = pendingVault
        pendingVault = emptyList()
        if (copied.isEmpty()) return
        if (!approved) {
            // User said no: undo the copies so nothing is duplicated
            vault.delete(copied.map { it.second })
            refreshVault()
            return
        }
        removeFromMatches(copied.map { it.first })
        refreshVault()
        doneTitle = "Hidden away."
        doneBody = "${plural(copied.size)} moved into Scrub's private vault. They no longer appear " +
            "in your gallery. Open the vault any time to restore or delete them."
        screen = Screen.Done
    }

    private fun removeFromMatches(images: List<GalleryImage>) {
        val ids = images.map { it.id }.toSet()
        matches.removeAll { it.image.id in ids }
        cache.remove(ids)
    }

    // ---------- vault ----------

    fun refreshVault() {
        viewModelScope.launch {
            vaultItems = withContext(Dispatchers.IO) { vault.items() }
        }
    }

    fun vaultFile(item: VaultItem): File = vault.fileFor(item)

    fun restoreFromVault(items: List<VaultItem>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            busyMessage = "Restoring…"
            val ok = withContext(Dispatchers.IO) { items.count { vault.restore(resolver, it) } }
            busyMessage = null
            refreshVault()
            onDone(ok)
        }
    }

    fun deleteFromVault(items: List<VaultItem>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { vault.delete(items) }
            refreshVault()
        }
    }

    // ---------- navigation ----------

    fun goHome() {
        scanJob?.cancel()
        screen = Screen.Home
    }

    fun startOver() {
        references.clear()
        matches.clear()
        screen = Screen.Home
    }

    override fun onCleared() {
        scanJob?.cancel()
        if (finderLazy.isInitialized()) finder.close()
    }

    companion object {
        /**
         * Strictness slider range. Straightened, flip-averaged embeddings (cache v2) score the
         * same person higher than before (emulator test pairs: 0.74 -> 0.88 and 0.82 -> 0.83)
         * while different people stay below ~0.28, so the top end moved from 0.85 to 0.90 to
         * leave room for very strict settings. The bottom stays at 0.35, well above
         * different-person scores.
         */
        const val MIN_SCORE = 0.35f
        const val MAX_SCORE = 0.90f
        const val DEFAULT_STRICTNESS = 0.55f
        private const val PARALLEL = 4

        fun plural(n: Int) = if (n == 1) "1 photo" else "$n photos"
    }
}
