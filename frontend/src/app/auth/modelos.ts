export type Rol = 'VECINO' | 'ADMINISTRATIVO' | 'AGENTE' | 'ADMIN_COMUNA' | 'SUPERADMIN';

export type ColorComuna = 'CELESTE' | 'MARINO' | 'VERDE_AZULADO' | 'BOSQUE' | 'CIRUELA' | 'OCRE' | 'PIZARRA';

export interface ComunaDto {
  nombre: string;
  provincia: string;
  departamento: string | null;
  slug: string;
  tieneLogo: boolean;
  color: ColorComuna | string;
  telefono?: string | null;
  whatsapp?: string | null;
}

export interface UsuarioDto {
  id: number;
  nombre: string;
  correo: string;
  rol: Rol;
  comunaSlug: string | null;
}

export interface AuthRespuesta {
  token: string;
  usuario: UsuarioDto;
}

// Lo que ve el superadmin de cada comuna (incluye si está activa).
export interface ComunaAdminDto extends ComunaDto {
  id: number;
  activa: boolean;
  // Sin límite, los vecinos pueden reportar desde cualquier lugar.
  tieneLimite?: boolean;
  limiteCargadoEn?: string | null;
  creadaEn?: string | null;
  // Última modificación: datos, logo, límite o estado.
  actualizadaEn?: string | null;
}

// Cambios parciales de una comuna (PATCH): solo viajan los campos que cambiaron.
// Un teléfono, WhatsApp o departamento vacío ('') lo borra.
export interface ActualizarComunaRequest {
  nombre?: string;
  departamento?: string;
  color?: string;
  telefono?: string;
  whatsapp?: string;
  activa?: boolean;
}

export interface NuevaComunaRequest {
  nombre: string;
  provincia: string;
  slug?: string;
  color?: string;
  departamento?: string;
  telefono?: string;
  whatsapp?: string;
  adminNombre: string;
  adminCorreo: string;
}

// La comuna se crea aunque el correo falle: `invitacionEnviada` dice si salió la invitación al administrador.
export interface ComunaCreadaDto {
  comuna: ComunaAdminDto;
  adminCorreo: string;
  invitacionEnviada: boolean;
}

// Las 24 jurisdicciones. La clave es la que espera el backend (enum `Provincia`).
export const PROVINCIAS: readonly { clave: string; nombre: string }[] = [
  { clave: 'BUENOS_AIRES', nombre: 'Buenos Aires' },
  { clave: 'CABA', nombre: 'Ciudad Autónoma de Buenos Aires' },
  { clave: 'CATAMARCA', nombre: 'Catamarca' },
  { clave: 'CHACO', nombre: 'Chaco' },
  { clave: 'CHUBUT', nombre: 'Chubut' },
  { clave: 'CORDOBA', nombre: 'Córdoba' },
  { clave: 'CORRIENTES', nombre: 'Corrientes' },
  { clave: 'ENTRE_RIOS', nombre: 'Entre Ríos' },
  { clave: 'FORMOSA', nombre: 'Formosa' },
  { clave: 'JUJUY', nombre: 'Jujuy' },
  { clave: 'LA_PAMPA', nombre: 'La Pampa' },
  { clave: 'LA_RIOJA', nombre: 'La Rioja' },
  { clave: 'MENDOZA', nombre: 'Mendoza' },
  { clave: 'MISIONES', nombre: 'Misiones' },
  { clave: 'NEUQUEN', nombre: 'Neuquén' },
  { clave: 'RIO_NEGRO', nombre: 'Río Negro' },
  { clave: 'SALTA', nombre: 'Salta' },
  { clave: 'SAN_JUAN', nombre: 'San Juan' },
  { clave: 'SAN_LUIS', nombre: 'San Luis' },
  { clave: 'SANTA_CRUZ', nombre: 'Santa Cruz' },
  { clave: 'SANTA_FE', nombre: 'Santa Fe' },
  { clave: 'SANTIAGO_DEL_ESTERO', nombre: 'Santiago del Estero' },
  { clave: 'TIERRA_DEL_FUEGO', nombre: 'Tierra del Fuego' },
  { clave: 'TUCUMAN', nombre: 'Tucumán' },
];

// ---- Usuarios de una comuna (panel del administrador de la comuna) ----

export type RolDeComuna = Exclude<Rol, 'SUPERADMIN'>;

export type EstadoUsuario = 'ACTIVO' | 'INACTIVO' | 'PENDIENTE';

// Un usuario de la comuna tal como lo ve su administrador. Nunca trae la clave.
export interface UsuarioComunaDto {
  id: number;
  nombre: string;
  correo: string;
  rol: RolDeComuna;
  estado: EstadoUsuario;
  creadoEn: string;
}

// Solo personal: los vecinos se registran solos.
export type RolDePersonal = Exclude<RolDeComuna, 'VECINO'>;

export interface CrearUsuarioRequest {
  nombre: string;
  correo: string;
  rol: RolDePersonal;
}

// El usuario se crea aunque el correo falle: `invitacionEnviada` dice si salió la invitación.
export interface UsuarioCreadoDto {
  usuario: UsuarioComunaDto;
  invitacionEnviada: boolean;
}

// Cambio parcial: se manda solo lo que cambia. El correo solo se corrige mientras está PENDIENTE.
export interface ActualizarUsuarioRequest {
  rol?: RolDeComuna;
  activo?: boolean;
  correo?: string;
}

export const NOMBRES_ROL: Record<RolDeComuna, string> = {
  VECINO: 'Vecino',
  AGENTE: 'Agente',
  ADMINISTRATIVO: 'Personal administrativo',
  ADMIN_COMUNA: 'Administrador',
};

export const NOMBRES_ESTADO: Record<EstadoUsuario, string> = {
  ACTIVO: 'Activo',
  INACTIVO: 'Inactivo',
  PENDIENTE: 'Pendiente',
};
