package com.freeobd.app.data.remote

import com.freeobd.app.utils.ByteUtils
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Mutex to serialize all command execution.
     *
     * Without this, concurrent calls to [sendRaw] (e.g. PID discovery running
     * in parallel with live data polling) corrupt the shared InputStream/OutputStream.
     * The ELM327 is a half-duplex serial protocol — only one command may be
     * in flight at a time.
     */
    private val mutex = kotlinx.coroutines.sync.Mutex()

    fun initialize() {
        connection = ObdDeviceConnection(transport.inputStream, transport.outputStream)
        isFirstCommand = true

        // Flush any initial data the adapter may have emitted upon connection
        // (e.g. welcome banner "ELM327 v2.1"). Without this, the first command
        // would read stale buffered data instead of its actual response.
        try {
            val input = transport.inputStream
            while (input.available() > 0) {
                input.skip(input.available().toLong())
            }
        } catch (_: Exception) {
            // InputStream may not support available() — ignore
        }
    }

    // ── Library command execution ──────────────────────────

    /** Run a kotlin-obd-api library command. */
    suspend fun runLibraryCommand(command: ObdCommand): Result<ObdResponse> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = connection ?: throwNotInitialized()
                    val response = withTimeout(commandTimeout()) { conn.run(command) }
                    if (isFirstCommand) isFirstCommand = false
                    delay(interCommandDelayMs)
                    response
                }
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
     * All calls are serialized via [mutex] — the ELM327 is half-duplex.
     *
     * @param rawCommand The command string without '\\r' suffix (e.g. "010C").
     * @return Raw response bytes (with echo and prompt stripped).
     */
    suspend fun sendRaw(rawCommand: String, forceLog: Boolean = false): Result<ByteArray> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val cmd = if (rawCommand.endsWith("\r")) rawCommand else "$rawCommand\r"
                    // forceLog=false: automatic commands, logged only when debug is enabled
                    // forceLog=true: caller handles logging (sendRawCommand, sendObdCommand)
                    val shouldLog = !forceLog && DebugLogger.enabled

                    if (shouldLog) DebugLogger.tx(rawCommand)

                    transport.outputStream.write(cmd.toByteArray(Charsets.US_ASCII))
                    transport.outputStream.flush()

                    delay(50)

                    val response = withTimeout(commandTimeout()) {
                        readResponse()
                    }

                    if (isFirstCommand) isFirstCommand = false
                    delay(interCommandDelayMs)

                    val result = stripEcho(rawCommand, response)

                    if (shouldLog) {
                        val rxText = String(result, Charsets.US_ASCII)
                            .replace(">", "")
                            .replace("\r", "\n")
                            .trim()
                        DebugLogger.rx(rxText)
                    }

                    result
                }.onFailure { e ->
                    DebugLogger.error("${rawCommand}: ${e.message ?: "unknown error"}")
                }
            }
        }

    /**
     * Send an OBD mode command (01-09) with negative response detection.
     *
     * Wraps [sendRaw] and checks the response for ISO 15765-2 negative
     * response frames (7F [service] [responseCode]). If a negative response
     * is detected, returns [Result.failure] with [NegativeResponseException].
     *
     * AT commands (ATZ, ATSP, etc.) should continue to use [sendRaw] directly.
     */
    suspend fun sendObdCommand(command: String, forceLog: Boolean = false): Result<ByteArray> {
        val result = sendRaw(command, forceLog)
        if (result.isFailure) return result

        val response = result.getOrThrow()
        // Decode ELM327 ASCII hex response and check for 7F negative response.
        // The response may contain status messages on separate lines
        // (e.g. "SEARCHING..."); only hex data lines are checked.
        val text = String(response, Charsets.US_ASCII)
            .replace(">", "").replace("\r", "\n")
        for (line in text.lines()) {
            val hex = line.replace(" ", "").trim()
            if (hex.isEmpty() || hex.length < 6) continue
            val decoded = try {
                ByteUtils.fromHexString(hex)
            } catch (_: Exception) {
                continue // Not valid hex (e.g. "SEARCHING...") — skip
            }
            // Search for 7F [service] [responseCode] pattern
            for (i in 0 until decoded.size - 2) {
                if (decoded[i].toInt() and 0xFF == 0x7F) {
                    val service = decoded[i + 1].toInt() and 0xFF
                    val responseCode = decoded[i + 2].toInt() and 0xFF
                    val ex = NegativeResponseException(
                        service = service,
                        responseCode = responseCode,
                        command = command
                    )
                    DebugLogger.error("$command: 7F ${String.format("%02X", service)} ${String.format("%02X", responseCode)}")
                    return Result.failure(ex)
                }
            }
        }
        return result
    }

    fun markFirstCommand() { isFirstCommand = true }
    val isActive: Boolean get() = transport.isConnected && connection != null
    fun release() { connection = null }

    // ── Internal ───────────────────────────────────────────

    private fun commandTimeout(): Long =
        if (isFirstCommand) FIRST_COMMAND_TIMEOUT_MS else STANDARD_COMMAND_TIMEOUT_MS

    /**
     * Read response bytes from the input stream until the ELM327 prompt '>'.
     *
     * The ELM327 signals end-of-response with a single '>' character.
     * We wait up to the command timeout for data — the caller wraps this
     * in [withTimeout], so a hung adapter will be caught at that level.
     */
    private fun readResponse(): ByteArray {
        val buffer = ByteArrayOutputStream()
        val input = transport.inputStream

        while (true) {
            val b = input.read()
            if (b == -1) break

            // ELM327 prompt '>' signals end of response — don't include it
            if (b == '>'.code) break

            buffer.write(b)

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

/**
 * Thrown when an OBD mode command receives an ISO 15765-2 negative response.
 *
 * @property service The requested service ID (e.g. 0x09 for Mode 09).
 * @property responseCode The NRC (Negative Response Code) from the ECU.
 * @property command The original OBD command that triggered this response.
 */
class NegativeResponseException(
    val service: Int,
    val responseCode: Int,
    val command: String
) : Exception(
    buildString {
        append("Negative response for $command: service=0x")
        append(String.format("%02X", service))
        append(", code=0x")
        append(String.format("%02X", responseCode))
        append(" (")
        append(responseCodeDescription(responseCode))
        append(")")
    }
) {
    /** Hex representation for display in UI (e.g. "7F 09 11"). */
    fun toHexString(): String = String.format("7F %02X %02X", service, responseCode)

    companion object {
        /** Human-readable descriptions for common NRC values (SAE J1979 / ISO 14229). */
        private fun responseCodeDescription(code: Int): String = when (code) {
            0x10 -> "generalReject"
            0x11 -> "serviceNotSupported"
            0x12 -> "subFunctionNotSupported"
            0x13 -> "incorrectMessageLengthOrInvalidFormat"
            0x21 -> "busyRepeatRequest"
            0x22 -> "conditionsNotCorrect"
            0x24 -> "requestSequenceError"
            0x31 -> "requestOutOfRange"
            0x33 -> "securityAccessDenied"
            0x35 -> "invalidKey"
            0x36 -> "exceedNumberOfAttempts"
            0x37 -> "requiredTimeDelayNotExpired"
            0x78 -> "requestCorrectlyReceivedResponsePending"
            else -> "unknown"
        }
    }
}
