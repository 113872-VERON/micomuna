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
