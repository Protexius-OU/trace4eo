package com.protexius.trace4eo.provenance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.protexius.trace4eo.provenance.record.FilesInfo;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleDeserializers;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.module.SimpleSerializers;

import java.util.Base64;

@SuppressWarnings("unchecked")
public class ProvenanceJsonMapper extends JsonMapper {

    public ProvenanceJsonMapper() {
        super(JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .addModule(buildModule(byte[].class, new ByteArraySerializer(), new ByteArrayDeserializer()))
            .addModule(buildModule(FilesInfo.class, new FilesInfoSerializer(), null))
        );
    }

    private static <T> JacksonModule buildModule(
        Class<T> clazz,
        ValueSerializer<T> serializer,
        ValueDeserializer<T> deserializer
    ) {
        SimpleModule module = new SimpleModule();
        if (serializer != null) {
            SimpleSerializers serializers = new SimpleSerializers();
            serializers.addSerializer(clazz, serializer);
            module.setSerializers(serializers);
        }
        if (deserializer != null) {
            SimpleDeserializers deserializers = new SimpleDeserializers();
            deserializers.addDeserializer(clazz, deserializer);
            module.setDeserializers(deserializers);
        }
        return module;
    }

    private static final class ByteArraySerializer extends ValueSerializer<byte[]> {
        @Override
        public void serialize(byte[] value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(value == null ? null : Base64.getEncoder().encodeToString(value));
        }
    }

    private static final class ByteArrayDeserializer extends ValueDeserializer<byte[]> {
        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) {
            String text = p.getValueAsString();
            return text == null ? null : Base64.getDecoder().decode(text);
        }
    }

    private static final class FilesInfoSerializer extends ValueSerializer<FilesInfo> {
        @Override
        public void serialize(FilesInfo value, JsonGenerator gen, SerializationContext ctxt) {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeStartObject();
            gen.writeName("files");
            ctxt.writeValue(gen, value.files());
            gen.writeEndObject();
        }
    }
}
