package com.example.chronosyncapp

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.example.chronosyncapp.network.ChronoSyncApi
import com.facebook.drawee.backends.pipeline.Fresco
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
//Lynx导入
//import zcom.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.lynx.tasm.LynxEnv
import com.lynx.service.image.LynxImageService
import com.lynx.service.log.LynxLogService
import com.lynx.service.http.LynxHttpService
import com.lynx.tasm.service.LynxServiceCenter

class ChronoSyncApp : Application() {

	companion object {
		lateinit var instance: ChronoSyncApp
			private set

		lateinit var prefs: SharedPreferences
			private set

		lateinit var httpClient: OkHttpClient
			private set

		lateinit var chronoSyncApi: ChronoSyncApi
			private set

		// ===== 本地用户管理（方案A） =====
		fun getCurrentUserId(): Long = prefs.getLong("currentUserId", -1)
		fun getCurrentUsername(): String = prefs.getString("currentUsername", "") ?: ""
		fun getCurrentRole(): String = prefs.getString("currentRole", "user") ?: "user"
		fun isLoggedIn(): Boolean = getCurrentUserId() > 0
		fun isAdmin(): Boolean = getCurrentRole() == "admin"

		fun setCurrentUser(userId: Long, username: String, role: String) {
			prefs.edit()
				.putLong("currentUserId", userId)
				.putString("currentUsername", username)
				.putString("currentRole", role)
				.apply()
		}

		fun logout() {
			prefs.edit()
				.remove("currentUserId")
				.remove("currentUsername")
				.remove("currentRole")
				.apply()
		}
	}

	override fun onCreate() {
		super.onCreate()
		instance = this
		Log.d("ChronoSyncApp", "Application started")

		// 全局 SharedPreferences
		prefs = getSharedPreferences("chronosync_prefs", MODE_PRIVATE)

		// 全局 OkHttpClient
		httpClient = OkHttpClient.Builder()
			.connectTimeout(12, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(12, TimeUnit.SECONDS)
			.build()

		// 全局 ChronoSync API 客户端
		val url = BuildConfig.CHRONOSYNC_BASE_URL.ifBlank { "http://115.190.155.26:8000" }
		chronoSyncApi = ChronoSyncApi(httpClient, url)


        //初始化Lynx引擎
        initLynxService()
        initLynxEnv()


    }
    private fun initLynxService() {
        // 初始化 Fresco
        val config = ImagePipelineConfig.newBuilder(this).build()
        Fresco.initialize(this, config)

        // 注册 Lynx 提供的标准服务
        LynxServiceCenter.inst().registerService(LynxImageService.getInstance())
        LynxServiceCenter.inst().registerService(LynxLogService)
        LynxServiceCenter.inst().registerService(LynxHttpService)
    }

    private fun initLynxEnv() {
        LynxEnv.inst().init(
            this,      // Application Context
            null,      // 自定义 so 加载器，为 null 则使用系统默认
            null,      // 全局 AppBundle 加载器，为 null 则每个 LynxView 单独处理
            null       // 自定义组件列表，为 null 则不加载
        )
    }
}
