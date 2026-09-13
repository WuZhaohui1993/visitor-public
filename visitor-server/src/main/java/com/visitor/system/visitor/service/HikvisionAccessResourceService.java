package com.visitor.system.visitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.domain.HikvisionAccessTarget;
import com.visitor.system.visitor.dto.HikvisionAccessResourceInfo;
import com.visitor.system.visitor.dto.HikvisionAccessTargetResp;
import com.visitor.system.visitor.repository.HikvisionAccessTargetRepository;
import com.visitor.system.visitor.service.impl.HikvisionHttpSupport;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 海康门禁权限目标解析服务。
 *
 * <p>权限下发需要知道“把人员授权到哪些门禁资源和通道”。这里优先使用显式配置；
 * 未配置时从海康资源接口自动发现并缓存，避免每次审批都实时扫全量设备。
 */
@Service
public class HikvisionAccessResourceService {

    private static final Logger log = LoggerFactory.getLogger(HikvisionAccessResourceService.class);

    private final VisitorProperties properties;
    private final HikvisionHttpSupport httpSupport;
    private final HikvisionAccessTargetRepository targetRepository;

    public HikvisionAccessResourceService(VisitorProperties properties,
                                          HikvisionHttpSupport httpSupport,
                                          HikvisionAccessTargetRepository targetRepository) {
        this.properties = properties;
        this.httpSupport = httpSupport;
        this.targetRepository = targetRepository;
    }

    @PostConstruct
    public void warmUpDevicesOnStartup() {
        VisitorProperties.Access access = properties.getHikvision().getAccess();
        // 启动预热只在启用自动发现时执行；失败只记录日志，避免海康临时不可用导致业务服务无法启动。
        if (!properties.getHikvision().isEnabled()
            || !access.isEnabled()
            || !access.isAutoDiscoverResources()
            || !access.isRefreshResourcesOnStartup()) {
            return;
        }
        try {
            refreshAccessDevices();
        } catch (Exception exception) {
            log.warn("warm up hikvision access devices failed: {}", exception.getMessage());
        }
    }

    @Scheduled(cron = "${visitor.hikvision.access.resource-refresh-cron:0 0 3 * * MON}")
    public void scheduledRefreshAccessDevices() {
        VisitorProperties.Access access = properties.getHikvision().getAccess();
        // 周期刷新用于吸收海康侧门禁设备调整，审批下发时仍从本地缓存读取，降低实时依赖。
        if (!properties.getHikvision().isEnabled() || !access.isEnabled() || !access.isAutoDiscoverResources()) {
            return;
        }
        try {
            refreshAccessDevices();
        } catch (Exception exception) {
            log.warn("refresh hikvision access devices failed: {}", exception.getMessage());
        }
    }

    public List<HikvisionAccessResourceInfo> resolveAccessResourceInfos() {
        VisitorProperties.Access access = properties.getHikvision().getAccess();
        if (access.getResourceIndexCodes() != null && !access.getResourceIndexCodes().isEmpty()) {
            // 明确配置的门禁资源优先级最高，适合现场只授权固定门点的部署模式。
            return access.getResourceIndexCodes().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .map(code -> HikvisionAccessResourceInfo.builder()
                    .resourceIndexCode(code)
                    .channelNos(List.copyOf(access.getChannelNos()))
                    .build())
                .toList();
        }
        if (!access.isAutoDiscoverResources()) {
            throw new BusinessException("未配置海康权限下发资源列表");
        }
        // 自动发现模式下先用缓存；缓存为空才实时拉取，避免审批接口被海康资源分页查询拖慢。
        List<HikvisionAccessResourceInfo> cachedResources = groupTargets(targetRepository.findAllByOrderByResourceIndexCodeAscChannelNoAsc());
        if (!cachedResources.isEmpty()) {
            return cachedResources;
        }
        return refreshAccessDevices();
    }

    public List<HikvisionAccessTargetResp> listAccessTargets() {
        return targetRepository.findAllByOrderByResourceIndexCodeAscChannelNoAsc().stream()
            .map(target -> HikvisionAccessTargetResp.builder()
                .resourceIndexCode(target.getResourceIndexCode())
                .channelNo(target.getChannelNo())
                .sourceIndexCode(target.getSourceIndexCode())
                .sourceName(target.getSourceName())
                .sourceResourceType(target.getSourceResourceType())
                .build())
            .toList();
    }

    @Transactional
    public List<HikvisionAccessResourceInfo> refreshAccessDevices() {
        VisitorProperties.Access access = properties.getHikvision().getAccess();
        if (!access.isAutoDiscoverResources()) {
            return resolveAccessResourceInfos();
        }
        if (!StringUtils.hasText(access.getResourceQueryPath())) {
            throw new BusinessException("未配置海康门禁设备自动查询接口路径");
        }
        int pageNo = 1;
        int pageSize = access.getResourceQueryPageSize() == null || access.getResourceQueryPageSize() <= 0
            ? 1000 : access.getResourceQueryPageSize();
        LinkedHashMap<String, HikvisionAccessTarget> targets = new LinkedHashMap<>();
        while (true) {
            // 海康资源接口按页返回设备/门点；用 resourceIndexCode + channelNo 去重，保证重复刷新幂等。
            JsonNode root = httpSupport.postRaw(
                access.getResourceQueryPath(),
                buildQueryBody(access, pageNo, pageSize),
                "查询海康门禁设备列表失败"
            );
            JsonNode data = root.path("data");
            List<JsonNode> items = extractItems(data);
            for (JsonNode item : items) {
                HikvisionAccessTarget target = toTarget(item);
                if (target != null) {
                    String key = target.getResourceIndexCode() + "#" + target.getChannelNo();
                    targets.put(key, target);
                }
            }
            if (items.isEmpty() || isLastPage(data, pageNo, pageSize, items.size())) {
                break;
            }
            pageNo++;
        }
        if (targets.isEmpty()) {
            throw new BusinessException("未查询到可下发权限的海康门禁设备");
        }
        targetRepository.deleteAllInBatch();
        targetRepository.saveAll(targets.values());
        return groupTargets(targetRepository.findAllByOrderByResourceIndexCodeAscChannelNoAsc());
    }

    private Map<String, Object> buildQueryBody(VisitorProperties.Access access, int pageNo, int pageSize) {
        // 允许在配置里补充厂商现场要求的固定查询条件，再统一覆盖分页和资源类型。
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        if (access.getResourceQueryBody() != null) {
            body.putAll(access.getResourceQueryBody());
        }
        body.put("pageNo", pageNo);
        body.put("pageSize", pageSize);
        body.put("resourceType", access.getResourceQueryType());
        return body;
    }

    private HikvisionAccessTarget toTarget(JsonNode item) {
        // 权限下发需要父资源或资源自身编码；不同海康资源接口字段名不一致，按可用字段归一化。
        String targetCode = item.path("parentIndexCode").asText();
        if (!StringUtils.hasText(targetCode)) {
            targetCode = item.path("indexCode").asText();
        }
        if (!StringUtils.hasText(targetCode)) {
            targetCode = item.path("resourceIndexCode").asText();
        }
        if (!StringUtils.hasText(targetCode)) {
            return null;
        }
        HikvisionAccessTarget target = new HikvisionAccessTarget();
        target.setResourceIndexCode(targetCode.trim());
        String sourceIndexCode = item.path("indexCode").asText();
        if (StringUtils.hasText(sourceIndexCode)) {
            target.setSourceIndexCode(sourceIndexCode.trim());
        }
        String sourceName = item.path("name").asText();
        if (!StringUtils.hasText(sourceName)) {
            sourceName = item.path("resourceName").asText();
        }
        target.setSourceName(StringUtils.hasText(sourceName) ? sourceName : targetCode.trim());
        String sourceResourceType = item.path("resourceType").asText();
        target.setSourceResourceType(StringUtils.hasText(sourceResourceType) ? sourceResourceType : properties.getHikvision().getAccess().getResourceQueryType());
        int channelNo = item.path("channelNo").asInt(1);
        if (channelNo <= 0) {
            channelNo = 1;
        }
        target.setChannelNo(channelNo);
        return target;
    }

    private List<HikvisionAccessResourceInfo> groupTargets(List<HikvisionAccessTarget> targets) {
        // ACPS 下发参数以资源为单位携带通道列表，因此缓存表按资源聚合成 resourceInfos。
        LinkedHashMap<String, LinkedHashSet<Integer>> grouped = new LinkedHashMap<>();
        for (HikvisionAccessTarget target : targets) {
            if (!StringUtils.hasText(target.getResourceIndexCode())) {
                continue;
            }
            grouped.computeIfAbsent(target.getResourceIndexCode(), key -> new LinkedHashSet<>())
                .add(target.getChannelNo() == null || target.getChannelNo() <= 0 ? 1 : target.getChannelNo());
        }
        return grouped.entrySet().stream()
            .map(entry -> HikvisionAccessResourceInfo.builder()
                .resourceIndexCode(entry.getKey())
                .channelNos(List.copyOf(entry.getValue()))
                .build())
            .toList();
    }

    private List<JsonNode> extractItems(JsonNode data) {
        // 兼容海康返回 list、rows、resourceInfos 或直接数组的几种分页结构。
        List<JsonNode> items = new ArrayList<>();
        if (data == null || data.isMissingNode() || data.isNull()) {
            return items;
        }
        JsonNode listNode = data.path("list");
        if (!listNode.isArray()) {
            listNode = data.path("rows");
        }
        if (!listNode.isArray()) {
            listNode = data.path("resourceInfos");
        }
        if (!listNode.isArray() && data.isArray()) {
            listNode = data;
        }
        if (listNode.isArray()) {
            listNode.forEach(items::add);
        }
        return items;
    }

    private boolean isLastPage(JsonNode data, int pageNo, int pageSize, int currentSize) {
        long total = data.path("total").asLong(-1);
        if (total >= 0) {
            return (long) pageNo * pageSize >= total;
        }
        int totalPage = data.path("totalPage").asInt(-1);
        if (totalPage > 0) {
            return pageNo >= totalPage;
        }
        return currentSize < pageSize;
    }
}
