import { RolDeComuna } from '../../../auth/modelos';

// Qué hace cada rol, en palabras del vecino. Se muestra al elegir el rol de alguien.
export const DESCRIPCIONES_ROL: Record<RolDeComuna, string> = {
  VECINO: 'Reporta problemas y sigue sus reclamos.',
  ADMINISTRATIVO: 'Ve todos los reclamos y los asigna a los agentes.',
  AGENTE: 'Resuelve los reclamos que le asignan, desde el celular.',
  ADMIN_COMUNA: 'Hace lo mismo que el personal administrativo y además maneja los usuarios de la comuna.',
};
