// Copia este archivo con el nombre config.js y completa los valores.
// config.js NO se sube a Git (esta en .gitignore); este archivo de ejemplo si.
export const CONFIG = {
    API_BASE_URL: "http://localhost:8080",           // donde corre el backend Spring Boot
    MAPS_API_KEY: "PON_AQUI_TU_CLAVE_DE_NAVEGADOR",  // la clave de Maps JavaScript API (restringida por sitio web)
    USAR_DATOS_DEMO: false,                           // true: usa una ruta de ejemplo. false: llama al backend (cuando Tania termine)
    REFRESCO_MS: 30000                               // cada cuanto se vuelve a consultar la ruta si faltan coordenadas
};