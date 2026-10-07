import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Merges class names, letting later Tailwind utilities win over earlier ones.
 *
 * clsx handles conditionals and arrays; twMerge resolves conflicts so that
 * `cn('p-2', 'p-4')` yields 'p-4' rather than both classes fighting in the
 * cascade with the winner decided by stylesheet order.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
