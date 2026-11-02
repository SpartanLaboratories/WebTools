package com.spartanlabs.testing.support.webtools.http

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * The gzip encoding of [mebibytes] MiB of zero bytes, streamed through `GZIPOutputStream` in 64 KiB blocks (50 MiB becomes about 50 KB).
 *
 * @param mebibytes the decoded size in MiB.
 * @return the encoded bytes.
 */
internal fun gzipOfZeros(mebibytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { gzip ->
        val block = ByteArray(64 * 1024)
        repeat(mebibytes * 16) { gzip.write(block) }
    }
    return out.toByteArray()
}
