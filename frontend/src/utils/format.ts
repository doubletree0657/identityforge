export function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

export function csvToArray(value: string) {
  return value
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function arrayToCsv(values?: string[]) {
  return (values ?? []).join(', ');
}

export function compact<T extends Record<string, unknown>>(value: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(value).filter(([, field]) => field !== undefined && field !== ''),
  ) as Partial<T>;
}
