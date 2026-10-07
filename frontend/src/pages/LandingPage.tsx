import { Link } from 'react-router-dom'
import {
  ArrowRight,
  Check,
  Filter,
  Keyboard,
  LogIn,
  Moon,
  Search,
  ShieldCheck,
  Zap,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import { useAuth } from '@/lib/auth'
import { ThemeToggle } from '@/components/ThemeToggle'

/**
 * Public landing page. Deliberately the same palette as the app - a warm neutral
 * base with teal reserved for interactive elements - so moving from here into the
 * dashboard feels like one product rather than two.
 *
 * Avoids the usual landing-page furniture: no giant centred hero headline, no
 * gradient mesh, no floating glass cards. The layout is left-aligned with real
 * content density, which reads as a product page rather than a template.
 */
export default function LandingPage() {
  const { googleEnabled } = useAuth()

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-30 border-b border-border bg-background/85 backdrop-blur-sm">
        <div className="mx-auto flex h-13 w-full max-w-5xl items-center gap-3 px-5 py-3">
          <Mark />
          <span className="text-sm font-semibold tracking-[-0.01em]">Tasks</span>

          <nav className="ml-auto hidden items-center gap-6 text-sm text-muted-foreground sm:flex">
            <a href="#features" className="transition-colors hover:text-foreground">
              Features
            </a>
            <a href="#stack" className="transition-colors hover:text-foreground">
              Stack
            </a>
          </nav>

          <div className="ml-auto flex items-center gap-1.5 sm:ml-0">
            <ThemeToggle />
            <Button size="sm" variant="ghost" render={<Link to="/login" />}>
              Sign in
            </Button>
            <Button size="sm" render={<Link to="/login" />}>
              Get started
              <ArrowRight className="size-3.5" />
            </Button>
          </div>
        </div>
      </header>

      <main>
        {/* Hero - left aligned, tight leading, no gradient */}
        <section className="mx-auto w-full max-w-5xl px-5 pb-20 pt-16 sm:pt-24">
          <p className="label-xs text-primary">Spring Boot · React · PostgreSQL</p>
          <h1 className="mt-4 max-w-2xl text-4xl font-semibold leading-[1.08] tracking-[-0.03em] sm:text-5xl">
            A to-do app that stays out of the way.
          </h1>
          <p className="mt-5 max-w-xl text-[1.0625rem] leading-relaxed text-muted-foreground">
            Sign in with Google or email and password. Search, filter and sort without
            losing your place. Dark mode included, because you will use this at night.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Button size="lg" render={<Link to="/login" />}>
              Start for free
              <ArrowRight className="size-4" />
            </Button>
            {googleEnabled && (
              <p className="text-sm text-muted-foreground">
                or continue with Google in one click
              </p>
            )}
          </div>

          <p className="mt-6 flex items-center gap-2 text-sm text-muted-foreground">
            <ShieldCheck className="size-4 text-primary" />
            No card, no tracking, your data stays yours.
          </p>
        </section>

        {/* A real product screenshot beats a marketing illustration, so this is
            an honest mock of the list view using the app's own tokens. */}
        <section className="mx-auto w-full max-w-5xl px-5 pb-20">
          <PreviewCard />
        </section>

        {/* Features */}
        <section id="features" className="border-y border-border bg-muted/40">
          <div className="mx-auto w-full max-w-5xl px-5 py-20">
            <h2 className="max-w-lg text-2xl font-semibold tracking-[-0.02em]">
              Built for the boring parts, too.
            </h2>
            <p className="mt-3 max-w-lg text-muted-foreground">
              The details that decide whether a to-do list survives a month of use.
            </p>

            <div className="mt-10 grid gap-px overflow-hidden rounded-xl border border-border bg-border sm:grid-cols-2 lg:grid-cols-3">
              {FEATURES.map((feature) => (
                <div key={feature.title} className="bg-background p-6">
                  <feature.icon className="size-5 text-primary" />
                  <h3 className="mt-4 text-[0.9375rem] font-medium">{feature.title}</h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                    {feature.body}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Stack */}
        <section id="stack" className="mx-auto w-full max-w-5xl px-5 py-20">
          <div className="grid gap-10 sm:grid-cols-[1fr_1.2fr]">
            <div>
              <h2 className="text-2xl font-semibold tracking-[-0.02em]">
                Nothing exotic.
              </h2>
              <p className="mt-3 text-muted-foreground">
                Every layer is something you can read about and run locally.
              </p>
            </div>
            <dl className="divide-y divide-border border-y border-border">
              {STACK.map((row) => (
                <div key={row.term} className="flex gap-4 py-3.5">
                  <dt className="w-32 shrink-0 text-sm font-medium">{row.term}</dt>
                  <dd className="text-sm text-muted-foreground">{row.detail}</dd>
                </div>
              ))}
            </dl>
          </div>
        </section>

        {/* Closing CTA */}
        <section className="border-t border-border bg-muted/40">
          <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center justify-between gap-6 px-5 py-16">
            <div>
              <h2 className="text-xl font-semibold tracking-[-0.02em]">
                Your list is waiting.
              </h2>
              <p className="mt-1.5 text-sm text-muted-foreground">
                Takes about a minute to get going.
              </p>
            </div>
            <Button size="lg" render={<Link to="/login" />}>
              {googleEnabled ? 'Continue with Google' : 'Create an account'}
              <ArrowRight className="size-4" />
            </Button>
          </div>
        </section>
      </main>

      <footer className="border-t border-border">
        <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center gap-3 px-5 py-8 text-sm text-muted-foreground">
          <Mark small />
          <span>Tasks</span>
          <span className="ml-auto">
            A demo project · Spring Boot, React and PostgreSQL
          </span>
        </div>
      </footer>
    </div>
  )
}

function Mark({ small = false }: { small?: boolean }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground ${
        small ? 'size-5' : 'size-7'
      }`}
      aria-hidden="true"
    >
      <svg viewBox="0 0 24 24" className={small ? 'size-3' : 'size-4'} fill="none" strokeWidth={3}>
        <path d="M4 7l4.5 4.5L20 4" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  )
}

/** Mock of the real list, drawn with the app's own design tokens. */
function PreviewCard() {
  const rows = [
    { title: 'Ship the to-do app', meta: 'In progress', tone: 'text-status-progress', done: false },
    { title: 'Review the design system', meta: 'Medium', tone: 'text-priority-medium', done: false },
    { title: 'Overdue: ship yesterday', meta: 'Overdue', tone: 'text-destructive', done: false },
    { title: 'Write the integration tests', meta: 'Done', tone: 'text-status-done', done: true },
  ]

  return (
    <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(0,0,0,0.04)]">
      <div className="flex items-center gap-3 border-b border-border px-4 py-3">
        <span className="text-sm font-semibold">Ada Lovelace</span>
        <span className="text-xs text-muted-foreground">ada@example.com</span>
        <Moon className="ml-auto size-4 text-muted-foreground" />
      </div>

      <div className="grid grid-cols-4 gap-px bg-border">
        {[
          { label: 'To do', value: '2' },
          { label: 'In progress', value: '1' },
          { label: 'Done', value: '1' },
          { label: 'Overdue', value: '1' },
        ].map((stat) => (
          <div key={stat.label} className="bg-card px-3 py-2.5">
            <p className="label-xs text-muted-foreground">{stat.label}</p>
            <p className="mt-0.5 text-lg font-semibold tabular-nums tracking-[-0.02em]">{stat.value}</p>
          </div>
        ))}
      </div>

      <ul>
        {rows.map((row) => (
          <li
            key={row.title}
            className="relative flex items-start gap-3 border-b border-border px-4 py-3 last:border-b-0"
          >
            <span
              className={`absolute inset-y-0 left-0 w-0.5 ${row.done ? 'bg-transparent' : 'bg-primary/70'}`}
              aria-hidden="true"
            />
            <span
              className={`mt-0.5 grid size-4 shrink-0 place-items-center rounded-[5px] border ${
                row.done ? 'border-primary bg-primary text-primary-foreground' : 'border-input'
              }`}
            >
              {row.done && <Check className="size-3" />}
            </span>
            <div className="min-w-0">
              <p className={`text-sm ${row.done ? 'text-muted-foreground line-through' : ''}`}>
                {row.title}
              </p>
              <p className={`mt-1 text-xs ${row.tone}`}>{row.meta}</p>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}

const FEATURES = [
  {
    icon: LogIn,
    title: 'Two ways in',
    body: 'Google one-click, or email and password. One account either way, so your list follows you.',
  },
  {
    icon: Search,
    title: 'Instant search',
    body: 'Filter by text, status or priority as you type, without waiting for a page load.',
  },
  {
    icon: Filter,
    title: 'Sort that makes sense',
    body: 'By due date, priority or title. Undated items sink to the bottom instead of floating to the top.',
  },
  {
    icon: Zap,
    title: 'Feels instant',
    body: 'Tasks appear the moment you add them, and roll back cleanly if the network disagrees.',
  },
  {
    icon: Moon,
    title: 'Light and dark',
    body: 'Follows your system by default. Remembered after that, with no white flash on reload.',
  },
  {
    icon: Keyboard,
    title: 'Keyboard first',
    body: 'Press / to search and n for a new task. Tab through everything else as you would expect.',
  },
]

const STACK = [
  { term: 'Backend', detail: 'Spring Boot 4, Java 25, Spring Security, Flyway' },
  { term: 'Frontend', detail: 'React 19, TypeScript, Tailwind, shadcn/ui' },
  { term: 'Database', detail: 'PostgreSQL 17 in Docker, migrated by Flyway' },
  { term: 'Auth', detail: 'JWT in an httpOnly cookie, plus Google OAuth2' },
  { term: 'Tests', detail: 'Integration tests against a real Postgres container' },
]
