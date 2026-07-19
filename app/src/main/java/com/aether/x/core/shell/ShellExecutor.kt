package com.aether.x.core.shell

data class ShellResult(
    val success: Boolean,
    val output: List<String> = emptyList(),
    val error: List<String> = emptyList(),
) {
    val outputText: String get() = output.joinToString("\n")
    val errorText: String get() = error.joinToString("\n")

    companion object {
        fun failure(message: String) = ShellResult(success = false, error = listOf(message))
    }
}

interface ShellExecutor {
    val backendName: String

    suspend fun exec(command: String): ShellResult
}
