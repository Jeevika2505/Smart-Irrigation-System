import java.util.*;

class CropProfile {
    private String cropName;
    private int priority;

    public CropProfile(String cropName, int priority) {
        this.cropName = cropName;
        setPriority(priority);
    }

    public String getCropName() {
        return cropName;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        if (priority < 1 || priority > 3) {
            throw new IllegalArgumentException(
                "Priority must be 1, 2, or 3."
            );
        }
        this.priority = priority;
    }

    public String getPriorityName() {
        if (priority == 1) return "High";
        if (priority == 2) return "Medium";
        return "Low";
    }
}

class Field {
    private String fieldId;
    private String farmerName;
    private String password;
    private CropProfile crop;
    private double soilMoisture;

    public Field(String fieldId, String farmerName,
                 String password, CropProfile crop,
                 double soilMoisture) {
        this.fieldId = fieldId.toUpperCase();
        this.farmerName = farmerName;
        this.password = password;
        this.crop = crop;
        setSoilMoisture(soilMoisture);
    }

    public String getFieldId() {
        return fieldId;
    }

    public String getFarmerName() {
        return farmerName;
    }

    public boolean matchesPassword(String password) {
        return this.password.equals(password);
    }

    public CropProfile getCrop() {
        return crop;
    }

    public double getSoilMoisture() {
        return soilMoisture;
    }

    public void setSoilMoisture(double soilMoisture) {
        if (soilMoisture < 0 || soilMoisture > 100) {
            throw new IllegalArgumentException(
                "Soil moisture must be between 0 and 100."
            );
        }
        this.soilMoisture = soilMoisture;
    }

    public void display() {
        System.out.println(
            "Field ID: " + fieldId +
            " | Farmer: " + farmerName +
            " | Crop: " + crop.getCropName() +
            " | Moisture: " + soilMoisture + "%" +
            " | Priority: " + crop.getPriorityName()
        );
    }
}

class Reservoir {
    private double availableWater;

    public Reservoir(double availableWater) {
        setAvailableWater(availableWater);
    }

    public double getAvailableWater() {
        return availableWater;
    }

    public void setAvailableWater(double availableWater) {
        if (availableWater < 0) {
            throw new IllegalArgumentException(
                "Water cannot be negative."
            );
        }
        this.availableWater = availableWater;
    }

    public void addWater(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "Enter a positive amount."
            );
        }
        availableWater += amount;
    }

    public boolean allocateWater(double amount) {
        if (amount <= 0 || amount > availableWater) {
            return false;
        }

        availableWater -= amount;
        return true;
    }
}

abstract class IrrigationRequest {
    private String requestId;
    private Field field;
    private double requestedWater;
    private double allocatedWater;

    public IrrigationRequest(String requestId, Field field,
                             double requestedWater) {
        if (requestedWater <= 0) {
            throw new IllegalArgumentException(
                "Requested water must be greater than zero."
            );
        }

        this.requestId = requestId;
        this.field = field;
        this.requestedWater = requestedWater;
        this.allocatedWater = 0;
    }

    public String getRequestId() {
        return requestId;
    }

    public Field getField() {
        return field;
    }

    public double getRequestedWater() {
        return requestedWater;
    }

    public double getAllocatedWater() {
        return allocatedWater;
    }

    public double getRemainingWater() {
        return requestedWater - allocatedWater;
    }

    public void addAllocation(double amount) {
        if (amount > 0 && amount <= getRemainingWater()) {
            allocatedWater += amount;
        }
    }

    public boolean isCompleted() {
        return getRemainingWater() <= 0.000001;
    }

    public abstract int priorityClass();

    public abstract String getRequestType();
}

class RoutineRequest extends IrrigationRequest {

    public RoutineRequest(String requestId, Field field,
                          double requestedWater) {
        super(requestId, field, requestedWater);
    }

    @Override
    public int priorityClass() {
        return getField().getCrop().getPriority();
    }

    @Override
    public String getRequestType() {
        return "Routine";
    }
}

class DroughtReliefRequest extends IrrigationRequest {

    public DroughtReliefRequest(String requestId, Field field,
                                double requestedWater) {
        super(requestId, field, requestedWater);
    }

    @Override
    public int priorityClass() {
        return 1;
    }

    @Override
    public String getRequestType() {
        return "Drought Relief";
    }
}

class IrrigationPriorityPolicy
        implements Comparator<IrrigationRequest> {

    @Override
    public int compare(IrrigationRequest r1,
                       IrrigationRequest r2) {

        // First: request priority (1 = highest)
        int result = Integer.compare(
            r1.priorityClass(),
            r2.priorityClass()
        );

        if (result != 0) {
            return result;
        }

        // Second: lower soil moisture gets priority
        result = Double.compare(
            r1.getField().getSoilMoisture(),
            r2.getField().getSoilMoisture()
        );

        if (result != 0) {
            return result;
        }

        // Third: request ID order
        return r1.getRequestId().compareTo(r2.getRequestId());
    }
}

class CanalLink {
    private String from;
    private String to;
    private boolean working;

    public CanalLink(String from, String to) {
        this.from = from.toUpperCase();
        this.to = to.toUpperCase();
        this.working = true;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public boolean isWorking() {
        return working;
    }

    public void setWorking(boolean working) {
        this.working = working;
    }
}

class IrrigationNetwork {
    private ArrayList<CanalLink> canals;

    public IrrigationNetwork() {
        canals = new ArrayList<>();
    }

    public void addCanal(String from, String to) {
        canals.add(new CanalLink(from, to));
    }

    public ArrayList<CanalLink> getCanals() {
        return canals;
    }

    public boolean setCanalStatus(int canalNumber,
                                  boolean working) {
        if (canalNumber < 1 || canalNumber > canals.size()) {
            return false;
        }

        canals.get(canalNumber - 1).setWorking(working);
        return true;
    }

    public boolean isReachable(String destination) {
        destination = destination.toUpperCase();

        Queue<String> queue = new LinkedList<>();
        HashSet<String> visited = new HashSet<>();

        queue.add("RESERVOIR");
        visited.add("RESERVOIR");

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(destination)) {
                return true;
            }

            for (CanalLink canal : canals) {
                if (!canal.isWorking()) {
                    continue;
                }

                String next = null;

                if (canal.getFrom().equals(current)) {
                    next = canal.getTo();
                } else if (canal.getTo().equals(current)) {
                    next = canal.getFrom();
                }

                if (next != null && !visited.contains(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }

        return false;
    }

    public void displayCanals() {
        if (canals.isEmpty()) {
            System.out.println("No canals have been added.");
            return;
        }

        for (int i = 0; i < canals.size(); i++) {
            CanalLink canal = canals.get(i);

            System.out.println(
                (i + 1) + ". " +
                canal.getFrom() + " <--> " +
                canal.getTo() + " | " +
                (canal.isWorking() ? "Working" : "Blocked")
            );
        }
    }
}

class IrrigationSystem {
    private Map<String, Field> fieldMap;
    private Reservoir reservoir;
    private IrrigationNetwork network;

    private ArrayList<IrrigationRequest> allRequests;
    private ArrayList<IrrigationRequest> pendingRequests;
    private ArrayList<String> history;

    private int nextRequestNumber;

    public IrrigationSystem() {
        fieldMap = new LinkedHashMap<>();
        reservoir = new Reservoir(10000);
        network = new IrrigationNetwork();

        allRequests = new ArrayList<>();
        pendingRequests = new ArrayList<>();
        history = new ArrayList<>();

        nextRequestNumber = 1;
    }

    // This method fixes the fieldExists() compilation error.
    public boolean fieldExists(String fieldId) {
        if (fieldId == null) {
            return false;
        }

        return fieldMap.containsKey(fieldId.trim().toUpperCase());
    }

    public Field getField(String fieldId) {
        if (fieldId == null) {
            return null;
        }

        return fieldMap.get(fieldId.trim().toUpperCase());
    }

    public boolean addField(String fieldId, String farmerName,
                            String password, String cropName,
                            int priority, double moisture) {
        fieldId = fieldId.trim().toUpperCase();

        if (fieldId.isEmpty() || farmerName.trim().isEmpty()
                || password.isEmpty() || cropName.trim().isEmpty()) {
            return false;
        }

        if (fieldExists(fieldId)) {
            return false;
        }

        CropProfile crop = new CropProfile(cropName, priority);

        Field field = new Field(
            fieldId, farmerName, password, crop, moisture
        );

        fieldMap.put(fieldId, field);
        return true;
    }

    public boolean verifyFarmerPassword(String fieldId,
                                        String password) {
        Field field = getField(fieldId);
        return field != null && field.matchesPassword(password);
    }

    public void displayFields() {
        if (fieldMap.isEmpty()) {
            System.out.println("No fields registered.");
            return;
        }

        for (Field field : fieldMap.values()) {
            field.display();
        }
    }

    public void updateMoisture(String fieldId, double moisture) {
        Field field = getField(fieldId);

        if (field == null) {
            System.out.println("Field not found.");
            return;
        }

        field.setSoilMoisture(moisture);
        System.out.println("Soil moisture updated successfully.");
    }

    public void addReservoirWater(double amount) {
        reservoir.addWater(amount);
        System.out.println("Water added successfully.");
    }

    public void setReservoirWater(double amount) {
        reservoir.setAvailableWater(amount);
        System.out.println("Reservoir water level updated.");
    }

    public void displayReservoir() {
        System.out.println(
            "Available reservoir water: " +
            reservoir.getAvailableWater() + " litres"
        );
    }

    public void addCanal(String from, String to) {
        from = from.trim().toUpperCase();
        to = to.trim().toUpperCase();

        if (from.equals(to)) {
            System.out.println("Canal endpoints must be different.");
            return;
        }

        if (!from.equals("RESERVOIR") && !fieldExists(from)) {
            System.out.println("Source field does not exist.");
            return;
        }

        if (!to.equals("RESERVOIR") && !fieldExists(to)) {
            System.out.println("Destination field does not exist.");
            return;
        }

        network.addCanal(from, to);
        System.out.println("Canal added successfully.");
    }

    public void displayCanals() {
        network.displayCanals();
    }

    public void updateCanalStatus(int number, boolean working) {
        if (network.setCanalStatus(number, working)) {
            System.out.println(
                working ? "Canal restored." : "Canal blocked."
            );
        } else {
            System.out.println("Invalid canal number.");
        }
    }

    public void submitRequest(String fieldId, double water,
                              boolean droughtRelief) {
        Field field = getField(fieldId);

        if (field == null) {
            System.out.println("Field not found.");
            return;
        }

        String requestId = String.format(
            "R%03d", nextRequestNumber++
        );

        IrrigationRequest request;

        if (droughtRelief) {
            request = new DroughtReliefRequest(
                requestId, field, water
            );
        } else {
            request = new RoutineRequest(
                requestId, field, water
            );
        }

        allRequests.add(request);
        pendingRequests.add(request);

        System.out.println(
            "Request submitted successfully. Request ID: " +
            requestId
        );
    }

    public void allocateWater() {
        if (pendingRequests.isEmpty()) {
            System.out.println("No pending requests.");
            return;
        }

        PriorityQueue<IrrigationRequest> queue =
            new PriorityQueue<>(new IrrigationPriorityPolicy());

        queue.addAll(pendingRequests);

        ArrayList<IrrigationRequest> stillPending =
            new ArrayList<>();

        System.out.println("\n--- Water Allocation Results ---");

        while (!queue.isEmpty()) {
            IrrigationRequest request = queue.poll();

            if (reservoir.getAvailableWater() <= 0) {
                stillPending.add(request);

                while (!queue.isEmpty()) {
                    stillPending.add(queue.poll());
                }

                break;
            }

            String fieldId = request.getField().getFieldId();

            if (!network.isReachable(fieldId)) {
                stillPending.add(request);

                history.add(
                    request.getRequestId() +
                    " | Field: " + fieldId +
                    " | Status: Unreachable - Pending"
                );

                System.out.println(
                    request.getRequestId() + " (" + fieldId +
                    "): Field is unreachable. Request remains pending."
                );

                continue;
            }

            double remainingWater = request.getRemainingWater();
            double availableWater = reservoir.getAvailableWater();
            double amount = Math.min(remainingWater, availableWater);

            if (availableWater < remainingWater) {
                amount = Math.min(amount, remainingWater / 2.0);
            }

            if (amount > 0) {
                reservoir.allocateWater(amount);
                request.addAllocation(amount);

                String status = request.isCompleted()
                    ? "Completed" : "Partially Allocated - Pending";

                history.add(
                    request.getRequestId() +
                    " | Field: " + fieldId +
                    " | Allocated: " + amount +
                    " litres | Status: " + status
                );

                System.out.println(
                    request.getRequestId() + " (" + fieldId +
                    "): Allocated " + amount + " litres."
                );
            }

            if (!request.isCompleted()) {
                stillPending.add(request);
            }
        }

        pendingRequests = stillPending;

        System.out.println(
            "\nRemaining reservoir water: " +
            reservoir.getAvailableWater() + " litres"
        );

        System.out.println(
            "Pending requests: " + pendingRequests.size()
        );
    }

    public void displayPendingRequests() {
        if (pendingRequests.isEmpty()) {
            System.out.println("No pending requests.");
            return;
        }

        System.out.println("\n--- Pending Requests ---");

        for (IrrigationRequest request : pendingRequests) {
            System.out.println(
                "Request ID: " + request.getRequestId() +
                " | Field: " + request.getField().getFieldId() +
                " | Type: " + request.getRequestType() +
                " | Priority: " + request.priorityClass() +
                " | Requested: " + request.getRequestedWater() +
                " litres | Allocated: " + request.getAllocatedWater() +
                " litres | Remaining: " + request.getRemainingWater() +
                " litres"
            );
        }
    }

    public void displayHistory() {
        if (history.isEmpty()) {
            System.out.println("No allocation history available.");
            return;
        }

        System.out.println("\n--- Allocation History ---");

        for (String record : history) {
            System.out.println(record);
        }
    }

    public void displayMyRequests(String fieldId) {
        fieldId = fieldId.trim().toUpperCase();

        boolean found = false;

        System.out.println("\n--- Your Irrigation Requests ---");

        for (IrrigationRequest request : allRequests) {
            if (request.getField().getFieldId().equals(fieldId)) {
                found = true;

                String status;

                if (request.isCompleted()) {
                    status = "Completed";
                } else if (request.getAllocatedWater() > 0) {
                    status = "Partially Allocated - Pending";
                } else {
                    status = "Pending";
                }

                if (!network.isReachable(fieldId)) {
                    status += " (Field currently unreachable)";
                }

                System.out.println(
                    "Request ID: " + request.getRequestId() +
                    " | Type: " + request.getRequestType() +
                    " | Requested: " + request.getRequestedWater() +
                    " litres | Allocated: " + request.getAllocatedWater() +
                    " litres | Remaining: " + request.getRemainingWater() +
                    " litres | Status: " + status
                );
            }
        }

        if (!found) {
            System.out.println("You have not submitted any requests.");
        }
    }

    public boolean isReachable(String fieldId) {
        return network.isReachable(fieldId);
    }
}

public class Main {
    private static Scanner sc = new Scanner(System.in);
    private static IrrigationSystem system = new IrrigationSystem();

    public static void main(String[] args) {
        System.out.println(
            "=============================================="
        );
        System.out.println(
            " SMART AGRICULTURAL IRRIGATION SYSTEM"
        );
        System.out.println(
            "=============================================="
        );

        boolean running = true;

        while (running) {
            System.out.println("\n1. Admin Login");
            System.out.println("2. Farmer Login");
            System.out.println("3. Exit");

            int choice = readInt("Enter choice: ");

            switch (choice) {
                case 1:
                    adminLogin();
                    break;

                case 2:
                    farmerLogin();
                    break;

                case 3:
                    running = false;
                    System.out.println("Program closed.");
                    break;

                default:
                    System.out.println("Invalid choice.");
            }
        }

        sc.close();
    }

    private static void adminLogin() {
        System.out.print("Enter admin username: ");
        String username = sc.nextLine().trim();

        System.out.print("Enter admin password: ");
        String password = sc.nextLine();

        if (username.equals("admin")
                && password.equals("admin123")) {
            System.out.println("\nAdmin login successful.");
            adminMenu();
        } else {
            System.out.println("Invalid admin credentials.");
        }
    }

    private static void farmerLogin() {
        System.out.print("Enter your registered Field ID: ");
        String fieldId = sc.nextLine().trim().toUpperCase();

        if (!system.fieldExists(fieldId)) {
            System.out.println(
                "Field not registered. Contact the coordinator."
            );
            return;
        }

        System.out.print("Enter farmer password: ");
        String password = sc.nextLine();

        if (!system.verifyFarmerPassword(fieldId, password)) {
            System.out.println("Incorrect password.");
            return;
        }

        System.out.println("Farmer login successful.");
        farmerMenu(fieldId);
    }

    private static void adminMenu() {
        boolean loggedIn = true;

        while (loggedIn) {
            System.out.println("\n========== ADMIN MENU ==========");
            System.out.println("1. Add Field");
            System.out.println("2. View All Fields");
            System.out.println("3. Update Soil Moisture");
            System.out.println("4. View Reservoir Water");
            System.out.println("5. Add Water to Reservoir");
            System.out.println("6. Set Reservoir Water Level");
            System.out.println("7. Add Canal");
            System.out.println("8. View Canals");
            System.out.println("9. Block or Restore Canal");
            System.out.println("10. Allocate Water");
            System.out.println("11. View Pending Requests");
            System.out.println("12. View Allocation History");
            System.out.println("13. Logout");

            int choice = readInt("Enter choice: ");

            try {
                switch (choice) {
                    case 1:
                        addField();
                        break;

                    case 2:
                        system.displayFields();
                        break;

                    case 3:
                        updateMoisture();
                        break;

                    case 4:
                        system.displayReservoir();
                        break;

                    case 5:
                        double amount = readDouble(
                            "Enter water amount to add (litres): "
                        );

                        system.addReservoirWater(amount);
                        break;

                    case 6:
                        double newLevel = readDouble(
                            "Enter new available water level (litres): "
                        );

                        system.setReservoirWater(newLevel);
                        break;

                    case 7:
                        addCanal();
                        break;

                    case 8:
                        system.displayCanals();
                        break;

                    case 9:
                        updateCanal();
                        break;

                    case 10:
                        system.allocateWater();
                        break;

                    case 11:
                        system.displayPendingRequests();
                        break;

                    case 12:
                        system.displayHistory();
                        break;

                    case 13:
                        loggedIn = false;
                        System.out.println("Admin logged out.");
                        break;

                    default:
                        System.out.println("Invalid choice.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void farmerMenu(String fieldId) {
        boolean loggedIn = true;

        while (loggedIn) {
            System.out.println("\n========== FARMER MENU ==========");
            System.out.println("1. View My Field");
            System.out.println("2. Submit Routine Irrigation Request");
            System.out.println("3. Submit Drought Relief Request");
            System.out.println("4. View My Request Status");
            System.out.println("5. View Reservoir Water");
            System.out.println("6. Logout");

            int choice = readInt("Enter choice: ");

            try {
                switch (choice) {
                    case 1:
                        Field field = system.getField(fieldId);

                        if (field != null) {
                            field.display();

                            System.out.println(
                                "Reachable from reservoir: " +
                                (system.isReachable(fieldId) ? "Yes" : "No")
                            );
                        }
                        break;

                    case 2:
                        submitFarmerRequest(fieldId, false);
                        break;

                    case 3:
                        submitFarmerRequest(fieldId, true);
                        break;

                    case 4:
                        system.displayMyRequests(fieldId);
                        break;

                    case 5:
                        system.displayReservoir();
                        break;

                    case 6:
                        loggedIn = false;
                        System.out.println("Farmer logged out.");
                        break;

                    default:
                        System.out.println("Invalid choice.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void addField() {
        System.out.print("Enter Field ID: ");
        String fieldId = sc.nextLine().trim();

        System.out.print("Enter Farmer Name: ");
        String farmerName = sc.nextLine().trim();

        System.out.print("Set farmer password: ");
        String password = sc.nextLine();

        System.out.print("Enter Crop Name: ");
        String cropName = sc.nextLine().trim();

        System.out.println("Crop Priority:");
        System.out.println("1. High");
        System.out.println("2. Medium");
        System.out.println("3. Low");

        int priority = readInt("Enter priority: ");

        double moisture = readDouble(
            "Enter soil moisture percentage (0-100): "
        );

        if (system.addField(
            fieldId, farmerName, password, cropName,
            priority, moisture)) {
            System.out.println("Field registered successfully.");
        } else {
            System.out.println(
                "Could not add field. Check details or duplicate ID."
            );
        }
    }

    private static void updateMoisture() {
        System.out.print("Enter Field ID: ");
        String fieldId = sc.nextLine().trim();

        double moisture = readDouble(
            "Enter new soil moisture percentage: "
        );

        system.updateMoisture(fieldId, moisture);
    }

    private static void addCanal() {
        System.out.println(
            "Use RESERVOIR as the reservoir endpoint."
        );
        System.out.println(
            "Example: RESERVOIR to F01, or F01 to F02."
        );

        System.out.print("Enter starting node: ");
        String from = sc.nextLine().trim();

        System.out.print("Enter ending node: ");
        String to = sc.nextLine().trim();

        system.addCanal(from, to);
    }

    private static void updateCanal() {
        system.displayCanals();

        int number = readInt("Enter canal number: ");

        System.out.println("1. Block Canal");
        System.out.println("2. Restore Canal");

        int action = readInt("Enter choice: ");

        if (action == 1) {
            system.updateCanalStatus(number, false);
        } else if (action == 2) {
            system.updateCanalStatus(number, true);
        } else {
            System.out.println("Invalid choice.");
        }
    }

    private static void submitFarmerRequest(String fieldId,
                                            boolean droughtRelief) {
        double water = readDouble(
            "Enter required water amount (litres): "
        );

        system.submitRequest(fieldId, water, droughtRelief);
    }

    private static int readInt(String message) {
        while (true) {
            System.out.print(message);
            String input = sc.nextLine().trim();

            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    private static double readDouble(String message) {
        while (true) {
            System.out.print(message);
            String input = sc.nextLine().trim();

            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}