export function luhn(digits) {
  if (!/^[0-9]+$/.test(digits) || /^0+$/.test(digits)) return false;
  let sum = 0, doubleDigit = false;
  for (let index = digits.length - 1; index >= 0; index--) {
    let digit = Number(digits[index]);
    if (doubleDigit && (digit *= 2) > 9) digit -= 9;
    sum += digit;
    doubleDigit = !doubleDigit;
  }
  return sum % 10 === 0;
}

export function validIdentity(id, dateOfBirth) {
  if (!/^[0-9]{13}$/.test(id) || !luhn(id) || !'01'.includes(id[10]) || !'89'.includes(id[11])) return false;
  if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(dateOfBirth)) return false;
  const date = new Date(dateOfBirth + 'T12:00:00Z');
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== dateOfBirth) return false;
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Johannesburg', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  return dateOfBirth <= today && Number(dateOfBirth.slice(0, 4)) >= new Date().getFullYear() - 120
    && id.slice(0, 6) === dateOfBirth.slice(2).replaceAll('-', '');
}

export function validCard(card) {
  if (!card.trim()) return true;
  if (!/^[0-9 -]{12,32}$/.test(card)) return false;
  const digits = card.replace(/[ -]/g, '');
  return /^[0-9]{12,19}$/.test(digits) && luhn(digits);
}
