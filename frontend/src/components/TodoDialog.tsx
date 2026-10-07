import { useEffect, useState } from 'react'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'

import { ApiError, api } from '@/lib/api'
import type { Priority, Todo, TodoStatus } from '@/lib/types'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

/**
 * One dialog for both create and edit. `todo === null` means create.
 *
 * For an existing task the due date is stored as an ISO instant but edited as a
 * plain date, because "2026-12-31" is what a person means and a time picker is
 * noise for a to-do list.
 */
export default function TodoDialog({
  open,
  todo,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  todo: Todo | null
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const editing = todo !== null

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<Priority>('MEDIUM')
  const [status, setStatus] = useState<TodoStatus>('TODO')
  const [dueDate, setDueDate] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Reset the form each time the dialog opens so a previous edit never leaks in.
  useEffect(() => {
    if (!open) return
    setTitle(todo?.title ?? '')
    setDescription(todo?.description ?? '')
    setPriority(todo?.priority ?? 'MEDIUM')
    setStatus(todo?.status ?? 'TODO')
    setDueDate(toDateInputValue(todo?.dueDate ?? null))
    setError(null)
  }, [open, todo])

  async function save() {
    if (!title.trim()) {
      setError('Give the task a title.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      const payload = {
        title: title.trim(),
        description: description.trim() || null,
        priority,
        status,
        dueDate: dueDate ? new Date(`${dueDate}T12:00:00`).toISOString() : null,
      }
      if (editing) {
        await api.put<Todo>(`/todos/${todo.id}`, payload)
        toast.success('Task updated')
      } else {
        await api.post<Todo>('/todos', payload)
        toast.success('Task added')
      }
      onOpenChange(false)
      onSaved()
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not save that task.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{editing ? 'Edit task' : 'New task'}</DialogTitle>
          <DialogDescription>
            {editing ? 'Update the details below.' : 'Capture it now, organise it later.'}
          </DialogDescription>
        </DialogHeader>

        <form
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            save()
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="todo-title">Title</Label>
            <Input
              id="todo-title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="What needs doing?"
              autoFocus
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="todo-desc">
              Notes <span className="font-normal text-muted-foreground">(optional)</span>
            </Label>
            <Textarea
              id="todo-desc"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Any detail worth remembering"
              rows={3}
              className="resize-none"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="todo-priority">Priority</Label>
              <Select value={priority} onValueChange={(v) => setPriority(v as Priority)}>
                <SelectTrigger id="todo-priority" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="HIGH">High</SelectItem>
                  <SelectItem value="MEDIUM">Medium</SelectItem>
                  <SelectItem value="LOW">Low</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="todo-due">Due date</Label>
              <Input
                id="todo-due"
                type="date"
                value={dueDate}
                onChange={(event) => setDueDate(event.target.value)}
              />
            </div>
          </div>

          {editing && (
            <div className="space-y-1.5">
              <Label htmlFor="todo-status">Status</Label>
              <Select value={status} onValueChange={(v) => setStatus(v as TodoStatus)}>
                <SelectTrigger id="todo-status" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="TODO">To do</SelectItem>
                  <SelectItem value="IN_PROGRESS">In progress</SelectItem>
                  <SelectItem value="DONE">Done</SelectItem>
                </SelectContent>
              </Select>
            </div>
          )}

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}

          <DialogFooter className="pt-1">
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving && <Loader2 className="size-4 animate-spin" />}
              {editing ? 'Save changes' : 'Add task'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/**
 * datetime-local inputs need `YYYY-MM-DDTHH:mm`. Taking the UTC date rather
 * than the local one stops the day shifting when a user west of Greenwich
 * opens a task due "today" - the classic off-by-one-day bug in date pickers.
 */
function toDateInputValue(iso: string | null): string {
  if (!iso) return ''
  return iso.slice(0, 10)
}
