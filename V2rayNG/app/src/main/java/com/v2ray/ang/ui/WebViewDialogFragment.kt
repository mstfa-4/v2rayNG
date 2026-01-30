package com.v2ray.ang.ui

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.v2ray.ang.R

/**
 * این کلاس یک اینترفیس برای ارتباط جاوا اسکریپت با کد نیتیو فراهم می‌کند
 */
class WebAppInterface(private val context: Context) {
    /**
     * این متد می‌تواند توسط جاوا اسکریپت فراخوانی شود تا متنی را در کلیپ‌بورد کپی کند.
     */
    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
        // این Toast از کد نیتیو نمایش داده می‌شود و نشان‌دهنده موفقیت‌آمیز بودن عملیات است
        Toast.makeText(context, "کپی شد!", Toast.LENGTH_SHORT).show()
    }
}


class WebViewDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_URL = "url"

        fun newInstance(url: String): WebViewDialogFragment {
            val fragment = WebViewDialogFragment()
            val args = Bundle()
            args.putString(ARG_URL, url)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_webview, container, false)
        val webView = view.findViewById<WebView>(R.id.webview)
        val url = arguments?.getString(ARG_URL) ?: ""

        // تنظیمات WebView
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true

        // افزودن اینترفیس برای ارتباط جاوا اسکریپت با کاتلین
        webView.addJavascriptInterface(WebAppInterface(requireContext()), "Android")

        // کد برای مدیریت دانلود
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
            request.setMimeType(mimetype)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("در حال دانلود فایل...")

            val fileName = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
            request.setTitle(fileName)

            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(requireContext(), "دانلود آغاز شد...", Toast.LENGTH_SHORT).show()
        }

        webView.loadUrl(url)
        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
