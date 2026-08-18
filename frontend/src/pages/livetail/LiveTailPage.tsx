import { useState, useEffect, useRef } from "react";
import { Client, type StompSubscription } from "@stomp/stompjs";
import type { LogEvent } from "../../types/log";
import { getAccessToken } from "../../auth/authToken";

export function LiveTailPage() {
  const [projectId] = useState("demo-project");
  const [connected, setConnected] = useState(false);
  const [connectionError, setConnectionError] = useState<string | null>(null);
  const [paused, setPaused] = useState(false);
  const [events, setEvents] = useState<LogEvent[]>([]);

  const [levelFilter, setLevelFilter] = useState("");
  const [serviceFilter, setServiceFilter] = useState("");
  const [keywordFilter, setKeywordFilter] = useState("");

  const pausedRef = useRef(paused);
  const bufferedEventsRef = useRef<LogEvent[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  useEffect(() => {
    const accessToken = getAccessToken();
    if (!accessToken) {
      setConnectionError(
        "Your session is not available. Sign in again before opening Live Tail.",
      );
      return;
    }

    const wsUrl =
      import.meta.env.VITE_WS_URL?.trim() ||
      `ws://${window.location.hostname}:8080/ws-logs`;
    const destination = `/user/queue/projects/${projectId}/livetail`;
    const subscriptionRef = {
      current: undefined as StompSubscription | undefined,
    };
    const client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setConnected(true);
        setConnectionError(null);
        let subscription: StompSubscription | undefined;
        subscription = client.subscribe(
          destination,
          (message) => {
            try {
              const event: LogEvent = JSON.parse(message.body);
              if (pausedRef.current) {
                bufferedEventsRef.current.push(event);
              } else {
                setEvents((prev) => [...prev.slice(-200), event]);
              }
            } catch (err) {
              console.error("Failed to parse STOMP message", err);
            }
          },
          {
            id: `live-tail-${projectId}`,
          },
        );
        subscriptionRef.current = subscription;
      },
      onDisconnect: () => {
        setConnected(false);
      },
      onWebSocketError: () => {
        setConnected(false);
        setConnectionError("Live Tail WebSocket connection failed.");
      },
      onStompError: () => {
        setConnected(false);
        setConnectionError(
          "Live Tail subscription was rejected by the server.",
        );
      },
    });

    client.activate();

    return () => {
      subscriptionRef.current?.unsubscribe();
      void client.deactivate();
    };
  }, [projectId]);

  useEffect(() => {
    if (!paused) {
      if (bufferedEventsRef.current.length > 0) {
        const bufferedEvents = bufferedEventsRef.current;
        setEvents((prev) => [...prev, ...bufferedEvents].slice(-200));
        bufferedEventsRef.current = [];
      }
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [paused, events]);

  const filteredEvents = events.filter((e) => {
    if (levelFilter && e.level.toUpperCase() !== levelFilter.toUpperCase())
      return false;
    if (
      serviceFilter &&
      !e.service.toLowerCase().includes(serviceFilter.toLowerCase())
    )
      return false;
    if (
      keywordFilter &&
      !e.message.toLowerCase().includes(keywordFilter.toLowerCase())
    )
      return false;
    return true;
  });

  const getLevelBadge = (lvl: string) => {
    switch (lvl.toUpperCase()) {
      case "ERROR":
        return "bg-rose-500 text-white font-bold";
      case "WARN":
        return "bg-amber-500 text-white font-bold";
      case "INFO":
        return "bg-sky-500 text-white font-bold";
      case "DEBUG":
        return "bg-slate-600 text-slate-200";
      default:
        return "bg-gray-600 text-white";
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-6 py-8 space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Live Tail Stream
          </h1>
          <span
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold border ${
              connected
                ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                : "bg-amber-50 text-amber-700 border-amber-200"
            }`}
          >
            <span
              className={`h-2 w-2 rounded-full ${connected ? "bg-emerald-500 animate-ping" : "bg-amber-500"}`}
            />
            {connected ? "LIVE STREAM" : "CONNECTING..."}
          </span>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setPaused(!paused)}
            className={`rounded-xl px-4 py-2 text-xs font-bold transition-all shadow-xs ${
              paused
                ? "bg-emerald-600 text-white hover:bg-emerald-700"
                : "bg-amber-500 text-white hover:bg-amber-600"
            }`}
          >
            {paused ? "▶ Resume Stream" : "⏸ Pause Stream"}
          </button>
          <button
            onClick={() => setEvents([])}
            className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 transition-all"
          >
            Clear Console
          </button>
        </div>
      </div>

      {connectionError && (
        <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          {connectionError}
        </p>
      )}

      {/* Stream Filters */}
      <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-xs flex flex-wrap items-center gap-3 text-xs">
        <div className="flex items-center gap-1.5">
          <span className="font-semibold text-slate-400">Level:</span>
          {["", "ERROR", "WARN", "INFO", "DEBUG"].map((lvl) => (
            <button
              key={lvl}
              onClick={() => setLevelFilter(lvl)}
              className={`rounded-md px-2.5 py-1 font-semibold border transition-all ${
                levelFilter === lvl
                  ? "bg-slate-900 text-white border-slate-900 shadow-xs"
                  : "bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100"
              }`}
            >
              {lvl || "ALL"}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2 ml-auto">
          <input
            type="text"
            placeholder="Filter service..."
            value={serviceFilter}
            onChange={(e) => setServiceFilter(e.target.value)}
            className="w-36 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 text-slate-800 placeholder-slate-400 focus:bg-white focus:outline-none"
          />
          <input
            type="text"
            placeholder="Search keyword..."
            value={keywordFilter}
            onChange={(e) => setKeywordFilter(e.target.value)}
            className="w-48 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 text-slate-800 placeholder-slate-400 focus:bg-white focus:outline-none"
          />
        </div>
      </div>

      {/* Live Terminal Console */}
      <div className="rounded-2xl border border-slate-900 bg-slate-950 p-4 font-mono text-xs shadow-xl overflow-hidden min-h-[500px] flex flex-col">
        <div className="flex items-center justify-between pb-3 border-b border-slate-800 text-slate-400 text-[11px]">
          <span>STREAM CONSOLE (MAX 200 EVENTS)</span>
          <span>EVENTS: {filteredEvents.length}</span>
        </div>

        <div className="flex-1 overflow-y-auto pt-3 space-y-1.5 max-h-[600px]">
          {filteredEvents.length === 0 && (
            <div className="text-slate-600 text-center py-20 font-sans">
              Waiting for incoming log events on /user/queue/projects/
              {projectId}/livetail...
            </div>
          )}
          {filteredEvents.map((evt, idx) => (
            <div
              key={idx}
              className="flex items-start gap-3 hover:bg-slate-900/60 p-1.5 rounded transition-colors"
            >
              <span className="text-slate-500 whitespace-nowrap">
                {new Date(evt.timestamp).toLocaleTimeString()}
              </span>
              <span
                className={`px-1.5 py-0.5 rounded text-[10px] whitespace-nowrap ${getLevelBadge(evt.level)}`}
              >
                {evt.level}
              </span>
              <span className="text-sky-400 font-semibold whitespace-nowrap">
                [{evt.service}]
              </span>
              <span className="text-slate-200 flex-1 break-all">
                {evt.message}
              </span>
              {evt.traceId && (
                <span className="text-slate-500 text-[10px] font-sans">
                  trace:{evt.traceId}
                </span>
              )}
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
      </div>
    </div>
  );
}
