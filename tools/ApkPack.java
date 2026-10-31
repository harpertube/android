import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

// Usage: java ApkPack.java OUT.apk BASE.apk CLASSES.dex
//
// Writes OUT.apk with every entry of BASE.apk (the manifest and resources from
// "aapt2 link") plus CLASSES.dex. Entries keep their compression method, since
// Android requires some, like resources.arsc, to be stored. The result still
// needs zipalign and apksigner.
public class ApkPack {
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: java ApkPack.java OUT.apk BASE.apk CLASSES.dex");
            System.exit(1);
        }
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[0]));
             ZipFile base = new ZipFile(args[1])) {
            for (Enumeration<? extends ZipEntry> entries = base.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setMethod(entry.getMethod());
                if (entry.getMethod() == ZipEntry.STORED) {
                    copy.setSize(entry.getSize());
                    copy.setCompressedSize(entry.getSize());
                    copy.setCrc(entry.getCrc());
                }
                out.putNextEntry(copy);
                try (InputStream in = base.getInputStream(entry)) {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
            byte[] dex = Files.readAllBytes(Path.of(args[2]));
            CRC32 crc = new CRC32();
            crc.update(dex);
            // Stored so Android can map it without unpacking it.
            ZipEntry dexEntry = new ZipEntry("classes.dex");
            dexEntry.setMethod(ZipEntry.STORED);
            dexEntry.setSize(dex.length);
            dexEntry.setCompressedSize(dex.length);
            dexEntry.setCrc(crc.getValue());
            out.putNextEntry(dexEntry);
            out.write(dex);
            out.closeEntry();
        }
    }
}
