CREATE TABLE orchestration_runs (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
    state TEXT NOT NULL CHECK (state IN ('PLANNED', 'READY', 'RUNNING', 'WAITING_EXIT_APPROVAL', 'COMPLETE', 'FAILED', 'CANCELLED', 'ROLLED_BACK')),
    graph_version INTEGER NOT NULL CHECK (graph_version >= 1),
    entry_approved INTEGER NOT NULL DEFAULT 0 CHECK (entry_approved IN (0, 1)),
    created_at TEXT NOT NULL,
    started_at TEXT,
    completed_at TEXT,
    outcome TEXT,
    rollback_metadata TEXT,
    last_error TEXT
);

CREATE TABLE orchestration_tasks (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES orchestration_runs(id),
    task_key TEXT NOT NULL,
    description TEXT NOT NULL CHECK (length(description) BETWEEN 1 AND 240),
    action TEXT NOT NULL CHECK (length(action) BETWEEN 1 AND 80),
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'WAITING_APPROVAL', 'READY', 'RUNNING', 'RETRY_WAITING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    high_impact INTEGER NOT NULL DEFAULT 0 CHECK (high_impact IN (0, 1)),
    max_attempts INTEGER NOT NULL CHECK (max_attempts BETWEEN 1 AND 3),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    worker_id TEXT,
    claimed_at TEXT,
    started_at TEXT,
    completed_at TEXT,
    output_summary TEXT,
    error_code TEXT,
    fallback_task_key TEXT,
    rollback_metadata TEXT,
    UNIQUE (run_id, task_key)
);

CREATE TABLE orchestration_dependencies (
    run_id TEXT NOT NULL REFERENCES orchestration_runs(id),
    task_key TEXT NOT NULL,
    depends_on_key TEXT NOT NULL,
    PRIMARY KEY (run_id, task_key, depends_on_key),
    CHECK (task_key <> depends_on_key)
);

CREATE TABLE orchestration_attempts (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES orchestration_runs(id),
    task_key TEXT NOT NULL,
    attempt_no INTEGER NOT NULL CHECK (attempt_no >= 1),
    state TEXT NOT NULL CHECK (state IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    worker_id TEXT NOT NULL,
    started_at TEXT NOT NULL,
    ended_at TEXT,
    error_code TEXT,
    outcome_summary TEXT,
    rollback_metadata TEXT,
    UNIQUE (run_id, task_key, attempt_no)
);

CREATE TABLE orchestration_approvals (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES orchestration_runs(id),
    task_key TEXT,
    checkpoint TEXT NOT NULL CHECK (checkpoint IN ('ENTRY', 'TASK', 'EXIT')),
    decision TEXT NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    approved_by TEXT NOT NULL CHECK (length(approved_by) BETWEEN 1 AND 80),
    reason TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE orchestration_graph_versions (
    run_id TEXT NOT NULL REFERENCES orchestration_runs(id),
    version INTEGER NOT NULL CHECK (version >= 1),
    parent_version INTEGER,
    trigger_task_key TEXT,
    decision TEXT NOT NULL CHECK (length(decision) BETWEEN 1 AND 240),
    created_at TEXT NOT NULL,
    PRIMARY KEY (run_id, version)
);

CREATE TABLE orchestration_audit_events (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES orchestration_runs(id),
    task_key TEXT,
    event_type TEXT NOT NULL CHECK (length(event_type) BETWEEN 1 AND 80),
    from_state TEXT,
    to_state TEXT,
    actor TEXT NOT NULL CHECK (length(actor) BETWEEN 1 AND 80),
    detail TEXT NOT NULL CHECK (length(detail) <= 240),
    occurred_at TEXT NOT NULL
);

CREATE TABLE orchestration_metrics (
    run_id TEXT PRIMARY KEY REFERENCES orchestration_runs(id),
    outcome TEXT NOT NULL,
    success_rate REAL NOT NULL DEFAULT 0,
    retry_frequency REAL NOT NULL DEFAULT 0,
    rollback_frequency REAL NOT NULL DEFAULT 0,
    mttr_ms INTEGER NOT NULL DEFAULT 0,
    end_to_end_latency_ms INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL
);

CREATE TABLE analytics_click_events (
    id INTEGER PRIMARY KEY,
    code TEXT NOT NULL COLLATE BINARY,
    occurred_at TEXT NOT NULL
);

CREATE INDEX orchestration_tasks_ready_idx ON orchestration_tasks(run_id, state);
CREATE INDEX orchestration_dependencies_task_idx ON orchestration_dependencies(run_id, task_key);
CREATE INDEX orchestration_audit_run_idx ON orchestration_audit_events(run_id, occurred_at);
CREATE INDEX analytics_click_code_time_idx ON analytics_click_events(code, occurred_at);
