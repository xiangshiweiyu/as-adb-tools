// =====================================================================
// EDT 调度器（工具）
// 将 RxJava 的 observeOn 结果调度回 Swing 事件分发线程（EDT），
// 保证所有 UI 更新都在界面线程执行，与 C# 版的 BackgroundWorker 回主线一致。
// =====================================================================

package com.asadb.tools.presenter

import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import java.awt.EventQueue
import java.util.concurrent.Executor

/**
 * 提供调度到 Swing EDT 的 RxJava 调度器。
 */
object EdtSchedulers {
    /** 调度到 Swing 事件分发线程（EDT）的调度器。 */
    val edt: Scheduler by lazy {
        Schedulers.from(Executor { runnable -> EventQueue.invokeLater(runnable) })
    }
}
