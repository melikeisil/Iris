import { useEffect } from 'react';
import { pageTitle } from '@/config/brand';

export const useDocumentTitle = (title?: string): void => {
  useEffect(() => {
    document.title = pageTitle(title);
  }, [title]);
};
