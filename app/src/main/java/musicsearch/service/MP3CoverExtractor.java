package musicsearch.service;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import java.io.File;
import java.io.FileOutputStream;

public class MP3CoverExtractor {
    
    public static String extractCoverFromMP3(String filePath) {
        try {
            AudioFile audioFile = AudioFileIO.read(new File(filePath));
            Tag tag = audioFile.getTag();
            
            if (tag == null) {
                return null;
            }
            
            Artwork artwork = tag.getFirstArtwork();
            if (artwork == null) {
                return null;
            }
            
            byte[] imageData = artwork.getBinaryData();
            if (imageData == null || imageData.length == 0) {
                return null;
            }
            
            String fileHash = Integer.toHexString(filePath.hashCode());
            String tempFileName = "cover_" + fileHash + "_" + System.currentTimeMillis() + ".jpg";
            File tempFile = new File(System.getProperty("java.io.tmpdir"), tempFileName);
            
            File existingFile = findExistingCoverFile(fileHash);
            if (existingFile != null && existingFile.exists()) {
                return existingFile.toURI().toString();
            }
            
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(imageData);
            }
            
            return tempFile.toURI().toString();
            
        } catch (Exception e) {
            System.err.println("Error extracting cover from MP3: " + e.getMessage());
            return null;
        }
    }
    
    private static File findExistingCoverFile(String fileHash) {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File[] files = tempDir.listFiles((dir, name) -> 
            name.startsWith("cover_" + fileHash) && name.endsWith(".jpg")
        );
        
        if (files != null && files.length > 0) {
            return files[0];
        }
        return null;
    }
    
    public static void cleanupOldCoverFiles() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File[] coverFiles = tempDir.listFiles((dir, name) -> 
            name.startsWith("cover_") && name.endsWith(".jpg")
        );
        
        if (coverFiles != null) {
            long now = System.currentTimeMillis();
            long DAY_IN_MS = 24 * 60 * 60 * 1000;
            
            for (File file : coverFiles) {
                if (now - file.lastModified() > DAY_IN_MS) {
                    file.delete();
                }
            }
        }
    }
    
    public static boolean hasEmbeddedCover(String filePath) {
        try {
            AudioFile audioFile = AudioFileIO.read(new File(filePath));
            Tag tag = audioFile.getTag();
            return tag != null && tag.getFirstArtwork() != null;
        } catch (Exception e) {
            return false;
        }
    }
}