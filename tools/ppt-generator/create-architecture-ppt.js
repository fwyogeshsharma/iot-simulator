const pptxgen = require("pptxgenjs");

const pptx = new pptxgen();

// Set presentation properties
pptx.author = "SymbIOT Team";
pptx.title = "SymbIOT System Architecture";
pptx.subject = "How the Simulator fits in the overall system";
pptx.company = "SymbIOT";

// Define colors
const colors = {
    dark: "1a1a2e",
    primary: "e94560",
    secondary: "0f4c75",
    accent: "3282b8",
    light: "bbe1fa",
    white: "FFFFFF",
    green: "27ae60",
    orange: "f39c12",
    purple: "9b59b6",
    teal: "1abc9c"
};

// Slide 1: Title Slide
let slide1 = pptx.addSlide();
slide1.background = { color: colors.dark };
slide1.addText("SymbIOT System Architecture", {
    x: 0.5, y: 2, w: "90%", h: 1.2,
    fontSize: 48, bold: true, color: colors.primary,
    align: "center"
});
slide1.addText("How the IoT Simulator Integrates with the SymbIOT Platform", {
    x: 0.5, y: 3.3, w: "90%", h: 0.6,
    fontSize: 22, color: colors.accent,
    align: "center"
});
slide1.addText("Elderly Care Monitoring System", {
    x: 0.5, y: 4.2, w: "90%", h: 0.5,
    fontSize: 18, color: colors.light,
    align: "center"
});

// Slide 2: High-Level Overview
let slide2 = pptx.addSlide();
slide2.background = { color: colors.dark };
slide2.addText("High-Level System Overview", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

// Main SymbIOT Box
slide2.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.3, y: 1, w: 4.2, h: 3.8,
    fill: { color: colors.secondary },
    line: { color: colors.accent, width: 3 }
});
slide2.addText("SymbIOT Platform", {
    x: 0.3, y: 1.1, w: 4.2, h: 0.4,
    fontSize: 16, bold: true, color: colors.primary,
    align: "center"
});
slide2.addText("(Main Application)", {
    x: 0.3, y: 1.45, w: 4.2, h: 0.3,
    fontSize: 11, color: colors.light,
    align: "center"
});

// SymbIOT components inside
slide2.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 1.9, w: 3.8, h: 0.6,
    fill: { color: colors.teal }
});
slide2.addText("React Frontend (symbiot-care-peace)", {
    x: 0.5, y: 2, w: 3.8, h: 0.4,
    fontSize: 11, color: colors.white,
    align: "center"
});

slide2.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 2.7, w: 3.8, h: 0.6,
    fill: { color: colors.green }
});
slide2.addText("Supabase Edge Functions", {
    x: 0.5, y: 2.8, w: 3.8, h: 0.4,
    fontSize: 11, color: colors.white,
    align: "center"
});

slide2.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 3.5, w: 3.8, h: 0.6,
    fill: { color: colors.purple }
});
slide2.addText("Supabase PostgreSQL Database", {
    x: 0.5, y: 3.6, w: 3.8, h: 0.4,
    fontSize: 11, color: colors.white,
    align: "center"
});

slide2.addText("Caretaker monitors elderly\nvia dashboard & notifications", {
    x: 0.5, y: 4.2, w: 3.8, h: 0.5,
    fontSize: 9, color: colors.light,
    align: "center"
});

// Simulator Box
slide2.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.5, y: 1, w: 4.2, h: 3.8,
    fill: { color: "2c3e50" },
    line: { color: colors.orange, width: 3 }
});
slide2.addText("IoT Simulator", {
    x: 5.5, y: 1.1, w: 4.2, h: 0.4,
    fontSize: 16, bold: true, color: colors.orange,
    align: "center"
});
slide2.addText("(Testing & Demo Tool)", {
    x: 5.5, y: 1.45, w: 4.2, h: 0.3,
    fontSize: 11, color: colors.light,
    align: "center"
});

// Simulator components inside
slide2.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.7, y: 1.9, w: 3.8, h: 0.6,
    fill: { color: colors.teal }
});
slide2.addText("Angular Frontend (Port 4200)", {
    x: 5.7, y: 2, w: 3.8, h: 0.4,
    fontSize: 11, color: colors.white,
    align: "center"
});

slide2.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.7, y: 2.7, w: 3.8, h: 0.6,
    fill: { color: colors.green }
});
slide2.addText("Java/Spring Boot Backend (Port 3000)", {
    x: 5.7, y: 2.8, w: 3.8, h: 0.4,
    fontSize: 11, color: colors.white,
    align: "center"
});

slide2.addText("Generates fake sensor data\nfor testing & demonstrations", {
    x: 5.7, y: 3.5, w: 3.8, h: 0.5,
    fontSize: 9, color: colors.light,
    align: "center"
});

// Arrow from Simulator to SymbIOT
slide2.addText("Sends simulated\ndevice data", {
    x: 4.4, y: 2.3, w: 1.2, h: 0.5,
    fontSize: 9, color: colors.primary,
    align: "center"
});
slide2.addShape(pptx.shapes.RIGHT_ARROW, {
    x: 4.5, y: 2.9, w: 1, h: 0.3,
    fill: { color: colors.primary },
    rotate: 180
});

// Slide 3: Detailed Data Flow
let slide3 = pptx.addSlide();
slide3.background = { color: colors.dark };
slide3.addText("Detailed Data Flow Architecture", {
    x: 0.5, y: 0.2, w: "90%", h: 0.5,
    fontSize: 28, bold: true, color: colors.primary,
    align: "center"
});

// Layer labels
slide3.addText("SIMULATOR LAYER", {
    x: 0.2, y: 0.8, w: 2, h: 0.3,
    fontSize: 10, bold: true, color: colors.orange
});
slide3.addText("API LAYER", {
    x: 0.2, y: 2.1, w: 2, h: 0.3,
    fontSize: 10, bold: true, color: colors.green
});
slide3.addText("DATA LAYER", {
    x: 0.2, y: 3.4, w: 2, h: 0.3,
    fontSize: 10, bold: true, color: colors.purple
});
slide3.addText("CONSUMER LAYER", {
    x: 0.2, y: 4.3, w: 2, h: 0.3,
    fontSize: 10, bold: true, color: colors.teal
});

// Simulator Angular Frontend
slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 2.5, y: 0.75, w: 2.5, h: 0.8,
    fill: { color: colors.orange }
});
slide3.addText("Simulator UI\n(Angular)", {
    x: 2.5, y: 0.85, w: 2.5, h: 0.6,
    fontSize: 11, color: colors.white,
    align: "center"
});

// Simulator Backend
slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.5, y: 0.75, w: 2.5, h: 0.8,
    fill: { color: colors.orange }
});
slide3.addText("Simulator Backend\n(Java/Spring Boot)", {
    x: 5.5, y: 0.85, w: 2.5, h: 0.6,
    fontSize: 11, color: colors.white,
    align: "center"
});

// Arrow between simulator components
slide3.addShape(pptx.shapes.RIGHT_ARROW, {
    x: 5, y: 1, w: 0.5, h: 0.25,
    fill: { color: colors.light }
});

// Supabase REST API
slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 2.5, y: 2, w: 2.5, h: 0.8,
    fill: { color: colors.green }
});
slide3.addText("Supabase REST API\n(/rest/v1/*)", {
    x: 2.5, y: 2.1, w: 2.5, h: 0.6,
    fontSize: 11, color: colors.white,
    align: "center"
});

// Device Ingest Function
slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.5, y: 2, w: 2.5, h: 0.8,
    fill: { color: colors.green }
});
slide3.addText("device-ingest\n(Edge Function)", {
    x: 5.5, y: 2.1, w: 2.5, h: 0.6,
    fontSize: 11, color: colors.white,
    align: "center"
});

// Arrows to API layer
slide3.addShape(pptx.shapes.DOWN_ARROW, {
    x: 3.6, y: 1.55, w: 0.25, h: 0.45,
    fill: { color: colors.light }
});
slide3.addShape(pptx.shapes.DOWN_ARROW, {
    x: 6.6, y: 1.55, w: 0.25, h: 0.45,
    fill: { color: colors.light }
});

// Database tables
slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 1.5, y: 3.2, w: 2, h: 0.7,
    fill: { color: colors.purple }
});
slide3.addText("profiles\ndevices", {
    x: 1.5, y: 3.3, w: 2, h: 0.5,
    fontSize: 10, color: colors.white,
    align: "center"
});

slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 4, y: 3.2, w: 2, h: 0.7,
    fill: { color: colors.purple }
});
slide3.addText("device_types\ndata_configs", {
    x: 4, y: 3.3, w: 2, h: 0.5,
    fontSize: 10, color: colors.white,
    align: "center"
});

slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 6.5, y: 3.2, w: 2, h: 0.7,
    fill: { color: colors.purple }
});
slide3.addText("device_data\nalerts", {
    x: 6.5, y: 3.3, w: 2, h: 0.5,
    fontSize: 10, color: colors.white,
    align: "center"
});

// Arrows to database
slide3.addShape(pptx.shapes.DOWN_ARROW, {
    x: 3.6, y: 2.8, w: 0.25, h: 0.4,
    fill: { color: colors.light }
});
slide3.addShape(pptx.shapes.DOWN_ARROW, {
    x: 6.6, y: 2.8, w: 0.25, h: 0.4,
    fill: { color: colors.light }
});

// SymbIOT Frontend (Consumer)
slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 3.5, y: 4.2, w: 3, h: 0.8,
    fill: { color: colors.teal }
});
slide3.addText("SymbIOT Dashboard (React)\nCaretaker views real-time data", {
    x: 3.5, y: 4.3, w: 3, h: 0.6,
    fontSize: 11, color: colors.white,
    align: "center"
});

// Arrow from DB to Consumer
slide3.addShape(pptx.shapes.DOWN_ARROW, {
    x: 4.9, y: 3.9, w: 0.25, h: 0.3,
    fill: { color: colors.light }
});

// Annotations
slide3.addText("GET profiles,\ndevices, configs", {
    x: 2.5, y: 1.6, w: 1.5, h: 0.4,
    fontSize: 8, color: colors.accent
});
slide3.addText("POST simulated\nsensor data", {
    x: 6.5, y: 1.6, w: 1.5, h: 0.4,
    fontSize: 8, color: colors.accent
});

// Slide 4: Simulator Internal Architecture
let slide4 = pptx.addSlide();
slide4.background = { color: colors.dark };
slide4.addText("Simulator Internal Architecture", {
    x: 0.5, y: 0.2, w: "90%", h: 0.5,
    fontSize: 28, bold: true, color: colors.primary,
    align: "center"
});

// Frontend section
slide4.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.3, y: 0.9, w: 4.4, h: 2.2,
    fill: { color: "1e3a5f" },
    line: { color: colors.accent, width: 2 }
});
slide4.addText("Angular Frontend (Port 4200)", {
    x: 0.3, y: 0.95, w: 4.4, h: 0.35,
    fontSize: 14, bold: true, color: colors.accent,
    align: "center"
});

const frontendComponents = [
    "Profile Selection (Dropdown)",
    "Device Multi-Select (Checkboxes)",
    "Data Type Configuration",
    "Simulation Controls (Start/Stop)",
    "Real-time Statistics Dashboard"
];
frontendComponents.forEach((comp, i) => {
    slide4.addText("• " + comp, {
        x: 0.5, y: 1.35 + (i * 0.32), w: 4, h: 0.3,
        fontSize: 11, color: colors.light
    });
});

// Backend section
slide4.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.3, y: 0.9, w: 4.4, h: 2.2,
    fill: { color: "1e3a5f" },
    line: { color: colors.green, width: 2 }
});
slide4.addText("Java/Spring Boot Backend (Port 3000)", {
    x: 5.3, y: 0.95, w: 4.4, h: 0.35,
    fontSize: 14, bold: true, color: colors.green,
    align: "center"
});

const backendComponents = [
    "SimulatorController (REST APIs)",
    "SimulatorService (Device/Config Mgmt)",
    "SimulationManager (Thread Pool)",
    "Data Generators (Per Device Type)",
    "Supabase Client (HTTP Requests)"
];
backendComponents.forEach((comp, i) => {
    slide4.addText("• " + comp, {
        x: 5.5, y: 1.35 + (i * 0.32), w: 4, h: 0.3,
        fontSize: 11, color: colors.light
    });
});

// Arrow between frontend and backend
slide4.addShape(pptx.shapes.RIGHT_ARROW, {
    x: 4.7, y: 1.9, w: 0.6, h: 0.25,
    fill: { color: colors.primary }
});
slide4.addText("HTTP", {
    x: 4.7, y: 1.65, w: 0.6, h: 0.25,
    fontSize: 9, color: colors.light,
    align: "center"
});

// API Endpoints
slide4.addText("Backend API Endpoints:", {
    x: 0.3, y: 3.3, w: 9.4, h: 0.35,
    fontSize: 14, bold: true, color: colors.accent
});

const endpoints = [
    ["GET", "/api/devices/{elderlyPersonId}", "Get all devices for an elderly person"],
    ["GET", "/api/data-types/{deviceId}", "Get data type configs for a device"],
    ["POST", "/api/simulation/start", "Start simulation with selected devices"],
    ["POST", "/api/simulation/stop", "Stop running simulation"],
    ["GET", "/api/simulation/status/{id}", "Get simulation status"],
    ["GET", "/api/simulation/statistics/{id}", "Get real-time statistics"]
];

endpoints.forEach((ep, i) => {
    slide4.addText(ep[0], {
        x: 0.4, y: 3.7 + (i * 0.32), w: 0.6, h: 0.28,
        fontSize: 9, bold: true, color: ep[0] === "GET" ? colors.green : colors.orange
    });
    slide4.addText(ep[1], {
        x: 1.0, y: 3.7 + (i * 0.32), w: 3.5, h: 0.28,
        fontSize: 9, color: colors.light, fontFace: "Courier New"
    });
    slide4.addText(ep[2], {
        x: 4.6, y: 3.7 + (i * 0.32), w: 5, h: 0.28,
        fontSize: 9, color: colors.accent
    });
});

// Slide 5: Data Generation Flow
let slide5 = pptx.addSlide();
slide5.background = { color: colors.dark };
slide5.addText("Data Generation Flow", {
    x: 0.5, y: 0.2, w: "90%", h: 0.5,
    fontSize: 28, bold: true, color: colors.primary,
    align: "center"
});

// Step 1
slide5.addShape(pptx.shapes.OVAL, {
    x: 0.3, y: 0.9, w: 0.5, h: 0.5,
    fill: { color: colors.primary }
});
slide5.addText("1", {
    x: 0.3, y: 0.98, w: 0.5, h: 0.35,
    fontSize: 18, bold: true, color: colors.white,
    align: "center"
});
slide5.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.9, y: 0.85, w: 8.8, h: 0.6,
    fill: { color: colors.secondary }
});
slide5.addText("User selects elderly person, devices, and data types in Angular UI", {
    x: 1, y: 0.95, w: 8.6, h: 0.4,
    fontSize: 12, color: colors.light
});

// Step 2
slide5.addShape(pptx.shapes.OVAL, {
    x: 0.3, y: 1.6, w: 0.5, h: 0.5,
    fill: { color: colors.primary }
});
slide5.addText("2", {
    x: 0.3, y: 1.68, w: 0.5, h: 0.35,
    fontSize: 18, bold: true, color: colors.white,
    align: "center"
});
slide5.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.9, y: 1.55, w: 8.8, h: 0.6,
    fill: { color: colors.secondary }
});
slide5.addText("Frontend calls POST /api/simulation/start with device IDs and configurations", {
    x: 1, y: 1.65, w: 8.6, h: 0.4,
    fontSize: 12, color: colors.light
});

// Step 3
slide5.addShape(pptx.shapes.OVAL, {
    x: 0.3, y: 2.3, w: 0.5, h: 0.5,
    fill: { color: colors.primary }
});
slide5.addText("3", {
    x: 0.3, y: 2.38, w: 0.5, h: 0.35,
    fontSize: 18, bold: true, color: colors.white,
    align: "center"
});
slide5.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.9, y: 2.25, w: 8.8, h: 0.6,
    fill: { color: colors.secondary }
});
slide5.addText("SimulationManager creates thread pool and schedules data generation per device", {
    x: 1, y: 2.35, w: 8.6, h: 0.4,
    fontSize: 12, color: colors.light
});

// Step 4
slide5.addShape(pptx.shapes.OVAL, {
    x: 0.3, y: 3, w: 0.5, h: 0.5,
    fill: { color: colors.primary }
});
slide5.addText("4", {
    x: 0.3, y: 3.08, w: 0.5, h: 0.35,
    fontSize: 18, bold: true, color: colors.white,
    align: "center"
});
slide5.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.9, y: 2.95, w: 8.8, h: 0.6,
    fill: { color: colors.secondary }
});
slide5.addText("Data generators create realistic values based on device_type_data_configs ranges", {
    x: 1, y: 3.05, w: 8.6, h: 0.4,
    fontSize: 12, color: colors.light
});

// Step 5
slide5.addShape(pptx.shapes.OVAL, {
    x: 0.3, y: 3.7, w: 0.5, h: 0.5,
    fill: { color: colors.primary }
});
slide5.addText("5", {
    x: 0.3, y: 3.78, w: 0.5, h: 0.35,
    fontSize: 18, bold: true, color: colors.white,
    align: "center"
});
slide5.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.9, y: 3.65, w: 8.8, h: 0.6,
    fill: { color: colors.secondary }
});
slide5.addText("Backend POSTs to Supabase device-ingest edge function with sensor payload", {
    x: 1, y: 3.75, w: 8.6, h: 0.4,
    fontSize: 12, color: colors.light
});

// Step 6
slide5.addShape(pptx.shapes.OVAL, {
    x: 0.3, y: 4.4, w: 0.5, h: 0.5,
    fill: { color: colors.primary }
});
slide5.addText("6", {
    x: 0.3, y: 4.48, w: 0.5, h: 0.35,
    fontSize: 18, bold: true, color: colors.white,
    align: "center"
});
slide5.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.9, y: 4.35, w: 8.8, h: 0.6,
    fill: { color: colors.secondary }
});
slide5.addText("Data stored in device_data table → SymbIOT dashboard shows live updates", {
    x: 1, y: 4.45, w: 8.6, h: 0.4,
    fontSize: 12, color: colors.light
});

// Slide 6: Database Schema
let slide6 = pptx.addSlide();
slide6.background = { color: colors.dark };
slide6.addText("Database Schema (Shared Supabase)", {
    x: 0.5, y: 0.2, w: "90%", h: 0.5,
    fontSize: 28, bold: true, color: colors.primary,
    align: "center"
});

// Tables
const tables = [
    { name: "profiles", fields: ["id", "email", "full_name", "phone"], x: 0.3, y: 0.9, color: colors.teal },
    { name: "elderly_persons", fields: ["id", "profile_id", "health_status"], x: 3.4, y: 0.9, color: colors.teal },
    { name: "devices", fields: ["id", "elderly_person_id", "device_type_id", "device_name", "status"], x: 6.5, y: 0.9, color: colors.green },
    { name: "device_types", fields: ["id", "code", "name", "category", "data_frequency"], x: 0.3, y: 2.5, color: colors.purple },
    { name: "device_type_data_configs", fields: ["id", "device_type_id", "data_type", "unit", "min/max"], x: 3.4, y: 2.5, color: colors.purple },
    { name: "device_data", fields: ["id", "device_id", "data_type", "value", "timestamp"], x: 6.5, y: 2.5, color: colors.orange }
];

tables.forEach(table => {
    slide6.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: table.x, y: table.y, w: 3, h: 1.4,
        fill: { color: table.color },
        line: { color: colors.light, width: 1 }
    });
    slide6.addText(table.name, {
        x: table.x, y: table.y + 0.05, w: 3, h: 0.35,
        fontSize: 12, bold: true, color: colors.white,
        align: "center"
    });
    table.fields.forEach((field, i) => {
        slide6.addText("• " + field, {
            x: table.x + 0.1, y: table.y + 0.4 + (i * 0.22), w: 2.8, h: 0.22,
            fontSize: 9, color: colors.light
        });
    });
});

// Relationship arrows
slide6.addText("1:N", {
    x: 3, y: 1.3, w: 0.4, h: 0.3,
    fontSize: 9, color: colors.accent
});
slide6.addText("1:N", {
    x: 6.1, y: 1.3, w: 0.4, h: 0.3,
    fontSize: 9, color: colors.accent
});
slide6.addText("1:N", {
    x: 3, y: 2.9, w: 0.4, h: 0.3,
    fontSize: 9, color: colors.accent
});

// Legend
slide6.addText("Legend:", {
    x: 0.3, y: 4.2, w: 1, h: 0.3,
    fontSize: 11, bold: true, color: colors.light
});

const legendItems = [
    { label: "User/Profile Data", color: colors.teal },
    { label: "Device Configuration", color: colors.green },
    { label: "Type Definitions", color: colors.purple },
    { label: "Simulated Data", color: colors.orange }
];
legendItems.forEach((item, i) => {
    slide6.addShape(pptx.shapes.RECTANGLE, {
        x: 1.5 + (i * 2.2), y: 4.25, w: 0.3, h: 0.25,
        fill: { color: item.color }
    });
    slide6.addText(item.label, {
        x: 1.85 + (i * 2.2), y: 4.22, w: 1.9, h: 0.3,
        fontSize: 9, color: colors.light
    });
});

// Slide 7: Device Ingest API
let slide7 = pptx.addSlide();
slide7.background = { color: colors.dark };
slide7.addText("Device Ingest API (Integration Point)", {
    x: 0.5, y: 0.2, w: "90%", h: 0.5,
    fontSize: 28, bold: true, color: colors.primary,
    align: "center"
});

slide7.addText("This is the key integration point where the Simulator sends data to SymbIOT", {
    x: 0.5, y: 0.75, w: "90%", h: 0.35,
    fontSize: 14, color: colors.accent,
    align: "center"
});

// Endpoint box
slide7.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 1.2, w: 9, h: 0.5,
    fill: { color: colors.green }
});
slide7.addText("POST https://wiyfcvypeifbdaqnfgrr.supabase.co/functions/v1/device-ingest", {
    x: 0.5, y: 1.28, w: 9, h: 0.35,
    fontSize: 12, bold: true, color: colors.white,
    align: "center"
});

// Request section
slide7.addText("Request Payload:", {
    x: 0.5, y: 1.85, w: 4, h: 0.35,
    fontSize: 14, bold: true, color: colors.accent
});

slide7.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 2.2, w: 4.3, h: 2,
    fill: { color: "2c3e50" }
});
slide7.addText(`{
  "device_id": "KT001",
  "data_type": "heart_rate",
  "value": { "bpm": 72 },
  "unit": "bpm"
}`, {
    x: 0.6, y: 2.3, w: 4.1, h: 1.8,
    fontSize: 11, color: colors.light,
    fontFace: "Courier New"
});

// Headers section
slide7.addText("Headers:", {
    x: 5.2, y: 1.85, w: 4, h: 0.35,
    fontSize: 14, bold: true, color: colors.accent
});

slide7.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.2, y: 2.2, w: 4.3, h: 2,
    fill: { color: "2c3e50" }
});
slide7.addText(`Authorization: Bearer symbiot_ec287...

Content-Type: application/json

apikey: eyJhbGciOiJIUzI1...`, {
    x: 5.3, y: 2.3, w: 4.1, h: 1.8,
    fontSize: 10, color: colors.light,
    fontFace: "Courier New"
});

// Response
slide7.addText("Response: 201 Created", {
    x: 0.5, y: 4.4, w: 4, h: 0.35,
    fontSize: 12, bold: true, color: colors.green
});

// What happens
slide7.addText("What happens when data is ingested:", {
    x: 5.2, y: 4.4, w: 4.3, h: 0.35,
    fontSize: 12, bold: true, color: colors.accent
});
slide7.addText("1. Edge function validates the payload\n2. Data stored in device_data table\n3. Triggers alerts if thresholds exceeded\n4. SymbIOT dashboard updates in real-time", {
    x: 5.2, y: 4.75, w: 4.3, h: 0.8,
    fontSize: 9, color: colors.light
});

// Slide 8: Deployment Architecture
let slide8 = pptx.addSlide();
slide8.background = { color: colors.dark };
slide8.addText("Deployment Architecture", {
    x: 0.5, y: 0.2, w: "90%", h: 0.5,
    fontSize: 28, bold: true, color: colors.primary,
    align: "center"
});

// Docker section
slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.3, y: 0.85, w: 4.4, h: 3.2,
    fill: { color: "1e3a5f" },
    line: { color: colors.accent, width: 2 }
});
slide8.addText("Docker Compose (Simulator)", {
    x: 0.3, y: 0.9, w: 4.4, h: 0.35,
    fontSize: 14, bold: true, color: colors.accent,
    align: "center"
});

// Backend container
slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 1.35, w: 4, h: 0.8,
    fill: { color: colors.green }
});
slide8.addText("iot-simulator-backend\n(Java 11 + Spring Boot)", {
    x: 0.5, y: 1.45, w: 4, h: 0.6,
    fontSize: 10, color: colors.white,
    align: "center"
});
slide8.addText("Port: 3000", {
    x: 3.4, y: 1.35, w: 1, h: 0.3,
    fontSize: 8, color: colors.light
});

// Frontend container
slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 2.3, w: 4, h: 0.8,
    fill: { color: colors.teal }
});
slide8.addText("iot-simulator-frontend\n(Angular + Nginx)", {
    x: 0.5, y: 2.4, w: 4, h: 0.6,
    fontSize: 10, color: colors.white,
    align: "center"
});
slide8.addText("Port: 4200", {
    x: 3.4, y: 2.3, w: 1, h: 0.3,
    fontSize: 8, color: colors.light
});

// Network
slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 3.25, w: 4, h: 0.5,
    fill: { color: colors.purple }
});
slide8.addText("Bridge Network: iot-network", {
    x: 0.5, y: 3.35, w: 4, h: 0.3,
    fontSize: 10, color: colors.white,
    align: "center"
});

// Cloud section
slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.3, y: 0.85, w: 4.4, h: 3.2,
    fill: { color: "1e3a5f" },
    line: { color: colors.green, width: 2 }
});
slide8.addText("Supabase Cloud", {
    x: 5.3, y: 0.9, w: 4.4, h: 0.35,
    fontSize: 14, bold: true, color: colors.green,
    align: "center"
});

// Supabase components
slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.5, y: 1.35, w: 4, h: 0.55,
    fill: { color: colors.green }
});
slide8.addText("PostgreSQL Database", {
    x: 5.5, y: 1.45, w: 4, h: 0.35,
    fontSize: 10, color: colors.white,
    align: "center"
});

slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.5, y: 2, w: 4, h: 0.55,
    fill: { color: colors.green }
});
slide8.addText("Edge Functions (device-ingest)", {
    x: 5.5, y: 2.1, w: 4, h: 0.35,
    fontSize: 10, color: colors.white,
    align: "center"
});

slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.5, y: 2.65, w: 4, h: 0.55,
    fill: { color: colors.green }
});
slide8.addText("REST API (/rest/v1/*)", {
    x: 5.5, y: 2.75, w: 4, h: 0.35,
    fontSize: 10, color: colors.white,
    align: "center"
});

slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 5.5, y: 3.3, w: 4, h: 0.55,
    fill: { color: colors.green }
});
slide8.addText("Auth & Storage", {
    x: 5.5, y: 3.4, w: 4, h: 0.35,
    fontSize: 10, color: colors.white,
    align: "center"
});

// Arrow between Docker and Cloud
slide8.addShape(pptx.shapes.RIGHT_ARROW, {
    x: 4.7, y: 2.2, w: 0.6, h: 0.3,
    fill: { color: colors.primary }
});
slide8.addText("HTTPS", {
    x: 4.7, y: 1.95, w: 0.6, h: 0.25,
    fontSize: 9, color: colors.light,
    align: "center"
});

// Quick start commands
slide8.addText("Quick Start:", {
    x: 0.3, y: 4.2, w: 2, h: 0.35,
    fontSize: 12, bold: true, color: colors.accent
});
slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.3, y: 4.55, w: 9.4, h: 0.5,
    fill: { color: "2c3e50" }
});
slide8.addText("./start-docker.sh                    # Start all services with health checks", {
    x: 0.4, y: 4.62, w: 9.2, h: 0.35,
    fontSize: 11, color: colors.light,
    fontFace: "Courier New"
});

// Slide 9: Use Cases
let slide9 = pptx.addSlide();
slide9.background = { color: colors.dark };
slide9.addText("Simulator Use Cases", {
    x: 0.5, y: 0.2, w: "90%", h: 0.5,
    fontSize: 28, bold: true, color: colors.primary,
    align: "center"
});

const useCases = [
    {
        title: "Development & Testing",
        desc: "Test SymbIOT features without real IoT hardware. Generate consistent test data for automated testing.",
        icon: "1"
    },
    {
        title: "Demos & Presentations",
        desc: "Showcase the platform to stakeholders with realistic simulated data across multiple device types.",
        icon: "2"
    },
    {
        title: "Training & Onboarding",
        desc: "Train new caregivers on the SymbIOT dashboard using simulated scenarios without risk.",
        icon: "3"
    },
    {
        title: "Alert Testing",
        desc: "Trigger specific alert conditions (falls, high heart rate, etc.) to verify notification systems.",
        icon: "4"
    },
    {
        title: "Performance Testing",
        desc: "Generate high volumes of data to test system scalability and database performance.",
        icon: "5"
    }
];

useCases.forEach((uc, i) => {
    const y = 0.85 + (i * 0.85);

    slide9.addShape(pptx.shapes.OVAL, {
        x: 0.4, y: y + 0.1, w: 0.5, h: 0.5,
        fill: { color: colors.primary }
    });
    slide9.addText(uc.icon, {
        x: 0.4, y: y + 0.18, w: 0.5, h: 0.35,
        fontSize: 16, bold: true, color: colors.white,
        align: "center"
    });

    slide9.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 1.1, y: y, w: 8.5, h: 0.7,
        fill: { color: colors.secondary }
    });
    slide9.addText(uc.title, {
        x: 1.2, y: y + 0.05, w: 3, h: 0.3,
        fontSize: 13, bold: true, color: colors.accent
    });
    slide9.addText(uc.desc, {
        x: 1.2, y: y + 0.35, w: 8.3, h: 0.3,
        fontSize: 10, color: colors.light
    });
});

// Slide 10: Summary
let slide10 = pptx.addSlide();
slide10.background = { color: colors.dark };
slide10.addText("Architecture Summary", {
    x: 0.5, y: 0.2, w: "90%", h: 0.5,
    fontSize: 28, bold: true, color: colors.primary,
    align: "center"
});

// Key points
const keyPoints = [
    "The IoT Simulator is a separate application that integrates with SymbIOT via Supabase APIs",
    "Both systems share the same Supabase database for seamless data flow",
    "Simulator Backend fetches device configurations from Supabase REST API",
    "Simulated sensor data is sent to the device-ingest Edge Function",
    "SymbIOT dashboard displays simulated data in real-time, indistinguishable from real sensors",
    "Docker deployment makes the simulator easy to run anywhere"
];

keyPoints.forEach((point, i) => {
    slide10.addText("✓", {
        x: 0.4, y: 0.9 + (i * 0.55), w: 0.4, h: 0.4,
        fontSize: 20, color: colors.green
    });
    slide10.addText(point, {
        x: 0.9, y: 0.95 + (i * 0.55), w: 8.5, h: 0.45,
        fontSize: 13, color: colors.light
    });
});

// Visual summary
slide10.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 4.3, w: 9, h: 0.8,
    fill: { color: colors.secondary }
});
slide10.addText("Simulator (Angular + Java)  →  Supabase (DB + Edge Functions)  →  SymbIOT Dashboard (React)", {
    x: 0.5, y: 4.5, w: 9, h: 0.4,
    fontSize: 14, bold: true, color: colors.accent,
    align: "center"
});

// Slide 11: Thank You
let slide11 = pptx.addSlide();
slide11.background = { color: colors.dark };
slide11.addText("Thank You", {
    x: 0.5, y: 2, w: "90%", h: 1,
    fontSize: 48, bold: true, color: colors.primary,
    align: "center"
});
slide11.addText("SymbIOT System Architecture", {
    x: 0.5, y: 3.1, w: "90%", h: 0.5,
    fontSize: 22, color: colors.accent,
    align: "center"
});
slide11.addText("IoT Simulator Integration Guide", {
    x: 0.5, y: 3.6, w: "90%", h: 0.4,
    fontSize: 16, color: colors.light,
    align: "center"
});

// Save the presentation
pptx.writeFile({ fileName: "SymbIOT_Architecture.pptx" })
    .then(fileName => {
        console.log(`PowerPoint created: ${fileName}`);
    })
    .catch(err => {
        console.error(err);
    });
