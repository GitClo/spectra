package io.github.gitclo.spectra.usb

interface SerialLink {
    fun write(s: String)

    fun read(buf: ByteArray, timeoutMs: Int): Int

    fun drain()
    fun close()
}
