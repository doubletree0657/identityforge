import { Check } from 'lucide-react';

export interface StepItem {
  label: string;
  detail?: string;
  status: 'complete' | 'current' | 'upcoming';
}

export function Stepper({ steps, label = 'Workflow progress' }: { steps: StepItem[]; label?: string }) {
  return (
    <ol className="grid gap-3 sm:grid-cols-3" aria-label={label}>
      {steps.map((step, index) => (
        <li key={step.label} className={`rounded-lg border p-3 ${step.status === 'current' ? 'border-brand bg-brand/5' : 'border-line bg-white'}`} aria-current={step.status === 'current' ? 'step' : undefined}>
          <div className="flex items-center gap-2">
            <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${step.status === 'complete' ? 'bg-brand text-white' : step.status === 'current' ? 'border-2 border-brand text-brand' : 'bg-slate-100 text-slate-500'}`}>
              {step.status === 'complete' ? <Check className="h-3.5 w-3.5" /> : index + 1}
            </span>
            <span className="text-sm font-semibold text-ink">{step.label}</span>
          </div>
          {step.detail && <p className="mt-2 text-xs leading-5 text-slate-500">{step.detail}</p>}
        </li>
      ))}
    </ol>
  );
}
