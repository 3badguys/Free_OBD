package com.freeobd.app.data.remote

import kotlinx.coroutines.delay

/**
 * Executes the ELM327 initialization sequence using raw AT commands.
 *
 * Standard init sequence:
 * 1. ATZ  — Reset adapter (requires ~1.5s wait)
 * 2. ATE0 — Disable command echo
 * 3. ATL0 — Disable line feeds
 * 4. ATSPx — Protocol selection (default: ATSP0 = auto-detect)
 * 5. ATH1 — Enable CAN headers
 * 6. ATSH — Set CAN header address (optional)
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

        // Step 1: Reset adapter (long delay for full reset)
        steps.add(InitStep("ATZ (reset)", "ATZ", 1500L))

        // Step 2: Disable echo
        steps.add(InitStep("ATE0 (disable echo)", "ATE0", 200L))

        // Step 3: Disable line feed
        steps.add(InitStep("ATL0 (disable line feed)", "ATL0", 200L))

        // Step 4: Select protocol
        val proto = if (protocol in VALID_PROTOCOLS) protocol else "ATSP0"
        steps.add(
            InitStep(
                "$proto (protocol select)",
                proto,
                if (proto == "ATSP0") 500L else 300L
            )
        )

        // Step 5: Enable headers
        steps.add(InitStep("ATH1 (enable headers)", "ATH1", 200L))

        // Step 6 (optional): Set ECU address
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
    }
}

class ELM327InitException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
