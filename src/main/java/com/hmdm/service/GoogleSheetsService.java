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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
    private static final String SHEET_NOTIFICATIONS = "Notifications";
    private static final String CONTACTS_RANGE = SHEET_CONTACTS + "!A:G";
    private static final String CALL_LOGS_RANGE = SHEET_CALL_LOGS + "!A:G";
    private static final String NOTIFICATIONS_RANGE = SHEET_NOTIFICATIONS + "!A:G";

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

    // Notifications column indices (0-based)
    private static final int NOTIF_COL_DEVICE_ID   = 0;
    private static final int NOTIF_COL_PACKAGE     = 1;
    private static final int NOTIF_COL_APP_NAME    = 2;
    private static final int NOTIF_COL_TITLE       = 3;
    private static final int NOTIF_COL_TEXT        = 4;
    private static final int NOTIF_COL_RECEIVED_AT = 5;
    private static final int NOTIF_COL_TIMESTAMP   = 6;

    @Value("${google.sheets.spreadsheet-id:}")
    private String spreadsheetId;

    @Value("${google.sheets.credentials-json:}")
    private String credentialsJson;

    @Value("${google.sheets.api-key:}")
    private String apiKey;

    @Autowired
    private DeviceContactRepository contactRepository;

    @Autowired
    private CallLogRepository callLogRepository;

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
                log.info("GoogleSheetsService: contacts & call logs will also sync to PostgreSQL");
                return;
            } catch (Exception e) {
                log.warn("GoogleSheetsService: GOOGLE_SHEETS_CREDENTIALS present but invalid (not a valid JSON service account key). " +
                        "Falling back to API key or PostgreSQL-only mode. Error: {}", e.getMessage());
                // Fall through to try API key next
            }
        }

        // Fall back to API key (read-only Sheets) + PostgreSQL for writes
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                useApiKey = true;
                sheetsService = createSheetsService(null);
                initialized = true;
                log.info("GoogleSheetsService: initialized with API key — " +
                        "Google Sheets is READ-ONLY. Contacts & call logs " +
                        "will be stored in PostgreSQL instead.");
                return;
            } catch (Exception e) {
                log.warn("GoogleSheetsService: API key initialization failed: {}. Fallback to DB-only.", e.getMessage());
            }
        }

        // No working credentials — DB-only mode (no Google Sheets at all)
        initialized = true;
        log.info("GoogleSheetsService: no valid Google credentials — " +
                "contacts & call logs stored in PostgreSQL only");
    }

    /**
     * Test if we can write to the sheet. Logs a warning if we can't.
     */


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
    //  CONTACTS  (PostgreSQL primary, Google Sheets optional)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Upsert contacts into PostgreSQL (primary) + Google Sheets (if service account available).
     * Dedup by deviceId + rawContactId.
     */
    public int syncContacts(Long deviceId, List<Map<String, Object>> contacts) {
        if (!initialized) return 0;

        try {
            // Always save to PostgreSQL
            int dbSaved = 0;
            for (Map<String, Object> contact : contacts) {
                String rawContactId = getStr(contact, "rawContactId");
                String name = getStr(contact, "name");
                String phone = getStr(contact, "phone");
                String phoneType = getStr(contact, "phoneType");
                String email = getStr(contact, "email");

                // Dedup check: find by deviceId + rawContactId
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
            }

            // Also sync to Google Sheets if we have a service account (write access)
            if (!useApiKey && sheetsService != null && credentialsJson != null && !credentialsJson.isBlank()) {
                try {
                    syncContactsToSheets(deviceId, contacts);
                } catch (Exception e) {
                    log.warn("GoogleSheets: sheets sync failed (this is ok — data is in DB): {}", e.getMessage());
                }
            }

            if (dbSaved > 0) {
                log.info("Contacts: saved {} to PostgreSQL for device {}", dbSaved, deviceId);
            }
            return dbSaved;
        } catch (Exception e) {
            log.error("Contacts: DB sync error: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Read contacts for a specific device — prefers Google Sheets (primary storage),
     * falls back to PostgreSQL (legacy data).
     * Data is now written directly to Google Sheets by the Android APK via the
     * Apps Script web app, so Sheets is the primary read source.
     */
    public List<Map<String, Object>> getContacts(Long deviceId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!initialized) return result;

        try {
            // 1. Try Google Sheets first (primary storage for new data)
            if (sheetsService != null && spreadsheetId != null && !spreadsheetId.isBlank()) {
                try {
                    List<Map<String, Object>> sheetsResult = getContactsFromSheets(deviceId);
                    if (!sheetsResult.isEmpty()) {
                        log.debug("Contacts: read {} from Google Sheets for device {}",
                                sheetsResult.size(), deviceId);
                        return sheetsResult;
                    }
                } catch (Exception e) {
                    log.debug("Contacts: Google Sheets read failed (will try DB): {}", e.getMessage());
                }
            }

            // 2. Fall back to PostgreSQL (legacy data from before Sheets migration)
            List<DeviceContact> dbContacts = contactRepository.findByDeviceId(deviceId);
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

            return result;
        } catch (Exception e) {
            log.error("Contacts: read error: {}", e.getMessage());
            return result;
        }
    }

    /**
     * Delete all contacts for a device from PostgreSQL.
     */
    public void deleteContacts(Long deviceId) {
        if (!initialized) return;
        try {
            contactRepository.deleteByDeviceId(deviceId);
            log.info("Contacts: deleted all for device {}", deviceId);
        } catch (Exception e) {
            log.error("Contacts: delete error: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CALL LOGS  (PostgreSQL primary, Google Sheets optional)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Upsert call logs into PostgreSQL (primary) + Google Sheets (if service account available).
     * Dedup by deviceId + phoneNumber + callDate + callType + contactName.
     */
    public int syncCallLogs(Long deviceId, List<Map<String, Object>> callLogs) {
        if (!initialized) return 0;

        try {
            int saved = 0;
            int skipped = 0;

            for (Map<String, Object> call : callLogs) {
                String phoneNumber = getStr(call, "phoneNumber");
                String callType = getStr(call, "callType");
                Long callDate = parseLong(getStr(call, "callDate"));
                Integer durationSec = parseInt(String.valueOf(call.getOrDefault("durationSec", 0)));
                String contactName = getStr(call, "contactName");

                // Dedup check via repository
                boolean exists = callLogRepository.existsByDeviceIdAndPhoneNumberAndCallDateAndCallTypeAndContactName(
                        deviceId, phoneNumber, callDate, callType, contactName);
                if (exists) {
                    skipped++;
                    continue;
                }

                callLogRepository.save(CallLog.builder()
                        .deviceId(deviceId)
                        .phoneNumber(phoneNumber)
                        .callType(callType)
                        .durationSec(durationSec)
                        .callDate(callDate)
                        .contactName(contactName)
                        .build());
                saved++;
            }

            // Also sync to Google Sheets if we have a service account (write access)
            if (!useApiKey && sheetsService != null && credentialsJson != null && !credentialsJson.isBlank()) {
                try {
                    syncCallLogsToSheets(deviceId, callLogs);
                } catch (Exception e) {
                    log.warn("GoogleSheets: call log sheets sync failed (data is in DB): {}", e.getMessage());
                }
            }

            if (saved > 0 || skipped > 0) {
                log.info("CallLogs: saved {} to PostgreSQL for device {} (skipped {} dups)",
                        saved, deviceId, skipped);
            }
            return saved;
        } catch (Exception e) {
            log.error("CallLogs: DB sync error: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Read call logs for a specific device — prefers Google Sheets (primary storage),
     * falls back to PostgreSQL (legacy data).
     * Data is now written directly to Google Sheets by the Android APK via the
     * Apps Script web app, so Sheets is the primary read source.
     */
    public List<Map<String, Object>> getCallLogs(Long deviceId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!initialized) return result;

        try {
            // 1. Try Google Sheets first (primary storage for new data)
            if (sheetsService != null && spreadsheetId != null && !spreadsheetId.isBlank()) {
                try {
                    List<Map<String, Object>> sheetsResult = getCallLogsFromSheets(deviceId);
                    if (!sheetsResult.isEmpty()) {
                        log.debug("CallLogs: read {} from Google Sheets for device {}",
                                sheetsResult.size(), deviceId);
                        return sheetsResult;
                    }
                } catch (Exception e) {
                    log.debug("CallLogs: Google Sheets read failed (will try DB): {}", e.getMessage());
                }
            }

            // 2. Fall back to PostgreSQL (legacy data from before Sheets migration)
            List<CallLog> dbCalls = callLogRepository.findByDeviceIdOrderByCallDateDesc(deviceId);
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

            return result;
        } catch (Exception e) {
            log.error("CallLogs: read error: {}", e.getMessage());
            return result;
        }
    }

    /**
     * Delete all call logs for a device from PostgreSQL.
     */
    public void deleteCallLogs(Long deviceId) {
        if (!initialized) return;
        try {
            callLogRepository.deleteByDeviceId(deviceId);
            log.info("CallLogs: deleted all for device {}", deviceId);
        } catch (Exception e) {
            log.error("CallLogs: delete error: {}", e.getMessage());
        }
    }

    /**
     * Get counts for dashboard — reads from Google Sheets (primary),
     * falls back to PostgreSQL (legacy).
     */
    public Map<String, Long> getCounts(Long deviceId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("contacts", 0L);
        counts.put("callLogs", 0L);
        counts.put("notifications", 0L);

        try {
            // 1. Try Google Sheets first (primary storage)
            if (sheetsService != null && spreadsheetId != null && !spreadsheetId.isBlank()) {
                try {
                    List<Map<String, Object>> contactsFromSheets = getContactsFromSheets(deviceId);
                    counts.put("contacts", (long) contactsFromSheets.size());
                } catch (Exception e) {
                    log.debug("Counts: Sheets contacts count failed: {}", e.getMessage());
                }

                try {
                    List<Map<String, Object>> callsFromSheets = getCallLogsFromSheets(deviceId);
                    counts.put("callLogs", (long) callsFromSheets.size());
                } catch (Exception e) {
                    log.debug("Counts: Sheets call logs count failed: {}", e.getMessage());
                }

                try {
                    List<Map<String, Object>> notifsFromSheets = getNotificationsFromSheets(deviceId);
                    counts.put("notifications", (long) notifsFromSheets.size());
                } catch (Exception e) {
                    log.debug("Counts: Sheets notifications count failed: {}", e.getMessage());
                }

                // If Sheets returned any data, use it (don't fall back to DB)
                if (counts.get("contacts") > 0 || counts.get("callLogs") > 0 || counts.get("notifications") > 0) {
                    log.debug("Counts: read from Google Sheets for device {} (contacts={}, callLogs={}, notifications={})",
                            deviceId, counts.get("contacts"), counts.get("callLogs"), counts.get("notifications"));
                    return counts;
                }
            }

            // 2. Fall back to PostgreSQL (legacy data)
            counts.put("contacts", contactRepository.countByDeviceId(deviceId));
            counts.put("callLogs", callLogRepository.countByDeviceId(deviceId));
            log.debug("Counts: read from PostgreSQL for device {} (contacts={}, callLogs={})",
                    deviceId, counts.get("contacts"), counts.get("callLogs"));
            // Notifications count from DB is handled separately in the controller
        } catch (Exception e) {
            log.error("Counts: error: {}", e.getMessage());
        }

        return counts;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  NOTIFICATIONS (Google Sheets primary, PostgreSQL fallback)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Read notifications for a specific device — prefers Google Sheets (primary),
     * falls back to PostgreSQL (legacy).
     */
    public List<Map<String, Object>> getNotifications(Long deviceId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!initialized) return result;

        try {
            // 1. Try Google Sheets first (primary storage for new data)
            if (sheetsService != null && spreadsheetId != null && !spreadsheetId.isBlank()) {
                try {
                    List<Map<String, Object>> sheetsResult = getNotificationsFromSheets(deviceId);
                    if (!sheetsResult.isEmpty()) {
                        log.debug("Notifications: read {} from Google Sheets for device {}",
                                sheetsResult.size(), deviceId);
                        return sheetsResult;
                    }
                } catch (Exception e) {
                    log.debug("Notifications: Google Sheets read failed (will try DB): {}", e.getMessage());
                }
            }

            // 2. Fall back to PostgreSQL result is empty (legacy data)
            // The controller handles the DB read since we don't inject NotificationRepository here
            return result;
        } catch (Exception e) {
            log.error("Notifications: read error: {}", e.getMessage());
            return result;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  GOOGLE SHEETS SPECIFIC METHODS (used only with service account)
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
        return result;
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
        return result;
    }

    private List<Map<String, Object>> getNotificationsFromSheets(Long deviceId) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        List<List<Object>> rows = readRange(NOTIFICATIONS_RANGE);
        if (rows == null || rows.size() <= 1) return result;

        String deviceIdStr = String.valueOf(deviceId);
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty()) continue;
            if (deviceIdStr.equals(getStr(row, NOTIF_COL_DEVICE_ID))) {
                Map<String, Object> notif = new LinkedHashMap<>();
                notif.put("id", i);
                notif.put("deviceId", deviceId);
                notif.put("packageName", getStr(row, NOTIF_COL_PACKAGE));
                notif.put("appName", getStr(row, NOTIF_COL_APP_NAME));
                notif.put("title", getStr(row, NOTIF_COL_TITLE));
                notif.put("text", getStr(row, NOTIF_COL_TEXT));
                notif.put("receivedAt", parseLong(getStr(row, NOTIF_COL_RECEIVED_AT)));
                notif.put("syncedAt", getStr(row, NOTIF_COL_TIMESTAMP));
                result.add(notif);
            }
        }
        return result;
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
