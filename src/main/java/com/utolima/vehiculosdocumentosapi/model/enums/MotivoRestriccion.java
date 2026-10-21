package com.utolima.vehiculosdocumentosapi.model.enums;

// Por qué una ruta aparece como restringida. No se guarda en la base de datos,
// solo se calcula al responder, por eso no necesita converter como los otros enums.
public enum MotivoRestriccion {
    VEHICULO_NO_HABILITADO, // el vehículo no tiene TODOS sus documentos en Habilitado
    CONDUCTOR_RESTRINGIDO,  // el conductor está en RO para ese vehículo
    AMBOS                   // se cumplen las dos cosas a la vez
}
