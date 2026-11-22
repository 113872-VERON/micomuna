import { DOCUMENT } from '@angular/common';
import { Component, Injector, OnInit, afterNextRender, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { mensajeDeError } from '../../../auth/errores';
import { EstadoUsuario, NOMBRES_ESTADO, NOMBRES_ROL, RolDeComuna, UsuarioComunaDto } from '../../../auth/modelos';
import { UsuariosService } from '../../../panel/usuarios.service';
import { AyudaFlotante } from '../../../compartido/ayuda-flotante/ayuda-flotante';
import { CorregirCorreo } from './corregir-correo/corregir-correo';
import { DESCRIPCIONES_ROL } from './roles';

// Lo que se está por confirmar en una fila: un rol nuevo, o activar/desactivar.
type Confirmacion =
  | { tipo: 'rol'; usuario: UsuarioComunaDto; rol: RolDeComuna }
  | { tipo: 'estado'; usuario: UsuarioComunaDto; activo: boolean };

// Resultado de la última acción. Si es de una fila se muestra debajo de esa fila.
interface Aviso {
  usuarioId: number | null;
  tipo: 'ok' | 'error';
  texto: string;
}

const ORDEN_ROLES: readonly RolDeComuna[] = ['VECINO', 'AGENTE', 'ADMINISTRATIVO', 'ADMIN_COMUNA'];

// Sin tildes ni mayúsculas: "lucia" encuentra a "Lucía".
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().trim();
}

// Para pegar el texto del servidor con otra oración: si no cierra con un signo final, se le agrega el punto.
export function conPuntoFinal(texto: string): string {
  const limpio = texto.trim();
  return /[.!?…]$/.test(limpio) ? limpio : `${limpio}.`;
}

@Component({
  selector: 'app-usuarios',
  imports: [RouterLink, CorregirCorreo, AyudaFlotante],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.scss',
})
export class Usuarios implements OnInit {
  private readonly servicio = inject(UsuariosService);
  // Viene de la ruta (/:slug/panel/usuarios).
  readonly slug = input.required<string>();
  protected readonly textoEstados =
    'Activo: ya eligió su contraseña y puede ingresar.\nPendiente: todavía no eligió su contraseña; podés reenviarle la invitación.\nInactivo: está desactivado y no puede ingresar hasta que lo actives.';
  protected readonly motivoPropio = 'Es tu usuario: no podés cambiar tu rol ni desactivarte.';
  protected readonly textoAcciones = `${this.motivoPropio}\nLos usuarios no se borran: se desactivan.\nLa comuna siempre tiene que tener al menos un administrador activo.`;
  private readonly auth = inject(AuthService);
  private readonly documento = inject(DOCUMENT);
  private readonly injector = inject(Injector);

  protected readonly roles = ORDEN_ROLES.map((clave) => ({ clave, nombre: NOMBRES_ROL[clave] }));

  protected readonly usuarios = signal<UsuarioComunaDto[]>([]);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal(false);

  // Filtros del lado del cliente: la lista viene completa y en el orden del servidor.
  protected readonly filtroRol = signal<RolDeComuna | ''>('');
  protected readonly busqueda = signal('');
  protected readonly filtrados = computed(() => {
    const rol = this.filtroRol();
    const texto = normalizar(this.busqueda());
    return this.usuarios().filter(
      (u) => (!rol || u.rol === rol) && (!texto || normalizar(u.nombre).includes(texto) || normalizar(u.correo).includes(texto)),
    );
  });
  protected readonly hayFiltros = computed(() => this.filtroRol() !== '' || this.busqueda().trim() !== '');

  protected readonly confirmacion = signal<Confirmacion | null>(null);
  protected readonly corrigiendo = signal<number | null>(null);
  protected readonly errorCorreccion = signal<string | null>(null);
  // Fila con un pedido en curso: sus controles quedan deshabilitados para no mandar dos veces.
  protected readonly ocupado = signal<number | null>(null);
  // Se atiende un pedido por vez: mientras hay uno en curso, los botones que mandan otro quedan deshabilitados.
  protected readonly hayPedido = computed(() => this.ocupado() !== null);
  // Rol que se está guardando: el selector lo sigue mostrando hasta que responde el servidor.
  private readonly rolEnCurso = signal<{ id: number; rol: RolDeComuna } | null>(null);
  protected readonly aviso = signal<Aviso | null>(null);
  // Si la fila del aviso quedó fuera del filtro (o no es de una fila), el aviso va arriba de la tabla.
  protected readonly avisoArriba = computed(() => {
    const aviso = this.aviso();
    return aviso && (aviso.usuarioId === null || !this.filtrados().some((u) => u.id === aviso.usuarioId)) ? aviso : null;
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(false);
    this.servicio.listar().subscribe({
      next: (lista) => {
        this.usuarios.set(lista);
        this.cargando.set(false);
      },
      error: () => {
        this.errorCarga.set(true);
        this.cargando.set(false);
      },
    });
  }

  protected esPropio(u: UsuarioComunaDto): boolean {
    return u.id === this.auth.usuario()?.id;
  }

  protected nombreRol(rol: RolDeComuna): string {
    return NOMBRES_ROL[rol] ?? rol;
  }

  protected descripcionRol(rol: RolDeComuna): string {
    return DESCRIPCIONES_ROL[rol] ?? '';
  }

  protected nombreEstado(estado: EstadoUsuario): string {
    return NOMBRES_ESTADO[estado] ?? estado;
  }

  // Mientras se confirma un cambio de rol, el selector muestra el rol elegido; al cancelar vuelve al actual.
  protected rolMostrado(u: UsuarioComunaDto): RolDeComuna {
    const c = this.confirmacion();
    if (c?.tipo === 'rol' && c.usuario.id === u.id) {
      return c.rol;
    }
    const enCurso = this.rolEnCurso();
    return enCurso?.id === u.id ? enCurso.rol : u.rol;
  }

  protected cantidad(): string {
    const total = this.usuarios().length;
    const vistos = this.filtrados().length;
    const usuarios = (n: number) => (n === 1 ? '1 usuario' : `${n} usuarios`);
    return this.hayFiltros() ? `Mostrando ${vistos} de ${usuarios(total)}` : usuarios(total);
  }

  protected cambiarFiltroRol(evento: Event): void {
    this.filtroRol.set((evento.target as HTMLSelectElement).value as RolDeComuna | '');
  }

  protected cambiarBusqueda(evento: Event): void {
    this.busqueda.set((evento.target as HTMLInputElement).value);
  }

  protected limpiarFiltros(): void {
    this.filtroRol.set('');
    this.busqueda.set('');
    this.enfocar('buscar-usuario');
  }

  protected reemplazar(usuario: UsuarioComunaDto): void {
    this.usuarios.update((lista) => lista.map((u) => (u.id === usuario.id ? usuario : u)));
  }

  // ---- Rol y estado (con confirmación) ----

  protected elegirRol(u: UsuarioComunaDto, evento: Event): void {
    const selector = evento.target as HTMLSelectElement;
    // El selector de la fila propia solo parece deshabilitado (para poder mostrar el motivo): el cambio se frena acá.
    if (this.esPropio(u)) {
      selector.value = u.rol;
      return;
    }
    const rol = selector.value as RolDeComuna;
    this.aviso.set(null);
    this.corrigiendo.set(null);
    this.confirmacion.set(rol === u.rol ? null : { tipo: 'rol', usuario: u, rol });
    if (rol !== u.rol) {
      this.enfocar('confirmar-cancelar');
    }
  }

  protected pedirCambioDeEstado(u: UsuarioComunaDto, activo: boolean): void {
    this.aviso.set(null);
    this.corrigiendo.set(null);
    this.confirmacion.set({ tipo: 'estado', usuario: u, activo });
    this.enfocar('confirmar-cancelar');
  }

  protected cancelarConfirmacion(): void {
    const c = this.confirmacion();
    this.confirmacion.set(null);
    if (c) {
      this.enfocar(c.tipo === 'rol' ? `rol-${c.usuario.id}` : `estado-${c.usuario.id}`);
    }
  }

  protected confirmar(): void {
    const c = this.confirmacion();
    if (!c || this.ocupado() !== null) {
      return;
    }
    const u = c.usuario;
    this.confirmacion.set(null);
    this.ocupado.set(u.id);
    this.rolEnCurso.set(c.tipo === 'rol' ? { id: u.id, rol: c.rol } : null);
    const cambios = c.tipo === 'rol' ? { rol: c.rol } : { activo: c.activo };
    this.servicio.actualizar(u.id, cambios).subscribe({
      next: (actualizado) => {
        this.ocupado.set(null);
        this.rolEnCurso.set(null);
        this.reemplazar(actualizado);
        let texto: string;
        if (c.tipo === 'rol') {
          texto = `Listo: ${actualizado.nombre} ahora es ${this.nombreRol(actualizado.rol)}.`;
        } else if (c.activo && actualizado.estado === 'PENDIENTE') {
          // Al desactivarlo se anuló su enlace y nunca eligió contraseña: sin una invitación nueva no puede entrar.
          texto = `Activamos a ${actualizado.nombre}. Todavía no eligió su contraseña: usá "Reenviar invitación" para mandarle un enlace nuevo.`;
        } else if (c.activo) {
          texto = `Listo: activamos a ${actualizado.nombre}.`;
        } else {
          texto = `Listo: desactivamos a ${actualizado.nombre}. No va a poder ingresar hasta que vuelvas a activar su usuario.`;
        }
        this.aviso.set({ usuarioId: u.id, tipo: 'ok', texto });
      },
      // Si el servidor lo rechaza, el selector vuelve al rol que tenía.
      error: (e: unknown) => {
        this.ocupado.set(null);
        this.rolEnCurso.set(null);
        this.aviso.set({ usuarioId: u.id, tipo: 'error', texto: mensajeDeError(e) });
      },
    });
  }

  // ---- Pendientes: reenviar y corregir el correo ----

  protected reenviar(u: UsuarioComunaDto): void {
    if (this.ocupado() !== null) {
      return;
    }
    this.aviso.set(null);
    this.confirmacion.set(null);
    this.ocupado.set(u.id);
    this.servicio.reenviarInvitacion(u.id).subscribe({
      next: () => {
        this.ocupado.set(null);
        this.aviso.set({ usuarioId: u.id, tipo: 'ok', texto: `Invitación enviada a ${u.correo}.` });
      },
      error: (e: unknown) => {
        this.ocupado.set(null);
        this.aviso.set({ usuarioId: u.id, tipo: 'error', texto: mensajeDeError(e) });
      },
    });
  }

  protected enviandoInvitacion(u: UsuarioComunaDto): boolean {
    return this.ocupado() === u.id && this.corrigiendo() !== u.id;
  }

  // Nombre para el lector de pantalla: empieza con el texto visible y suma a quién (todas las filas tienen el mismo botón).
  protected nombreReenviar(u: UsuarioComunaDto): string {
    return this.enviandoInvitacion(u) ? `Enviando la invitación a ${u.nombre}` : `Reenviar invitación a ${u.nombre}`;
  }

  protected abrirCorreccion(u: UsuarioComunaDto): void {
    this.aviso.set(null);
    this.confirmacion.set(null);
    this.errorCorreccion.set(null);
    this.corrigiendo.set(u.id);
  }

  protected cancelarCorreccion(u: UsuarioComunaDto): void {
    this.corrigiendo.set(null);
    this.enfocar(`corregir-${u.id}`);
  }

  protected corregirCorreo(u: UsuarioComunaDto, correo: string): void {
    if (this.ocupado() !== null) {
      return;
    }
    this.ocupado.set(u.id);
    this.errorCorreccion.set(null);
    this.servicio.corregirCorreoYReenviar(u.id, correo).subscribe({
      next: ({ usuario, invitacionEnviada, errorEnvio }) => {
        this.ocupado.set(null);
        this.corrigiendo.set(null);
        this.reemplazar(usuario);
        const texto = invitacionEnviada
          ? `Modificamos el correo y enviamos la invitación a ${usuario.correo}.`
          : `Guardamos el correo nuevo (${usuario.correo}), pero la invitación no salió. ${conPuntoFinal(mensajeDeError(errorEnvio))} Probá con "Reenviar invitación".`;
        this.aviso.set({ usuarioId: u.id, tipo: invitacionEnviada ? 'ok' : 'error', texto });
      },
      // El correo no cambió (por ejemplo, ya lo usa otra persona): el formulario sigue abierto con el motivo.
      error: (e: unknown) => {
        this.ocupado.set(null);
        this.errorCorreccion.set(mensajeDeError(e));
      },
    });
  }

  private enfocar(id: string): void {
    afterNextRender(() => this.documento.getElementById(id)?.focus(), { injector: this.injector });
  }
}
