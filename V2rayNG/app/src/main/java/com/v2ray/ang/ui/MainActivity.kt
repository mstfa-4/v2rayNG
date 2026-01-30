package com.v2ray.ang.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayoutMediator
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null
	
	/ تعریف انیماتور در سطح کلاس
    private var fabAnimator: ObjectAnimator? = null

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            setupGroupTab()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
		
		// در ابتدای onCreate
		if (!MmkvManager.isAppActivated()) {
			startActivity(Intent(this, ActivationActivity::class.java))
			finish()
			return
		}
		
        setContentView(binding.root)
        setupToolbar(binding.toolbar, false, getString(R.string.title_server))
		
		// در انتهای onCreate و در متد onNewIntent
		intent.getStringExtra("openUrl")?.let { url ->
			if (url.startsWith("http")) {
				try {
					val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
					startActivity(browserIntent)
				} catch (e: Exception) {
					// خطا در باز کردن لینک
				}
			}
		}
		
		binding.fab.setOnClickListener {
            // منطق کلیک بر اساس حالت جدید
            when (mainViewModel.vpnState.value) {
                VpnState.CONNECTED -> {
                    V2RayServiceManager.stopVService(this)
                }
                VpnState.DISCONNECTED -> {
                    if (mainViewModel.serversCache.count() > 0) {
                        mainViewModel.testAllRealPing()
                    } else {
                        toast("هیچ سروری برای تست وجود ندارد. لطفاً لیست را به‌روزرسانی کنید.")
                    }
                }
                VpnState.TESTING -> {
                    toast("در حال تست سرورها، لطفاً صبر کنید...")
                    // در حالت تست، کاری انجام نده
                }
                else -> {
                    // برای حالت null
                }
            }
        }
        binding.layoutTest.setOnClickListener {
            if (mainViewModel.vpnState.value == VpnState.CONNECTED) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            }
        }

        // setup viewpager and tablayout
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.adapter = groupPagerAdapter
        binding.viewPager.isUserInputEnabled = true

        // setup navigation drawer
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        binding.fab.setOnClickListener { handleFabAction() }
        binding.layoutTest.setOnClickListener { handleLayoutTestClick() }
		
		addMustafaSubscription()
        setupGroupTab()
        setupViewModel()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
    }
	
	override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Process openUrl from new Intent
        intent.getStringExtra("openUrl")?.let { url ->
            if (url.startsWith("http")) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    toast("Cannot open URL: ${e.message}")
                }
            }
        }
    }
	
	private fun addMustafaSubscription() {
        val appName = getString(R.string.app_name)
        // val mustafaUrl = "https://raw.githubusercontent.com/mustafa137608064/subdr/refs/heads/main/users/$appName.php"
        val mustafaUrl = "https://fervent-hamilton-zwkjnlgwo.storage.c2.liara.space/$appName.txt"
        val existingSubscriptions = MmkvManager.decodeSubscriptions()
        if (existingSubscriptions.none { it.second.url == mustafaUrl }) {
            val subscriptionId = Utils.getUuid()
            val subscriptionItem = SubscriptionItem(
                remarks = "$appName Subscription",
                url = mustafaUrl,
                enabled = true
            )
            MmkvManager.encodeSubscription(subscriptionId, subscriptionItem)
            Log.d(AppConfig.TAG, "Added $appName subscription with ID: $subscriptionId")
        } else {
            Log.d(AppConfig.TAG, "$appName subscription already exists")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }

        mainViewModel.vpnState.observe(this) { state ->
            adapter.isRunning = (state == VpnState.CONNECTED)
            binding.fab.isEnabled = true
            
            // توقف و ریست انیمیشن قبلی
            fabAnimator?.cancel()
            binding.fab.rotation = 0f

            when (state) {
                VpnState.DISCONNECTED -> {
                    binding.fab.setImageResource(R.drawable.ic_play_24dp)
                    binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_inactive))
					binding.fab.contentDescription = getString(R.string.tasker_start_service)
                    setTestState(getString(R.string.connection_not_connected))
                    binding.layoutTest.isFocusable = false
                }
                VpnState.TESTING -> {
                    binding.fab.setImageResource(R.drawable.ic_sync_24dp)
                    binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_testing))
                    setTestState(getString(R.string.connection_standby))
                    binding.fab.isEnabled = false
                    
                    // شروع انیمیشن چرخش
                    fabAnimator = ObjectAnimator.ofFloat(binding.fab, "rotation", 0f, 360f).apply {
                        duration = 1000
                        repeatCount = ValueAnimator.INFINITE
                        interpolator = LinearInterpolator()
                        start()
                    }
                }
                VpnState.CONNECTED -> {
                    binding.fab.setImageResource(R.drawable.ic_stop_24dp)
                    binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_active))
					binding.fab.contentDescription = getString(R.string.action_stop_service)
                    setTestState(getString(R.string.connection_connected))
                    binding.layoutTest.isFocusable = true
                }
                else -> {}
            }
        }

        mainViewModel.startVpnConnection.observe(this) {
            if (it == true) {
                connectToVpn()
                mainViewModel.startVpnConnection.value = false
            }
        }

        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupPagerAdapter.update(groups)

        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
            groupPagerAdapter.groups.getOrNull(position)?.let {
                tab.text = it.remarks
                tab.tag = it.id
            }
        }.also { it.attach() }

        val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }.takeIf { it >= 0 } ?: (groups.size - 1)
        binding.viewPager.setCurrentItem(targetIndex, false)

        binding.tabGroup.isVisible = groups.size > 1
    }

    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.isRunning.value == true) {
            setTestState(getString(R.string.connection_test_testing))
            mainViewModel.testCurrentServerRealPing()
        } else {
            // service not running: keep existing no-op (could show a message if desired)
        }
    }
	
	private fun connectToVpn() {
        val selectedServer = MmkvManager.getSelectServer()
        if (selectedServer.isNullOrEmpty()) {
            toast("سروری برای اتصال انتخاب نشده است.")
            mainViewModel.vpnState.postValue(VpnState.DISCONNECTED)
            return
        }

        if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                lifecycleScope.launch { startV2Ray() }
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            lifecycleScope.launch { startV2Ray() }
        }
    }
	
    private suspend fun startV2Ray() {
        val selectedServer = MmkvManager.getSelectServer()
        if (selectedServer.isNullOrEmpty()) {
            toast("لطفاً یک سرور را انتخاب کنید یا منتظر اتمام به‌روزرسانی بمانید")
            mainViewModel.vpnState.postValue(VpnState.DISCONNECTED)
            return
        }
        try {
            if (mainViewModel.vpnState.value == VpnState.CONNECTED || isServiceRunning(this, "com.v2ray.ang.service.V2RayVpnService")) {
                V2RayServiceManager.stopVService(this)
                delay(0)
                if (isServiceRunning(this, "com.v2ray.ang.service.V2RayVpnService")) {
                    toastError("سرویس هنوز در حال اجرا است، لطفاً دوباره تلاش کنید")
                    return
                }
            }
            V2RayServiceManager.startVService(this)
        } catch (e: Exception) {
            toastError("خطا در شروع سرویس VPN: ${e.message}")
            Log.e(AppConfig.TAG, "Failed to start V2Ray service", e)
        }
    }

    private fun restartV2Ray() {
        if (mainViewModel.vpnState.value == VpnState.CONNECTED || isServiceRunning(this, "com.v2ray.ang.service.V2RayVpnService")) {
            V2RayServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            connectToVpn()
        }
    }
	
	private fun updateServerList() {
        binding.pbWaiting.show()
        isUpdatingServers = true
        binding.fab.isEnabled = false

        Api.fetchAllSubscriptions()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ configsList ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val newServers = mutableListOf<String>()
                        configsList.forEach { config ->
                            val (count, countSub) = AngConfigManager.importBatchConfig(config, mainViewModel.subscriptionId, false)
                            if (count > 0 || countSub > 0) {
                                newServers.add(config)
                                Log.d(AppConfig.TAG, "Imported $count servers and $countSub subscriptions")
                            }
                        }
                        if (newServers.isNotEmpty()) {
                            mainViewModel.removeAllServer()
                            newServers.forEach { config ->
                                AngConfigManager.importBatchConfig(config, mainViewModel.subscriptionId, true)
                            }
                            withContext(Dispatchers.Main) {
                                toast(getString(R.string.title_import_config_count, newServers.size))
                                mainViewModel.reloadServerList()
                                initGroupTab()
                                // تست خودکار پینگ حذف شد
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                toastError("خطا: بروزرسانی انجام نشد")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            toastError("خطا در وارد کردن سرورها: ${e.message}")
                            Log.e(AppConfig.TAG, "Failed to import configs", e)
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            binding.pbWaiting.hide()
                            isUpdatingServers = false
                            binding.fab.isEnabled = true
                        }
                    }
                }
            }, { error ->
                toastError("خطا در دریافت سرورها: ${error.message}")
                Log.e(AppConfig.TAG, "Error fetching subscriptions: ${error.message}", error)
                binding.pbWaiting.hide()
                isUpdatingServers = false
                binding.fab.isEnabled = true
            })
            .let { disposables.add(it) }
    }
	
	private fun importConfigViaSub(): Boolean {
        try {
            toast(R.string.title_sub_update)
            MmkvManager.decodeSubscriptions().forEach {
                if (TextUtils.isEmpty(it.first) || TextUtils.isEmpty(it.second.remarks) || TextUtils.isEmpty(it.second.url)) {
                    return@forEach
                }
                if (!it.second.enabled) {
                    return@forEach
                }
                val url = Utils.idnToASCII(it.second.url)
                if (!Utils.isValidUrl(url)) {
                    toastError("URL نامعتبر: ${it.second.remarks}")
                    return@forEach
                }
                Log.d(AppConfig.TAG, "Fetching subscription: $url")
                lifecycleScope.launch(Dispatchers.IO) {
                    val configText = try {
                        Utils.getUrlContentWithCustomUserAgent(url)
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            toastError("\"${it.second.remarks}\" ${getString(R.string.toast_failure)}: ${e.message}")
                        }
                        Log.e(AppConfig.TAG, "Failed to fetch subscription $url: ${e.message}", e)
                        return@launch
                    }
                    try {
                        val (count, countSub) = AngConfigManager.importBatchConfig(configText, it.first, true)
                        launch(Dispatchers.Main) {
                            if (count > 0 || countSub > 0) {
                                toast(getString(R.string.title_import_config_count, count))
                                mainViewModel.reloadServerList()
                                initGroupTab()
                            } else {
                                toastError("هیچ سروری از ${it.second.remarks} وارد نشد")
                            }
                        }
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            toastError("خطا در وارد کردن سرورها از ${it.second.remarks}: ${e.message}")
                        }
                        Log.e(AppConfig.TAG, "Failed to import configs from $url: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            toastError("خطا در به‌روزرسانی ساب‌اسکریپشن‌ها: ${e.message}")
            Log.e(AppConfig.TAG, "Error updating subscriptions", e)
            return false
        }
        return true
    }
	
    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    private  fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        if (isLoading) {
            binding.fab.setImageResource(R.drawable.ic_fab_check)
            return
        }

        if (isRunning) {
            binding.fab.setImageResource(R.drawable.ic_stop_24dp)
            binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_active))
            binding.fab.contentDescription = getString(R.string.action_stop_service)
            setTestState(getString(R.string.connection_connected))
            binding.layoutTest.isFocusable = true
        } else {
            binding.fab.setImageResource(R.drawable.ic_play_24dp)
            binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_inactive))
            binding.fab.contentDescription = getString(R.string.tasker_start_service)
            setTestState(getString(R.string.connection_not_connected))
            binding.layoutTest.isFocusable = false
        }
    }

    override fun onResume() {
        super.onResume()
		mainViewModel.reloadServerList()
    }

    override fun onPause() {
        super.onPause()
    }
	
	override fun onStart() {
        super.onStart()
        if (isServiceRunning(this, "com.v2ray.ang.service.V2RayVpnService")) {
            V2RayServiceManager.stopVService(this)
            lifecycleScope.launch {
                delay(0)
                mainViewModel.vpnState.value = VpnState.DISCONNECTED
            }
        }
        updateServerList()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    mainViewModel.filterConfig(newText.orEmpty())
                    return false
                }
            })

            searchView.setOnCloseListener {
                mainViewModel.filterConfig("")
                false
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode()
            true
        }

        R.id.import_clipboard -> {
            importClipboard()
            true
        }

        R.id.import_local -> {
            importConfigLocal()
            true
        }

        R.id.import_manually_policy_group -> {
            importManually(EConfigType.POLICYGROUP.value)
            true
        }

        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }

        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }

        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }

        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }

        R.id.import_manually_http -> {
            importManually(EConfigType.HTTP.value)
            true
        }

        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }

        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value)
            true
        }

        R.id.import_manually_hysteria2 -> {
            importManually(EConfigType.HYSTERIA2.value)
            true
        }

        R.id.export_all -> {
            exportAll()
            true
        }

        R.id.ping_all -> {
            if (mainViewModel.vpnState.value == VpnState.TESTING) {
                toast("تست پینگ در حال انجام است.")
            } else {
                toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
                mainViewModel.testAllTcping() // فرض بر اینکه این متد هم برای تست دستی استفاده می‌شود
            }
            true
        }

        R.id.real_ping_all -> {
            if (mainViewModel.vpnState.value == VpnState.TESTING) {
                toast("تست پینگ در حال انجام است.")
            } else {
                toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
                mainViewModel.testAllRealPing()
            }
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            delAllConfig()
            true
        }

        R.id.del_duplicate_config -> {
            delDuplicateConfig()
            true
        }

        R.id.del_invalid_config -> {
            delInvalidConfig()
            true
        }

        R.id.sort_by_test_results -> {
            sortByTestResults()
            true
        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }
		
		R.id.refresh_servers -> {
            if (isUpdatingServers) {
                toast("در حال به‌روزرسانی سرورها، لطفاً صبر کنید")
                true
            } else {
                updateServerList()
                true
            }
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType: Int) {
        if (createConfigType == EConfigType.POLICYGROUP.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
        } else {
            startActivity(
                Intent()
                    .putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerActivity::class.java)
            )
        }
    }

    /**
     * import config from qrcode
     */
    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importBatchConfig(scanResult)
            }
        }
        return true
    }

    /**
     * import config from clipboard
     */
    private fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?) {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                        }

                        countSub > 0 -> setupGroupTab()
                        else -> toastError(R.string.toast_failure)
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    hideLoading()
                }
                Log.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    /**
     * import config from local config file
     */
    private fun importConfigLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to import config from local file", e)
            return false
        }
        return true
    }


    /**
     * import config from sub
     */
    private fun importConfigViaSub(): Boolean {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            val count = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (count > 0) {
                    toast(getString(R.string.title_update_config_count, count))
                    mainViewModel.reloadServerList()
                } else {
                    toastError(R.string.toast_failure)
                }
                hideLoading()
            }
        }
        return true
    }

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            launch(Dispatchers.Main) {
                if (ret > 0)
                    toast(getString(R.string.title_export_config_count, ret))
                else
                    toastError(R.string.toast_failure)
                hideLoading()
            }
        }
    }

    private fun delAllConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeAllServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delDuplicateConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeDuplicateServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_duplicate_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delInvalidConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_invalid_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeInvalidServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun sortByTestResults() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                hideLoading()
            }
        }
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                importBatchConfig(input?.bufferedReader()?.readText())
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to read content from URI", e)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_check_update -> {
                val appName = getString(R.string.app_name)
                val updateUrl = "https://v2pgweb.storage.c2.liara.space/update1.10.27.html#appName"
                WebViewDialogFragment.newInstance(updateUrl).show(supportFragmentManager, "WebViewDialog")
            }
            R.id.nav_tutorial_web -> {
                val tutorialUrl = "https://v2pgweb.storage.c2.liara.space/tutorial.html"
                WebViewDialogFragment.newInstance(tutorialUrl).show(supportFragmentManager, "WebViewDialog")
            }
            R.id.nav_report_problem -> {
                val reportUrl = "https://v2pgweb.storage.c2.liara.space/contact.html"
                WebViewDialogFragment.newInstance(reportUrl).show(supportFragmentManager, "WebViewDialog")
            }
            R.id.nav_about_us -> {
                val aboutusUrl = "https://v2pgweb.storage.c2.liara.space/about.html"
                WebViewDialogFragment.newInstance(aboutusUrl).show(supportFragmentManager, "WebViewDialog")
            }
            R.id.check_for_update -> startActivity(Intent(this, CheckUpdateActivity::class.java))
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
	
    override fun onDestroy() {
        super.onDestroy()
        // متوقف کردن انیمیشن برای جلوگیری از نشت حافظه
        fabAnimator?.cancel()
        fabAnimator = null
        if (isServiceRunning(this, "com.v2ray.ang.service.V2RayVpnService")) {
            V2RayServiceManager.stopVService(this)
            lifecycleScope.launch {
                delay(0)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        disposables.clear()
    }
}