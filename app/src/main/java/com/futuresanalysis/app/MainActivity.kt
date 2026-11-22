package com.futuresanalysis.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FuturesAnalysisScreen() }
    }
}

private val networkExecutor = Executors.newSingleThreadExecutor()
private val mainHandler = Handler(Looper.getMainLooper())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuturesAnalysisScreen() {
    var symbol by remember { mutableStateOf("BTC/USDT") }
    var price by remember { mutableStateOf("") }
    var change by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("برای دریافت قیمت عمومی، نماد را وارد کنید.") }
    var loading by remember { mutableStateOf(false) }
    var comparisonPrice by remember { mutableStateOf("") }
    var comparisonResult by remember { mutableStateOf("") }
    val context = LocalContext.current

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Futures Analysis 1.47") }) }) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("داده عمومی بازار", style = MaterialTheme.typography.titleMedium)
                Text("قیمت عمومی بازار از API عمومی Binance دریافت می‌شود؛ بدون ورود به حساب و بدون کلید API.")
                OutlinedTextField(value = symbol, onValueChange = { symbol = it },
                    label = { Text("نماد (مثلاً BTC/USDT)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(enabled = !loading, onClick = {
                    val normalized = symbol.trim().uppercase().replace("/", "").replace("-", "").replace("_", "")
                    if (!normalized.matches(Regex("[A-Z0-9]{5,20}"))) {
                        status = "نماد معتبر نیست. نمونه: BTC/USDT"
                    } else {
                        loading = true
                        status = "در حال دریافت داده عمومی..."
                        networkExecutor.execute {
                            var result: String? = null
                            var error: String? = null
                            try {
                                val connection = (URL("https://api.binance.com/api/v3/ticker/price?symbol=$normalized")
                                    .openConnection() as HttpURLConnection).apply {
                                    requestMethod = "GET"
                                    connectTimeout = 10000
                                    readTimeout = 10000
                                }
                                try {
                                    val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                                        .bufferedReader().use { it.readText() }
                                    if (connection.responseCode in 200..299) result = JSONObject(body).getString("price")
                                    else error = "نماد پیدا نشد یا سرویس پاسخ مناسبی نداد."
                                } finally { connection.disconnect() }
                            } catch (_: Exception) {
                                error = "دریافت اطلاعات ناموفق بود؛ اتصال اینترنت را بررسی کنید."
                            }
                            mainHandler.post {
                                loading = false
                                if (result != null) {
                                    price = result!!
                                    status = "قیمت عمومی دریافت شد؛ ممکن است با قیمت صرافی‌های دیگر تفاوت داشته باشد."
                                } else status = error ?: "خطای نامشخص"
                            }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "در حال دریافت..." else "دریافت قیمت عمومی") }
                Text(status, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = price, onValueChange = { price = it },
                    label = { Text("قیمت فعلی") }, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("مقایسه آموزشی قیمت‌ها", style = MaterialTheme.typography.titleMedium)
                Text("دو قیمت را مقایسه کنید؛ این بخش فقط درصد تغییر را محاسبه می‌کند و معامله‌ای انجام نمی‌دهد.")
                OutlinedTextField(value = comparisonPrice, onValueChange = { comparisonPrice = it },
                    label = { Text("قیمت مبنا") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val start = comparisonPrice.toDoubleOrNull()
                    val end = price.toDoubleOrNull()
                    comparisonResult = if (start != null && end != null && start > 0.0) {
                        val pct = ((end - start) / start) * 100.0
                        "تغییر قیمت: " + String.format("%.2f", pct) + "٪"
                    } else "برای محاسبه، قیمت مبنا و قیمت فعلی معتبر وارد کنید."
                }, modifier = Modifier.fillMaxWidth()) { Text("محاسبه درصد تغییر") }
                if (comparisonResult.isNotBlank()) Text(comparisonResult, style = MaterialTheme.typography.titleSmall)
                HorizontalDivider()
                Text("یادداشت تحلیل", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = change, onValueChange = { change = it },
                    label = { Text("درصد تغییر دلخواه (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("توضیحات و مشاهدات") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Button(onClick = {
                    val prompt = "لطفاً فقط تحلیل آموزشی و غیر اجرایی ارائه کن؛ هیچ توصیه قطعی خرید/فروش یا سفارش واقعی نده.\nنماد: $symbol\nقیمت عمومی دریافت‌شده/واردشده: $price\nدرصد تغییر: $change\nمشاهدات: $notes\nعدم قطعیت‌ها و ریسک‌های کلی را توضیح بده."
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, prompt)
                    }
                    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری متن تحلیل"))
                }, modifier = Modifier.fillMaxWidth()) { Text("اشتراک‌گذاری متن تحلیل") }
                Text("ایمنی: فقط داده عمومی خوانده می‌شود. برنامه به حساب صرافی وصل نمی‌شود، کلید یا رمز نمی‌خواهد و هیچ سفارشی ثبت نمی‌کند.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
