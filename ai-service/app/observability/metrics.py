"""Prometheus counters exposed at /metrics — contract §11."""

from __future__ import annotations

from prometheus_client import Counter, Histogram

analysis_requests_total = Counter(
    "analysis_requests_total", "Total analysis requests", ["endpoint", "provider"]
)

analysis_validation_failures_total = Counter(
    "analysis_validation_failures_total", "Validation failures by stage", ["stage"]
)

analysis_latency_seconds = Histogram(
    "analysis_latency_seconds", "End-to-end analysis latency", ["endpoint", "provider"]
)

injection_suspected_total = Counter(
    "injection_suspected_total", "Requests flagged as a suspected prompt injection", ["endpoint"]
)
