package com.example.chronosyncapp.LynxCard

import android.content.Context
import com.lynx.tasm.provider.AbsTemplateProvider
import java.io.ByteArrayOutputStream
import java.io.IOException

class DemoTemplateProvider(context: Context) : AbsTemplateProvider() {

    private val mContext: Context = context.applicationContext

    override fun loadTemplate(url: String, callback: Callback) {
        // 在子线程中执行文件读取操作
        Thread {
            try {
                mContext.assets.open(url).use { inputStream ->
                    val result = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } != -1) {
                        result.write(buffer, 0, length)
                    }
                    callback.onSuccess(result.toByteArray())
                }
            } catch (e: IOException) {
                callback.onFailed(e.message)
            }
        }.start()
    }
}
