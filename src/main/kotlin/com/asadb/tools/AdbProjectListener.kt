// =====================================================================
// 项目打开生命周期监听
// 每次启动（项目打开）时不主动展示 ADB 工具窗口：
//   1) 工具窗口一经注册立即隐藏，抢在平台布局恢复（会恢复可见状态并触发内容懒加载）之前；
//   2) 延迟到事件循环末尾再兜底隐藏一次，防止平台恢复覆盖。
// 用户点击右侧工具窗口栏按钮或「Tools → 打开 ADB 工具」时才创建并显示窗口内容。
// =====================================================================

package com.asadb.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/**
 * 项目打开时隐藏 ADB 工具窗口，避免启动阶段创建内容与执行重量初始化。
 */
class AdbProjectListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        // 1) 窗口注册完成即刻隐藏：若平台随后恢复可见状态，仍由兜底隐藏兜住，
        //    但绝大多数场景下内容不会被预创建
        val connection = project.messageBus.connect()
        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowRegistered(id: String) {
                if (id == AdbToolWindowFactory.TOOL_WINDOW_ID) {
                    ToolWindowManager.getInstance(project).getToolWindow(id)?.hide()
                }
            }
        })
        Disposer.register(project, connection)

        // 2) 兜底：延迟到当前事件循环末尾再隐藏，确保平台已完成窗口布局/可见状态恢复后窗口不可见
        ApplicationManager.getApplication().invokeLater {
            ToolWindowManager.getInstance(project)
                .getToolWindow(AdbToolWindowFactory.TOOL_WINDOW_ID)
                ?.hide()
        }
    }
}
