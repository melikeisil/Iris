import { useState } from 'react';
import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { LogIn } from 'lucide-react';
import { Button } from '@/design-system/components/Button';
import { FormField } from '@/design-system/components/fields';
import { ErrorState } from '@/design-system/components/states';
import { useAuth } from '@/app/providers/AuthProvider';
import { paths } from '@/app/router/paths';
import { brand } from '@/config/brand';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';

/**
 * Sign in.
 *
 * The Stitch set has no login screen, so this is a minimal new composition built only
 * from Iris tokens and existing primitives: the editorial serif title, a
 * surface-container-lowest card on the #07090C canvas, and the single indigo primary
 * action. Nothing here is invented visually that does not already appear in the
 * converted screens.
 */
const schema = z.object({
  email: z
    .string()
    .min(1, 'Enter your work email.')
    .email('That does not look like an email address.'),
  password: z.string().min(8, 'Passwords are at least 8 characters.'),
});

type LoginValues = z.infer<typeof schema>;

export const LoginPage = () => {
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitError, setSubmitError] = useState<unknown>(null);
  useDocumentTitle('Sign in');

  const from = (location.state as { from?: string } | null)?.from ?? paths.dashboard;

  const form = useForm<LoginValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });

  if (status === 'authenticated') return <Navigate to={from} replace />;

  const onSubmit = form.handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await login(values.email, values.password);
      navigate(from, { replace: true });
    } catch (error) {
      setSubmitError(error);
    }
  });

  return (
    <div className="mx-auto max-w-md">
      <div className="mb-8">
        <span className="mb-3 block font-technical-label text-technical-label uppercase tracking-[0.25em] text-brand-indigo">
          Sign in
        </span>
        <h1 className="font-display-lg text-4xl italic text-on-surface">
          Welcome back to {brand.productName}
        </h1>
        <p className="mt-2 font-body-lg text-body-lg text-on-surface-variant opacity-80">
          {brand.descriptor}
        </p>
      </div>

      <div className="rounded-xl border border-border-structural bg-surface-container-lowest p-8">
        <form onSubmit={onSubmit} noValidate className="space-y-6">
          <FormField
            label="Work email"
            type="email"
            autoComplete="username"
            required
            placeholder="you@company.com"
            error={form.formState.errors.email?.message}
            {...form.register('email')}
          />
          <FormField
            label="Password"
            type="password"
            autoComplete="current-password"
            required
            error={form.formState.errors.password?.message}
            {...form.register('password')}
          />

          {submitError ? <ErrorState error={submitError} compact /> : null}

          <Button
            type="submit"
            fullWidth
            size="lg"
            loading={form.formState.isSubmitting}
            leadingIcon={<LogIn className="size-4" aria-hidden />}
          >
            Sign in
          </Button>
        </form>
      </div>

      <p className="mt-6 text-center text-[0.75rem] text-on-surface-variant/60">
        Access is provisioned by your administrator. There is no self-service sign-up.
      </p>
    </div>
  );
};

export default LoginPage;
