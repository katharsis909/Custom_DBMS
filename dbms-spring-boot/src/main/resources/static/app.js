const sqlInput = document.getElementById("sqlInput");
const runButton = document.getElementById("runButton");
const resetButton = document.getElementById("resetButton");
const outputArea = document.getElementById("outputArea");
const statusBadge = document.getElementById("statusBadge");

runButton.addEventListener("click", () => executeSql());
resetButton.addEventListener("click", () => resetCatalog());

async function executeSql() {
    const sql = sqlInput.value;
    if (!sql.trim()) {
        renderError("SQL must not be empty");
        return;
    }

    setRunningState(true, "Running");
    try {
        const response = await fetch("/api/sql/execute", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ sql })
        });

        const payload = await response.json();
        if (!response.ok || !payload.success) {
            renderError(payload.error || "Query execution failed");
            return;
        }

        renderSuccess(payload.output || "OK", payload);
    } catch (error) {
        renderError("Unable to reach the API. Make sure the Spring Boot app is running.");
    } finally {
        setRunningState(false);
    }
}

async function resetCatalog() {
    setRunningState(true, "Resetting");
    try {
        const response = await fetch("/api/sql/reset", {
            method: "POST"
        });

        const payload = await response.json();
        if (!response.ok || !payload.success) {
            renderError(payload.error || "Catalog reset failed");
            return;
        }

        renderSuccess(payload.output || "Catalog reset", payload);
    } catch (error) {
        renderError("Unable to reach the API. Make sure the Spring Boot app is running.");
    } finally {
        setRunningState(false);
    }
}

function setRunningState(isRunning, label = "Running") {
    runButton.disabled = isRunning;
    resetButton.disabled = isRunning;
    if (isRunning) {
        updateStatus("running", label);
    }
}

function renderSuccess(output, payload) {
    outputArea.className = "output-body";
    outputArea.innerHTML = "";

    if (payload && Array.isArray(payload.results) && payload.results.length > 0) {
        payload.results.forEach((block) => {
            outputArea.appendChild(buildResultBlock(block));
        });
    } else {
        const pre = document.createElement("pre");
        pre.className = "text-output";
        pre.textContent = output;
        outputArea.appendChild(pre);
    }

    updateStatus("success", "Success");
}

function renderError(message) {
    outputArea.className = "output-body error-output";
    outputArea.textContent = message;
    updateStatus("error", "Error");
}

function updateStatus(state, label) {
    statusBadge.className = `status-badge ${state}`;
    statusBadge.textContent = label;
}

function buildResultBlock(block) {
    const container = document.createElement("section");

    const label = document.createElement("p");
    label.className = "result-block-label";

    if (Array.isArray(block.columns) && Array.isArray(block.rows)) {
        label.textContent = "Table Result";
        container.appendChild(label);
        container.appendChild(buildTable(block.columns, block.rows));
        return container;
    }

    label.textContent = "Message";
    container.appendChild(label);

    const pre = document.createElement("pre");
    pre.className = "text-output";
    pre.textContent = block.message || "";
    container.appendChild(pre);
    return container;
}

function buildTable(headers, rows) {
    const table = document.createElement("table");
    table.className = "result-table";

    const thead = document.createElement("thead");
    const headRow = document.createElement("tr");
    headers.forEach((header) => {
        const th = document.createElement("th");
        th.textContent = header;
        headRow.appendChild(th);
    });
    thead.appendChild(headRow);

    const tbody = document.createElement("tbody");
    rows.forEach((row) => {
        const tr = document.createElement("tr");
        row.forEach((cell) => {
            const td = document.createElement("td");
            td.textContent = cell;
            tr.appendChild(td);
        });
        tbody.appendChild(tr);
    });

    table.appendChild(thead);
    table.appendChild(tbody);
    return table;
}
