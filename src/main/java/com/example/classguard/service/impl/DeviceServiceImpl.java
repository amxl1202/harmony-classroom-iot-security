package com.example.classguard.service.impl;

import com.example.classguard.constant.CommandType;
import com.example.classguard.dto.CmdContext;
import com.example.classguard.dto.CommandResponse;
import com.example.classguard.entity.Device;
import com.example.classguard.exception.BusinessException;
import com.example.classguard.exception.ErrorCode;
import com.example.classguard.mapper.DeviceMapper;
import com.example.classguard.service.DeviceService;
import com.example.classguard.service.MqttService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 设备业务实现（API-08 设备列表 / API-09 指令下发；MQTT status 建档）。
 *
 * <p>只依赖 MqttService（发布 cmd）——MqttService 已与业务服务解耦，无循环依赖。
 *
 * <p>两张内存表的分工：
 * <ul>
 *   <li>{@link #pendingCmds}：cmdId → CompletableFuture，仅用于 <b>同步等待唤醒</b>——
 *       cmd_resp 回执到达时经 completeCommand 唤醒 API-09 的阻塞等待；
 *       API-09 超时后该 Future 即移除（不再等待）。</li>
 *   <li>{@link #cmdContexts}：cmdId → {@link CmdContext}，仅用于 <b>回执合并入库</b>——
 *       《数据库设计说明书 V0.3》§4.3：cmd_resp 报文不携带 command/issued_by，
 *       后端发布 cmd 时以此缓存补全信息，收到回执后合并写入 device_records；
 *       <b>3002 超时后仍保留</b>，供晚到回执兜底入库（入库完成后由 removeCmdContext 清理）。</li>
 * </ul>
 */
@Slf4j
@Service
public class DeviceServiceImpl implements DeviceService {

    /** 教室编号（本期固定 c101，多教室预留，见 REST 文档 API-09） */
    private static final String CLASSROOM_ID = "c101";

    /** cmd_resp 回执等待上限 3 秒（REST 文档 §5 / MQTT 文档 §9） */
    private static final long CMD_RESP_TIMEOUT_MS = 3000L;

    /** cmd_id 当日序号的日期格式（cmd-YYYYMMDD-四位序号，MQTT v1.2 §4） */
    private static final DateTimeFormatter CMD_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** cmd 下发上下文保留时长：5 分钟。晚到回执兜底入库的窗口，超过即淘汰（防内存泄漏） */
    private static final long CMD_CONTEXT_TTL_MS = 5 * 60 * 1000L;

    /** 上下文清理检查间隔：1 分钟。放在 sendCommand 内惰性触发，不引入定时任务（设计 V1.6 §4.5 本期无定时任务） */
    private static final long CMD_CONTEXT_SWEEP_INTERVAL_MS = 60 * 1000L;

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private MqttService mqttService;

    /** JSON 序列化器（Spring Boot 自动配置提供），用于组装 cmd 信封报文 */
    @Resource
    private ObjectMapper objectMapper;

    /** 待回执指令：cmdId → Future。MqttMessageProcessor 收到 cmd_resp 后经 completeCommand 唤醒等待 */
    private final Map<String, CompletableFuture<CommandResponse>> pendingCmds = new ConcurrentHashMap<>();

    /** cmd 下发上下文：cmdId → {command, issued_by} 等。DB V0.3 §4.3 缓存，回执入库后清理；超时不清理 */
    private final Map<String, CmdContext> cmdContexts = new ConcurrentHashMap<>();

    /** 当日已分配的 cmd_id 序号（配合 currentDay 实现按日归零） */
    private final AtomicInteger cmdSeq = new AtomicInteger(0);

    /** 当前序号所属日期（yyyyMMdd），日期变化时 cmdSeq 归零 */
    private String currentDay = LocalDate.now().format(CMD_DAY_FORMAT);

    /** 上次清理 cmdContexts 的时刻（毫秒），用于惰性限流，避免每条指令都全表扫描 */
    private volatile long lastContextSweepAt = 0L;

    /**
     * 生成指令唯一标识 cmd-YYYYMMDD-四位序号（MQTT v1.2 §4：cmd_id 由后端生成）。
     * <p>synchronized 保证跨线程不重号；序号超过 9999 自然变为 5 位，
     * cmd_id 长度仍远小于 device_records.cmd_id 的 VARCHAR(32) 上限。
     */
    private synchronized String nextCmdId() {
        String day = LocalDate.now().format(CMD_DAY_FORMAT);
        // 跨日将序号归零，重新从 0001 计数
        if (!day.equals(currentDay)) {
            currentDay = day;
            cmdSeq.set(0);
        }
        // incrementAndGet 保证从 0001 起编
        return String.format("cmd-%s-%04d", day, cmdSeq.incrementAndGet());
    }

    @Override
    public List<Device> listDevices() {
        // API-08 设备列表与在线状态（管理员）：online/lastOnlineAt 由 MQTT status/LWT 驱动
        return deviceMapper.selectAll();
    }

    @Override
    public CommandResponse sendCommand(String deviceId, String command, Map<String, Object> paras, String issuedBy) {
        // ⓪ 指令白名单与参数校验（MQTT v1.2 §8）：非法指令直接 1001，不下发、不等 3 秒
        CommandType commandType = CommandType.fromCode(command);
        if (commandType == null) {
            log.warn("非法指令，拒绝下发：deviceId={}, command={}, issuedBy={}", deviceId, command, issuedBy);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的指令：" + command);
        }
        // paras 允许缺省；显式传 null 时统一按空对象处理，避免后续 NPE 与空指针序列化
        Map<String, Object> safeParas = paras == null ? new LinkedHashMap<>() : paras;
        if (commandType.isNeedsValue()) {
            Object value = safeParas.get("value");
            if (!"armed".equals(value) && !"disarmed".equals(value)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "set_mode 的 paras.value 须为 armed 或 disarmed");
            }
        }

        // ① 先做一次惰性清理（每 60 秒最多一次），淘汰无回执的历史上下文，防止 map 无限增长
        sweepStaleContexts();

        // ② 生成 cmd_id（cmd-YYYYMMDD-四位序号，后端生成，MQTT v1.2 §4）
        String cmdId = nextCmdId();

        // ③ 组装 cmd 信封（MQTT v1.2 §8，字段顺序与文档一致）：v=1, classroom_id, device_id,
        //    msg_type=cmd, cmd_id, issued_by, command, paras
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("v", 1);
        envelope.put("classroom_id", CLASSROOM_ID);
        envelope.put("device_id", deviceId);
        envelope.put("msg_type", "cmd");
        envelope.put("cmd_id", cmdId);
        envelope.put("issued_by", issuedBy);
        envelope.put("command", command);
        envelope.put("paras", safeParas);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (IOException e) {
            // 信封序列化失败属服务器内部错误（cmd_id 尚未发布，无后续清理需求）
            log.error("cmd 信封序列化失败：cmdId={}, deviceId={}", cmdId, deviceId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "指令序列化失败");
        }

        // ④ 先注册等待 Future 再发布：避免 cmd_resp 回执早于等待注册到达而丢失唤醒；
        //    同时按 cmd_id 缓存下发上下文 {command, issued_by}（DB V0.3 §4.3，
        //    cmd_resp 不回传这两个字段，回执入库时据此合并补全）
        CompletableFuture<CommandResponse> future = new CompletableFuture<>();
        pendingCmds.put(cmdId, future);
        cmdContexts.put(cmdId, new CmdContext(cmdId, CLASSROOM_ID, deviceId, command, issuedBy,
                System.currentTimeMillis()));

        // ⑤ 发布 cmd 到 classroom/{cid}/device/{did}/cmd（QoS1，MQTT v1.2 §4）
        try {
            mqttService.publish("classroom/" + CLASSROOM_ID + "/device/" + deviceId + "/cmd", payload, 1);
        } catch (Exception e) {
            // 发布不可达（如 broker 断连）视同设备未响应：清理本次注册的等待与上下文，
            // 返回 3002 覆盖设备离线场景（REST v1.1 API-09 设备离线场景）
            pendingCmds.remove(cmdId);
            cmdContexts.remove(cmdId);
            log.error("cmd 发布失败，视同设备未响应：cmdId={}, deviceId={}", cmdId, deviceId, e);
            throw new BusinessException(ErrorCode.DEVICE_CMD_TIMEOUT);
        }

        // ⑥ 同步等待 cmd_resp，上限 3 秒（REST v1.1 API-09 / MQTT v1.2 §9）
        try {
            return future.get(CMD_RESP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 超时：移除等待 Future（不再唤醒），但保留 cmdContexts 中的上下文不删——
            // 晚到的 cmd_resp 仍可经 getCmdContext 兜底入库（DB V0.3 §4.3）。
            pendingCmds.remove(cmdId);
            log.warn("等待 cmd_resp 超时：cmdId={}, deviceId={}, 等待 {}ms", cmdId, deviceId, CMD_RESP_TIMEOUT_MS);
            // data 携带 {cmdId,...}（result/reason/state 为 null），符合 REST v1.1 §3 3002 超时响应示例；
            // CommandResponse 仅有 @Data（无全参构造器），经 setter 装配出等价报文
            CommandResponse timeoutResp = new CommandResponse();
            timeoutResp.setCmdId(cmdId);
            throw new BusinessException(ErrorCode.DEVICE_CMD_TIMEOUT, timeoutResp);
        } catch (InterruptedException e) {
            // 等待线程被中断：恢复中断标记，按服务器内部错误处理
            Thread.currentThread().interrupt();
            pendingCmds.remove(cmdId);
            log.error("等待 cmd_resp 被中断：cmdId={}", cmdId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } catch (ExecutionException e) {
            // 回执处理链路异常：按服务器内部错误处理
            pendingCmds.remove(cmdId);
            log.error("等待 cmd_resp 执行异常：cmdId={}", cmdId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        // 说明：设备离线（cleanSession=true 无离线消息排队）时 cmd 报文无法送达，
        // 同样走上述 3 秒等待后超时返回 3002（REST v1.1 API-09 设备离线场景）。
    }

    /**
     * 惰性清理过期的 cmd 下发上下文。
     *
     * <p>为什么需要：3002 超时后上下文按设计保留，供晚到的 cmd_resp 兜底入库；
     * 但设备离线/报文丢失时回执永远不会到达，{@code removeCmdContext} 也就不会执行，
     * 上下文会随运行时长无限累积（每条约百字节，长期运行即内存泄漏）。
     *
     * <p>为什么不用 @Scheduled：设计 V1.6 §4.5 明确"本期不设置定时任务"，
     * 故改为在 sendCommand 入口惰性触发，并用时间戳限流为每 60 秒最多一次，
     * 不引入额外线程与调度依赖。并发下可能重复触发，清理逻辑本身幂等，无副作用。
     */
    private void sweepStaleContexts() {
        long now = System.currentTimeMillis();
        // 限流：距上次清理不足 60 秒则直接返回，避免每条指令都遍历整个 map
        if (now - lastContextSweepAt < CMD_CONTEXT_SWEEP_INTERVAL_MS) {
            return;
        }
        lastContextSweepAt = now;

        int before = cmdContexts.size();
        // 淘汰创建超过 5 分钟仍未入库的上下文（晚到回执的兜底窗口已过）
        cmdContexts.entrySet().removeIf(entry -> now - entry.getValue().getCreatedAtMillis() > CMD_CONTEXT_TTL_MS);
        int removed = before - cmdContexts.size();
        if (removed > 0) {
            log.info("清理过期 cmd 下发上下文 {} 条，当前剩余 {} 条", removed, cmdContexts.size());
        }
    }

    @Override
    public void completeCommand(String cmdId, CommandResponse response) {
        CompletableFuture<CommandResponse> future = pendingCmds.remove(cmdId);
        if (future != null) {
            future.complete(response);
        } else {
            // 无等待者：API-09 已超时返回 3002 后回执才到达，或重复回执/非本服务下发的 cmd
            // 执行记录由 recordService.saveFromMqtt 按 cmd_id 幂等入库兜底，此处仅记日志
            log.info("收到无等待者的 cmd_resp：cmdId={}", cmdId);
        }
    }

    @Override
    public void updateOnlineStatus(String classroomId, String deviceId, boolean online) {
        // 组装 Device：device_name 传空串兜底（DB V0.3 §6.3(8)：status 消息不携带设备名称，
        // 设备首次上线自动建档；UPSERT 的 ON DUPLICATE KEY UPDATE 不更新 device_name，预置名称得以保留）
        Device device = new Device();
        device.setClassroomId(classroomId);
        device.setDeviceId(deviceId);
        device.setDeviceName("");
        device.setOnline(online ? 1 : 0);
        // UPSERT：INSERT ... ON DUPLICATE KEY UPDATE online/last_online_at，status 消息与 LWT 遗嘱共用
        deviceMapper.upsert(device);
        log.info("设备在线状态更新：classroomId={}, deviceId={}, online={}", classroomId, deviceId, online);
    }

    @Override
    public CmdContext getCmdContext(String cmdId) {
        // 供回执合并入库读取 {command, issued_by}；无上下文（非本服务下发/已清理）返回 null
        return cmdContexts.get(cmdId);
    }

    @Override
    public void removeCmdContext(String cmdId) {
        // 回执入库完成后调用；3002 超时未入库的上下文会留存，由晚到回执入库后再清
        CmdContext removed = cmdContexts.remove(cmdId);
        log.debug("移除 cmd 下发上下文：cmdId={}, 存在={}", cmdId, removed != null);
    }
}
