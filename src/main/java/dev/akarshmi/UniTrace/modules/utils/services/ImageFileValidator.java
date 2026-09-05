package dev.akarshmi.UniTrace.modules.utils.services;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

@Component
public class ImageFileValidator {

    public boolean isValidImage(MultipartFile file) {
        try {
            byte[] header = new byte[12];
            int bytesRead = file.getInputStream().read(header);
            if (bytesRead < 4) return false;

            return isJpeg(header) || isPng(header) || isGif(header) || isWebp(header);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isJpeg(byte[] h) {
        return (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] h) {
        byte[] pngSig = {(byte) 0x89, 0x50, 0x4E, 0x47};
        return Arrays.equals(Arrays.copyOfRange(h, 0, 4), pngSig);
    }

    private boolean isGif(byte[] h) {
        return h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8';
    }

    private boolean isWebp(byte[] h) {
        // bytes 0-3 = "RIFF", bytes 8-11 = "WEBP"
        return h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }
}