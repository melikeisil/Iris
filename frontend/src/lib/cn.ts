import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Conditional class names with Tailwind conflict resolution. */
export const cn = (...inputs: ClassValue[]): string => twMerge(clsx(inputs));
