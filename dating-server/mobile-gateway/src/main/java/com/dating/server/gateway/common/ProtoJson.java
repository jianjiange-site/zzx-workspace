package com.dating.server.gateway.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import java.util.Map;

/**
 * Protobuf → JSON Map 转换工具。
 * <p>
 * 为什么不直接返回 proto 对象？
 * Jackson 序列化 proto 时会多出 getSerializedSize() / getDefaultInstance() 等杂字段，
 * 先用 protobuf-java-util 的 JsonFormat 转成标准 JSON 再反成 Map，输出干净可控。
 * 这种方式比手写 VO 类更省，但类型不安全——业务复杂的接口后续应改为专用 DTO。
 */
public final class ProtoJson {

    private static final JsonFormat.Printer PRINTER = JsonFormat.printer().omittingInsignificantWhitespace();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Map<String, Object> toMap(Message proto) {
        try {
            String json = PRINTER.print(proto);
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("proto to map failed", e);
        }
    }
}
