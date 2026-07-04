package com.freeobd.app.data.remote

import kotlinx.coroutines.delay

/**
 * Executes the ELM327 initialization sequence using raw AT commands.
 *
 * ATZ is skipped because many ELM327 clones reboot their entire adapter
 * (including the Bluetooth chip) on reset, breaking the connection.
 *
 * CAN bus protocol init:
 *   1. ATE0 — Disable command echo
 *   2. ATL0 — Disable line feeds
 *   3. ATSPx — Protocol selection
 *   4. ATH1 — Enable CAN headers (CAN only, skipped for K-line ATSP3/4/5)
 *   5. ATSH — Set CAN/K-line header address (optional)
 *
 * Each step has an appropriate post-command delay.
 */
class ELM327Initializer(
    private val commandQueue: ObdCommandQueue
) {
    suspend fun initialize(
        protocol: String = "ATSP0",
        ecuAddress: String? = null
    ): Result<Unit> {
        val steps = buildInitSteps(protocol, ecuAddress)

        steps.forEachIndexed { index, step ->
            val result = commandQueue.sendRaw(step.command)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                return Result.failure(
                    ELM327InitException(
                        "ELM327 init failed at step ${index + 1}/${steps.size} " +
                            "(${step.description}): ${error?.message ?: "unknown error"}",
                        error
                    )
                )
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

        // Step 0: Wake up — send a bare CR to trigger the adapter's prompt.
        steps.add(InitStep("wake-up", "\r", 200L))

        // Step 1: Disable echo
        steps.add(InitStep("ATE0 (disable echo)", "ATE0", 300L))

        // Step 2: Disable line feed
        steps.add(InitStep("ATL0 (disable line feed)", "ATL0", 200L))

        // Step 3: Select protocol
        val proto = if (protocol in VALID_PROTOCOLS) protocol else "ATSP0"
        steps.add(
            InitStep(
                "$proto (protocol select)",
                proto,
                if (proto == "ATSP0") 500L else 300L
            )
        )

        // Step 4: Enable CAN headers (CAN-only; skipped for K-line as K-line
        // doesn't have CAN-style headers and the adapter handles init automatically)
        if (proto !in KLINE_PROTOCOLS) {
            steps.add(InitStep("ATH1 (enable CAN headers)", "ATH1", 200L))
        }

        // Step 5 (optional): Set ECU address
        if (!ecuAddress.isNullOrBlank()) {
            steps.add(InitStep("ATSH $ecuAddress", "ATSH$ecuAddress", 200L))
        }

        return steps
    }

    private data class InitStep(
        val description: String,
        val command: String,
        val postDelayMs: Long
    )

    companion object {
        private val VALID_PROTOCOLS = setOf(
            "ATSP0", "ATSP1", "ATSP2", "ATSP3", "ATSP4",
            "ATSP5", "ATSP6", "ATSP7", "ATSP8", "ATSP9"
        )

        /** K-line based protocols (ISO 9141-2 / KWP2000). */
        private val KLINE_PROTOCOLS = setOf("ATSP3", "ATSP4", "ATSP5")
    }
}

class ELM327InitException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
