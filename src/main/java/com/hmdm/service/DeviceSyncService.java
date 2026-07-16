package com.hmdm.service;

import com.hmdm.dto.device.*;
import com.hmdm.entity.*;
import com.hmdm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceSyncService {

    private final DeviceRepository deviceRepository;
    private final ConfigurationRepository configurationRepository;
    private final ApplicationRepository applicationRepository;
    private final PushMessageRepository pushMessageRepository;
    private final DeviceLocationRepository locationRepository;
    private final DeviceApplicationRepository deviceApplicationRepository;

    /**
     * Enroll device or get its configuration by device number.
     * Called by Android APK: POST/GET /{project}/rest/public/sync/configuration/{number}
     */
    @Transactional
    public ServerConfigDto enrollAndGetConfig(String number, DeviceEnrollDto enrollDto) {
        // Find or create device
        Device device = deviceRepository.findByNumber(number).orElseGet(() -> {
            Device d = Device.builder()
                    .customerId(1L)
                    .number(number)
                    .status("PENDING")
                    .enrolledAt(LocalDateTime.now())
                    .build();
            return deviceRepository.save(d);
        });

        // Update status
        device.setStatus("ONLINE");
        device.setLastSync(LocalDateTime.now());
        deviceRepository.save(device);

        // Get configuration
        if (device.getConfigId() == null) {
            // Assign default config if exists
            List<Configuration> configs = configurationRepository.findByCustomerId(device.getCustomerId());
            if (!configs.isEmpty()) {
                device.setConfigId(configs.get(0).getId());
                deviceRepository.save(device);
            }
        }

        return buildServerConfig(device);
    }

    /**
     * Get config for already-enrolled device.
     */
    @Transactional
    public ServerConfigDto getConfig(String number) {
        Device device = deviceRepository.findByNumber(number).orElseGet(() -> {
            Device d = Device.builder()
                    .customerId(1L)
                    .number(number)
                    .status("PENDING")
                    .enrolledAt(LocalDateTime.now())
                    .build();
            return deviceRepository.save(d);
        });
        device.setLastSync(LocalDateTime.now());
        device.setStatus("ONLINE");
        deviceRepository.save(device);
        return buildServerConfig(device);
    }

    /**
     * Receive device info payload from Android APK.
     * Called by: POST /{project}/rest/public/sync/info
     */
    @Transactional
    public void updateDeviceInfo(DeviceInfoDto info) {
        if (info.getDeviceId() == null) return;

        Device device = deviceRepository.findByNumber(info.getDeviceId()).orElseGet(() -> {
            Device d = Device.builder()
                    .customerId(1L)
                    .number(info.getDeviceId())
                    .status("PENDING")
                    .enrolledAt(LocalDateTime.now())
                    .build();
            return deviceRepository.save(d);
        });

        // Update device fields
        if (info.getModel() != null)          device.setModel(info.getModel());
        if (info.getImei() != null)           device.setImei(info.getImei());
        if (info.getImei2() != null)          device.setImei2(info.getImei2());
        if (info.getPhone() != null)          device.setPhone(info.getPhone());
        if (info.getPhone2() != null)         device.setPhone2(info.getPhone2());
        if (info.getIccid() != null)          device.setIccid(info.getIccid());
        if (info.getIccid2() != null)         device.setIccid2(info.getIccid2());
        if (info.getImsi() != null)           device.setImsi(info.getImsi());
        if (info.getImsi2() != null)          device.setImsi2(info.getImsi2());
        if (info.getCpu() != null)            device.setCpu(info.getCpu());
        if (info.getSerial() != null)         device.setSerial(info.getSerial());
        if (info.getAndroidVersion() != null) device.setAndroidVersion(info.getAndroidVersion());
        if (info.getBatteryLevel() != null)   device.setBatteryLevel(info.getBatteryLevel());
        if (info.getBatteryCharging() != null) device.setBatteryCharging(info.getBatteryCharging());
        if (info.getMdmMode() != null)        device.setMdmMode(info.getMdmMode());
        if (info.getKioskMode() != null)      device.setKioskMode(info.getKioskMode());
        if (info.getDefaultLauncher() != null) device.setDefaultLauncher(info.getDefaultLauncher());
        if (info.getLauncherType() != null)   device.setLauncherType(info.getLauncherType());
        if (info.getLauncherPackage() != null) device.setLauncherPackage(info.getLauncherPackage());
        if (info.getCustom1() != null)        device.setCustom1(info.getCustom1());
        if (info.getCustom2() != null)        device.setCustom2(info.getCustom2());
        if (info.getCustom3() != null)        device.setCustom3(info.getCustom3());

        // Save location if present
        if (info.getLocation() != null) {
            DeviceInfoDto.Location loc = info.getLocation();
            device.setLat(loc.getLat());
            device.setLon(loc.getLon());
            device.setLocationTs(loc.getTs());
            DeviceLocation dl = DeviceLocation.builder()
                    .deviceId(device.getId())
                    .lat(loc.getLat())
                    .lon(loc.getLon())
                    .ts(loc.getTs())
                    .build();
            locationRepository.save(dl);
        }

        device.setLastSync(LocalDateTime.now());
        device.setStatus("ONLINE");
        deviceRepository.save(device);

        // Save installed apps reported by APK — uses native upsert to prevent duplicate key errors
        if (info.getApplications() != null && !info.getApplications().isEmpty()) {
            for (DeviceInfoDto.InstalledAppDto appDto : info.getApplications()) {
                if (appDto.getPkg() == null) continue;
                deviceApplicationRepository.upsert(
                        device.getId(),
                        appDto.getPkg(),
                        appDto.getName(),
                        appDto.getVersion(),
                        appDto.getCode(),
                        true);
            }
        }
    }

    /**
     * Return pending push messages for device.
     */
    @Transactional
    public List<PushMessageDto> getPushMessages(String number) {
        Optional<Device> deviceOpt = deviceRepository.findByNumber(number);
        if (deviceOpt.isEmpty()) return List.of();

        Device device = deviceOpt.get();
        List<PushMessage> messages = pushMessageRepository.findByDeviceIdAndSentFalseOrderByCreatedAtAsc(device.getId());
        List<PushMessageDto> result = new ArrayList<>();
        for (PushMessage msg : messages) {
            msg.setSent(true);
            msg.setSentAt(LocalDateTime.now());
            pushMessageRepository.save(msg);
            result.add(new PushMessageDto(msg.getMessageType(), msg.getPayload()));
        }
        return result;
    }

    // Build the server config response from DB
    private ServerConfigDto buildServerConfig(Device device) {
        ServerConfigDto dto = new ServerConfigDto();
        if (device.getConfigId() == null) return dto;

        Configuration cfg = configurationRepository.findById(device.getConfigId()).orElse(null);
        if (cfg == null) return dto;

        // Map configuration fields
        dto.setBackgroundColor(cfg.getBackgroundColor());
        dto.setTextColor(cfg.getTextColor());
        dto.setBackgroundImageUrl(cfg.getBackgroundImageUrl());
        dto.setIconSize(cfg.getIconSize());
        dto.setTitle(cfg.getTitle());
        dto.setDisplayStatus(cfg.getDisplayStatus());
        dto.setGps(cfg.getGps());
        dto.setBluetooth(cfg.getBluetooth());
        dto.setWifi(cfg.getWifi());
        dto.setMobileData(cfg.getMobileData());
        dto.setKioskMode(cfg.getKioskMode());
        dto.setMainApp(cfg.getMainApp());
        dto.setLockStatusBar(cfg.getLockStatusBar());
        dto.setSystemUpdateType(cfg.getSystemUpdateType());
        dto.setSystemUpdateFrom(cfg.getSystemUpdateFrom());
        dto.setSystemUpdateTo(cfg.getSystemUpdateTo());
        dto.setPushOptions(cfg.getPushOptions());
        dto.setKeepaliveTime(cfg.getKeepaliveTime());
        dto.setRequestUpdates(cfg.getRequestUpdates());
        dto.setDisableLocation(cfg.getDisableLocation());
        dto.setAppPermissions(cfg.getAppPermissions());
        dto.setUsbStorage(cfg.getUsbStorage());
        dto.setAutoBrightness(cfg.getAutoBrightness());
        dto.setBrightness(cfg.getBrightness());
        dto.setManageTimeout(cfg.getManageTimeout());
        dto.setTimeout(cfg.getTimeoutVal());
        dto.setLockVolume(cfg.getLockVolume());
        dto.setManageVolume(cfg.getManageVolume());
        dto.setVolume(cfg.getVolume());
        dto.setPasswordMode(cfg.getPasswordMode());
        dto.setTimeZone(cfg.getTimeZone());
        dto.setOrientation(cfg.getOrientation());
        dto.setKioskHome(cfg.getKioskHome());
        dto.setKioskRecents(cfg.getKioskRecents());
        dto.setKioskNotifications(cfg.getKioskNotifications());
        dto.setKioskSystemInfo(cfg.getKioskSystemInfo());
        dto.setKioskKeyguard(cfg.getKioskKeyguard());
        dto.setKioskLockButtons(cfg.getKioskLockButtons());
        dto.setKioskScreenOn(cfg.getKioskScreenOn());
        dto.setLockSafeSettings(cfg.getLockSafeSettings());
        dto.setPermissive(cfg.getPermissive());
        dto.setKioskExit(cfg.getKioskExit());
        dto.setDisableScreenshots(cfg.getDisableScreenshots());
        dto.setAutostartForeground(cfg.getAutostartForeground());
        dto.setShowWifi(cfg.getShowWifi());
        dto.setRestrictions(cfg.getRestrictions());
        dto.setDescription(cfg.getDescription());
        dto.setCustom1(cfg.getCustom1());
        dto.setCustom2(cfg.getCustom2());
        dto.setCustom3(cfg.getCustom3());
        dto.setNewServerUrl(cfg.getNewServerUrl());
        dto.setPassword(cfg.getPassword());

        // Applications
        List<ServerConfigDto.AppDto> apps = new ArrayList<>();
        for (ConfigurationApplication ca : cfg.getApplications()) {
            Application app = ca.getApplication();
            ServerConfigDto.AppDto appDto = new ServerConfigDto.AppDto();
            appDto.setType(app.getType());
            appDto.setName(app.getName());
            appDto.setPkg(app.getPkg());
            appDto.setVersion(app.getVersion());
            appDto.setCode(app.getVersionCode());
            appDto.setUrl(app.getUrl());
            appDto.setUseKiosk(ca.getUseKiosk());
            appDto.setShowIcon(ca.getShowIcon());
            appDto.setRemove(ca.getRemove());
            appDto.setRunAfterInstall(ca.getRunAfterInstall());
            appDto.setRunAtBoot(ca.getRunAtBoot());
            appDto.setSkipVersion(ca.getSkipVersion());
            appDto.setIconText(ca.getIconText());
            appDto.setIcon(app.getIcon());
            appDto.setScreenOrder(ca.getScreenOrder());
            appDto.setKeyCode(ca.getKeyCode());
            appDto.setBottom(ca.getBottom());
            appDto.setLongTap(ca.getLongTap());
            appDto.setIntent(ca.getIntent());
            apps.add(appDto);
        }
        dto.setApplications(apps);

        // App settings
        List<ServerConfigDto.AppSettingDto> settings = new ArrayList<>();
        for (ApplicationSetting s : cfg.getApplicationSettings()) {
            ServerConfigDto.AppSettingDto sd = new ServerConfigDto.AppSettingDto();
            sd.setPackageId(s.getPackageId());
            sd.setName(s.getName());
            sd.setValue(s.getValue());
            sd.setType(s.getType());
            sd.setLastUpdate(s.getLastUpdate());
            sd.setReadOnly(s.getReadOnly());
            sd.setIsVariable(s.getIsVariable());
            settings.add(sd);
        }
        dto.setApplicationSettings(settings);

        // Remote files
        List<ServerConfigDto.RemoteFileDto> files = new ArrayList<>();
        for (RemoteFile f : cfg.getFiles()) {
            ServerConfigDto.RemoteFileDto fd = new ServerConfigDto.RemoteFileDto();
            fd.setPath(f.getPath());
            fd.setUrl(f.getUrl());
            fd.setDescription(f.getDescription());
            fd.setChecksum(f.getChecksum());
            fd.setRemove(f.getRemove());
            files.add(fd);
        }
        dto.setFiles(files);

        return dto;
    }
}
