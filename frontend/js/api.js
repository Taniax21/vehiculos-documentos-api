import { CONFIG } from "../config.js";
import { obtenerSesion, cerrarSesion } from "./session.js";

// Error propio para distinguir "se acabo la sesion" de otros fallos y mandar al usuario al login
export class SesionExpiradaError extends Error { }

// fetch solo lanza excepcion cuando NO hay respuesta: servidor apagado, URL mal escrita o llamada bloqueada por CORS
async function peticion(url, opciones) {
    try {
        return await fetch(url, opciones);
    } catch {
        throw new Error("No se pudo conectar con el servidor. Revisa que el backend esté encendido y que CORS esté configurado.");
    }
}

// Nuestro backend responde los errores con { fecha, estado, mensaje, detalles }
async function leerMensajeError(respuesta) {
    try {
        const cuerpo = await respuesta.json();
        const detalles = Array.isArray(cuerpo.detalles) && cuerpo.detalles.length
            ? ` (${cuerpo.detalles.join("; ")})` : "";
        return (cuerpo.mensaje ?? `Error ${respuesta.status}`) + detalles;
    } catch {
        return `Error ${respuesta.status}`;
    }
}

export async function login(usuario, password) {
    const respuesta = await peticion(`${CONFIG.API_BASE_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ login: usuario, password }),
    });
    if (!respuesta.ok) throw new Error(await leerMensajeError(respuesta));
    return respuesta.json(); // { token, apikey }
}

// GET a una ruta protegida: agrega SOLO los dos headers que exige el backend
export async function apiGet(ruta) {
    const sesion = obtenerSesion();
    if (!sesion) throw new SesionExpiradaError("Tu sesión expiró. Inicia sesión de nuevo.");

    const respuesta = await peticion(`${CONFIG.API_BASE_URL}${ruta}`, {
        headers: { "Authorization": `Bearer ${sesion.token}`, "X-API-Key": sesion.apikey },
    });

    if (respuesta.status === 401) { // token vencido o APIKey invalida: el servidor tiene la ultima palabra
        const mensaje = await leerMensajeError(respuesta);
        cerrarSesion();
        throw new SesionExpiradaError(mensaje);
    }
    if (!respuesta.ok) throw new Error(await leerMensajeError(respuesta));
    return respuesta.json();
}