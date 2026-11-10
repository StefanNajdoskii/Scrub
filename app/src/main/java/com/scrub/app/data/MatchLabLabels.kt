package com.scrub.app.data

import org.json.JSONObject
import java.io.File

/**
 * Match lab labels ("Them" / "Not them") keyed by MediaStore photo ID, in a small JSON file in
 * the app's private storage. Debug builds only; never leaves the phone.
 */
class MatchLabLabels(private val file: File) {

    fun load(): Map<Long, Boolean> = try {
        if (!file.exists()) emptyMap()
        else {
            val json = JSONObject(file.readText())
            json.keys().asSequence().associate { it.toLong() to json.getBoolean(it) }
        }
    } catch (t: Throwable) {
        emptyMap()
    }

    fun save(labels: Map<Long, Boolean>) {
        val json = JSONObject()
        for ((id, them) in labels) json.put(id.toString(), them)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }
}
