package com.hmdm.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for syncing device contacts and call logs to Google Sheets.
 *
 * Uses a Google Service Account (GOOGLE_SHEETS_CREDENTIALS env var) or
 * falls back to API key for read-only access.
 *
 * Required environment variables:
 *   GOOGLE_SHEETS_CREDENTIALS — JSON string of the service account key
 *   GOOGLE_SHEETS_SPREADSHEET_ID — The spreadsheet ID from the sheet URL
 */
@Service
@Slf4j
public class GoogleSheetsService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "MDM-GoogleSheets-Sync";

    // Sheet names
    private static final String SHEET_CONTACTS = "Contacts";
    private static final String SHEET_CALL_LOGS = "CallLogs";
    private static final String CONTACTS_RANGE = SHEET_CONTACTS + "!A:G";
    private static final String CALL_LOGS_RANGE = SHEET_CALL_LOGS + "!A:G";

    // Column indices (0-based)
    private static final int COL_DEVICE_ID = 0;
    private static final int COL_RAW_CONTACT_ID = 1;
    private static final int COL_NAME = 2;
    private static final int COL_PHONE = 3;
    private static final int COL_PHONE_TYPE = 4;
    private static final int COL_EMAIL = 5;
    private static final int COL_TIMESTAMP = 6;

    private static final int CALL_COL_DEVICE_ID = 0;
    private static final int CALL_COL_PHONE_NUMBER = 1;
    private static final int CALL_COL_CALL_TYPE = 2;
    private static final int CALL_COL_DURATION_SEC = 3;
    private static final int CALL_COL_CALL_DATE = 4;
    private static final int CALL_COL_CONTACT_NAME = 5;
    private static final int CALL_COL_TIMESTAMP = 6;

    @Value("${google.sheets.spreadsheet-id:}")
    private String spreadsheetId;

    @Value("${google.sheets.credentials-json:}")
    private String credentialsJson;

    @Value("${google.sheets.api-key:}")
    private String apiKey;

    private Sheets sheetsService;
    private boolean initialized = false;
    private boolean useApiKey = false;

    @PostConstruct
    public void init() {
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            log.warn("GoogleSheetsService: GOOGLE_SHEETS_SPREADSHEET_ID not configured — disabled");
            return;
        }

        try {
            // Try service account first (required for write operations)
            if (credentialsJson != null && !credentialsJson.isBlank()) {
                GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes()))
                        .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
                sheetsService = createSheetsService(credentials);
                initialized = true;
                log.info("GoogleSheetsService: initialized with service account (read/write)");
                return;
            }

            // Fall back to API key (read-only)
            if (apiKey != null && !apiKey.isBlank()) {
                useApiKey = true;
                sheetsService = createSheetsService(null);
                initialized = true;
                log.warn("GoogleSheetsService: initialized with API key — READ ONLY! " +
                        "Writes will fail. To enable writes, create a service account at " +
                        "https://console.cloud.google.com/apis/credentials, download the JSON, " +
                        "share your sheet with the service account email, and set " +
                        "GOOGLE_SHEETS_CREDENTIALS env var with the JSON contents.");

                // Test write access immediately
                testWriteAccess();
                return;
            }

            log.warn("GoogleSheetsService: no credentials or API key configured — disabled");
        } catch (Exception e) {
            log.error("GoogleSheetsService: initialization failed: {}", e.getMessage());
        }
    }

    /**
     * Test if we can write to the sheet. Logs a warning if we can't.
     */
    private void testWriteAccess() {
        try {
            List<List<Object>> testValues = new ArrayList<>();
            testValues.add(Arrays.asList("MDM Test Write", "Testing write access", timestampNow()));
            String testRange = SHEET_CONTACTS + "!A1:C1";

            var body = new ValueRange().setValues(testValues);
            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, testRange, body)
                    .setValueInputOption("USER_ENTERED")
                    .setKey(apiKey)
                    .execute();

            log.info("GoogleSheetsService: write test SUCCESS — API key has write access");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("403") || msg.contains("401")) {
                log.warn("GoogleSheetsService: API key does NOT have write access (HTTP 403). " +
                        "Contacts & call logs will NOT be saved. " +
                        "Action required: Create a service account at " +
                        "https://console.cloud.google.com/apis/credentials, " +
                        "share your sheet with the service account email, " +
                        "and set GOOGLE_SHEETS_CREDENTIALS env var.");
            } else {
                log.warn("GoogleSheetsService: write test failed: {} — sync may not work", msg);
            }
        }

        // Clean up test rows
        try {
            var clearBody = new ValueRange().setValues(new ArrayList<>());
            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, SHEET_CONTACTS + "!A1:C1", clearBody)
                    .setValueInputOption("USER_ENTERED")
                    .setKey(apiKey)
                    .execute();
        } catch (Exception ignored) {}
    }

    private Sheets createSheetsService(GoogleCredentials credentials) throws GeneralSecurityException, IOException {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        HttpRequestInitializer requestInitializer;

        if (credentials != null) {
            requestInitializer = new HttpCredentialsAdapter(credentials);
        } else {
            // No auth — will use API key in the URL
            requestInitializer = request -> {};
        }

        return new Sheets.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CONTACTS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Upsert contacts into Google Sheets.
     * Dedup by deviceId + rawContactId.
     */
    public int syncContacts(Long deviceId, List<Map<String, Object>> contacts) {
        if (!initialized) return 0;

        try {
            // Ensure sheet exists
            ensureSheetExists(SHEET_CONTACTS, Arrays.asList("DeviceID", "RawContactID", "Name", "Phone", "PhoneType", "Email", "Timestamp"));

            // Read existing data
            List<List<Object>> existingRows = readRange(CONTACTS_RANGE);
            Map<String, Integer> existingMap = new HashMap<>(); // key -> row index
            if (existingRows != null && !existingRows.isEmpty()) {
                // Skip header
                for (int i = 1; i < existingRows.size(); i++) {
                    List<Object> row = existingRows.get(i);
                    if (row.size() > COL_PHONE) {
                        String key = deviceId + ":" + getStr(row, COL_RAW_CONTACT_ID);
                        existingMap.put(key, i);
                    }
                }
            }

            List<List<Object>> rowsToAppend = new ArrayList<>();
            List<Map<Integer, List<Object>>> rowsToUpdate = new ArrayList<>(); // {rowIndex -> newValues}
            String now = timestampNow();

            for (Map<String, Object> contact : contacts) {
                String rawContactId = getStr(contact, "rawContactId");
                String name = getStr(contact, "name");
                String phone = getStr(contact, "phone");
                String phoneType = getStr(contact, "phoneType");
                String email = getStr(contact, "email");

                String key = deviceId + ":" + rawContactId;

                if (existingMap.containsKey(key)) {
                    // Update existing row
                    int rowIndex = existingMap.get(key);
                    List<Object> newValues = Arrays.asList(
                            String.valueOf(deviceId), rawContactId, name, phone, phoneType, email, now);
                    Map<Integer, List<Object>> update = new HashMap<>();
                    update.put(rowIndex, newValues);
                    rowsToUpdate.add(update);
                } else {
                    // New row
                    rowsToAppend.add(Arrays.asList(
                            String.valueOf(deviceId), rawContactId, name, phone, phoneType, email, now));
                }
            }

            int updated = 0;
            // Append new rows
            if (!rowsToAppend.isEmpty()) {
                appendRange(SHEET_CONTACTS + "!A:G", rowsToAppend);
                updated += rowsToAppend.size();
            }

            // Update existing rows
            for (Map<Integer, List<Object>> update : rowsToUpdate) {
                for (Map.Entry<Integer, List<Object>> entry : update.entrySet()) {
                    int rowIdx = entry.getKey();
                    List<Object> vals = entry.getValue();
                    String range = SHEET_CONTACTS + "!A" + (rowIdx + 1) + ":G" + (rowIdx + 1);
                    updateRange(range, Collections.singletonList(vals));
                    updated++;
                }
            }

            if (updated > 0) {
                log.info("GoogleSheets: synced {} contacts for device {}", updated, deviceId);
            }
            return updated;
        } catch (Exception e) {
            log.error("GoogleSheets: contact sync error: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Read contacts for a specific device from Google Sheets.
     */
    public List<Map<String, Object>> getContacts(Long deviceId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!initialized) return result;

        try {
            List<List<Object>> rows = readRange(CONTACTS_RANGE);
            if (rows == null || rows.size() <= 1) return result;

            String deviceIdStr = String.valueOf(deviceId);
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (row.isEmpty()) continue;
                if (deviceIdStr.equals(getStr(row, COL_DEVICE_ID))) {
                    Map<String, Object> contact = new LinkedHashMap<>();
                    contact.put("id", i);
                    contact.put("deviceId", deviceId);
                    contact.put("rawContactId", getStr(row, COL_RAW_CONTACT_ID));
                    contact.put("name", getStr(row, COL_NAME));
                    contact.put("phone", getStr(row, COL_PHONE));
                    contact.put("phoneType", getStr(row, COL_PHONE_TYPE));
                    contact.put("email", getStr(row, COL_EMAIL));
                    contact.put("syncedAt", getStr(row, COL_TIMESTAMP));
                    result.add(contact);
                }
            }
        } catch (Exception e) {
            log.error("GoogleSheets: get contacts error: {}", e.getMessage());
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CALL LOGS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Upsert call logs into Google Sheets.
     * Dedup by deviceId + phoneNumber + callDate + callType + contactName.
     */
    public int syncCallLogs(Long deviceId, List<Map<String, Object>> callLogs) {
        if (!initialized) return 0;

        try {
            ensureSheetExists(SHEET_CALL_LOGS, Arrays.asList(
                    "DeviceID", "PhoneNumber", "CallType", "DurationSec", "CallDate", "ContactName", "Timestamp"));

            List<List<Object>> existingRows = readRange(CALL_LOGS_RANGE);
            Set<String> existingSet = new HashSet<>();
            if (existingRows != null && !existingRows.isEmpty()) {
                for (int i = 1; i < existingRows.size(); i++) {
                    List<Object> row = existingRows.get(i);
                    if (row.size() > CALL_COL_CONTACT_NAME) {
                        String key = deviceId + ":" +
                                getStr(row, CALL_COL_PHONE_NUMBER) + ":" +
                                getStr(row, CALL_COL_CALL_DATE) + ":" +
                                getStr(row, CALL_COL_CALL_TYPE) + ":" +
                                getStr(row, CALL_COL_CONTACT_NAME);
                        existingSet.add(key);
                    }
                }
            }

            String now = timestampNow();
            List<List<Object>> rowsToAppend = new ArrayList<>();
            int skipped = 0;

            for (Map<String, Object> call : callLogs) {
                String phoneNumber = getStr(call, "phoneNumber");
                String callType = getStr(call, "callType");
                String callDate = getStr(call, "callDate");
                String durationSec = String.valueOf(call.get("durationSec"));
                String contactName = getStr(call, "contactName");

                String key = deviceId + ":" + phoneNumber + ":" + callDate + ":" + callType + ":" + contactName;

                if (existingSet.contains(key)) {
                    skipped++;
                    continue;
                }

                rowsToAppend.add(Arrays.asList(
                        String.valueOf(deviceId), phoneNumber, callType, durationSec, callDate, contactName, now));
            }

            if (!rowsToAppend.isEmpty()) {
                appendRange(SHEET_CALL_LOGS + "!A:G", rowsToAppend);
            }

            if (skipped > 0 || !rowsToAppend.isEmpty()) {
                log.info("GoogleSheets: synced {} call logs for device {} (skipped {} dups)",
                        rowsToAppend.size(), deviceId, skipped);
            }
            return rowsToAppend.size();
        } catch (Exception e) {
            log.error("GoogleSheets: call log sync error: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Read call logs for a specific device from Google Sheets.
     */
    public List<Map<String, Object>> getCallLogs(Long deviceId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!initialized) return result;

        try {
            List<List<Object>> rows = readRange(CALL_LOGS_RANGE);
            if (rows == null || rows.size() <= 1) return result;

            String deviceIdStr = String.valueOf(deviceId);
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (row.isEmpty()) continue;
                if (deviceIdStr.equals(getStr(row, CALL_COL_DEVICE_ID))) {
                    Map<String, Object> call = new LinkedHashMap<>();
                    call.put("id", i);
                    call.put("deviceId", deviceId);
                    call.put("phoneNumber", getStr(row, CALL_COL_PHONE_NUMBER));
                    call.put("callType", getStr(row, CALL_COL_CALL_TYPE));
                    call.put("durationSec", parseInt(getStr(row, CALL_COL_DURATION_SEC)));
                    call.put("callDate", parseLong(getStr(row, CALL_COL_CALL_DATE)));
                    call.put("contactName", getStr(row, CALL_COL_CONTACT_NAME));
                    call.put("syncedAt", getStr(row, CALL_COL_TIMESTAMP));
                    result.add(call);
                }
            }
        } catch (Exception e) {
            log.error("GoogleSheets: get call logs error: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Get counts for dashboard.
     */
    public Map<String, Long> getCounts(Long deviceId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("contacts", (long) getContacts(deviceId).size());
        counts.put("callLogs", (long) getCallLogs(deviceId).size());
        return counts;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private void ensureSheetExists(String sheetName, List<String> headers) throws IOException {
        try {
            Spreadsheet spreadsheet;
            if (useApiKey) {
                spreadsheet = sheetsService.spreadsheets().get(spreadsheetId)
                        .setKey(apiKey).execute();
            } else {
                spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
            }
            boolean exists = spreadsheet.getSheets().stream()
                    .anyMatch(s -> sheetName.equals(s.getProperties().getTitle()));
            if (!exists) {
                // Add sheet with headers
                var addRequest = new BatchUpdateSpreadsheetRequest()
                        .setRequests(Collections.singletonList(
                                new Request().setAddSheet(new AddSheetRequest()
                                        .setProperties(new SheetProperties().setTitle(sheetName)))));
                if (useApiKey) {
                    sheetsService.spreadsheets().batchUpdate(spreadsheetId, addRequest)
                            .setKey(apiKey).execute();
                } else {
                    sheetsService.spreadsheets().batchUpdate(spreadsheetId, addRequest).execute();
                }

                // Add header row
                if (!headers.isEmpty()) {
                    String range = sheetName + "!A1:" + (char) ('A' + headers.size() - 1) + "1";
                    updateRange(range, Collections.singletonList(new ArrayList<>(headers)));
                }
            }
        } catch (Exception e) {
            log.warn("GoogleSheets: ensureSheet {} error: {}", sheetName, e.getMessage());
        }
    }

    private List<List<Object>> readRange(String range) throws IOException {
        if (useApiKey) {
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .setKey(apiKey)
                    .execute();
            return response.getValues();
        } else {
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            return response.getValues();
        }
    }

    private void appendRange(String range, List<List<Object>> values) throws IOException {
        var body = new ValueRange().setValues(values);
        if (useApiKey) {
            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .setKey(apiKey)
                    .execute();
        } else {
            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        }
    }

    private void updateRange(String range, List<List<Object>> values) throws IOException {
        var body = new ValueRange().setValues(values);
        if (useApiKey) {
            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .setKey(apiKey)
                    .execute();
        } else {
            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        }
    }

    private String getStr(List<Object> row, int index) {
        if (index >= row.size() || row.get(index) == null) return "";
        return row.get(index).toString();
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    private String timestampNow() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())
                .format(Instant.now());
    }
}
