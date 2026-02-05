package musicsearch.service;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class MP3CoverExtractor {

    private static final String COVER_DIR_NAME = "MusicSearch_covers";
    private static final Path COVER_DIR = initCoverDir();
    private static final Map<String, Path> cache = new ConcurrentHashMap<>();

    private static Path initCoverDir() {
        try {
            String tmp = System.getProperty("java.io.tmpdir");
            Path dir = Paths.get(tmp, COVER_DIR_NAME);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return dir;
        } catch (Exception e) {
            // fallback to system temp (shouldn't happen often)
            return Paths.get(System.getProperty("java.io.tmpdir"));
        }
    }

    /**
     * Возвращает URI (file://...) созданного файла с обложкой, или null, если не удалось извлечь.
     */
    public static String extractCoverFromMP3(String filePath) {
        if (filePath == null) return null;
        try {
            AudioFile audioFile = AudioFileIO.read(new File(filePath));
            Tag tag = audioFile.getTag();
            if (tag == null) return null;
            Artwork artwork = tag.getFirstArtwork();
            if (artwork == null) return null;
            byte[] imageData = artwork.getBinaryData();
            if (imageData == null || imageData.length == 0) return null;

            // детерминированный хеш по пути + содержимому (чтобы при смене обложки создавался новый файл)
            String fileHash = sha1Hex(filePath + "|" + Arrays.hashCode(imageData));

            // уже в кеше?
            Path existing = cache.get(fileHash);
            if (existing != null && Files.exists(existing)) {
                return existing.toUri().toString();
            }

            // ищем на диске (вдруг оставшийся после перезапуска)
            Path found = findExistingCoverFile(fileHash);
            if (found != null) {
                cache.put(fileHash, found);
                return found.toUri().toString();
            }

            // определяем расширение по mime
            String mime = artwork.getMimeType();
            String ext = extByMime(mime);
            if (ext == null) {
                ext = detectImageExtensionBySignature(imageData);
                if (ext == null) ext = "jpg";
            }

            String fileName = "cover_" + fileHash + "." + ext;
            Path target = COVER_DIR.resolve(fileName);

            // atomic write: сначала во временный файл, затем move(REPLACE_EXISTING)
            Path tmp = Files.createTempFile(COVER_DIR, "tmp_cover_", "." + ext);
            try (OutputStream os = Files.newOutputStream(tmp, StandardOpenOption.WRITE)) {
                os.write(imageData);
                os.flush();
            }

            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            cache.put(fileHash, target);
            return target.toUri().toString();

        } catch (Exception e) {
            System.err.println("Error extracting cover from MP3: " + e.getMessage());
            // e.printStackTrace();  // можно раскомментировать при отладке
            return null;
        }
    }

    public static boolean hasEmbeddedCover(String filePath) {
        if (filePath == null) return false;
        try {
            AudioFile audioFile = AudioFileIO.read(new File(filePath));
            Tag tag = audioFile.getTag();
            return tag != null && tag.getFirstArtwork() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path findExistingCoverFile(String fileHash) {
        try (Stream<Path> s = Files.list(COVER_DIR)) {
            return s
                    .filter(p -> p.getFileName().toString().startsWith("cover_" + fileHash + "."))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String extByMime(String mime) {
        if (mime == null) return null;
        mime = mime.toLowerCase();
        if (mime.contains("png")) return "png";
        if (mime.contains("jpeg") || mime.contains("jpg")) return "jpg";
        if (mime.contains("gif")) return "gif";
        if (mime.contains("bmp")) return "bmp";
        return null;
    }

    private static String detectImageExtensionBySignature(byte[] bytes) {
        if (bytes == null || bytes.length < 8) return null;
        // PNG signature
        if (bytes[0] == (byte)0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) return "png";
        // JPG (ff d8 ff)
        if (bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8) return "jpg";
        // GIF (GIF8)
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "gif";
        // BMP "BM"
        if (bytes[0] == 'B' && bytes[1] == 'M') return "bmp";
        return null;
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            // fallback
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * Удаляет файлы-обложки старше maxAgeMs и ограничивает общее число файлов keepMax.
     * Перед вызовом убедись, что приложение не использует эти файлы в данный момент.
     */
    public static void cleanupOldCoverFiles(long maxAgeMs, int keepMax) {
        try (Stream<Path> s = Files.list(COVER_DIR)) {
            // сортируем по дате изменения (старые первыми)
            Path[] arr = s.filter(p -> p.getFileName().toString().startsWith("cover_"))
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(Files.getLastModifiedTime(a).toMillis(), Files.getLastModifiedTime(b).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    }).toArray(Path[]::new);

            long now = System.currentTimeMillis();
            int deleted = 0;
            for (int i = 0; i < arr.length; i++) {
                Path p = arr[i];
                boolean tooOld = false;
                try {
                    long last = Files.getLastModifiedTime(p).toMillis();
                    if (now - last > maxAgeMs) tooOld = true;
                } catch (IOException ignored) {}

                if (tooOld || (keepMax > 0 && arr.length - deleted > keepMax && i < arr.length - keepMax)) {
                    try {
                        Files.deleteIfExists(p);
                        // remove from cache if present
                        cache.values().removeIf(path -> path.equals(p));
                        deleted++;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Удобный вызов: удаляет файлы старше 7 дней и оставляет максимум 200 файлов.
     */
    public static void cleanupOldCoverFiles() {
        long sevenDays = 7L * 24 * 60 * 60 * 1000;
        cleanupOldCoverFiles(sevenDays, 200);
    }
}
