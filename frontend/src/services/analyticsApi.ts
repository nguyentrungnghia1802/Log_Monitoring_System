import type {
  AnalyticsSearchParams,
  AnalyticsSummaryResponse,
  AnalyticsHistogramResponse,
} from "../types/analytics";
import { apiRequest } from "./http";

export async function fetchAnalyticsSummary(
  params: AnalyticsSearchParams,
): Promise<AnalyticsSummaryResponse> {
  const query = new URLSearchParams();
  if (params.startTime) query.set("startTime", params.startTime);
  if (params.endTime) query.set("endTime", params.endTime);
  if (params.environment) query.set("environment", params.environment);
  if (params.service) query.set("service", params.service);

  return apiRequest<AnalyticsSummaryResponse>(
    `/projects/${params.projectId}/analytics/summary?${query.toString()}`,
  );
}

export async function fetchAnalyticsHistogram(
  params: AnalyticsSearchParams,
): Promise<AnalyticsHistogramResponse> {
  const query = new URLSearchParams();
  if (params.startTime) query.set("startTime", params.startTime);
  if (params.endTime) query.set("endTime", params.endTime);
  if (params.interval) query.set("interval", params.interval);
  if (params.environment) query.set("environment", params.environment);
  if (params.service) query.set("service", params.service);

  return apiRequest<AnalyticsHistogramResponse>(
    `/projects/${params.projectId}/analytics/histogram?${query.toString()}`,
  );
}
