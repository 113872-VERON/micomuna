import { UsuarioDto } from './modelos';

// La pantalla de inicio de cada rol (a donde se vuelve desde "Mis datos" o "Cambiar contraseña").
export function inicioDeUsuario(usuario: UsuarioDto | null): string[] {
  const slug = usuario?.comunaSlug;
  if (!usuario || !slug) {
    return usuario?.rol === 'SUPERADMIN' ? ['/admin', 'comunas'] : ['/'];
  }
  switch (usuario.rol) {
    case 'AGENTE':
      return ['/', slug, 'agente'];
    case 'ADMINISTRATIVO':
    case 'ADMIN_COMUNA':
      return ['/', slug, 'panel', 'reclamos'];
    default:
      return ['/', slug, 'inicio'];
  }
}
