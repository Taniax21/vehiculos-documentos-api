import { CONFIG } from "../config.js";

let mapa = null;
let clases = null;                // clases de Google ya cargadas (InfoWindow, Polyline, ...)
let ventana = null;               // la burbuja de informacion que se abre al tocar un marcador
let linea = null;                 // la linea que une las paradas en orden
const marcadores = new Map();     // indice de parada -> { marcador, parada, posicion }

// Una parada "tiene coordenadas" solo si latitud y longitud son numeros reales (null = la tarea programada aun no las completa)
export function tieneCoordenadas(parada) {
    const valido = (v) => v !== null && v !== undefined && v !== "" && Number.isFinite(Number(v));
    return valido(parada.latitud) && valido(parada.longitud);
}

// 0 = inicial (verde), la ultima = final (roja), el resto = intermedias (azules)
export function tipoDeParada(indice, total) {
    if (indice === 0) return "inicio";
    if (indice === total - 1) return "fin";
    return "intermedia";
}

// Carga el script de Google con la clave de config.js. Devuelve una promesa que se cumple cuando Google termina de cargar.
function cargarScriptGoogle() {
    if (window.google && window.google.maps) return Promise.resolve();
    return new Promise((resolver, rechazar) => {
        window.__mapsListo = () => resolver(); // Google llama a esta funcion al terminar (parametro callback=)
        const script = document.createElement("script");
        script.src = "https://maps.googleapis.com/maps/api/js?key=" + encodeURIComponent(CONFIG.MAPS_API_KEY)
            + "&callback=__mapsListo&loading=async&v=weekly";
        script.async = true;
        script.onerror = () => rechazar(new Error("No se pudo cargar Google Maps. Revisa la conexión y MAPS_API_KEY en config.js."));
        document.head.appendChild(script);
    });
}

export async function iniciarMapa(elemento, alError) {
    // Google llama a esta funcion global cuando rechaza la clave (restriccion de sitio, API no habilitada...)
    window.gm_authFailure = () => alError?.(
        "Google Maps rechazó la clave: revisa que la Maps JavaScript API esté habilitada y que http://127.0.0.1:5500 esté permitido en la clave.");

    await cargarScriptGoogle();
    const { Map: MapaGoogle, InfoWindow, Polyline } = await google.maps.importLibrary("maps");
    const { LatLngBounds } = await google.maps.importLibrary("core");
    const { AdvancedMarkerElement } = await google.maps.importLibrary("marker");
    clases = { InfoWindow, Polyline, LatLngBounds, AdvancedMarkerElement };

    mapa = new MapaGoogle(elemento, {
        center: { lat: 4.4389, lng: -75.2322 }, // Ibague
        zoom: 13,
        mapId: "DEMO_MAP_ID",                   // los marcadores avanzados exigen un mapId; este valor sirve para desarrollo
    });
    ventana = new InfoWindow();
}

// Dibuja los marcadores numerados y la linea que los une. Devuelve cuantas paradas pudo poner en el mapa.
export function dibujarRuta(paradas, { encuadrar = true } = {}) {
    limpiarMapa();
    const puntos = [];
    const limites = new clases.LatLngBounds();

    paradas.forEach((parada, indice) => {
        if (!tieneCoordenadas(parada)) return; // las pendientes se ven en la lista, no en el mapa

        // latitud es latitud y longitud es longitud. NO se intercambian
        // (el ejemplo del profesor si lo hace porque sus datos estaban guardados al reves).
        const posicion = { lat: Number(parada.latitud), lng: Number(parada.longitud) };

        // El marcador es un div nuestro (estilo .pin en style.css) con el numero de la parada
        const pin = document.createElement("div");
        pin.className = "pin " + tipoDeParada(indice, paradas.length);
        pin.textContent = String(indice + 1);

        const marcador = new clases.AdvancedMarkerElement({ map: mapa, position: posicion, content: pin, title: parada.ubicacion });
        marcador.addListener("click", () => abrirVentana(marcador, parada, indice));

        marcadores.set(indice, { marcador, parada, posicion });
        puntos.push(posicion);
        limites.extend(posicion);
    });

    if (puntos.length >= 2) {
        linea = new clases.Polyline({ path: puntos, map: mapa, strokeColor: "#1a73e8", strokeOpacity: 0.8, strokeWeight: 4 });
    }
    if (encuadrar) encuadrarMapa(puntos, limites);
    return puntos.length;
}

// Se llama al tocar una parada de la lista lateral. Devuelve false si esa parada aun no tiene coordenadas.
export function enfocarParada(indice) {
    const registro = marcadores.get(indice);
    if (!registro) return false;
    mapa.panTo(registro.posicion);
    abrirVentana(registro.marcador, registro.parada, indice);
    return true;
}

export function limpiarMapa() {
    marcadores.forEach(({ marcador }) => { marcador.map = null; }); // quitar un marcador avanzado = ponerle map en null
    marcadores.clear();
    if (linea) { linea.setMap(null); linea = null; }
    if (ventana) ventana.close();
}

function encuadrarMapa(puntos, limites) {
    if (puntos.length === 0) return;
    if (puntos.length === 1) { mapa.setCenter(puntos[0]); mapa.setZoom(15); return; }
    mapa.fitBounds(limites, 60); // ajusta zoom y centro para que se vean todas las paradas, con 60 px de margen
}

function abrirVentana(marcador, parada, indice) {
    // Se arma con textContent y NO con innerHTML: la ubicacion viene de la base de datos y no debe interpretarse como HTML
    const caja = document.createElement("div");
    const titulo = document.createElement("strong");
    titulo.textContent = `Parada ${indice + 1}`;
    const texto = document.createElement("p");
    texto.textContent = parada.ubicacion;
    caja.append(titulo, texto);
    ventana.setContent(caja);
    ventana.open({ anchor: marcador, map: mapa });
}