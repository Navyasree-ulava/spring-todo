import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Circle,
  Loader2,
  LogOut,
  MoreHorizontal,
  Pencil,
  Plus,
  Search,
  Sun,
  Moon,
  Trash2,
  X,
} from 'lucide-react'
import { toast } from 'sonner'

import { ApiError, api } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { Priority, Todo, TodoStats, TodoStatus } from '@/lib/types'
import { useTheme } from 'next-themes'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import TodoDialog from '@/components/TodoDialog'

const STATUS_LABEL: Record<TodoStatus, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  DONE: 'Done',
}

export default function TodoPage() {

  const [todos, setTodos] = useState<Todo[]>([])
  const [stats, setStats] = useState<TodoStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)

  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<TodoStatus | 'ALL'>('ALL')
  const [priority, setPriority] = useState<Priority | 'ALL'>('ALL')
  const [sort, setSort] = useState('default')

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<Todo | null>(null)
  const [pendingDelete, setPendingDelete] = useState<Todo | null>(null)

  const composerRef = useRef<HTMLInputElement>(null)
  // Tracks the value at the time a request started, so a typing user does not
  // have their text reverted when a background refetch lands.
  const searchRef = useRef(search)
  searchRef.current = search

  const load = useCallback(
    async (options: { quiet?: boolean } = {}) => {
      if (!options.quiet) setLoading(true)
      const params = new URLSearchParams()
      if (search) params.set('search', search)
      if (status !== 'ALL') params.set('status', status)
      if (priority !== 'ALL') params.set('priority', priority)
      if (sort !== 'default') params.set('sort', sort)
      try {
        const [list, counts] = await Promise.all([
          api.get<Todo[]>(`/todos?${params}`),
          api.get<TodoStats>('/todos/stats'),
        ])
        setTodos(list)
        setStats(counts)
      } catch (caught) {
        if (caught instanceof ApiError && caught.status !== 401) {
          toast.error(caught.message)
        }
      } finally {
        setLoading(false)
      }
    },
    [search, status, priority, sort],
  )

  // Debounce so typing does not fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => load(), search ? 250 : 0)
    return () => clearTimeout(timer)
  }, [load, search])

  // Keyboard shortcuts: "/" focuses search, "n" opens the composer.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement
      const typing = ['INPUT', 'TEXTAREA'].includes(target?.tagName)
      if (event.key === '/' && !typing) {
        event.preventDefault()
        document.getElementById('search-input')?.focus()
      }
      if (event.key === 'n' && !typing) {
        event.preventDefault()
        setEditing(null)
        setDialogOpen(true)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  async function createTodo(title: string) {
    const trimmed = title.trim()
    if (!trimmed) return
    setBusy(true)
    try {
      // Optimistic insert: the row appears immediately and is reconciled by
      // the refetch. Feels instant on a normal network.
      const optimistic: Todo = {
        id: -Date.now(),
        title: trimmed,
        description: null,
        status: 'TODO',
        priority: 'MEDIUM',
        dueDate: null,
        overdue: false,
        completedAt: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      setTodos((current) => [optimistic, ...current])
      if (composerRef.current) composerRef.current.value = ''

      await api.post<Todo>('/todos', { title: trimmed })
      await load({ quiet: true })
    } catch (caught) {
      setTodos((current) => current.filter((t) => t.id > 0 || t.title !== trimmed))
      toast.error(caught instanceof ApiError ? caught.message : 'Could not add that')
    } finally {
      setBusy(false)
    }
  }

  async function toggleStatus(todo: Todo) {
    const next: TodoStatus = todo.status === 'DONE' ? 'TODO' : 'DONE'
    const previous = todos
    // Optimistic: the checkbox responds on the same frame as the click.
    setTodos((current) =>
      current.map((t) =>
        t.id === todo.id
          ? { ...t, status: next, completedAt: next === 'DONE' ? new Date().toISOString() : null }
          : t,
      ),
    )
    try {
      await api.patch<Todo>(`/todos/${todo.id}/status?status=${next}`)
      await load({ quiet: true })
    } catch {
      setTodos(previous)
      toast.error('Could not update that')
    }
  }

  async function removeTodo(todo: Todo) {
    const previous = todos
    setTodos((current) => current.filter((t) => t.id !== todo.id))
    setPendingDelete(null)
    try {
      await api.delete(`/todos/${todo.id}`)
      await load({ quiet: true })
      toast.success('Deleted')
    } catch {
      setTodos(previous)
      toast.error('Could not delete that')
    }
  }

  const filtersActive = search !== '' || status !== 'ALL' || priority !== 'ALL' || sort !== 'default'

  return (
    <div className="min-h-dvh">
      <Header />

      <main className="mx-auto w-full max-w-2xl px-5 pb-28 pt-8">
        <StatsBar stats={stats} loading={loading} />

        <section className="mt-7">
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-0 flex-1">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="search-input"
                defaultValue={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search tasks"
                className="h-8 pl-8 pr-8"
              />
              {search && (
                <button
                  type="button"
                  onClick={() => setSearch('')}
                  aria-label="Clear search"
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-0.5 text-muted-foreground hover:text-foreground"
                >
                  <X className="size-3.5" />
                </button>
              )}
            </div>

            <FilterSelect
              value={status}
              onChange={(v) => v && setStatus(v as TodoStatus | 'ALL')}
              label="Status"
              options={[
                { value: 'ALL', label: 'All statuses' },
                { value: 'TODO', label: 'To do' },
                { value: 'IN_PROGRESS', label: 'In progress' },
                { value: 'DONE', label: 'Done' },
              ]}
            />
            <FilterSelect
              value={priority}
              onChange={(v) => v && setPriority(v as Priority | 'ALL')}
              label="Priority"
              options={[
                { value: 'ALL', label: 'All priorities' },
                { value: 'HIGH', label: 'High' },
                { value: 'MEDIUM', label: 'Medium' },
                { value: 'LOW', label: 'Low' },
              ]}
            />
            <FilterSelect
              value={sort}
              onChange={(v) => v && setSort(v)}
              label="Sort"
              options={[
                { value: 'default', label: 'Priority' },
                { value: 'dueDate', label: 'Due date' },
                { value: 'createdAt', label: 'Newest' },
                { value: 'title', label: 'Title' },
              ]}
            />
          </div>

          <div className="mt-3 flex items-center justify-between">
            <p className="text-xs text-muted-foreground">
              {loading
                ? 'Loading…'
                : `${todos.length} ${todos.length === 1 ? 'task' : 'tasks'}${
                    filtersActive ? ' matching' : ''
                  }`}
            </p>
            {filtersActive && (
              <button
                type="button"
                onClick={() => {
                  setSearch('')
                  setStatus('ALL')
                  setPriority('ALL')
                  setSort('default')
                }}
                className="text-xs text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
              >
                Reset filters
              </button>
            )}
          </div>
        </section>

        <section className="mt-4">
          {loading ? (
            <TodoSkeleton />
          ) : todos.length === 0 ? (
            <EmptyState hasFilters={filtersActive} onClear={() => {
              setSearch('')
              setStatus('ALL')
              setPriority('ALL')
              setSort('default')
            }} />
          ) : (
            <ul className="overflow-hidden rounded-xl border border-border bg-card">
              {todos.map((todo) => (
                <TodoRow
                  key={todo.id}
                  todo={todo}
                  onToggle={() => toggleStatus(todo)}
                  onEdit={() => {
                    setEditing(todo)
                    setDialogOpen(true)
                  }}
                  onDelete={() => setPendingDelete(todo)}
                />
              ))}
            </ul>
          )}
        </section>
      </main>

      <Composer busy={busy} inputRef={composerRef} onSubmit={createTodo} />

      <TodoDialog
        open={dialogOpen}
        todo={editing}
        onOpenChange={(open) => {
          setDialogOpen(open)
          if (!open) setEditing(null)
        }}
        onSaved={() => load({ quiet: true })}
      />

      <AlertDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this task?</AlertDialogTitle>
            <AlertDialogDescription>
              “{pendingDelete?.title}” will be permanently removed. This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => pendingDelete && removeTodo(pendingDelete)}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

function Header() {
  const { user, logout } = useAuth()
  const { resolvedTheme, setTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  // next-themes cannot know the theme during SSR/first paint, so the icon is
  // only rendered after mount to avoid a hydration mismatch.
  useEffect(() => setMounted(true), [])

  const initials = (user?.displayName ?? '?')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background/85 backdrop-blur-sm">
      <div className="mx-auto flex h-13 w-full max-w-2xl items-center gap-3 px-5 py-3">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold tracking-[-0.01em]">
            {user?.displayName ?? 'Tasks'}
          </p>
          <p className="truncate text-xs text-muted-foreground">{user?.email}</p>
        </div>

        {user?.avatarUrl ? (
          <img
            src={user.avatarUrl}
            alt=""
            className="size-7 rounded-full border border-border object-cover"
            referrerPolicy="no-referrer"
          />
        ) : (
          <span className="grid size-7 shrink-0 place-items-center rounded-full bg-secondary text-[0.65rem] font-semibold tracking-wide text-secondary-foreground">
            {initials}
          </span>
        )}

        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={mounted && resolvedTheme === 'dark' ? 'Switch to light' : 'Switch to dark'}
          onClick={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')}
        >
          {mounted && resolvedTheme === 'dark' ? (
            <Sun className="size-4" />
          ) : (
            <Moon className="size-4" />
          )}
        </Button>

        <Button variant="ghost" size="icon-sm" aria-label="Sign out" onClick={() => logout()}>
          <LogOut className="size-4" />
        </Button>
      </div>
    </header>
  )
}

function StatsBar({ stats, loading }: { stats: TodoStats | null; loading: boolean }) {
  if (loading && !stats) {
    return (
      <div className="grid grid-cols-4 gap-3">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-14 rounded-lg" />
        ))}
      </div>
    )
  }
  if (!stats) return null

  const items = [
    { label: 'To do', value: stats.todo, className: 'text-status-todo' },
    { label: 'In progress', value: stats.inProgress, className: 'text-status-progress' },
    { label: 'Done', value: stats.done, className: 'text-status-done' },
    { label: 'Overdue', value: stats.overdue, className: stats.overdue ? 'text-destructive' : 'text-muted-foreground' },
  ]

  return (
    <div className="grid grid-cols-4 gap-3">
      {items.map((item) => (
        <div key={item.label} className="rounded-lg border border-border bg-card px-3 py-2.5">
          <p className="label-xs text-muted-foreground">{item.label}</p>
          <p className={`mt-0.5 text-lg font-semibold tabular-nums tracking-[-0.02em] ${item.className}`}>
            {item.value}
          </p>
        </div>
      ))}
    </div>
  )
}

function FilterSelect({
  value,
  onChange,
  label,
  options,
}: {
  value: string
  onChange: (value: string | null) => void
  label: string
  options: { value: string; label: string }[]
}) {
  const active = value !== 'ALL' && value !== 'default'
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger
        aria-label={label}
        className={`h-8 w-auto gap-1.5 text-[0.8125rem] ${active ? 'border-primary/40 text-foreground' : ''}`}
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value} className="text-[0.8125rem]">
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

function TodoRow({
  todo,
  onToggle,
  onEdit,
  onDelete,
}: {
  todo: Todo
  onToggle: () => void
  onEdit: () => void
  onDelete: () => void
}) {
  const done = todo.status === 'DONE'
  const priorityColor =
    todo.priority === 'HIGH'
      ? 'text-priority-high'
      : todo.priority === 'MEDIUM'
        ? 'text-priority-medium'
        : 'text-priority-low'

  return (
    <li className="group relative flex items-start gap-3 border-b border-border px-3 py-3 last:border-b-0 hover:bg-muted/50">
      {/* A hairline on the left edge encodes priority without adding a badge -
          it reads at a glance and costs no layout width. */}
      <span
        aria-hidden="true"
        className={`absolute inset-y-0 left-0 w-0.5 ${done ? 'bg-transparent' : priorityColor.replace('text-', 'bg-')}`}
      />

      <Checkbox
        checked={done}
        onCheckedChange={onToggle}
        aria-label={done ? `Mark "${todo.title}" as not done` : `Mark "${todo.title}" as done`}
        className="mt-0.5 size-4 rounded-[5px]"
      />

      <div className="min-w-0 flex-1">
        <p
          className={`text-sm leading-snug ${
            done ? 'text-muted-foreground line-through decoration-muted-foreground/50' : ''
          }`}
        >
          {todo.title}
        </p>

        <div className="mt-1 flex flex-wrap items-center gap-x-2.5 gap-y-1 text-xs text-muted-foreground">
          {todo.status !== 'TODO' && (
            <span className={todo.status === 'DONE' ? 'text-status-done' : 'text-status-progress'}>
              {STATUS_LABEL[todo.status]}
            </span>
          )}
          {!done && (
            <span className={priorityColor}>
              {todo.priority.charAt(0) + todo.priority.slice(1).toLowerCase()}
            </span>
          )}
          {todo.dueDate && (
            <span className={todo.overdue ? 'font-medium text-destructive' : undefined}>
              {todo.overdue ? 'Overdue · ' : 'Due '}
              {new Date(todo.dueDate).toLocaleDateString(undefined, {
                month: 'short',
                day: 'numeric',
              })}
            </span>
          )}
          {todo.description && (
            <span className="truncate opacity-70">{todo.description.slice(0, 60)}</span>
          )}
        </div>
      </div>

      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="Task actions"
              // Hidden until hover so the list stays calm, but always reachable
              // by keyboard because focus-within also reveals it.
              className="opacity-0 transition-opacity focus-visible:opacity-100 group-hover:opacity-100 data-[state=open]:opacity-100"
            />
          }
        >
          <MoreHorizontal className="size-4" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-36">
          <DropdownMenuItem onSelect={onEdit}>
            <Pencil className="size-3.5" />
            Edit
          </DropdownMenuItem>
          <DropdownMenuItem
            onSelect={onDelete}
            className="text-destructive focus:bg-destructive/10 focus:text-destructive"
          >
            <Trash2 className="size-3.5" />
            Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </li>
  )
}

function Composer({
  busy,
  inputRef,
  onSubmit,
}: {
  busy: boolean
  inputRef: React.RefObject<HTMLInputElement | null>
  onSubmit: (title: string) => void
}) {
  const [value, setValue] = useState('')

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-0 z-20 bg-gradient-to-t from-background via-background/92 to-transparent pb-5 pt-8">
      <form
        className="pointer-events-auto mx-auto w-full max-w-2xl px-5"
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit(value)
          setValue('')
        }}
      >
        <div className="flex items-center gap-2 rounded-xl border border-border bg-card px-3 py-2 shadow-[0_1px_2px_rgba(0,0,0,0.04)] focus-within:border-ring focus-within:ring-2 focus-within:ring-ring/20">
          <Plus className="size-4 shrink-0 text-muted-foreground" />
          <input
            ref={inputRef}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            placeholder="Add a task and press Enter"
            aria-label="Add a task"
            className="h-6 min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          />
          {value.trim() && (
            <Button type="submit" size="sm" disabled={busy} className="h-7">
              {busy ? <Loader2 className="size-3.5 animate-spin" /> : 'Add'}
            </Button>
          )}
        </div>
        <p className="mt-2 px-1 text-[0.6875rem] text-muted-foreground">
          <Kbd>n</Kbd> new task · <Kbd>/</Kbd> search
        </p>
      </form>
    </div>
  )
}

function Kbd({ children }: { children: React.ReactNode }) {
  return (
    <kbd className="rounded border border-border bg-muted px-1 py-px font-sans text-[0.625rem] font-medium">
      {children}
    </kbd>
  )
}

function EmptyState({ hasFilters, onClear }: { hasFilters: boolean; onClear: () => void }) {
  return (
    <div className="rounded-xl border border-dashed border-border px-6 py-14 text-center">
      {hasFilters ? (
        <>
          <Search className="mx-auto size-5 text-muted-foreground/60" />
          <p className="mt-3 text-sm font-medium">No tasks match these filters</p>
          <p className="mt-1 text-sm text-muted-foreground">Try widening your search.</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={onClear}>
            Reset filters
          </Button>
        </>
      ) : (
        <>
          <Circle className="mx-auto size-5 text-muted-foreground/60" />
          <p className="mt-3 text-sm font-medium">Nothing here yet</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Add your first task using the box below.
          </p>
        </>
      )}
    </div>
  )
}

function TodoSkeleton() {
  return (
    <div className="overflow-hidden rounded-xl border border-border bg-card">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex items-center gap-3 border-b border-border px-3 py-3.5 last:border-b-0">
          <Skeleton className="size-4 rounded-[5px]" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-3.5 w-2/5" />
            <Skeleton className="h-2.5 w-1/5" />
          </div>
        </div>
      ))}
    </div>
  )
}
