export function Badge({ children }: { children: string }) {
  return (
    <span className="inline-flex items-center rounded-full border border-line bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-700">
      {children}
    </span>
  );
}
