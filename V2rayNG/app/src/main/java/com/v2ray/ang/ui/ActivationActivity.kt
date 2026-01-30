package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityActivationBinding
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ActivationActivity : BaseActivity() {

    private lateinit var binding: ActivityActivationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // راه‌اندازی ViewBinding
        binding = ActivityActivationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // مخفی کردن نوار عنوان برای زیبایی بیشتر
        supportActionBar?.hide()

        // تنظیم عملکرد دکمه
        binding.btnSubmit.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            if (token.isNotEmpty()) {
                // مخفی کردن ارور قبلی
                binding.tvError.visibility = View.GONE
                checkTokenOnServer(token)
            } else {
                showError(getString(R.string.activation_msg_empty))
            }
        }

        // حذف پیام ارور وقتی کاربر شروع به تایپ می‌کند
        binding.etToken.setOnKeyListener { _, _, _ ->
            binding.tvError.visibility = View.GONE
            false
        }
    }

    private fun checkTokenOnServer(token: String) {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val baseUrl = getString(R.string.activation_base_url)
        
        // --- بخش اصلاح شده برای جلوگیری از مشکل آدرس ---
        // بررسی می‌کند که آیا آدرس خودش علامت سوال دارد یا خیر
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val fullUrl = "$baseUrl${separator}token=$token&hwid=$androidId"
        // -----------------------------------------------

        // چاپ آدرس در لاگ برای دیباگ (در پایین اندروید استودیو قابل مشاهده است)
        android.util.Log.d("V2RayLicense", "Requesting: $fullUrl")

        setLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(fullUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000 // ۱۵ ثانیه زمان انتظار
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "V2RayManager")

                val responseCode = conn.responseCode
                android.util.Log.d("V2RayLicense", "Response Code: $responseCode")

                // خواندن پاسخ سرور (چه موفق، چه خطا)
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }?.trim() ?: ""
                
                android.util.Log.d("V2RayLicense", "Response Text: $responseText")

                withContext(Dispatchers.Main) {
                    setLoading(false)
                    
                    if (responseCode == 200 && responseText == "OK") {
                        // --- موفقیت ---
                        MmkvManager.setAppActivated(true, token)
                        Toast.makeText(this@ActivationActivity, R.string.activation_msg_success, Toast.LENGTH_LONG).show()
                        
                        // رفتن به صفحه اصلی
                        startActivity(Intent(this@ActivationActivity, MainActivity::class.java))
                        finish()
                    } else {
                        // --- مدیریت خطاها ---
                        val msg = when {
                            responseCode == 404 -> "خطای ۴۰۴: آدرس API در سرور پیدا نشد."
                            responseText == "INVALID_TOKEN" -> getString(R.string.activation_msg_invalid)
                            responseText == "ALREADY_USED" -> getString(R.string.activation_msg_used)
                            responseText == "MISSING_PARAMS" -> "اطلاعات ناقص ارسال شد."
                            else -> "خطای سرور ($responseCode): $responseText"
                        }
                        showError(msg)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("V2RayLicense", "Error: ${e.message}")

                withContext(Dispatchers.Main) {
                    setLoading(false)
                    // نمایش خطای دقیق تکنیکال (مثل قطعی اینترنت یا SSL)
                    showError("Connection Error: ${e.message}")
                }
            }
        }
    }

    // تابع کمکی برای نمایش خطا در TextView قرمز رنگ
    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    // تابع کمکی برای تغییر وضعیت دکمه هنگام لودینگ
    private fun setLoading(isLoading: Boolean) {
        binding.btnSubmit.isEnabled = !isLoading
        binding.etToken.isEnabled = !isLoading
        binding.btnSubmit.text = if (isLoading) getString(R.string.activation_checking) else getString(R.string.activation_btn)
    }
    
    // جلوگیری از بسته شدن برنامه با دکمه برگشت
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}