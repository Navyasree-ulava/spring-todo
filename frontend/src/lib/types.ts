export type TodoStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Todo {
  id: number
  title: string
  description: string | null
  status: TodoStatus
  priority: Priority
  dueDate: string | null
  overdue: boolean
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface TodoStats {
  total: number
  todo: number
  inProgress: number
  done: number
  overdue: number
}

export interface User {
  id: number
  email: string
  displayName: string
  avatarUrl: string | null
  emailVerified: boolean
  authProvider: 'LOCAL' | 'GOOGLE'
  googleLinked: boolean
}

export interface TodoPayload {
  title: string
  description?: string | null
  status?: TodoStatus
  priority?: Priority
  dueDate?: string | null
}

export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: Record<string, string> | null
}
