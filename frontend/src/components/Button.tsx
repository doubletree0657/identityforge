import { ButtonHTMLAttributes, ReactNode } from 'react';
import { Loader2 } from 'lucide-react';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  icon?: ReactNode;
  isLoading?: boolean;
  loadingLabel?: string;
};

export function Button({ variant = 'primary', icon, isLoading = false, loadingLabel, className = '', children, ...props }: Props) {
  const styles = {
    primary: 'bg-brand text-white hover:bg-[#17665c]',
    secondary: 'border border-line bg-white text-ink hover:bg-slate-50',
    danger: 'bg-[#b42318] text-white hover:bg-[#8f1c13]',
    ghost: 'text-ink hover:bg-slate-100',
  };
  return (
    <button
      className={`inline-flex min-h-9 items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${styles[variant]} ${className}`}
      {...props}
      disabled={props.disabled || isLoading}
      aria-busy={isLoading || undefined}
    >
      {isLoading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : icon}
      {isLoading ? loadingLabel ?? children : children}
    </button>
  );
}
