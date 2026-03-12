CREATE TABLE IF NOT EXISTS bibliography_ai_provider_model_history (
provider      TEXT NOT NULL DEFAULT '',
model         TEXT NOT NULL DEFAULT '',
last_used_at  TEXT NOT NULL,
use_count     INTEGER NOT NULL DEFAULT 1,
PRIMARY KEY (provider, model)
);

CREATE INDEX IF NOT EXISTS idx_bibliography_ai_history_provider
    ON bibliography_ai_provider_model_history(provider);

CREATE INDEX IF NOT EXISTS idx_bibliography_ai_history_model
    ON bibliography_ai_provider_model_history(model);

CREATE INDEX IF NOT EXISTS idx_bibliography_ai_history_last_used
    ON bibliography_ai_provider_model_history(last_used_at DESC);