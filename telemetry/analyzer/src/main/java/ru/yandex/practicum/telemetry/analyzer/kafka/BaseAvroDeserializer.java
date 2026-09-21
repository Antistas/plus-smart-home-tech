package ru.yandex.practicum.telemetry.analyzer.kafka;
import org.apache.avro.Schema;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.serialization.Deserializer;
import java.io.IOException;
public class BaseAvroDeserializer<T extends SpecificRecordBase> implements Deserializer<T> {
    private final SpecificDatumReader<T> reader;
    private final DecoderFactory decoderFactory;
    public BaseAvroDeserializer(Schema schema) { this(DecoderFactory.get(), schema); }
    BaseAvroDeserializer(DecoderFactory decoderFactory, Schema schema) { this.decoderFactory = decoderFactory; this.reader = new SpecificDatumReader<>(schema); }
    @Override public T deserialize(String topic, byte[] data) {
        if (data == null) return null;
        try { return reader.read(null, decoderFactory.binaryDecoder(data, null)); }
        catch (IOException e) { throw new IllegalArgumentException("Не удалось десериализовать Avro-сообщение из топика " + topic, e); }
    }
}
