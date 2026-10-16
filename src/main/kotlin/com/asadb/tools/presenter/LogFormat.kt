// =====================================================================
// 日志抓取格式策略（Presenter 层，开闭原则）
// 对应 C# 版 AdbTool.Presenter.LogFormat.cs。
// 新增日志格式时只需增加一个 ILogFormat 实现并在工厂注册，
// 无需修改 LogPresenter 中已稳定的抓取逻辑。
// =====================================================================

package com.asadb.tools.presenter

/** 日志抓取格式策略：把 logcat 参数与展示标签封装为一个策略。 */
interface ILogFormat {
    /** logcat 的附加参数（如 -v time）。 */
    val arguments: String

    /** 用于界面提示的格式名称。 */
    val label: String
}

/** 按时间抓取：使用 -v time。 */
class TimeLogFormat : ILogFormat {
    override val arguments: String get() = "-v time"
    override val label: String get() = "按时间抓取"
}

/** 时间线程抓取：使用 -v threadtime。 */
class ThreadTimeLogFormat : ILogFormat {
    override val arguments: String get() = "-v threadtime"
    override val label: String get() = "时间线程抓取"
}

/** 日志格式工厂：根据视图提供的模式字符串创建对应策略。 */
object LogFormatFactory {
    /** 创建日志格式策略；未知模式回退为按时间抓取。 */
    fun create(mode: String): ILogFormat {
        return when (mode) {
            "threadtime" -> ThreadTimeLogFormat()
            else -> TimeLogFormat()
        }
    }
}
