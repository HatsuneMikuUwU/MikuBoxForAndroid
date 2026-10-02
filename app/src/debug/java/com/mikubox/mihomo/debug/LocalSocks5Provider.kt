package com.mikubox.mihomo.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * A loopback SOCKS5 server, for driving the tunnel on an emulator.
 *
 * The tunnel can only be exercised end to end when the profile's proxy points at
 * something that answers, and on an emulator the host's loopback (10.0.2.2) is
 * only reachable if something is listening on the machine running the emulator.
 * This starts that listener inside the app instead, on the device's own loopback,
 * so a profile whose proxy is 127.0.0.1:[PORT] carries real bytes through the
 * core and out to the internet.
 *
 * Its own sockets belong to an app the tunnel excludes, so the traffic it dials
 * leaves directly — which is what makes it an upstream and not a loop.
 *
 * Debug only: this file lives in the `debug` source set and never ships.
 */
class LocalSocks5Provider : ContentProvider() {

    override fun onCreate(): Boolean {
        thread(name = "local-socks5", isDaemon = true) { serve() }
        return true
    }

    private fun serve() {
        try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", PORT))
            }.use { server ->
                Log.i(TAG, "local SOCKS5 upstream listening on 127.0.0.1:$PORT")
                while (!server.isClosed) {
                    val client = server.accept()
                    thread(isDaemon = true) { handle(client) }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "local SOCKS5 upstream stopped", error)
        }
    }

    private fun handle(client: Socket) {
        Log.i(TAG, "accepted from ${client.inetAddress.hostAddress}:${client.port}")
        try {
            client.tcpNoDelay = true
            // Greeting: version, then the methods the client offers.
            val greeting = read(client, 2)
            Log.i(TAG, "greeting: ${greeting.joinToString(",") { (it.toInt() and 0xFF).toString() }}")
            if (greeting[0].toInt() != 5) return
            read(client, greeting[1].toInt())
            client.getOutputStream().write(byteArrayOf(5, 0))

            // Request: version, command, reserved, address type, address, port.
            val head = read(client, 4)
            Log.i(TAG, "request: ${head.joinToString(",") { (it.toInt() and 0xFF).toString() }}")
            if (head[1].toInt() != 1) {
                // Only CONNECT is needed to move TCP through the tunnel.
                client.getOutputStream().write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0))
                return
            }
            val host = when (head[3].toInt()) {
                1 -> read(client, 4).joinToString(".") { (it.toInt() and 0xFF).toString() }
                3 -> String(read(client, read(client, 1)[0].toInt()))
                4 -> {
                    val raw = read(client, 16)
                    (0 until 8).joinToString(":") { index ->
                        ((raw[index * 2].toInt() and 0xFF) shl 8 or (raw[index * 2 + 1].toInt() and 0xFF))
                            .toString(16)
                    }
                }

                else -> return
            }
            val portBytes = read(client, 2)
            val port = (((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF))

            val upstream = Socket()
            upstream.tcpNoDelay = true
            upstream.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            client.getOutputStream().write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            Log.i(TAG, "proxying $host:$port")

            thread(isDaemon = true) { pump(client, upstream) }
            pump(upstream, client)
        } catch (error: Exception) {
            Log.w(TAG, "connection failed: ${error.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    /** Copies one direction until either end closes; both directions run as threads. */
    private fun pump(from: Socket, to: Socket) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            while (true) {
                val read = from.getInputStream().read(buffer)
                if (read <= 0) break
                to.getOutputStream().write(buffer, 0, read)
                to.getOutputStream().flush()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { to.shutdownOutput() }
            runCatching { from.close() }
        }
    }

    private fun read(socket: Socket, count: Int): ByteArray {
        val data = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = socket.getInputStream().read(data, offset, count - offset)
            if (read <= 0) throw IllegalStateException("peer closed")
            offset += read
        }
        return data
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "MikuBoxDebug"
        const val PORT = 11080
        const val CONNECT_TIMEOUT_MS = 15_000
    }
}
