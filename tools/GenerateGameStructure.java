import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

public final class GenerateGameStructure {
    public static void main(String[] arguments) throws Exception {
        Path destination = Path.of("src/gametest/resources/data/shiinacasino/structure/empty.nbt");
        Files.createDirectories(destination.getParent());
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(destination)))) {
            output.writeByte(10); output.writeUTF("");
            output.writeByte(3); output.writeUTF("DataVersion"); output.writeInt(3955);
            output.writeByte(9); output.writeUTF("size"); output.writeByte(3); output.writeInt(3);
            output.writeInt(9); output.writeInt(5); output.writeInt(9);
            output.writeByte(9); output.writeUTF("palette"); output.writeByte(10); output.writeInt(1);
            output.writeByte(8); output.writeUTF("Name"); output.writeUTF("minecraft:air"); output.writeByte(0);
            output.writeByte(9); output.writeUTF("blocks"); output.writeByte(10); output.writeInt(0);
            output.writeByte(9); output.writeUTF("entities"); output.writeByte(10); output.writeInt(0);
            output.writeByte(0);
        }
        System.out.println("Generated empty GameTest structure.");
    }
}
