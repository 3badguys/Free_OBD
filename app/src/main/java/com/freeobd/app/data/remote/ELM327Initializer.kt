package com.freeobd.app.data.remote

import kotlinx.coroutines.delay

/**
 * Executes the ELM327 initialization sequence using raw AT commands.
 *
 *  1. ATZ            — Full reset (clears previous session state)
 *  2. ATE0           — Disable command echo
 *  3. ATL0           — Disable line feeds
 *  4. AT+VERSION     — Extended version info; auto-detects Yuming crypto handshake
 *  5. AT+SETCRYPT    — Auto-computed for Yuming adapters, or manual override (non-critical)
 *  6. ATSPx          — Protocol selection
 *  7. ATH1           — Enable CAN headers (CAN only, skipped for K-line)
 *  8. ATSH           — ECU header address (optional)
 *
 * Note: ATRV and ATI are queried after connection by BluetoothViewModel
 * for UI display. Including them here would double-send both commands.
 */
class ELM327Initializer(
    private val commandQueue: ObdCommandQueue
) {
    /**
     * @param cryptoKey Optional manual crypto key override. When null (the default),
     *                  the initializer will auto-detect Yuming Electronics adapters
     *                  from the AT+VERSION response and compute the key automatically.
     */
    suspend fun initialize(
        protocol: String = "ATSP0",
        ecuAddress: String? = null,
        cryptoKey: String? = null
    ): Result<Unit> {
        val steps = buildInitSteps(protocol, ecuAddress)

        steps.forEachIndexed { index, step ->
            val result = commandQueue.sendRaw(step.command)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                if (step.critical) {
                    return Result.failure(
                        ELM327InitException(
                            "ELM327 init failed at step ${index + 1}/${steps.size} " +
                                "(${step.description}): ${error?.message ?: "unknown error"}",
                            error
                        )
                    )
                }
                // Non-critical step failure (e.g. ATRV on adapters that
                // don't support it) — skip and continue.
            }

            // After AT+VERSION, auto-detect Yuming crypto handshake.
            // The crypt: challenge is embedded in the version response and
            // changes on every connection, so the key must be computed at runtime.
            if (step.command == "AT+VERSION") {
                val key = cryptoKey ?: result.getOrNull()?.let { response ->
                    if (YMOBDCrypto.isYumingAdapter(response)) {
                        YMOBDCrypto.extractCryptChallenge(response)?.let { challenge ->
                            YMOBDCrypto.generateKey(challenge)
                        }
                    } else null
                }
                if (!key.isNullOrBlank()) {
                    commandQueue.sendRaw("AT+SETCRYPT$key")
                    delay(300L)
                }
            }

            delay(step.postDelayMs)
        }
        return Result.success(Unit)
    }

    private fun buildInitSteps(
        protocol: String,
        ecuAddress: String?
    ): List<InitStep> {
        val steps = mutableListOf<InitStep>()
        val proto = if (protocol in VALID_PROTOCOLS) protocol else "ATSP0"

        // Step 1: ATZ — full reset to clear previous session state.
        steps.add(InitStep("ATZ (reset)", "ATZ", 500L))

        // Step 2: Disable echo
        steps.add(InitStep("ATE0 (disable echo)", "ATE0", 300L))

        // Step 3: Disable line feed
        steps.add(InitStep("ATL0 (disable line feed)", "ATL0", 200L))

        // Step 4: Read extended version info.
        // ATRV and ATI are NOT included here — they are queried after
        // connection by BluetoothViewModel for UI display. Including them
        // here would double-send them, wasting ~400ms in the connection flow.
        // If the adapter identifies as Shenzhen Yuming Electronics, the
        // crypt: challenge is extracted and the SETCRYPT key is computed
        // automatically (see YMOBDCrypto). A manual cryptoKey override
        // passed to initialize() takes precedence.
        steps.add(InitStep("AT+VERSION", "AT+VERSION", 200L, critical = false))

        // Step 7: Select protocol
        steps.add(
            InitStep(
                "$proto (protocol select)",
                proto,
                if (proto == "ATSP0") 500L else 300L
            )
        )

        // Step 8: Enable CAN headers (CAN-only; skipped for K-line)
        if (proto !in NON_CAN_PROTOCOLS) {
            steps.add(InitStep("ATH1 (enable CAN headers)", "ATH1", 200L))
        }

        // Step 9 (optional): Set ECU header address
        if (!ecuAddress.isNullOrBlank()) {
            steps.add(InitStep("ATSH $ecuAddress", "ATSH$ecuAddress", 200L))
        }

        return steps
    }

    private data class InitStep(
        val description: String,
        val command: String,
        val postDelayMs: Long,
        val critical: Boolean = true
    )

    companion object {
        private val VALID_PROTOCOLS = setOf(
            "ATSP0", "ATSP1", "ATSP2", "ATSP3", "ATSP4",
            "ATSP5", "ATSP6", "ATSP7", "ATSP8", "ATSP9",
            "ATSPA", "ATSPB", "ATSPC"
        )

        /** Non-CAN protocols — ATH1 is skipped for these. */
        private val NON_CAN_PROTOCOLS = setOf("ATSP1", "ATSP2", "ATSP3", "ATSP4", "ATSP5")
    }
}

class ELM327InitException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
