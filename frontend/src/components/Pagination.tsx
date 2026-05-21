import { ChevronLeft, ChevronRight } from 'lucide-react';
import { PageResponse } from '../types/api';
import { Button } from './Button';

export function Pagination<T>({
  page,
  onPageChange,
}: {
  page?: PageResponse<T>;
  onPageChange: (page: number) => void;
}) {
  if (!page) {
    return null;
  }
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3 text-sm text-slate-600">
      <span>
        Page {page.page + 1} of {Math.max(page.totalPages, 1)} · {page.totalElements} total
      </span>
      <div className="flex items-center gap-2">
        <Button
          variant="secondary"
          icon={<ChevronLeft className="h-4 w-4" />}
          disabled={!page.hasPrevious}
          onClick={() => onPageChange(page.page - 1)}
        >
          Previous
        </Button>
        <Button
          variant="secondary"
          icon={<ChevronRight className="h-4 w-4" />}
          disabled={!page.hasNext}
          onClick={() => onPageChange(page.page + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  );
}
