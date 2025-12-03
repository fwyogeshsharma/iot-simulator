const pptxgen = require("pptxgenjs");

const pptx = new pptxgen();

// Set presentation properties
pptx.author = "SymbIOT Team";
pptx.title = "SymbIOT - Sensors & Devices Platform Overview";
pptx.subject = "IoT Health Monitoring for Elderly Care";
pptx.company = "SymbIOT";

// Define colors
const colors = {
    dark: "1a1a2e",
    primary: "e94560",
    secondary: "0f4c75",
    accent: "3282b8",
    light: "bbe1fa",
    white: "FFFFFF"
};

// Slide 1: Title Slide
let slide1 = pptx.addSlide();
slide1.background = { color: colors.dark };
slide1.addText("SymbIOT Platform", {
    x: 0.5, y: 2, w: "90%", h: 1.5,
    fontSize: 54, bold: true, color: colors.primary,
    align: "center"
});
slide1.addText("IoT Health Monitoring for Elderly Care", {
    x: 0.5, y: 3.5, w: "90%", h: 0.8,
    fontSize: 28, color: colors.accent,
    align: "center"
});
slide1.addText("Sensors & Devices Catalog", {
    x: 0.5, y: 4.5, w: "90%", h: 0.6,
    fontSize: 20, color: colors.light,
    align: "center"
});

// Slide 2: Architecture Overview
let slide2 = pptx.addSlide();
slide2.background = { color: colors.dark };
slide2.addText("System Architecture", {
    x: 0.5, y: 0.3, w: "90%", h: 0.8,
    fontSize: 36, bold: true, color: colors.primary,
    align: "center"
});

const archBoxes = [
    { text: "Frontend UI\n(Angular)", x: 0.3 },
    { text: "Simulator\nBackend (Java)", x: 2.7 },
    { text: "Supabase\nREST APIs", x: 5.1 },
    { text: "Supabase\nDatabase", x: 7.5 }
];

archBoxes.forEach((box, i) => {
    slide2.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: box.x, y: 1.5, w: 2, h: 1.2,
        fill: { color: colors.secondary },
        line: { color: colors.accent, width: 2 }
    });
    slide2.addText(box.text, {
        x: box.x, y: 1.6, w: 2, h: 1,
        fontSize: 12, color: colors.white,
        align: "center", valign: "middle"
    });
    if (i < 3) {
        slide2.addText("→", {
            x: box.x + 2, y: 1.7, w: 0.7, h: 0.8,
            fontSize: 28, color: colors.primary,
            align: "center"
        });
    }
});

slide2.addText("Key Features:", {
    x: 0.5, y: 3, w: "90%", h: 0.5,
    fontSize: 18, bold: true, color: colors.accent
});

const features = [
    "• Automatic detection of daily routines & movement patterns",
    "• Intelligent notifications for falls, missed medications, irregular vitals",
    "• Real-time monitoring with configurable simulation"
];

features.forEach((feat, i) => {
    slide2.addText(feat, {
        x: 0.7, y: 3.5 + (i * 0.4), w: "85%", h: 0.4,
        fontSize: 14, color: colors.light
    });
});

// Slide 3: Platform Statistics
let slide3 = pptx.addSlide();
slide3.background = { color: colors.dark };
slide3.addText("Platform Statistics", {
    x: 0.5, y: 0.3, w: "90%", h: 0.8,
    fontSize: 36, bold: true, color: colors.primary,
    align: "center"
});

const stats = [
    { num: "18", label: "Device Types", x: 0.5 },
    { num: "42", label: "Data Types", x: 2.8 },
    { num: "21", label: "Manufacturers", x: 5.1 },
    { num: "6", label: "Categories", x: 7.4 }
];

stats.forEach(stat => {
    slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: stat.x, y: 1.3, w: 2, h: 1.5,
        fill: { color: colors.secondary }
    });
    slide3.addText(stat.num, {
        x: stat.x, y: 1.4, w: 2, h: 0.9,
        fontSize: 48, bold: true, color: colors.primary,
        align: "center"
    });
    slide3.addText(stat.label, {
        x: stat.x, y: 2.3, w: 2, h: 0.4,
        fontSize: 14, color: colors.light,
        align: "center"
    });
});

slide3.addText("Categories Covered:", {
    x: 0.5, y: 3.2, w: "90%", h: 0.5,
    fontSize: 18, bold: true, color: colors.accent,
    align: "center"
});

const categories = ["HEALTH", "SAFETY", "SECURITY", "TRACKING", "ENVIRONMENT", "AUTOMATION"];
categories.forEach((cat, i) => {
    slide3.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 0.8 + (i * 1.5), y: 3.8, w: 1.4, h: 0.5,
        fill: { color: colors.primary }
    });
    slide3.addText(cat, {
        x: 0.8 + (i * 1.5), y: 3.85, w: 1.4, h: 0.4,
        fontSize: 10, bold: true, color: colors.white,
        align: "center"
    });
});

// Slide 4: Health Monitoring Devices (Part 1)
let slide4 = pptx.addSlide();
slide4.background = { color: colors.dark };
slide4.addText("Health Monitoring Devices", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

const healthDevices1 = [
    {
        name: "Medical Device",
        desc: "Heart Rate, Blood Pressure, Glucose Monitor",
        data: "heart_rate (65-95 bpm), blood_pressure (110-130/70-85 mmHg), temperature (36.2-37.2°C), oxygen_saturation (95-100%)"
    },
    {
        name: "Wearable Device",
        desc: "Smart Watch, Activity Tracker",
        data: "heart_rate (60-100 bpm), steps (1000-15000), activity (walking/running/resting/sleeping)"
    },
    {
        name: "Sleep Monitor",
        desc: "Sleep Quality Tracking",
        data: "sleep_quality (60-95%), sleep_stage (deep/light/rem/awake)"
    },
    {
        name: "Medication Dispenser",
        desc: "Automated Medication Reminder",
        data: "medication_taken (boolean), next_dose_time (timestamp)"
    }
];

healthDevices1.forEach((device, i) => {
    const y = 1 + (i * 1.1);
    slide4.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 0.3, y: y, w: 9.4, h: 1,
        fill: { color: colors.secondary }
    });
    slide4.addText(device.name, {
        x: 0.5, y: y + 0.05, w: 3, h: 0.35,
        fontSize: 16, bold: true, color: colors.primary
    });
    slide4.addText(device.desc, {
        x: 0.5, y: y + 0.35, w: 4, h: 0.25,
        fontSize: 11, color: colors.light
    });
    slide4.addText(device.data, {
        x: 0.5, y: y + 0.6, w: 9, h: 0.35,
        fontSize: 9, color: colors.accent
    });
});

// Slide 5: Health Monitoring Devices (Part 2)
let slide5 = pptx.addSlide();
slide5.background = { color: colors.dark };
slide5.addText("Health Monitoring Devices (Continued)", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

const healthDevices2 = [
    {
        name: "Commercial Scale",
        desc: "Digital scale for weight and body composition",
        data: "weight (50-120 kg), bmi (18-35 kg/m²), body_fat (15-40%)"
    },
    {
        name: "Bed Pad",
        desc: "Pressure-sensitive pad for bed occupancy and sleep patterns",
        data: "pressure (0-50 mmHg), occupancy (boolean)"
    },
    {
        name: "Toilet Seat Sensor",
        desc: "Smart toilet seat for bathroom usage monitoring",
        data: "usage (0-3 times), duration (1-15 min), urine_analysis (normal/abnormal/needs_review)"
    }
];

healthDevices2.forEach((device, i) => {
    const y = 1 + (i * 1.2);
    slide5.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 0.3, y: y, w: 9.4, h: 1.1,
        fill: { color: colors.secondary }
    });
    slide5.addText(device.name, {
        x: 0.5, y: y + 0.05, w: 3, h: 0.35,
        fontSize: 16, bold: true, color: colors.primary
    });
    slide5.addText(device.desc, {
        x: 0.5, y: y + 0.4, w: 9, h: 0.3,
        fontSize: 11, color: colors.light
    });
    slide5.addText(device.data, {
        x: 0.5, y: y + 0.7, w: 9, h: 0.35,
        fontSize: 10, color: colors.accent
    });
});

// Slide 6: Safety Devices
let slide6 = pptx.addSlide();
slide6.background = { color: colors.dark };
slide6.addText("Safety Devices", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

const safetyDevices = [
    {
        name: "Fall Detection",
        desc: "Automatic Fall Detection Device - Immediate alerts when falls are detected",
        data: "fall_detected (boolean, 5% probability), impact_force (0-3 G)",
        freq: "720 readings/day"
    },
    {
        name: "Emergency Button",
        desc: "Panic/SOS Button - One-touch emergency assistance",
        data: "button_pressed (boolean, 2% probability)",
        freq: "1440 readings/day (always monitored)"
    }
];

safetyDevices.forEach((device, i) => {
    const y = 1 + (i * 1.5);
    slide6.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 0.3, y: y, w: 9.4, h: 1.3,
        fill: { color: colors.secondary }
    });
    slide6.addText(device.name, {
        x: 0.5, y: y + 0.1, w: 3, h: 0.35,
        fontSize: 18, bold: true, color: colors.primary
    });
    slide6.addText(device.desc, {
        x: 0.5, y: y + 0.45, w: 9, h: 0.3,
        fontSize: 12, color: colors.light
    });
    slide6.addText("Data: " + device.data, {
        x: 0.5, y: y + 0.75, w: 9, h: 0.25,
        fontSize: 10, color: colors.accent
    });
    slide6.addText("Frequency: " + device.freq, {
        x: 0.5, y: y + 1, w: 9, h: 0.25,
        fontSize: 10, color: colors.light
    });
});

slide6.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 4, w: 9, h: 0.8,
    fill: { color: "4a1528" }
});
slide6.addText("Critical Safety Feature: 24/7 monitoring with immediate alert notifications to caregivers", {
    x: 0.7, y: 4.2, w: 8.6, h: 0.5,
    fontSize: 14, color: colors.primary,
    align: "center"
});

// Slide 7: Security Devices
let slide7 = pptx.addSlide();
slide7.background = { color: colors.dark };
slide7.addText("Security Devices", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

const securityDevices = [
    {
        name: "Motion Sensor",
        desc: "Movement Detection - Track activity in different rooms",
        data: "motion_detected (boolean, 40% probability)",
        freq: "720 readings/day"
    },
    {
        name: "Door/Window Sensor",
        desc: "Entry/Exit Detection - Monitor all entry points",
        data: "door_status (open/closed), last_opened (timestamp)",
        freq: "1440 readings/day"
    },
    {
        name: "Security Camera",
        desc: "Video Surveillance - Visual monitoring with motion detection",
        data: "motion_detected (boolean, 30% probability), recording_status (recording/standby/offline)",
        freq: "60 readings/day"
    }
];

securityDevices.forEach((device, i) => {
    const y = 1 + (i * 1.3);
    slide7.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 0.3, y: y, w: 9.4, h: 1.15,
        fill: { color: colors.secondary }
    });
    slide7.addText(device.name, {
        x: 0.5, y: y + 0.05, w: 3, h: 0.3,
        fontSize: 16, bold: true, color: colors.primary
    });
    slide7.addText(device.desc, {
        x: 0.5, y: y + 0.35, w: 9, h: 0.25,
        fontSize: 11, color: colors.light
    });
    slide7.addText("Data: " + device.data, {
        x: 0.5, y: y + 0.6, w: 9, h: 0.25,
        fontSize: 9, color: colors.accent
    });
    slide7.addText("Frequency: " + device.freq, {
        x: 0.5, y: y + 0.85, w: 9, h: 0.25,
        fontSize: 9, color: colors.light
    });
});

// Slide 8: Tracking Devices
let slide8 = pptx.addSlide();
slide8.background = { color: colors.dark };
slide8.addText("Tracking Devices", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

const trackingDevices = [
    {
        name: "GPS Tracker",
        desc: "Outdoor Location Tracking - Know where your loved ones are",
        data: "location (lat: -90 to 90°, long: -180 to 180°), speed (0-5 km/h)",
        freq: "288 readings/day"
    },
    {
        name: "Smart Phone",
        desc: "Mobile smartphone with GPS tracking and activity monitoring",
        data: "gps (GPS coordinates), battery (20-100%), steps (0-15000)",
        freq: "96 readings/day | Supports Position Tracking"
    },
    {
        name: "Worker Wearable",
        desc: "Indoor Positioning Device with Movement Tracking",
        data: "position (indoor positioning in meters)",
        freq: "288 readings/day | Supports Position Tracking"
    }
];

trackingDevices.forEach((device, i) => {
    const y = 1 + (i * 1.3);
    slide8.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 0.3, y: y, w: 9.4, h: 1.15,
        fill: { color: colors.secondary }
    });
    slide8.addText(device.name, {
        x: 0.5, y: y + 0.05, w: 3, h: 0.3,
        fontSize: 16, bold: true, color: colors.primary
    });
    slide8.addText(device.desc, {
        x: 0.5, y: y + 0.35, w: 9, h: 0.25,
        fontSize: 11, color: colors.light
    });
    slide8.addText("Data: " + device.data, {
        x: 0.5, y: y + 0.6, w: 9, h: 0.25,
        fontSize: 9, color: colors.accent
    });
    slide8.addText("Frequency: " + device.freq, {
        x: 0.5, y: y + 0.85, w: 9, h: 0.25,
        fontSize: 9, color: colors.light
    });
});

// Slide 9: Environment & Activity Devices
let slide9 = pptx.addSlide();
slide9.background = { color: colors.dark };
slide9.addText("Environment & Activity Devices", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

const envDevices = [
    {
        name: "Environmental Sensor",
        desc: "Temperature, Humidity Monitor - Ensure comfortable living conditions",
        data: "temperature (18-28°C), humidity (40-70%), aqi (0-500 AQI)",
        freq: "24 readings/day"
    },
    {
        name: "Chair/Seat Sensor",
        desc: "Pressure sensor for sitting duration and posture monitoring",
        data: "occupancy (boolean), pressure (0-100 kg), posture (good/fair/poor/slouching)",
        freq: "48 readings/day"
    },
    {
        name: "Smart Home Hub",
        desc: "Home Automation Controller - Central control for all smart devices",
        data: "power_consumption (50-500 W), system_status (online/offline/maintenance)",
        freq: "144 readings/day"
    }
];

envDevices.forEach((device, i) => {
    const y = 1 + (i * 1.3);
    slide9.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 0.3, y: y, w: 9.4, h: 1.15,
        fill: { color: colors.secondary }
    });
    slide9.addText(device.name, {
        x: 0.5, y: y + 0.05, w: 4, h: 0.3,
        fontSize: 16, bold: true, color: colors.primary
    });
    slide9.addText(device.desc, {
        x: 0.5, y: y + 0.35, w: 9, h: 0.25,
        fontSize: 11, color: colors.light
    });
    slide9.addText("Data: " + device.data, {
        x: 0.5, y: y + 0.6, w: 9, h: 0.25,
        fontSize: 9, color: colors.accent
    });
    slide9.addText("Frequency: " + device.freq, {
        x: 0.5, y: y + 0.85, w: 9, h: 0.25,
        fontSize: 9, color: colors.light
    });
});

// Slide 10: Supported Manufacturers
let slide10 = pptx.addSlide();
slide10.background = { color: colors.dark };
slide10.addText("Supported Manufacturers", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

// Fully supported manufacturers (green background)
const supportedManufacturers = ["Apple", "Fitbit", "Withings", "Philips", "Ring", "Purple Air"];

const manufacturers = [
    ["Apple", "Fitbit", "Samsung", "Google Nest", "Garmin"],
    ["Omron", "Ring", "Oura", "Withings", "Philips"],
    ["Polar", "Xiaomi", "ResMed", "Dexcom", "Medtronic"],
    ["Honeywell", "Purple Air", "Arlo", "Eufy", "Whoop"]
];

const greenColor = "27ae60"; // Green for supported manufacturers

manufacturers.forEach((row, rowIdx) => {
    row.forEach((mfr, colIdx) => {
        const x = 0.3 + (colIdx * 1.9);
        const y = 1 + (rowIdx * 1);
        const isSupported = supportedManufacturers.includes(mfr);
        slide10.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
            x: x, y: y, w: 1.8, h: 0.8,
            fill: { color: isSupported ? greenColor : colors.secondary }
        });
        slide10.addText(mfr, {
            x: x, y: y + 0.25, w: 1.8, h: 0.4,
            fontSize: 12, color: colors.white,
            align: "center"
        });
    });
});

// Add legend
slide10.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 0.5, y: 4.6, w: 0.4, h: 0.3,
    fill: { color: greenColor }
});
slide10.addText("Fully Supported", {
    x: 1, y: 4.6, w: 2, h: 0.3,
    fontSize: 11, color: colors.light
});

slide10.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
    x: 3.5, y: 4.6, w: 0.4, h: 0.3,
    fill: { color: colors.secondary }
});
slide10.addText("In Progress", {
    x: 4, y: 4.6, w: 2, h: 0.3,
    fontSize: 11, color: colors.light
});

// Slide 11: Data Types Reference
let slide11 = pptx.addSlide();
slide11.background = { color: colors.dark };
slide11.addText("Complete Data Types Reference", {
    x: 0.5, y: 0.2, w: "90%", h: 0.5,
    fontSize: 28, bold: true, color: colors.primary,
    align: "center"
});

const dataTypesTable = [
    ["Data Type", "Unit", "Type", "Range"],
    ["heart_rate", "bpm", "number", "60-100"],
    ["blood_pressure", "mmHg", "object", "110-130/70-85"],
    ["temperature", "°C", "number", "36-37 / 18-28"],
    ["oxygen_saturation", "%", "number", "95-100"],
    ["steps", "steps", "number", "0-15000"],
    ["sleep_quality", "%", "number", "60-95"],
    ["weight", "kg", "number", "50-120"],
    ["fall_detected", "-", "boolean", "5% prob"],
    ["motion_detected", "-", "boolean", "30-40% prob"],
    ["door_status", "-", "string", "open/closed"],
    ["humidity", "%", "number", "40-70"],
    ["gps/location", "°", "object", "lat/long"],
    ["battery", "%", "number", "20-100"]
];

slide11.addTable(dataTypesTable, {
    x: 0.3, y: 0.8, w: 9.4,
    colW: [2.5, 1.5, 1.5, 4],
    fontSize: 10,
    color: colors.light,
    fill: { color: colors.secondary },
    border: { pt: 1, color: colors.accent },
    fontFace: "Arial",
    align: "center",
    valign: "middle"
});

// Slide 12: Simulation Workflow
let slide12 = pptx.addSlide();
slide12.background = { color: colors.dark };
slide12.addText("Simulation Workflow", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

const workflow = [
    { step: "1", title: "Select Elderly Person", desc: "Choose from registered profiles" },
    { step: "2", title: "Select Device", desc: "Choose device by name (e.g., KT001)" },
    { step: "3", title: "Select Data Type", desc: "Choose what to simulate (heart_rate, etc.)" },
    { step: "4", title: "Set Value/Range", desc: "Configure fixed value or range" },
    { step: "5", title: "Simulate!", desc: "Send data to device-ingest API" }
];

workflow.forEach((item, i) => {
    const y = 1 + (i * 0.8);

    // Step circle
    slide12.addShape(pptx.shapes.OVAL, {
        x: 0.5, y: y, w: 0.6, h: 0.6,
        fill: { color: colors.primary }
    });
    slide12.addText(item.step, {
        x: 0.5, y: y + 0.1, w: 0.6, h: 0.4,
        fontSize: 20, bold: true, color: colors.white,
        align: "center"
    });

    // Content box
    slide12.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: 1.3, y: y, w: 8.2, h: 0.6,
        fill: { color: colors.secondary }
    });
    slide12.addText(item.title, {
        x: 1.5, y: y + 0.05, w: 3, h: 0.3,
        fontSize: 14, bold: true, color: colors.light
    });
    slide12.addText(item.desc, {
        x: 1.5, y: y + 0.3, w: 8, h: 0.25,
        fontSize: 11, color: colors.accent
    });
});

// Slide 13: Summary
let slide13 = pptx.addSlide();
slide13.background = { color: colors.dark };
slide13.addText("Summary", {
    x: 0.5, y: 0.2, w: "90%", h: 0.6,
    fontSize: 32, bold: true, color: colors.primary,
    align: "center"
});

const summary = [
    { count: "7", label: "Health Devices" },
    { count: "2", label: "Safety Devices" },
    { count: "3", label: "Security Devices" },
    { count: "3", label: "Tracking Devices" },
    { count: "3", label: "Env/Activity" }
];

summary.forEach((item, i) => {
    const x = 0.4 + (i * 1.9);
    slide13.addShape(pptx.shapes.ROUNDED_RECTANGLE, {
        x: x, y: 1, w: 1.8, h: 1.2,
        fill: { color: colors.secondary }
    });
    slide13.addText(item.count, {
        x: x, y: 1.1, w: 1.8, h: 0.6,
        fontSize: 36, bold: true, color: colors.primary,
        align: "center"
    });
    slide13.addText(item.label, {
        x: x, y: 1.7, w: 1.8, h: 0.4,
        fontSize: 11, color: colors.light,
        align: "center"
    });
});

slide13.addText("SymbIOT Platform Capabilities", {
    x: 0.5, y: 2.5, w: 9, h: 0.4,
    fontSize: 18, bold: true, color: colors.accent,
    align: "center"
});

const capabilities = [
    "✓ Complete elderly care monitoring ecosystem",
    "✓ 21+ device manufacturer integrations",
    "✓ Real-time simulation for testing & demos",
    "✓ Configurable data ranges and frequencies",
    "✓ Intelligent alerting for critical events"
];

capabilities.forEach((cap, i) => {
    slide13.addText(cap, {
        x: 2, y: 3 + (i * 0.4), w: 6, h: 0.35,
        fontSize: 14, color: colors.light
    });
});

// Slide 14: Thank You
let slide14 = pptx.addSlide();
slide14.background = { color: colors.dark };
slide14.addText("Thank You", {
    x: 0.5, y: 2, w: "90%", h: 1,
    fontSize: 54, bold: true, color: colors.primary,
    align: "center"
});
slide14.addText("SymbIOT - Caring for Your Loved Ones", {
    x: 0.5, y: 3.2, w: "90%", h: 0.6,
    fontSize: 24, color: colors.accent,
    align: "center"
});
slide14.addText("IoT Health Monitoring Platform for Elderly Care", {
    x: 0.5, y: 4, w: "90%", h: 0.5,
    fontSize: 16, color: colors.light,
    align: "center"
});

// Save the presentation
pptx.writeFile({ fileName: "SymbIOT_Sensors_Devices.pptx" })
    .then(fileName => {
        console.log(`PowerPoint created: ${fileName}`);
    })
    .catch(err => {
        console.error(err);
    });
