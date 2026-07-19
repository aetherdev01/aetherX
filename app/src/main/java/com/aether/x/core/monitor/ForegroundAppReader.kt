package com.aether.x.core.monitor

import com.aether.x.core.shell.ShellExecutor

class ForegroundAppReader {

    suspend fun readForegroundPackage(executor: ShellExecutor): String? {
        readFromActivities(executor)?.let { return it }
        readFromWindow(executor)?.let { return it }
        return null
    }

    private suspend fun readFromActivities(executor: ShellExecutor): String? {
        val result = executor.exec("dumpsys activity activities")
        if (!result.success) return null
        return parseResumedPackage(result.output)
    }

    private suspend fun readFromWindow(executor: ShellExecutor): String? {
        val result = executor.exec("dumpsys window")
        if (!result.success) return null
        return parseFocusedWindowPackage(result.output)
    }

    companion object {

        private val RESUMED_ACTIVITY_REGEX =
            Regex("""[Rr]esumedActivity:\s*ActivityRecord\{[^ ]+\s+[^ ]+\s+([a-zA-Z0-9_.]+)/""")

        private val FOCUSED_WINDOW_REGEX =
            Regex("""m(CurrentFocus|FocusedApp)=\w*\{[^ ]+\s+([a-zA-Z0-9_.]+)/""")

        internal fun parseResumedPackage(lines: List<String>): String? {
            for (line in lines) {
                RESUMED_ACTIVITY_REGEX.find(line)?.let { match ->
                    return match.groupValues[1].takeIf { it.isNotBlank() }
                }
            }
            return null
        }

        internal fun parseFocusedWindowPackage(lines: List<String>): String? {
            for (line in lines) {
                FOCUSED_WINDOW_REGEX.find(line)?.let { match ->
                    return match.groupValues[2].takeIf { it.isNotBlank() }
                }
            }
            return null
        }
    }
}
