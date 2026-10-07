package com.todoapp.todo.dto;

/** Counts shown above the list, so the UI does not have to derive them. */
public record TodoStatsResponse(long total, long todo, long inProgress, long done, long overdue) {
}
