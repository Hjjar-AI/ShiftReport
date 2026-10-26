package com.hos.rushdpatients.testutil

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Debug APK only: real ContentResolver calls with deterministic provider faults. */
class FaultDocumentProvider : ContentProvider() {
    override fun onCreate() = true
    private fun file(uri: Uri): File {
        val id = requireNotNull(uri.lastPathSegment)
        require(id.matches(Regex("[a-zA-Z0-9-]+")))
        return File(requireNotNull(context).cacheDir, "fault-document-$id")
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val fault = uri.getQueryParameter("fault")
        if (fault == "denied") throw SecurityException("synthetic permission revoked")
        if (fault == "unavailable") throw FileNotFoundException("synthetic unavailable destination")
        val file = file(uri)
        if (mode.contains('w')) {
            if (fault == "full") throw FileNotFoundException("synthetic ENOSPC")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
        }
        if (fault == "read-denied") throw SecurityException("synthetic verification permission revoked")
        if (fault == "partial") file.writeBytes(file.readBytes().let { it.copyOf(it.size / 2) })
        if (fault == "corrupt") file.writeBytes(file.readBytes().also { if (it.isNotEmpty()) it[0] = (it[0].toInt() xor 1).toByte() })
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = if (file(uri).delete()) 1 else 0
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Not supported")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
