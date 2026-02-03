package com.trading.report;

import com.trading.config.BenchmarkConfig;
import com.trading.config.BenchmarkNotes;
import com.trading.core.BenchmarkResult;
import com.trading.core.BenchmarkRunner;

public final class HtmlReportBuilder {
    public static String render(String timestamp, BenchmarkResult standard, BenchmarkResult zeroGc) {
        String template = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>Low Latency Router Benchmark</title>
                  <style>
                    :root {
                      --bg: #0b0f14;
                      --panel: #121821;
                      --panel-2: #161f2b;
                      --text: #e6edf3;
                      --muted: #94a3b8;
                      --accent: #4ade80;
                      --accent-2: #60a5fa;
                      --border: #223043;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: radial-gradient(1200px 600px at 80% -10%, #1b2a3f 0%, transparent 60%),
                                  radial-gradient(900px 500px at -10% 20%, #1c3a2b 0%, transparent 55%),
                                  var(--bg);
                      color: var(--text);
                      font: 15px/1.5 "Segoe UI", "Helvetica Neue", Arial, sans-serif;
                      padding: 32px;
                    }
                    header {
                      display: flex;
                      flex-direction: column;
                      gap: 6px;
                      margin-bottom: 24px;
                    }
                    h1 {
                      font-size: 26px;
                      margin: 0;
                      letter-spacing: 0.2px;
                    }
                    .sub {
                      color: var(--muted);
                      font-size: 13px;
                    }
                    .grid {
                      display: grid;
                      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
                      gap: 16px;
                      margin-bottom: 20px;
                    }
                    .card {
                      background: linear-gradient(180deg, var(--panel), var(--panel-2));
                      border: 1px solid var(--border);
                      border-radius: 12px;
                      padding: 16px;
                      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
                    }
                    .card h2 {
                      font-size: 16px;
                      margin: 0 0 10px 0;
                    }
                    .pill {
                      display: inline-block;
                      padding: 2px 8px;
                      border-radius: 999px;
                      font-size: 12px;
                      background: rgba(96, 165, 250, 0.2);
                      color: #c7d2fe;
                      border: 1px solid rgba(96, 165, 250, 0.3);
                    }
                    .metrics {
                      display: grid;
                      grid-template-columns: 1fr 1fr;
                      gap: 10px;
                      margin-top: 12px;
                    }
                    .metric {
                      background: rgba(255, 255, 255, 0.03);
                      border: 1px solid var(--border);
                      border-radius: 10px;
                      padding: 10px;
                    }
                    .metric .label {
                      color: var(--muted);
                      font-size: 12px;
                    }
                    .metric .value {
                      font-size: 18px;
                      font-weight: 600;
                    }
                    .compare {
                      border: 1px solid var(--border);
                      border-radius: 12px;
                      overflow: hidden;
                      margin-bottom: 20px;
                    }
                    table {
                      width: 100%;
                      border-collapse: collapse;
                    }
                    thead th {
                      text-align: left;
                      background: #0f1722;
                      color: var(--muted);
                      font-weight: 600;
                      font-size: 12px;
                      padding: 10px 12px;
                      border-bottom: 1px solid var(--border);
                    }
                    tbody td {
                      padding: 12px;
                      border-bottom: 1px solid var(--border);
                      font-size: 14px;
                    }
                    tbody tr:last-child td { border-bottom: 0; }
                    .good { color: var(--accent); }
                    .info { color: var(--accent-2); }
                    .notes {
                      background: linear-gradient(180deg, var(--panel), var(--panel-2));
                      border: 1px solid var(--border);
                      border-radius: 12px;
                      padding: 16px;
                    }
                    .notes h3 {
                      margin: 0 0 8px 0;
                      font-size: 15px;
                    }
                    .notes ul {
                      margin: 0;
                      padding-left: 18px;
                      color: var(--muted);
                      font-size: 13px;
                    }
                    footer {
                      color: var(--muted);
                      font-size: 12px;
                      margin-top: 18px;
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <h1>Low Latency Router Benchmark</h1>
                    <div class="sub">Generated: __TS__</div>
                  </header>

                  <section class="grid">
                    __CARD_STANDARD__
                    __CARD_ZEROGC__
                  </section>

                  <section class="compare">
                    <table>
                      <thead>
                        <tr>
                          <th>Mode</th>
                          <th>Average (ms)</th>
                          <th>Median (ms)</th>
                          <th>P95 (ms)</th>
                          <th>Max (ms)</th>
                          <th>Allocations</th>
                          <th>Throughput</th>
                        </tr>
                      </thead>
                      <tbody>
                        __ROW_STANDARD__
                        __ROW_ZEROGC__
                      </tbody>
                    </table>
                  </section>

                  <section class="notes">
                    <h3>Notes</h3>
                    __NOTES__
                  </section>

                  <footer>Workload: __ORDERS__ orders per round - __ROUNDS__ rounds</footer>
                </body>
                </html>
                """;

        return template
                .replace("__TS__", timestamp)
                .replace("__CARD_STANDARD__", renderCard("Standard", standard, "Default GC"))
                .replace("__CARD_ZEROGC__", renderCard("ZeroGC", zeroGc, "ZGC, reuse-focused"))
                .replace("__ROW_STANDARD__", renderRow("Standard", standard, "good"))
                .replace("__ROW_ZEROGC__", renderRow("ZeroGC", zeroGc, "info"))
                .replace("__NOTES__", renderNotes())
                .replace("__ORDERS__", Integer.toString(BenchmarkConfig.ORDERS_PER_ROUND))
                .replace("__ROUNDS__", Integer.toString(BenchmarkConfig.MEASURE_ROUNDS));
    }

    private static String renderCard(String title, BenchmarkResult result, String subtitle) {
        return """
                <div class="card">
                  <span class="pill">%s</span>
                  <h2>%s</h2>
                  <div class="metrics">
                    %s
                    %s
                    %s
                    %s
                  </div>
                </div>
                """.formatted(
                subtitle,
                title,
                metric("Average (ms)", BenchmarkRunner.formatMillis(result.averageMs())),
                metric("Median (ms)", BenchmarkRunner.formatMillis(result.medianMs())),
                metric("P95 (ms)", BenchmarkRunner.formatMillis(result.p95Ms())),
                metric("Allocations", Integer.toString(result.averageAllocations())));
    }

    private static String metric(String label, String value) {
        return """
                <div class="metric">
                  <div class="label">%s</div>
                  <div class="value">%s</div>
                </div>
                """.formatted(label, value);
    }

    private static String renderRow(String title, BenchmarkResult result, String cls) {
        return """
                <tr>
                  <td>%s</td>
                  <td class="%s">%s</td>
                  <td>%s</td>
                  <td>%s</td>
                  <td>%s</td>
                  <td>%d</td>
                  <td>%.0f /s</td>
                </tr>
                """.formatted(
                title,
                cls,
                BenchmarkRunner.formatMillis(result.averageMs()),
                BenchmarkRunner.formatMillis(result.medianMs()),
                BenchmarkRunner.formatMillis(result.p95Ms()),
                BenchmarkRunner.formatMillis(result.maxMs()),
                result.averageAllocations(),
                result.throughputPerSecond());
    }

    private static String renderNotes() {
        StringBuilder builder = new StringBuilder();
        builder.append("<ul>");
        for (String note : BenchmarkNotes.NOTES) {
            builder.append("<li>").append(note).append("</li>");
        }
        builder.append("</ul>");
        return builder.toString();
    }
}
