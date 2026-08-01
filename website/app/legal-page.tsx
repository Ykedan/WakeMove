import Link from "next/link";
import type { ReactNode } from "react";

type LegalSection = {
  id: string;
  label: string;
  title: string;
  content: ReactNode;
};

type LegalPageProps = {
  eyebrow: string;
  title: string;
  summary: string;
  updatedAt: string;
  sections: LegalSection[];
  alternate: {
    href: string;
    label: string;
    description: string;
  };
};

function LegalOrbitMark() {
  return (
    <span className="legal-brand-mark" aria-hidden="true">
      <i />
    </span>
  );
}

export function LegalPage({
  eyebrow,
  title,
  summary,
  updatedAt,
  sections,
  alternate,
}: LegalPageProps) {
  return (
    <main className="legal-shell">
      <header className="legal-nav">
        <Link className="legal-brand" href="/" aria-label="返回 WakeMove 醒动首页">
          <LegalOrbitMark />
          <span>WAKE<em>MOVE</em></span>
          <b>醒动</b>
        </Link>
        <nav aria-label="说明页面导航">
          <Link href="/">首页</Link>
          <Link href="/privacy">隐私政策</Link>
          <Link href="/security">安全说明</Link>
        </nav>
      </header>

      <section className="legal-hero">
        <div className="legal-hero-orbit" aria-hidden="true">
          <i />
        </div>
        <div className="legal-hero-copy">
          <span className="legal-eyebrow">{eyebrow}</span>
          <h1>{title}</h1>
          <p>{summary}</p>
          <div className="legal-meta">
            <span>最后更新</span>
            <strong>{updatedAt}</strong>
          </div>
        </div>
      </section>

      <div className="legal-layout">
        <aside className="legal-index">
          <span>阅读目录</span>
          <ol>
            {sections.map((section, index) => (
              <li key={section.id}>
                <a href={`#${section.id}`}>
                  <small>{String(index + 1).padStart(2, "0")}</small>
                  {section.label}
                </a>
              </li>
            ))}
          </ol>
        </aside>

        <div className="legal-content">
          {sections.map((section, index) => (
            <article id={section.id} key={section.id}>
              <span className="legal-section-number">
                {String(index + 1).padStart(2, "0")}
              </span>
              <h2>{section.title}</h2>
              <div>{section.content}</div>
            </article>
          ))}

          <Link className="legal-next" href={alternate.href}>
            <span>继续了解</span>
            <strong>{alternate.label}</strong>
            <p>{alternate.description}</p>
            <i aria-hidden="true">→</i>
          </Link>
        </div>
      </div>

      <footer className="legal-footer">
        <div>
          <LegalOrbitMark />
          <strong>WakeMove 醒动</strong>
        </div>
        <p>不只叫醒你，还让你真正醒来。</p>
        <Link href="/">返回首页</Link>
      </footer>
    </main>
  );
}
