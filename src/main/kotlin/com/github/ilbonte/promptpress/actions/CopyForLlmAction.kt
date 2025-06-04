package com.github.ilbonte.promptpress.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class CopyForLlmAction : AnAction("Copy for LLM") {

    private val log = Logger.getInstance(CopyForLlmAction::class.java)
    private val notificationGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("CopyForLLM")

    override fun update(e: AnActionEvent) {
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = vf?.isDirectory == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val dir = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val tool = detectTool() ?: run {
            notify("Neither Repomix nor Git-Ingest found in PATH.", NotificationType.ERROR, project)
            return
        }

        val cmd = when (tool) {
            "repomix"   -> listOf(tool, "--copy", "--stdout")
            "git-ingest", "gitingest" -> listOf(tool, "--copy", "--stdout")
            else -> listOf(tool, "--copy")
        }

        try {
            val process = ProcessBuilder(cmd)
                .directory(File(dir.path))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(120, TimeUnit.SECONDS)

            if (process.exitValue() == 0) {
                // se l’external tool non ha già copiato, ci pensiamo noi
                Toolkit.getDefaultToolkit().systemClipboard.setContents(
                    StringSelection(output), null
                )
                notify("Content copied to clipboard for LLM 🎉", NotificationType.INFORMATION, project)
            } else {
                notify("Copy for LLM failed:\n$output", NotificationType.ERROR, project)
            }
        } catch (ex: IOException) {
            log.warn(ex)
            notify("Unable to run $tool: ${ex.message}", NotificationType.ERROR, project)
        }
    }

    /** Verifica quale dei tool è disponibile in PATH */
    private fun detectTool(): String? =
        listOf("repomix", "git-ingest", "gitingest").firstOrNull { isOnPath(it) }

    private fun isOnPath(cmd: String): Boolean =
        try {
            ProcessBuilder(cmd, "--version")
                .redirectErrorStream(true)
                .start()
                .waitFor(3, TimeUnit.SECONDS)
        } catch (_: IOException) {
            false
        }

    private fun notify(msg: String, type: NotificationType, project: com.intellij.openapi.project.Project?) {
        notificationGroup.createNotification(msg, type).notify(project)
    }
}
