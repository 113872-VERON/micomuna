// Sugiere el enlace de una comuna: nombre + provincia, en minúsculas, sin tildes y con guiones
// (Salsipuedes + CORDOBA da salsipuedes-cordoba). El backend vuelve a validar y es quien decide.
export function sugerirSlug(nombre: string, provinciaClave: string): string {
  const base = nombre
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  if (!base || !provinciaClave) {
    return '';
  }
  return `${base}-${provinciaClave.toLowerCase().replace(/_/g, '-')}`;
}
