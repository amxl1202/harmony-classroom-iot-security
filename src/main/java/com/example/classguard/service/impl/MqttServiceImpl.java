package com.example.classguard.service.impl;

import com.example.classguard.config.MqttConfig;
import com.example.classguard.service.MqttMessageListener;
import com.example.classguard.service.MqttService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT 订阅模块实现（设计说明书 §4.4 / MQTT 接口文档 v1.2）。
 * <p>以 backend 账号、ClientID backend-service 连接 EMQX，订阅 classroom/+/device/+/# 全部上行主题
 * （MQTT 接口文档 v1.2 §3）。本模块<strong>只负责连接/订阅/发布</strong>与 Paho 回调适配，
 * 业务分发经 {@link MqttMessageListener} 解耦（上行消息由启动流程注册的
 * MqttMessageProcessor 消费），自身不依赖任何业务服务，避免循环依赖。
 *
 * <p>连接约定（MQTT 接口文档 v1.2 §2）：KeepAlive 60s、Clean Session true、断线自动重连。
 * 已知边界（接受）：cleanSession=true，后端离线期间 QoS1 消息不排队，告警丢失不补传。
 */
@Slf4j
@Service
public class MqttServiceImpl implements MqttService {

    /** 后端通配订阅全部上行主题（data/status/alarm/cmd_resp，MQTT 接口文档 v1.2 §3） */
    private static final String SUBSCRIBE_TOPIC = "classroom/+/device/+/#";

    /** MQTT 连接参数（application.properties 的 mqtt.*，松弛绑定到 MqttConfig） */
    @Resource
    private MqttConfig mqttConfig;

    /** 上行消息监听器（StartupCheckRunner 启动时注册 MqttMessageProcessor；写少读多，CopyOnWriteArrayList 保证遍历安全） */
    private final List<MqttMessageListener> listeners = new CopyOnWriteArrayList<>();

    /** Paho 客户端（volatile：连接/重连线程与业务发布线程间的可见性） */
    private volatile MqttClient client;

    /**
     * 上行消息处理线程池（单线程 + 有界队列）。
     *
     * <p>为什么不能在 Paho 回调线程里直接处理：{@code messageArrived} 在 Paho 的收包线程执行，
     * QoS1 的 PUBACK 要等回调返回后才发出；而监听器内部要写 MySQL（告警入库、回执入库）。
     * DB 一旦变慢，收包线程被卡住 → ACK 延迟、KeepAlive 心跳延迟，严重时被 broker 判离线。
     *
     * <p>线程数为 1：保证消息按到达顺序串行处理（状态"后到覆盖"语义不被打乱），
     * 单线程也足够支撑 2 秒一帧的 data 与偶发告警/回执。
     * 队列满时使用 CallerRunsPolicy：由 Paho 线程执行，形成反向背压（宁可慢，不丢消息）。
     */
    private ThreadPoolExecutor inboundExecutor;

    /**
     * 初始化上行消息执行器。
     * <p>@PostConstruct：在 Spring 完成依赖注入后、MQTT 连接建立前创建，
     * 保证 connectAndSubscribe 可能触发的回调一定有执行器可用。
     */
    @PostConstruct
    public void initInboundExecutor() {
        AtomicInteger seq = new AtomicInteger(0);
        this.inboundExecutor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1000),
                r -> {
                    // 守护线程：不阻止 JVM 退出；命名便于日志定位（mqtt-inbound-1）
                    Thread t = new Thread(r, "mqtt-inbound-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        log.info("MQTT 上行消息执行器已初始化：1 线程 + 队列 1000（避免阻塞 Paho 收包线程）");
    }

    @Override
    public void registerListener(MqttMessageListener listener) {
        listeners.add(listener);
    }

    @Override
    public void connectAndSubscribe() {
        // ① 幂等保护：已连接则直接返回，允许重复调用而不重建连接
        if (client != null && client.isConnected()) {
            log.info("MQTT 已连接（broker={}），跳过重复连接", mqttConfig.getBroker());
            return;
        }

        try {
            // ② 创建 Paho 客户端：内存持久化即可（cleanSession=true 无离线排队需求）
            MqttClient newClient = new MqttClient(mqttConfig.getBroker(), mqttConfig.getClientId(), new MemoryPersistence());

            // ③ 连接参数（MQTT 接口文档 v1.2 §2）
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(mqttConfig.getUsername());
            options.setPassword(mqttConfig.getPassword().toCharArray());
            // KeepAlive 60s（MQTT v1.2 §2）
            options.setKeepAliveInterval(60);
            // Clean Session true；边界：后端离线期间 QoS1 消息不排队、告警丢失不补传（接受）
            options.setCleanSession(true);
            // 断线自动重连（设计说明书 §4.4）
            options.setAutomaticReconnect(true);

            // ④ 回调设置：使用 MqttCallbackExtended 以获得 connectComplete（含重连完成）
            newClient.setCallback(new MqttCallbackExtended() {

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("MQTT 连接完成（reconnect={}, serverURI={}）", reconnect, serverURI);
                    // cleanSession=true 时 broker 不保留会话（含订阅关系），
                    // 因此每次连接/重连成功都必须重新订阅，否则重连后收不到任何上行消息
                    try {
                        // 此处引用本次连接创建的客户端实例（回调时序可能早于字段赋值，避免空引用）
                        newClient.subscribe(SUBSCRIBE_TOPIC, 1);
                        log.info("订阅成功（QoS1）：topic={}", SUBSCRIBE_TOPIC);
                    } catch (MqttException e) {
                        log.error("MQTT 订阅失败：topic={}", SUBSCRIBE_TOPIC, e);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    // setAutomaticReconnect(true) 已启用，Paho 将自动重连，无需人工干预
                    log.warn("MQTT 连接断开，自动重连中：{}", (cause == null ? "未知原因" : cause.getMessage()));
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // 上行报文统一 UTF-8（MQTT v1.2 §4）
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    // 只做「取出报文 + 投递任务」两件事，立即返回让 Paho 发 PUBACK、继续收下一帧；
                    // 真正的解析与落库在 mqtt-inbound 线程串行执行
                    try {
                        inboundExecutor.execute(() -> dispatch(topic, payload));
                    } catch (Exception e) {
                        // 执行器已关闭（停机阶段）或拒绝任务：直接在当前线程兜底处理一次
                        log.warn("上行消息投递执行器失败，改为同步处理：topic={}", topic, e);
                        dispatch(topic, payload);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 空实现：后端仅发布 cmd（QoS1），QoS1 由 Paho 内部完成确认，无需业务处理
                }
            });

            // ⑤ 建立连接，成功后再赋值字段，保证业务看到的 client 一定可用
            newClient.connect(options);
            this.client = newClient;
            // 首次连接时 connectComplete 回调时序不保证早于本方法返回，
            // 此处同步再订阅一次作为启动自检确认；重复订阅同一主题无副作用
            this.client.subscribe(SUBSCRIBE_TOPIC, 1);
            log.info("订阅成功（QoS1）：topic={}", SUBSCRIBE_TOPIC);
        } catch (MqttException e) {
            // 启动自检失败：复位客户端状态并抛出，由 StartupCheckRunner 终止启动
            this.client = null;
            throw new IllegalStateException("MQTT 连接/订阅失败：broker=" + mqttConfig.getBroker(), e);
        }
    }

    @Override
    public void publish(String topic, String payload, int qos) {
        // 未连接即链路不可用：sendCommand 捕获该异常后转为 3002 设备未响应
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("MQTT 未连接，无法发布");
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            client.publish(topic, message);
            log.debug("MQTT 发布成功：topic={}, qos={}, payload={}", topic, qos, payload);
        } catch (MqttException e) {
            log.error("MQTT 发布失败：topic={}, payload={}", topic, payload, e);
            throw new IllegalStateException("MQTT 发布失败", e);
        }
    }

    /**
     * 分发一条上行消息到全部监听器。
     *
     * <p>运行在 mqtt-inbound 单线程上，串行执行保证消息处理顺序。
     * 每个监听器独立 try/catch：单个业务异常不能影响同一条消息的其他监听器。
     *
     * @param topic   主题
     * @param payload 报文（UTF-8 字符串）
     */
    private void dispatch(String topic, String payload) {
        for (MqttMessageListener listener : listeners) {
            try {
                listener.onMessage(topic, payload);
            } catch (Exception e) {
                log.error("MQTT 消息监听器处理异常（已拦截，不影响连接）：topic={}, payload={}", topic, payload, e);
            }
        }
    }

    /**
     * 断开连接并释放资源。
     * <p>@PreDestroy：应用关闭时由 Spring 容器回调（优雅停机），确保 MQTT 正常离线，
     * 避免 broker 等待 KeepAlive 超时才判定后端离线。
     */
    @Override
    @PreDestroy
    public void disconnect() {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
                log.info("MQTT 已断开连接并释放客户端资源");
            } catch (MqttException e) {
                log.error("MQTT 断开连接异常（停机阶段忽略）", e);
            }
        }
        client = null;

        // 关闭上行执行器：先停止接收新任务，再等待在途消息处理完（最多 5 秒），避免停机丢告警
        if (inboundExecutor != null) {
            inboundExecutor.shutdown();
            try {
                if (!inboundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    inboundExecutor.shutdownNow();
                    log.warn("MQTT 上行执行器未在 5 秒内结束，已强制中断");
                }
            } catch (InterruptedException e) {
                inboundExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("MQTT 上行执行器已关闭");
        }
    }
}
