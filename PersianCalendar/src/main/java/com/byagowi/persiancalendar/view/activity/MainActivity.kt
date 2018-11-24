package com.byagowi.persiancalendar.view.activity

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import com.byagowi.persiancalendar.Constants
import com.byagowi.persiancalendar.Constants.*
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.ActivityMainBinding
import com.byagowi.persiancalendar.di.dependencies.AppDependency
import com.byagowi.persiancalendar.di.dependencies.MainActivityDependency
import com.byagowi.persiancalendar.service.ApplicationService
import com.byagowi.persiancalendar.util.*
import com.byagowi.persiancalendar.view.fragment.CalendarFragment
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.DaggerAppCompatActivity
import java.util.*
import javax.inject.Inject


/**
 * Program activity for android
 *
 * @author ebraminio
 */
class MainActivity : DaggerAppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, NavigationView.OnNavigationItemSelectedListener {
    @Inject
    lateinit var mAppDependency: AppDependency // same object from App
    @Inject
    lateinit var mMainActivityDependency: MainActivityDependency
    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mNavOptions: NavOptions
    private var mCreationDateJdn: Long = 0
    private var mSettingHasChanged = false

    val mCoordinator: CoordinatorLayout by lazy { mBinding.coordinator }

    private val seasonImage: Int by lazy {
        var isSouthernHemisphere = false
        val coordinate = Utils.getCoordinate(this)
        if (coordinate != null && coordinate.latitude < 0) {
            isSouthernHemisphere = true
        }

        var month = CalendarUtils.getTodayOfCalendar(CalendarType.SHAMSI).month
        if (isSouthernHemisphere) month = (month + 6 - 1) % 12 + 1

        when {
            month < 4 -> R.drawable.spring
            month < 7 -> R.drawable.summer
            month < 10 -> R.drawable.fall
            else -> R.drawable.winter
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Don't replace below with mAppDependency.getSharedPreferences() ever
        // as the injection won't happen at the right time
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setTheme(UIUtils.getThemeFromName(prefs.getString(PREF_THEME, LIGHT_THEME) ?: LIGHT_THEME))

        Utils.applyAppLanguage(this)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        Utils.initUtils(this)

        TypefaceUtils.overrideFont("SERIF",
                TypefaceUtils.getAppFont(applicationContext))

        Utils.startEitherServiceOrWorker(this)

        // Doesn't matter apparently
        // oneTimeClockDisablingForAndroid5LE();
        UpdateUtils.setDeviceCalendarEvents(applicationContext)
        UpdateUtils.update(applicationContext, false)

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setSupportActionBar(mBinding.toolbar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }

        val isRTL = UIUtils.isRTL(this)

        val drawerToggle = object : ActionBarDrawerToggle(this, mBinding.drawer, mBinding.toolbar, R.string.openDrawer, R.string.closeDrawer) {
            val slidingDirection = if (isRTL) -1 else +1

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)
                slidingAnimation(drawerView, slideOffset)
            }

            private fun slidingAnimation(drawerView: View, slideOffset: Float) {
                mBinding.appMainLayout.translationX = slideOffset * drawerView.width.toFloat() * slidingDirection.toFloat()
                mBinding.drawer.bringChildToFront(drawerView)
                mBinding.drawer.requestLayout()
            }
        }

        mBinding.drawer.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        mNavOptions = NavOptions.Builder()
                .setEnterAnim(android.R.anim.fade_in)
                .setExitAnim(android.R.anim.fade_out)
                .setPopEnterAnim(android.R.anim.fade_in)
                .setPopExitAnim(android.R.anim.fade_out)
                .build()

        when (intent?.action) {
            "COMPASS" -> navigateTo(R.id.compass)
            "LEVEL" -> navigateTo(R.id.level)
            "CONVERTER" -> navigateTo(R.id.converter)
            "SETTINGS" -> navigateTo(R.id.settings)
            else -> navigateTo(R.id.calendar)
        }

        prefs.registerOnSharedPreferenceChangeListener(this)

        if (Utils.isShowDeviceCalendarEvents()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                UIUtils.askForCalendarPermission(this)
            }
        }

        mBinding.navigation.setNavigationItemSelectedListener(this)

        (mBinding.navigation.getHeaderView(0).findViewById<View>(R.id.season_image) as ImageView)
                .setImageResource(seasonImage)

        if (prefs.getString(PREF_APP_LANGUAGE, "N/A") == "N/A" && !prefs.getBoolean(Constants.CHANGE_LANGUAGE_IS_PROMOTED_ONCE, false)) {
            val snackbar = Snackbar.make(mCoordinator, "✖  Change app language?",
                    10000)
            val snackbarView = snackbar.view
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                snackbarView.layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            val text = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            text.setTextColor(Color.WHITE)

            snackbarView.setOnClickListener { snackbar.dismiss() }
            snackbar.setAction("Settings") {
                prefs.edit().apply {
                    putString(Constants.PREF_APP_LANGUAGE, Constants.LANG_EN_US)
                    putString(PREF_MAIN_CALENDAR_KEY, "GREGORIAN")
                    putString(PREF_OTHER_CALENDARS_KEY, "ISLAMIC,SHAMSI")
                    putStringSet(PREF_HOLIDAY_TYPES, HashSet())
                }.apply()

                restartToSettings()
            }
            snackbar.setActionTextColor(resources.getColor(R.color.dark_accent))
            snackbar.show()

            // Show this snackbar only once
            prefs.edit().apply { putBoolean(Constants.CHANGE_LANGUAGE_IS_PROMOTED_ONCE, true) }.apply()
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBinding.appbarLayout.outlineProvider = null
        }

        mCreationDateJdn = CalendarUtils.getTodayJdn()
        Utils.applyAppLanguage(this)
    }

    fun navigateTo(@IdRes id: Int) {
        val menuItem = mBinding.navigation.menu.findItem(
                if (id == R.id.level) R.id.compass else id) // We don't have a menu entry for compass, so
        if (menuItem != null) {
            menuItem.isCheckable = true
            menuItem.isChecked = true
        }

        if (mSettingHasChanged) { // update when checked menu item is changed
            Utils.initUtils(this)
            UpdateUtils.update(applicationContext, true)
            mSettingHasChanged = false // reset for the next time
        }

        Navigation.findNavController(this, R.id.nav_host_fragment)
                .navigate(id, null, mNavOptions)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        mSettingHasChanged = true
        if (key == PREF_APP_LANGUAGE) {
            var persianDigits = false
            var changeToAfghanistanHolidays = false
            var changeToIslamicCalendar = false
            var changeToGregorianCalendar = false
            var changeToPersianCalendar = false
            var changeToIranEvents = false
            var lang = sharedPreferences.getString(PREF_APP_LANGUAGE, DEFAULT_APP_LANGUAGE)
            if (lang == null) lang = ""
            when (lang) {
                LANG_EN_US -> changeToGregorianCalendar = true
                LANG_FA -> {
                    persianDigits = true
                    changeToPersianCalendar = true
                    changeToIranEvents = true
                }
                LANG_EN_IR -> {
                    persianDigits = false
                    changeToPersianCalendar = true
                    changeToIranEvents = true
                }
                LANG_UR -> {
                    persianDigits = false
                    changeToGregorianCalendar = true
                }
                LANG_AR -> {
                    persianDigits = true
                    changeToIslamicCalendar = true
                }
                LANG_FA_AF -> {
                    persianDigits = true
                    changeToPersianCalendar = true
                    changeToAfghanistanHolidays = true
                }
                LANG_PS -> {
                    persianDigits = true
                    changeToPersianCalendar = true
                    changeToAfghanistanHolidays = true
                }
                else -> persianDigits = true
            }

            sharedPreferences.edit().apply {
                putBoolean(PREF_PERSIAN_DIGITS, persianDigits)
                // Enable Afghanistan holidays when Dari or Pashto is set
                if (changeToAfghanistanHolidays) {
                    val currentHolidays = sharedPreferences.getStringSet(PREF_HOLIDAY_TYPES, HashSet())

                    if (currentHolidays == null || currentHolidays.isEmpty() ||
                            currentHolidays.size == 1 && currentHolidays.contains("iran_holidays")) {
                        putStringSet(PREF_HOLIDAY_TYPES,
                                HashSet(listOf("afghanistan_holidays")))
                    }
                }
                if (changeToIranEvents) {
                    val currentHolidays = sharedPreferences.getStringSet(PREF_HOLIDAY_TYPES, HashSet())

                    if (currentHolidays == null || currentHolidays.isEmpty() ||
                            currentHolidays.size == 1 && currentHolidays.contains("afghanistan_holidays")) {
                        putStringSet(PREF_HOLIDAY_TYPES,
                                HashSet(listOf("iran_holidays")))
                    }
                }
                when {
                    changeToGregorianCalendar -> {
                        putString(PREF_MAIN_CALENDAR_KEY, "GREGORIAN")
                        putString(PREF_OTHER_CALENDARS_KEY, "ISLAMIC,SHAMSI")
                        putString(PREF_WEEK_START, "1")
                        putStringSet(PREF_WEEK_ENDS, HashSet(listOf("1")))
                    }
                    changeToIslamicCalendar -> {
                        putString(PREF_MAIN_CALENDAR_KEY, "ISLAMIC")
                        putString(PREF_OTHER_CALENDARS_KEY, "GREGORIAN,SHAMSI")
                        putString(PREF_WEEK_START, DEFAULT_WEEK_START)
                        putStringSet(PREF_WEEK_ENDS, DEFAULT_WEEK_ENDS)
                    }
                    changeToPersianCalendar -> {
                        putString(PREF_MAIN_CALENDAR_KEY, "SHAMSI")
                        putString(PREF_OTHER_CALENDARS_KEY, "GREGORIAN,ISLAMIC")
                        putString(PREF_WEEK_START, DEFAULT_WEEK_START)
                        putStringSet(PREF_WEEK_ENDS, DEFAULT_WEEK_ENDS)
                    }
                }
            }.apply()
        }

        if (key == PREF_SHOW_DEVICE_CALENDAR_EVENTS) {
            if (sharedPreferences.getBoolean(PREF_SHOW_DEVICE_CALENDAR_EVENTS, true)) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    UIUtils.askForCalendarPermission(this)
                }
            }
        }

        if (key == PREF_APP_LANGUAGE || key == PREF_THEME) {
            restartToSettings()
        }

        if (key == PREF_NOTIFY_DATE) {
            if (!sharedPreferences.getBoolean(PREF_NOTIFY_DATE, true)) {
                stopService(Intent(this, ApplicationService::class.java))
                Utils.startEitherServiceOrWorker(applicationContext)
            }
        }

        Utils.updateStoredPreference(this)
        UpdateUtils.update(applicationContext, true)

        mAppDependency.localBroadcastManager
                .sendBroadcast(Intent(Constants.LOCAL_INTENT_UPDATE_PREFERENCE))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.CALENDAR_READ_PERMISSION_REQUEST_CODE) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                UIUtils.toggleShowDeviceCalendarOnPreference(this, true)
                val currentDestination = Navigation
                        .findNavController(this, R.id.nav_host_fragment)
                        .currentDestination
                if (currentDestination != null && currentDestination.id == R.id.calendar) {
                    recreate()
                }
            } else {
                UIUtils.toggleShowDeviceCalendarOnPreference(this, false)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Utils.initUtils(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mBinding.drawer.layoutDirection = if (UIUtils.isRTL(this)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
    }

    override fun onResume() {
        super.onResume()
        Utils.applyAppLanguage(this)
        UpdateUtils.update(applicationContext, false)
        if (mCreationDateJdn != CalendarUtils.getTodayJdn()) {
            recreate()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Checking for the "menu" key
        return if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (mBinding.drawer.isDrawerOpen(GravityCompat.START)) {
                mBinding.drawer.closeDrawers()
            } else {
                mBinding.drawer.openDrawer(GravityCompat.START)
            }
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    private fun restartToSettings() {
        intent?.action = "SETTINGS"
        finish()
        startActivity(intent)
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.exit) {
            finish()
            return true
        }

        mBinding.drawer.closeDrawers()
        navigateTo(menuItem.itemId)
        return true
    }

    fun setTitleAndSubtitle(title: String, subtitle: String) {
        supportActionBar?.title = title
        supportActionBar?.subtitle = subtitle
    }

    override fun onBackPressed() {
        if (mBinding.drawer.isDrawerOpen(GravityCompat.START)) {
            mBinding.drawer.closeDrawers()
        } else {
            val calendarFragment = supportFragmentManager
                    .findFragmentByTag(CalendarFragment::class.java.name) as CalendarFragment?

            if (calendarFragment?.closeSearch() == true)
                return

            val currentDestination = Navigation
                    .findNavController(this, R.id.nav_host_fragment)
                    .currentDestination
            if (currentDestination?.id == R.id.calendar)
                finish()
            else
                navigateTo(R.id.calendar)
        }
    }
}
