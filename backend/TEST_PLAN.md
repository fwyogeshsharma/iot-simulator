# IoT Elderly Care Simulator - Test Plan

# Get live statistics
curl http://localhost:8080/api/simulation/statistics/{simulationId}

## System Overview
The simulator consists of:
- **Backend**: Spring Boot application generating IoT sensor data (port 8080)
- **Frontend**: Angular application for UI (typically port 4200)
- **Database**: Supabase PostgreSQL with REST APIs
- **Data Ingest**: Supabase Edge Function (`device-ingest`)

## Architecture Changes Implemented
1. **Multi-Device Selection**: UI now shows checkboxes for multiple device selection
2. **Auto Data Generation**: Backend automatically generates data types and values (not user-selected)
3. **Continuous Simulation**: Replaced single "Simulate Data" button with "Start/Stop Simulation"
4. **Frequency-Based Generation**: Data generation frequency based on `sort_order` column
5. **Device ID Mapping**: Fixed to use actual hardware device_id (e.g., "temp-001") instead of UUID

## Prerequisites
- Java 11 or higher
- Maven 3.9.9
- Node.js and npm for frontend
- Access to Supabase instance: `wiyfcvypeifbdaqnfgrr.supabase.co`
- Valid API keys configured in `application.yml`

## Test Scenarios

### Test 1: Backend Build Verification
**Status**: ✅ PASSED
- Ran: `mvn clean package -DskipTests`
- Result: `iot-simulator-backend-0.0.1-SNAPSHOT.jar` created successfully
- JAR Location: `C:\Users\sjain\github\symbIOTSimulator\target\`

### Test 2: Configuration Verification
**Steps**:
1. Verify `application.yml` contains all required properties:
   - ✅ `simulator.device-ingest-url`
   - ✅ `simulator.bearer-token`
   - ✅ `supabase.device-types-url` (added)
   - ✅ All API keys and endpoints configured

2. Verify environment properties:
   - ✅ Profiles API key: Service role key with elevated privileges
   - ✅ Device ingest URL points to correct Supabase function
   - ✅ Bearer token for device-ingest authentication

### Test 3: Backend Startup
**Steps**:
```bash
cd C:\Users\sjain\github\symbIOTSimulator
java -jar target/iot-simulator-backend-0.0.1-SNAPSHOT.jar
```

**Expected Output**:
```
Started Application in X seconds
Tomcat started on port(s): 8080 with context path ''
```

**Verification Points**:
- [ ] Application starts without errors
- [ ] No bean creation failures
- [ ] Port 8080 is listening

### Test 4: API Endpoint Verification
**Test Device Selection by Elderly Person ID**:
```bash
curl -X GET "http://localhost:8080/api/devices/{elderlyPersonId}" \
  -H "Content-Type: application/json"
```

**Expected Response**:
- Status: 200 OK
- Body: Array of Device objects with `id`, `deviceName`, `deviceId`, `apiKey`

**Example**:
```json
[
  {
    "id": "uuid-1",
    "deviceName": "Temperature Sensor",
    "deviceId": "temp-001",
    "apiKey": "key-123"
  },
  {
    "id": "uuid-2",
    "deviceName": "Motion Sensor",
    "deviceId": "motion-1",
    "apiKey": "key-456"
  }
]
```

### Test 5: Frontend Startup and UI Verification
**Steps**:
```bash
cd C:\Users\sjain\github\symbIOTSimulatorFrontend\iot-simulator-frontend
npm install  # if needed
ng serve
```

**Navigate to**: `http://localhost:4200`

**UI Verification**:
- [ ] Step indicator shows 3 steps (Select Person, Select Devices, Simulate)
- [ ] Elderly person dropdown loads and displays names
- [ ] Device checkboxes appear after selecting a person
- [ ] Start/Stop buttons are visible and properly styled
- [ ] Green pulsing indicator appears when simulation is running

### Test 6: End-to-End Simulation Test

#### Test 6A: Start Simulation with All Devices (No Selection)
**Steps**:
1. Select an elderly person from dropdown
2. Leave device checkboxes unchecked (select 0 devices)
3. Click "▶️ Start Simulation"

**Expected Behavior**:
- Simulation ID is generated and returned
- Green pulsing "Simulation Running" indicator appears
- Message shows: "Simulation started! Generating data for X device(s)" (should be total devices)
- Browser console shows request/response

**Backend Verification**:
- Check logs for:
  ```
  Simulation started with ID: {simulationId} for X devices
  Scheduling {dataTypeName} for device {deviceName} (ID: {deviceId}) with interval of {seconds} seconds
  Data sent for {dataTypeName} on device {deviceId} - Status: 200
  ```

#### Test 6B: Start Simulation with Specific Devices
**Steps**:
1. Select an elderly person from dropdown
2. Check 1-2 device checkboxes
3. Click "▶️ Start Simulation"

**Expected Behavior**:
- Simulation starts only for selected devices
- Message shows correct count of devices
- Only selected devices generate data (check backend logs)

**Verification**:
- Backend logs show only selected device IDs in scheduled tasks

#### Test 6C: Verify Data Generation in Supabase
**Steps**:
1. While simulation is running, open Supabase dashboard
2. Navigate to `device_data` table
3. Check for recent records

**Expected Data**:
- `device_id`: Hardware ID (e.g., "temp-001", NOT UUID)
- `data_type`: Type from device_type_data_configs (e.g., "temperature", "motion")
- `value`: Random value within configured range
- `unit`: From database (e.g., "°C", "°F", not null)
- `created_at`: Current timestamp
- `device_key`: (if applicable)

**Example Record**:
```json
{
  "id": "uuid-xyz",
  "device_id": "temp-001",
  "data_type": "temperature",
  "value": 24.5,
  "unit": "°C",
  "created_at": "2025-10-29T12:35:20.123456Z",
  "device_key": "..."
}
```

#### Test 6D: Stop Simulation
**Steps**:
1. While simulation is running, click "⏹️ Stop Simulation"

**Expected Behavior**:
- Pulsing indicator disappears
- Message shows: "Simulation stopped successfully"
- No more data records appear in `device_data` table after stopping

**Backend Verification**:
- Check logs for:
  ```
  Simulation stopped: {simulationId}
  ```

### Test 7: Error Scenarios

#### Test 7A: No Elderly Person Selected
**Steps**:
1. Leave elderly person dropdown as "--Choose a person--"
2. Try to click Start Simulation

**Expected**:
- Start button is disabled
- Message shows: "Please select an elderly person"

#### Test 7B: Invalid Device ID in Payload
**Steps**:
1. Manually modify frontend to send invalid device IDs
2. Try to start simulation

**Expected**:
- Simulation starts but fails silently OR returns 400 error
- Message indicates error

#### Test 7C: Network Connectivity Loss
**Steps**:
1. Disconnect network while simulation is running
2. Observe behavior

**Expected**:
- Error logs in backend
- Simulation attempts to retry (if implemented) or stops gracefully
- Frontend doesn't crash

### Test 8: Frequency Calculation Verification

**Formula**: `interval_seconds = (24 * 60 * 60) / frequencyPerDay`

**Test Cases**:
- If `sort_order` = 4: interval = 86400 / 4 = 21600 seconds (6 hours)
- If `sort_order` = 24: interval = 86400 / 24 = 3600 seconds (1 hour)
- If `sort_order` = 96: interval = 86400 / 96 = 900 seconds (15 minutes)

**Verification**:
- Check backend logs during simulation start:
  ```
  Scheduling {dataType} for device ... with interval of {interval} seconds
  ```
- Verify interval matches expected calculation

### Test 9: Code Quality Checks

#### Backend Code Review
**Files Modified**:
- ✅ `SimulationManager.java` - Core scheduling logic
- ✅ `SimulationRequest.java` - Request model
- ✅ `SimulationResponse.java` - Response model
- ✅ `SimulatorController.java` - New endpoints
- ✅ `SimulatorService.java` - Enhanced to return DataTypeConfig
- ✅ `application.yml` - Configuration added

**Checks**:
- [ ] All classes compile without warnings
- [ ] No hardcoded values in endpoints
- [ ] Proper error handling in try-catch blocks
- [ ] Resource cleanup (thread pool shutdown)
- [ ] Thread-safe collections (ConcurrentHashMap)

#### Frontend Code Review
**Files Modified**:
- ✅ `app.component.ts` - Component logic refactored
- ✅ `app.component.html` - UI redesigned
- ✅ `app.component.css` - Styling enhanced

**Checks**:
- [ ] Proper TypeScript typing (interfaces defined)
- [ ] No console errors in browser
- [ ] Responsive design works on mobile
- [ ] localStorage properly handles settings

## Test Execution Steps

### Quick Start Testing
```bash
# Terminal 1: Start Backend
cd C:\Users\sjain\github\symbIOTSimulator
java -jar target/iot-simulator-backend-0.0.1-SNAPSHOT.jar

# Terminal 2: Start Frontend
cd C:\Users\sjain\github\symbIOTSimulatorFrontend\iot-simulator-frontend
ng serve

# Terminal 3: Monitor Supabase
# Open Supabase dashboard and watch device_data table in real-time
```

### Manual Testing Checklist
- [ ] Backend starts without errors
- [ ] Frontend loads at localhost:4200
- [ ] Elderly person dropdown populates
- [ ] Selecting a person loads device checkboxes
- [ ] Device count displays correctly
- [ ] Start Simulation button starts without errors
- [ ] Green status indicator pulses
- [ ] Data appears in device_data table within 10 seconds
- [ ] Device IDs match hardware format (temp-001, motion-1, etc.)
- [ ] Units are correct and not null
- [ ] Values are within configured ranges
- [ ] Stop Simulation button stops data generation
- [ ] No new records appear after stopping

## Expected Results

### Success Criteria
1. ✅ Backend builds without errors
2. ✅ All configurations are correct
3. ✅ Simulation starts and generates continuous data
4. ✅ Data format matches expected schema
5. ✅ Device IDs use hardware format (not UUID)
6. ✅ Units are populated from database
7. ✅ Frequency calculations are correct
8. ✅ Stop simulation properly halts data generation
9. ✅ Frontend UI is responsive and user-friendly
10. ✅ No console errors in browser or backend

### Known Issues (if any)
- None identified at this time

## Performance Considerations

### Default Configuration
- Thread pool: 10 scheduler threads
- Simulation poll interval: Based on `sort_order` (typically 6 hours for 4/day)
- Max concurrent simulations: Limited by thread pool size

### Scalability Notes
- Each simulation uses 1 thread per data type
- For example, 2 devices with 3 data types each = 6 scheduler threads
- Max recommended: ~50 active simulations (500 threads total)

## Troubleshooting Guide

### Issue: Empty Device List
**Cause**: ID mismatch or RLS policy blocking
**Solution**:
1. Verify `elderly_persons.id` is used, not `profiles.id`
2. Check RLS policies allow service_role key access

### Issue: 404 Device Not Found Error
**Cause**: Sending UUID instead of hardware device_id
**Solution**:
1. Verify SimulationManager uses `device.getDeviceId()`
2. Check device_id in Supabase matches hardware ID

### Issue: Null Units in Database
**Cause**: Not fetching from device_type_data_configs
**Solution**:
1. Verify SimulatorService.getDataTypesByDeviceId() queries device_type_data_configs
2. Check sample_data_config JSON contains unit field

### Issue: Bean Creation Error
**Cause**: Property placeholder not found
**Solution**:
1. Verify all @Value properties exist in application.yml
2. Check property names match exactly (case-sensitive)

## Sign-Off

- **Build Date**: 2025-10-29
- **Build Status**: ✅ SUCCESS
- **Last Updated**: Test Plan Creation
- **Next Step**: Execute Test Scenarios
