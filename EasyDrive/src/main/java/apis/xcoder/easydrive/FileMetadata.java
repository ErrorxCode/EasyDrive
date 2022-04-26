package apis.xcoder.easydrive;

public class FileMetadata {
    public String name;
    public String id;
    public String mimeType;
    public int size;

    public FileMetadata(String name, String id, String mimeType, int size) {
        this.name = name;
        this.id = id;
        this.mimeType = mimeType;
        this.size = size;
    }
}