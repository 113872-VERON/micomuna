// Los teléfonos de la comuna vienen como solo dígitos con el 54. Se arma el enlace solo si lo son:
// un dato raro no puede terminar convertido en un enlace.
export function soloDigitos(valor: string | null | undefined): string | null {
  const texto = valor?.trim() ?? '';
  return /^\d{8,15}$/.test(texto) ? texto : null;
}
