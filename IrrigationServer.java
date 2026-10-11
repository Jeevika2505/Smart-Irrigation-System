import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class IrrigationServer {
    private static final int PORT = 8080;
    private static final Path ROOT = Paths.get(".").toAbsolutePath().normalize();
    private static final Path DATA_FILE = ROOT.resolve("irrigation-data.ser");
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static State state;

    public static void main(String[] args) throws Exception {
        state = loadState();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/api/", IrrigationServer::handleApi);
        server.createContext("/", IrrigationServer::serveStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("AgriFlow running at http://localhost:" + PORT);
    }

    private static void handleApi(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (isLocalOrigin(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        }
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            Map<String, String> input = readInput(exchange);
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            Object result;

            if ("/api/login".equals(path) && "POST".equals(method)) {
                result = login(input);
            } else if ("/api/logout".equals(path) && "POST".equals(method)) {
                Session session = requireSession(exchange, null);
                SESSIONS.remove(session.token);
                result = map("message", "Logged out.");
            } else if (path.startsWith("/api/admin/")) {
                requireSession(exchange, "admin");
                result = handleAdmin(path.substring("/api/admin/".length()), method, input);
            } else if (path.startsWith("/api/farmer/")) {
                Session session = requireSession(exchange, "farmer");
                result = handleFarmer(path.substring("/api/farmer/".length()), method, input, session);
            } else {
                throw new ApiException(404, "API endpoint not found.");
            }
            sendJson(exchange, 200, result);
        } catch (ApiException exception) {
            sendJson(exchange, exception.status, map("error", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, map("error", exception.getMessage()));
        } catch (Exception exception) {
            exception.printStackTrace();
            sendJson(exchange, 500, map("error", "An internal server error occurred."));
        }
    }

    private static Object login(Map<String, String> input) {
        String role = value(input, "role");
        String token = UUID.randomUUID().toString();
        if ("admin".equals(role)) {
            if (!"admin".equals(input.get("username")) || !"admin123".equals(input.get("password"))) {
                throw new ApiException(401, "Incorrect admin username or password.");
            }
            SESSIONS.put(token, new Session(token, "admin", null));
        } else if ("farmer".equals(role)) {
            String fieldId = normalizeId(input.get("fieldId"));
            Field field = state.fields.get(fieldId);
            if (field == null || !field.matchesPassword(input.get("password"))) {
                throw new ApiException(401, "Invalid field ID or farmer password.");
            }
            SESSIONS.put(token, new Session(token, "farmer", fieldId));
        } else {
            throw new ApiException(400, "Choose admin or farmer login.");
        }
        return map("token", token, "role", role);
    }

    private static Object handleAdmin(String action, String method, Map<String, String> input) {
        synchronized (state) {
            switch (action) {
                case "overview":
                    requireMethod(method, "GET");
                    return adminOverview();
                case "fields":
                    requireMethod(method, "POST");
                    addField(input);
                    return map("message", "Field registered.");
                case "moisture":
                    requireMethod(method, "POST");
                    Field field = requireField(input.get("fieldId"));
                    double moisture = number(input, "moisture");
                    if (moisture < 0 || moisture > 100) throw new IllegalArgumentException("Soil moisture must be between 0 and 100.");
                    field.moisture = moisture;
                    saveState();
                    return map("message", "Soil moisture updated.");
                case "reservoir/add":
                    requireMethod(method, "POST");
                    double addAmount = number(input, "amount");
                    if (addAmount <= 0) throw new IllegalArgumentException("Enter a positive water quantity.");
                    state.reservoir += addAmount;
                    saveState();
                    return map("message", "Water added to reservoir.");
                case "reservoir/set":
                    requireMethod(method, "POST");
                    double newLevel = number(input, "amount");
                    if (newLevel < 0) throw new IllegalArgumentException("Reservoir water cannot be negative.");
                    state.reservoir = newLevel;
                    saveState();
                    return map("message", "Reservoir level updated.");
                case "canals":
                    requireMethod(method, "POST");
                    String from = normalizeNode(value(input, "from"));
                    String to = normalizeNode(value(input, "to"));
                    if (from.equals(to)) throw new IllegalArgumentException("Canal endpoints must be different.");
                    if (!"RESERVOIR".equals(from) && !state.fields.containsKey(from)) throw new IllegalArgumentException("Source field does not exist.");
                    if (!"RESERVOIR".equals(to) && !state.fields.containsKey(to)) throw new IllegalArgumentException("Destination field does not exist.");
                    state.canals.add(new Canal(from, to));
                    saveState();
                    return map("message", "Canal added.");
                case "canals/status":
                    requireMethod(method, "POST");
                    int canalNumber = integer(input, "number");
                    boolean working = Boolean.parseBoolean(value(input, "working"));
                    if (canalNumber < 1 || canalNumber > state.canals.size()) throw new IllegalArgumentException("Invalid canal number.");
                    state.canals.get(canalNumber - 1).working = working;
                    saveState();
                    return map("message", working ? "Canal restored." : "Canal blocked.");
                case "allocate":
                    requireMethod(method, "POST");
                    allocateWater();
                    return adminOverview();
                default:
                    throw new ApiException(404, "Admin endpoint not found.");
            }
        }
    }

    private static Object handleFarmer(String action, String method, Map<String, String> input, Session session) {
        synchronized (state) {
            if ("me".equals(action) && "GET".equals(method)) {
                Field field = state.fields.get(session.fieldId);
                List<Object> ownRequests = new ArrayList<>();
                for (IrrigationRequest request : state.requests) {
                    if (request.fieldId.equals(session.fieldId)) ownRequests.add(requestView(request));
                }
                return map("field", fieldView(field), "requests", ownRequests, "reservoir", state.reservoir,
                    "reachable", isReachable(session.fieldId));
            }
            if ("request".equals(action) && "POST".equals(method)) {
                double amount = number(input, "amount");
                if (amount <= 0) throw new IllegalArgumentException("Requested water must be greater than zero.");
                String type = value(input, "type");
                if (!"Routine".equals(type) && !"Drought Relief".equals(type)) throw new IllegalArgumentException("Invalid request type.");
                String id = String.format(Locale.ROOT, "R%03d", state.nextRequestNumber++);
                state.requests.add(new IrrigationRequest(id, session.fieldId, amount, type));
                saveState();
                return map("message", "Irrigation request submitted.", "requestId", id);
            }
            throw new ApiException(404, "Farmer endpoint not found.");
        }
    }

    private static void addField(Map<String, String> input) {
        String id = normalizeId(value(input, "fieldId"));
        String farmerName = value(input, "farmerName").trim();
        String password = input.getOrDefault("password", "");
        String cropName = value(input, "cropName").trim();
        int priority = integer(input, "priority");
        double moisture = number(input, "moisture");
        if (id.isEmpty() || farmerName.isEmpty() || password.isEmpty() || cropName.isEmpty()) {
            throw new IllegalArgumentException("Complete all field details.");
        }
        if (state.fields.containsKey(id)) throw new IllegalArgumentException("This Field ID is already registered.");
        if (priority < 1 || priority > 3) throw new IllegalArgumentException("Crop priority must be 1, 2, or 3.");
        if (moisture < 0 || moisture > 100) throw new IllegalArgumentException("Soil moisture must be between 0 and 100.");
        state.fields.put(id, new Field(id, farmerName, password, cropName, priority, moisture));
        saveState();
    }

    private static Object adminOverview() {
        List<Object> fields = new ArrayList<>();
        for (Field field : state.fields.values()) {
            Map<String, Object> view = fieldView(field);
            view.put("reachable", isReachable(field.id));
            fields.add(view);
        }
        List<Object> requests = new ArrayList<>();
        List<Object> pending = new ArrayList<>();
        for (IrrigationRequest request : state.requests) {
            Map<String, Object> view = requestView(request);
            requests.add(view);
            if (!request.completed()) pending.add(view);
        }
        List<Object> canals = new ArrayList<>();
        for (int i = 0; i < state.canals.size(); i++) {
            Canal canal = state.canals.get(i);
            canals.add(map("number", i + 1, "from", canal.from, "to", canal.to, "working", canal.working));
        }
        return map("fields", fields, "reservoir", state.reservoir, "requests", requests,
            "pendingRequests", pending, "canals", canals, "history", new ArrayList<>(state.history));
    }

    private static Map<String, Object> fieldView(Field field) {
        return map("fieldId", field.id, "farmerName", field.farmerName, "cropName", field.cropName,
            "priority", field.priority, "priorityName", priorityName(field.priority), "moisture", field.moisture);
    }

    private static Map<String, Object> requestView(IrrigationRequest request) {
        Field field = state.fields.get(request.fieldId);
        String status = request.completed() ? "Completed" : request.allocated > 0 ? "Partially Allocated - Pending" : "Pending";
        if (!request.completed() && !isReachable(request.fieldId)) status += " (Field currently unreachable)";
        return map("requestId", request.id, "fieldId", request.fieldId, "farmerName", field == null ? "" : field.farmerName,
            "type", request.type, "priority", "Drought Relief".equals(request.type) ? 1 : field.priority,
            "requested", request.requested, "allocated", request.allocated,
            "remaining", request.requested - request.allocated, "status", status, "completed", request.completed());
    }

    private static void allocateWater() {
        List<IrrigationRequest> pending = new ArrayList<>();
        for (IrrigationRequest request : state.requests) if (!request.completed()) pending.add(request);
        pending.sort((left, right) -> {
            int result = Integer.compare(requestPriority(left), requestPriority(right));
            if (result != 0) return result;
            result = Double.compare(state.fields.get(left.fieldId).moisture, state.fields.get(right.fieldId).moisture);
            return result != 0 ? result : left.id.compareTo(right.id);
        });
        for (IrrigationRequest request : pending) {
            if (state.reservoir <= 0) break;
            if (!isReachable(request.fieldId)) {
                state.history.add(request.id + " | Field: " + request.fieldId + " | Status: Unreachable - Pending");
                continue;
            }
            double remaining = request.requested - request.allocated;
            double amount = Math.min(remaining, state.reservoir);
            if (state.reservoir < remaining) amount = Math.min(amount, remaining / 2.0);
            if (amount <= 0) continue;
            state.reservoir -= amount;
            request.allocated += amount;
            String status = request.completed() ? "Completed" : "Partially Allocated - Pending";
            state.history.add(request.id + " | Field: " + request.fieldId + " | Allocated: " +
                amount + " litres | Status: " + status);
        }
        saveState();
    }

    private static int requestPriority(IrrigationRequest request) {
        return "Drought Relief".equals(request.type) ? 1 : state.fields.get(request.fieldId).priority;
    }

    private static boolean isReachable(String destination) {
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        queue.add("RESERVOIR");
        visited.add("RESERVOIR");
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(destination)) return true;
            for (Canal canal : state.canals) {
                if (!canal.working) continue;
                String next = canal.from.equals(current) ? canal.to : canal.to.equals(current) ? canal.from : null;
                if (next != null && visited.add(next)) queue.add(next);
            }
        }
        return false;
    }

    private static Session requireSession(HttpExchange exchange, String requiredRole) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
        Session session = SESSIONS.get(token);
        if (session == null) throw new ApiException(401, "Please log in again.");
        if (requiredRole != null && !requiredRole.equals(session.role)) throw new ApiException(403, "You are not authorized to access this page.");
        return session;
    }

    private static Map<String, String> readInput(HttpExchange exchange) throws IOException {
        Map<String, String> values = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null) parseForm(query, values);
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length > 0) parseForm(new String(bytes, StandardCharsets.UTF_8), values);
        return values;
    }

    private static void parseForm(String encoded, Map<String, String> values) throws UnsupportedEncodingException {
        for (String pair : encoded.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "";
            values.put(key, value);
        }
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if ("/".equals(path)) path = "/index.html";
        Path file = ROOT.resolve("." + path).normalize();
        if (!file.startsWith(ROOT) || !Files.isRegularFile(file)) {
            byte[] notFound = "Not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, notFound.length);
            exchange.getResponseBody().write(notFound);
            exchange.close();
            return;
        }
        String name = file.getFileName().toString();
        String contentType = name.endsWith(".html") ? "text/html; charset=utf-8"
            : name.endsWith(".css") ? "text/css; charset=utf-8"
            : name.endsWith(".js") ? "application/javascript; charset=utf-8" : "application/octet-stream";
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = Files.readAllBytes(file);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = json(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escape((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (Object raw : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) raw;
                joiner.add(json(entry.getKey().toString()) + ":" + json(entry.getValue()));
            }
            return joiner.toString();
        }
        if (value instanceof Iterable) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (Object item : (Iterable<?>) value) joiner.add(json(item));
            return joiner.toString();
        }
        return json(value.toString());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static State loadState() {
        if (!Files.exists(DATA_FILE)) return new State();
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(DATA_FILE))) {
            return (State) input.readObject();
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot load saved irrigation data.", exception);
        }
    }

    private static void saveState() {
        Path temporaryFile = DATA_FILE.resolveSibling(DATA_FILE.getFileName() + ".tmp");
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(temporaryFile))) {
            output.writeObject(state);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save irrigation data.", exception);
        }
        try {
            Files.move(temporaryFile, DATA_FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            try {
                Files.move(temporaryFile, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveException) {
                throw new IllegalStateException("Cannot save irrigation data.", moveException);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save irrigation data.", exception);
        }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    private static Field requireField(String id) {
        Field field = state.fields.get(normalizeId(id));
        if (field == null) throw new IllegalArgumentException("Field not found.");
        return field;
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeNode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String value(Map<String, String> input, String key) {
        return input.getOrDefault(key, "").trim();
    }

    private static double number(Map<String, String> input, String key) {
        try {
            double value = Double.parseDouble(value(input, key));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid number for " + key + ".");
        }
    }

    private static int integer(Map<String, String> input, String key) {
        try {
            return Integer.parseInt(value(input, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid whole number for " + key + ".");
        }
    }

    private static String priorityName(int priority) {
        return priority == 1 ? "High" : priority == 2 ? "Medium" : "Low";
    }

    private static byte[] passwordHash(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Password hashing is unavailable.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private static void requireMethod(String actual, String expected) {
        if (!expected.equals(actual)) throw new ApiException(405, "Method not allowed.");
    }

    private static boolean isLocalOrigin(String origin) {
        if (origin == null) return false;
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            return "http".equals(uri.getScheme()) &&
                ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static final class ApiException extends RuntimeException {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }

    private static final class Session {
        final String token;
        final String role;
        final String fieldId;
        Session(String token, String role, String fieldId) { this.token = token; this.role = role; this.fieldId = fieldId; }
    }

    private static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        final LinkedHashMap<String, Field> fields = new LinkedHashMap<>();
        final ArrayList<Canal> canals = new ArrayList<>();
        final ArrayList<IrrigationRequest> requests = new ArrayList<>();
        final ArrayList<String> history = new ArrayList<>();
        double reservoir = 10000;
        int nextRequestNumber = 1;
    }

    private static final class Field implements Serializable {
        private static final long serialVersionUID = 1L;
        final String id;
        final String farmerName;
        final byte[] passwordSalt;
        final byte[] passwordHash;
        final String cropName;
        final int priority;
        double moisture;
        Field(String id, String farmerName, String password, String cropName, int priority, double moisture) {
            this.id = id; this.farmerName = farmerName;
            this.passwordSalt = new byte[16];
            new SecureRandom().nextBytes(this.passwordSalt);
            this.passwordHash = IrrigationServer.passwordHash(password, this.passwordSalt);
            this.cropName = cropName; this.priority = priority; this.moisture = moisture;
        }
        boolean matchesPassword(String password) {
            return password != null && MessageDigest.isEqual(passwordHash, IrrigationServer.passwordHash(password, passwordSalt));
        }
    }

    private static final class Canal implements Serializable {
        private static final long serialVersionUID = 1L;
        final String from;
        final String to;
        boolean working = true;
        Canal(String from, String to) { this.from = from; this.to = to; }
    }

    private static final class IrrigationRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        final String id;
        final String fieldId;
        final double requested;
        final String type;
        double allocated;
        IrrigationRequest(String id, String fieldId, double requested, String type) {
            this.id = id; this.fieldId = fieldId; this.requested = requested; this.type = type;
        }
        boolean completed() { return requested - allocated <= 0.000001; }
    }
}
