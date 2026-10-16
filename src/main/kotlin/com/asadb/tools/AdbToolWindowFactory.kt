// =====================================================================
// 工具窗口工厂（入口）
// 对应 C# 版组合根（Program）：装配 MVP 三层依赖并把主面板挂入 IDE 工具窗口。
// =====================================================================

package com.asadb.tools

import com.asadb.tools.model.AdbService
import com.asadb.tools.model.LaunchItemStore
import com.asadb.tools.presenter.MainPresenter
import com.asadb.tools.view.MainPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * ADB 工具窗口工厂：创建主面板，注入依赖并初始化。
 */
class AdbToolWindowFactory : ToolWindowFactory {

    companion object {
        /** 工具窗口 ID（plugin.xml 的 toolWindow id 与此保持一致）。 */
        const val TOOL_WINDOW_ID = "ADB 工具"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 防重复：工具窗口已存在内容时不再重复创建（避免每次启动/重载项目叠加多个面板）
        if (toolWindow.contentManager.contentCount > 0) return

        val panel = MainPanel(project)

        // 组合根装配：View（主面板）← Presenter ← Model（AdbService + 启动项存储）
        val presenter = MainPresenter(panel, AdbService(), LaunchItemStore())
        panel.attachPresenter(presenter)

        val content = ContentFactory.getInstance().createContent(panel, "", false)

        // 工具窗口关闭时释放全部异步订阅
        Disposer.register(content, Disposable { presenter.dispose() })

        toolWindow.contentManager.addContent(content)
        panel.initialize()
    }
}
