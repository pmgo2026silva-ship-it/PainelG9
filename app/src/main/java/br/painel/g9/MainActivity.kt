package br.painel.g9

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    lateinit var prefs: Prefs
    lateinit var tema: PTheme
    lateinit var media: MediaHelper
    var obd: Obd? = null
    var carBmp: Bitmap? = null
    private var bgBmp: Bitmap? = null

    private lateinit var bg: BackgroundView
    private lateinit var pageHost: FrameLayout
    private lateinit var home: HomePage
    private lateinit var apps: AppsPage
    private lateinit var lab: LabPage
    private lateinit var settings: SettingsPage
    private lateinit var bar: LinearLayout
    private lateinit var playIcon: IconView
    private lateinit var nowPlaying: TextView
    private val icons = ArrayList<IconView>()
    private val navIcons = ArrayList<IconView>()
    private lateinit var root: FrameLayout

    private val handler = Handler(Looper.getMainLooper())
    private var page = 0
    private var tickN = 0
    private var gpsOn = false
    private var lastBeep = 0L
    private var lastWeather = 0L
    private var tone: ToneGenerator? = null

    // ------------------------------------------------------------ ciclo de vida

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        tema = Themes.byId(prefs.tema)
        media = MediaHelper(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        reloadImages()
        buildUi()
        applyTheme()
        showPage(0)
        requestPerms()
        startGps()
        if (prefs.autoObd) obdConnect()
        if (prefs.boasVindas && b == null) showWelcome()
        handler.post(ticker)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        showPage(0)
    }

    @Deprecated("Launcher: o botão voltar só volta para a tela inicial")
    override fun onBackPressed() {
        if (page != 0) showPage(0)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        obd?.stop()
        try { tone?.release() } catch (e: Exception) { }
        super.onDestroy()
    }

    private val ticker = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Exception) { }
            handler.postDelayed(this, 500)
        }
    }

    private fun tick() {
        tickN++
        val sp = Live.speed()
        if (sp > 1f) Live.tripMs += 500
        Live.tripKm += sp / 3600f * 0.5f
        val l = Live.lph()
        if (l > 0f) Live.tripL += l / 3600f * 0.5f

        // aviso sonoro de velocidade
        val lim = prefs.limite
        val now = System.currentTimeMillis()
        if (lim > 0 && sp > lim && now - lastBeep > 8000) {
            lastBeep = now
            try {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            } catch (e: Exception) { }
        }

        if (tickN % 2 == 0) {
            media.refresh()
            nowPlaying.text = when {
                media.title != null -> media.title + (if (media.artist != null) " · " + media.artist else "")
                media.hasSession -> "Música"
                else -> "Controle de música"
            }
            playIcon.kind = if (media.playing) IconView.Kind.PAUSE else IconView.Kind.PLAY
        }

        if (Live.gpsFix && now - lastWeather > 20 * 60 * 1000L) {
            lastWeather = now
            fetchWeather()
        }

        home.tick()
        if (page == 2) lab.tick()
        if (page == 3) settings.tick()
    }

    // ------------------------------------------------------------ interface

    private fun iconBtn(kind: IconView.Kind, onClick: () -> Unit): IconView {
        val v = IconView(this, kind)
        v.setOnClickListener { onClick() }
        icons.add(v)
        return v
    }

    private fun buildUi() {
        root = FrameLayout(this)
        bg = BackgroundView(this)
        root.addView(bg, MATCH, MATCH)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        pageHost = FrameLayout(this)
        home = HomePage(this)
        apps = AppsPage(this)
        lab = LabPage(this)
        settings = SettingsPage(this)
        for (p in listOf<View>(home, apps, lab, settings)) pageHost.addView(p, MATCH, MATCH)
        col.addView(pageHost, LinearLayout.LayoutParams(MATCH, 0, 1f))

        bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), 0, dp(12), 0)

        val sz = dp(60)
        bar.addView(iconBtn(IconView.Kind.PREV) { media.prev() }, LinearLayout.LayoutParams(sz, MATCH))
        playIcon = iconBtn(IconView.Kind.PLAY) { media.playPause() }
        bar.addView(playIcon, LinearLayout.LayoutParams(sz, MATCH))
        bar.addView(iconBtn(IconView.Kind.NEXT) { media.next() }, LinearLayout.LayoutParams(sz, MATCH))

        nowPlaying = label(this, "Controle de música", 14f, Color.WHITE, true)
        nowPlaying.maxLines = 1
        nowPlaying.gravity = Gravity.CENTER
        nowPlaying.isSelected = true
        bar.addView(nowPlaying, LinearLayout.LayoutParams(0, MATCH, 1f))

        bar.addView(iconBtn(IconView.Kind.VOLDN) { media.volDown() }, LinearLayout.LayoutParams(sz, MATCH))
        bar.addView(iconBtn(IconView.Kind.VOLUP) { media.volUp() }, LinearLayout.LayoutParams(sz, MATCH))

        val gap = View(this)
        bar.addView(gap, LinearLayout.LayoutParams(dp(24), MATCH))

        val nav = listOf(
            iconBtn(IconView.Kind.HOME) { showPage(0) },
            iconBtn(IconView.Kind.GRID) { showPage(1) },
            iconBtn(IconView.Kind.GAUGE) { showPage(2) },
            iconBtn(IconView.Kind.TUNE) { showPage(3) }
        )
        for (n in nav) {
            navIcons.add(n)
            bar.addView(n, LinearLayout.LayoutParams(sz, MATCH))
        }
        col.addView(bar, LinearLayout.LayoutParams(MATCH, dp(64)))

        root.addView(col, MATCH, MATCH)
        setContentView(root)
    }

    fun showPage(i: Int) {
        page = i
        val views = listOf<View>(home, apps, lab, settings)
        for (k in views.indices) views[k].visibility = if (k == i) View.VISIBLE else View.GONE
        if (i == 1) apps.load()
        if (i == 3) settings.build()
        for (k in navIcons.indices) navIcons[k].tint = if (k == i) tema.accent else Color.argb(190, 255, 255, 255)
    }

    private fun applyTheme() {
        bg.apply(tema, bgBmp)
        bar.background = cardBg(this, Color.argb(150, 0, 0, 0), Color.argb(30, 255, 255, 255), 0f)
        home.applyTheme(tema)
        lab.applyTheme(tema)
        for (k in navIcons.indices) navIcons[k].tint = if (k == page) tema.accent else Color.argb(190, 255, 255, 255)
        if (page == 3) settings.build()
    }

    fun setTema(id: String) {
        prefs.tema = id
        tema = Themes.byId(id)
        applyTheme()
    }

    private fun showWelcome() {
        val ov = FrameLayout(this)
        ov.setBackgroundColor(Color.BLACK)
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        val car = CarView(this)
        car.accent = tema.accent
        car.bitmap = carBmp
        c.addView(car, LinearLayout.LayoutParams(dp(520), dp(200)))
        val t1 = label(this, "BEM-VINDO AO", 14f, DIM, true)
        t1.gravity = Gravity.CENTER
        c.addView(t1, LinearLayout.LayoutParams(WRAP, WRAP))
        val t2 = label(this, prefs.nome.uppercase() + "!", 44f, Color.WHITE, true)
        t2.gravity = Gravity.CENTER
        c.addView(t2, LinearLayout.LayoutParams(WRAP, WRAP))
        val t3 = label(this, "VAMOS COMEÇAR", 13f, tema.accent, true)
        t3.gravity = Gravity.CENTER
        c.addView(t3, LinearLayout.LayoutParams(WRAP, WRAP))
        ov.addView(c, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        ov.setOnClickListener { root.removeView(ov) }
        root.addView(ov, MATCH, MATCH)
        ov.alpha = 0f
        ov.animate().alpha(1f).setDuration(450).withEndAction {
            handler.postDelayed({
                ov.animate().alpha(0f).setDuration(600).withEndAction { root.removeView(ov) }.start()
            }, 1900)
        }.start()
    }

    // ------------------------------------------------------------ apps e atalhos

    fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } else {
            toast("App não instalado")
        }
    }

    fun listApps(): List<AppItem> {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .filter { it.pkg != packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun dialog(): AlertDialog.Builder =
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    /** Escolhe um app para ocupar o atalho [slot]. */
    fun pickApp(slot: Int) {
        Thread {
            val l = listApps()
            runOnUiThread {
                dialog().setTitle("Atalho ${slot + 1}")
                    .setItems(l.map { it.label }.toTypedArray()) { _, k ->
                        prefs.setSlot(slot, l[k].pkg)
                        home.refreshTiles()
                    }.show()
            }
        }.start()
    }

    /** Toque longo num app da gaveta: escolher qual atalho ele vai ocupar. */
    fun chooseSlot(pkg: String) {
        dialog().setTitle("Fixar na tela inicial")
            .setItems(Array(5) { "Atalho ${it + 1}" }) { _, k ->
                prefs.setSlot(k, pkg)
                home.refreshTiles()
                toast("Fixado no atalho ${k + 1}")
            }.show()
    }

    fun askText(title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(this)
        et.setText(initial)
        et.setTextColor(Color.WHITE)
        et.setSingleLine(true)
        dialog().setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> ok(et.text.toString().trim()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun openSystem(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Esta central não tem essa tela de ajustes")
        }
    }

    // ------------------------------------------------------------ imagens

    private fun fileFor(kind: Int) = File(filesDir, if (kind == 0) "carro.png" else "fundo.jpg")

    private fun reloadImages() {
        carBmp = loadBitmap(fileFor(0), 1200)
        bgBmp = loadBitmap(fileFor(1), 1600)
    }

    fun pickImage(kind: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        try {
            startActivityForResult(i, 10 + kind)
        } catch (e: Exception) {
            toast("Esta central não tem seletor de arquivos. Use \"Da pasta\".")
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if ((req == 10 || req == 11) && res == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openInputStream(data.data!!)?.use { input ->
                    fileFor(req - 10).outputStream().use { input.copyTo(it) }
                }
                reloadImages()
                applyTheme()
            } catch (e: Exception) {
                toast("Não consegui ler a imagem")
            }
        }
    }

    /** Copia carro.png / fundo.jpg de Android/data/br.painel.g9/files/ (útil sem seletor de arquivos). */
    fun reloadFromFolder(kind: Int) {
        val src = File(getExternalFilesDir(null), fileFor(kind).name)
        if (!src.exists()) {
            toast("Coloque ${src.name} em ${getExternalFilesDir(null)?.path}")
            return
        }
        try {
            src.copyTo(fileFor(kind), true)
            reloadImages()
            applyTheme()
        } catch (e: Exception) {
            toast("Não consegui copiar a imagem")
        }
    }

    fun removeImage(kind: Int) {
        fileFor(kind).delete()
        reloadImages()
        applyTheme()
    }

    // ------------------------------------------------------------ permissões, GPS

    private fun requestPerms() {
        val need = ArrayList<String>()
        need.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) need.add(Manifest.permission.BLUETOOTH_CONNECT)
        val miss = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (miss.isNotEmpty()) requestPermissions(miss.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startGps()
        if (prefs.autoObd && obd == null) obdConnect()
    }

    private val locListener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            Live.gpsFix = true
            Live.lat = l.latitude
            Live.lon = l.longitude
            Live.gpsSpeed = if (l.hasSpeed()) l.speed * 3.6f else 0f
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (gpsOn) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener)
            gpsOn = true
        } catch (e: Exception) {
        }
    }

    private fun fetchWeather() {
        val la = Live.lat
        val lo = Live.lon
        Thread {
            try {
                val u = URL("https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo&current=temperature_2m,weather_code")
                val c = u.openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                val s = c.inputStream.bufferedReader().readText()
                val cur = JSONObject(s).getJSONObject("current")
                Live.temp = Math.round(cur.getDouble("temperature_2m")).toInt()
                Live.weatherTxt = wmo(cur.getInt("weather_code"))
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun wmo(c: Int): String = when (c) {
        0 -> "Céu limpo"
        1, 2, 3 -> "Nublado"
        45, 48 -> "Neblina"
        in 51..57 -> "Garoa"
        in 61..67 -> "Chuva"
        in 71..77 -> "Neve"
        in 80..82 -> "Pancadas"
        in 95..99 -> "Tempestade"
        else -> ""
    }

    // ------------------------------------------------------------ OBD2

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<Pair<String, String>> = try {
        BluetoothAdapter.getDefaultAdapter()?.bondedDevices
            ?.map { Pair(it.name ?: it.address, it.address) } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun pickBluetooth(done: () -> Unit) {
        val l = bondedDevices()
        if (l.isEmpty()) {
            toast("Nenhum aparelho pareado. Pareie o ELM327 em Configurações > Bluetooth (senha 1234 ou 0000).")
            return
        }
        dialog().setTitle("Adaptador OBD2")
            .setItems(l.map { it.first + "  (" + it.second + ")" }.toTypedArray()) { _, k ->
                prefs.obdTipo = "bt"
                prefs.obdMac = l[k].second
                done()
            }.show()
    }

    @SuppressLint("MissingPermission")
    fun obdConnect() {
        obd?.stop()
        obd = null
        val wifi = prefs.obdTipo == "wifi"
        var dev: android.bluetooth.BluetoothDevice? = null
        if (!wifi) {
            dev = try {
                BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.firstOrNull { it.address == prefs.obdMac }
            } catch (e: Exception) {
                null
            }
            if (dev == null) {
                Live.obdState = "Escolha o adaptador em Ajustes"
                return
            }
        }
        val o = Obd(
            { s -> Live.obdState = s },
            { d -> Live.obd = d; Live.obdAt = System.currentTimeMillis() },
            { l -> Live.dtc = l; lab.showDtc() }
        )
        o.calib = prefs.calib
        obd = o
        if (wifi) o.start(null, prefs.obdWifi) else o.start(dev, null)
    }

    fun obdDisconnect() {
        obd?.stop()
        obd = null
        Live.obd = null
        Live.obdState = "Desconectado"
    }
}
