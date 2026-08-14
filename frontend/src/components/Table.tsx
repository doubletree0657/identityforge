import { ReactNode } from 'react';
import { EmptyState } from './State';

export interface Column<T> {
  header: string;
  render: (item: T) => ReactNode;
  className?: string;
}

export function DataTable<T>({
  items,
  columns,
  emptyTitle = 'No records found',
  emptyDetail,
  emptyAction,
  getKey,
}: {
  items: T[];
  columns: Column<T>[];
  emptyTitle?: string;
  emptyDetail?: string;
  emptyAction?: ReactNode;
  getKey?: (item: T) => string;
}) {
  if (items.length === 0) {
    return <EmptyState title={emptyTitle} detail={emptyDetail} action={emptyAction} />;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] text-left text-sm">
        <thead className="border-b border-line bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            {columns.map((column) => (
              <th key={column.header} className={`px-4 py-3 font-semibold ${column.className ?? ''}`}>
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {items.map((item, index) => (
            <tr key={getKey?.(item) ?? index} className="bg-white transition hover:bg-slate-50/70">
              {columns.map((column) => (
                <td key={column.header} className={`px-4 py-3 align-top ${column.className ?? ''}`}>
                  {column.render(item)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
