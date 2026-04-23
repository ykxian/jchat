interface SessionGateFallbackProps {
  title: string;
  description: string;
}

export function SessionGateFallback({ title, description }: SessionGateFallbackProps) {
  return (
    <div className="gate-screen">
      <section className="auth-card auth-card--compact">
        <p className="eyebrow">Session</p>
        <h2>{title}</h2>
        <p className="muted">{description}</p>
      </section>
    </div>
  );
}
