// =====================================================================
// 终端输出适配器（View 层）
// 将插件的运行输出（连接信息 / 命令结果 / 错误提示）重定向到
// Android Studio 内置的 Terminal 工具窗口，插件自身不再绘制信息展示窗。
// 依赖内置插件 org.jetbrains.plugins.terminal（AS 自带）。
// =====================================================================

package com.asadb.tools.view

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalView
import javax.swing.SwingUtilities

/**
 * 输出到 Android Studio 内置 Terminal 的适配器：
 * 在 Terminal 工具窗口中维护一个专用的「ADB Tools」标签页，
 * [writeOutput] / [writeError] 把文本直接写入该标签页（仅显示、不执行），
 * [openTerminal] 负责激活工具窗口并选中该标签页。
 */
class TerminalOutput(private val project: Project) {

    companion object {
        private const val TAB_NAME = "ADB Tools"
        private const val TOOL_WINDOW_ID = "Terminal"
    }

    private var widget: ShellTerminalWidget? = null

    /** 打开 / 激活内置 Terminal 并选中「ADB Tools」标签页（显式调用，始终打开）。 */
    fun openTerminal() = onEdt {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
            ?: return@onEdt
        openTerminalInternal(toolWindow)
    }

    /** 若 Terminal 尚未打开则自动打开；已打开时不重复启动、不抢占焦点。 */
    fun openIfNeeded() = onEdt {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
            ?: return@onEdt
        if (toolWindow.isVisible) return@onEdt
        openTerminalInternal(toolWindow)
    }

    /** 打开工具窗口并选中「ADB Tools」标签页；标签页已被关闭时自动重建。 */
    private fun openTerminalInternal(toolWindow: ToolWindow) {
        toolWindow.show()
        toolWindow.activate(null, true)

        var w = getOrCreateWidget() ?: return
        // 用户可能已手动关闭该标签页（内容已被移除），此时需重建
        if (!isAlive(toolWindow, w)) {
            widget = null
            w = getOrCreateWidget() ?: return
        }
        selectContentOf(toolWindow, w)
    }

    /** 将普通输出文本写入终端（仅显示，不执行）。 */
    fun writeOutput(text: String) = onEdt {
        writeToTerminal(text)
    }

    /** 将错误提示写入终端（附加 [ERROR] 前缀，便于区分）。 */
    fun writeError(text: String) = onEdt {
        writeToTerminal("[ERROR] $text")
    }

    /** 获取或创建「ADB Tools」终端标签页；创建失败（如终端插件不可用）返回 null。 */
    private fun getOrCreateWidget(): ShellTerminalWidget? {
        val cached = widget
        if (cached != null) return cached

        val created = try {
            TerminalView.getInstance(project)
                .createLocalShellWidget(project.basePath ?: System.getProperty("user.home"), TAB_NAME)
        } catch (e: Exception) {
            null
        } ?: return null
        widget = created
        return created
    }

    /** 向终端写入文本；若当前标签页已被销毁，则重建后重试一次。 */
    private fun writeToTerminal(text: String) {
        var w = getOrCreateWidget() ?: return
        try {
            w.writePlainMessage(text)
        } catch (e: Exception) {
            // 标签页可能已被用户关闭销毁，重建后重试一次
            widget = null
            w = getOrCreateWidget() ?: return
            try {
                w.writePlainMessage(text)
            } catch (ignored: Exception) {
            }
        }
    }

    /** 判断终端标签页是否仍存在于工具窗口中（关闭后其 content 会被移除）。 */
    private fun isAlive(toolWindow: ToolWindow, w: ShellTerminalWidget): Boolean =
        toolWindow.contentManager.contents.any { TerminalView.getWidgetByContent(it) === w }

    /** 在终端工具窗口中选中包含指定终端组件的标签页。 */
    private fun selectContentOf(toolWindow: ToolWindow, w: ShellTerminalWidget) {
        val cm = toolWindow.contentManager
        for (content in cm.contents) {
            if (TerminalView.getWidgetByContent(content) === w) {
                cm.setSelectedContent(content, true)
                return
            }
        }
    }

    /** 确保所有终端操作在 EDT 线程执行（创建 Swing 组件有线程要求）。 */
    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block()
        else SwingUtilities.invokeLater { block() }
    }
}
