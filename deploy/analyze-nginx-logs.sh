#!/usr/bin/env bash
set -euo pipefail

ACCESS_LOG="./logs/nginx/access.log"
ERROR_LOG=""
ERROR_TIMEZONE="+09:00"
LINES=1000
SINCE=""
SLOW_THRESHOLD=1.0
TOP_N=10
ALL=false

usage() {
    cat <<'USAGE'
Usage:
  ./analyze-nginx-logs.sh [options] [access-log]

Options:
  --access-log FILE   Nginx access log path. Default: ./logs/nginx/access.log
  --error-log FILE    Nginx error log path. Default: access log directory/error.log
  --error-timezone TZ Timezone for error log timestamps without offsets. Default: +09:00
  --lines N           Analyze only the latest N lines from each log. Default: 1000
  --since DURATION    Analyze entries newer than DURATION ago. Examples: 10m, 2h, 1d
  --slow SECONDS      Slow request threshold in seconds. Default: 1.0
  --top N             Number of top rows to print. Default: 10
  --all               Analyze whole log files
  -h, --help          Show this help

Examples:
  ./analyze-nginx-logs.sh
  ./analyze-nginx-logs.sh --since 15m
  ./analyze-nginx-logs.sh --lines 5000 --slow 0.5
  ./analyze-nginx-logs.sh --error-log ./logs/nginx/error.log
  ./analyze-nginx-logs.sh --error-timezone +00:00  # old UTC logs
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --access-log)
            ACCESS_LOG="${2:?--access-log requires a value}"
            shift 2
            ;;
        --error-log)
            ERROR_LOG="${2:?--error-log requires a value}"
            shift 2
            ;;
        --error-timezone)
            ERROR_TIMEZONE="${2:?--error-timezone requires a value}"
            shift 2
            ;;
        --lines)
            LINES="${2:?--lines requires a value}"
            shift 2
            ;;
        --since)
            SINCE="${2:?--since requires a value}"
            shift 2
            ;;
        --slow)
            SLOW_THRESHOLD="${2:?--slow requires a value}"
            shift 2
            ;;
        --top)
            TOP_N="${2:?--top requires a value}"
            shift 2
            ;;
        --all)
            ALL=true
            shift
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
        *)
            ACCESS_LOG="$1"
            shift
            ;;
    esac
done

if [[ -z "$ERROR_LOG" ]]; then
    ERROR_LOG="$(dirname "$ACCESS_LOG")/error.log"
fi

if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 is required." >&2
    exit 1
fi

if ! [[ "$LINES" =~ ^[0-9]+$ ]] || [[ "$LINES" -lt 1 ]]; then
    echo "--lines must be a positive integer." >&2
    exit 1
fi

if ! [[ "$TOP_N" =~ ^[0-9]+$ ]] || [[ "$TOP_N" -lt 1 ]]; then
    echo "--top must be a positive integer." >&2
    exit 1
fi

if ! [[ "$SLOW_THRESHOLD" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "--slow must be a number." >&2
    exit 1
fi

if [[ -n "$SINCE" && "$ALL" == true ]]; then
    echo "Use only one of --since or --all." >&2
    exit 1
fi

export ACCESS_LOG ERROR_LOG ERROR_TIMEZONE LINES SINCE SLOW_THRESHOLD TOP_N ALL

python3 - <<'PY'
from __future__ import annotations

from collections import Counter, deque
from datetime import datetime, timedelta, timezone
from math import ceil
from pathlib import Path
from urllib.parse import urlsplit
import os
import re
import sys


access_log = Path(os.environ["ACCESS_LOG"])
error_log = Path(os.environ["ERROR_LOG"])
error_timezone = os.environ["ERROR_TIMEZONE"].strip()
line_limit = int(os.environ["LINES"])
since = os.environ["SINCE"].strip()
slow_threshold = float(os.environ["SLOW_THRESHOLD"])
top_n = int(os.environ["TOP_N"])
read_all = os.environ["ALL"].lower() == "true"

ACCESS_RE = re.compile(
    r'^(?P<ip>\S+) - (?P<user>\S+) \[(?P<ts>[^\]]+)\] '
    r'"(?P<method>\S+) (?P<target>\S+) (?P<protocol>[^"]+)" '
    r'(?P<status>\d{3}) (?P<bytes>\d+) "(?P<referer>[^"]*)" '
    r'"(?P<user_agent>[^"]*)" "(?P<xff>[^"]*)" '
    r'rt=(?P<request_time>[-0-9.]+) '
    r'uct="(?P<upstream_connect_time>[^"]*)" '
    r'uht="(?P<upstream_header_time>[^"]*)" '
    r'urt="(?P<upstream_response_time>[^"]*)"'
)

ERROR_PREFIX_RE = re.compile(
    r"^(?P<ts>\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}) "
    r"\[(?P<level>\w+)\] (?P<body>.*)$"
)

SUSPICIOUS_TARGET_RE = re.compile(
    r"(?:"
    r"\.\./|%2e%2e|%252e%252e|"
    r"pearcmd|php://|data://|expect://|"
    r"<\?php|<\?echo|eval\(|base64_decode|"
    r"/(?:index|xmlrpc)\.php(?:\?|$)|"
    r"(^|/)(?:\.env|\.git|sdk/|wp-|wordpress|phpmyadmin|boaform|config|admin|vendor|cgi-bin)"
    r")",
    re.IGNORECASE,
)
SUSPICIOUS_USER_AGENT_RE = re.compile(
    r"(?:libredtail|sqlmap|nikto|acunetix|nessus|zgrab|masscan|nuclei|dirbuster|gobuster)",
    re.IGNORECASE,
)


def die(message: str) -> None:
    print(message, file=sys.stderr)
    sys.exit(1)


def parse_duration(value: str) -> timedelta:
    match = re.fullmatch(r"(\d+)([smhd])", value)
    if not match:
        die(f"Invalid --since value: {value}. Use examples like 10m, 2h, 1d.")
    amount = int(match.group(1))
    unit = match.group(2)
    if unit == "s":
        return timedelta(seconds=amount)
    if unit == "m":
        return timedelta(minutes=amount)
    if unit == "h":
        return timedelta(hours=amount)
    return timedelta(days=amount)


def parse_timezone(value: str) -> timezone:
    if value.upper() in {"UTC", "Z"}:
        return timezone.utc
    match = re.fullmatch(r"([+-])(\d{2}):?(\d{2})", value)
    if not match:
        die(f"Invalid --error-timezone value: {value}. Use examples like +09:00 or +00:00.")
    sign = 1 if match.group(1) == "+" else -1
    hours = int(match.group(2))
    minutes = int(match.group(3))
    if hours > 23 or minutes > 59:
        die(f"Invalid --error-timezone value: {value}.")
    return timezone(sign * timedelta(hours=hours, minutes=minutes))


error_tz = parse_timezone(error_timezone)
duration = parse_duration(since) if since else None
access_cutoff = datetime.now(timezone.utc) - duration if duration else None
error_cutoff = datetime.now(error_tz) - duration if duration else None


def scope_text() -> str:
    if since:
        return f"entries since {since} ago"
    if read_all:
        return "all lines"
    return f"latest {line_limit} lines"


def read_lines(path: Path) -> list[str]:
    if not path.exists():
        return []
    if read_all or since:
        return path.read_text(errors="replace").splitlines()
    with path.open(errors="replace") as file:
        return list(deque(file, maxlen=line_limit))


def parse_access_ts(raw: str) -> datetime | None:
    try:
        return datetime.strptime(raw, "%d/%b/%Y:%H:%M:%S %z")
    except ValueError:
        return None


def parse_error_ts(raw: str) -> datetime | None:
    try:
        return datetime.strptime(raw, "%Y/%m/%d %H:%M:%S").replace(tzinfo=error_tz)
    except ValueError:
        return None


def first_float(value: str) -> float | None:
    first = value.split(",")[0].strip()
    if not first or first == "-":
        return None
    try:
        return float(first)
    except ValueError:
        return None


def request_path(target: str) -> str:
    try:
        parsed = urlsplit(target)
        return parsed.path or "/"
    except ValueError:
        return target.split("?", 1)[0] or "/"


def normalize_path(target: str) -> str:
    path = request_path(target)
    path = re.sub(
        r"/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)",
        "/{uuid}",
        path,
        flags=re.IGNORECASE,
    )
    path = re.sub(r"/\d+(?=/|$)", "/{id}", path)
    return path


def percentile(values: list[float], ratio: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, ceil(len(ordered) * ratio) - 1))
    return ordered[index]


def print_section(title: str) -> None:
    print()
    print(title)
    print("-" * 44)


def print_counter(counter: Counter[str], empty: str = "No data.") -> None:
    if not counter:
        print(empty)
        return
    for value, count in counter.most_common(top_n):
        print(f"{count:8d} {value}")


def time_stats(values: list[float], label: str) -> None:
    if not values:
        print(f"No {label} data.")
        return
    print(f"count: {len(values)}")
    print(f"avg  : {sum(values) / len(values):.3f}s")
    print(f"min  : {min(values):.3f}s")
    print(f"p50  : {percentile(values, 0.50):.3f}s")
    print(f"p95  : {percentile(values, 0.95):.3f}s")
    print(f"p99  : {percentile(values, 0.99):.3f}s")
    print(f"max  : {max(values):.3f}s")


def extract_quoted_field(line: str, field: str) -> str:
    match = re.search(rf'{field}: "([^"]*)"', line)
    return match.group(1) if match else ""


def extract_simple_field(line: str, field: str) -> str:
    match = re.search(rf"{field}: ([^,]+)", line)
    return match.group(1).strip() if match else ""


def parse_error_request(request: str) -> tuple[str, str]:
    parts = request.split()
    if len(parts) >= 2:
        return parts[0], request_path(parts[1])
    return "", ""


def classify_error(line: str, method: str, path: str) -> str:
    path_lower = path.lower()
    if "limiting requests, excess:" in line:
        return "rate_limited"
    if path_lower == "/favicon.ico":
        return "favicon"
    if SUSPICIOUS_TARGET_RE.search(path_lower):
        return "bot_scan"
    if path == "/" and (method == "POST" or "index.html" in line):
        return "root_probe"
    if "no such file or directory" in line.lower() or " is not found " in line:
        return "missing_static"
    return "other"


def classify_access(row: dict[str, object]) -> str:
    target = str(row["target"])
    path = str(row["path"])
    user_agent = str(row["user_agent"])
    status = int(row["status"])

    if status == 429:
        return "rate_limited"
    if SUSPICIOUS_TARGET_RE.search(target) or SUSPICIOUS_USER_AGENT_RE.search(user_agent):
        return "bot_scan"
    if path == "/favicon.ico":
        return "favicon"
    if path == "/" and str(row["method"]) == "POST":
        return "root_probe"
    if 500 <= status:
        return "server_error"
    if 400 <= status:
        return "client_error"
    return "normal"


if not access_log.exists() and not error_log.exists():
    die(f"Neither access log nor error log exists: {access_log}, {error_log}")

access_rows: list[dict[str, object]] = []
access_parse_errors = 0
for raw_line in read_lines(access_log):
    line = raw_line.rstrip("\n")
    match = ACCESS_RE.match(line)
    if not match:
        access_parse_errors += 1
        continue

    values = match.groupdict()
    ts = parse_access_ts(values["ts"])
    if access_cutoff and ts and ts < access_cutoff:
        continue

    access_rows.append(
        {
            "ip": values["ip"],
            "timestamp": values["ts"],
            "method": values["method"],
            "target": values["target"],
            "path": request_path(values["target"]),
            "normalized_path": normalize_path(values["target"]),
            "status": int(values["status"]),
            "bytes": int(values["bytes"]),
            "request_time": first_float(values["request_time"]),
            "upstream_connect_time": first_float(values["upstream_connect_time"]),
            "upstream_header_time": first_float(values["upstream_header_time"]),
            "upstream_response_time": first_float(values["upstream_response_time"]),
            "user_agent": values["user_agent"],
        }
    )

error_rows: list[dict[str, object]] = []
error_parse_errors = 0
for raw_line in read_lines(error_log):
    line = raw_line.rstrip("\n")
    match = ERROR_PREFIX_RE.match(line)
    if not match:
        error_parse_errors += 1
        continue

    ts = parse_error_ts(match.group("ts"))
    if error_cutoff and ts and ts < error_cutoff:
        continue

    request = extract_quoted_field(line, "request")
    method, path = parse_error_request(request)
    category = classify_error(line, method, path)
    error_rows.append(
        {
            "timestamp": match.group("ts"),
            "level": match.group("level"),
            "ip": extract_simple_field(line, "client"),
            "host": extract_quoted_field(line, "host"),
            "request": request,
            "method": method,
            "path": path or "-",
            "category": category,
            "message": line,
        }
    )

status_counts = Counter(str(row["status"]) for row in access_rows)
path_counts = Counter(str(row["normalized_path"]) for row in access_rows)
api_path_counts = Counter(
    str(row["normalized_path"]) for row in access_rows if str(row["normalized_path"]).startswith("/api/")
)
ip_counts = Counter(str(row["ip"]) for row in access_rows)
request_times = [row["request_time"] for row in access_rows if isinstance(row["request_time"], float)]
upstream_response_times = [
    row["upstream_response_time"] for row in access_rows if isinstance(row["upstream_response_time"], float)
]
slow_rows = sorted(
    [row for row in access_rows if isinstance(row["request_time"], float) and row["request_time"] >= slow_threshold],
    key=lambda row: float(row["request_time"]),
    reverse=True,
)
recent_error_status_rows = [row for row in access_rows if int(row["status"]) >= 400][-top_n:]
access_category_counts = Counter(classify_access(row) for row in access_rows)
access_bot_rows = [row for row in access_rows if classify_access(row) in {"bot_scan", "root_probe", "rate_limited"}]
access_bot_ip_counts = Counter(str(row["ip"]) for row in access_bot_rows)
access_bot_path_counts = Counter(str(row["normalized_path"]) for row in access_bot_rows)
access_bot_user_agent_counts = Counter(str(row["user_agent"]) for row in access_bot_rows)
recent_access_bot_rows = access_bot_rows[-top_n:]

error_category_counts = Counter(str(row["category"]) for row in error_rows)
error_path_counts = Counter(str(row["path"]) for row in error_rows if str(row["path"]) != "-")
error_ip_counts = Counter(str(row["ip"]) for row in error_rows if str(row["ip"]))
recent_bot_rows = [
    row for row in error_rows if row["category"] in {"bot_scan", "root_probe", "rate_limited"}
][-top_n:]

print("=" * 44)
print("Nginx log report")
print(f"Scope     : {scope_text()}")
print(f"Access log: {access_log}")
print(f"Error log : {error_log}")
print(f"Access    : {len(access_rows)} parsed")
if access_parse_errors:
    print(f"            {access_parse_errors} skipped")
print(f"Errors    : {len(error_rows)} parsed")
if error_parse_errors:
    print(f"            {error_parse_errors} skipped")
print("=" * 44)

if access_log.exists():
    print_section("HTTP status")
    print_counter(status_counts)

    print_section("Top paths")
    print_counter(path_counts)

    print_section("Top API paths")
    print_counter(api_path_counts, "No API requests.")

    print_section("Top IPs")
    print_counter(ip_counts)

    print_section("Access categories")
    print_counter(access_category_counts)

    print_section("Top access scanner IPs")
    print_counter(access_bot_ip_counts, "No scanner signals.")

    print_section("Top access scanner paths")
    print_counter(access_bot_path_counts, "No scanner paths.")

    print_section("Top access scanner user agents")
    print_counter(access_bot_user_agent_counts, "No scanner user agents.")

    print_section("Request time")
    time_stats(request_times, "request time")

    print_section("Upstream response time")
    time_stats(upstream_response_times, "upstream response time")

    print_section(f"Slow requests >= {slow_threshold:.3f}s")
    if not slow_rows:
        print("No slow requests.")
    for row in slow_rows[:top_n]:
        upstream = row["upstream_response_time"]
        upstream_text = "-" if upstream is None else f"{float(upstream):.3f}s"
        print(
            f"{float(row['request_time']):7.3f}s "
            f"status={row['status']} upstream={upstream_text} "
            f"{row['method']} {row['target']} [{row['timestamp']}] ip={row['ip']}"
        )

    print_section("Recent access errors")
    if not recent_error_status_rows:
        print("No 4xx/5xx access log entries.")
    for row in recent_error_status_rows:
        print(
            f"status={row['status']} {row['method']} {row['target']} "
            f"[{row['timestamp']}] ip={row['ip']} ua={row['user_agent']}"
        )

    print_section("Recent access scanner signals")
    if not recent_access_bot_rows:
        print("No recent scanner signals.")
    for row in recent_access_bot_rows:
        print(
            f"status={row['status']} {row['method']} {row['target']} "
            f"[{row['timestamp']}] ip={row['ip']} ua={row['user_agent']}"
        )
else:
    print_section("Access log")
    print(f"Not found: {access_log}")

if error_log.exists():
    print_section("Error categories")
    print_counter(error_category_counts, "No error log entries.")

    print_section("Top error paths")
    print_counter(error_path_counts, "No error paths.")

    print_section("Top error IPs")
    print_counter(error_ip_counts, "No error IPs.")

    print_section("Recent bot/rate-limit signals")
    if not recent_bot_rows:
        print("No recent bot or rate-limit signals.")
    for row in recent_bot_rows:
        print(
            f"{row['timestamp']} {row['category']} ip={row['ip']} "
            f"host={row['host']} request=\"{row['request']}\""
        )
else:
    print_section("Error log")
    print(f"Not found: {error_log}")

print()
print("Done.")
PY
