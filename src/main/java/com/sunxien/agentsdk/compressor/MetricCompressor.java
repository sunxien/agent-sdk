package com.sunxien.agentsdk.compressor;

/**
 * @author sunxien
 * @date 2026/4/26
 * @since 1.0.0-SNAPSHOT
 */

import com.google.common.base.Stopwatch;
import lombok.Getter;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 监控指标多级区间压缩，解决token超限问题。针对连续时间窗口内指标处于相近水位，效果最佳。
 * 最差的情况：整个时间窗口内的指标是随机波动（即忽高忽低），不仅无法压缩，反而会膨胀。注意！！！
 * <p>
 * 区间划分：
 * [时间戳] [使用率水位] [均值:xx%] [峰值:xx%]
 * 示例如下：
 * 1782395509 normal_usage avg:36% max:36%
 */
public class MetricCompressor {

    /**
     * 压缩核心方法：按等级分段，连续时间段内，相同等级的水位合并成一段。
     *
     * @param rawMetrics
     * @return List<String>
     */
    public static List<String> compressByLevel(Object[][] rawMetrics) {
        List<String> result = new ArrayList<>();
        if (rawMetrics == null || rawMetrics.length == 0) {
            result.add("No metrics given");
            return result;
        }

        Level currentLevel = null;
        long startTime = -1;
        long endTime = -1;
        double sum = 0;
        int count = 0;
        int max = Integer.MIN_VALUE;

        for (Object[] metric : rawMetrics) {
            if (metric == null || metric.length < 2) {
                continue;
            }

            long ts = convertToNumber(metric[0]);
            int val = convertToNumber(metric[1]);

            Level level = getLevel(val);

            // 初始化第一段
            if (currentLevel == null) {
                currentLevel = level;
                startTime = ts;
            }

            // 等级发生变化 → 把上一段输出
            if (level != currentLevel) {
                int avg = ((Double) (sum / count)).intValue();
                addSegment(result, currentLevel, startTime, endTime, avg, max);

                // 重置新段
                currentLevel = level;
                startTime = ts;
                sum = 0;
                count = 0;
                max = Integer.MIN_VALUE;
            }

            // 累计本段数据
            endTime = ts;
            sum += val;
            count++;
            if (val > max) {
                max = val;
            }
        }

        // 输出最后一段
        if (count > 0) {
            int avg = ((Double) (sum / count)).intValue();
            addSegment(result, currentLevel, startTime, endTime, avg, max);
        }
        return result;
    }

    /**
     * 根据指标值获取对应的等级，等级如下：
     * [0, 50) normal usage
     * [50, 70) medium usage
     * [70, 100] high usage
     *
     * @param value
     * @return Level
     */
    private static Level getLevel(double value) {
        if (value < 50) {
            return Level.NORMAL;
        } else if (value < 70) {
            return Level.MEDIUM;
        } else {
            return Level.HIGH;
        }
    }

    /**
     * 高性能：浮点字符串，直接截断小数转int。
     * 默认：只支持正常的小数，不支持±符号，科学计数符号e，以及其他非法数字。
     * 例："1782394361.99" → 1782394361
     * 无法处理科学计数法，例如：1.34e3
     * 非数字或者非有效数字，该方法均返回0
     *
     * @param val
     * @return int
     */
    private static int convertToNumber(Object val) {
        if (val == null) {
            return 0;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        String s = null;
        if (val instanceof String) {
            s = (String) val;
        } else {
            s = val.toString();
        }
        final int len = s.length();
        int res = 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            // 遇到小数点直接截断，结束遍历
            if (c == '.') {
                break;
            }
            // 仅处理有效数字字符
            if (c >= '0' && c <= '9') {
                res = res * 10 + (c - '0');
            } else {
                // 非数字（包括科学计数符e），直接返回0
                return 0;
            }
        }
        return res;
    }

    /**
     * 输出分段摘要（大模型友好格式）
     *
     * @param result
     * @param level
     * @param startTime
     * @param endTime
     * @param avg
     * @param max
     */
    private static void addSegment(List<String> result, Level level,
                                   long startTime, long endTime, int avg, int max) {
        String display = "";
        if (startTime == endTime) {
            display += startTime;
        } else {
            display = startTime + "~" + endTime;
        }
        display += " " + level.getText() + " avg:" + avg + "% max:" + max + "%";
        result.add(display);
    }

    /**
     * 定义负载等级枚举
     */
    @Getter
    public static enum Level {

        // normal_usage: [0%, 50%)
        NORMAL("normal_usage"),

        // medium_usage: [50%, 70%)
        MEDIUM("medium_usage"),

        // high_usage: [70%, 100%]
        HIGH("high_usage");

        private final String text;

        Level(String text) {
            this.text = text;
        }
    }

    // ====================== 测试 ======================
    public static void main(String[] args) {
        Object[][] values = mockMetrics();

        Stopwatch stopwatch = Stopwatch.createStarted();
        List<String> res = MetricCompressor.compressByLevel(values);
        System.out.println("MetricCompressor Elapsed: " + stopwatch.stop());

        res.forEach(System.out::println);
    }

    /**
     * @return Object[][]
     */
    private static Object[][] mockMetrics() {
        long timeSecs = System.currentTimeMillis() / 1000;
        int cap = 86400; // 1day = 86400secs
        Object[][] values = new Object[cap][2];
        for (int i = 0; i < cap; i++) {
            values[i][0] = timeSecs + (i + 1) * 60;
            double normal = new Random().nextDouble(100);
            values[i][1] = String.valueOf(normal);
        }
        return values;
    }
}
