// =====================================================================
// 主窗体视图聚合接口（View 层，接口隔离）
// 对应 C# 版 AdbTool.View.IMainView.cs。
// 聚合各窄接口作为主面板的完整视图契约，使 Presenter 门面只需面向一个清晰的视图抽象。
// =====================================================================

package com.asadb.tools.view

/**
 * 主窗体视图聚合接口：由 [MainPanel] 实现，
 * 聚合了信息输出、文件选择、用户输入、确认与日志抓取等窄接口。
 */
interface IMainView : IInfoOutput, IFileDialogProvider, IUserInputProvider,
    IConfirmationProvider, ILogCaptureView, ILaunchView, IAppManagementView, IFolderOpenProvider
