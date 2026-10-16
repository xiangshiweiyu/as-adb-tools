// =====================================================================
// 打开 ADB 工具窗口的菜单入口（Actions 层）
// 点击 Tools → 打开 ADB 工具 时显示工具窗口；
// 窗口内容采用平台懒加载机制，首次显示时才创建主面板并初始化。
// =====================================================================

package com.asadb.tools

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 「打开 ADB 工具」菜单动作：显示 ADB 工具窗口（首次点击时才创建内容）。
 */
class OpenAdbToolsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ToolWindowManager.getInstance(project)
            .getToolWindow(AdbToolWindowFactory.TOOL_WINDOW_ID)
            ?.show()
    }

    override fun update(e: AnActionEvent) {
        // 仅在项目上下文可用（无项目时隐藏菜单项）
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
