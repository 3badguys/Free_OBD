package com.freeobd.app.data.remote

import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/**
 * OBD command execution layer.
 *
 * Two execution modes:
 * 1. Library commands via [runLibraryCommand] — for kotlin-obd-api's built-in commands
 * 2. Raw commands via [sendRaw] — for any custom AT/Mode command, returns raw bytes
 */
class ObdCommandQueue(
    private val transport: ObdTransport,
    private val interCommandDelayMs: Long = DEFAULT_INTER_COMMAND_DELAY_MS
) {
    private var connection: ObdDeviceConnection? = null
    private var isFirstCommand = true

    fun initialize() {
        connection = ObdDeviceConnection(transport.inputStream, transport.outputStream)
        isFirstCommand = true
    }

    // ── Library command execution ──────────────────────────

    /** Run a kotlin-obd-api library command. */
    suspend fun runLibraryCommand(command: ObdCommand): Result<ObdResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = connection ?: throwNotInitialized()
                val response = withTimeout(commandTimeout()) { conn.run(command) }
                if (isFirstCommand) isFirstCommand = false
                delay(interCommandDelayMs)
                response
            }
        }

    // ── Raw command execution (bypasses library command classes) ──

    /**
     * Send a raw OBD command string and read the response.
     *
     * Handles the ELM327 protocol directly:
     * 1. Sends the command + CR ('\r')
     * 2. Reads the response until the '>' prompt
     * 3. Strips echo bytes if present
     *
     * @param rawCommand The command string without '\\r' suffix (e.g. "010C").
     * @return Raw response bytes (with echo and prompt stripped).
     */
    suspend fun sendRaw(rawCommand: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cmd = if (rawCommand.endsWith("\r")) rawCommand else "$rawCommand\r"
                transport.outputStream.write(cmd.toByteArray(Charsets.US_ASCII))
                transport.outputStream.flush()

                delay(50) // Minimal delay for adapter to start responding

                val response = withTimeout(commandTimeout()) {
                    readResponse()
                }

                if (isFirstCommand) isFirstCommand = false
                delay(interCommandDelayMs)

                // If echo is on, strip the echoed command from the response
                val result = stripEcho(rawCommand, response)
                result
            }
        }

    fun markFirstCommand() { isFirstCommand = true }
    val isActive: Boolean get() = transport.isConnected && connection != null
    fun release() { connection = null }

    // ── Internal ───────────────────────────────────────────

    private fun commandTimeout(): Long =
        if (isFirstCommand) FIRST_COMMAND_TIMEOUT_MS else STANDARD_COMMAND_TIMEOUT_MS

    /**
     * Read response bytes from the input stream until the ELM327 prompt '>'.
     */
    private fun readResponse(): ByteArray {
        val buffer = ByteArrayOutputStream()
        val input = transport.inputStream
        var consecutivePrompt = 0

        while (true) {
            val b = input.read()
            if (b == -1) break

            buffer.write(b)

            // Detect '>' prompt character
            if (b == '>'.code) {
                consecutivePrompt++
                if (consecutivePrompt >= 2) break // Two consecutive '>' signals end
            } else {
                consecutivePrompt = 0
            }

            // Safety: limit response size
            if (buffer.size() > MAX_RESPONSE_SIZE) break
        }

        return buffer.toByteArray()
    }

    /**
     * If the ELM327 echo is on (cheap clones), the response starts with
     * the sent command. Strip it from the response bytes.
     */
    private fun stripEcho(command: String, response: ByteArray): ByteArray {
        val cmdBytes = (command + "\r").toByteArray(Charsets.US_ASCII)
        val respStr = String(response, Charsets.US_ASCII)

        // Check if response starts with the command (echo)
        if (respStr.startsWith(command) || respStr.startsWith(command + "\r")) {
            // Find where echo ends
            val echoEnd = if (respStr.startsWith(command + "\r\n"))
                command.length + 2
            else if (respStr.startsWith(command + "\r"))
                command.length + 1
            else
                command.length

            if (echoEnd < response.size) {
                return response.copyOfRange(echoEnd, response.size)
            }
        }

        return response
    }

    private fun throwNotInitialized(): Nothing =
        throw IllegalStateException("ObdCommandQueue not initialized — call initialize() first")

    companion object {
        const val DEFAULT_INTER_COMMAND_DELAY_MS = 100L
        private const val FIRST_COMMAND_TIMEOUT_MS = 10_000L
        private const val STANDARD_COMMAND_TIMEOUT_MS = 3_000L
        private const val MAX_RESPONSE_SIZE = 4096
    }
}
