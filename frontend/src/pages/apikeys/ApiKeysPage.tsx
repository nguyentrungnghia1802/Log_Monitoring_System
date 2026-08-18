import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchProjects } from "../../services/projectApi";
import {
  createApiKey,
  fetchApiKeys,
  revokeApiKey,
  rotateApiKey,
} from "../../services/apiKeyApi";
import type { ApiKeyWithSecret } from "../../types/apiKey";

function formatTimestamp(value: string | null) {
  return value ? new Date(value).toLocaleString() : "Never";
}

function statusClasses(status: string) {
  return status === "ACTIVE"
    ? "bg-emerald-50 text-emerald-700 ring-emerald-200"
    : "bg-slate-100 text-slate-600 ring-slate-200";
}

export function ApiKeysPage() {
  const queryClient = useQueryClient();
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [name, setName] = useState("");
  const [oneTimeSecret, setOneTimeSecret] = useState<ApiKeyWithSecret | null>(
    null,
  );
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);

  const projectsQuery = useQuery({
    queryKey: ["projects"],
    queryFn: fetchProjects,
  });

  const projects = projectsQuery.data ?? [];
  const selectedProject =
    projects.find((project) => project.id === selectedProjectId) ?? null;

  useEffect(() => {
    if (!selectedProjectId && projects.length > 0) {
      setSelectedProjectId(projects[0].id);
    }
  }, [projects, selectedProjectId]);

  const keysQuery = useQuery({
    queryKey: ["api-keys", selectedProjectId],
    queryFn: () => fetchApiKeys(selectedProjectId),
    enabled: Boolean(selectedProjectId),
  });

  const invalidateKeys = () => {
    void queryClient.invalidateQueries({
      queryKey: ["api-keys", selectedProjectId],
    });
  };

  const createMutation = useMutation({
    gcTime: 0,
    mutationFn: () => createApiKey(selectedProjectId, name.trim()),
    onSuccess: (created) => {
      setOneTimeSecret(created);
      setCopied(false);
      setCopyError(false);
      setName("");
      invalidateKeys();
    },
  });

  const rotateMutation = useMutation({
    gcTime: 0,
    mutationFn: (keyId: string) => rotateApiKey(selectedProjectId, keyId),
    onSuccess: (rotated) => {
      setOneTimeSecret(rotated);
      setCopied(false);
      setCopyError(false);
      invalidateKeys();
    },
  });

  const revokeMutation = useMutation({
    mutationFn: (keyId: string) => revokeApiKey(selectedProjectId, keyId),
    onSuccess: invalidateKeys,
  });

  const dismissSecret = () => {
    setOneTimeSecret(null);
    setCopied(false);
    setCopyError(false);
    createMutation.reset();
    rotateMutation.reset();
  };

  const submitCreate = (event: FormEvent) => {
    event.preventDefault();
    if (selectedProject?.active && name.trim()) {
      createMutation.mutate();
    }
  };

  const copySecret = async () => {
    if (!oneTimeSecret || !navigator.clipboard) {
      setCopyError(true);
      return;
    }
    try {
      await navigator.clipboard.writeText(oneTimeSecret.rawApiKey);
      setCopied(true);
      setCopyError(false);
    } catch {
      setCopied(false);
      setCopyError(true);
    }
  };

  const rotate = (keyId: string, keyName: string) => {
    if (
      window.confirm(
        `Rotate ${keyName}? The current secret will stop working immediately.`,
      )
    ) {
      rotateMutation.mutate(keyId);
    }
  };

  const revoke = (keyId: string, keyName: string) => {
    if (
      window.confirm(
        `Revoke ${keyName}? This cannot be undone and ingestion will stop.`,
      )
    ) {
      revokeMutation.mutate(keyId);
    }
  };

  if (projectsQuery.isLoading) {
    return (
      <main className="mx-auto max-w-6xl p-6 text-slate-600">
        Loading API-key management…
      </main>
    );
  }

  if (projectsQuery.error) {
    return (
      <main className="mx-auto max-w-6xl space-y-4 p-6">
        <h1 className="text-2xl font-bold text-slate-900">API keys</h1>
        <div className="rounded-xl border border-rose-200 bg-rose-50 p-5 text-rose-800">
          <p>
            {projectsQuery.error instanceof Error
              ? projectsQuery.error.message
              : "Projects could not be loaded."}
          </p>
          <button
            onClick={() => void projectsQuery.refetch()}
            className="mt-3 rounded-lg bg-rose-700 px-3 py-2 text-sm font-semibold text-white"
          >
            Retry
          </button>
        </div>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-7xl space-y-6 p-6">
      <div>
        <p className="text-sm font-semibold uppercase tracking-wide text-sky-700">
          Administration
        </p>
        <h1 className="mt-1 text-3xl font-bold text-slate-900">API keys</h1>
        <p className="mt-1 max-w-3xl text-sm text-slate-600">
          Issue credentials for a monitored project and revoke or rotate them
          without exposing stored secrets.
        </p>
      </div>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <label className="block max-w-xl text-sm font-medium text-slate-700">
          Project
          <select
            disabled={createMutation.isPending || rotateMutation.isPending}
            value={selectedProjectId}
            onChange={(event) => {
              setSelectedProjectId(event.target.value);
              dismissSecret();
            }}
            className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm disabled:bg-slate-100"
          >
            {projects.length === 0 && (
              <option value="">No projects available</option>
            )}
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name} ({project.key})
              </option>
            ))}
          </select>
        </label>
        {projects.length === 0 && (
          <p className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
            Create a project first from the Projects page before issuing an API
            key.
          </p>
        )}
        {selectedProject && !selectedProject.active && (
          <p className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
            This project is inactive. New keys cannot be created; existing keys
            remain visible for audit and revocation.
          </p>
        )}
      </section>

      <section className="grid gap-6 lg:grid-cols-[0.75fr_1.25fr]">
        <form
          onSubmit={submitCreate}
          className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
        >
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              Create API key
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              Give each source application its own key so it can be rotated
              independently.
            </p>
          </div>
          <label className="block text-sm font-medium text-slate-700">
            Key name
            <input
              required
              maxLength={100}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="production collector"
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </label>
          <button
            disabled={
              !selectedProject?.active ||
              createMutation.isPending ||
              Boolean(oneTimeSecret)
            }
            title={
              oneTimeSecret
                ? "Dismiss the current one-time secret before creating another key."
                : undefined
            }
            className="rounded-lg bg-sky-700 px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {createMutation.isPending ? "Creating…" : "Create API key"}
          </button>
          {createMutation.error && (
            <p className="text-sm text-rose-700">
              {createMutation.error.message}
            </p>
          )}
        </form>

        {oneTimeSecret && (
          <section
            aria-live="polite"
            className="space-y-4 rounded-2xl border-2 border-amber-300 bg-amber-50 p-5 shadow-sm"
          >
            <div>
              <p className="text-sm font-semibold uppercase tracking-wide text-amber-800">
                Secret shown once
              </p>
              <h2 className="mt-1 text-lg font-semibold text-slate-900">
                Save this secret before dismissing
              </h2>
              <p className="mt-1 text-sm text-amber-900">
                <span>
                  It will not be shown again. Store it in your secret manager.
                </span>{" "}
                <span>This page does not save it to browser storage.</span>
              </p>
            </div>
            <code
              data-testid="one-time-api-key"
              className="block break-all rounded-lg border border-amber-200 bg-white p-3 text-xs text-slate-900"
            >
              {oneTimeSecret.rawApiKey}
            </code>
            <div className="flex flex-wrap gap-3">
              <button
                type="button"
                onClick={() => void copySecret()}
                className="rounded-lg bg-amber-700 px-4 py-2 text-sm font-semibold text-white"
              >
                {copied ? "Copied" : "Copy secret"}
              </button>
              <button
                type="button"
                onClick={dismissSecret}
                className="rounded-lg border border-amber-400 px-4 py-2 text-sm font-semibold text-amber-900"
              >
                I stored it safely
              </button>
            </div>
            {copyError && (
              <p className="text-sm font-medium text-rose-800">
                Copy was unavailable. Select the secret manually, then dismiss
                this screen after saving it.
              </p>
            )}
          </section>
        )}
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              Key inventory
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              Only metadata is returned after creation. The secret is never
              rendered in this table.
            </p>
          </div>
          {selectedProject && (
            <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">
              {selectedProject.name}
            </span>
          )}
        </div>

        {keysQuery.isLoading && (
          <p className="mt-5 text-sm text-slate-600">Loading key metadata…</p>
        )}
        {keysQuery.error && (
          <div className="mt-5 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
            <p>
              {keysQuery.error instanceof Error
                ? keysQuery.error.message
                : "API keys could not be loaded."}
            </p>
            <button
              onClick={() => void keysQuery.refetch()}
              className="mt-3 rounded-lg bg-rose-700 px-3 py-2 font-semibold text-white"
            >
              Retry
            </button>
          </div>
        )}
        {!keysQuery.isLoading &&
          !keysQuery.error &&
          (keysQuery.data?.length ?? 0) === 0 && (
            <p className="mt-5 rounded-lg bg-slate-50 p-5 text-sm text-slate-600">
              No API keys yet. Create one for the first log source above.
            </p>
          )}
        {!keysQuery.isLoading &&
          !keysQuery.error &&
          (keysQuery.data?.length ?? 0) > 0 && (
            <div className="mt-5 overflow-x-auto">
              <table className="w-full min-w-[850px] text-left text-sm">
                <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-3 py-3">Name</th>
                    <th className="px-3 py-3">Public ID</th>
                    <th className="px-3 py-3">Secret ending</th>
                    <th className="px-3 py-3">Status</th>
                    <th className="px-3 py-3">Last used</th>
                    <th className="px-3 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {keysQuery.data?.map((key) => (
                    <tr
                      key={key.id}
                      className={
                        key.status === "REVOKED"
                          ? "text-slate-400"
                          : "text-slate-700"
                      }
                    >
                      <td className="px-3 py-4">
                        <p className="font-semibold text-slate-900">
                          {key.name}
                        </p>
                        <p className="mt-1 text-xs text-slate-500">
                          Created {formatTimestamp(key.createdAt)}
                        </p>
                      </td>
                      <td className="px-3 py-4 font-mono text-xs">
                        {key.publicId}
                      </td>
                      <td className="px-3 py-4 font-mono text-xs">
                        {key.secretLast4
                          ? `••••${key.secretLast4}`
                          : "Unavailable"}
                      </td>
                      <td className="px-3 py-4">
                        <span
                          className={`rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${statusClasses(key.status)}`}
                        >
                          {key.status}
                        </span>
                        {key.revokedAt && (
                          <p className="mt-1 text-xs">
                            Revoked {formatTimestamp(key.revokedAt)}
                          </p>
                        )}
                      </td>
                      <td className="px-3 py-4 text-xs">
                        {formatTimestamp(key.lastUsedAt)}
                      </td>
                      <td className="px-3 py-4">
                        <div className="flex flex-wrap gap-2">
                          {key.status === "ACTIVE" && (
                            <>
                              <button
                                type="button"
                                onClick={() => rotate(key.id, key.name)}
                                disabled={
                                  rotateMutation.isPending ||
                                  Boolean(oneTimeSecret)
                                }
                                title={
                                  oneTimeSecret
                                    ? "Dismiss the current one-time secret before rotating another key."
                                    : undefined
                                }
                                className="rounded-lg border border-amber-300 px-2.5 py-1.5 text-xs font-semibold text-amber-800 disabled:opacity-50"
                              >
                                Rotate
                              </button>
                              <button
                                type="button"
                                onClick={() => revoke(key.id, key.name)}
                                disabled={revokeMutation.isPending}
                                className="rounded-lg border border-rose-300 px-2.5 py-1.5 text-xs font-semibold text-rose-700 disabled:opacity-50"
                              >
                                Revoke
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        {(rotateMutation.error || revokeMutation.error) && (
          <p className="mt-4 text-sm text-rose-700">
            {(rotateMutation.error ?? revokeMutation.error)?.message}
          </p>
        )}
      </section>
    </main>
  );
}
