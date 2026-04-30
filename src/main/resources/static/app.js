const state = {
    providers: [],
    apiLog: [],
    lastDepartureRequest: null,
    refreshTimer: null,
};

const elements = {
    providerSelect: document.querySelector("#providerSelect"),
    providerDescription: document.querySelector("#providerDescription"),
    stationForm: document.querySelector("#stationForm"),
    stationQuery: document.querySelector("#stationQuery"),
    locationType: document.querySelector("#locationType"),
    stationResults: document.querySelector("#stationResults"),
    departureForm: document.querySelector("#departureForm"),
    departureFrom: document.querySelector("#departureFrom"),
    departureLimit: document.querySelector("#departureLimit"),
    numberFilter: document.querySelector("#numberFilter"),
    toFilter: document.querySelector("#toFilter"),
    departureResults: document.querySelector("#departureResults"),
    refreshDepartures: document.querySelector("#refreshDepartures"),
    autoRefresh: document.querySelector("#autoRefresh"),
    tripForm: document.querySelector("#tripForm"),
    tripFrom: document.querySelector("#tripFrom"),
    tripTo: document.querySelector("#tripTo"),
    tripProduct: document.querySelector("#tripProduct"),
    timeOffset: document.querySelector("#timeOffset"),
    tripResults: document.querySelector("#tripResults"),
    apiLogEntries: document.querySelector("#apiLogEntries"),
    clearLog: document.querySelector("#clearLog"),
};

document.addEventListener("DOMContentLoaded", () => {
    bindEvents();
    loadProviders();
});

function bindEvents() {
    elements.providerSelect.addEventListener("change", updateProviderDescription);
    elements.stationForm.addEventListener("submit", (event) => {
        event.preventDefault();
        searchStations();
    });
    elements.departureForm.addEventListener("submit", (event) => {
        event.preventDefault();
        loadDeparturesFromForm();
    });
    elements.refreshDepartures.addEventListener("click", () => {
        if (state.lastDepartureRequest) {
            loadDepartures(state.lastDepartureRequest);
        }
    });
    elements.autoRefresh.addEventListener("change", toggleAutoRefresh);
    elements.tripForm.addEventListener("submit", (event) => {
        event.preventDefault();
        findTrips();
    });
    elements.clearLog.addEventListener("click", () => {
        state.apiLog = [];
        renderApiLog();
    });
}

async function loadProviders() {
    const result = await apiRequest("/v2/provider");
    if (!result.ok || !Array.isArray(result.data)) {
        elements.providerDescription.textContent = "Could not load providers.";
        return;
    }

    state.providers = result.data;
    elements.providerSelect.replaceChildren(...result.data.map((provider) => {
        const option = document.createElement("option");
        option.value = provider.name;
        option.textContent = provider.description ? `${provider.name} - ${provider.description}` : provider.name;
        return option;
    }));

    const kvv = result.data.find((provider) => provider.name === "KVV");
    if (kvv) {
        elements.providerSelect.value = kvv.name;
    }
    updateProviderDescription();
}

function updateProviderDescription() {
    const provider = selectedProvider();
    elements.providerDescription.textContent = provider?.description || "Provider used for all API calls.";
}

function selectedProviderName() {
    return elements.providerSelect.value || "KVV";
}

function selectedProvider() {
    const name = selectedProviderName();
    return state.providers.find((provider) => provider.name === name);
}

async function searchStations() {
    const query = elements.stationQuery.value.trim();
    if (!query) {
        return;
    }

    setLoading(elements.stationResults, "Searching stations...");
    const params = {
        q: query,
        provider: selectedProviderName(),
        locationType: elements.locationType.value,
    };
    const result = await apiRequest("/v2/station/suggest", params);

    if (!result.ok) {
        renderError(elements.stationResults, result);
        return;
    }
    renderStations(Array.isArray(result.data) ? result.data : []);
}

function renderStations(stations) {
    if (!stations.length) {
        renderEmpty(elements.stationResults, "No matching locations returned.");
        return;
    }

    elements.stationResults.classList.remove("empty-state");
    elements.stationResults.replaceChildren(...stations.map((station) => {
        const card = document.createElement("article");
        card.className = "result-card";

        const heading = document.createElement("div");
        heading.className = "card-head";
        heading.append(titleBlock(station.name || "Unnamed location", station.type || "Location"));

        const meta = document.createElement("div");
        meta.className = "meta";
        meta.append(...[
            station.id ? `ID: ${station.id}` : "No ID",
            station.place ? `Place: ${station.place}` : null,
            station.lat && station.lon ? `${station.lat}, ${station.lon}` : null,
        ].filter(Boolean).map(toSpan));

        const actions = document.createElement("div");
        actions.className = "card-actions";
        actions.append(
            actionButton("Use as origin", () => { elements.tripFrom.value = station.id || ""; }),
            actionButton("Use as destination", () => { elements.tripTo.value = station.id || ""; }),
            actionButton("Show departures", () => {
                elements.departureFrom.value = station.id || "";
                loadDeparturesFromForm();
            })
        );

        card.append(heading, meta, actions);
        return card;
    }));
}

function loadDeparturesFromForm() {
    const stationId = elements.departureFrom.value.trim();
    if (!stationId) {
        return;
    }

    const request = {
        from: stationId,
        provider: selectedProviderName(),
        limit: elements.departureLimit.value || "10",
        numberFilter: elements.numberFilter.value.trim(),
        toFilter: elements.toFilter.value.trim(),
    };
    state.lastDepartureRequest = request;
    loadDepartures(request);
}

async function loadDepartures(request) {
    setLoading(elements.departureResults, "Loading live departures...");
    const params = removeEmpty({
        from: request.from,
        provider: request.provider,
        limit: request.limit,
        numberFilter: request.numberFilter,
        toFilter: request.toFilter,
    });
    const result = await apiRequest("/v2/departure", params);

    if (!result.ok) {
        renderError(elements.departureResults, result);
        return;
    }
    renderDepartures(Array.isArray(result.data) ? result.data : []);
}

function renderDepartures(departures) {
    if (!departures.length) {
        renderEmpty(elements.departureResults, "No departures returned for this station.");
        return;
    }

    elements.departureResults.classList.remove("empty-state");
    elements.departureResults.replaceChildren(...departures.map((departure) => {
        const card = document.createElement("article");
        card.className = "result-card departure-card";

        const minutes = document.createElement("div");
        minutes.className = "departure-time";
        minutes.textContent = Number.isFinite(departure.departureTimeInMinutes) ? `${departure.departureTimeInMinutes}m` : "--";

        const content = document.createElement("div");
        const title = `${departure.number || "?"} to ${departure.to || "Unknown destination"}`;
        content.append(titleBlock(title, departure.product || "Service"));

        const meta = document.createElement("div");
        meta.className = "meta";
        meta.append(...[
            departure.departureTime ? `Time: ${departure.departureTime}` : null,
            departure.platform ? `Platform: ${departure.platform}` : null,
            departure.departureDelay ? `Delay: ${departure.departureDelay} min` : "Delay: 0 min",
            departure.message ? `Message: ${departure.message}` : null,
        ].filter(Boolean).map(toSpan));
        content.append(meta);
        card.append(minutes, content);
        return card;
    }));
}

async function findTrips() {
    const product = elements.tripProduct.value.trim();
    if (!elements.tripFrom.value.trim() || !elements.tripTo.value.trim() || !product) {
        return;
    }

    setLoading(elements.tripResults, "Finding direct trips...");
    const result = await apiRequest("/connection", {
        from: elements.tripFrom.value.trim(),
        to: elements.tripTo.value.trim(),
        provider: selectedProviderName(),
        product: product.charAt(0),
        timeOffset: elements.timeOffset.value || "0",
    });

    if (!result.ok) {
        renderError(elements.tripResults, result);
        return;
    }
    renderTrips(Array.isArray(result.data) ? result.data : []);
}

function renderTrips(trips) {
    if (!trips.length) {
        renderEmpty(elements.tripResults, "No direct trips returned.");
        return;
    }

    elements.tripResults.classList.remove("empty-state");
    elements.tripResults.replaceChildren(...trips.map((trip) => {
        const card = document.createElement("article");
        card.className = "result-card";
        card.append(titleBlock(`${trip.number || "?"}: ${trip.from || "Origin"} to ${trip.to || "Destination"}`, trip.product || "Trip"));

        const meta = document.createElement("div");
        meta.className = "meta";
        meta.append(...[
            trip.departureTime ? `Departure: ${trip.departureTime}` : null,
            trip.plannedDepartureTime ? `Planned: ${trip.plannedDepartureTime}` : null,
            Number.isFinite(trip.departureDelay) ? `Delay: ${Math.round(trip.departureDelay / 60)} min` : null,
            trip.fromId ? `From ID: ${trip.fromId}` : null,
            trip.toId ? `To ID: ${trip.toId}` : null,
        ].filter(Boolean).map(toSpan));
        card.append(meta);
        return card;
    }));
}

async function apiRequest(path, params = {}) {
    const url = buildUrl(path, params);
    const startedAt = performance.now();
    const entry = {
        id: randomId(),
        method: "GET",
        path: `${url.pathname}${url.search}`,
        absoluteUrl: url.href,
        curl: toCurl(url.href),
        status: "pending",
        statusText: "Pending",
        startedAt: new Date(),
        durationMs: null,
        responseText: "",
        ok: false,
    };
    state.apiLog.unshift(entry);
    renderApiLog();

    try {
        const response = await fetch(url, { headers: { Accept: "application/json" } });
        const responseText = await response.text();
        const data = parseResponse(responseText, response.headers.get("content-type"));
        Object.assign(entry, {
            status: response.status,
            statusText: response.statusText || String(response.status),
            durationMs: Math.round(performance.now() - startedAt),
            responseText: formatResponseText(data, responseText),
            ok: response.ok,
        });
        renderApiLog();
        return { ok: response.ok, status: response.status, data, responseText: entry.responseText };
    } catch (error) {
        Object.assign(entry, {
            status: "error",
            statusText: "Network error",
            durationMs: Math.round(performance.now() - startedAt),
            responseText: error instanceof Error ? error.message : String(error),
            ok: false,
        });
        renderApiLog();
        return { ok: false, status: "error", data: null, responseText: entry.responseText };
    }
}

function buildUrl(path, params) {
    const url = new URL(path, window.location.origin);
    Object.entries(removeEmpty(params)).forEach(([key, value]) => {
        url.searchParams.set(key, value);
    });
    return url;
}

function toCurl(url) {
    return `curl -X GET '${url.replaceAll("'", "'\\''")}' -H 'Accept: application/json'`;
}

function parseResponse(text, contentType) {
    if (!text) {
        return null;
    }
    if (contentType?.includes("application/json")) {
        try {
            return JSON.parse(text);
        } catch (_ignored) {
            return text;
        }
    }
    try {
        return JSON.parse(text);
    } catch (_ignored) {
        return text;
    }
}

function formatResponseText(data, fallback) {
    if (data === null || data === undefined) {
        return fallback || "";
    }
    if (typeof data === "string") {
        return data;
    }
    return JSON.stringify(data, null, 2);
}

function renderApiLog() {
    if (!state.apiLog.length) {
        renderEmpty(elements.apiLogEntries, "No API calls yet.");
        return;
    }

    elements.apiLogEntries.classList.remove("empty-state");
    elements.apiLogEntries.replaceChildren(...state.apiLog.map((entry) => {
        const article = document.createElement("article");
        article.className = `log-entry ${entry.status === "pending" ? "pending" : ""} ${entry.ok || entry.status === "pending" ? "" : "error"}`;

        const head = document.createElement("div");
        head.className = "log-entry-head";

        const title = document.createElement("div");
        title.className = "log-title";
        const path = document.createElement("strong");
        path.textContent = `${entry.method} ${entry.path}`;
        const when = document.createElement("small");
        when.textContent = `${entry.startedAt.toLocaleTimeString()}${entry.durationMs === null ? "" : ` - ${entry.durationMs}ms`}`;
        title.append(path, when);

        const status = document.createElement("span");
        status.className = `status ${entry.ok || entry.status === "pending" ? "" : "error"}`;
        status.textContent = `${entry.status} ${entry.statusText}`;
        head.append(title, status);

        const curlRow = copyRow("curl", entry.curl);
        const curl = document.createElement("pre");
        curl.className = "code-block curl-block";
        curl.textContent = entry.curl;
        const responseRow = copyRow("response", entry.responseText || "");
        const response = document.createElement("pre");
        response.className = "code-block";
        response.textContent = entry.responseText || (entry.status === "pending" ? "Waiting for response..." : "No response body.");

        article.append(head, curlRow, curl, responseRow, response);
        return article;
    }));
}

function copyRow(label, value) {
    const row = document.createElement("div");
    row.className = "copy-row";
    const text = document.createElement("span");
    text.textContent = label;
    const button = document.createElement("button");
    button.type = "button";
    button.className = "copy-button";
    button.textContent = "Copy";
    button.addEventListener("click", async () => {
        await copyText(value);
        button.textContent = "Copied";
        window.setTimeout(() => { button.textContent = "Copy"; }, 1200);
    });
    row.append(text, button);
    return row;
}

async function copyText(value) {
    if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(value);
        return;
    }
    const textarea = document.createElement("textarea");
    textarea.value = value;
    textarea.setAttribute("readonly", "");
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.append(textarea);
    textarea.select();
    document.execCommand("copy");
    textarea.remove();
}

function toggleAutoRefresh() {
    if (state.refreshTimer) {
        window.clearInterval(state.refreshTimer);
        state.refreshTimer = null;
    }
    if (elements.autoRefresh.checked) {
        state.refreshTimer = window.setInterval(() => {
            if (state.lastDepartureRequest) {
                loadDepartures(state.lastDepartureRequest);
            }
        }, 30000);
    }
}

function renderError(container, result) {
    renderEmpty(container, `Request failed (${result.status}): ${result.responseText || "No response body."}`);
}

function setLoading(container, message) {
    renderEmpty(container, message);
}

function renderEmpty(container, message) {
    container.classList.add("empty-state");
    container.replaceChildren(document.createTextNode(message));
}

function titleBlock(title, subtitle) {
    const wrap = document.createElement("div");
    const h3 = document.createElement("h3");
    h3.textContent = title;
    const meta = document.createElement("div");
    meta.className = "meta";
    meta.append(toSpan(subtitle));
    wrap.append(h3, meta);
    return wrap;
}

function actionButton(label, onClick) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "secondary";
    button.textContent = label;
    button.addEventListener("click", onClick);
    return button;
}

function toSpan(text) {
    const span = document.createElement("span");
    span.textContent = text;
    return span;
}

function randomId() {
    if (crypto.randomUUID) {
        return crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function removeEmpty(object) {
    return Object.fromEntries(Object.entries(object).filter(([, value]) => value !== undefined && value !== null && String(value).trim() !== ""));
}
