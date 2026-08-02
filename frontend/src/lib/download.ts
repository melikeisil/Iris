/** Client-side CSV export for report tables. No backend reporting API exists yet. */
export const toCsv = (headers: readonly string[], rows: readonly (readonly string[])[]): string => {
  const escape = (cell: string) => `"${cell.replaceAll('"', '""')}"`;
  return [headers, ...rows].map((row) => row.map(escape).join(',')).join('\n');
};

export const downloadCsv = (filename: string, content: string): void => {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
};
