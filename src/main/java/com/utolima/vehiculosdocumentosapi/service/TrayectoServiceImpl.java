package com.utolima.vehiculosdocumentosapi.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.utolima.vehiculosdocumentosapi.dto.ConductorPublicoDTO;
import com.utolima.vehiculosdocumentosapi.dto.ConductorResumenDTO;
import com.utolima.vehiculosdocumentosapi.dto.ParadaPublicaDTO;
import com.utolima.vehiculosdocumentosapi.dto.RutaConConductorDTO;
import com.utolima.vehiculosdocumentosapi.dto.RutaRestringidaDTO;
import com.utolima.vehiculosdocumentosapi.dto.TrayectoRequestDTO;
import com.utolima.vehiculosdocumentosapi.dto.TrayectoResponseDTO;
import com.utolima.vehiculosdocumentosapi.exception.PersonaNoEsConductorException;
import com.utolima.vehiculosdocumentosapi.exception.RecursoNoEncontradoException;
import com.utolima.vehiculosdocumentosapi.exception.ReglaNegocioException;
import com.utolima.vehiculosdocumentosapi.mapper.TrayectoMapper;
import com.utolima.vehiculosdocumentosapi.model.Persona;
import com.utolima.vehiculosdocumentosapi.model.Trayecto;
import com.utolima.vehiculosdocumentosapi.model.Usuario;
import com.utolima.vehiculosdocumentosapi.model.Vehiculo;
import com.utolima.vehiculosdocumentosapi.model.VehiculoDocumento;
import com.utolima.vehiculosdocumentosapi.model.VehiculoPersona;
import com.utolima.vehiculosdocumentosapi.model.VehiculoPersonaId;
import com.utolima.vehiculosdocumentosapi.model.enums.EstadoConductor;
import com.utolima.vehiculosdocumentosapi.model.enums.EstadoDocumento;
import com.utolima.vehiculosdocumentosapi.model.enums.MotivoRestriccion;
import com.utolima.vehiculosdocumentosapi.model.enums.TipoPersona;
import com.utolima.vehiculosdocumentosapi.repository.PersonaRepository;
import com.utolima.vehiculosdocumentosapi.repository.TrayectoRepository;
import com.utolima.vehiculosdocumentosapi.repository.VehiculoDocumentoRepository;
import com.utolima.vehiculosdocumentosapi.repository.VehiculoPersonaRepository;
import com.utolima.vehiculosdocumentosapi.repository.VehiculoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor // reemplaza los @Autowired de campo: mismo estilo que el resto del proyecto
public class TrayectoServiceImpl implements TrayectoService {

    private static final int MIN_PARADAS = 2; // parada inicial + parada final
    private static final int MAX_PARADAS = 7; // inicial + hasta 5 intermedias + final

    private final TrayectoRepository trayectoRepository;
    private final PersonaRepository personaRepository;
    private final VehiculoRepository vehiculoRepository;
    private final VehiculoPersonaRepository vehiculoPersonaRepository;
    private final VehiculoDocumentoRepository vehiculoDocumentoRepository;

    @Override
    @Transactional // si falla cualquier parada no se guarda ninguna: la ruta se registra completa o no se registra
    public void crearRuta(List<TrayectoRequestDTO> paradas) {

        // 1. Forma de la ruta: cantidad, campos obligatorios, mismo conductor/vehículo/código, órdenes 0..n-1
        validarEstructuraDeRuta(paradas);

        TrayectoRequestDTO base = paradas.get(0); // ya validamos que todas las paradas comparten estos datos

        // 2. Que existan el conductor y el vehículo (404 si no)
        Persona conductor = personaRepository.findById(base.getIdPersona())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el conductor con id " + base.getIdPersona()));
        Vehiculo vehiculo = vehiculoRepository.findById(base.getIdVehiculo())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo con id " + base.getIdVehiculo()));

        // 3. Solo personas tipo CONDUCTOR (reutiliza la excepción que ya existe)
        if (conductor.getTipoPersona() != TipoPersona.CONDUCTOR) {
            throw new PersonaNoEsConductorException("Solo se pueden registrar trayectos a personas de tipo CONDUCTOR. "
                    + "La persona " + conductor.getIdPersona() + " es de tipo " + conductor.getTipoPersona());
        }

        // 4. Regla A de la nota del PDF: el conductor debe poder operar (PO) ESTE vehículo
        VehiculoPersona asociacion = vehiculoPersonaRepository
                .findById(new VehiculoPersonaId(vehiculo.getId(), conductor.getIdPersona())) // llave compuesta, sin @Query
                .orElseThrow(() -> new ReglaNegocioException("El conductor no está asociado a este vehículo."));

        // se compara contra la CONSTANTE del enum: name() devuelve "PUEDE_OPERAR", nunca "PO"
        if (asociacion.getEstadoConductor() != EstadoConductor.PUEDE_OPERAR) {
            throw new ReglaNegocioException("El conductor no está en estado PO (Puede Operar) para este vehículo.");
        }

        // 5. Regla B de la nota: los documentos del vehículo deben estar Habilitados.
        // Usa el mismo método que la consulta 4: así hay UNA sola definición de "vehículo habilitado".
        if (!vehiculoHabilitado(vehiculo.getId())) {
            throw new ReglaNegocioException("El vehículo no tiene todos sus documentos en estado Habilitado.");
        }

        // 6. El login sale del usuario autenticado (token), no del body: el cliente no puede falsificarlo
        String loginQuienRegistra = obtenerLoginAutenticado();

        // 7. Guardar todas las paradas
        for (TrayectoRequestDTO dto : paradas) {
            Trayecto trayecto = new Trayecto();
            trayecto.setConductor(conductor);
            trayecto.setVehiculo(vehiculo);
            trayecto.setCodigoRuta(dto.getCodigoRuta());
            trayecto.setUbicacion(dto.getUbicacion());
            trayecto.setOrdenParada(dto.getOrdenParada());
            trayecto.setLatitud(dto.getLatitud());   // puede ser null: la tarea de Google Maps la completa luego
            trayecto.setLongitud(dto.getLongitud());
            trayecto.setLoginUsuario(loginQuienRegistra);
            trayectoRepository.save(trayecto);
        }
    }

    // CONSULTA 1: devuelve las paradas de una ruta, ya ordenadas.
    // readOnly = true: solo lee (más liviano) y mantiene la sesión abierta mientras el mapper
    // toca las relaciones LAZY (conductor y vehículo). Sin transacción, eso fallaría.
    @Override
    @Transactional(readOnly = true)
    public List<TrayectoResponseDTO> consultarRutaPorCodigo(String codigoRuta) {
        // Método que ya existía en el repositorio: filtra por código y ordena por ordenParada ascendente
        List<Trayecto> trayectos = trayectoRepository.findByCodigoRutaOrderByOrdenParadaAsc(codigoRuta);

        // Si no hay ninguna parada, la ruta no existe -> el manejador global lo convierte en HTTP 404
        if (trayectos.isEmpty()) {
            throw new RecursoNoEncontradoException("No existe una ruta con el código " + codigoRuta);
        }

        return TrayectoMapper.toResponseDTOList(trayectos); // entidades -> DTOs, nunca devolvemos entidades
    }

    // CONSULTA 2: códigos de ruta de un conductor.
    @Override
    @Transactional(readOnly = true)
    public List<String> consultarRutasPorConductor(String identificacion) {
        // Busca por el campo "identificacion" (el número de documento), no por el id de la tabla.
        // Si no existe la persona -> RecursoNoEncontradoException -> HTTP 404
        Persona persona = personaRepository.findByIdentificacion(identificacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una persona con identificación " + identificacion));

        // Existe, pero solo los CONDUCTORES tienen rutas -> PersonaNoEsConductorException -> HTTP 400
        if (persona.getTipoPersona() != TipoPersona.CONDUCTOR) {
            throw new PersonaNoEsConductorException(
                    "La persona con identificación " + identificacion + " no es de tipo CONDUCTOR.");
        }

        // SELECT DISTINCT del repositorio: devuelve cada código una sola vez.
        // Si el conductor aún no tiene rutas, devuelve lista vacía (HTTP 200), no 404.
        return trayectoRepository.findCodigosRutaPorIdentificacionConductor(identificacion);
    }

    // CONSULTA 3: rutas de un vehículo, una fila por ruta con su conductor.
    @Override
    @Transactional(readOnly = true)
    public List<RutaConConductorDTO> consultarRutasPorVehiculo(String placa) {
        // Si la placa no existe -> 404. Esto distingue "placa inventada" de "vehículo sin rutas".
        vehiculoRepository.findByPlaca(placa)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un vehículo con placa " + placa));

        // Trae todas las paradas del vehículo, con el conductor ya cargado (JOIN FETCH),
        // ordenadas por código de ruta y por parada.
        List<Trayecto> trayectos = trayectoRepository.findTrayectosPorPlacaConConductor(placa);

        // AGRUPAR: Map<codigoRuta, paradas de esa ruta>.
        // LinkedHashMap conserva el orden en que llegaron (el de la consulta).
        Map<String, List<Trayecto>> porRuta = trayectos.stream()
                .collect(Collectors.groupingBy(Trayecto::getCodigoRuta, LinkedHashMap::new, Collectors.toList()));

        // Una entrada del mapa = una ruta. El conductor es el mismo en todas sus paradas,
        // así que tomamos el del primer trayecto del grupo.
        return porRuta.entrySet().stream()
                .map(entrada -> {
                    Persona c = entrada.getValue().get(0).getConductor();
                    return new RutaConConductorDTO(
                            entrada.getKey(),
                            new ConductorResumenDTO(c.getIdPersona(), c.getIdentificacion(), c.getNombres(), c.getApellidos()),
                            entrada.getValue().size()); // total de paradas de la ruta
                })
                .toList();
    }

    // CONSULTA 4 (pública): rutas con vehículo NO habilitado o conductor Restringido (RO).
    @Override
    @Transactional(readOnly = true)
    public List<RutaRestringidaDTO> consultarRutasRestringidas() {
        // Todas las paradas, ya ordenadas, con conductor y vehículo cargados
        List<Trayecto> todos = trayectoRepository.findAllConConductorYVehiculo();

        // Agrupamos por código de ruta (una ruta = un conductor y un vehículo, así lo exige crearRuta)
        Map<String, List<Trayecto>> porRuta = todos.stream()
                .collect(Collectors.groupingBy(Trayecto::getCodigoRuta, LinkedHashMap::new, Collectors.toList()));

        List<RutaRestringidaDTO> resultado = new ArrayList<>();

        for (Map.Entry<String, List<Trayecto>> entrada : porRuta.entrySet()) {
            List<Trayecto> paradas = entrada.getValue();
            Trayecto primera = paradas.get(0); // conductor y vehículo son los mismos en toda la ruta
            Persona conductor = primera.getConductor();
            Vehiculo vehiculo = primera.getVehiculo();

            boolean vehiculoNoHabilitado = !vehiculoHabilitado(vehiculo.getId());
            boolean conductorRestringido = conductorRestringido(vehiculo.getId(), conductor.getIdPersona());

            // Si no cumple ninguna de las dos condiciones, la ruta está bien: no se incluye
            if (!vehiculoNoHabilitado && !conductorRestringido) {
                continue;
            }

            // Motivo: uno de los dos, o ambos
            MotivoRestriccion motivo;
            if (vehiculoNoHabilitado && conductorRestringido) {
                motivo = MotivoRestriccion.AMBOS;
            } else if (vehiculoNoHabilitado) {
                motivo = MotivoRestriccion.VEHICULO_NO_HABILITADO;
            } else {
                motivo = MotivoRestriccion.CONDUCTOR_RESTRINGIDO;
            }

            // Paradas sin datos internos (sin loginUsuario)
            List<ParadaPublicaDTO> paradasDto = paradas.stream()
                    .map(t -> new ParadaPublicaDTO(t.getOrdenParada(), t.getUbicacion(), t.getLatitud(), t.getLongitud()))
                    .toList();

            resultado.add(new RutaRestringidaDTO(
                    entrada.getKey(),
                    motivo,
                    new ConductorPublicoDTO(conductor.getNombres(), conductor.getApellidos()), // sin identificación ni correo
                    vehiculo.getPlaca(),
                    paradasDto));
        }

        return resultado;
    }

    // ============ REGLAS COMPARTIDAS (una sola definición para crearRuta y la consulta 4) ============

    // "Vehículo habilitado" = tiene AL MENOS un documento y TODOS están en Habilitado.
    // Un vehículo sin documentos NO está habilitado.
    private boolean vehiculoHabilitado(Long idVehiculo) {
        List<VehiculoDocumento> documentos = vehiculoDocumentoRepository.findByVehiculo_Id(idVehiculo);
        return !documentos.isEmpty()
                && documentos.stream().allMatch(vd -> vd.getEstadoDocumento() == EstadoDocumento.HABILITADO);
    }

    // El conductor está restringido si su asociación con ESE vehículo está en RO.
    private boolean conductorRestringido(Long idVehiculo, Long idPersona) {
        return vehiculoPersonaRepository.findById(new VehiculoPersonaId(idVehiculo, idPersona))
                .map(vp -> vp.getEstadoConductor() == EstadoConductor.RESTRINGIDO)
                .orElse(false);
    }

    // Validaciones que no dependen de la base de datos
    private void validarEstructuraDeRuta(List<TrayectoRequestDTO> paradas) {
        if (paradas == null || paradas.size() < MIN_PARADAS || paradas.size() > MAX_PARADAS) {
            throw new ReglaNegocioException("La ruta debe tener entre " + MIN_PARADAS + " y " + MAX_PARADAS
                    + " paradas (inicial, final y máximo 5 intermedias).");
        }

        TrayectoRequestDTO base = paradas.get(0);
        Set<Integer> ordenesVistos = new HashSet<>();

        for (TrayectoRequestDTO p : paradas) {
            if (p == null || p.getIdPersona() == null || p.getIdVehiculo() == null || p.getOrdenParada() == null
                    || esVacio(p.getCodigoRuta()) || esVacio(p.getUbicacion())) {
                throw new ReglaNegocioException(
                        "Cada parada debe traer idPersona, idVehiculo, codigoRuta, ubicacion y ordenParada.");
            }
            // todas las paradas pertenecen a la MISMA ruta, conductor y vehículo
            if (!p.getIdPersona().equals(base.getIdPersona())
                    || !p.getIdVehiculo().equals(base.getIdVehiculo())
                    || !p.getCodigoRuta().equals(base.getCodigoRuta())) {
                throw new ReglaNegocioException(
                        "Todas las paradas de una ruta deben tener el mismo conductor, vehículo y código de ruta.");
            }
            // 0 = inicial, el mayor = final. Con valores únicos dentro de 0..n-1 quedan exactamente 0,1,2,...,n-1
            if (p.getOrdenParada() < 0 || p.getOrdenParada() >= paradas.size()) {
                throw new ReglaNegocioException("ordenParada debe estar entre 0 y " + (paradas.size() - 1) + ".");
            }
            if (!ordenesVistos.add(p.getOrdenParada())) { // add() devuelve false si ya estaba
                throw new ReglaNegocioException("El ordenParada " + p.getOrdenParada() + " está repetido en la ruta.");
            }
        }

        // el código de ruta agrupa paradas: no puede reutilizarse
        if (trayectoRepository.existsByCodigoRuta(base.getCodigoRuta())) {
            throw new ReglaNegocioException("Ya existe una ruta con el código " + base.getCodigoRuta() + ".");
        }
    }

    // El filtro JWT puso al Usuario completo como "principal" del SecurityContext
    private String obtenerLoginAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario usuario) {
            return usuario.getId().getLogin();
        }
        throw new ReglaNegocioException("No se pudo identificar al usuario autenticado que registra el trayecto.");
    }

    private boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}