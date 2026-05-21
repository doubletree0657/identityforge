import { ButtonHTMLAttributes, ReactNode } from 'react';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  icon?: ReactNode;
};

export function Button({ variant = 'primary', icon, className = '', children, ...props }: Props) {
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
    >
      {icon}
      {children}
    </button>
  );
}
