package dev.ygtlab.carplay.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.ygtlab.carplay.data.TripPoint
import java.io.File

/** Writes the trip to the cache dir and opens the system share sheet for it. */
fun Context.shareGpx(name: String, fileName: String, points: List<TripPoint>) {
    val dir = File(cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(Gpx.write(name, points))
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/gpx+xml")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
