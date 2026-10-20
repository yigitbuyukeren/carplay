package com.ridedeck.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Writes [text] to the cache dir and opens the system share sheet for it. */
fun Context.shareTextFile(fileName: String, text: String) {
    val dir = File(cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(text)
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
