const TOKEN_KEY = 'agriflowToken';
const ROLE_KEY = 'agriflowRole';
const FIELD_KEY = 'agriflowField';
const API_BASE = window.location.port === '8080'
  ? window.location.origin
  : 'http://127.0.0.1:8080';

document.addEventListener('DOMContentLoaded', () => {
  const year = document.getElementById('year');
  if (year) year.textContent = new Date().getFullYear();

  initializeLoginSwitcher();

  document.querySelectorAll('.logout-link').forEach((link) => {
    link.addEventListener('click', async (event) => {
      event.preventDefault();
      try {
        await api('/api/logout', 'POST');
      } catch (error) {
        console.error('Could not close server session:', error.message);
      }
      clearSession();
      window.location.href = 'login.html';
    });
  });

  const adminLogin = document.getElementById('admin-login-form');
  if (adminLogin) adminLogin.addEventListener('submit', async (event) => {
    event.preventDefault();
    await submitForm(adminLogin, async () => {
      const result = await api('/api/login', 'POST', {
        role: 'admin',
        username: document.getElementById('admin-user').value,
        password: document.getElementById('admin-pass').value
      }, false);
      saveSession(result, 'admin');
      window.location.href = 'admin-dashboard.html';
    });
  });

  const farmerLogin = document.getElementById('farmer-login-form');
  if (farmerLogin) farmerLogin.addEventListener('submit', async (event) => {
    event.preventDefault();
    await submitForm(farmerLogin, async () => {
      const result = await api('/api/login', 'POST', {
        role: 'farmer',
        fieldId: document.getElementById('field-id').value,
        password: document.getElementById('farmer-pass').value
      }, false);
      saveSession(result, 'farmer', document.getElementById('field-id').value.trim().toUpperCase());
      window.location.href = 'farmer-dashboard.html';
    });
  });

  if (document.getElementById('admin-dashboard')) initializeAdminDashboard();
  if (document.getElementById('farmer-dashboard')) initializeFarmerDashboard();
});

function initializeLoginSwitcher() {
  const switcher = document.getElementById('login-role-switch');
  if (!switcher) return;

  const panels = Array.from(document.querySelectorAll('[data-login-panel]'));
  const buttons = Array.from(switcher.querySelectorAll('[data-login-target]'));
  const activate = (panelId, updateHash = false) => {
    const activePanel = panels.find((panel) => panel.id === panelId);
    if (!activePanel) return;

    panels.forEach((panel) => {
      panel.hidden = panel !== activePanel;
    });
    buttons.forEach((button) => {
      const selected = button.dataset.loginTarget === panelId;
      button.setAttribute('aria-pressed', String(selected));
      button.classList.toggle('active', selected);
    });

    if (updateHash) window.history.replaceState(null, '', `#${panelId}`);
  };

  const initialPanel = panels.find((panel) => panel.id === window.location.hash.slice(1));
  activate(initialPanel ? initialPanel.id : 'admin-login');

  buttons.forEach((button) => {
    button.addEventListener('click', () => activate(button.dataset.loginTarget, true));
  });

  window.addEventListener('hashchange', () => {
    activate(window.location.hash.slice(1));
  });
}

async function api(path, method = 'GET', data = null, authenticated = true) {
  const headers = {};
  if (data) headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (authenticated && token) headers.Authorization = `Bearer ${token}`;
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: data ? new URLSearchParams(data).toString() : undefined
    });
  } catch (error) {
    throw new Error('Cannot connect to Java backend. Start it with start-server.bat and try again.');
  }
  const responseText = await response.text();
  let result;
  try {
    result = responseText ? JSON.parse(responseText) : {};
  } catch (error) {
    throw new Error('The Java server returned an invalid response. Restart it and try again.');
  }
  if (!response.ok && !result.error) {
    throw new Error('The Java server is unavailable. Start it with start-server.bat and try again.');
  }
  if (!response.ok) throw new Error(result.error || 'Request failed.');
  return result;
}

function saveSession(result, role, fieldId = '') {
  sessionStorage.setItem(TOKEN_KEY, result.token);
  sessionStorage.setItem(ROLE_KEY, role);
  if (fieldId) sessionStorage.setItem(FIELD_KEY, fieldId);
}

function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(ROLE_KEY);
  sessionStorage.removeItem(FIELD_KEY);
}

function requireRole(role) {
  if (!sessionStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(ROLE_KEY) !== role) {
    window.location.replace('login.html');
    return false;
  }
  return true;
}

async function submitForm(form, action) {
  const button = form.querySelector('button[type="submit"]');
  const originalText = button ? button.textContent : '';
  if (button) button.disabled = true;
  try {
    await action();
  } catch (error) {
    if (form.closest('#admin-dashboard')) {
      showMessage('admin-message', error.message, true);
    } else if (form.closest('#farmer-dashboard')) {
      showMessage('farmer-message', error.message, true);
    } else {
      const output = form.querySelector('[data-form-message]') || ensureFormMessage(form);
      output.textContent = error.message;
      output.classList.add('error-message');
    }
  } finally {
    if (button) {
      button.disabled = false;
      button.textContent = originalText;
    }
  }
}

function ensureFormMessage(form) {
  const output = document.createElement('p');
  output.dataset.formMessage = '';
  output.setAttribute('role', 'alert');
  form.appendChild(output);
  return output;
}

function showMessage(id, text, isError = false) {
  const output = document.getElementById(id);
  if (!output) return;
  output.textContent = text;
  output.classList.toggle('error-message', isError);
}

async function initializeAdminDashboard() {
  if (!requireRole('admin')) return;
  const addFieldForm = document.getElementById('add-field-form');
  const moistureForm = document.getElementById('moisture-form');
  const reservoirAddForm = document.getElementById('reservoir-add-form');
  const reservoirSetForm = document.getElementById('reservoir-set-form');
  const canalForm = document.getElementById('canal-form');

  addFieldForm.addEventListener('submit', (event) => {
    event.preventDefault();
    submitForm(addFieldForm, async () => {
      await api('/api/admin/fields', 'POST', formData(addFieldForm));
      addFieldForm.reset();
      showMessage('admin-message', 'Field registered successfully.');
      await renderAdmin();
    });
  });
  moistureForm.addEventListener('submit', (event) => {
    event.preventDefault();
    submitForm(moistureForm, async () => {
      await api('/api/admin/moisture', 'POST', formData(moistureForm));
      moistureForm.reset();
      showMessage('admin-message', 'Soil moisture updated.');
      await renderAdmin();
    });
  });
  reservoirAddForm.addEventListener('submit', (event) => {
    event.preventDefault();
    submitForm(reservoirAddForm, async () => {
      await api('/api/admin/reservoir/add', 'POST', formData(reservoirAddForm));
      reservoirAddForm.reset();
      showMessage('admin-message', 'Reservoir water added.');
      await renderAdmin();
    });
  });
  reservoirSetForm.addEventListener('submit', (event) => {
    event.preventDefault();
    submitForm(reservoirSetForm, async () => {
      await api('/api/admin/reservoir/set', 'POST', formData(reservoirSetForm));
      reservoirSetForm.reset();
      showMessage('admin-message', 'Reservoir level updated.');
      await renderAdmin();
    });
  });
  canalForm.addEventListener('submit', (event) => {
    event.preventDefault();
    submitForm(canalForm, async () => {
      await api('/api/admin/canals', 'POST', formData(canalForm));
      canalForm.reset();
      showMessage('admin-message', 'Canal added.');
      await renderAdmin();
    });
  });
  document.getElementById('allocate-water').addEventListener('click', () => {
    submitForm(document.getElementById('allocation-actions'), async () => {
      await api('/api/admin/allocate', 'POST', {});
      showMessage('admin-message', 'Allocation run completed. Unreachable and unfulfilled requests remain pending.');
      await renderAdmin();
    });
  });
  document.getElementById('canal-table').addEventListener('click', (event) => {
    const button = event.target.closest('[data-canal-number]');
    if (!button) return;
    submitForm(document.getElementById('canal-actions'), async () => {
      await api('/api/admin/canals/status', 'POST', {
        number: button.dataset.canalNumber,
        working: button.dataset.working
      });
      showMessage('admin-message', button.dataset.working === 'true' ? 'Canal restored.' : 'Canal blocked.');
      await renderAdmin();
    });
  });
  try {
    await renderAdmin();
  } catch (error) {
    showMessage('admin-message', error.message, true);
  }
}

async function renderAdmin() {
  const data = await api('/api/admin/overview');
  setText('total-fields', data.fields.length);
  setText('reservoir-total', `${formatNumber(data.reservoir)} L`);
  setText('total-requests', data.requests.length);
  setText('pending-total', data.pendingRequests.length);
  renderFields(data.fields);
  renderRequests(data.requests, 'admin-request-rows', true);
  renderRequests(data.pendingRequests, 'pending-request-rows', true);
  renderCanals(data.canals, data.fields);
  renderHistory(data.history);
  const fieldOptions = data.fields.map((field) => `<option value="${escapeHtml(field.fieldId)}">${escapeHtml(field.fieldId)} — ${escapeHtml(field.farmerName)}</option>`).join('');
  document.getElementById('moisture-field').innerHTML = `<option value="">Choose field</option>${fieldOptions}`;
  const nodeOptions = `<option value="RESERVOIR">RESERVOIR</option>${data.fields.map((field) => `<option value="${escapeHtml(field.fieldId)}">${escapeHtml(field.fieldId)}</option>`).join('')}`;
  document.getElementById('canal-from').innerHTML = nodeOptions;
  document.getElementById('canal-to').innerHTML = nodeOptions;
}

function renderFields(fields) {
  const rows = fields.map((field) => `<tr>
    <td>${escapeHtml(field.fieldId)}</td><td>${escapeHtml(field.farmerName)}</td>
    <td>${escapeHtml(field.cropName)}</td><td>${field.priority} — ${escapeHtml(field.priorityName)}</td>
    <td>${formatNumber(field.moisture)}%</td><td><span class="status-badge ${field.reachable ? 'status-good' : 'status-alert'}">${field.reachable ? 'Reachable' : 'Unreachable'}</span></td>
  </tr>`).join('');
  document.getElementById('field-rows').innerHTML = rows || '<tr><td colspan="6">No fields registered yet.</td></tr>';
}

function renderRequests(requests, target, includeFarmer, includeField = false) {
  const rows = requests.map((request) => `<tr>
    <td>${escapeHtml(request.requestId)}</td>
    ${includeField && !includeFarmer ? `<td>${escapeHtml(request.fieldId)}</td>` : ''}
    ${includeFarmer ? `<td>${escapeHtml(request.fieldId)}</td><td>${escapeHtml(request.farmerName)}</td>` : ''}
    <td>${escapeHtml(request.type)}</td><td>${request.priority} — ${['', 'High', 'Medium', 'Low'][request.priority]}</td>
    <td>${formatNumber(request.requested)} L</td><td>${formatNumber(request.allocated)} L</td>
    <td>${formatNumber(request.remaining)} L</td><td><span class="status-badge ${request.completed ? 'status-good' : request.allocated > 0 ? 'status-warn' : 'status-alert'}">${escapeHtml(request.status)}</span></td>
  </tr>`).join('');
  const colspan = includeFarmer ? 9 : includeField ? 8 : 7;
  document.getElementById(target).innerHTML = rows || `<tr><td colspan="${colspan}">No irrigation requests.</td></tr>`;
}

function renderCanals(canals, fields) {
  const rows = canals.map((canal) => `<tr>
    <td>${canal.number}</td><td>${escapeHtml(canal.from)} ↔ ${escapeHtml(canal.to)}</td>
    <td><span class="status-badge ${canal.working ? 'status-good' : 'status-alert'}">${canal.working ? 'Working' : 'Blocked'}</span></td>
    <td><button type="button" class="small-button" data-canal-number="${canal.number}" data-working="${!canal.working}">${canal.working ? 'Block' : 'Restore'}</button></td>
  </tr>`).join('');
  document.getElementById('canal-rows').innerHTML = rows || '<tr><td colspan="4">No canals added yet.</td></tr>';
  renderCanalMap(canals, fields);
}

function renderCanalMap(canals, fields) {
  const nodeIds = ['RESERVOIR', ...fields.map((field) => field.fieldId)];
  const adjacency = new Map(nodeIds.map((id) => [id, []]));
  canals.forEach((canal) => {
    if (!canal.working || !adjacency.has(canal.from) || !adjacency.has(canal.to)) return;
    adjacency.get(canal.from).push(canal.to);
    adjacency.get(canal.to).push(canal.from);
  });

  const distances = new Map([['RESERVOIR', 0]]);
  const queue = ['RESERVOIR'];
  for (let index = 0; index < queue.length; index += 1) {
    const current = queue[index];
    adjacency.get(current).forEach((neighbor) => {
      if (distances.has(neighbor)) return;
      distances.set(neighbor, distances.get(current) + 1);
      queue.push(neighbor);
    });
  }

  const furthestReachable = Math.max(0, ...distances.values());
  const layers = new Map();
  fields.forEach((field) => {
    const depth = distances.get(field.fieldId) ?? furthestReachable + 1;
    if (!layers.has(depth)) layers.set(depth, []);
    layers.get(depth).push(field.fieldId);
  });

  const maxDepth = Math.max(1, ...layers.keys());
  const largestLayer = Math.max(1, ...Array.from(layers.values(), (layer) => layer.length));
  const width = Math.max(880, 260 + maxDepth * 210);
  const height = Math.max(360, largestLayer * 96 + 80);
  const positions = new Map([['RESERVOIR', { x: 110, y: height / 2 }]]);

  layers.forEach((layer, depth) => {
    layer.forEach((id, index) => {
      positions.set(id, {
        x: 110 + depth * 210,
        y: 48 + ((index + 1) * (height - 96)) / (layer.length + 1)
      });
    });
  });

  const links = canals.map((canal) => {
    const from = positions.get(canal.from);
    const to = positions.get(canal.to);
    if (!from || !to) return '';

    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const fromScale = Math.min(68 / Math.abs(dx || Number.EPSILON), 29 / Math.abs(dy || Number.EPSILON));
    const toScale = Math.min(68 / Math.abs(dx || Number.EPSILON), 29 / Math.abs(dy || Number.EPSILON));
    const x1 = from.x + dx * fromScale;
    const y1 = from.y + dy * fromScale;
    const x2 = to.x - dx * toScale;
    const y2 = to.y - dy * toScale;
    const labelX = (x1 + x2) / 2;
    const labelY = (y1 + y2) / 2;
    const status = canal.working ? 'Working' : 'Blocked';

    return `<g class="canal-link ${canal.working ? 'is-working' : 'is-blocked'}">
      <title>Canal ${canal.number}: ${escapeHtml(canal.from)} to ${escapeHtml(canal.to)} - ${status}</title>
      <line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}"></line>
      <g transform="translate(${labelX} ${labelY})">
        <rect x="-19" y="-11" width="38" height="22" rx="8"></rect>
        <text text-anchor="middle" dominant-baseline="central">C${canal.number}</text>
      </g>
    </g>`;
  }).join('');

  const nodes = nodeIds.map((id) => {
    const position = positions.get(id);
    const isReservoir = id === 'RESERVOIR';
    const label = id.length > 18 ? `${id.slice(0, 16)}...` : id;
    return `<g class="canal-map-node ${isReservoir ? 'is-reservoir' : 'is-field'}" transform="translate(${position.x} ${position.y})">
      <title>${escapeHtml(id)}</title>
      <rect x="${isReservoir ? -72 : -68}" y="${isReservoir ? -31 : -28}" width="${isReservoir ? 144 : 136}" height="${isReservoir ? 62 : 56}" rx="14"></rect>
      <text text-anchor="middle" dominant-baseline="central">${escapeHtml(label)}</text>
    </g>`;
  }).join('');

  const map = document.getElementById('canal-map');
  map.setAttribute('viewBox', `0 0 ${width} ${height}`);
  map.setAttribute('width', width);
  map.setAttribute('height', height);
  map.setAttribute('aria-label', `Canal network map with ${canals.length} connections and ${fields.length} fields`);
  map.innerHTML = `${links}${nodes}`;

  const emptyMessage = document.getElementById('canal-map-empty');
  emptyMessage.textContent = canals.length
    ? ''
    : 'No canal connections yet. Add a connection below to map the irrigation network.';
}

function renderHistory(history) {
  const rows = history.slice().reverse().map((item) => `<tr><td>${escapeHtml(item)}</td></tr>`).join('');
  document.getElementById('history-rows').innerHTML = rows || '<tr><td>No allocation history yet.</td></tr>';
}

async function initializeFarmerDashboard() {
  if (!requireRole('farmer')) return;
  const addCropForm = document.getElementById('add-crop-form');
  addCropForm.addEventListener('submit', (event) => {
    event.preventDefault();
    submitForm(addCropForm, async () => {
      const result = await api('/api/farmer/crops', 'POST', formData(addCropForm));
      addCropForm.reset();
      showMessage('farmer-message', `${result.message} New Field ID: ${result.fieldId}.`);
      await renderFarmer();
    });
  });
  document.getElementById('farmer-crop-fields').addEventListener('submit', (event) => {
    const form = event.target.closest('[data-crop-request]');
    if (!form) return;
    event.preventDefault();
    submitForm(form, async () => {
      const result = await api('/api/farmer/request', 'POST', {
        amount: form.elements.amount.value,
        type: form.elements.type.value,
        fieldId: form.dataset.fieldId
      });
      form.reset();
      showMessage('farmer-message', `${result.message} Request ID: ${result.requestId}`);
      await renderFarmer();
    });
  });
  try {
    await renderFarmer();
  } catch (error) {
    showMessage('farmer-message', error.message, true);
  }
}

async function renderFarmer() {
  const data = await api('/api/farmer/me');
  const field = data.field;
  const fields = data.fields || [field];
  setText('farmer-name', field.farmerName);
  setText('farmer-field-count', fields.length);
  setText('farmer-reachability', `${fields.filter((cropField) => cropField.reachable).length} / ${fields.length}`);
  setText('farmer-reservoir', `${formatNumber(data.reservoir)} L`);
  renderFarmerCropFields(fields);
  const addCropForm = document.getElementById('add-crop-form');
  const cropLimit = document.getElementById('add-crop-limit');
  const addCropButton = addCropForm.querySelector('button[type="submit"]');
  const cropLimitReached = fields.length >= 3;
  addCropButton.disabled = cropLimitReached;
  addCropForm.querySelectorAll('select, input').forEach((input) => {
    input.disabled = cropLimitReached;
  });
  cropLimit.textContent = cropLimitReached
    ? 'You have reached the 3-crop limit for this field.'
    : `${3 - fields.length} additional crop${3 - fields.length === 1 ? '' : 's'} available.`;
  renderRequests(data.requests, 'farmer-request-rows', false, true);
  setText('farmer-total-requests', data.requests.length);
  setText('farmer-pending-requests', data.requests.filter((request) => !request.completed).length);
}

function renderFarmerCropFields(fields) {
  const cards = fields.map((field) => `
    <article class="crop-field-card">
      <div class="crop-field-heading">
        <div>
          <span class="crop-field-label">Field ID</span>
          <h4>${escapeHtml(field.fieldId)}</h4>
        </div>
        <span class="status-badge ${field.reachable ? 'status-good' : 'status-alert'}">${field.reachable ? 'Reachable' : 'Unreachable'}</span>
      </div>
      <dl class="crop-field-details">
        <div><dt>Crop</dt><dd>${escapeHtml(field.cropName)}</dd></div>
        <div><dt>Priority</dt><dd>${field.priority} — ${escapeHtml(field.priorityName)}</dd></div>
        <div><dt>Soil moisture</dt><dd>${formatNumber(field.moisture)}%</dd></div>
      </dl>
      <form class="crop-request-form" data-crop-request data-field-id="${escapeHtml(field.fieldId)}">
        <label>Request type
          <select name="type" required>
            <option value="Routine">Routine</option>
            <option value="Drought Relief">Drought Relief</option>
          </select>
        </label>
        <label>Water quantity (litres)
          <input name="amount" type="number" min="0.01" step="0.01" required />
        </label>
        <button class="button button-primary" type="submit">Request water</button>
      </form>
    </article>
  `).join('');
  document.getElementById('farmer-crop-fields').innerHTML = cards
    || '<p>No crop fields are registered yet.</p>';
}

function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = value;
}

function formatNumber(value) {
  return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[character]));
}
