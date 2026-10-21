// Maneja el CICLO DE VIDA del token: guardarlo, saber cuando vence y cerrar la sesion a tiempo.
const CLAVE = "sesionTransporte";
let temporizador = null;

export function guardarSesion(token, apikey) {
    // sessionStorage se borra al cerrar la pestana. localStorage lo dejaria guardado indefinidamente.
    sessionStorage.setItem(CLAVE, JSON.stringify({ token, apikey }));
}

// Devuelve { token, apikey } si hay sesion VIGENTE; si no existe o ya vencio, la limpia y devuelve null
export function obtenerSesion() {
    const crudo = sessionStorage.getItem(CLAVE);
    if (!crudo) return null;
    try {
        const sesion = JSON.parse(crudo);
        if (haExpirado(sesion.token)) {
            cerrarSesion();
            return null;
        }
        return sesion;
    } catch {
        cerrarSesion();
        return null;
    }
}

export function cerrarSesion() {
    sessionStorage.removeItem(CLAVE);
    clearTimeout(temporizador);
}

// Un JWT son 3 partes separadas por puntos: encabezado.payload.firma
// El payload (la del medio) es un JSON en Base64URL que trae "exp": el instante de vencimiento en SEGUNDOS.
function leerVencimientoMs(token) {
    try {
        const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"); // Base64URL -> Base64
        const exp = JSON.parse(atob(payload)).exp;
        return typeof exp === "number" ? exp * 1000 : 0; // a milisegundos, que es lo que usa Date
    } catch {
        return 0; // un token ilegible se trata como vencido
    }
}

export function haExpirado(token) {
    return Date.now() >= leerVencimientoMs(token);
}

export function expiraEn(token) {
    return new Date(leerVencimientoMs(token));
}

// Programa un aviso para el instante exacto en que vence el token, aunque el usuario no toque nada
export function iniciarVigilancia(alExpirar) {
    clearTimeout(temporizador);
    const sesion = obtenerSesion();
    if (!sesion) return;
    const faltan = leerVencimientoMs(sesion.token) - Date.now();
    temporizador = setTimeout(() => {
        cerrarSesion();
        alExpirar();
    }, Math.max(faltan, 0)); // nuestro token dura 1 hora; setTimeout no admite esperas de mas de ~24 dias
}