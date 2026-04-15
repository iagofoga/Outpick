package com.example.op

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

// --- COLORES ---
val FondoPrincipal = Color.White
val FondoTarjetas = Color(0xFFF5F5F5)
val Fucsia = Color(0xFFFF00FF)
val DoradoPremium = Color(0xFFFFD700)
val ColorTexto = Color(0xFF121212)

// --- DATOS Y MODELOS ---
data class Prenda(val id: String, val nombre: String, val zona: String, val nombreArchivo: String)
data class Outfit(val id: Int, var nombre: String, var prendas: Map<String, Prenda> = mapOf())
data class OutfitCerrado(val nombre: String, val nombreFoto: String)

val catalogoCerrado = List(30) { index -> OutfitCerrado("Opción ${index + 1}", "opcion${index + 1}") }
val zonasDisponibles = listOf("Gorro/Sombrero", "Gafas", "Camisa", "Camiseta", "Sudadera", "Jersey", "Chaqueta", "Bufanda", "Piernas", "Pies", "Collares", "Pendientes", "Anillos")

// --- FUNCIÓN PARA DESCARGAR IMÁGENES ---
@Composable
fun CargarImagenDesdeInternet(url: String): Bitmap? {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                val stream = URL(url).openStream()
                bitmap = android.graphics.BitmapFactory.decodeStream(stream)
            } catch (e: Exception) {}
        }
    }
    return bitmap
}

// --- LÓGICA DE INVENTARIO ---
fun obtenerSufijoZona(zona: String): String {
    return when(zona) {
        "Gorro/Sombrero" -> "gorro"
        "Gafas" -> "gafas"
        "Camisa" -> "camisa"
        "Camiseta" -> "camiseta"
        "Sudadera" -> "sudadera"
        "Jersey" -> "jersey"
        "Chaqueta" -> "chaqueta"
        "Bufanda" -> "bufanda"
        "Piernas" -> "piernas"
        "Pies" -> "pies"
        "Collares" -> "collares"
        "Pendientes" -> "pendientes"
        "Anillos" -> "anillos"
        else -> "extra"
    }
}

fun generarInventario(): Map<String, List<Prenda>> {
    val inventario = mutableMapOf<String, List<Prenda>>()
    for (zona in zonasDisponibles) {
        val sufijo = obtenerSufijoZona(zona)
        val prendasDeEstaZona = List(12) { index ->
            val numOutfit = index + 1
            Prenda(id = "o${numOutfit}_$sufijo", nombre = "$zona $numOutfit", zona = zona, nombreArchivo = "o${numOutfit}_$sufijo")
        }
        inventario[zona] = prendasDeEstaZona
    }
    return inventario
}

val inventarioGlobal = generarInventario()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppPrincipal() }
    }
}

@Composable
fun AppPrincipal() {
    var mostrarSplash by rememberSaveable { mutableStateOf(true) }
    if (mostrarSplash) {
        LaunchedEffect(Unit) { delay(4500); mostrarSplash = false }
        PantallaSplash()
    } else {
        AppOutpick()
    }
}

@Composable
fun PantallaSplash() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.video_splash}"))
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    start()
                    setOnPreparedListener { mp -> mp.isLooping = true }
                }
            }, modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOutpick() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("OutpickDatos", Context.MODE_PRIVATE)

    var pilaPantallas by rememberSaveable { mutableStateOf(listOf("Inicio")) }
    val pantallaActual = pilaPantallas.last()

    fun navegarA(pantalla: String, esTabPrincipal: Boolean = false) {
        if (pantallaActual == pantalla) return
        if (esTabPrincipal) pilaPantallas = listOf(pantalla) else pilaPantallas = pilaPantallas + pantalla
    }
    fun irAtras() { if (pilaPantallas.size > 1) pilaPantallas = pilaPantallas.dropLast(1) }

    var nombreUsuario by remember { mutableStateOf(prefs.getString("perfil_nombre", "") ?: "") }
    var emailUsuario by remember { mutableStateOf(prefs.getString("perfil_email", "") ?: "") }
    var fotoUriString by remember { mutableStateOf(prefs.getString("perfil_foto", "") ?: "") }

    val listaOutfits = remember { mutableStateListOf(*Array(30) { Outfit(it, prefs.getString("outfit_$it", "Outfit ${it + 1}") ?: "Outfit ${it + 1}") }) }
    var outfitSeleccionadoIndex by remember { mutableStateOf(0) }
    var zonaASeleccionar by remember { mutableStateOf("") }
    var fotoAmpliadaNombre by rememberSaveable { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen || pilaPantallas.size > 1) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else irAtras()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = FondoPrincipal) {
                Spacer(modifier = Modifier.height(30.dp))
                Text("Menú de Outpick", modifier = Modifier.padding(start = 16.dp, bottom = 4.dp), color = Fucsia, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("🏆 1º PREMIO EDUEMPRENDE IDEA 2025", modifier = Modifier.padding(start = 16.dp, bottom = 16.dp), color = Color(0xFF1976D2), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                HorizontalDivider(color = Color.LightGray)
                NavigationDrawerItem(label = { Text("Mi Perfil") }, selected = pantallaActual == "Perfil", onClick = { navegarA("Perfil"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Person, null) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
                NavigationDrawerItem(label = { Text("Mis Medidas") }, selected = pantallaActual == "Medidas", onClick = { navegarA("Medidas"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Accessibility, null) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
                NavigationDrawerItem(label = { Text("Colorimetría") }, selected = pantallaActual == "Colorimetria", onClick = { navegarA("Colorimetria"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Palette, null) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)
                NavigationDrawerItem(label = { Text("Outpick Premium", color = DoradoPremium, fontWeight = FontWeight.Bold) }, selected = pantallaActual == "Premium", onClick = { navegarA("Premium"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Star, null, tint = DoradoPremium) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)
                NavigationDrawerItem(label = { Text("FAQ & Nosotros") }, selected = pantallaActual == "FAQ", onClick = { navegarA("FAQ"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Info, null) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
                NavigationDrawerItem(label = { Text("Recomendaciones") }, selected = pantallaActual == "Recomendaciones", onClick = { navegarA("Recomendaciones"); scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.MailOutline, null) }, colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent))
                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(color = Color.LightGray)
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Una idea de:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Armony", color = Fucsia, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Jose M. Piñan Romero\nPalmira Gómez Durán\nIago Fontenla Gago\nFátima ElGad", color = Color.DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = FondoPrincipal,
            bottomBar = {
                NavigationBar(containerColor = FondoPrincipal) {
                    NavigationBarItem(selected = pantallaActual == "Inicio", onClick = { navegarA("Inicio", true) }, icon = { Icon(Icons.Default.Home, null) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Fucsia, indicatorColor = Color.Transparent))
                    NavigationBarItem(selected = pantallaActual in listOf("MiArmario", "DetalleOutfit", "ComoMeQueda", "SeleccionarPrenda"), onClick = { navegarA("MiArmario", true) }, icon = { Icon(Icons.Default.Star, null) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Fucsia, indicatorColor = Color.Transparent))
                    NavigationBarItem(selected = pantallaActual in listOf("CatalogoCerrado", "VisorFoto"), onClick = { navegarA("CatalogoCerrado", true) }, icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Fucsia, indicatorColor = Color.Transparent))
                }
            }
        ) { pv ->
            Box(modifier = Modifier.padding(pv)) {
                AnimatedContent(
                    targetState = pantallaActual,
                    transitionSpec = {
                        if (targetState == "ComoMeQueda" || targetState == "VisorFoto") {
                            (scaleIn(initialScale = 0.8f, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))) togetherWith (scaleOut(targetScale = 1.2f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)))
                        } else if (initialState == "ComoMeQueda" || initialState == "VisorFoto") {
                            (scaleIn(initialScale = 1.2f, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))) togetherWith (scaleOut(targetScale = 0.8f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)))
                        } else { fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250)) }
                    }, label = "animaciones"
                ) { pantalla ->
                    when (pantalla) {
                        "Inicio" -> PantallaInicio { scope.launch { drawerState.open() } }
                        "CatalogoCerrado" -> PantallaCatalogoCerrado(onVerFoto = { fotoAmpliadaNombre = it; navegarA("VisorFoto") }, onIrAPremium = { navegarA("Premium") })
                        "VisorFoto" -> PantallaVisorFoto(nombreFoto = fotoAmpliadaNombre, onVolver = { irAtras() })
                        "MiArmario" -> PantallaMiArmario(listaOutfits, onIrAPremium = { navegarA("Premium") }, onAbrirOutfit = { idx -> outfitSeleccionadoIndex = idx; navegarA("DetalleOutfit") })
                        "DetalleOutfit" -> PantallaDetalleOutfit(
                            outfit = listaOutfits[outfitSeleccionadoIndex],
                            onActualizarNombre = { nuevo -> listaOutfits[outfitSeleccionadoIndex] = listaOutfits[outfitSeleccionadoIndex].copy(nombre = nuevo); prefs.edit().putString("outfit_$outfitSeleccionadoIndex", nuevo).apply() },
                            onVer = { navegarA("ComoMeQueda") },
                            onAdd = { zona -> zonaASeleccionar = zona; navegarA("SeleccionarPrenda") }
                        )
                        "SeleccionarPrenda" -> PantallaElegirPrenda(
                            zona = zonaASeleccionar, onVolver = { irAtras() },
                            onPrendaSeleccionada = { prendaElegida ->
                                val prendasActuales = listaOutfits[outfitSeleccionadoIndex].prendas.toMutableMap()
                                prendasActuales[zonaASeleccionar] = prendaElegida
                                listaOutfits[outfitSeleccionadoIndex] = listaOutfits[outfitSeleccionadoIndex].copy(prendas = prendasActuales)
                                irAtras()
                            }
                        )
                        "ComoMeQueda" -> PantallaResultado2D(outfitId = outfitSeleccionadoIndex) { irAtras() }
                        "Perfil" -> PantallaPerfil(
                            nombreUsuario, emailUsuario, fotoUriString,
                            onNombreChange = { nombreUsuario = it; prefs.edit().putString("perfil_nombre", it).apply() },
                            onEmailChange = { emailUsuario = it; prefs.edit().putString("perfil_email", it).apply() },
                            onFotoChange = { fotoUriString = it; prefs.edit().putString("perfil_foto", it).apply() },
                            onVolver = { irAtras() }, onSacarFoto = { navegarA("SacarFoto") }
                        )
                        "SacarFoto" -> PantallaSacarFoto { irAtras() }
                        "Medidas" -> PantallaMedidas(prefs) { irAtras() }
                        "Colorimetria" -> PantallaColorimetria(prefs) { irAtras() }
                        "Premium" -> PantallaPremium { irAtras() }
                        "FAQ" -> PantallaFAQ { irAtras() }
                        "Recomendaciones" -> PantallaRecomendaciones { irAtras() }
                    }
                }
            }
        }
    }
}

@Composable
fun PantallaInicio(onAbrirMenu: () -> Unit) {
    val estadoColumna1 = rememberLazyListState()
    val estadoColumna2 = rememberLazyListState(initialFirstVisibleItemIndex = 500)
    LaunchedEffect(Unit) { while (true) { estadoColumna1.animateScrollBy(value = 3000f, animationSpec = tween(durationMillis = 10000, easing = LinearEasing)) } }
    LaunchedEffect(Unit) { while (true) { estadoColumna2.animateScrollBy(value = -3000f, animationSpec = tween(durationMillis = 10000, easing = LinearEasing)) } }
    val fotos1 = listOf(R.drawable.i1, R.drawable.i2, R.drawable.i3, R.drawable.i4, R.drawable.i5)
    val fotos2 = listOf(R.drawable.i6, R.drawable.i7, R.drawable.i8, R.drawable.i9, R.drawable.i10)
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(state = estadoColumna1, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                items(1000) { index -> Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(12.dp)).background(Color.LightGray)) { Image(painterResource(id = fotos1[index % fotos1.size]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
            }
            LazyColumn(state = estadoColumna2, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                items(1000) { index -> Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(12.dp)).background(Color.LightGray)) { Image(painterResource(id = fotos2[index % fotos2.size]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
            }
        }
        IconButton(onClick = onAbrirMenu, modifier = Modifier.padding(top = 65.dp, start = 16.dp).background(Color.White.copy(alpha = 0.8f), CircleShape)) { Icon(Icons.Default.Menu, null, tint = Fucsia) }
    }
}

@Composable
fun PantallaCatalogoCerrado(onVerFoto: (String) -> Unit, onIrAPremium: () -> Unit) {
    var tabSeleccionada by remember { mutableStateOf(0) }
    var mostrarDialogoPremium by remember { mutableStateOf(false) }
    val tabs = listOf("Favoritos", "Comunidad", "IAPick")
    val context = LocalContext.current

    if (mostrarDialogoPremium) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoPremium = false }, title = { Text("Premium", fontWeight = FontWeight.Bold, color = DoradoPremium) },
            text = { Text("Actualiza a Premium para desbloquear los favoritos exclusivos de nuestra comunidad y guardar más espacios.") },
            confirmButton = { Button(onClick = { mostrarDialogoPremium = false; onIrAPremium() }, colors = ButtonDefaults.buttonColors(containerColor = DoradoPremium)) { Text("Ir a Premium", color = Color.Black, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { mostrarDialogoPremium = false }) { Text("Cancelar", color = Color.Gray) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabSeleccionada, containerColor = FondoPrincipal, contentColor = Fucsia) { tabs.forEachIndexed { index, title -> Tab(selected = tabSeleccionada == index, onClick = { tabSeleccionada = index }, text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp) }) } }
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (tabSeleccionada) {
                0 -> {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(catalogoCerrado.size) { index ->
                            val outfit = catalogoCerrado[index]
                            val estaBloqueado = index >= 12
                            Card(modifier = Modifier.fillMaxWidth().clickable { if (estaBloqueado) mostrarDialogoPremium = true else onVerFoto(outfit.nombreFoto) }, colors = CardDefaults.cardColors(containerColor = FondoTarjetas)) {
                                Column {
                                    Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(Color.LightGray)) {
                                        val idImagen = remember(outfit.nombreFoto) { context.resources.getIdentifier(outfit.nombreFoto, "drawable", context.packageName) }
                                        if (idImagen != 0) { Image(painter = painterResource(id = idImagen), contentDescription = null, modifier = Modifier.fillMaxSize().background(Color.White), contentScale = ContentScale.Fit) } else { Icon(Icons.Default.Image, null, modifier = Modifier.size(50.dp).align(Alignment.Center), tint = Color.Gray) }
                                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = if (estaBloqueado) Color.LightGray else Fucsia, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp))
                                        if (index < 12) { Box(modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Fucsia).padding(horizontal = 6.dp, vertical = 2.dp).align(Alignment.TopStart)) { Text("Opción Outpick", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } else { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(32.dp)) } }
                                    }
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(outfit.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                        Button(onClick = { if (estaBloqueado) mostrarDialogoPremium = true else onVerFoto(outfit.nombreFoto) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = if (estaBloqueado) Color.Gray else Fucsia), contentPadding = PaddingValues(0.dp)) { Text(if (estaBloqueado) "Premium" else "Ver", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }
                1, 2 -> { Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(if(tabSeleccionada == 1) Icons.Default.Groups else Icons.Default.AutoAwesome, null, modifier = Modifier.size(80.dp), tint = Color.LightGray); Spacer(modifier = Modifier.height(16.dp)); Text(if(tabSeleccionada == 1) "Selección de la comunidad" else "IAPick te sugiere outfits", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = ColorTexto); Spacer(modifier = Modifier.height(16.dp)); Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))) { Text("🚧 Función en desarrollo", modifier = Modifier.padding(12.dp), color = Color(0xFF856404), fontWeight = FontWeight.Bold) } } }
            }
        }
    }
}

@Composable
fun PantallaVisorFoto(nombreFoto: String, onVolver: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        val idImagen = remember(nombreFoto) { context.resources.getIdentifier(nombreFoto, "drawable", context.packageName) }
        if (idImagen != 0) { Image(painter = painterResource(id = idImagen), contentDescription = "Foto Ampliada", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } else { Icon(Icons.Default.Image, null, modifier = Modifier.size(100.dp).align(Alignment.Center), tint = Color.Gray) }
        IconButton(onClick = onVolver, modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.5f), CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar", tint = Color.White) }
    }
}

@Composable
fun PantallaMiArmario(outfits: List<Outfit>, onIrAPremium: () -> Unit, onAbrirOutfit: (Int) -> Unit) {
    var mostrarDialogoPremium by remember { mutableStateOf(false) }
    var tabSeleccionada by remember { mutableStateOf(0) }
    val tabs = listOf("Mis Outfits", "Mis Prendas")

    if (mostrarDialogoPremium) {
        AlertDialog(onDismissRequest = { mostrarDialogoPremium = false }, title = { Text("Premium", fontWeight = FontWeight.Bold, color = DoradoPremium) }, text = { Text("Actualiza a Premium para desbloquear hasta 30 espacios.") }, confirmButton = { Button(onClick = { mostrarDialogoPremium = false; onIrAPremium() }, colors = ButtonDefaults.buttonColors(containerColor = DoradoPremium)) { Text("Ir a Premium", color = Color.Black, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { mostrarDialogoPremium = false }) { Text("Cancelar", color = Color.Gray) } })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabSeleccionada, containerColor = FondoPrincipal, contentColor = Fucsia) { tabs.forEachIndexed { index, title -> Tab(selected = tabSeleccionada == index, onClick = { tabSeleccionada = index }, text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp) }) } }
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (tabSeleccionada == 0) {
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(30) { index ->
                        val estaBloqueado = index >= 12
                        Card(modifier = Modifier.aspectRatio(1f).clickable { if (estaBloqueado) mostrarDialogoPremium = true else onAbrirOutfit(index) }, colors = CardDefaults.cardColors(containerColor = FondoTarjetas)) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = if (estaBloqueado) Icons.Default.Lock else Icons.Default.Checkroom, null, tint = if (estaBloqueado) Color.LightGray else Fucsia, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = outfits[index].nombre, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(80.dp), tint = Color.LightGray); Spacer(modifier = Modifier.height(16.dp)); Text("Sube fotos y organiza tu ropa\n(Camisetas, Pantalones...)", fontSize = 18.sp, textAlign = TextAlign.Center, color = ColorTexto); Spacer(modifier = Modifier.height(24.dp)); Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))) { Text("🚧 Función en desarrollo", modifier = Modifier.padding(12.dp), color = Color(0xFF856404), fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

@Composable
fun PantallaDetalleOutfit(outfit: Outfit, onActualizarNombre: (String) -> Unit, onVer: () -> Unit, onAdd: (String) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = outfit.nombre, onValueChange = { onActualizarNombre(it) }, label = { Text("Nombre del Outfit") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Fucsia, focusedLabelColor = Fucsia))
        Spacer(modifier = Modifier.height(20.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(zonasDisponibles) { zona ->
                Card(modifier = Modifier.aspectRatio(1f).clickable { onAdd(zona) }, colors = CardDefaults.cardColors(containerColor = FondoTarjetas)) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = zona, color = Fucsia, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (outfit.prendas.containsKey(zona)) {
                            val prendaSeleccionada = outfit.prendas[zona]!!
                            val idImagenPrenda = remember(prendaSeleccionada.nombreArchivo) { context.resources.getIdentifier(prendaSeleccionada.nombreArchivo, "drawable", context.packageName) }
                            if (idImagenPrenda != 0) { Image(painter = painterResource(id = idImagenPrenda), null, modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) } else { Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.LightGray), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, null, tint = Color.Gray, modifier = Modifier.size(32.dp)) } }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = prendaSeleccionada.nombre, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
                        } else { Icon(Icons.Default.AddCircleOutline, null, tint = Color.Gray, modifier = Modifier.size(40.dp)) }
                    }
                }
            }
        }
        Button(onClick = onVer, modifier = Modifier.fillMaxWidth().height(55.dp).padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Fucsia), shape = RoundedCornerShape(12.dp)) { Text("Ver Previsualización", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun PantallaElegirPrenda(zona: String, onVolver: () -> Unit, onPrendaSeleccionada: (Prenda) -> Unit) {
    val context = LocalContext.current
    val prendasDisponibles = inventarioGlobal[zona] ?: emptyList()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }; Text("Elige para: $zona", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Fucsia) }
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(prendasDisponibles) { prenda ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onPrendaSeleccionada(prenda) }, colors = CardDefaults.cardColors(containerColor = FondoTarjetas)) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.LightGray), contentAlignment = Alignment.Center) {
                            val idImagen = remember(prenda.nombreArchivo) { context.resources.getIdentifier(prenda.nombreArchivo, "drawable", context.packageName) }
                            if (idImagen != 0) { Image(painter = painterResource(id = idImagen), null, modifier = Modifier.fillMaxSize().background(Color.White), contentScale = ContentScale.Crop) } else { Icon(Icons.Default.Image, "Sin foto", tint = Color.Gray, modifier = Modifier.size(40.dp)) }
                        }
                        Text(prenda.nombre, Modifier.padding(12.dp).fillMaxWidth(), fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// --- PERFIL CON FOTO STAND DESDE SUPABASE (ARREGLADA PARA QUE NO SE CORTE) ---
@Composable
fun PantallaPerfil(
    nombre: String, email: String, fotoUri: String,
    onNombreChange: (String) -> Unit, onEmailChange: (String) -> Unit, onFotoChange: (String) -> Unit,
    onVolver: () -> Unit, onSacarFoto: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { onFotoChange(it.toString()) } }
    var bitmap by remember(fotoUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(fotoUri) { if (fotoUri.isNotEmpty()) { try { val uri = Uri.parse(fotoUri); bitmap = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) else MediaStore.Images.Media.getBitmap(context.contentResolver, uri) } catch (e: Exception) {} } }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            Text("Mi Perfil", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Fucsia)
            Spacer(modifier = Modifier.height(30.dp))
            Box(modifier = Modifier.size(150.dp).clip(CircleShape).background(FondoTarjetas).clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) {
                if (bitmap != null) { Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } else { Icon(Icons.Default.AddAPhoto, null, Modifier.size(50.dp), tint = Color.Gray) }
            }
            Spacer(modifier = Modifier.height(30.dp))
            OutlinedTextField(value = nombre, onValueChange = onNombreChange, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = email, onValueChange = onEmailChange, label = { Text("Correo Electrónico") }, modifier = Modifier.fillMaxWidth())
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color.LightGray)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Bienvenido al Stand de Outpick", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Fucsia)
            Spacer(modifier = Modifier.height(8.dp))
            Text("A continuación le sacaremos una foto para generar el outfit.", textAlign = TextAlign.Center, color = Color.DarkGray, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onSacarFoto, colors = ButtonDefaults.buttonColors(containerColor = Fucsia), modifier = Modifier.height(50.dp).fillMaxWidth(0.6f)) {
                Icon(Icons.Default.CameraAlt, null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text("Sacar foto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- CARGA DINÁMICA DE LA FOTO DEL STAND DESDE SUPABASE ---
            // ¡MAGIA AQUÍ! Caja más grande, llena el ancho, y usa ContentScale.Fit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                val urlFotoStand = remember { "https://vodpkdvuaybnmrouoxzd.supabase.co/storage/v1/object/public/Ropa/foto.jpg?v=${System.currentTimeMillis()}" }
                val bitmapStand = CargarImagenDesdeInternet(url = urlFotoStand)

                if (bitmapStand != null) {
                    Image(
                        bitmap = bitmapStand.asImageBitmap(),
                        contentDescription = "Foto portable",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit // Ajusta sin cortar
                    )
                } else {
                    CircularProgressIndicator(color = Fucsia, modifier = Modifier.size(30.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PantallaSacarFoto(onVolver: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        IconButton(onClick = onVolver, modifier = Modifier.padding(16.dp).align(Alignment.TopStart).background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White) }
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Fucsia, modifier = Modifier.size(60.dp), strokeWidth = 6.dp); Spacer(modifier = Modifier.height(24.dp)); Text("Sacando foto...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun PantallaColorimetria(prefs: android.content.SharedPreferences, onVolver: () -> Unit) {
    var codigoHex by remember { mutableStateOf("") }
    val coloresGuardados = remember { val lista = mutableStateListOf<Color>(); val guardadosStr = prefs.getString("colores_guardados", "") ?: ""; if (guardadosStr.isNotEmpty()) { guardadosStr.split(",").forEach { hex -> try { lista.add(Color(android.graphics.Color.parseColor(hex))) } catch (e: Exception) {} } }; lista }
    fun guardarColores() { val str = coloresGuardados.joinToString(",") { color -> String.format("#%06X", 0xFFFFFF and color.toArgb()) }; prefs.edit().putString("colores_guardados", str).apply() }
    val coloresPredefinidos = listOf(Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF0000FF), Color(0xFF4B0082), Color(0xFF9400D3), Color(0xFFFF1493), Color(0xFF00FFFF), Color(0xFFFFD700), Color(0xFF8B4513), Color(0xFF808080), Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFFFB6C1))

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }; Text("Colorimetría", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Fucsia) }
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = FondoTarjetas), modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(16.dp)) { Text("¿Qué es la colorimetría?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Fucsia); Spacer(modifier = Modifier.height(8.dp)); Text("Es el estudio del color y cómo interactúa con tus rasgos.", fontSize = 14.sp, color = Color.DarkGray) } }
            Spacer(modifier = Modifier.height(24.dp)); Text("Elige tus colores", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(modifier = Modifier.height(8.dp))
        }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(coloresPredefinidos) { color -> Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(color).border(1.dp, Color.LightGray, CircleShape).clickable { if (!coloresGuardados.contains(color)) { coloresGuardados.add(color); guardarColores() } }) } } }
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = codigoHex, onValueChange = { codigoHex = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Código HEX") }); Spacer(modifier = Modifier.width(8.dp)); Button(onClick = { try { val nC = Color(android.graphics.Color.parseColor(if(codigoHex.startsWith("#")) codigoHex else "#$codigoHex")); if(!coloresGuardados.contains(nC)){ coloresGuardados.add(nC); guardarColores() } } catch(e:Exception){} }, colors = ButtonDefaults.buttonColors(containerColor = Fucsia)) { Text("Añadir") } }
            Spacer(modifier = Modifier.height(24.dp)); Text("Tu Paleta", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            LazyRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(coloresGuardados) { color -> Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(color).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)).clickable { coloresGuardados.remove(color); guardarColores() }) } }
        }
        item {
            Spacer(modifier = Modifier.height(32.dp)); HorizontalDivider(color = Color.LightGray); Spacer(modifier = Modifier.height(24.dp))
            Text("IAPick te ayuda a saber cuál es tu colorimetría", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Fucsia, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()); Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))) { Text("🚧 Función en desarrollo", modifier = Modifier.padding(12.dp), color = Color(0xFF856404), fontWeight = FontWeight.Bold) } }
        }
    }
}

@Composable
fun PantallaMedidas(prefs: android.content.SharedPreferences, onVolver: () -> Unit) {
    var altura by remember { mutableStateOf(prefs.getString("medida_altura", "") ?: "") }
    var peso by remember { mutableStateOf(prefs.getString("medida_peso", "") ?: "") }
    var zapato by remember { mutableStateOf(prefs.getString("medida_zapato", "") ?: "") }
    var pecho by remember { mutableStateOf(prefs.getString("medida_pecho", "") ?: "") }
    var cintura by remember { mutableStateOf(prefs.getString("medida_cintura", "") ?: "") }
    var cadera by remember { mutableStateOf(prefs.getString("medida_cadera", "") ?: "") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }; Text("Medidas del Maniquí", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Fucsia) }
        Spacer(modifier = Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { OutlinedTextField(value = altura, onValueChange = { altura = it; prefs.edit().putString("medida_altura", it).apply() }, label = { Text("Altura (cm)") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = peso, onValueChange = { peso = it; prefs.edit().putString("medida_peso", it).apply() }, label = { Text("Peso (kg)") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = zapato, onValueChange = { zapato = it; prefs.edit().putString("medida_zapato", it).apply() }, label = { Text("Talla de Zapato (EUR)") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = pecho, onValueChange = { pecho = it; prefs.edit().putString("medida_pecho", it).apply() }, label = { Text("Contorno de Pecho (cm)") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = cintura, onValueChange = { cintura = it; prefs.edit().putString("medida_cintura", it).apply() }, label = { Text("Contorno de Cintura (cm)") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = cadera, onValueChange = { cadera = it; prefs.edit().putString("medida_cadera", it).apply() }, label = { Text("Contorno de Cadera (cm)") }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun PantallaPremium(onVolver: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(FondoPrincipal).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        Icon(Icons.Default.WorkspacePremium, null, modifier = Modifier.size(100.dp), tint = DoradoPremium)
        Text("Outpick Premium", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = DoradoPremium)
        Text("Tu estilo, sin límites.", color = Color.Gray, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp)); Spacer(modifier = Modifier.height(32.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = FondoTarjetas), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PremiumFeature(Icons.Default.Checkroom, "30 Espacios de Armario", "Guarda todos tus outfits sin preocuparte por el espacio.")
                PremiumFeature(Icons.Default.Favorite, "30 Favoritos Exclusivos", "Acceso total a las mejores elecciones de la comunidad.")
                PremiumFeature(Icons.Default.Palette, "Colorimetría IAPick", "Analizamos tu tono de piel con Inteligencia Artificial.")
                PremiumFeature(Icons.Default.AutoAwesome, "Outfits por IAPick", "Accede a increíbles outfits generados por nuestra IA.")
                PremiumFeature(Icons.Default.Groups, "Comunidad Ilimitada", "Descubre más de 15 outfits diarios del resto de usuarios.")
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { }, modifier = Modifier.fillMaxWidth().height(65.dp), colors = ButtonDefaults.buttonColors(containerColor = DoradoPremium), shape = RoundedCornerShape(16.dp)) { Text("Suscribirse por 4.99€ / mes", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        Text("Cancela cuando quieras.", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun PremiumFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = Fucsia, modifier = Modifier.size(36.dp)); Spacer(modifier = Modifier.width(16.dp))
        Column { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ColorTexto); Text(desc, fontSize = 14.sp, color = Color.DarkGray, lineHeight = 18.sp) }
    }
}

@Composable
fun PantallaFAQ(onVolver: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }; Text("FAQ & Nosotros", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Fucsia) }
        Spacer(modifier = Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ItemFAQ("¿Quiénes somos?", "Somos un grupo de 4 estudiantes del IES Chan do Monte con una idea que cada vez se vuelve mas grande.") }
            item { ItemFAQ("¿Cómo funciona el probador?", "Sube la foto de la prenda y nosotros hacemos la magia.") }
            item { ItemFAQ("¿Es seguro mi pago?", "Usamos la pasarela oficial de Google Play/App Store para Premium.") }
        }
    }
}

@Composable
fun ItemFAQ(pregunta: String, respuesta: String) {
    var expandido by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { expandido = !expandido }, colors = CardDefaults.cardColors(containerColor = FondoTarjetas)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text(pregunta, fontWeight = FontWeight.Bold, color = ColorTexto, modifier = Modifier.weight(1f)); Icon(if (expandido) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Fucsia) }
            if (expandido) { Spacer(modifier = Modifier.height(8.dp)); Text(respuesta, color = Color.DarkGray, fontSize = 14.sp) }
        }
    }
}

@Composable
fun PantallaRecomendaciones(onVolver: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }; Text("Recomendaciones", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Fucsia); Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Escribe aquí...") }, modifier = Modifier.fillMaxWidth().height(150.dp))
        Button(onClick = onVolver, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Fucsia)) { Text("Enviar", color = Color.White) }
    }
}

@Composable
fun PantallaResultado2D(outfitId: Int, onVolver: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(FondoPrincipal)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }; Text("Resultado Outfit ${outfitId + 1}", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))) { Text("🚧 Modelo 3D todavía en desarrollo.\nSe ha generado una imagen modificada en su lugar.", modifier = Modifier.padding(12.dp), color = Color(0xFF856404), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 14.sp) }
        Box(modifier = Modifier.fillMaxSize().padding(16.dp).clip(RoundedCornerShape(20.dp)).background(FondoTarjetas), contentAlignment = Alignment.Center) {
            val urlResultado = remember(outfitId) { "https://vodpkdvuaybnmrouoxzd.supabase.co/storage/v1/object/public/Ropa/o${outfitId + 1}.jpeg?v=${System.currentTimeMillis()}" }
            val bitmapResultado = CargarImagenDesdeInternet(url = urlResultado)
            if (bitmapResultado != null) { Image(bitmap = bitmapResultado.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } else { CircularProgressIndicator(color = Fucsia, modifier = Modifier.size(50.dp)) }
        }
    }
}