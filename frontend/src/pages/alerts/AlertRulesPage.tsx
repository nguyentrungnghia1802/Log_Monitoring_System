import { useEffect, useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  fetchAlertRules,
  createAlertRule,
  toggleAlertRule,
  deleteAlertRule,
} from "../../services/api";
import { fetchProjects } from "../../services/projectApi";
import type { AlertRule } from "../../types/alert";

export function AlertRulesPage() {
  const queryClient = useQueryClient();
  const [projectId, setProjectId] = useState("");
  const [showModal, setShowModal] = useState(false);
  const [formData, setFormData] = useState<Partial<AlertRule>>({
    name: "",
    environment: "production",
    service: "",
    levels: ["ERROR"],
    windowSeconds: 60,
    threshold: 10,
    cooldownSeconds: 300,
    eventTypes: [],
  });

  const projectsQuery = useQuery({
    queryKey: ["projects"],
    queryFn: fetchProjects,
  });
  const projects = useMemo(
    () => projectsQuery.data ?? [],
    [projectsQuery.data],
  );
  useEffect(() => {
    if (!projectId && projects.length > 0) setProjectId(projects[0].id);
  }, [projectId, projects]);

  const { data: rules = [], isLoading } = useQuery<AlertRule[]>({
    queryKey: ["alert-rules", projectId],
    queryFn: () => fetchAlertRules(projectId),
    enabled: Boolean(projectId),
  });

  const createMutation = useMutation({
    mutationFn: (newRule: Partial<AlertRule>) =>
      createAlertRule(projectId, newRule),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["alert-rules", projectId] });
      setShowModal(false);
      setFormData({
        name: "",
        environment: "production",
        service: "",
        levels: ["ERROR"],
        windowSeconds: 60,
        threshold: 10,
        cooldownSeconds: 300,
        eventTypes: [],
      });
    },
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      toggleAlertRule(projectId, id, enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["alert-rules", projectId] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteAlertRule(projectId, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["alert-rules", projectId] });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name?.trim()) return;
    createMutation.mutate(formData);
  };

  return (
    <div className="p-6 max-w-7xl mx-auto space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">Alert Rules</h1>
          <p className="text-sm text-slate-400">
            Configure log thresholds and automated triggers
          </p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-sm font-medium transition"
        >
          + Create Alert Rule
        </button>
      </div>

      <label className="block max-w-xl text-sm text-slate-300">
        Project
        <select
          aria-label="Project"
          value={projectId}
          onChange={(event) => setProjectId(event.target.value)}
          className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2"
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

      {projectsQuery.isLoading || isLoading ? (
        <div className="text-center py-12 text-slate-400">
          Loading alert rules...
        </div>
      ) : rules.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-12 text-center text-slate-400">
          No alert rules configured yet. Click "+ Create Alert Rule" to add one.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {rules.map((rule) => (
            <div
              key={rule.id}
              className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-3"
            >
              <div className="flex justify-between items-start">
                <div>
                  <h3 className="font-semibold text-slate-200">{rule.name}</h3>
                  <div className="text-xs text-slate-400 mt-1 flex gap-2">
                    <span className="bg-slate-800 px-2 py-0.5 rounded text-slate-300">
                      Env: {rule.environment || "All"}
                    </span>
                    <span className="bg-slate-800 px-2 py-0.5 rounded text-slate-300">
                      Svc: {rule.service || "All"}
                    </span>
                  </div>
                  <p className="mt-2 text-xs text-slate-500">
                    Levels: {rule.levels?.join(", ") || "All"} · Event types:{" "}
                    {rule.eventTypes?.join(", ") || "All"}
                  </p>
                </div>
                <button
                  onClick={() =>
                    rule.id &&
                    toggleMutation.mutate({
                      id: rule.id,
                      enabled: !rule.enabled,
                    })
                  }
                  className={`px-3 py-1 text-xs rounded-full font-medium transition ${
                    rule.enabled
                      ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                      : "bg-slate-800 text-slate-400 border border-slate-700"
                  }`}
                >
                  {rule.enabled ? "Enabled" : "Disabled"}
                </button>
              </div>

              <div className="text-xs text-slate-300 bg-slate-950 p-3 rounded-lg flex justify-between">
                <div>
                  <span className="text-slate-500">Condition: </span>
                  <span className="font-mono text-amber-400">
                    &gt;= {rule.threshold} events / {rule.windowSeconds}s
                  </span>
                </div>
                <div>
                  <span className="text-slate-500">Cooldown: </span>
                  <span className="font-mono text-slate-300">
                    {rule.cooldownSeconds}s
                  </span>
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
                <button
                  onClick={() => rule.id && deleteMutation.mutate(rule.id)}
                  className="text-xs text-red-400 hover:text-red-300 transition"
                >
                  Delete Rule
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 max-w-md w-full space-y-4">
            <h2 className="text-lg font-bold text-slate-100">
              Create New Alert Rule
            </h2>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">
                  Rule Name
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Queue Service Error Spike"
                  value={formData.name}
                  onChange={(e) =>
                    setFormData({ ...formData, name: e.target.value })
                  }
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-blue-500"
                  maxLength={120}
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-slate-400 block mb-1">
                    Levels (comma-separated)
                  </label>
                  <input
                    aria-label="Levels"
                    type="text"
                    placeholder="ERROR, WARN"
                    value={formData.levels?.join(", ") ?? ""}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        levels: e.target.value
                          .split(",")
                          .map((item) => item.trim())
                          .filter(Boolean),
                      })
                    }
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="text-xs text-slate-400 block mb-1">
                    Event types (comma-separated)
                  </label>
                  <input
                    aria-label="Event types"
                    type="text"
                    placeholder="QUEUE_CREATE_FAILED"
                    value={formData.eventTypes?.join(", ") ?? ""}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        eventTypes: e.target.value
                          .split(",")
                          .map((item) => item.trim())
                          .filter(Boolean),
                      })
                    }
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-slate-400 block mb-1">
                    Environment
                  </label>
                  <input
                    type="text"
                    placeholder="production"
                    value={formData.environment}
                    onChange={(e) =>
                      setFormData({ ...formData, environment: e.target.value })
                    }
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="text-xs text-slate-400 block mb-1">
                    Service
                  </label>
                  <input
                    type="text"
                    placeholder="queue-service"
                    value={formData.service}
                    onChange={(e) =>
                      setFormData({ ...formData, service: e.target.value })
                    }
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="text-xs text-slate-400 block mb-1">
                    Window (sec)
                  </label>
                  <input
                    type="number"
                    aria-label="Window (sec)"
                    min={10}
                    max={86400}
                    value={formData.windowSeconds}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        windowSeconds: Number(e.target.value),
                      })
                    }
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="text-xs text-slate-400 block mb-1">
                    Threshold
                  </label>
                  <input
                    type="number"
                    aria-label="Threshold"
                    min={1}
                    max={1000000}
                    value={formData.threshold}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        threshold: Number(e.target.value),
                      })
                    }
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="text-xs text-slate-400 block mb-1">
                    Cooldown (sec)
                  </label>
                  <input
                    type="number"
                    aria-label="Cooldown (sec)"
                    min={1}
                    max={604800}
                    value={formData.cooldownSeconds}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        cooldownSeconds: Number(e.target.value),
                      })
                    }
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              {createMutation.error && (
                <p role="alert" className="text-sm text-red-300">
                  {createMutation.error.message}
                </p>
              )}

              <div className="flex justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm rounded-lg transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm rounded-lg font-medium transition"
                >
                  {createMutation.isPending ? "Saving..." : "Create Rule"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
