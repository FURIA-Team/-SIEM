(() => {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const state = {
    authenticated: false,
    user: null,
    csrf: null,
    registerMode: false,
    bootstrapMode: false,
    page: "dashboard",
    dashboardPage: 0,
    dashboardSize: 10,
    eventPage: 0,
    eventSize: 25,
    alertPage: 0,
    alertSize: 10,
    userPage: 0,
    userSize: 10,
    logPage: 0,
    logSize: 25,
    eventQuery: "",
    eventHours: 0,
    userQuery: "",
    severity: "",
    hours: 24,
    chartRange: "24h",
    alertStatus: "",
    alertSeverity: "",
    requestSequence: 0
  };

  const toast = $("#toast");
  let toastTimer;
  let searchTimer;

  function showToast(message, error = false) {
    toast.textContent = message;
    toast.classList.toggle("error", error);
    toast.classList.add("visible");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove("visible"), 3200);
  }

  async function getCsrfToken() {
    if (state.csrf) return state.csrf;
    const response = await fetch("/api/auth/csrf", { credentials: "same-origin" });
    if (!response.ok) throw new Error("Unable to initialize the security token.");
    const token = await response.json();
    state.csrf = token.token;
    return state.csrf;
  }

  async function api(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    if (options.body !== undefined) headers.set("Content-Type", "application/json");
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
      headers.set("X-XSRF-TOKEN", await getCsrfToken());
    }

    const response = await fetch(path, {
      ...options,
      method,
      headers,
      credentials: "same-origin"
    });
    if (response.status === 401 && state.authenticated) {
      state.authenticated = false;
      state.user = null;
      showAuth();
      setAuthMode(false);
    }
    if (!response.ok) {
      let message = `Request failed (${response.status})`;
      try {
        const body = await response.json();
        message = body.message || body.error || message;
      } catch {
        if (response.statusText) message = response.statusText;
      }
      const error = new Error(message);
      error.status = response.status;
      throw error;
    }
    if (response.status === 204) return null;
    return response;
  }

  async function getJson(path) {
    return (await api(path)).json();
  }

  async function sendJson(path, method, data) {
    const response = await api(path, { method, body: JSON.stringify(data) });
    return response ? response.json() : null;
  }

  function encodeQuery(values) {
    const params = new URLSearchParams();
    Object.entries(values).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== "") params.set(key, value);
    });
    return params.toString();
  }

  function formattedDate(value, includeDate = false) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat(undefined, includeDate
      ? { dateStyle: "medium", timeStyle: "short" }
      : { hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(date);
  }

  function relativeDate(value) {
    if (!value) return "—";
    const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000));
    if (minutes < 1) return "just now";
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    return `${Math.floor(hours / 24)}d ago`;
  }

  function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== null && text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
  }

  function setText(selector, value) {
    const node = $(selector);
    if (node) node.textContent = value;
  }

  function showAuth() {
    $("#authScreen").hidden = false;
    $(".app").hidden = true;
    $("#logoutButton").hidden = true;
  }

  function showApp() {
    $("#authScreen").hidden = true;
    $(".app").hidden = false;
    $("#logoutButton").hidden = false;
    const username = state.user?.username || "User";
    $(".profile-name").textContent = username;
    $(".profile-role").textContent = state.user?.role === "ADMIN" ? "Administrator" : "Security analyst";
    $(".avatar").textContent = username.slice(0, 2).toUpperCase();
  }

  function routeFromHash() {
    const match = location.hash.match(/^#\/(dashboard|events|alerts|users|logs|settings)$/);
    return match ? match[1] : "dashboard";
  }

  function navigate(page) {
    const hash = `#/${page}`;
    if (location.hash === hash) renderPage(page);
    else location.hash = hash;
  }

  function setRouteShell(page, title, description) {
    const dashboard = page === "dashboard";
    $("#dashboardView").hidden = !dashboard;
    $("#routeView").hidden = dashboard;
    $$(".nav-link[data-page]").forEach((link) => {
      const active = link.dataset.page === page;
      link.classList.toggle("active", active);
      if (active) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
    });
    $(".breadcrumbs strong").textContent = title;
    $("#routeTitle").textContent = title;
    $("#routeSubtitle").textContent = description;
    $("#routeToolbar").replaceChildren();
    $("#routePanel").replaceChildren();
    $("#routeActions").replaceChildren();
    $("#eventSearch").placeholder = page === "users" ? "Search users..." : "Search events...";
    $("#eventSearch").value = page === "users" ? state.userQuery : state.eventQuery;
    $("#clearSearch").classList.toggle("visible", Boolean($("#eventSearch").value));
  }

  function tableHeader(labels) {
    const table = element("table", null, "route-table");
    const head = element("thead");
    const row = element("tr");
    labels.forEach((label) => row.append(element("th", label)));
    head.append(row);
    table.append(head, element("tbody"));
    return table;
  }

  function createPager(container, result, currentPage, onPage) {
    container.replaceChildren();
    const totalPages = result.totalPages || 0;
    const previous = element("button", "‹", "page-button");
    previous.type = "button";
    previous.setAttribute("aria-label", "Previous page");
    previous.disabled = currentPage <= 0;
    previous.addEventListener("click", () => onPage(currentPage - 1));
    container.append(previous);

    const lastPage = Math.max(0, totalPages - 1);
    const first = Math.max(0, Math.min(currentPage - 2, lastPage - 4));
    const last = Math.min(lastPage, first + 4);
    if (first > 0) {
      addPageButton(container, 0, currentPage, onPage);
      if (first > 1) container.append(element("span", "…"));
    }
    for (let page = first; page <= last && totalPages > 0; page++) {
      addPageButton(container, page, currentPage, onPage);
    }
    if (last < lastPage) {
      if (last < lastPage - 1) container.append(element("span", "…"));
      addPageButton(container, lastPage, currentPage, onPage);
    }
    const next = element("button", "›", "page-button");
    next.type = "button";
    next.setAttribute("aria-label", "Next page");
    next.disabled = currentPage >= lastPage;
    next.addEventListener("click", () => onPage(currentPage + 1));
    container.append(next);
  }

  function addPageButton(container, page, currentPage, onPage) {
    const button = element("button", String(page + 1), `page-button${page === currentPage ? " current" : ""}`);
    button.type = "button";
    if (page === currentPage) button.setAttribute("aria-current", "page");
    button.addEventListener("click", () => onPage(page));
    container.append(button);
  }

  function rangeText(result) {
    if (!result.totalElements) return "Showing 0 of 0";
    const start = result.page * result.size + 1;
    const end = Math.min(result.totalElements, start + result.content.length - 1);
    return `Showing ${start}–${end} of ${result.totalElements}`;
  }

  function severityPill(value) {
    const severity = (value || "LOW").toLowerCase();
    return element("span", severity[0].toUpperCase() + severity.slice(1), `severity-pill ${severity}`);
  }

  function renderEventRows(target, events) {
    target.replaceChildren();
    if (!events.length) {
      const row = element("tr");
      const cell = element("td", "No events match these filters.", "route-empty");
      cell.colSpan = 6;
      row.append(cell);
      target.append(row);
      return;
    }
    events.forEach((event) => {
      const row = element("tr");
      row.append(element("td", formattedDate(event.occurredAt), "time-cell"));
      const name = element("td");
      const title = element("span", event.title, "event-title");
      const icon = element("span", "!", `event-icon ${["CRITICAL", "HIGH"].includes(event.severity) ? "" : "info"}`);
      title.prepend(icon);
      name.append(title);
      row.append(name);
      const level = element("td");
      level.append(severityPill(event.severity));
      row.append(level, element("td", event.source, "source-cell"), element("td", event.destination));
      const status = (event.status || "OPEN").toLowerCase();
      row.append(element("td", event.status || "Open", `trend ${status === "resolved" || status === "blocked" ? "up" : "neutral"}`));
      target.append(row);
    });
  }

  function updateCount(node, result) {
    node.textContent = rangeText(result);
  }

  async function loadEventTable({ page, size, query, severity, hours, target, count, pager, setPage }) {
    const requestId = ++state.requestSequence;
    try {
      const params = encodeQuery({ page, size, query, severity, hours });
      const data = await getJson(`/api/user/events?${params}`);
      if (requestId !== state.requestSequence) return data;
      renderEventRows(target, data.content);
      updateCount(count, data);
      createPager(pager, data, page, setPage);
      return data;
    } catch (error) {
      if (error.status === 403) {
        renderTableError(target, "Administrator access is required to view this page.");
        return null;
      }
      if (error.status !== 401) showToast(error.message, true);
      return null;
    }
  }

  function renderTableError(target, message) {
    target.replaceChildren();
    const row = element("tr");
    const cell = element("td", message);
    cell.colSpan = 8;
    cell.className = "route-empty";
    row.append(cell);
    target.append(row);
  }

  function createDashboardFilterSelects() {
    $("#dashboardSeverity").value = state.severity;
    $("#dashboardHours").value = String(state.hours);
  }

  async function loadDashboard() {
    if (!state.authenticated || state.page !== "dashboard") return;
    createDashboardFilterSelects();
    const range = state.chartRange;
    try {
      const dashboard = await getJson(`/api/user/dashboard?range=${encodeURIComponent(range)}`);
      if (state.page !== "dashboard" || range !== state.chartRange) return;
      $("#metricEvents").textContent = Number(dashboard.securityEvents24h).toLocaleString();
      $("#metricThreats").textContent = Number(dashboard.criticalThreats).toLocaleString();
      $("#metricUsers").textContent = Number(dashboard.activeUsers).toLocaleString();
      $("#metricOpen").textContent = Number(dashboard.openEvents).toLocaleString();
      $("#workspaceName").textContent = dashboard.workspaceName || "MiniSIEM";
      $("#systemStatus").textContent = "Security services connected";
      $("#monitoringStatus").textContent = "Database-backed monitoring";
      renderChart(dashboard.activity || []);
      renderPriorityAlerts(dashboard.priorityAlerts || []);
      const alertCount = Number(dashboard.unresolvedAlerts) || 0;
      const badge = $('.nav-link[data-page="alerts"] .nav-count');
      badge.textContent = String(alertCount);
      badge.hidden = alertCount === 0;
      setText("#lastUpdated", new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(new Date()));
    } catch (error) {
      if (error.status !== 401) showToast(error.message, true);
    }
    await loadEventTable({
      page: state.dashboardPage,
      size: state.dashboardSize,
      query: state.eventQuery,
      severity: state.severity,
      hours: state.hours,
      target: $("#eventsBody"),
      count: $("#eventCount"),
      pager: $("#dashboardPagination"),
      setPage: (page) => {
        state.dashboardPage = page;
        loadDashboard();
      }
    });
  }

  function renderChart(points) {
    const svg = $("#activityChart");
    const values = points.flatMap((point) => [Number(point.events) || 0, Number(point.threats) || 0]);
    const max = Math.max(1, ...values);
    const top = Math.ceil(max / 4) * 4;
    const coordinate = (value) => 164 - ((Number(value) || 0) / top) * 140;
    const makePath = (valuesForLine) => {
      if (!points.length || valuesForLine.every((value) => !value)) return "";
      const coords = valuesForLine.map((value, index) => ({
        x: 48 + (642 * index) / Math.max(1, valuesForLine.length - 1),
        y: coordinate(value)
      }));
      return `M${coords.map((point) => `${point.x} ${point.y}`).join(" L")}`;
    };
    const events = points.map((point) => Number(point.events) || 0);
    const threats = points.map((point) => Number(point.threats) || 0);
    const eventPath = makePath(events);
    const threatPath = makePath(threats);
    $("#eventsLine").setAttribute("d", eventPath);
    $("#threatsLine").setAttribute("d", threatPath);
    $("#eventsArea").setAttribute("d", eventPath ? `${eventPath} L690 164 L48 164 Z` : "");
    $("#threatsArea").setAttribute("d", threatPath ? `${threatPath} L690 164 L48 164 Z` : "");
    const axisLabels = $$("#activityChart g text").slice(-5);
    [0, 3, 6, 9, 11].forEach((pointIndex, labelIndex) => {
      axisLabels[labelIndex].textContent = points[pointIndex]?.label || "—";
    });
    const yLabels = $$("#activityChart g text").slice(0, 5);
    const steps = [top, Math.round(top * .75), Math.round(top * .5), Math.round(top * .25), 0];
    steps.forEach((value, index) => { yLabels[index].textContent = value.toLocaleString(); });
    const circles = $("#chartPoints");
    circles.replaceChildren();
    points.forEach((point, index) => {
      if (!point.events) return;
      const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
      circle.setAttribute("cx", String(48 + (642 * index) / Math.max(1, points.length - 1)));
      circle.setAttribute("cy", String(coordinate(point.events)));
      circle.setAttribute("r", "3");
      circle.dataset.value = `${Number(point.events).toLocaleString()} events · ${Number(point.threats).toLocaleString()} threats`;
      circle.addEventListener("mouseenter", () => {
        const tooltip = $("#chartTooltip");
        tooltip.textContent = `${point.label}: ${circle.dataset.value}`;
        tooltip.style.display = "block";
        const bounds = svg.getBoundingClientRect();
        tooltip.style.left = `${Math.max(8, Math.min(Number(circle.getAttribute("cx")) / 700 * bounds.width, bounds.width - 165))}px`;
        tooltip.style.top = `${Math.max(0, Number(circle.getAttribute("cy")) / 190 * bounds.height - 38)}px`;
      });
      circle.addEventListener("mouseleave", () => { $("#chartTooltip").style.display = "none"; });
      circles.append(circle);
    });
    const peak = points.reduce((best, point) => Number(point.events) > Number(best?.events || 0) ? point : best, null);
    $("#chartPeak").textContent = peak
      ? `${Number(peak.events).toLocaleString()} events at ${peak.label}`
      : "0 events";
    svg.setAttribute("aria-label", `Security events and threats for the last ${state.chartRange}`);
  }

  function renderPriorityAlerts(alerts) {
    const list = $("#priorityAlerts");
    list.replaceChildren();
    if (!alerts.length) {
      list.append(element("div", "No priority alerts recorded.", "route-empty"));
      return;
    }
    alerts.forEach((alert) => {
      const row = element("button", null, "alert-row");
      row.type = "button";
      row.style.width = "100%";
      row.style.border = "0";
      row.style.borderBottom = "1px solid rgba(32, 43, 56, .75)";
      row.style.background = "transparent";
      row.style.textAlign = "left";
      const mark = element("span", null, `severity-mark ${alert.severity === "MEDIUM" ? "medium" : alert.severity === "LOW" ? "low" : ""}`);
      const copy = element("span", null, "alert-copy");
      copy.append(element("span", alert.title, "alert-name"));
      copy.append(element("span", `${alert.severity} · ${alert.status}`, "alert-meta"));
      row.append(mark, copy, element("span", relativeDate(alert.detectedAt), "alert-time"));
      row.addEventListener("click", () => navigate("alerts"));
      list.append(row);
    });
  }

  function eventToolbar(includeSearch = false) {
    const group = element("div", null, "route-toolbar-group");
    if (includeSearch) {
      const query = element("input", null, "form-control");
      query.type = "search";
      query.placeholder = "Search title, source, IP…";
      query.value = state.eventQuery;
      query.setAttribute("aria-label", "Search events");
      query.addEventListener("input", () => {
        state.eventQuery = query.value.trim();
        $("#eventSearch").value = state.eventQuery;
        state.eventPage = 0;
        debounceRouteLoad();
      });
      group.append(query);
    }
    const severity = createSelect([
      ["", "All severities"], ["CRITICAL", "Critical"], ["HIGH", "High"],
      ["MEDIUM", "Medium"], ["LOW", "Low"]
    ], state.severity, "Filter severity");
    severity.addEventListener("change", () => {
      state.severity = severity.value;
      state.eventPage = 0;
      loadCurrentEvents();
    });
    const hours = createSelect([
      ["0", "All time"], ["1", "Last hour"], ["6", "Last 6 hours"], ["24", "Last 24 hours"], ["168", "Last 7 days"]
    ], String(state.eventHours), "Filter time range");
    hours.addEventListener("change", () => {
      state.eventHours = Number(hours.value);
      state.eventPage = 0;
      loadCurrentEvents();
    });
    group.append(severity, hours);
    return group;
  }

  function createSelect(options, selected, label) {
    const select = element("select", null, "filter-select");
    select.setAttribute("aria-label", label);
    options.forEach(([value, name]) => {
      const option = element("option", name);
      option.value = value;
      option.selected = value === selected;
      select.append(option);
    });
    return select;
  }

  function createPageSizeSelect(size, onChange) {
    const label = element("label", "Rows", "page-size-control");
    const select = element("select", null, "page-size-select");
    select.setAttribute("aria-label", "Rows per page");
    [10, 25, 50, 100].forEach((value) => {
      const option = element("option", String(value));
      option.value = String(value);
      option.selected = value === size;
      select.append(option);
    });
    select.addEventListener("change", () => onChange(Number(select.value)));
    label.append(select);
    return label;
  }

  function createPageTable(labels, id, footerId, pagerId, size, onSizeChange) {
    const table = tableHeader(labels);
    const wrapper = element("div", null, "table-scroll");
    wrapper.append(table);
    const footer = element("footer", null, "table-footer");
    const left = element("div", null, "table-footer-left");
    left.append(element("span", "Loading…", null));
    left.lastChild.id = footerId;
    left.append(createPageSizeSelect(size, onSizeChange));
    const pager = element("div", null, "pagination");
    pager.id = pagerId;
    footer.append(left, pager);
    $("#routePanel").replaceChildren(wrapper, footer);
    return { body: $("tbody", table), count: $(`#${footerId}`), pager: $(`#${pagerId}`) };
  }

  function debounceRouteLoad() {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      if (state.page === "events") loadCurrentEvents();
      else if (state.page === "alerts") loadAlerts();
      else if (state.page === "logs") loadLogs();
    }, 250);
  }

  function loadCurrentEvents() {
    const target = $("#routePanel tbody");
    if (!target) return;
    loadEventTable({
      page: state.eventPage,
      size: state.eventSize,
      query: state.eventQuery,
      severity: state.severity,
      hours: state.eventHours,
      target,
      count: $("#eventPageCount"),
      pager: $("#eventPager"),
      setPage: (page) => { state.eventPage = page; loadCurrentEvents(); }
    });
  }

  function exportButton() {
    const button = element("button", "Export CSV", "action-button");
    button.type = "button";
    button.addEventListener("click", exportEvents);
    return button;
  }

  async function exportEvents() {
    const query = state.eventQuery;
    const hours = state.page === "events" ? state.eventHours : state.hours;
    const params = encodeQuery({ query, severity: state.severity, hours });
    try {
      const response = await api(`/api/user/events/export?${params}`, {
        headers: { Accept: "text/csv" }
      });
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = element("a");
      anchor.href = url;
      anchor.download = `minisiem-security-events-${new Date().toISOString().slice(0, 10)}.csv`;
      anchor.style.position = "fixed";
      anchor.style.left = "-10000px";
      document.body.append(anchor);
      anchor.click();
      setTimeout(() => {
        URL.revokeObjectURL(url);
        anchor.remove();
      }, 1000);
      showToast("Filtered security events exported");
    } catch (error) {
      showToast(error.message, true);
    }
  }

  async function loadAlerts() {
    const params = encodeQuery({
      query: state.eventQuery,
      severity: state.alertSeverity,
      status: state.alertStatus,
      page: state.alertPage,
      size: state.alertSize
    });
    try {
      const result = await getJson(`/api/user/alerts?${params}`);
      const body = $("#routePanel tbody");
      body.replaceChildren();
      if (!result.content.length) {
        renderTableError(body, "No alerts match these filters.");
      } else {
        result.content.forEach((alert) => {
          const row = element("tr");
          row.append(element("td", formattedDate(alert.detectedAt, true)));
          row.append(element("td", alert.title));
          const severity = element("td");
          severity.append(severityPill(alert.severity));
          row.append(severity, element("td", alert.source, "source-cell"));
          const statusCell = element("td");
          const status = createSelect([
            ["OPEN", "Open"], ["INVESTIGATING", "Investigating"], ["RESOLVED", "Resolved"]
          ], alert.status, `Status for ${alert.title}`);
          status.addEventListener("change", async () => {
            try {
              await sendJson(`/api/user/alerts/${alert.id}`, "PATCH", { status: status.value });
              showToast("Alert status updated");
              await loadAlerts();
              if (state.page === "dashboard") await loadDashboard();
            } catch (error) {
              showToast(error.message, true);
              await loadAlerts();
            }
          });
          statusCell.append(status);
          row.append(statusCell);
          const actions = element("td");
          const details = element("button", "Details", "action-button");
          details.type = "button";
          details.addEventListener("click", () => showAlertDetails(alert));
          actions.append(details);
          row.append(actions);
          body.append(row);
        });
      }
      updateCount($("#alertPageCount"), result);
      createPager($("#alertPager"), result, state.alertPage, (page) => { state.alertPage = page; loadAlerts(); });
    } catch (error) {
      if (error.status === 403) renderTableError($("#routePanel tbody"), "Administrator access is required.");
      else if (error.status !== 401) showToast(error.message, true);
    }
  }

  function showAlertDetails(alert) {
    $("#detailTitle").textContent = alert.title;
    $("#detailCopy").textContent = `${alert.severity} priority · Detected ${formattedDate(alert.detectedAt, true)}`;
    const details = $("#detailContent");
    details.replaceChildren();
    [
      ["Status", alert.status],
      ["Source", alert.source],
      ["Event ID", alert.eventId || "—"],
      ["Details", alert.details || "No additional details were recorded."]
    ].forEach(([label, value]) => {
      const row = element("p");
      row.style.color = "#aebac8";
      row.style.fontSize = "11px";
      const strong = element("strong", `${label}: `);
      strong.style.color = "#e8edf4";
      row.append(strong, document.createTextNode(String(value)));
      details.append(row);
    });
    $("#detailDialog").showModal();
  }

  async function loadUsers() {
    const params = encodeQuery({ query: state.userQuery, page: state.userPage, size: state.userSize });
    try {
      const result = await getJson(`/api/admin/users?${params}`);
      const body = $("#routePanel tbody");
      body.replaceChildren();
      if (!result.content.length) renderTableError(body, "No users found.");
      result.content.forEach((user) => {
        const row = element("tr");
        row.append(element("td", user.username), element("td", user.email));
        const role = element("td", user.role === "ADMIN" ? "Administrator" : "User", "source-cell");
        row.append(role, element("td", formattedDate(user.createdAt, true)),
          element("td", user.lastLoginAt ? formattedDate(user.lastLoginAt, true) : "Never"));
        const actionCell = element("td");
        const remove = element("button", "Delete", "action-button danger");
        remove.type = "button";
        remove.disabled = user.id === state.user.id;
        remove.title = remove.disabled ? "You cannot delete your own account" : "Delete this user";
        remove.addEventListener("click", async () => {
          if (!window.confirm(`Delete user ${user.username}?`)) return;
          try {
            await api(`/api/admin/users/${user.id}`, { method: "DELETE" });
            showToast("User deleted");
            loadUsers();
          } catch (error) {
            showToast(error.message, true);
          }
        });
        actionCell.append(remove);
        row.append(actionCell);
        body.append(row);
      });
      updateCount($("#userPageCount"), result);
      createPager($("#userPager"), result, state.userPage, (page) => { state.userPage = page; loadUsers(); });
    } catch (error) {
      if (error.status === 403) renderTableError($("#routePanel tbody"), "User management requires the administrator role.");
      else if (error.status !== 401) showToast(error.message, true);
    }
  }

  async function loadLogs() {
    const params = encodeQuery({ query: state.eventQuery, page: state.logPage, size: state.logSize });
    try {
      const result = await getJson(`/api/user/logs?${params}`);
      const body = $("#routePanel tbody");
      body.replaceChildren();
      if (!result.content.length) renderTableError(body, "No system journal entries have been recorded.");
      result.content.forEach((log) => {
        const row = element("tr");
        row.append(element("td", formattedDate(log.createdAt, true)));
        row.append(element("td", log.level));
        row.append(element("td", log.category));
        row.append(element("td", log.message));
        row.append(element("td", log.actor));
        body.append(row);
      });
      updateCount($("#logPageCount"), result);
      createPager($("#logPager"), result, state.logPage, (page) => { state.logPage = page; loadLogs(); });
    } catch (error) {
      if (error.status !== 401) showToast(error.message, true);
    }
  }

  async function loadSettings() {
    try {
      if (state.user.role !== "ADMIN") throw Object.assign(new Error("System settings require the administrator role."), { status: 403 });
      const settings = await getJson("/api/admin/settings");
      $("#settingsForm").elements.workspaceName.value = settings.workspaceName;
      $("#settingsForm").elements.timezone.value = settings.timezone;
      $("#settingsForm").elements.retentionDays.value = settings.retentionDays;
      setText("#settingsUpdated", settings.updatedAt ? `Last saved ${formattedDate(settings.updatedAt, true)}` : "No custom settings have been saved.");
    } catch (error) {
      if (error.status === 403) {
        $("#routePanel").replaceChildren(element("div", error.message, "route-empty"));
        $("#routeToolbar").replaceChildren();
      } else if (error.status !== 401) showToast(error.message, true);
    }
  }

  function buildEventsPage() {
    $("#routeToolbar").append(eventToolbar(true));
    const right = element("div", null, "route-toolbar-group");
    right.append(exportButton());
    $("#routeToolbar").append(right);
    const table = createPageTable(
      ["Time", "Event", "Severity", "Source", "Destination", "Status"],
      "eventsTable", "eventPageCount", "eventPager", state.eventSize,
      (size) => { state.eventSize = size; state.eventPage = 0; loadCurrentEvents(); }
    );
    table.body.replaceChildren();
    loadCurrentEvents();
  }

  function buildAlertsPage() {
    const filters = element("div", null, "route-toolbar-group");
    const severity = createSelect([
      ["", "All severities"], ["CRITICAL", "Critical"], ["HIGH", "High"], ["MEDIUM", "Medium"], ["LOW", "Low"]
    ], state.alertSeverity, "Filter alert severity");
    severity.addEventListener("change", () => { state.alertSeverity = severity.value; state.alertPage = 0; loadAlerts(); });
    const status = createSelect([
      ["", "All statuses"], ["OPEN", "Open"], ["INVESTIGATING", "Investigating"], ["RESOLVED", "Resolved"]
    ], state.alertStatus, "Filter alert status");
    status.addEventListener("change", () => { state.alertStatus = status.value; state.alertPage = 0; loadAlerts(); });
    filters.append(severity, status);
    $("#routeToolbar").append(filters);
    const table = createPageTable(
      ["Detected", "Threat", "Priority", "Source", "Status", "Actions"],
      "alertsTable", "alertPageCount", "alertPager", state.alertSize,
      (size) => { state.alertSize = size; state.alertPage = 0; loadAlerts(); }
    );
    table.body.replaceChildren();
    loadAlerts();
  }

  function buildUsersPage() {
    const toolbar = element("div", null, "route-toolbar-group");
    const search = element("input", null, "form-control");
    search.type = "search";
    search.placeholder = "Search username or email";
    search.value = state.userQuery;
    search.setAttribute("aria-label", "Search users");
    search.addEventListener("input", () => {
      state.userQuery = search.value.trim();
      state.userPage = 0;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(loadUsers, 250);
    });
    toolbar.append(search);
    $("#routeToolbar").append(toolbar);
    const add = element("button", "Add user", "action-button primary-button");
    add.type = "button";
    add.addEventListener("click", () => {
      $("#userForm").reset();
      $("#userFormMessage").textContent = "";
      $("#userDialog").showModal();
    });
    $("#routeActions").append(add);
    const table = createPageTable(
      ["Username", "Email", "Role", "Registered", "Last sign-in", "Actions"],
      "usersTable", "userPageCount", "userPager", state.userSize,
      (size) => { state.userSize = size; state.userPage = 0; loadUsers(); }
    );
    table.body.replaceChildren();
    loadUsers();
  }

  function buildLogsPage() {
    $("#routeToolbar").append(eventToolbar(true));
    const table = createPageTable(
      ["Time", "Level", "Category", "Message", "Actor"],
      "logsTable", "logPageCount", "logPager", state.logSize,
      (size) => { state.logSize = size; state.logPage = 0; loadLogs(); }
    );
    table.body.replaceChildren();
    loadLogs();
  }

  function buildSettingsPage() {
    const form = element("form");
    form.id = "settingsForm";
    const grid = element("div", null, "form-grid");
    const workspace = element("label", "Workspace name", "form-field");
    const workspaceInput = element("input", null, "form-control");
    workspaceInput.name = "workspaceName";
    workspaceInput.maxLength = 100;
    workspaceInput.required = true;
    workspace.append(workspaceInput);
    const timezone = element("label", "Time zone", "form-field");
    const timezoneInput = element("input", null, "form-control");
    timezoneInput.name = "timezone";
    timezoneInput.maxLength = 60;
    timezoneInput.placeholder = "UTC";
    timezoneInput.required = true;
    timezone.append(timezoneInput);
    const retention = element("label", "Event retention (days)", "form-field");
    const retentionInput = element("input", null, "form-control");
    retentionInput.name = "retentionDays";
    retentionInput.type = "number";
    retentionInput.min = "1";
    retentionInput.max = "3650";
    retentionInput.required = true;
    retention.append(retentionInput);
    grid.append(workspace, timezone, retention);
    const updated = element("p", "", "panel-subtitle");
    updated.id = "settingsUpdated";
    const actions = element("div", null, "form-actions");
    const save = element("button", "Save settings", "action-button primary-button");
    save.type = "submit";
    actions.append(save);
    form.append(grid, updated, actions);
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const data = Object.fromEntries(new FormData(form));
      data.retentionDays = Number(data.retentionDays);
      try {
        const saved = await sendJson("/api/admin/settings", "PUT", data);
        setText("#settingsUpdated", `Last saved ${formattedDate(saved.updatedAt, true)}`);
        showToast("System settings saved");
      } catch (error) {
        showToast(error.message, true);
      }
    });
    $("#routePanel").replaceChildren(form);
    loadSettings();
  }

  async function renderPage(page) {
    state.page = page;
    $("#sidebar").classList.remove("open");
    $("#mobileMenu").setAttribute("aria-expanded", "false");
    if (!state.authenticated) {
      showAuth();
      return;
    }
    const definitions = {
      dashboard: ["Security overview", "Monitor your security posture and investigate threats across your environment."],
      events: ["Security events", "Search, filter, review, and export recorded security events."],
      alerts: ["Security alerts", "Investigate threats, review priority and update response status."],
      users: ["User management", "Review registered accounts, roles, and recent sign-ins."],
      logs: ["System logs", "Review the audit trail for recorded security operations."],
      settings: ["System settings", "Configure the workspace and event-retention policy."]
    };
    setRouteShell(page, ...definitions[page]);
    if (page === "dashboard") {
      await loadDashboard();
      return;
    }
    if (page === "events") buildEventsPage();
    if (page === "alerts") buildAlertsPage();
    if (page === "users") buildUsersPage();
    if (page === "logs") buildLogsPage();
    if (page === "settings") buildSettingsPage();
  }

  async function setAuthMode(register, bootstrap = false) {
    state.registerMode = register;
    state.bootstrapMode = bootstrap;
    $("#authTitle").textContent = bootstrap ? "Set up administrator" : register ? "Create your account" : "Welcome back";
    $("#authDescription").textContent = bootstrap
      ? "Bootstrap is available only with a server-configured one-time token. Set MINISIEM_ADMIN_BOOTSTRAP_TOKEN before setup."
      : register ? "Register a MiniSIEM account to access your security workspace."
        : "Sign in to your security operations workspace.";
    $("#authSubmit").textContent = bootstrap ? "Create administrator" : register ? "Create account" : "Sign in";
    $("#authToggle").textContent = register ? "Already have an account? Sign in" : "Create an account";
    $("#authEmailField").hidden = !(register || bootstrap);
    $("#authTokenField").hidden = !bootstrap;
    $("#authToggle").hidden = bootstrap;
    $("#bootstrapToggle").hidden = bootstrap;
    $("#authForm").elements.email.required = register || bootstrap;
    $("#authForm").elements.token.required = bootstrap;
    $("#authMessage").textContent = "";
  }

  async function login(usernameOrEmail, password) {
    await sendJson("/api/auth/login", "POST", { usernameOrEmail, password });
    state.user = await getJson("/api/auth/me");
    state.authenticated = true;
    showApp();
    const page = routeFromHash();
    await renderPage(page);
  }

  async function checkSession() {
    try {
      state.user = await getJson("/api/auth/me");
      state.authenticated = true;
      showApp();
      await renderPage(routeFromHash());
    } catch (error) {
      if (error.status === 401) {
        showAuth();
        await setAuthMode(false);
      } else {
        showAuth();
        $("#authMessage").textContent = error.message;
      }
    }
  }

  $("#authToggle").addEventListener("click", () => setAuthMode(!state.registerMode));
  $("#bootstrapToggle").addEventListener("click", () => setAuthMode(false, true));
  $("#authForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const identity = String(data.get("usernameOrEmail")).trim();
    const password = String(data.get("password"));
    $("#authMessage").textContent = "";
    try {
      if (state.bootstrapMode) {
        await sendJson("/api/auth/bootstrap-admin", "POST", {
          username: identity,
          email: String(data.get("email")).trim(),
          password,
          token: String(data.get("token"))
        });
      } else if (state.registerMode) {
        await sendJson("/api/auth/register", "POST", {
          username: identity,
          email: String(data.get("email")).trim(),
          password
        });
      }
      await login(identity, password);
      showToast(state.bootstrapMode ? "Administrator created" : state.registerMode ? "Account created" : "Signed in");
    } catch (error) {
      $("#authMessage").textContent = error.message;
    }
  });

  $("#userForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.currentTarget));
    try {
      await sendJson("/api/admin/users", "POST", data);
      $("#userDialog").close();
      showToast("User created");
      loadUsers();
    } catch (error) {
      $("#userFormMessage").textContent = error.message;
    }
  });

  $$("#detailDialog [data-close-dialog], #userDialog [data-close-dialog]").forEach((button) => {
    button.addEventListener("click", () => button.closest("dialog").close());
  });
  $("#closeDialog").addEventListener("click", () => $("#detailDialog").close());

  $("#eventSearch").addEventListener("input", (event) => {
    const query = event.target.value.trim();
    $("#clearSearch").classList.toggle("visible", Boolean(query));
    if (state.page === "users") {
      state.userQuery = query;
      state.userPage = 0;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(loadUsers, 250);
      return;
    }
    state.eventQuery = query;
    state.dashboardPage = 0;
    state.eventPage = 0;
    debounceRouteLoad();
    if (state.page === "dashboard") {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(loadDashboard, 250);
    }
  });
  $("#clearSearch").addEventListener("click", () => {
    $("#eventSearch").value = "";
    $("#eventSearch").dispatchEvent(new Event("input", { bubbles: true }));
    $("#eventSearch").focus();
  });

  $("#dashboardSeverity").addEventListener("change", () => {
    state.severity = $("#dashboardSeverity").value;
    state.dashboardPage = 0;
    loadDashboard();
  });
  $("#dashboardHours").addEventListener("change", () => {
    state.hours = Number($("#dashboardHours").value);
    state.dashboardPage = 0;
    loadDashboard();
  });
  $$(".range-button").forEach((button) => {
    button.addEventListener("click", () => {
      $$(".range-button").forEach((range) => {
        range.classList.remove("selected");
        range.setAttribute("aria-pressed", "false");
      });
      button.classList.add("selected");
      button.setAttribute("aria-pressed", "true");
      state.chartRange = button.dataset.range;
      loadDashboard();
    });
  });
  $("#dashboardPageSize").addEventListener("change", () => {
    state.dashboardSize = Number($("#dashboardPageSize").value);
    state.dashboardPage = 0;
    loadDashboard();
  });
  $("#exportButton").addEventListener("click", exportEvents);
  $("#refreshButton").addEventListener("click", () => {
    if (state.page === "dashboard") loadDashboard();
    else renderPage(state.page);
    showToast("Data refreshed");
  });
  $("#notificationButton").addEventListener("click", () => {
    state.alertStatus = "OPEN";
    navigate("alerts");
  });
  $("#logoutButton").addEventListener("click", async () => {
    try {
      await api("/api/auth/logout", { method: "POST" });
      state.authenticated = false;
      state.user = null;
      state.csrf = null;
      showAuth();
      await setAuthMode(false);
      showToast("Signed out");
    } catch (error) {
      showToast(error.message, true);
    }
  });
  $("#mobileMenu").addEventListener("click", () => {
    const open = $("#sidebar").classList.toggle("open");
    $("#mobileMenu").setAttribute("aria-expanded", String(open));
  });
  $$(".nav-link[data-page]").forEach((link) => {
    link.addEventListener("click", (event) => {
      event.preventDefault();
      navigate(link.dataset.page);
    });
  });
  $("#sidebar .brand").addEventListener("click", (event) => {
    event.preventDefault();
    navigate("dashboard");
  });
  window.addEventListener("hashchange", () => renderPage(routeFromHash()));

  checkSession();
})();
