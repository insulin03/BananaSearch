package com.banana.search

import android.app.Application
import com.banana.search.core.AppLog
import com.banana.search.core.QuestionBank

class QSearchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)

        Thread {
            try {
                val n = QuestionBank.load(this)
                AppLog.i("Bank", "题库载入完成：$n 题")
                val check = QuestionBank.selfCheck()
                AppLog.i(
                    "Bank",
                    "自检：平均接近率 ${(check.avgScore * 100).toInt()}%，最低 ${(check.worstScore * 100).toInt()}%（id=${check.worstId}）"
                )
            } catch (t: Throwable) {
                AppLog.e("Bank", "题库载入失败", t)
            }
        }.start()
    }
}
