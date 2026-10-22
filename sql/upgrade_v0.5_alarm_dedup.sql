-- ============================================================
-- 智能教室安防系统 数据库升级脚本：alarms 幂等键调整（V0.4 -> V0.5）
-- 依据：设备端无 RTC 且 alarm_seq 仅存 RAM（重启归零），
--       重启后 alarm_id 会与上一轮重复，若仍以 alarm_id 单列做唯一键，
--       后端会把新告警误判为重复消息丢弃（现象：APP 弹窗有、告警列表查不到）。
-- 变更：uk_alarm_id(alarm_id) -> uk_alarm_id_created(alarm_id, created_at)
--       语义改为「同一 alarm_id 在同一接收秒内唯一」，保留 QoS1 重传去重能力，
--       同时允许设备重启后复用编号。
-- 注意：created_at 为 DATETIME（秒精度）且由数据库默认值 CURRENT_TIMESTAMP 生成。
-- 幂等性：可重复执行；索引不存在时先建、存在时跳过（MySQL 8.0 不支持 ADD INDEX IF NOT EXISTS，
--         故用 information_schema 判断 + 预处理语句）。
-- 适用：MySQL 8.x
-- ============================================================

USE classroom_security;

-- 1. 删除旧的单列唯一索引（若存在）
SET @drop_old := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'alarms'
        AND index_name = 'uk_alarm_id'
    ),
    'ALTER TABLE alarms DROP INDEX uk_alarm_id',
    'SELECT ''uk_alarm_id 不存在，跳过'''
  )
);
PREPARE stmt_drop FROM @drop_old;
EXECUTE stmt_drop;
DEALLOCATE PREPARE stmt_drop;

-- 2. 创建联合唯一索引 alarm_id + created_at（若不存在）
SET @add_new := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'alarms'
        AND index_name = 'uk_alarm_id_created'
    ),
    'SELECT ''uk_alarm_id_created 已存在，跳过''',
    'ALTER TABLE alarms ADD UNIQUE KEY uk_alarm_id_created (alarm_id, created_at)'
  )
);
PREPARE stmt_add FROM @add_new;
EXECUTE stmt_add;
DEALLOCATE PREPARE stmt_add;

-- 3. 校验结果：应看到 uk_alarm_id_created（Non_unique=0），且不再有 uk_alarm_id
SELECT index_name, seq_in_index, column_name, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'alarms'
ORDER BY index_name, seq_in_index;
