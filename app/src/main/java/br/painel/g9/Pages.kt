package br.painel.g9

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun lp(w: Int, h: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, h, wt)

// =====================================================================  HOME

class HomePage(private val a: MainActivity) : FrameLayout(a) {
    private val tvTime = label(a, "--:--", 46f, Color.WHITE, true)
    private val tvDate = label(a, "", 14f, DIM)
    private val tvWeather = label(a, "", 16f, Color.WHITE, true)
    private val tvObd = label(a, "", 12f, DIM, true)
    private val gauge = GaugeView(a)
    private val car = CarView(a)
    private val tvCons = label(a, "--,-", 54f, Color.WHITE, true)
    private val tvConsSub = label(a, "", 12f, DIM)
    private val tvRpm = label(a, "", 14f, DIM, true)
    private val tvInfo = label(a, "", 14f, DIM, true)
    private val tileRow = LinearLayout(a)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    private val dateFmt = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))

    init {
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(a.dp(24), a.dp(10), a.dp(24), a.dp(10))

        // cabeçalho
        val header = FrameLayout(a)
        val center = LinearLayout(a)
        center.orientation = LinearLayout.VERTICAL
        center.gravity = Gravity.CENTER_HORIZONTAL
        tvTime.gravity = Gravity.CENTER
        tvDate.gravity = Gravity.CENTER
        center.addView(tvTime, lp(WRAP, WRAP))
        center.addView(tvDate, lp(WRAP, WRAP))
        header.addView(center, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL))
        header.addView(tvObd, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.TOP))
        header.addView(tvWeather, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.TOP))
        col.addView(header, lp(MATCH, WRAP))

        // meio: velocidade | carro | consumo
        val mid = LinearLayout(a)
        mid.orientation = LinearLayout.HORIZONTAL

        val left = LinearLayout(a)
        left.orientation = LinearLayout.VERTICAL
        left.addView(gauge, lp(MATCH, 0, 1f))
        tvRpm.gravity = Gravity.CENTER
        left.addView(tvRpm, lp(MATCH, WRAP))
        mid.addView(left, lp(0, MATCH, 1f))

        mid.addView(car, lp(0, MATCH, 1.6f))

        val right = LinearLayout(a)
        right.orientation = LinearLayout.VERTICAL
        right.gravity = Gravity.CENTER
        val t1 = label(a, "CONSUMO", 12f, DIM, true)
        t1.gravity = Gravity.CENTER
        val t2 = label(a, "km/l", 16f, DIM, true)
        t2.gravity = Gravity.CENTER
        tvCons.gravity = Gravity.CENTER
        tvConsSub.gravity = Gravity.CENTER
        tvInfo.gravity = Gravity.CENTER
        right.addView(t1, lp(MATCH, WRAP))
        right.addView(tvCons, lp(MATCH, WRAP))
        right.addView(t2, lp(MATCH, WRAP))
        right.addView(tvConsSub, lp(MATCH, WRAP))
        right.addView(tvInfo, lp(MATCH, WRAP))
        mid.addView(right, lp(0, MATCH, 1f))

        col.addView(mid, lp(MATCH, 0, 1f))

        // atalhos
        tileRow.orientation = LinearLayout.HORIZONTAL
        col.addView(tileRow, lp(MATCH, a.dp(78)))

        addView(col, MATCH, MATCH)
    }

    fun applyTheme(t: PTheme) {
        gauge.accent = t.accent
        car.accent = t.accent
        car.bitmap = a.carBmp
        refreshTiles()
    }

    fun refreshTiles() {
        tileRow.removeAllViews()
        for (i in 0 until 5) {
            val pkg = a.prefs.slot(i)
            var text = "+ ESCOLHER"
            var icon: Drawable? = null
            try {
                val pm = a.packageManager
                text = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().uppercase()
                icon = pm.getApplicationIcon(pkg)
            } catch (e: Exception) {
            }
            val tile = makeTile(text, icon, false)
            tile.setOnClickListener {
                if (icon != null) a.launch(pkg) else a.pickApp(i)
            }
            tile.setOnLongClickListener { a.pickApp(i); true }
            tileRow.addView(tile, tileLp())
        }
        val apps = makeTile("TODOS OS APPS", null, true)
        apps.setOnClickListener { a.showPage(1) }
        tileRow.addView(apps, tileLp())
    }

    private fun tileLp(): LinearLayout.LayoutParams {
        val p = lp(0, MATCH, 1f)
        p.setMargins(a.dp(5), a.dp(6), a.dp(5), a.dp(6))
        return p
    }

    private fun makeTile(text: String, icon: Drawable?, filled: Boolean): TextView {
        val t = a.tema
        val v = label(a, text, 11f, Color.WHITE, true)
        v.gravity = Gravity.CENTER
        v.maxLines = 1
        v.setPadding(a.dp(6), a.dp(6), a.dp(6), a.dp(6))
        v.background = if (filled) cardBg(a, t.accent, t.accent, 12f)
        else cardBg(a, t.card, Color.argb(120, Color.red(t.accent), Color.green(t.accent), Color.blue(t.accent)), 12f)
        if (icon != null) {
            icon.setBounds(0, 0, a.dp(30), a.dp(30))
            v.setCompoundDrawables(null, icon, null, null)
            v.compoundDrawablePadding = a.dp(2)
        }
        return v
    }

    fun tick() {
        val now = Date()
        tvTime.text = timeFmt.format(now)
        tvDate.text = dateFmt.format(now).replaceFirstChar { it.uppercase() }

        val sp = Live.speed()
        gauge.max = 200f
        gauge.value = sp
        gauge.text = sp.toInt().toString()
        gauge.unit = "km/h"
        gauge.title = "VELOCIDADE"
        gauge.warn = a.prefs.limite > 0 && sp > a.prefs.limite

        val rpm = Live.rpm()
        tvRpm.text = if (rpm >= 0) "$rpm RPM" else ""

        val inst = Live.instKml()
        tvCons.text = if (inst != null) fmt1(minOf(inst, 99.9f)) else "--,-"
        val avg = Live.avgKml()
        tvConsSub.text = if (avg != null) "Média da viagem ${fmt1(avg)} km/l" else "Média da viagem --"

        val o = Live.obd
        tvInfo.text = if (o != null && Live.obdFresh() && o.coolant > -100) "Motor ${o.coolant}°C" else ""

        tvObd.text = "OBD · " + Live.obdState
        tvWeather.text = if (Live.temp != null) "${Live.temp}°C  ${Live.weatherTxt}" else ""
    }
}

// =====================================================================  APPS

class AppItem(val label: String, val pkg: String, val icon: Drawable)

class AppsPage(private val a: MainActivity) : FrameLayout(a) {
    private val grid = GridView(a)
    private var items: List<AppItem> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val it = items[position]
            val box = LinearLayout(a)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            box.setPadding(a.dp(6), a.dp(10), a.dp(6), a.dp(10))
            val img = ImageView(a)
            img.setImageDrawable(it.icon)
            box.addView(img, lp(a.dp(56), a.dp(56)))
            val tv = label(a, it.label, 12f, Color.WHITE)
            tv.gravity = Gravity.CENTER
            tv.maxLines = 2
            box.addView(tv, lp(MATCH, WRAP))
            return box
        }
    }

    init {
        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = a.dp(120)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.setPadding(a.dp(20), a.dp(14), a.dp(20), a.dp(14))
        grid.selector = ColorDrawable(Color.TRANSPARENT)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> a.launch(items[pos].pkg) }
        grid.setOnItemLongClickListener { _, _, pos, _ -> a.chooseSlot(items[pos].pkg); true }
        addView(grid, MATCH, MATCH)
    }

    fun load() {
        Thread {
            val l = a.listApps()
            a.runOnUiThread {
                items = l
                adapter.notifyDataSetChanged()
            }
        }.start()
    }
}

// =====================================================================  DRIVE LAB

class StatCard(a: MainActivity, titleText: String) : LinearLayout(a) {
    private val v = label(a, "--", 26f, Color.WHITE, true)

    init {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val t = label(a, titleText, 11f, DIM, true)
        t.gravity = Gravity.CENTER
        v.gravity = Gravity.CENTER
        addView(t, lp(MATCH, WRAP))
        addView(v, lp(MATCH, WRAP))
    }

    fun set(s: String) {
        v.text = s
    }

    fun skin(a: MainActivity) {
        background = cardBg(a, a.tema.card, Color.argb(40, 255, 255, 255))
    }
}

class LabPage(private val a: MainActivity) : FrameLayout(a) {
    private val rpm = GaugeView(a)
    private val spd = GaugeView(a)
    private val cTemp = StatCard(a, "TEMP. MOTOR")
    private val cVolt = StatCard(a, "BATERIA")
    private val cLoad = StatCard(a, "CARGA")
    private val cThr = StatCard(a, "ACELERADOR")
    private val tripBox = LinearLayout(a)
    private val dtcBox = LinearLayout(a)
    private val tvTrip = label(a, "", 15f, Color.WHITE, true)
    private val tvDtc = label(a, "Toque em LER CÓDIGOS com o adaptador conectado.", 13f, DIM)
    private val bReset = TextView(a)
    private val bRead = TextView(a)

    init {
        val col = LinearLayout(a)
        col.orientation = LinearLayout.HORIZONTAL
        col.setPadding(a.dp(20), a.dp(12), a.dp(20), a.dp(12))

        // esquerda: giro + velocidade, e cartões
        val left = LinearLayout(a)
        left.orientation = LinearLayout.VERTICAL
        val gaugesRow = LinearLayout(a)
        gaugesRow.orientation = LinearLayout.HORIZONTAL
        gaugesRow.addView(rpm, lp(0, MATCH, 1f))
        gaugesRow.addView(spd, lp(0, MATCH, 1f))
        left.addView(gaugesRow, lp(MATCH, 0, 1.5f))
        val cards = LinearLayout(a)
        cards.orientation = LinearLayout.HORIZONTAL
        for (c in listOf(cTemp, cVolt, cLoad, cThr)) {
            val p = lp(0, MATCH, 1f)
            p.setMargins(a.dp(4), a.dp(4), a.dp(4), a.dp(4))
            cards.addView(c, p)
        }
        left.addView(cards, lp(MATCH, 0, 0.7f))
        col.addView(left, lp(0, MATCH, 1.7f))

        // direita: viagem + códigos
        val right = LinearLayout(a)
        right.orientation = LinearLayout.VERTICAL
        tripBox.orientation = LinearLayout.VERTICAL
        tripBox.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
        tripBox.addView(label(a, "CONSUMO DA VIAGEM", 12f, DIM, true), lp(WRAP, WRAP))
        tripBox.addView(tvTrip, lp(MATCH, 0, 1f))
        styleBtn(bReset, "NOVA VIAGEM")
        bReset.setOnClickListener { Live.resetTrip() }
        tripBox.addView(bReset, lp(MATCH, WRAP))
        val pr = lp(MATCH, 0, 1f)
        pr.setMargins(a.dp(10), a.dp(4), 0, a.dp(6))
        right.addView(tripBox, pr)

        dtcBox.orientation = LinearLayout.VERTICAL
        dtcBox.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
        dtcBox.addView(label(a, "CÓDIGOS DE FALHA", 12f, DIM, true), lp(WRAP, WRAP))
        val sv = ScrollView(a)
        sv.addView(tvDtc, MATCH, WRAP)
        dtcBox.addView(sv, lp(MATCH, 0, 1f))
        styleBtn(bRead, "LER CÓDIGOS")
        bRead.setOnClickListener {
            if (a.obd != null && Live.obdFresh()) {
                tvDtc.text = "Lendo…"
                a.obd?.requestDtc()
            } else {
                a.toast("Conecte o adaptador OBD2 primeiro (Ajustes).")
            }
        }
        dtcBox.addView(bRead, lp(MATCH, WRAP))
        val pd = lp(MATCH, 0, 1f)
        pd.setMargins(a.dp(10), a.dp(6), 0, a.dp(4))
        right.addView(dtcBox, pd)
        col.addView(right, lp(0, MATCH, 1f))

        addView(col, MATCH, MATCH)
    }

    private fun styleBtn(b: TextView, text: String) {
        b.text = text
        b.setTextColor(Color.WHITE)
        b.typeface = HEAD
        b.textSize = 13f
        b.gravity = Gravity.CENTER
        b.setPadding(a.dp(10), a.dp(10), a.dp(10), a.dp(10))
    }

    fun applyTheme(t: PTheme) {
        rpm.accent = t.accent
        spd.accent = t.accent
        for (c in listOf(cTemp, cVolt, cLoad, cThr)) c.skin(a)
        tripBox.background = cardBg(a, t.card, Color.argb(40, 255, 255, 255))
        dtcBox.background = cardBg(a, t.card, Color.argb(40, 255, 255, 255))
        bReset.background = cardBg(a, t.accent, t.accent, 10f)
        bRead.background = cardBg(a, t.accent, t.accent, 10f)
    }

    fun showDtc() {
        val l = Live.dtc ?: return
        tvDtc.text = if (l.isEmpty()) "Nenhum código armazenado ✓"
        else l.joinToString("\n\n") { it + "\n" + Obd.describe(it) }
    }

    fun tick() {
        val o = Live.obd
        val fresh = o != null && Live.obdFresh()
        rpm.max = 7000f
        rpm.value = if (fresh && o!!.rpm >= 0) o.rpm.toFloat() else 0f
        rpm.text = if (fresh && o!!.rpm >= 0) o.rpm.toString() else "--"
        rpm.unit = "rpm"
        rpm.title = "GIRO"
        spd.max = 200f
        val sp = Live.speed()
        spd.value = sp
        spd.text = sp.toInt().toString()
        spd.unit = "km/h"
        spd.title = "VELOCIDADE"
        spd.warn = a.prefs.limite > 0 && sp > a.prefs.limite

        cTemp.set(if (fresh && o!!.coolant > -100) "${o.coolant}°C" else "--")
        cVolt.set(if (fresh && o!!.volt > 0f) "${fmt1(o.volt)} V" else "--")
        cLoad.set(if (fresh && o!!.load >= 0) "${o.load}%" else "--")
        cThr.set(if (fresh && o!!.throttle >= 0) "${o.throttle}%" else "--")

        val inst = Live.instKml()
        val avg = Live.avgKml()
        val min = Live.tripMs / 60000L
        val cost = Live.tripL * a.prefs.preco
        tvTrip.text = "Agora: " + (if (inst != null) fmt1(minOf(inst, 99.9f)) + " km/l" else "--") +
            "\nMédia: " + (if (avg != null) fmt1(avg) + " km/l" else "--") +
            "\nDistância: " + fmt1(Live.tripKm) + " km" +
            "\nCombustível: " + fmt2(Live.tripL) + " L" +
            "\nGasto: R$ " + fmt2(cost) +
            "\nTempo: " + min + " min"
    }
}

// =====================================================================  AJUSTES

class SettingsPage(private val a: MainActivity) : ScrollView(a) {
    private val box = LinearLayout(a)
    private var tvState: TextView? = null

    init {
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(a.dp(24), a.dp(14), a.dp(24), a.dp(14))
        addView(box, MATCH, WRAP)
    }

    private fun btn(text: String, active: Boolean = false, onClick: () -> Unit): TextView {
        val t = label(a, text.uppercase(), 13f, Color.WHITE, true)
        t.gravity = Gravity.CENTER
        t.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
        val acc = a.tema.accent
        t.background = if (active) cardBg(a, acc, acc, 10f)
        else cardBg(a, Color.argb(50, 255, 255, 255), Color.argb(90, 255, 255, 255), 10f)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun head(text: String) {
        val t = label(a, text, 13f, a.tema.accent, true)
        t.setPadding(0, a.dp(14), 0, a.dp(6))
        box.addView(t, lp(WRAP, WRAP))
    }

    private fun row(title: String, sub: String?, vararg views: View) {
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(10))
        r.background = cardBg(a, a.tema.card, Color.argb(40, 255, 255, 255))
        val c = LinearLayout(a)
        c.orientation = LinearLayout.VERTICAL
        c.addView(label(a, title, 16f, Color.WHITE, true), lp(WRAP, WRAP))
        if (sub != null) c.addView(label(a, sub, 12f, DIM), lp(WRAP, WRAP))
        r.addView(c, lp(0, WRAP, 1f))
        for (v in views) {
            val p = lp(WRAP, WRAP)
            p.leftMargin = a.dp(8)
            r.addView(v, p)
        }
        val p = lp(MATCH, WRAP)
        p.bottomMargin = a.dp(8)
        box.addView(r, p)
    }

    private fun value(text: String): TextView {
        val t = label(a, text, 16f, Color.WHITE, true)
        t.gravity = Gravity.CENTER
        t.minWidth = a.dp(70)
        return t
    }

    fun build() {
        box.removeAllViews()
        val prefs = a.prefs

        head("APARÊNCIA")
        row("Tema", null, *Themes.all.map { th -> btn(th.nome, th.id == a.tema.id) { a.setTema(th.id) } }.toTypedArray())
        row("Foto do carro (PNG com fundo transparente)", "Se não houver foto, aparece a silhueta na cor do tema",
            btn("Escolher") { a.pickImage(0) },
            btn("Da pasta") { a.reloadFromFolder(0) },
            btn("Remover") { a.removeImage(0) })
        row("Foto de fundo", "Aparece escurecida atrás de tudo",
            btn("Escolher") { a.pickImage(1) },
            btn("Da pasta") { a.reloadFromFolder(1) },
            btn("Remover") { a.removeImage(1) })
        row("Boas-vindas ao ligar", "Mostra \"Bem-vindo ao ${prefs.nome}!\"",
            btn(if (prefs.boasVindas) "Ligado" else "Desligado", prefs.boasVindas) {
                prefs.boasVindas = !prefs.boasVindas; build()
            },
            btn("Mudar nome") { a.askText("Nome do carro", prefs.nome) { prefs.nome = it; build() } })

        head("VELOCIDADE E COMBUSTÍVEL")
        row("Aviso sonoro de velocidade", "Apita ao passar do limite (0 = desligado)",
            btn("−") { prefs.limite = maxOf(0, prefs.limite - 10); build() },
            value(if (prefs.limite == 0) "OFF" else "${prefs.limite} km/h"),
            btn("+") { prefs.limite = minOf(200, prefs.limite + 10); build() })
        row("Preço do combustível", "Usado para calcular o gasto da viagem",
            btn("−") { prefs.preco = maxOf(1f, prefs.preco - 0.10f); build() },
            value("R$ " + fmt2(prefs.preco)),
            btn("+") { prefs.preco = minOf(20f, prefs.preco + 0.10f); build() })
        row("Calibração do consumo", "Ajuste até a média bater com a do tanque (abasteça cheio e compare)",
            btn("−") { prefs.calib = maxOf(0.5f, prefs.calib - 0.05f); a.obd?.calib = prefs.calib; build() },
            value("${(prefs.calib * 100).toInt()}%"),
            btn("+") { prefs.calib = minOf(1.5f, prefs.calib + 0.05f); a.obd?.calib = prefs.calib; build() })

        head("ADAPTADOR OBD2 (ELM327)")
        val sel = if (prefs.obdTipo == "wifi") "Wi-Fi: ${prefs.obdWifi}"
        else if (prefs.obdMac.isEmpty()) "Nenhum adaptador escolhido" else "Bluetooth: ${prefs.obdMac}"
        row("Tipo de conexão", sel,
            btn("Bluetooth", prefs.obdTipo == "bt") { prefs.obdTipo = "bt"; build() },
            btn("Wi-Fi", prefs.obdTipo == "wifi") { prefs.obdTipo = "wifi"; build() })
        if (prefs.obdTipo == "wifi") {
            row("Endereço do adaptador Wi-Fi", "Padrão da maioria: 192.168.0.10:35000",
                btn("Editar") { a.askText("IP:porta", prefs.obdWifi) { prefs.obdWifi = it; build() } })
        } else {
            row("Adaptador Bluetooth", "Pareie antes em Configurações > Bluetooth (senha 1234 ou 0000)",
                btn("Escolher") { a.pickBluetooth { build() } })
        }
        val st = value(Live.obdState)
        st.minWidth = a.dp(120)
        tvState = st
        row("Conexão", "Só lê dados. Nunca apaga códigos nem escreve na central do carro.",
            st,
            btn("Conectar") { a.obdConnect() },
            btn("Desconectar") { a.obdDisconnect() })
        row("Conectar sozinho ao abrir", null,
            btn(if (prefs.autoObd) "Ligado" else "Desligado", prefs.autoObd) { prefs.autoObd = !prefs.autoObd; build() })

        head("SISTEMA")
        row("Launcher padrão", "Escolha \"Painel G9\" e toque em Sempre",
            btn("Definir") { a.openSystem(Settings.ACTION_HOME_SETTINGS) })
        row("Mostrar a música que está tocando", "Libere \"Painel G9\" no acesso a notificações",
            btn("Abrir") { a.openSystem(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) })
        row("Configurações do Android", null,
            btn("Abrir") { a.openSystem(Settings.ACTION_SETTINGS) })
        val about = label(a, "Painel G9 1.0 · launcher independente para central Android", 12f, DIM)
        about.setPadding(0, a.dp(10), 0, a.dp(20))
        box.addView(about, lp(WRAP, WRAP))
    }

    fun tick() {
        tvState?.text = Live.obdState
    }
}
