export interface ServiceVolume {
  service: string;
  count: number;
}

export interface ErrorFingerprintCount {
  errorFingerprint: string;
  sampleMessage?: string;
  count: number;
}

export interface AnalyticsSummaryResponse {
  totalLogs: number;
  errorRatePercentage: number;
  countByLevel: Record<string, number>;
  topServices: ServiceVolume[];
  topErrors: ErrorFingerprintCount[];
}

export interface HistogramBucket {
  timestamp: string;
  total: number;
  errorCount: number;
  warnCount: number;
  infoCount: number;
  debugCount: number;
}

export interface AnalyticsHistogramResponse {
  interval: string;
  buckets: HistogramBucket[];
}

export interface AnalyticsSearchParams {
  projectId: string;
  startTime?: string;
  endTime?: string;
  interval?: string;
  environment?: string;
  service?: string;
}
