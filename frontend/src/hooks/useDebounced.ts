import { useEffect, useState } from 'react';

/** Delays a rapidly changing value, for search-as-you-type. */
export const useDebounced = <T,>(value: T, delayMs = 300): T => {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
};
