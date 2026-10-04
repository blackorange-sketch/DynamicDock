import os
import sys

# Визначаємо корінь проєкту динамічно
project_root = None
current_dir = os.getcwd()

# Шукаємо папку DynamicDock або просто перевіряємо структуру
if os.path.exists(os.path.join(current_dir, "app", "src", "main", "java", "com", "dynamicdock")):
    project_root = current_dir
elif os.path.exists(os.path.join(current_dir, "..", "DynamicDock", "app")):
    project_root = os.path.abspath(os.path.join(current_dir, "..", "DynamicDock"))
else:
    # Спроба знайти через HOME
    home = os.path.expanduser("~")
    possible_paths = [
        os.path.join(home, "DynamicDock"),
        os.path.join(home, "storage", "shared", "DynamicDock")
    ]
    for p in possible_paths:
        if os.path.exists(os.path.join(p, "app", "src", "main", "java", "com", "dynamicdock")):
            project_root = p
            break

if not project_root:
    print("❌ НЕ ЗНАЙДЕНО ПРОЄКТ! Будь ласка, перейди в папку ~/DynamicDock перед запуском.")
    sys.exit(1)

print(f"✅ Знайдено проєкт у: {project_root}")

base_path = os.path.join(project_root, "app", "src", "main", "java", "com", "dynamicdock")

# ==========================================
# 1. DockService.kt (Чистий код без злитих рядків)
# ==========================================
service_code = '''package com.dynamicdock

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sqrt

class DockService : Service() {

    companion object {
        @Volatile var instance: DockService? = null
        fun updateActivePackage(packageName: String) { instance?.updatePackage(packageName) }
        fun updateVisiblePackages(packages: Set<String>) { instance?.removeMissingDynamicApps(packages) }
        fun removeDynamicPackage(packageName: String) { instance?.removeDynamicPackage(packageName) }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var appContainer: LinearLayout
    private lateinit var registry: RunningAppRegistry
    private lateinit var vibrator: Vibrator
    private lateinit var contextMenu: DockContextMenu
    
    private var activePackageName: String? = null
    private val indicators = mutableMapOf<String, View>()
    
    private var isHidden = false
    private var hideHandle: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideDock() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        registry = RunningAppRegistry(this)
        registry.restorePinned(this)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        
        contextMenu = DockContextMenu(this, windowManager) { action, app ->
            when (action) {
                DockContextMenu.Action.PIN -> registry.pin(app.packageName)
                DockContextMenu.Action.UNPIN -> registry.unpin(app.packageName)
            }
            rebuildDock()
            resetAutoHide()
        }

        setupContainer()
        refreshDock()
    }

    fun refreshDock() {
        appContainer.post {
            rebuildDock()
            adjustPosition()
        }
    }

    fun updateDockHeight(heightDp: Int) {
        val params = appContainer.layoutParams as? WindowManager.LayoutParams ?: return
        val settings = DockSettings(this)
        val isVertical = settings.dockPosition == "left" || settings.dockPosition == "right"
        
        if (isVertical) {
            params.width = dp(heightDp)
        } else {
            params.height = dp(heightDp)
        }
        windowManager.updateViewLayout(appContainer, params)
        rebuildDock()
    }

    fun updateDockPosition(position: String) {
        val params = appContainer.layoutParams as? WindowManager.LayoutParams ?: return
        val settings = DockSettings(this)
        val isVertical = position == "left" || position == "right"

        appContainer.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        
        if (isVertical) {
            params.width = dp(settings.dockHeightDp)
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = if (position == "left") Gravity.TOP or Gravity.LEFT else Gravity.TOP or Gravity.RIGHT
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = dp(settings.dockHeightDp)
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.y = 0
        }
        
        windowManager.updateViewLayout(appContainer, params)
        rebuildDock()
        adjustPosition()
    }

    fun updateVerticalPosition(percent: Int) {
        val params = appContainer.layoutParams as? WindowManager.LayoutParams ?: return
        val settings = DockSettings(this)
        val isVertical = settings.dockPosition == "left" || settings.dockPosition == "right"
        
        if (!isVertical) return

        val screenHeight = resources.displayMetrics.heightPixels
        val dockHeight = appContainer.height
        val maxY = (screenHeight - dockHeight).coerceAtLeast(0)
        params.y = (maxY * percent / 100f).toInt()
        
        windowManager.updateViewLayout(appContainer, params)
    }

    fun updateIconSize(sizeDp: Int) {
        rebuildDock()
    }

    fun updatePadding() {
        rebuildDock()
    }

    fun refreshHideHandle() {
        if (isHidden) {
            createHandle()
        }
    }

    private fun setupContainer() {
        val settings = DockSettings(this)
        val isVertical = settings.dockPosition == "left" || settings.dockPosition == "right"

        appContainer = LinearLayout(this).apply {
            orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.dock_background)
            visibility = View.INVISIBLE
        }

        val lp = WindowManager.LayoutParams(
            if (isVertical) dp(settings.dockHeightDp) else WindowManager.LayoutParams.WRAP_CONTENT,
            if (isVertical) WindowManager.LayoutParams.WRAP_CONTENT else dp(settings.dockHeightDp),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        lp.gravity = when (settings.dockPosition) {
            "left" -> Gravity.TOP or Gravity.LEFT
            "right" -> Gravity.TOP or Gravity.RIGHT
            else -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        windowManager.addView(appContainer, lp)
    }

    private fun adjustPosition() {
        val settings = DockSettings(this)
        val isVertical = settings.dockPosition == "left" || settings.dockPosition == "right"
        val lp = appContainer.layoutParams as? WindowManager.LayoutParams ?: return

        if (isVertical) {
            appContainer.post {
                val h = appContainer.height
                if (h > 0) {
                    val maxH = resources.displayMetrics.heightPixels - h
                    val pct = settings.verticalPositionPercent.coerceIn(0, 100)
                    lp.y = (maxH * pct / 100f).toInt()
                    windowManager.updateViewLayout(appContainer, lp)
                    appContainer.visibility = View.VISIBLE
                }
            }
        } else {
            lp.y = 0
            windowManager.updateViewLayout(appContainer, lp)
            appContainer.visibility = View.VISIBLE
        }
    }

    private fun rebuildDock() {
        indicators.clear()
        appContainer.removeAllViews()
        
        val apps = registry.getApps()
        val settings = DockSettings(this)
        val isVertical = settings.dockPosition == "left" || settings.dockPosition == "right"

        appContainer.setPadding(
            if (isVertical) 0 else dp(settings.horizontalPaddingDp),
            if (isVertical) dp(settings.verticalPaddingDp) else 0,
            if (isVertical) 0 else dp(settings.horizontalPaddingDp),
            if (isVertical) dp(settings.verticalPaddingDp) else 0
        )

        val closeBtn = TextView(this).apply {
            text = "\\u00d7" // × символ
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(110, 40, 40, 40))
                setStroke(dp(1), Color.argb(160, 255, 255, 255))
            }
            setOnClickListener { hideDock() }
        }
        appContainer.addView(closeBtn, LinearLayout.LayoutParams(dp(28), dp(28)))

        apps.forEachIndexed { idx, app ->
            if (idx > 0 && apps[idx-1].pinned && !app.pinned) {
                val sep = View(this).apply { setBackgroundColor(Color.argb(90, 255, 255, 255)) }
                val sepLp = if (isVertical) 
                    LinearLayout.LayoutParams(dp(28), dp(1)).apply { setMargins(0, dp(4), 0, dp(4)) } 
                else 
                    LinearLayout.LayoutParams(dp(1), dp(28)).apply { setMargins(dp(4), 0, dp(4), 0) }
                appContainer.addView(sep, sepLp)
            }

            lateinit var iconImg: ImageView
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                tag = app

                setOnClickListener {
                    showDock()
                    animate().scaleX(0.88f).scaleY(0.88f).setDuration(120).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }.start()
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                    }
                }

                setOnLongClickListener {
                    showDock()
                    if (vibrator.hasVibrator()) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    val act = if (app.pinned) DockContextMenu.Action.UNPIN else DockContextMenu.Action.PIN
                    iconImg.post {
                        contextMenu.show(iconImg, app, act, settings.dockPosition)
                    }
                    resetAutoHide()
                    true
                }

                setOnTouchListener(object : View.OnTouchListener {
                    private var tStart = 0L
                    private var xStart = 0f
                    private var yStart = 0f
                    private var ox = 0f
                    private var oy = 0f
                    private var dragging = false

                    override fun onTouch(v: View, e: MotionEvent): Boolean {
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                tStart = System.currentTimeMillis()
                                xStart = e.rawX
                                yStart = e.rawY
                                ox = v.x
                                oy = v.y
                                dragging = false
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = e.rawX - xStart
                                val dy = e.rawY - yStart
                                val dist = sqrt(dx*dx + dy*dy)
                                if (dist > dp(10) && app.pinned && System.currentTimeMillis() - tStart > 300) {
                                    dragging = true
                                    v.x = ox + dx
                                    v.y = oy + dy
                                    v.alpha = 0.7f
                                    v.scaleX = 1.1f
                                    v.scaleY = 1.1f
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (dragging) {
                                    handleDrop(v, e)
                                } else {
                                    val dx = e.rawX - xStart
                                    val dy = e.rawY - yStart
                                    if (!app.pinned && sqrt(dx*dx + dy*dy) > dp(80)) {
                                        removeDynamicPackage(app.packageName)
                                    }
                                }
                                dragging = false
                            }
                        }
                        return false
                    }

                    private fun handleDrop(dragged: View, e: MotionEvent) {
                        var swapped = false
                        for (i in 0 until appContainer.childCount) {
                            val child = appContainer.getChildAt(i)
                            if (child != dragged && child.tag is RunningApp) {
                                val target = child.tag as RunningApp
                                if (target.pinned) {
                                    val loc = IntArray(2)
                                    child.getLocationOnScreen(loc)
                                    if (e.rawX >= loc[0] && e.rawX <= loc[0]+child.width && e.rawY >= loc[1] && e.rawY <= loc[1]+child.height) {
                                        swapAnim(dragged, child, app, target)
                                        swapped = true
                                        break
                                    }
                                }
                            }
                        }
                        if (!swapped) {
                            dragged.animate().x(ox).y(oy).alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
                        }
                    }
                })
            }

            iconImg = ImageView(this).apply {
                setImageDrawable(app.icon)
                contentDescription = app.appName
            }
            
            val size = settings.iconSizeDp
            val contW = if (isVertical) dp(size+6) else dp(size)
            val contH = if (isVertical) dp(size) else dp(size+6)
            
            val frame = FrameLayout(this)
            frame.addView(iconImg, FrameLayout.LayoutParams(dp(size), dp(size)).apply { gravity = Gravity.CENTER })

            val ind = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(100).toFloat()
                }
                alpha = if (app.packageName == activePackageName) 1f else 0f
            }
            
            val indLp = if (isVertical) {
                FrameLayout.LayoutParams(dp(3), dp((size*0.8f).toInt())).apply {
                    gravity = Gravity.CENTER_VERTICAL or (if (settings.dockPosition=="left") Gravity.END else Gravity.START)
                }
            } else {
                FrameLayout.LayoutParams(dp((size*0.8f).toInt()), dp(3)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                }
            }
            frame.addView(ind, indLp)
            indicators[app.packageName] = ind

            item.addView(frame, LinearLayout.LayoutParams(contW, contH))

            if (settings.showAppLabels) {
                val tv = TextView(this).apply {
                    text = app.appName
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 1
                }
                item.addView(tv, LinearLayout.LayoutParams(dp(40), dp(24)))
            }

            val itemLp = if (isVertical) 
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            else 
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            
            if (isVertical) itemLp.topMargin = dp(settings.verticalPaddingDp) else itemLp.leftMargin = dp(settings.horizontalPaddingDp)
            
            appContainer.addView(item, itemLp)
        }
    }

    private fun swapAnim(src: View, tgt: View, sApp: RunningApp, tApp: RunningApp) {
        val lS = IntArray(2)
        val lT = IntArray(2)
        src.getLocationInWindow(lS)
        tgt.getLocationInWindow(lT)
        
        val dSX = (lT[0]-lS[0]).toFloat()
        val dSY = (lT[1]-lS[1]).toFloat()
        val dTX = (lS[0]-lT[0]).toFloat()
        val dTY = (lS[1]-lT[1]).toFloat()

        src.animate().translationX(dSX).translationY(dSY).setDuration(250).start()
        tgt.animate().translationX(dTX).translationY(dTY).setDuration(250).withEndAction {
            src.animate().cancel()
            tgt.animate().cancel()
            src.translationX=0f; src.translationY=0f
            tgt.translationX=0f; tgt.translationY=0f
            
            val iS = appContainer.indexOfChild(src)
            val iT = appContainer.indexOfChild(tgt)
            appContainer.removeView(src)
            appContainer.removeView(tgt)
            
            if (iS < iT) {
                appContainer.addView(tgt, iS)
                appContainer.addView(src, iT)
            } else {
                appContainer.addView(src, iT)
                appContainer.addView(tgt, iS)
            }
            
            registry.swapApps(sApp.packageName, tApp.packageName)
        }.start()
    }

    private fun hideDock() {
        if (isHidden) return
        isHidden = true
        appContainer.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150).withEndAction {
            appContainer.visibility = View.GONE
            createHandle()
        }.start()
    }

    private fun showDock() {
        if (!isHidden) { resetAutoHide(); return }
        isHidden = false
        hideHandle?.let { try { windowManager.removeView(it) } catch(_:Exception){} }
        hideHandle = null
        
        appContainer.visibility = View.VISIBLE
        appContainer.alpha = 0f
        appContainer.scaleX = 0.8f
        appContainer.scaleY = 0.8f
        appContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).withEndAction { resetAutoHide() }.start()
    }

    private fun createHandle() {
        val settings = DockSettings(this)
        val len = dp(settings.hideHandleLengthDp)
        val thick = dp(settings.hideHandleThicknessDp)
        val mgn = dp(settings.hideHandleMarginDp)
        
        val c = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { showDock() }
        }
        
        val bar = View(this).apply { 
            background = GradientDrawable().apply { 
                setColor(Color.WHITE)
                cornerRadius = dp(100).toFloat() 
            } 
        }
        
        val isVert = settings.dockPosition == "left" || settings.dockPosition == "right"
        
        if (isVert) {
            val lpBar = FrameLayout.LayoutParams(thick, len).apply { 
                gravity = if (settings.dockPosition == "left") Gravity.START or Gravity.CENTER_VERTICAL else Gravity.END or Gravity.CENTER_VERTICAL
                leftMargin = if (settings.dockPosition == "left") mgn else 0
                rightMargin = if (settings.dockPosition == "right") mgn else 0
            }
            c.addView(bar, lpBar)
            
            val p = WindowManager.LayoutParams(mgn + dp(24), len).apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = if (settings.dockPosition == "right") Gravity.TOP or Gravity.RIGHT else Gravity.TOP or Gravity.LEFT
                
                val screenH = resources.displayMetrics.heightPixels
                val maxY = (screenH - len).coerceAtLeast(0)
                y = (maxY * settings.verticalPositionPercent / 100f).toInt()
            }
            windowManager.addView(c, p)
            hideHandle = c
        } else {
            val lpBar = FrameLayout.LayoutParams(len, thick).apply { 
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                bottomMargin = mgn
            }
            c.addView(bar, lpBar)
            
            val p = WindowManager.LayoutParams(len, mgn + dp(24)).apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            windowManager.addView(c, p)
            hideHandle = c
        }
        
        c.alpha = 0f
        c.animate().alpha(1f).setDuration(150).start()
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHideRunnable)
        val settings = DockSettings(this)
        if (settings.autoHide) {
            handler.postDelayed(autoHideRunnable, settings.autoHideDelaySeconds * 1000L)
        }
    }

    private fun resetAutoHide() {
        if (!isHidden) {
            scheduleAutoHide()
        }
    }

    private fun updatePackage(pkg: String) {
        if (pkg == packageName || pkg.startsWith("com.android.") || pkg.startsWith("com.google.")) return
        activePackageName = pkg
        val info = AppInfoRepository(this).getAppInfo(pkg)
        registry.activate(RunningApp(info.packageName, info.appName, info.icon))
        appContainer.post {
            rebuildDock()
            scheduleAutoHide()
        }
    }

    private fun removeDynamicPackage(pkg: String) {
        if (registry.removeDynamic(pkg)) {
            appContainer.post {
                rebuildDock()
                scheduleAutoHide()
            }
        }
    }

    private fun removeMissingDynamicApps(pkgs: Set<String>) {
        if (registry.removeMissingDynamicApps(pkgs)) {
            appContainer.post {
                rebuildDock()
                scheduleAutoHide()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        instance = null
        try { windowManager.removeView(appContainer) } catch(_:Exception){}
        hideHandle?.let { try { windowManager.removeView(it) } catch(_:Exception){} }
        super.onDestroy()
    }
}
'''

with open(os.path.join(base_path, "DockService.kt"), "w", encoding="utf-8") as f:
    f.write(service_code)
print("✓ DockService.kt оновлено")

# ==========================================
# 2. DockContextMenu.kt
# ==========================================
context_menu_code = '''package com.dynamicdock

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout

class DockContextMenu(
    private val service: DockService,
    private val windowManager: WindowManager,
    private val onAction: (Action, RunningApp) -> Unit
) {

    enum class Action { PIN, UNPIN }

    private var menuView: LinearLayout? = null

    fun show(anchor: View, app: RunningApp, action: Action, dockPosition: String) {
        dismiss()

        val buttonSize = dp(28)
        val margin = dp(4)
        val gap = dp(12)
        val screenMargin = dp(8)

        val button = ImageButton(service).apply {
            setImageResource(if (action == Action.PIN) android.R.drawable.ic_menu_add else android.R.drawable.ic_menu_close_clear_cancel)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 45, 45, 45))
                setStroke(dp(1), Color.argb(180, 255, 255, 255))
            }
            setColorFilter(android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN))
            setOnClickListener {
                onAction(action, app)
                dismiss()
            }
        }

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(margin, margin, margin, margin)
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.argb(235, 25, 25, 25))
                setStroke(dp(1), Color.argb(130, 255, 255, 255))
            }
            addView(button, LinearLayout.LayoutParams(buttonSize, buttonSize))
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
        }

        container.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                dismiss()
                true
            } else false
        }

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        
        val anchorLeft = location[0]
        val anchorTop = location[1]
        val anchorRight = anchorLeft + anchor.width
        val anchorBottom = anchorTop + anchor.height
        val anchorCenterX = anchorLeft + anchor.width / 2

        val menuWidth = buttonSize + margin * 2
        val menuHeight = buttonSize + margin * 2

        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        var menuX: Int
        var menuY: Int

        when (dockPosition) {
            "bottom" -> {
                menuX = anchorCenterX - menuWidth / 2
                menuY = anchorTop - menuHeight - gap
            }
            "top" -> {
                menuX = anchorCenterX - menuWidth / 2
                menuY = anchorBottom + gap
            }
            "left" -> {
                menuX = anchorRight + gap
                menuY = anchorTop + (anchor.height - menuHeight) / 2
            }
            "right" -> {
                menuX = anchorLeft - menuWidth - gap
                menuY = anchorTop + (anchor.height - menuHeight) / 2
            }
            else -> {
                menuX = anchorCenterX - menuWidth / 2
                menuY = anchorTop - menuHeight - gap
            }
        }

        val maxX = (screenWidth - menuWidth - screenMargin).coerceAtLeast(screenMargin)
        val maxY = (screenHeight - menuHeight - screenMargin).coerceAtLeast(screenMargin)
        menuX = menuX.coerceIn(screenMargin, maxX)
        menuY = menuY.coerceIn(screenMargin, maxY)

        try {
            val settings = DockSettings(service)
            menuX += settings.menuXOffset
            menuY += settings.menuYOffset
        } catch (_: Exception) {}

        val params = WindowManager.LayoutParams(
            menuWidth, menuHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = menuX
        params.y = menuY
        
        menuView = container
        windowManager.addView(container, params)

        container.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(120).start()
    }

    fun dismiss() {
        val view = menuView ?: return
        try {
            view.animate().cancel()
            windowManager.removeView(view)
        } catch (_: Exception) {}
        menuView = null
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
}
'''

with open(os.path.join(base_path, "DockContextMenu.kt"), "w", encoding="utf-8") as f:
    f.write(context_menu_code)
print("✓ DockContextMenu.kt оновлено")

# ==========================================
# 3. DockSettings.kt (Гарантуємо наявність offset)
# ==========================================
settings_code = '''package com.dynamicdock

import android.content.Context
import android.content.SharedPreferences

class DockSettings(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences("dynamic_dock_settings", Context.MODE_PRIVATE)

    var dockPosition: String
        get() = preferences.getString("dock_position", "bottom") ?: "bottom"
        set(value) { preferences.edit().putString("dock_position", value).apply() }

    var dockHeightDp: Int
        get() = preferences.getInt("dock_height_dp", 40)
        set(value) { preferences.edit().putInt("dock_height_dp", value).apply() }

    var iconSizeDp: Int
        get() = preferences.getInt("icon_size_dp", 32)
        set(value) { preferences.edit().putInt("icon_size_dp", value).apply() }

    var horizontalPaddingDp: Int
        get() = preferences.getInt("horizontal_padding_dp", 8)
        set(value) { preferences.edit().putInt("horizontal_padding_dp", value).apply() }

    var verticalPaddingDp: Int
        get() = preferences.getInt("vertical_padding_dp", 4)
        set(value) { preferences.edit().putInt("vertical_padding_dp", value).apply() }

    var showAppLabels: Boolean
        get() = preferences.getBoolean("show_app_labels", false)
        set(value) { preferences.edit().putBoolean("show_app_labels", value).apply() }

    var autoHide: Boolean
        get() = preferences.getBoolean("auto_hide", true)
        set(value) { preferences.edit().putBoolean("auto_hide", value).apply() }

    var autoHideDelaySeconds: Int
        get() = preferences.getInt("auto_hide_delay_seconds", 5)
        set(value) { preferences.edit().putInt("auto_hide_delay_seconds", value).apply() }

    var hideHandleLengthDp: Int
        get() = preferences.getInt("hide_handle_length_dp", 60)
        set(value) { preferences.edit().putInt("hide_handle_length_dp", value).apply() }

    var hideHandleThicknessDp: Int
        get() = preferences.getInt("hide_handle_thickness_dp", 6)
        set(value) { preferences.edit().putInt("hide_handle_thickness_dp", value).apply() }

    var hideHandleMarginDp: Int
        get() = preferences.getInt("hide_handle_margin_dp", 8)
        set(value) { preferences.edit().putInt("hide_handle_margin_dp", value).apply() }

    var verticalPositionPercent: Int
        get() = preferences.getInt("vertical_position_percent", 50)
        set(value) { preferences.edit().putInt("vertical_position_percent", value).apply() }

    var maxDynamicApps: Int
        get() = preferences.getInt("max_dynamic_apps", 5)
        set(value) { preferences.edit().putInt("max_dynamic_apps", value).apply() }

    var menuXOffset: Int
        get() = preferences.getInt("menu_x_offset", 0)
        set(value) { preferences.edit().putInt("menu_x_offset", value).apply() }

    var menuYOffset: Int
        get() = preferences.getInt("menu_y_offset", 0)
        set(value) { preferences.edit().putInt("menu_y_offset", value).apply() }
}
'''

with open(os.path.join(base_path, "DockSettings.kt"), "w", encoding="utf-8") as f:
    f.write(settings_code)
print("✓ DockSettings.kt оновлено")

# ==========================================
# 4. RunningAppRegistry.kt
# ==========================================
registry_code = '''package com.dynamicdock

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

class RunningAppRegistry(private val context: Context) {

    private val prefs = context.getSharedPreferences("dynamic_dock_registry", Context.MODE_PRIVATE)
    private var apps: MutableList<RunningApp> = mutableListOf()

    init {
        loadPinnedApps()
    }

    fun getApps(): List<RunningApp> = apps.toList()

    fun activate(app: RunningApp) {
        val existingIndex = apps.indexOfFirst { it.packageName == app.packageName }
        
        if (existingIndex != -1) {
            val current = apps[existingIndex]
            if (!current.pinned) {
                apps.removeAt(existingIndex)
                apps.add(0, app.copy(pinned = false))
            } else {
                apps[existingIndex] = app.copy(pinned = true)
            }
        } else {
            apps.add(0, app.copy(pinned = false))
        }
        
        trimDynamicAppsToLimit()
        saveState()
    }

    fun pin(packageName: String) {
        val index = apps.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            val app = apps[index]
            apps.removeAt(index)
            
            val firstUnpinnedIndex = apps.indexOfFirst { !it.pinned }
            if (firstUnpinnedIndex == -1) {
                apps.add(app.copy(pinned = true))
            } else {
                apps.add(firstUnpinnedIndex, app.copy(pinned = true))
            }
            saveState()
        }
    }

    fun unpin(packageName: String) {
        val index = apps.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            val app = apps[index]
            apps.removeAt(index)
            apps.add(app.copy(pinned = false))
            saveState()
        }
    }

    fun removeDynamic(packageName: String): Boolean {
        val index = apps.indexOfFirst { it.packageName == packageName && !it.pinned }
        if (index != -1) {
            apps.removeAt(index)
            return true
        }
        return false
    }

    fun swapApps(pkg1: String, pkg2: String) {
        val idx1 = apps.indexOfFirst { it.packageName == pkg1 }
        val idx2 = apps.indexOfFirst { it.packageName == pkg2 }
        
        if (idx1 != -1 && idx2 != -1) {
            val temp = apps[idx1]
            apps[idx1] = apps[idx2]
            apps[idx2] = temp
            saveState()
        }
    }

    fun trimDynamicAppsToLimit() {
        val settings = DockSettings(context)
        val limit = settings.maxDynamicApps
        
        var dynamicCount = 0
        for (i in apps.indices.reversed()) {
            if (!apps[i].pinned) {
                dynamicCount++
                if (dynamicCount > limit) {
                    apps.removeAt(i)
                }
            }
        }
    }

    fun removeMissingDynamicApps(visiblePackages: Set<String>) {
        val iterator = apps.iterator()
        while (iterator.hasNext()) {
            val app = iterator.next()
            if (!app.pinned && app.packageName !in visiblePackages) {
                iterator.remove()
            }
        }
    }

    fun restorePinned(serviceContext: Context) {
        loadPinnedApps()
    }

    private fun loadPinnedApps() {
        val jsonStr = prefs.getString("pinned_apps_json", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonStr)
            val newPinned = mutableListOf<RunningApp>()
            
            for (i in 0 until jsonArray.length()) {
                val pkg = jsonArray.getString(i)
                newPinned.add(RunningApp(packageName = pkg, appName = "", icon = null, pinned = true))
            }
            
            apps.clear()
            apps.addAll(newPinned)
            
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun saveState() {
        val pinnedPackages = apps.filter { it.pinned }.map { it.packageName }
        val jsonArray = JSONArray(pinnedPackages)
        prefs.edit().putString("pinned_apps_json", jsonArray.toString()).apply()
    }
}
'''

with open(os.path.join(base_path, "RunningAppRegistry.kt"), "w", encoding="utf-8") as f:
    f.write(registry_code)
print("✓ RunningAppRegistry.kt оновлено")

# ==========================================
# 5. RunningApp.kt
# ==========================================
running_app_code = '''package com.dynamicdock

import android.graphics.drawable.Drawable

data class RunningApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val pinned: Boolean = false
)
'''

with open(os.path.join(base_path, "RunningApp.kt"), "w", encoding="utf-8") as f:
    f.write(running_app_code)
print("✓ RunningApp.kt оновлено")

# ==========================================
# 6. Fix AppSelectionActivity.kt (видалити setSelected)
# ==========================================
sel_file = os.path.join(base_path, "AppSelectionActivity.kt")
if os.path.exists(sel_file):
    with open(sel_file, "r", encoding="utf-8") as f:
        lines = f.readlines()
    
    new_lines = []
    for line in lines:
        if "setSelected" not in line:
            new_lines.append(line)
    
    with open(sel_file, "w", encoding="utf-8") as f:
        f.writelines(new_lines)
    print("✓ AppSelectionActivity.kt очищено від setSelected")

# ==========================================
# 7. Fix SettingsActivity.kt (додати max до слайдерів)
# ==========================================
set_act_file = os.path.join(base_path, "SettingsActivity.kt")
if os.path.exists(set_act_file):
    with open(set_act_file, "r", encoding="utf-8") as f:
        content = f.read()
    
    # Додаємо , 200 якщо його немає після menuXOffset/menuYOffset
    import re
    content = re.sub(r'(addSlider\([^)]*?,\s*settings\.menu[YX]Offset,\s*-?\d+)\)', r'\1, 200)', content)
    
    # Видаляємо старі посилання на dockLengthDp/updateDockLength
    content = re.sub(r'.*dockLengthDp.*\n?', '', content)
    content = re.sub(r'.*updateDockLength.*\n?', '', content)
    
    with open(set_act_file, "w", encoding="utf-8") as f:
        f.write(content)
    print("✓ SettingsActivity.kt виправлено")

print("\n🎉 ВСІ ФАЙЛИ УСПІШНО ОНОВЛЕНО ТА ГАРАНТОВАНО ЗАПИСАНО!")
