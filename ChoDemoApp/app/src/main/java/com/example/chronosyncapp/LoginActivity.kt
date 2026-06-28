package com.example.chronosyncapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.chronosyncapp.data.AppDatabase
import com.example.chronosyncapp.data.UserEntity
import com.example.chronosyncapp.util.PasswordUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var toggleModeText: TextView
    private lateinit var errorText: TextView
    private lateinit var subtitle: TextView

    private var isLoginMode = true
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        db = AppDatabase.get(this)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        toggleModeText = findViewById(R.id.toggleModeText)
        errorText = findViewById(R.id.loginErrorText)
        subtitle = findViewById(R.id.loginSubtitle)

        loginButton.setOnClickListener {
            val username = usernameInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString()?.trim().orEmpty()
            if (username.isBlank() || password.isBlank()) {
                showError("请输入用户名和密码")
                return@setOnClickListener
            }
            if (password.length < 3) {
                showError("密码至少3位")
                return@setOnClickListener
            }
            if (isLoginMode) doLogin(username, password)
            else doRegister(username, password)
        }

        toggleModeText.setOnClickListener {
            isLoginMode = !isLoginMode
            updateModeUI()
        }
    }

    private fun updateModeUI() {
        if (isLoginMode) {
            loginButton.text = "登录"
            toggleModeText.text = "还没有账号？点击注册"
            subtitle.text = "智能日程助手"
        } else {
            loginButton.text = "注册"
            toggleModeText.text = "已有账号？返回登录"
            subtitle.text = "创建新账号"
        }
        errorText.visibility = View.GONE
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = View.VISIBLE
    }

    private fun doLogin(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val user = db.userDao().findByUsername(username)
            if (user == null) {
                withContext(Dispatchers.Main) { showError("用户不存在") }
                return@launch
            }
            if (!PasswordUtil.verify(password, user.passwordHash)) {
                withContext(Dispatchers.Main) { showError("密码错误") }
                return@launch
            }
            loginSuccess(user)
        }
    }

    private fun doRegister(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val existing = db.userDao().findByUsername(username)
            if (existing != null) {
                withContext(Dispatchers.Main) { showError("用户名已被占用") }
                return@launch
            }
            // 第一个注册的用户自动成为管理员
            val role = if (db.userDao().count() == 0) "admin" else "user"
            val hash = PasswordUtil.hash(password)
            val user = UserEntity(username, hash, role)
            val id = db.userDao().insert(user)
            user.id = id
            withContext(Dispatchers.Main) { loginSuccess(user) }
        }
    }

    private fun loginSuccess(user: UserEntity) {
        ChronoSyncApp.setCurrentUser(user.id, user.username, user.role)
        val data = Intent()
        data.putExtra("userId", user.id)
        data.putExtra("username", user.username)
        data.putExtra("role", user.role)
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val REQUEST_CODE = 1001
    }
}
