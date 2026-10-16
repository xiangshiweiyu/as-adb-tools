// =====================================================================
// IDE 退出生命周期监听
// 关闭编译器（Android Studio）时，若 ADB 工具窗口仍处于展示状态，
// 由插件主动关闭窗口内容，触发 MainPresenter.dispose() 停止 adb 进程与 logcat 抓取。
// =====================================================================

package com.asadb.tools

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 监听 IDE 退出事件：主动关闭 ADB 工具窗口并释放资源。
 */
class AdbAppLifecycleListener : AppLifecycleListener {

    override fun appWillBeClosed(restart: Boolean) {
        // 遍历所有已打开的项目，若 ADB 工具窗口存在内容（无论当前是否可见），
        // 主动移除全部内容，触发 Disposer 链上注册的 presenter.dispose()
        ProjectManager.getInstance().openProjects.forEach { project ->
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(AdbToolWindowFactory.TOOL_WINDOW_ID)
                ?: return@forEach
            if (toolWindow.isVisible || toolWindow.contentManager.contentCount > 0) {
                toolWindow.contentManager.removeAllContents(true)
            }
        }
    }
}
