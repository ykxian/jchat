import { usePreferences } from "../../preferences/preferences";

interface SessionGateFallbackProps {
  title: string;
  description: string;
}

export function SessionGateFallback({ title, description }: SessionGateFallbackProps) {
  const { copy } = usePreferences();

  return (
    <div className="gate-screen">
      <section className="auth-card auth-card--compact">
        <p className="eyebrow">{copy.shell.workspace}</p>
        <h2>{title}</h2>
        <p className="muted">{description}</p>
      </section>
    </div>
  );
}
