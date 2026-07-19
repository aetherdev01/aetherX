package com.aether.x.core.monitor

import com.aether.x.core.shell.ShellExecutor

class RecentTasksReader {

    suspend fun isPackageInRecentTasks(executor: ShellExecutor, packageName: String): Boolean? {
        val result = executor.exec("dumpsys activity activities")
        if (!result.success) return null
        return containsTaskForPackage(result.output, packageName)
    }

    suspend fun listRecentPackages(executor: ShellExecutor, excludingPackage: String? = null): List<String>? {
        val result = executor.exec("dumpsys activity activities")
        if (!result.success) return null
        return parseRecentPackagesInOrder(result.output)
            .filter { it != excludingPackage }
            .distinct()
    }

    companion object {

        private fun taskLineRegexFor(packageName: String): Regex {
            val escaped = Regex.escape(packageName)
            return Regex("""(A=$escaped\b)|(realActivity=$escaped/)""")
        }

        private val ANY_TASK_PACKAGE_REGEX =
            Regex("""(?:A=|realActivity=)([a-zA-Z0-9_][a-zA-Z0-9_.]*[a-zA-Z0-9_])(?:\s|/)""")

        internal fun containsTaskForPackage(lines: List<String>, packageName: String): Boolean {
            val regex = taskLineRegexFor(packageName)
            return lines.any { regex.containsMatchIn(it) }
        }

        internal fun parseRecentPackagesInOrder(lines: List<String>): List<String> {
            val result = mutableListOf<String>()
            for (line in lines) {
                ANY_TASK_PACKAGE_REGEX.find(line)?.let { match ->
                    val pkg = match.groupValues[1]
                    if (pkg.isNotBlank()) result.add(pkg)
                }
            }
            return result
        }
    }
}
