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
import com.hmdm.entity.CallLog;
import com.hmdm.entity.DeviceContact;
import com.hmdm.repository.CallLogRepository;
import com.hmdm.repository.DeviceContactRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for syncing device contacts and call logs to Google Sheets + PostgreSQL.
 *
 * DATA FLOW (Production with API key):
 *   Android APK → Apps Script Web App → Google Sheets (WRITE)
 *   Backend Admin API → Google Sheets API Key → Google Sheets (READ)
 *   PostgreSQL = primary read source for contacts & call logs (Sheets = backup)
 *
 * DATA FLOW (Service account available):
 *   Backend writes directly to Sheets + PostgreSQL primary.
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

    // Contacts column indices (0-based)
    private static final int COL_DEVICE_ID = 0;
    private static final int COL_RAW_CONTACT_ID = 1;
    private static final int COL_NAME = 2;
    private static final int COL_PHONE = 3;
    private static final int COL_PHONE_TYPE = 4;
    private static final int COL_EMAIL = 5;
    private static final int COL_TIMESTAMP = 6;

    // Call logs column indices (0-based)
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

    @Value("${google.sheets.webapp-url:}")
    private String webappUrl;

    @Autowired
    private DeviceContactRepository contactRepository;

    @Autowired
    private CallLogRepository callLogRepository;

    @Autowired
    private EntityManager entityManager;

    private Sheets sheetsService;
    private boolean initialized = false;
    private boolean useApiKey = false;

    @PostConstruct
    public void init() {
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            log.warn("GoogleSheetsService: GOOGLE_SHEETS_SPREADSHEET_ID not configured — disabled");
            return;
        }

        // Try service account first (required for Google Sheets write)
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            try {
                GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes()))
                        .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
                sheetsService = createSheetsService(credentials);
                initialized = true;
                log.info("GoogleSheetsService: initialized with service account (read/write to Sheets)");
                return;
            } catch (Exception e) {
                log.warn("GoogleSheetsService: GOOGLE_SHEETS_CREDENTIALS present but invalid. " +
                        "Falling back to API key or PostgreSQL-only mode. Error: {}", e.getMessage());
            }
        }

        // Fall back to API key (read-only Sheets)
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                useApiKey = true;
                sheetsService = createSheetsService(null);
                initialized = true;
                log.info("GoogleSheetsService: initialized with API key — Google Sheets is READ-ONLY. " +
                        "Contacts & call logs written by APK → Apps Script → Sheets. " +
                        "Backend reads from PostgreSQL (primary) + Sheets fallback.");
                return;
            } catch (Exception e) {
                log.warn("GoogleSheetsService: API key initialization failed: {}. Fallback to DB-only.", e.getMessage());
            }
        }

        initialized = true;
        log.info("GoogleSheetsService: no valid Google credentials — " +
                "contacts & call logs stored in PostgreSQL only");
    }

    private Sheets createSheetsService(GoogleCredentials credentials) throws GeneralSecurityException, IOException {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        HttpRequestInitializer requestInitializer;

        if (credentials != null) {
            requestInitializer = new HttpCredentialsAdapter(credentials);
        } else {
            requestInitializer = request -> {};
        }

        return new Sheets.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CONTACTS  (PostgreSQL primary read source + Sheets backup)
    // ═══════════════════════════════════════════════════════════════════

    public int syncContacts(Long deviceId, List<Map<String, Object>> contacts) {
        if (!initialized) return 0;

        int outerBatchSize = 2000;
        int totalProcessed = 0;

        try {
            for (int batchStart = 0; batchStart < contacts.size(); batchStart += outerBatchSize) {
                int batchEnd = Math.min(batchStart + outerBatchSize, contacts.size());
                List<Map<String, Object>> batch = contacts.subList(batchStart, batchEnd);

                // 1. ALWAYS save to PostgreSQL FIRST (primary read source)
                int dbSaved = saveContactsToDb(deviceId, batch);
                if (dbSaved > 0) {
                    totalProcessed += dbSaved;
                    log.info("Contacts: saved {}/{} to PostgreSQL for device {} (batch {}-{})",
                            dbSaved, batch.size(), deviceId, batchStart, batchEnd);
                }

                // 2. Try writing to Google Sheets (backup)
                boolean sheetsWritten = false;
                if (!useApiKey && sheetsService != null && credentialsJson != null && !credentialsJson.isBlank()) {
                    try {
                        syncContactsToSheets(deviceId, batch);
                        sheetsWritten = true;
                        log.debug("Contacts: synced batch {}-{} to Sheets (backup)", batchStart, batchEnd);
                    } catch (Exception e) {
                        log.warn("Contacts: Sheets batch {}-{} failed: {}", batchStart, batchEnd, e.getMessage());
                    }
                }

                // 3. Fallback: write via Apps Script Web App (API key mode)
                if (!sheetsWritten && webappUrl != null && !webappUrl.isBlank()) {
                    try {
                        Map<String, Object> sheetsPayload = new LinkedHashMap<>();
                        sheetsPayload.put("action", "syncAll");
                        sheetsPayload.put("deviceId", String.valueOf(deviceId));
                        sheetsPayload.put("contacts", batch);
                        sendToWebApp(sheetsPayload);
                        log.debug("Contacts: webapp batch {}-{} OK (backup)", batchStart, batchEnd);
                    } catch (Exception we) {
                        log.warn("Contacts: webapp batch {}-{} failed: {}", batchStart, batchEnd, we.getMessage());
                    }
                }

                batch = null;
            }

            log.info("Contacts: synced {} total to PostgreSQL for device {}", totalProcessed, deviceId);
            return contacts.size();

        } catch (Exception e) {
            log.error("Contacts: sync error: {}", e.getMessage());
            return 0;
        }
    }

    public List<Map<String, Object>> getContacts(Long deviceId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!initialized) return result;

        try {
            // 1. PostgreSQL FIRST
            List<DeviceContact> dbContacts = contactRepository.findByDeviceId(deviceId);
            if (!dbContacts.isEmpty()) {
                for (DeviceContact dc : dbContacts) {
                    Map<String, Object> contact = new LinkedHashMap<>();
                    contact.put("id", dc.getId());
                    contact.put("deviceId", dc.getDeviceId());
                    contact.put("rawContactId", dc.getRawContactId());
                    contact.put("name", dc.getName());
                    contact.put("phone", dc.getPhone());
                    contact.put("phoneType", dc.getPhoneType());
                    contact.put("email", dc.getEmail());
                    contact.put("syncedAt", dc.getCreatedAt() != null ? dc.getCreatedAt().toString() : "");
                    result.add(contact);
                }
                log.debug("Contacts: read {} from PostgreSQL for device {}", result.size(), deviceId);
                return result;
            }

            // 2. Fall back to Google Sheets
            if (sheetsService != null && spreadsheetId != null && !spreadsheetId.isBlank()) {
                try {
                    List<Map<String, Object>> sheetsResult = getContactsFromSheets(deviceId);
                    if (!sheetsResult.isEmpty()) {
                        log.debug("Contacts: read {} from Google Sheets for device {} (legacy fallback)",
                                sheetsResult.size(), deviceId);
                        return sheetsResult;
                    }
                } catch (Exception e) {
                    log.debug("Contacts: Google Sheets read failed: {}", e.getMessage());
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Contacts: read error: {}", e.getMessage());
            return result;
        }
    }

    public void deleteContacts(Long deviceId) {
        if (!initialized) return;
        try {
            if (!useApiKey && sheetsService != null && credentialsJson != null && !credentialsJson.isBlank()) {
                try {
                    deleteContactsFromSheets(deviceId);
                    log.info("Contacts: deleted from Google Sheets for device {}", deviceId);
                } catch (Exception e) {
                    log.warn("Contacts: Sheets delete failed: {}", e.getMessage());
                }
            }
            contactRepository.deleteByDeviceId(deviceId);
            log.info("Contacts: deleted all for device {} from PostgreSQL", deviceId);
        } catch (Exception e) {
            log.error("Contacts: delete error: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CALL LOGS  (PostgreSQL primary read source + Sheets backup)
    // ═══════════════════════════════════════════════════════════════════

    public int syncCallLogs(Long deviceId, List<Map<String, Object>> callLogs) {
        if (!initialized) return 0;

        int outerBatchSize = 2000;
        int totalProcessed = 0;

        try {
            for (int batchStart = 0; batchStart < callLogs.size(); batchStart += outerBatchSize) {
                int batchEnd = Math.min(batchStart + outerBatchSize, callLogs.size());
                List<Map<String, Object>> batch = callLogs.subList(batchStart, batchEnd);

                // 1. Save to PostgreSQL (with inner flush/clear every 500 records)
                int saved = saveCallLogsToDb(deviceId, batch);
                if (saved > 0) {
                    totalProcessed += saved;
                    log.info("CallLogs: saved {}/{} to PostgreSQL for device {} (batch {}-{})",
                            saved, batch.size(), deviceId, batchStart, batchEnd);
                }

                // 2. Try writing to Google Sheets (backup)
                if (!useApiKey && sheetsService != null && credentialsJson != null && !credentialsJson.isBlank()) {
                    int sheetsBatchSize = 500;
                    for (int i = 0; i < batch.size(); i += sheetsBatchSize) {
                        int end = Math.min(i + sheetsBatchSize, batch.size());
                        List<Map<String, Object>> sheetsBatch = batch.subList(i, end);
                        try {
                            syncCallLogsToSheets(deviceId, sheetsBatch);
                            log.debug("CallLogs: synced batch {}-{} to Sheets", batchStart + i, batchStart + end);
                        } catch (Exception e) {
                            log.warn("CallLogs: Sheets batch {}-{} failed: {}", batchStart + i, batchStart + end, e.getMessage());
                        }
                    }
                }

                // 3. Fallback: write via Apps Script Web App (batched)
                if (webappUrl != null && !webappUrl.isBlank()) {
                    int webBatchSize = 500;
                    for (int i = 0; i < batch.size(); i += webBatchSize) {
                        int end = Math.min(i + webBatchSize, batch.size());
                        List<Map<String, Object>> webBatch = batch.subList(i, end);
                        try {
                            Map<String, Object> sheetsPayload = new LinkedHashMap<>();
                            sheetsPayload.put("action", "syncAll");
                            sheetsPayload.put("deviceId", String.valueOf(deviceId));
                            sheetsPayload.put("callLogs", webBatch);
                            sendToWebApp(sheetsPayload);
                            log.debug("CallLogs: webapp batch {}-{} OK", batchStart + i, batchStart + end);
                        } catch (Exception we) {
                            log.warn("CallLogs: webapp batch {}-{} failed: {}", batchStart + i, batchStart + end, we.getMessage());
                        }
                    }
                }

                batch = null;
            }

            log.info("CallLogs: synced {} total to PostgreSQL for device {}", totalProcessed, deviceId);
            return callLogs.size();

        } catch (Exception e) {
            log.error("CallLogs: sync error: {}", e.getMessage());
            return 0;
        }
    }

    public List<Map<String, Object>> getCallLogs(Long deviceId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!initialized) return result;

        try {
            // 1. PostgreSQL is PRIMARY read source
            List<CallLog> dbCalls = callLogRepository.findByDeviceIdOrderByCallDateDesc(deviceId);
            if (!dbCalls.isEmpty()) {
                for (CallLog cl : dbCalls) {
                    Map<String, Object> call = new LinkedHashMap<>();
                    call.put("id", cl.getId());
                    call.put("deviceId", cl.getDeviceId());
                    call.put("phoneNumber", cl.getPhoneNumber());
                    call.put("callType", cl.getCallType());
                    call.put("durationSec", cl.getDurationSec());
                    call.put("callDate", cl.getCallDate());
                    call.put("contactName", cl.getContactName());
                    call.put("syncedAt", cl.getCreatedAt() != null ? cl.getCreatedAt().toString() : "");
                    result.add(call);
                }
                log.debug("CallLogs: read {} from PostgreSQL for device {}", result.size(), deviceId);
                return result;
            }

            // 2. Fall back to Google Sheets
            if (sheetsService != null && spreadsheetId != null && !spreadsheetId.isBlank()) {
                try {
                    List<Map<String, Object>> sheetsResult = getCallLogsFromSheets(deviceId);
                    if (!sheetsResult.isEmpty()) {
                        log.debug("CallLogs: read {} from Google Sheets for device {} (legacy)",
                                sheetsResult.size(), deviceId);
                        return sheetsResult;
                    }
                } catch (Exception e) {
                    log.debug("CallLogs: Google Sheets read failed: {}", e.getMessage());
                }
            }

            return result;
        } catch (Exception e) {
            log.error("CallLogs: read error: {}", e.getMessage());
            return result;
        }
    }

    public void deleteCallLogs(Long deviceId) {
        if (!initialized) return;
        try {
            if (!useApiKey && sheetsService != null && credentialsJson != null && !credentialsJson.isBlank()) {
                try {
                    deleteCallLogsFromSheets(deviceId);
                    log.info("CallLogs: deleted from Google Sheets for device {}", deviceId);
                } catch (Exception e) {
                    log.warn("CallLogs: Sheets delete failed: {}", e.getMessage());
                }
            }
            callLogRepository.deleteByDeviceId(deviceId);
            log.info("CallLogs: deleted all for device {} from PostgreSQL", deviceId);
        } catch (Exception e) {
            log.error("CallLogs: delete error: {}", e.getMessage());
        }
    }

    public Map<String, Long> getCounts(Long deviceId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("contacts", 0L);
        counts.put("callLogs", 0L);

        try {
            // Call logs: PostgreSQL ONLY
            counts.put("callLogs", callLogRepository.countByDeviceId(deviceId));

            // Contacts: PostgreSQL FIRST
            Long contactCount = contactRepository.countByDeviceId(deviceId);
            if (contactCount > 0) {
                counts.put("contacts", contactCount);
            } else if (sheetsService != null && spreadsheetId != null && !spreadsheetId.isBlank()) {
                counts.put("contacts", getCountFromSheets(deviceId, SHEET_CONTACTS, COL_DEVICE_ID));
            }

            log.debug("Counts: device {} (contacts={}, callLogs={})",
                    deviceId, counts.get("contacts"), counts.get("callLogs"));
        } catch (Exception e) {
            log.error("Counts: error: {}", e.getMessage());
        }

        return counts;
    }

    private long getCountFromSheets(Long deviceId, String sheetName, int deviceIdCol) {
        if (sheetsService == null || spreadsheetId == null || spreadsheetId.isBlank()) return 0L;
        try {
            String range = sheetName + "!A:A";
            List<List<Object>> rows = readRange(range);
            if (rows == null || rows.size() <= 1) return 0L;

            String deviceIdStr = String.valueOf(deviceId);
            long count = 0;
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (!row.isEmpty() && deviceIdStr.equals(getStr(row, 0))) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.debug("Count from Sheets failed for {}: {}", sheetName, e.getMessage());
            return 0L;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  POSTGRESQL BACKUP METHODS
    // ═══════════════════════════════════════════════════════════════════

    private int saveContactsToDb(Long deviceId, List<Map<String, Object>> contacts) {
        int dbSaved = 0;
        int sinceFlush = 0;
        for (Map<String, Object> contact : contacts) {
            String rawContactId = getStr(contact, "rawContactId");
            String name = getStr(contact, "name");
            String phone = getStr(contact, "phone");
            String phoneType = getStr(contact, "phoneType");
            String email = getStr(contact, "email");

            var existing = contactRepository.findByDeviceIdAndRawContactId(deviceId, rawContactId);
            if (existing.isPresent()) {
                DeviceContact dc = existing.get();
                dc.setName(name);
                dc.setPhone(phone);
                dc.setPhoneType(phoneType);
                dc.setEmail(email);
                contactRepository.save(dc);
            } else {
                contactRepository.save(DeviceContact.builder()
                        .deviceId(deviceId)
                        .rawContactId(rawContactId)
                        .name(name)
                        .phone(phone)
                        .phoneType(phoneType)
                        .email(email)
                        .build());
            }
            dbSaved++;

            sinceFlush++;
            if (sinceFlush >= 500) {
                entityManager.flush();
                entityManager.clear();
                sinceFlush = 0;
            }
        }
        if (sinceFlush > 0) {
            entityManager.flush();
            entityManager.clear();
        }
        return dbSaved;
    }

    private int saveCallLogsToDb(Long deviceId, List<Map<String, Object>> callLogs) {
        int saved = 0;
        int sinceFlush = 0;
        for (Map<String, Object> call : callLogs) {
            String phoneNumber = getStr(call, "phoneNumber");
            String callType = getStr(call, "callType");
            Long callDate = parseLong(getStr(call, "callDate"));
            Integer durationSec = parseInt(String.valueOf(call.getOrDefault("durationSec", 0)));
            String contactName = getStr(call, "contactName");

            boolean exists = callLogRepository.existsByDeviceIdAndPhoneNumberAndCallDateAndCallTypeAndContactName(
                    deviceId, phoneNumber, callDate, callType, contactName);
            if (exists) continue;

            callLogRepository.save(CallLog.builder()
                    .deviceId(deviceId)
                    .phoneNumber(phoneNumber)
                    .callType(callType)
                    .durationSec(durationSec)
                    .callDate(callDate)
                    .contactName(contactName)
                    .build());
            saved++;

            sinceFlush++;
            if (sinceFlush >= 500) {
                entityManager.flush();
                entityManager.clear();
                sinceFlush = 0;
            }
        }
        if (sinceFlush > 0) {
            entityManager.flush();
            entityManager.clear();
        }
        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  GOOGLE SHEETS METHODS
    // ═══════════════════════════════════════════════════════════════════

    private void syncContactsToSheets(Long deviceId, List<Map<String, Object>> contacts) throws IOException {
        ensureSheetExists(SHEET_CONTACTS, Arrays.asList("DeviceID", "RawContactID", "Name", "Phone", "PhoneType", "Email", "Timestamp"));

        List<List<Object>> existingRows = readRange(CONTACTS_RANGE);
        Map<String, Integer> existingMap = new HashMap<>();
        if (existingRows != null && !existingRows.isEmpty()) {
            for (int i = 1; i < existingRows.size(); i++) {
                List<Object> row = existingRows.get(i);
                if (row.size() > COL_PHONE) {
                    String key = deviceId + ":" + getStr(row, COL_RAW_CONTACT_ID);
                    existingMap.put(key, i);
                }
            }
        }

        List<List<Object>> rowsToAppend = new ArrayList<>();
        String now = timestampNow();

        for (Map<String, Object> contact : contacts) {
            String rawContactId = getStr(contact, "rawContactId");
            String name = getStr(contact, "name");
            String phone = getStr(contact, "phone");
            String phoneType = getStr(contact, "phoneType");
            String email = getStr(contact, "email");

            String key = deviceId + ":" + rawContactId;

            if (existingMap.containsKey(key)) {
                int rowIndex = existingMap.get(key);
                String range = SHEET_CONTACTS + "!A" + (rowIndex + 1) + ":G" + (rowIndex + 1);
                updateRange(range, Collections.singletonList(Arrays.asList(
                        String.valueOf(deviceId), rawContactId, name, phone, phoneType, email, now)));
            } else {
                rowsToAppend.add(Arrays.asList(
                        String.valueOf(deviceId), rawContactId, name, phone, phoneType, email, now));
            }
        }

        if (!rowsToAppend.isEmpty()) {
            appendRange(SHEET_CONTACTS + "!A:G", rowsToAppend);
        }
    }

    private List<Map<String, Object>> getContactsFromSheets(Long deviceId) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        if (sheetsService == null || spreadsheetId == null || spreadsheetId.isBlank()) return result;
        List<List<Object>> rows;
        try {
            rows = readRange(CONTACTS_RANGE);
        } catch (Exception e) {
            return result;
        }
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
        return result;
    }

    private void deleteContactsFromSheets(Long deviceId) throws IOException {
        List<List<Object>> rows = readRange(CONTACTS_RANGE);
        if (rows == null || rows.size() <= 1) return;

        String deviceIdStr = String.valueOf(deviceId);
        List<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (!row.isEmpty() && deviceIdStr.equals(getStr(row, COL_DEVICE_ID))) {
                rowsToDelete.add(i);
            }
        }

        Collections.sort(rowsToDelete, Collections.reverseOrder());
        for (int rowIndex : rowsToDelete) {
            String range = SHEET_CONTACTS + "!A" + (rowIndex + 1) + ":G" + (rowIndex + 1);
            updateRange(range, Collections.singletonList(Arrays.asList("", "", "", "", "", "", "")));
        }
    }

    private void syncCallLogsToSheets(Long deviceId, List<Map<String, Object>> callLogs) throws IOException {
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

        for (Map<String, Object> call : callLogs) {
            String key = deviceId + ":" +
                    getStr(call, "phoneNumber") + ":" +
                    getStr(call, "callDate") + ":" +
                    getStr(call, "callType") + ":" +
                    getStr(call, "contactName");

            if (!existingSet.contains(key)) {
                rowsToAppend.add(Arrays.asList(
                        String.valueOf(deviceId),
                        getStr(call, "phoneNumber"),
                        getStr(call, "callType"),
                        String.valueOf(call.getOrDefault("durationSec", 0)),
                        getStr(call, "callDate"),
                        getStr(call, "contactName"),
                        now));
            }
        }

        if (!rowsToAppend.isEmpty()) {
            appendRange(SHEET_CALL_LOGS + "!A:G", rowsToAppend);
        }
    }

    private List<Map<String, Object>> getCallLogsFromSheets(Long deviceId) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        if (sheetsService == null || spreadsheetId == null || spreadsheetId.isBlank()) return result;
        List<List<Object>> rows;
        try {
            rows = readRange(CALL_LOGS_RANGE);
        } catch (Exception e) {
            return result;
        }
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
        return result;
    }

    private void deleteCallLogsFromSheets(Long deviceId) throws IOException {
        List<List<Object>> rows = readRange(CALL_LOGS_RANGE);
        if (rows == null || rows.size() <= 1) return;

        String deviceIdStr = String.valueOf(deviceId);
        List<Integer> rowsToDelete = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (!row.isEmpty() && deviceIdStr.equals(getStr(row, CALL_COL_DEVICE_ID))) {
                rowsToDelete.add(i);
            }
        }

        Collections.sort(rowsToDelete, Collections.reverseOrder());
        for (int rowIndex : rowsToDelete) {
            String range = SHEET_CALL_LOGS + "!A" + (rowIndex + 1) + ":G" + (rowIndex + 1);
            updateRange(range, Collections.singletonList(Arrays.asList("", "", "", "", "", "", "")));
        }
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

    private void sendToWebApp(Map<String, Object> payload) throws IOException {
        if (webappUrl == null || webappUrl.isBlank()) return;

        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);

        URL url = URI.create(webappUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                log.debug("WebApp: POST OK ({} bytes sent)", json.length());
            } else {
                log.warn("WebApp: POST returned HTTP {} for URL {}", code, webappUrl);
            }
        } finally {
            conn.disconnect();
        }
    }
}
