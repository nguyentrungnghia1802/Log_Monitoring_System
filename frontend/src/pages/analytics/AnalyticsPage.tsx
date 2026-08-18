import { useState, useEffect } from "react";
import type {
  AnalyticsSummaryResponse,
  AnalyticsHistogramResponse,
} from "../../types/analytics";
import {
  fetchAnalyticsSummary,
  fetchAnalyticsHistogram,
} from "../../services/analyticsApi";

export function AnalyticsPage() {
  const [projectId] = useState("demo-project");
  const [timeRange, setTimeRange] = useState("24h");
  const [environment, setEnvironment] = useState("");
  const [service, setService] = useState("");

  const [summary, setSummary] = useState<AnalyticsSummaryResponse | null>(null);
  const [histogram, setHistogram] = useState<AnalyticsHistogramResponse | null>(
    null,
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const getTimeDates = (range: string) => {
    const end = new Date();
    const start = new Date();
    if (range === "1h") start.setHours(start.getHours() - 1);
    else if (range === "24h") start.setHours(start.getHours() - 24);
    else if (range === "7d") start.setDate(start.getDate() - 7);
    return { startTime: start.toISOString(), endTime: end.toISOString() };
  };

  const loadAnalytics = async () => {
    setLoading(true);
    setError(null);
    try {
      const { startTime, endTime } = getTimeDates(timeRange);
      const interval =
        timeRange === "1h" ? "5m" : timeRange === "24h" ? "1h" : "1d";

      const [sumRes, histRes] = await Promise.all([
        fetchAnalyticsSummary({
          projectId,
          startTime,
          endTime,
          environment: environment || undefined,
          service: service || undefined,
        }),
        fetchAnalyticsHistogram({
          projectId,
          startTime,
          endTime,
          interval,
          environment: environment || undefined,
          service: service || undefined,
        }),
      ]);

      setSummary(sumRes);
      setHistogram(histRes);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error loading analytics");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAnalytics();
  }, [projectId, timeRange, environment, service]);

  const maxBucketTotal = histogram?.buckets
    ? Math.max(...histogram.buckets.map((b) => b.total), 1)
    : 1;

  return (
    <div className="mx-auto max-w-7xl px-6 py-8 space-y-8">
      {/* Header */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Analytics & Insights
          </h1>
          <p className="text-sm text-slate-500">
            Log volume trends, error rate distributions, and top recurring
            exceptions.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="text"
            placeholder="Filter service..."
            value={service}
            onChange={(e) => setService(e.target.value)}
            className="w-36 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-800 focus:outline-none"
          />
          <input
            type="text"
            placeholder="Filter env..."
            value={environment}
            onChange={(e) => setEnvironment(e.target.value)}
            className="w-28 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-800 focus:outline-none"
          />
          {["1h", "24h", "7d"].map((range) => (
            <button
              key={range}
              onClick={() => setTimeRange(range)}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-all ${
                timeRange === range
                  ? "bg-sky-600 text-white shadow-xs"
                  : "bg-white text-slate-600 border border-slate-200 hover:bg-slate-50"
              }`}
            >
              {range === "1h"
                ? "Last Hour"
                : range === "24h"
                  ? "Last 24h"
                  : "Last 7 Days"}
            </button>
          ))}
        </div>
      </div>

      {loading && (
        <div className="text-xs text-sky-600 font-semibold animate-pulse">
          Updating analytics...
        </div>
      )}

      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 rounded-2xl text-rose-700 text-sm">
          {error}
        </div>
      )}

      {/* Summary Metric Cards */}
      <div className="grid gap-5 md:grid-cols-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs space-y-1">
          <span className="text-xs font-semibold text-slate-400">
            Total Ingested Logs
          </span>
          <div className="text-3xl font-extrabold text-slate-900">
            {summary ? summary.totalLogs.toLocaleString() : "-"}
          </div>
          <span className="text-[11px] text-slate-500">
            In selected time range
          </span>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs space-y-1">
          <span className="text-xs font-semibold text-slate-400">
            Error Rate
          </span>
          <div
            className={`text-3xl font-extrabold ${summary && summary.errorRatePercentage > 5 ? "text-rose-600" : "text-emerald-600"}`}
          >
            {summary ? `${summary.errorRatePercentage}%` : "-"}
          </div>
          <span className="text-[11px] text-slate-500">
            Errors & Warnings ratio
          </span>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs space-y-1">
          <span className="text-xs font-semibold text-slate-400">
            Active Services
          </span>
          <div className="text-3xl font-extrabold text-slate-900">
            {summary ? summary.topServices.length : "-"}
          </div>
          <span className="text-[11px] text-slate-500">Emitting events</span>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs space-y-1">
          <span className="text-xs font-semibold text-slate-400">
            Top Error Fingerprint
          </span>
          <div className="text-sm font-bold text-rose-700 truncate">
            {summary && summary.topErrors.length > 0
              ? summary.topErrors[0].errorFingerprint
              : "None"}
          </div>
          <span className="text-[11px] text-slate-500">
            {summary && summary.topErrors.length > 0
              ? `${summary.topErrors[0].count} occurrences`
              : "Clean"}
          </span>
        </div>
      </div>

      {/* Histogram Time-Series */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-slate-900">
            Log Volume Time-Series
          </h2>
          <span className="text-xs font-medium text-slate-400">
            Interval: {histogram?.interval || "1h"}
          </span>
        </div>

        {histogram && histogram.buckets.length > 0 ? (
          <div className="h-44 flex items-end gap-1.5 pt-6 pb-2 border-b border-slate-100">
            {histogram.buckets.map((bucket, idx) => {
              const heightPct = Math.max(
                (bucket.total / maxBucketTotal) * 100,
                4,
              );
              const errorPct = (bucket.errorCount / bucket.total) * 100;
              const warnPct = (bucket.warnCount / bucket.total) * 100;
              const infoPct = (bucket.infoCount / bucket.total) * 100;

              return (
                <div
                  key={idx}
                  className="flex-1 flex flex-col items-center group relative h-full justify-end"
                >
                  {/* Tooltip */}
                  <div className="absolute bottom-full mb-2 hidden group-hover:block bg-slate-900 text-white text-[10px] p-2 rounded-lg shadow-lg z-20 whitespace-nowrap">
                    <div>{new Date(bucket.timestamp).toLocaleTimeString()}</div>
                    <div className="text-rose-300 font-bold">
                      Errors: {bucket.errorCount}
                    </div>
                    <div className="text-amber-300">
                      Warns: {bucket.warnCount}
                    </div>
                    <div className="text-sky-300">Info: {bucket.infoCount}</div>
                    <div className="text-slate-300">Total: {bucket.total}</div>
                  </div>

                  {/* Stacked bar */}
                  <div
                    style={{ height: `${heightPct}%` }}
                    className="w-full rounded-t flex flex-col overflow-hidden bg-slate-200 transition-all hover:brightness-110"
                  >
                    {bucket.errorCount > 0 && (
                      <div
                        style={{ height: `${errorPct}%` }}
                        className="bg-rose-500 w-full"
                      />
                    )}
                    {bucket.warnCount > 0 && (
                      <div
                        style={{ height: `${warnPct}%` }}
                        className="bg-amber-400 w-full"
                      />
                    )}
                    {bucket.infoCount > 0 && (
                      <div
                        style={{ height: `${infoPct}%` }}
                        className="bg-sky-500 w-full"
                      />
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="h-32 flex items-center justify-center text-slate-400 text-xs">
            No histogram data available for current selection.
          </div>
        )}
      </div>

      {/* Breakdown Grids */}
      <div className="grid gap-6 md:grid-cols-2">
        {/* Top Error Fingerprints */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs space-y-4">
          <h2 className="text-base font-bold text-slate-900">
            Top Error Fingerprints
          </h2>
          <div className="space-y-3">
            {summary?.topErrors.length === 0 && (
              <div className="text-xs text-slate-400 py-4 text-center">
                No errors recorded.
              </div>
            )}
            {summary?.topErrors.map((err, idx) => (
              <div
                key={idx}
                className="p-3 bg-rose-50/50 rounded-xl border border-rose-100 space-y-1"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-rose-800 font-mono">
                    {err.errorFingerprint}
                  </span>
                  <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-rose-100 text-rose-700">
                    {err.count}
                  </span>
                </div>
                {err.sampleMessage && (
                  <p className="text-xs text-slate-600 truncate">
                    {err.sampleMessage}
                  </p>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Top Services Volume */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs space-y-4">
          <h2 className="text-base font-bold text-slate-900">
            Top Services Volume
          </h2>
          <div className="space-y-3">
            {summary?.topServices.length === 0 && (
              <div className="text-xs text-slate-400 py-4 text-center">
                No service data available.
              </div>
            )}
            {summary?.topServices.map((srv, idx) => {
              const pct =
                summary.totalLogs > 0
                  ? Math.round((srv.count / summary.totalLogs) * 100)
                  : 0;
              return (
                <div key={idx} className="space-y-1">
                  <div className="flex justify-between text-xs font-medium">
                    <span className="text-slate-800 font-bold">
                      {srv.service}
                    </span>
                    <span className="text-slate-500">
                      {srv.count.toLocaleString()} ({pct}%)
                    </span>
                  </div>
                  <div className="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
                    <div
                      style={{ width: `${pct}%` }}
                      className="h-full bg-sky-500 rounded-full"
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
