import { CONFIG } from "../config.js";
import { login, apiGet, SesionExpiradaError } from "./api.js";
import { guardarSesion, obtenerSesion, cerrarSesion, expiraEn, iniciarVigilancia } from "./session.js";
import { iniciarMapa, dibujarRuta, limpiarMapa, enfocarParada, tieneCoordenadas, tipoDeParada } from "./mapa.js";

const MAX_REFRESCOS = 20; // tope de reconsultas automaticas (20 x 30 s = 10 min): no se consulta para siempre

// --- Elementos de la pagina (se buscan una sola vez) ---
const $ = (id) => document.getElementById(id);
const vistaLogin = $("vista-login");
const vistaRuta = $("vista-ruta");
const formLogin = $("form-login");
const campoLogin = $("campo-login");
const campoPassword = $("campo-password");
const mensajeLogin = $("mensaje-login");
const formRuta = $("form-ruta");
const campoCodigo = $("codigo-ruta");
const mensajeRuta = $("mensaje-ruta");
const resumen = $("resumen");
const listaParadas = $("lista-paradas");
const textoSesion = $("texto-sesion");
const botonSalir = $("btn-salir");

let promesaMapa = null;
let temporizadorRefresco = null;

function mostrar(elemento, texto, esError = false) {
    elemento.textContent = texto;
    elemento.classList.toggle("error", esError);
}

// El mapa se crea la PRIMERA vez que se necesita (no antes: dentro de un div oculto tendria tamano 0)
function asegurarMapa() {
    if (!promesaMapa) {
        promesaMapa = iniciarMapa($("mapa"), (mensaje) => mostrar(mensajeRuta, mensaje, true))
            .catch((error) => { promesaMapa = null; throw error; }); // si falla, permite reintentar
    }
    return promesaMapa;
}

// ---------------- Vistas ----------------

function mostrarLogin(mensaje = "") {
    clearTimeout(temporizadorRefresco);
    limpiarMapa();
    listaParadas.replaceChildren();
    resumen.textContent = "";
    vistaRuta.hidden = true;
    vistaLogin.hidden = false;
    mostrar(mensajeLogin, mensaje, true);
}

function mostrarRutas() {
    const sesion = obtenerSesion(); // null si no hay sesion o ya vencio
    if (!sesion) { mostrarLogin(); return; }

    vistaLogin.hidden = true;
    vistaRuta.hidden = false;
    const hora = expiraEn(sesion.token).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" });
    textoSesion.textContent = `Sesión válida hasta las ${hora}`;

    // Cuando el token venza, se vuelve al login solo, aunque el usuario no haya tocado nada
    iniciarVigilancia(() => mostrarLogin("Tu sesión expiró. Inicia sesión de nuevo."));
    asegurarMapa().catch((error) => mostrar(mensajeRuta, error.message, true));
}

// ---------------- Datos ----------------

// Acepta tanto una lista directa de paradas como un objeto { paradas: [...] }, y las ordena por ordenParada.
// Si la respuesta real de Tania tiene otra forma, SOLO se cambia esta funcion.
function normalizarParadas(json) {
    const lista = Array.isArray(json) ? json : (json.paradas ?? json.trayectos ?? []);
    return [...lista].sort((a, b) => a.ordenParada - b.ordenParada);
}

function pintarResumen(paradas) {
    const primera = paradas[0];
    const conductor = primera.conductor
        ? `${primera.conductor.nombres ?? ""} ${primera.conductor.apellidos ?? ""}`.trim() : "—";
    const placa = primera.vehiculo?.placa ?? "—";
    resumen.textContent = `Conductor: ${conductor} · Vehículo: ${placa} · Registrada por: ${primera.loginUsuario ?? "—"}`;
}

function pintarLista(paradas) {
    listaParadas.replaceChildren();
    paradas.forEach((parada, indice) => {
        const item = document.createElement("li");
        item.className = tipoDeParada(indice, paradas.length) + (tieneCoordenadas(parada) ? "" : " pendiente");

        const numero = document.createElement("span");
        numero.className = "num";
        numero.textContent = String(indice + 1);

        const lugar = document.createElement("div");
        lugar.textContent = parada.ubicacion;                  // textContent: nunca innerHTML con datos de la base
        const coordenadas = document.createElement("div");
        coordenadas.className = "coord";
        coordenadas.textContent = tieneCoordenadas(parada)
            ? `${Number(parada.latitud).toFixed(6)}, ${Number(parada.longitud).toFixed(6)}`
            : "Coordenadas pendientes";

        const texto = document.createElement("div");
        texto.append(lugar, coordenadas);
        item.append(numero, texto);
        item.addEventListener("click", () => enfocarParada(indice));
        listaParadas.appendChild(item);
    });
}

// intento = 0 es una consulta manual; intento > 0 es una reconsulta automatica
async function cargarRuta(codigo, intento = 0) {
    clearTimeout(temporizadorRefresco);
    if (intento === 0) mostrar(mensajeRuta, "Cargando ruta...");

    try {
        const json = await apiGet(`/api/trayectos/rutas/${encodeURIComponent(codigo)}`);

        const paradas = normalizarParadas(json);
        if (paradas.length === 0) { mostrar(mensajeRuta, "Esa ruta no tiene paradas.", true); return; }

        await asegurarMapa();
        pintarResumen(paradas);
        pintarLista(paradas);
        // En una reconsulta automatica NO se reencuadra: si el usuario hizo zoom, el mapa no debe saltar
        const enMapa = dibujarRuta(paradas, { encuadrar: intento === 0 });
        const pendientes = paradas.length - enMapa;

        if (pendientes === 0) {
            mostrar(mensajeRuta, `Ruta ${codigo}: ${paradas.length} paradas en el mapa.`);
        } else if (intento < MAX_REFRESCOS) {
            // La tarea programada del backend completa las coordenadas cada 90 s: volvemos a preguntar hasta que esten
            mostrar(mensajeRuta, `${pendientes} parada(s) sin coordenadas todavía. Se consulta de nuevo sola cada ${CONFIG.REFRESCO_MS / 1000} s.`);
            temporizadorRefresco = setTimeout(() => cargarRuta(codigo, intento + 1), CONFIG.REFRESCO_MS);
        } else {
            mostrar(mensajeRuta, `${pendientes} parada(s) siguen sin coordenadas. Pulsa "Ver ruta" para consultar de nuevo.`, true);
        }
    } catch (error) {
        if (error instanceof SesionExpiradaError) mostrarLogin(error.message);
        else mostrar(mensajeRuta, error.message, true);
    }
}

// ---------------- Eventos ----------------

formLogin.addEventListener("submit", async (evento) => {
    evento.preventDefault(); // evita que el navegador recargue la pagina al enviar el formulario
    mostrar(mensajeLogin, "");
    try {
        const { token, apikey } = await login(campoLogin.value.trim(), campoPassword.value);
        if (!token || !apikey) {
            throw new Error("El servidor no devolvió token y APIKey. Falta actualizar el backend (LoginResponseDTO).");
        }
        guardarSesion(token, apikey);
        campoPassword.value = ""; // la contrasena no debe quedarse en el formulario
        mostrarRutas();
    } catch (error) {
        mostrar(mensajeLogin, error.message, true);
    }
});

formRuta.addEventListener("submit", (evento) => {
    evento.preventDefault();
    const codigo = campoCodigo.value.trim();
    if (codigo) cargarRuta(codigo);
});

botonSalir.addEventListener("click", () => {
    cerrarSesion();
    mostrarLogin();
});

// Arranque: si ya hay una sesion vigente (por ejemplo al recargar con F5) se salta el login
mostrarRutas();